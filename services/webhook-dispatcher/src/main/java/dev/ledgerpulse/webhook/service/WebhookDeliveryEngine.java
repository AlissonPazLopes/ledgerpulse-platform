package dev.ledgerpulse.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.domain.WebhookDelivery;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryAttempt;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.messaging.WebhookEventMessage;
import dev.ledgerpulse.webhook.observability.WebhookDispatcherMetrics;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryAttemptRepository;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryRepository;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import dev.ledgerpulse.webhook.web.InvalidDeliveryStateException;
import dev.ledgerpulse.webhook.web.ResourceNotFoundException;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches signed webhook HTTP requests on Java 21 Virtual Threads without holding
 * database connections open during outbound network I/O.
 */
@Service
public class WebhookDeliveryEngine {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryEngine.class);

    public record DispatchResult(
            UUID deliveryId,
            UUID endpointId,
            UUID eventId,
            WebhookDeliveryStatus status,
            int attemptNumber,
            Integer httpStatus,
            long latencyMs,
            String errorMessage,
            boolean skippedAlreadyDelivered,
            boolean executedOnVirtualThread
    ) {
    }

    public record DlqReplayResult(
            UUID deliveryId,
            UUID eventId,
            WebhookDeliveryStatus status,
            Instant replayedAt,
            Integer httpStatus,
            long latencyMs,
            String errorMessage
    ) {
    }

    private record PreparedDelivery(
            UUID deliveryId,
            UUID endpointId,
            String targetUrl,
            String signingSecret,
            UUID eventId,
            String correlationId,
            String payloadJson,
            int attemptNumber,
            boolean skippedAlreadyDelivered
    ) {
    }

    private record HttpExecutionResult(
            Integer httpStatus,
            long latencyMs,
            String signatureHeader,
            String errorMessage,
            boolean recoverable,
            boolean executedOnVirtualThread,
            Instant attemptedAt
    ) {
    }

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final HmacSignatureService hmacSignatureService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookDispatcherMetrics metrics;
    private final ExecutorService virtualThreadExecutor;
    private final HttpClient httpClient;
    private final long readTimeoutMs;

    public WebhookDeliveryEngine(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            HmacSignatureService hmacSignatureService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            WebhookDispatcherMetrics metrics,
            @Value("${app.webhook.http.connect-timeout-ms:1500}") long connectTimeoutMs,
            @Value("${app.webhook.http.read-timeout-ms:2000}") long readTimeoutMs
    ) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.hmacSignatureService = hmacSignatureService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.readTimeoutMs = Math.max(50L, readTimeoutMs);
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(50L, connectTimeoutMs)))
                .executor(this.virtualThreadExecutor)
                .build();
    }

    @PreDestroy
    void shutdownExecutor() {
        virtualThreadExecutor.close();
    }

    public List<DispatchResult> dispatchEvent(WebhookEventMessage event, String rawPayloadJson) {
        return dispatchEvent(event, rawPayloadJson, false);
    }

    public List<DispatchResult> dispatchEvent(
            WebhookEventMessage event,
            String rawPayloadJson,
            boolean forceDlqOnFailure
    ) {
        String normalizedPayload = resolvePayloadJson(event, rawPayloadJson);
        String correlationId = StringUtils.hasText(event.correlationId())
                ? event.correlationId().trim()
                : UUID.randomUUID().toString();

        List<PreparedDelivery> preparedList = prepareDeliveries(event, normalizedPayload, correlationId);
        if (preparedList.isEmpty()) {
            return List.of();
        }

        List<DispatchResult> results = new ArrayList<>(preparedList.size());
        List<CompletableFuture<DispatchResult>> inFlight = new ArrayList<>();

        for (PreparedDelivery work : preparedList) {
            if (work.skippedAlreadyDelivered()) {
                results.add(new DispatchResult(
                        work.deliveryId(),
                        work.endpointId(),
                        work.eventId(),
                        WebhookDeliveryStatus.DELIVERED,
                        work.attemptNumber(),
                        null,
                        0L,
                        null,
                        true,
                        false
                ));
                continue;
            }

            CompletableFuture<DispatchResult> future = CompletableFuture
                    .supplyAsync(() -> performHttpCall(work), virtualThreadExecutor)
                    .thenApply(httpResult -> persistAttemptAndOutcome(work, httpResult, forceDlqOnFailure));
            inFlight.add(future);
        }

        for (CompletableFuture<DispatchResult> future : inFlight) {
            results.add(future.join());
        }
        return results;
    }

    public DlqReplayResult replayDlqDelivery(UUID deliveryId) {
        PreparedDelivery work = transactionTemplate.execute(status -> {
            WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Entrega webhook não encontrada: " + deliveryId
                    ));
            if (delivery.getStatus() != WebhookDeliveryStatus.DLQ) {
                throw new InvalidDeliveryStateException(
                        "A entrega webhook " + deliveryId + " não está em DLQ (estado atual: " + delivery.getStatus() + ")."
                );
            }
            WebhookEndpoint endpoint = endpointRepository.findById(delivery.getEndpointId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Endpoint webhook não encontrado: " + delivery.getEndpointId()
                    ));
            return new PreparedDelivery(
                    delivery.getId(),
                    endpoint.getId(),
                    endpoint.getTargetUrl(),
                    endpoint.getSigningSecret(),
                    delivery.getEventId(),
                    delivery.getCorrelationId(),
                    delivery.getPayloadJson(),
                    delivery.getAttemptCount() + 1,
                    false
            );
        });

        HttpExecutionResult httpResult = CompletableFuture
                .supplyAsync(() -> performHttpCall(work), virtualThreadExecutor)
                .join();

        return transactionTemplate.execute(status -> {
            WebhookDelivery delivery = deliveryRepository.findById(work.deliveryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Entrega webhook não encontrada: " + work.deliveryId()
                    ));
            if (delivery.getStatus() != WebhookDeliveryStatus.DLQ) {
                throw new InvalidDeliveryStateException(
                        "A entrega webhook " + deliveryId + " mudou de estado concorrentemente para: " + delivery.getStatus()
                );
            }
            WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt(
                    UUID.randomUUID(),
                    delivery.getId(),
                    work.attemptNumber(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.signatureHeader(),
                    httpResult.errorMessage(),
                    httpResult.attemptedAt()
            );
            attemptRepository.save(attempt);

            delivery.recordAttemptOutcome(
                    work.attemptNumber(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.signatureHeader(),
                    httpResult.errorMessage(),
                    httpResult.recoverable(),
                    false,
                    httpResult.attemptedAt()
            );
            deliveryRepository.save(delivery);

            return new DlqReplayResult(
                    delivery.getId(),
                    delivery.getEventId(),
                    delivery.getStatus(),
                    httpResult.attemptedAt(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.errorMessage()
            );
        });
    }

    private List<PreparedDelivery> prepareDeliveries(
            WebhookEventMessage event,
            String payloadJson,
            String correlationId
    ) {
        List<WebhookEndpoint> activeEndpoints = endpointRepository
                .findByMerchantIdAndStatus(event.merchantId(), WebhookEndpointStatus.ACTIVE);
        if (activeEndpoints.isEmpty()) {
            log.debug("Nenhum endpoint ativo encontrado para merchantId={} eventId={}", event.merchantId(), event.eventId());
            return List.of();
        }

        List<PreparedDelivery> prepared = new ArrayList<>(activeEndpoints.size());
        for (WebhookEndpoint endpoint : activeEndpoints) {
            PreparedDelivery work = transactionTemplate.execute(status -> {
                WebhookDelivery delivery = deliveryRepository
                        .findByEventIdAndEndpointId(event.eventId(), endpoint.getId())
                        .orElseGet(() -> createDeliveryIdempotently(event, endpoint, payloadJson, correlationId));

                if (delivery.getStatus() == WebhookDeliveryStatus.DELIVERED) {
                    return new PreparedDelivery(
                            delivery.getId(),
                            endpoint.getId(),
                            endpoint.getTargetUrl(),
                            endpoint.getSigningSecret(),
                            event.eventId(),
                            delivery.getCorrelationId(),
                            delivery.getPayloadJson(),
                            delivery.getAttemptCount(),
                            true
                    );
                }

                return new PreparedDelivery(
                        delivery.getId(),
                        endpoint.getId(),
                        endpoint.getTargetUrl(),
                        endpoint.getSigningSecret(),
                        event.eventId(),
                        delivery.getCorrelationId(),
                        delivery.getPayloadJson(),
                        delivery.getAttemptCount() + 1,
                        false
                );
            });
            if (work != null) {
                prepared.add(work);
            }
        }
        return prepared;
    }

    private WebhookDelivery createDeliveryIdempotently(
            WebhookEventMessage event,
            WebhookEndpoint endpoint,
            String payloadJson,
            String correlationId
    ) {
        try {
            WebhookDelivery created = new WebhookDelivery(
                    UUID.randomUUID(),
                    event.eventId(),
                    endpoint.getId(),
                    event.merchantId(),
                    event.eventType(),
                    correlationId,
                    payloadJson,
                    WebhookDelivery.DEFAULT_MAX_ATTEMPTS
            );
            return deliveryRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException concurrentInsert) {
            return deliveryRepository.findByEventIdAndEndpointId(event.eventId(), endpoint.getId())
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private HttpExecutionResult performHttpCall(PreparedDelivery work) {
        boolean onVirtualThread = Thread.currentThread().isVirtual();
        Instant attemptedAt = Instant.now();
        String signatureHeader = hmacSignatureService.sign(
                work.signingSecret(),
                work.payloadJson(),
                attemptedAt.getEpochSecond()
        );

        long startNanos = System.nanoTime();
        Timer.Sample latencySample = metrics.startDeliveryTimer();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(work.targetUrl()))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-LedgerPulse-Signature", signatureHeader)
                    .header("X-LedgerPulse-Event-Id", work.eventId().toString())
                    .header("X-Correlation-Id", work.correlationId())
                    .POST(HttpRequest.BodyPublishers.ofString(work.payloadJson(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            int statusCode = response.statusCode();

            if (WebhookDelivery.isSuccessStatus(statusCode)) {
                return new HttpExecutionResult(statusCode, latencyMs, signatureHeader, null, false, onVirtualThread, attemptedAt);
            }
            boolean recoverable = WebhookDelivery.isRecoverableStatus(statusCode);
            return new HttpExecutionResult(
                    statusCode,
                    latencyMs,
                    signatureHeader,
                    "Webhook target returned HTTP " + statusCode,
                    recoverable,
                    onVirtualThread,
                    attemptedAt
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            long latencyMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            return new HttpExecutionResult(
                    null,
                    latencyMs,
                    signatureHeader,
                    "Webhook delivery interrupted: " + ex.getMessage(),
                    true,
                    onVirtualThread,
                    attemptedAt
            );
        } catch (Exception ex) {
            long latencyMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new HttpExecutionResult(
                    null,
                    latencyMs,
                    signatureHeader,
                    ex.getClass().getSimpleName() + ": " + message,
                    true,
                    onVirtualThread,
                    attemptedAt
            );
        } finally {
            metrics.stopDeliveryTimer(latencySample);
        }
    }

    private DispatchResult persistAttemptAndOutcome(
            PreparedDelivery work,
            HttpExecutionResult httpResult,
            boolean forceDlqOnFailure
    ) {
        return transactionTemplate.execute(status -> {
            WebhookDelivery delivery = deliveryRepository.findById(work.deliveryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Entrega webhook não encontrada: " + work.deliveryId()
                    ));
            if (delivery.getStatus() == WebhookDeliveryStatus.DELIVERED) {
                return new DispatchResult(
                        delivery.getId(),
                        delivery.getEndpointId(),
                        delivery.getEventId(),
                        delivery.getStatus(),
                        delivery.getAttemptCount(),
                        delivery.getLastHttpStatus(),
                        delivery.getLastLatencyMs() == null ? 0L : delivery.getLastLatencyMs(),
                        null,
                        true,
                        httpResult.executedOnVirtualThread()
                );
            }

            WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt(
                    UUID.randomUUID(),
                    delivery.getId(),
                    work.attemptNumber(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.signatureHeader(),
                    httpResult.errorMessage(),
                    httpResult.attemptedAt()
            );
            attemptRepository.save(attempt);

            delivery.recordAttemptOutcome(
                    work.attemptNumber(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.signatureHeader(),
                    httpResult.errorMessage(),
                    httpResult.recoverable(),
                    forceDlqOnFailure,
                    httpResult.attemptedAt()
            );
            deliveryRepository.save(delivery);
            metrics.recordDeliveryOutcome(delivery.getStatus(), delivery.getEventType());

            return new DispatchResult(
                    delivery.getId(),
                    delivery.getEndpointId(),
                    delivery.getEventId(),
                    delivery.getStatus(),
                    work.attemptNumber(),
                    httpResult.httpStatus(),
                    httpResult.latencyMs(),
                    httpResult.errorMessage(),
                    false,
                    httpResult.executedOnVirtualThread()
            );
        });
    }

    private String resolvePayloadJson(WebhookEventMessage event, String rawPayloadJson) {
        if (StringUtils.hasText(rawPayloadJson)) {
            return rawPayloadJson;
        }
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize WebhookEventMessage", ex);
        }
    }
}

package dev.ledgerpulse.webhook.service;

import dev.ledgerpulse.webhook.api.CreateWebhookEndpointRequest;
import dev.ledgerpulse.webhook.api.DlqReplayResponse;
import dev.ledgerpulse.webhook.api.WebhookDeliveryAttemptResponse;
import dev.ledgerpulse.webhook.api.WebhookDeliveryResponse;
import dev.ledgerpulse.webhook.api.WebhookEndpointResponse;
import dev.ledgerpulse.webhook.domain.WebhookDelivery;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryAttempt;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryAttemptRepository;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryRepository;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import dev.ledgerpulse.webhook.web.DomainValidationException;
import dev.ledgerpulse.webhook.web.PageResponse;
import dev.ledgerpulse.webhook.web.ValidationFieldError;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WebhookManagementService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final WebhookDeliveryEngine deliveryEngine;

    public WebhookManagementService(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            WebhookDeliveryEngine deliveryEngine
    ) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.deliveryEngine = deliveryEngine;
    }

    @Transactional
    public WebhookEndpointResponse createEndpoint(CreateWebhookEndpointRequest request) {
        String normalizedUrl = validateHttpUri(request.targetUrl());
        String description = StringUtils.hasText(request.description()) ? request.description().trim() : null;

        WebhookEndpoint endpoint = new WebhookEndpoint(
                UUID.randomUUID(),
                request.merchantId(),
                normalizedUrl,
                request.secret().trim(),
                description,
                WebhookEndpointStatus.ACTIVE
        );
        WebhookEndpoint saved = endpointRepository.saveAndFlush(endpoint);
        return toEndpointResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WebhookEndpointResponse> listEndpoints(
            UUID merchantId,
            WebhookEndpointStatus status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<WebhookEndpoint> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<WebhookEndpointResponse> result = endpointRepository.findAll(spec, pageable)
                .map(this::toEndpointResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<WebhookDeliveryResponse> listDeliveries(
            WebhookDeliveryStatus status,
            UUID merchantId,
            String eventType,
            UUID eventId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<WebhookDelivery> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (StringUtils.hasText(eventType)) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<WebhookDelivery> deliveryPage = deliveryRepository.findAll(spec, pageable);
        List<WebhookDelivery> deliveries = deliveryPage.getContent();
        if (deliveries.isEmpty()) {
            return PageResponse.from(deliveryPage.map(d -> toDeliveryResponse(d, "", List.of())));
        }

        List<UUID> deliveryIds = deliveries.stream().map(WebhookDelivery::getId).toList();
        Map<UUID, List<WebhookDeliveryAttemptResponse>> attemptsByDelivery = attemptRepository
                .findByDeliveryIdInOrderByAttemptNumberAsc(deliveryIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WebhookDeliveryAttempt::getDeliveryId,
                        Collectors.mapping(this::toAttemptResponse, Collectors.toList())
                ));

        Set<UUID> endpointIds = deliveries.stream().map(WebhookDelivery::getEndpointId).collect(Collectors.toSet());
        Map<UUID, String> targetUrlsByEndpointId = endpointRepository.findAllById(endpointIds).stream()
                .collect(Collectors.toMap(WebhookEndpoint::getId, WebhookEndpoint::getTargetUrl));

        Page<WebhookDeliveryResponse> mapped = deliveryPage.map(delivery -> toDeliveryResponse(
                delivery,
                targetUrlsByEndpointId.getOrDefault(delivery.getEndpointId(), ""),
                attemptsByDelivery.getOrDefault(delivery.getId(), List.of())
        ));
        return PageResponse.from(mapped);
    }

    public DlqReplayResponse replayDlqDelivery(UUID deliveryId) {
        WebhookDeliveryEngine.DlqReplayResult result = deliveryEngine.replayDlqDelivery(deliveryId);
        return new DlqReplayResponse(
                result.deliveryId(),
                result.eventId(),
                result.status(),
                result.replayedAt(),
                result.httpStatus(),
                result.latencyMs(),
                result.errorMessage()
        );
    }

    private String validateHttpUri(String rawUrl) {
        String candidate = rawUrl == null ? "" : rawUrl.trim();
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new DomainValidationException(
                        "targetUrl deve usar esquema http:// ou https:// com host válido.",
                        List.of(new ValidationFieldError("targetUrl", "must be a valid http or https URL"))
                );
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new DomainValidationException(
                    "targetUrl inválida.",
                    List.of(new ValidationFieldError("targetUrl", "must be a valid URI"))
            );
        }
    }

    private WebhookEndpointResponse toEndpointResponse(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getMerchantId(),
                endpoint.getTargetUrl(),
                endpoint.getMaskedSecretPreview(),
                endpoint.getDescription(),
                endpoint.getStatus(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }

    private WebhookDeliveryAttemptResponse toAttemptResponse(WebhookDeliveryAttempt attempt) {
        return new WebhookDeliveryAttemptResponse(
                attempt.getAttemptNumber(),
                attempt.getHttpStatus(),
                attempt.getLatencyMs(),
                attempt.getSignatureHeader(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }

    private WebhookDeliveryResponse toDeliveryResponse(
            WebhookDelivery delivery,
            String targetUrl,
            List<WebhookDeliveryAttemptResponse> attempts
    ) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getMerchantId(),
                delivery.getEndpointId(),
                targetUrl,
                delivery.getCorrelationId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getMaxAttempts(),
                delivery.getLastHttpStatus(),
                delivery.getLastLatencyMs(),
                delivery.getLastError(),
                delivery.getSignatureHeader(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getDeliveredAt(),
                attempts
        );
    }
}

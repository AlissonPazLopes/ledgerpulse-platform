package dev.ledgerpulse.core.stream;

import dev.ledgerpulse.core.outbox.OutboxEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseEventHub {

    private static final Logger log = LoggerFactory.getLogger(SseEventHub.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final long emitterTimeoutMs;

    public SseEventHub(@Value("${app.sse.emitter-timeout-ms:0}") long emitterTimeoutMs) {
        this.emitterTimeoutMs = Math.max(0L, emitterTimeoutMs);
    }

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> emitters.remove(emitter));

        emitters.add(emitter);
        try {
            SseControlEvent connected = new SseControlEvent("connected", Instant.now(), "SSE stream connected");
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .id(UUID.randomUUID().toString())
                    .data(connected, MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            emitters.remove(emitter);
            completeSilently(emitter);
            log.debug("Falha ao enviar evento inicial 'connected' no SSE: {}", ex.getMessage());
        }
        return emitter;
    }

    public void broadcastDomainEvent(OutboxEventEnvelope envelope) {
        if (emitters.isEmpty() || envelope == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(envelope.eventType())
                        .id(envelope.eventId().toString())
                        .data(envelope, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                emitters.remove(emitter);
                completeSilently(emitter);
                log.debug("Removendo emitter SSE inativo durante broadcast ({}): {}", envelope.eventType(), ex.getMessage());
            }
        }
    }

    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        SseControlEvent heartbeat = new SseControlEvent("heartbeat", Instant.now(), "keepalive");
        String heartbeatId = UUID.randomUUID().toString();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .id(heartbeatId)
                        .data(heartbeat, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                emitters.remove(emitter);
                completeSilently(emitter);
            }
        }
    }

    public int activeSubscriberCount() {
        return emitters.size();
    }

    private void completeSilently(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Emitter already closed.
        }
    }
}

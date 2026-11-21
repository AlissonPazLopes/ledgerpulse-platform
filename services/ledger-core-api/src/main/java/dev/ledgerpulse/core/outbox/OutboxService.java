package dev.ledgerpulse.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    public static final String SCHEMA_VERSION = "1.0";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordEvent(
            String aggregateType,
            UUID aggregateId,
            UUID merchantId,
            String eventType,
            String correlationId,
            Map<String, Object> payload
    ) {
        UUID eventId = UUID.randomUUID();
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                eventId,
                eventType,
                SCHEMA_VERSION,
                Instant.now(),
                correlationId,
                merchantId,
                payload
        );
        try {
            String payloadJson = objectMapper.writeValueAsString(envelope);
            OutboxEvent event = new OutboxEvent(
                    eventId,
                    aggregateType,
                    aggregateId,
                    merchantId,
                    eventType,
                    correlationId,
                    payloadJson
            );
            return outboxEventRepository.save(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar envelope Outbox para JSON.", ex);
        }
    }
}

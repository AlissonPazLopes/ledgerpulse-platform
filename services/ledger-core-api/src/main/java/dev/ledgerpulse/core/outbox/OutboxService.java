package dev.ledgerpulse.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.stream.DomainStreamEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this(outboxEventRepository, objectMapper, null);
    }

    @Autowired
    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
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
            OutboxEvent saved = outboxEventRepository.save(event);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new DomainStreamEvent(envelope));
            }
            return saved;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar envelope Outbox para JSON.", ex);
        }
    }
}

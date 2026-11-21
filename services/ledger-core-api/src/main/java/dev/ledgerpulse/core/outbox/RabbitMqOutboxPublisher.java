package dev.ledgerpulse.core.outbox;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqOutboxPublisher implements OutboxEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public RabbitMqOutboxPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.outbox.exchange:ledger.events}") String exchangeName
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    @Override
    public void publish(OutboxEvent event) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(event.getId().toString());
        props.setHeader("X-Correlation-Id", event.getCorrelationId());

        Message message = new Message(event.getPayloadJson().getBytes(StandardCharsets.UTF_8), props);
        rabbitTemplate.send(exchangeName, event.getEventType(), message);
    }
}

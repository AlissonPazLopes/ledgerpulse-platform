package dev.ledgerpulse.core.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqOutboxConfig {

    @Bean
    public TopicExchange ledgerEventsExchange(
            @Value("${app.outbox.exchange:ledger.events}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }
}

package dev.ledgerpulse.webhook.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange ledgerEventsExchange() {
        return new TopicExchange(RabbitTopologyConstants.LEDGER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ledgerRetryExchange() {
        return new DirectExchange(RabbitTopologyConstants.LEDGER_RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ledgerDeadLetterExchange() {
        return new DirectExchange(RabbitTopologyConstants.LEDGER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue webhooksDispatchQueue() {
        return QueueBuilder.durable(RabbitTopologyConstants.DISPATCH_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitTopologyConstants.LEDGER_RETRY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitTopologyConstants.RETRY_5S_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue retry5sQueue() {
        return buildRetryQueue(RabbitTopologyConstants.RETRY_5S_QUEUE, RabbitTopologyConstants.RETRY_5S_TTL_MS);
    }

    @Bean
    public Queue retry30sQueue() {
        return buildRetryQueue(RabbitTopologyConstants.RETRY_30S_QUEUE, RabbitTopologyConstants.RETRY_30S_TTL_MS);
    }

    @Bean
    public Queue retry120sQueue() {
        return buildRetryQueue(RabbitTopologyConstants.RETRY_120S_QUEUE, RabbitTopologyConstants.RETRY_120S_TTL_MS);
    }

    @Bean
    public Queue webhooksDlqQueue() {
        return QueueBuilder.durable(RabbitTopologyConstants.DLQ_QUEUE).build();
    }

    @Bean
    public Binding webhooksDispatchBinding(Queue webhooksDispatchQueue, TopicExchange ledgerEventsExchange) {
        return BindingBuilder.bind(webhooksDispatchQueue)
                .to(ledgerEventsExchange)
                .with(RabbitTopologyConstants.EVENTS_BINDING_KEY);
    }

    @Bean
    public Binding retry5sBinding(Queue retry5sQueue, DirectExchange ledgerRetryExchange) {
        return BindingBuilder.bind(retry5sQueue)
                .to(ledgerRetryExchange)
                .with(RabbitTopologyConstants.RETRY_5S_ROUTING_KEY);
    }

    @Bean
    public Binding retry30sBinding(Queue retry30sQueue, DirectExchange ledgerRetryExchange) {
        return BindingBuilder.bind(retry30sQueue)
                .to(ledgerRetryExchange)
                .with(RabbitTopologyConstants.RETRY_30S_ROUTING_KEY);
    }

    @Bean
    public Binding retry120sBinding(Queue retry120sQueue, DirectExchange ledgerRetryExchange) {
        return BindingBuilder.bind(retry120sQueue)
                .to(ledgerRetryExchange)
                .with(RabbitTopologyConstants.RETRY_120S_ROUTING_KEY);
    }

    @Bean
    public Binding webhooksDlqBinding(Queue webhooksDlqQueue, DirectExchange ledgerDeadLetterExchange) {
        return BindingBuilder.bind(webhooksDlqQueue)
                .to(ledgerDeadLetterExchange)
                .with(RabbitTopologyConstants.DLQ_ROUTING_KEY);
    }

    private Queue buildRetryQueue(String queueName, long ttlMs) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttlMs)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", RabbitTopologyConstants.DISPATCH_QUEUE)
                .build();
    }
}

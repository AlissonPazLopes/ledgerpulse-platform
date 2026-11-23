package dev.ledgerpulse.webhook.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.service.WebhookDeliveryEngine;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitTopologyAndRetryPolicyTest {

    private final RabbitTopologyConfig topologyConfig = new RabbitTopologyConfig();
    private final RetryEscalationPolicy policy = new RetryEscalationPolicy();

    @Test
    void shouldDeclareExchangesRetryQueuesWithTtlAndDlqBindings() {
        TopicExchange eventsExchange = topologyConfig.ledgerEventsExchange();
        DirectExchange retryExchange = topologyConfig.ledgerRetryExchange();
        DirectExchange dlxExchange = topologyConfig.ledgerDeadLetterExchange();

        assertThat(eventsExchange.getName()).isEqualTo("ledger.events");
        assertThat(eventsExchange.isDurable()).isTrue();
        assertThat(retryExchange.getName()).isEqualTo("ledger.retry");
        assertThat(dlxExchange.getName()).isEqualTo("ledger.dlx");

        Queue dispatch = topologyConfig.webhooksDispatchQueue();
        assertThat(dispatch.getName()).isEqualTo("ledger.webhooks.dispatch");
        assertThat(dispatch.getArguments())
                .containsEntry("x-dead-letter-exchange", "ledger.retry")
                .containsEntry("x-dead-letter-routing-key", "retry.5s");

        Queue retry5s = topologyConfig.retry5sQueue();
        Queue retry30s = topologyConfig.retry30sQueue();
        Queue retry120s = topologyConfig.retry120sQueue();
        Queue dlq = topologyConfig.webhooksDlqQueue();

        assertThat(retry5s.getArguments())
                .containsEntry("x-message-ttl", 5_000L)
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "ledger.webhooks.dispatch");
        assertThat(retry30s.getArguments()).containsEntry("x-message-ttl", 30_000L);
        assertThat(retry120s.getArguments()).containsEntry("x-message-ttl", 120_000L);
        assertThat(dlq.getName()).isEqualTo("ledger.webhooks.dlq");

        Binding dispatchBinding = topologyConfig.webhooksDispatchBinding(dispatch, eventsExchange);
        Binding retry5sBinding = topologyConfig.retry5sBinding(retry5s, retryExchange);
        Binding retry30sBinding = topologyConfig.retry30sBinding(retry30s, retryExchange);
        Binding retry120sBinding = topologyConfig.retry120sBinding(retry120s, retryExchange);
        Binding dlqBinding = topologyConfig.webhooksDlqBinding(dlq, dlxExchange);

        assertThat(dispatchBinding.getRoutingKey()).isEqualTo("#");
        assertThat(retry5sBinding.getRoutingKey()).isEqualTo("retry.5s");
        assertThat(retry30sBinding.getRoutingKey()).isEqualTo("retry.30s");
        assertThat(retry120sBinding.getRoutingKey()).isEqualTo("retry.120s");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("webhooks.dlq");
    }

    @Test
    void shouldEscalateRetriesFrom0To5sTo30sTo120sAndFinallyDlq() {
        var step0 = policy.decide(null);
        assertThat(step0.target()).isEqualTo(RetryEscalationPolicy.Target.RETRY);
        assertThat(step0.exchange()).isEqualTo("ledger.retry");
        assertThat(step0.routingKey()).isEqualTo("retry.5s");

        var step1 = policy.decide(List.of(
                Map.of("queue", "ledger.retry.5s", "count", 1L),
                Map.of("queue", "other.unrelated.queue", "count", 99L)
        ));
        assertThat(step1.target()).isEqualTo(RetryEscalationPolicy.Target.RETRY);
        assertThat(step1.routingKey()).isEqualTo("retry.30s");

        var step2 = policy.decide(List.of(
                Map.of("queue", "ledger.retry.30s", "count", 1L),
                Map.of("queue", "ledger.retry.5s", "count", 1L)
        ));
        assertThat(step2.target()).isEqualTo(RetryEscalationPolicy.Target.RETRY);
        assertThat(step2.routingKey()).isEqualTo("retry.120s");

        var step3 = policy.decide(List.of(
                Map.of("queue", "ledger.retry.120s", "count", 1L),
                Map.of("queue", "ledger.retry.30s", "count", 1L),
                Map.of("queue", "ledger.retry.5s", "count", 1L)
        ));
        assertThat(step3.isDlq()).isTrue();
        assertThat(step3.exchange()).isEqualTo("ledger.dlx");
        assertThat(step3.routingKey()).isEqualTo("webhooks.dlq");
    }

    @Test
    void shouldForwardRecoverableFailuresThroughRetryAndDlqWithManualAck() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        WebhookDeliveryEngine deliveryEngine = mock(WebhookDeliveryEngine.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        WebhookEventConsumer consumer = new WebhookEventConsumer(mapper, deliveryEngine, policy, rabbitTemplate);

        UUID eventId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        WebhookEventMessage event = new WebhookEventMessage(
                eventId,
                "payment.settled",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                merchantId,
                Map.of("amountCents", 15000)
        );
        byte[] jsonBytes = mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);

        // Attempt 1 -> 500 -> retry.5s
        MessageProperties props1 = new MessageProperties();
        props1.setDeliveryTag(101L);
        when(deliveryEngine.dispatchEvent(any(), any(), eq(false))).thenReturn(List.of(
                new WebhookDeliveryEngine.DispatchResult(
                        UUID.randomUUID(), UUID.randomUUID(), eventId,
                        WebhookDeliveryStatus.RETRYING, 1, 500, 12L, "HTTP 500", false, true
                )
        ));

        consumer.onMessage(new Message(jsonBytes, props1), channel);
        verify(rabbitTemplate).send(eq("ledger.retry"), eq("retry.5s"), any(Message.class));
        verify(channel).basicAck(101L, false);

        // Attempt 4 (x-death sum == 3) -> DLQ
        MessageProperties props4 = new MessageProperties();
        props4.setDeliveryTag(104L);
        props4.setHeader("x-death", List.of(
                Map.of("queue", "ledger.retry.5s", "count", 1L),
                Map.of("queue", "ledger.retry.30s", "count", 1L),
                Map.of("queue", "ledger.retry.120s", "count", 1L)
        ));
        when(deliveryEngine.dispatchEvent(any(), any(), eq(true))).thenReturn(List.of(
                new WebhookDeliveryEngine.DispatchResult(
                        UUID.randomUUID(), UUID.randomUUID(), eventId,
                        WebhookDeliveryStatus.DLQ, 4, 500, 15L, "HTTP 500", false, true
                )
        ));

        consumer.onMessage(new Message(jsonBytes, props4), channel);
        verify(rabbitTemplate).send(eq("ledger.dlx"), eq("webhooks.dlq"), any(Message.class));
        verify(channel).basicAck(104L, false);

        // Success -> no retry/dlq publish, ack only
        RabbitTemplate cleanRabbit = mock(RabbitTemplate.class);
        Channel cleanChannel = mock(Channel.class);
        WebhookEventConsumer cleanConsumer = new WebhookEventConsumer(mapper, deliveryEngine, policy, cleanRabbit);
        MessageProperties okProps = new MessageProperties();
        okProps.setDeliveryTag(200L);
        when(deliveryEngine.dispatchEvent(any(), any(), anyBoolean())).thenReturn(List.of(
                new WebhookDeliveryEngine.DispatchResult(
                        UUID.randomUUID(), UUID.randomUUID(), eventId,
                        WebhookDeliveryStatus.DELIVERED, 1, 200, 5L, null, false, true
                )
        ));

        cleanConsumer.onMessage(new Message(jsonBytes, okProps), cleanChannel);
        verify(cleanRabbit, never()).send(any(), any(), any(Message.class));
        verify(cleanChannel).basicAck(200L, false);
    }
}

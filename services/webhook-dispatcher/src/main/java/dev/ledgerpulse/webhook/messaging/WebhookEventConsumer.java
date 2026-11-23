package dev.ledgerpulse.webhook.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.service.WebhookDeliveryEngine;
import dev.ledgerpulse.webhook.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class WebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final WebhookDeliveryEngine deliveryEngine;
    private final RetryEscalationPolicy retryEscalationPolicy;
    private final RabbitTemplate rabbitTemplate;

    public WebhookEventConsumer(
            ObjectMapper objectMapper,
            WebhookDeliveryEngine deliveryEngine,
            RetryEscalationPolicy retryEscalationPolicy,
            RabbitTemplate rabbitTemplate
    ) {
        this.objectMapper = objectMapper;
        this.deliveryEngine = deliveryEngine;
        this.retryEscalationPolicy = retryEscalationPolicy;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitTopologyConstants.DISPATCH_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String rawBody = new String(message.getBody(), StandardCharsets.UTF_8);

        WebhookEventMessage event;
        try {
            event = objectMapper.readValue(rawBody, WebhookEventMessage.class);
        } catch (Exception ex) {
            log.error("Payload inválido em {}: encaminhando para DLQ: {}", RabbitTopologyConstants.DISPATCH_QUEUE, ex.getMessage());
            rabbitTemplate.send(
                    RabbitTopologyConstants.LEDGER_DLX_EXCHANGE,
                    RabbitTopologyConstants.DLQ_ROUTING_KEY,
                    copyForForwarding(message)
            );
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (event == null || event.eventId() == null || event.merchantId() == null || !StringUtils.hasText(event.eventType())) {
            log.error("Evento incompleto recebido em {}: encaminhando para DLQ", RabbitTopologyConstants.DISPATCH_QUEUE);
            rabbitTemplate.send(
                    RabbitTopologyConstants.LEDGER_DLX_EXCHANGE,
                    RabbitTopologyConstants.DLQ_ROUTING_KEY,
                    copyForForwarding(message)
            );
            channel.basicAck(deliveryTag, false);
            return;
        }

        String correlationId = StringUtils.hasText(event.correlationId())
                ? event.correlationId()
                : UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
        try {
            var xDeath = message.getMessageProperties().getXDeathHeader();
            RetryEscalationPolicy.RouteDecision routeDecision = retryEscalationPolicy.decide(xDeath);

            List<WebhookDeliveryEngine.DispatchResult> results = deliveryEngine.dispatchEvent(
                    event,
                    rawBody,
                    routeDecision.isDlq()
            );

            boolean needsRetry = results.stream().anyMatch(r -> r.status() == WebhookDeliveryStatus.RETRYING);
            boolean sentToDlq = results.stream().anyMatch(r -> r.status() == WebhookDeliveryStatus.DLQ);

            if (needsRetry && !routeDecision.isDlq()) {
                log.warn("Agendando retry de webhook eventId={} via {} ({})",
                        event.eventId(), routeDecision.exchange(), routeDecision.routingKey());
                rabbitTemplate.send(
                        routeDecision.exchange(),
                        routeDecision.routingKey(),
                        copyForForwarding(message)
                );
            } else if (sentToDlq || (needsRetry && routeDecision.isDlq())) {
                log.error("Tentativas esgotadas ou erro irreversível para eventId={}: encaminhando para DLQ", event.eventId());
                rabbitTemplate.send(
                        RabbitTopologyConstants.LEDGER_DLX_EXCHANGE,
                        RabbitTopologyConstants.DLQ_ROUTING_KEY,
                        copyForForwarding(message)
                );
            }

            channel.basicAck(deliveryTag, false);
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }
    }

    private Message copyForForwarding(Message original) {
        MessageProperties props = new MessageProperties();
        MessageProperties orig = original.getMessageProperties();
        if (orig != null) {
            props.setContentType(orig.getContentType() != null ? orig.getContentType() : MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(orig.getContentEncoding() != null ? orig.getContentEncoding() : StandardCharsets.UTF_8.name());
            props.setMessageId(orig.getMessageId());
            props.setCorrelationId(orig.getCorrelationId());
            if (orig.getHeaders() != null) {
                props.getHeaders().putAll(orig.getHeaders());
            }
        } else {
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());
        }
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(original.getBody(), props);
    }
}

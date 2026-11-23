package dev.ledgerpulse.webhook.repository;

import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID>, JpaSpecificationExecutor<WebhookEndpoint> {

    List<WebhookEndpoint> findByMerchantIdAndStatus(UUID merchantId, WebhookEndpointStatus status);
}

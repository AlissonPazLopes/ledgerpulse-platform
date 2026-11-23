package dev.ledgerpulse.webhook.repository;

import dev.ledgerpulse.webhook.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID>, JpaSpecificationExecutor<WebhookDelivery> {

    Optional<WebhookDelivery> findByEventIdAndEndpointId(UUID eventId, UUID endpointId);
}

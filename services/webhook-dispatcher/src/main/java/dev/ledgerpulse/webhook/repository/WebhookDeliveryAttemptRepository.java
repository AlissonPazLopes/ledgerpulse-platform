package dev.ledgerpulse.webhook.repository;

import dev.ledgerpulse.webhook.domain.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {

    List<WebhookDeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);

    List<WebhookDeliveryAttempt> findByDeliveryIdInOrderByAttemptNumberAsc(Collection<UUID> deliveryIds);
}

package dev.ledgerpulse.core.risk.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskReviewDecisionRepository extends JpaRepository<RiskReviewDecision, UUID> {

    Optional<RiskReviewDecision> findByPaymentId(UUID paymentId);
}

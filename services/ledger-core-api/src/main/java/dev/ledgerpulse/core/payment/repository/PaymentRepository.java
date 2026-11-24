package dev.ledgerpulse.core.payment.repository;

import dev.ledgerpulse.core.payment.domain.Payment;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    List<Payment> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(Instant since);

    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amountCents), 0) FROM Payment p GROUP BY p.status")
    List<Object[]> summarizeByStatus();

    @Query("SELECT COALESCE(SUM(p.riskScore), 0), COUNT(p) FROM Payment p WHERE p.riskScore IS NOT NULL")
    List<Object[]> summarizeRiskScores();

    @Query("SELECT COALESCE(SUM(p.amountCents), 0) FROM Payment p WHERE p.status IN :statuses")
    long sumAmountCentsByStatusIn(@Param("statuses") Collection<PaymentStatus> statuses);
}

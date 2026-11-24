package dev.ledgerpulse.core.refund.repository;

import dev.ledgerpulse.core.refund.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtAscIdAsc(UUID paymentId);

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDescIdAsc(UUID paymentId);

    @Query("SELECT COALESCE(SUM(r.amountCents), 0) FROM PaymentRefund r")
    long sumAllRefundedCents();
}

package dev.ledgerpulse.core.risk.review;

import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.observability.LedgerCoreMetrics;
import dev.ledgerpulse.core.outbox.OutboxService;
import dev.ledgerpulse.core.payment.domain.Payment;
import dev.ledgerpulse.core.payment.domain.PaymentSplit;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.payment.repository.PaymentSplitRepository;
import dev.ledgerpulse.core.payment.service.PaymentSettlementHelper;
import dev.ledgerpulse.core.security.AuthenticatedOperator;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.InvalidPaymentStateException;
import dev.ledgerpulse.core.web.PageResponse;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskReviewService {

    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final RiskReviewDecisionRepository decisionRepository;
    private final PaymentSettlementHelper settlementHelper;
    private final OutboxService outboxService;
    private final LedgerCoreMetrics metrics;

    public RiskReviewService(
            PaymentRepository paymentRepository,
            PaymentSplitRepository paymentSplitRepository,
            RiskReviewDecisionRepository decisionRepository,
            PaymentSettlementHelper settlementHelper,
            OutboxService outboxService,
            LedgerCoreMetrics metrics
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.decisionRepository = decisionRepository;
        this.settlementHelper = settlementHelper;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskReviewItemResponse> listPendingReviews(
            UUID merchantId,
            Integer minRiskScore,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), PaymentStatus.MANUAL_REVIEW));
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (minRiskScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("riskScore"), minRiskScore));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<Payment> result = paymentRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toReviewItem);
    }

    @Transactional
    public RiskReviewDecisionResponse submitDecision(
            UUID paymentId,
            RiskReviewDecisionRequest request,
            AuthenticatedOperator reviewer,
            String correlationId
    ) {
        String trimmedJustification = request.justification().trim();
        if (trimmedJustification.length() < 5 || trimmedJustification.length() > 500) {
            throw new DomainValidationException("justification deve conter entre 5 e 500 caracteres úteis.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + paymentId));

        if (payment.getStatus() != PaymentStatus.MANUAL_REVIEW) {
            throw new InvalidPaymentStateException(
                    "Pagamento " + paymentId + " não está em MANUAL_REVIEW (status atual: " + payment.getStatus() + ")."
            );
        }

        PaymentStatus previousStatus = payment.getStatus();
        List<String> reasonCodes = settlementHelper.deserializeReasonCodes(payment.getReasonCodesJson());

        if (request.decision() == RiskReviewDecisionType.APPROVE) {
            payment.transitionTo(PaymentStatus.AUTHORIZED);
            List<PaymentSplit> splits = paymentSplitRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
            LedgerTransaction ledgerTx = settlementHelper.postSettlement(
                    payment,
                    settlementHelper.fromEntities(splits),
                    correlationId
            );
            payment.attachLedgerTransaction(ledgerTx.getId());
            payment.transitionTo(PaymentStatus.SETTLED);
        } else {
            payment.transitionTo(PaymentStatus.DECLINED);
        }

        Payment savedPayment = paymentRepository.save(payment);
        settlementHelper.recordPaymentOutboxEvent(savedPayment, reasonCodes, correlationId);

        RiskReviewDecision decisionRecord = decisionRepository.save(new RiskReviewDecision(
                UUID.randomUUID(),
                savedPayment.getId(),
                reviewer.id(),
                reviewer.email(),
                request.decision(),
                trimmedJustification,
                previousStatus,
                savedPayment.getStatus(),
                correlationId
        ));

        recordRiskFeedbackOutbox(savedPayment, decisionRecord, reasonCodes, correlationId);
        metrics.recordPaymentProcessed(
                savedPayment.getStatus(),
                savedPayment.getPaymentMethod(),
                savedPayment.getRiskDecision()
        );

        return new RiskReviewDecisionResponse(
                decisionRecord.getId(),
                savedPayment.getId(),
                decisionRecord.getDecision(),
                savedPayment.getStatus(),
                savedPayment.getLedgerTransactionId(),
                decisionRecord.getJustification(),
                decisionRecord.getReviewerUserId(),
                decisionRecord.getReviewerEmail(),
                decisionRecord.getCreatedAt()
        );
    }

    private void recordRiskFeedbackOutbox(
            Payment payment,
            RiskReviewDecision decision,
            List<String> reasonCodes,
            String correlationId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("decisionId", decision.getId());
        payload.put("decision", decision.getDecision().name());
        payload.put("riskScore", payment.getRiskScore());
        payload.put("previousStatus", decision.getPreviousStatus().name());
        payload.put("resultingStatus", decision.getResultingStatus().name());
        payload.put("reasonCodes", reasonCodes);
        payload.put("reviewedByUserId", decision.getReviewerUserId());
        payload.put("reviewedByEmail", decision.getReviewerEmail());
        payload.put("justification", decision.getJustification());

        outboxService.recordEvent(
                "PAYMENT",
                payment.getId(),
                payment.getMerchantId(),
                "risk.feedback.recorded",
                correlationId,
                payload
        );
    }

    private RiskReviewItemResponse toReviewItem(Payment payment) {
        return new RiskReviewItemResponse(
                payment.getId(),
                payment.getMerchantId(),
                payment.getPayerId(),
                payment.getAmountCents(),
                payment.getPlatformFeeCents(),
                payment.getNetMerchantCents(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getRiskScore(),
                payment.getRiskDecision(),
                settlementHelper.deserializeReasonCodes(payment.getReasonCodesJson()),
                payment.getCorrelationId(),
                payment.getCreatedAt()
        );
    }
}

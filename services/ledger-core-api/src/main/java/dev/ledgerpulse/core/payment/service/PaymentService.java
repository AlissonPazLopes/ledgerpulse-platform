package dev.ledgerpulse.core.payment.service;

import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.idempotency.IdempotencyService;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.observability.LedgerCoreMetrics;
import dev.ledgerpulse.core.payment.api.CreatePaymentRequest;
import dev.ledgerpulse.core.payment.api.PaymentResponse;
import dev.ledgerpulse.core.payment.api.PaymentSplitRequest;
import dev.ledgerpulse.core.payment.api.PaymentSplitResponse;
import dev.ledgerpulse.core.payment.domain.Payment;
import dev.ledgerpulse.core.payment.domain.PaymentSplit;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.RiskDecision;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.payment.repository.PaymentSplitRepository;
import dev.ledgerpulse.core.security.AuthenticatedMerchant;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.PageResponse;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final PaymentSettlementHelper settlementHelper;
    private final RiskEvaluator riskEvaluator;
    private final SplitCalculator splitCalculator;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final LedgerCoreMetrics metrics;

    public PaymentService(
            MerchantRepository merchantRepository,
            AccountRepository accountRepository,
            PaymentRepository paymentRepository,
            PaymentSplitRepository paymentSplitRepository,
            PaymentSettlementHelper settlementHelper,
            RiskEvaluator riskEvaluator,
            SplitCalculator splitCalculator,
            IdempotencyService idempotencyService,
            TransactionTemplate transactionTemplate,
            LedgerCoreMetrics metrics
    ) {
        this.merchantRepository = merchantRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.settlementHelper = settlementHelper;
        this.riskEvaluator = riskEvaluator;
        this.splitCalculator = splitCalculator;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public IdempotencyService.IdempotencyExecution<PaymentResponse> createPayment(
            CreatePaymentRequest request,
            String idempotencyKey,
            String correlationId,
            Authentication authentication
    ) {
        UUID merchantId = resolveMerchantId(request.merchantId(), authentication);
        CreatePaymentRequest canonicalRequest = new CreatePaymentRequest(
                merchantId,
                request.payerId().trim(),
                request.amountCents(),
                request.currency(),
                request.paymentMethod(),
                request.description(),
                request.splits() == null ? List.of() : request.splits()
        );

        return idempotencyService.execute(
                merchantId,
                idempotencyKey,
                canonicalRequest,
                201,
                PaymentResponse.class,
                () -> transactionTemplate.execute(
                        status -> executePaymentTransaction(canonicalRequest, merchantId, idempotencyKey.trim(), correlationId)
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listPayments(
            PaymentStatus status,
            UUID merchantId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
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

        List<UUID> paymentIds = result.getContent().stream().map(Payment::getId).toList();
        Map<UUID, List<PaymentSplit>> splitsByPaymentId = paymentIds.isEmpty()
                ? Map.of()
                : paymentSplitRepository.findByPaymentIdInOrderByCreatedAtAscIdAsc(paymentIds).stream()
                        .collect(Collectors.groupingBy(PaymentSplit::getPaymentId));

        return PageResponse.from(result, p -> toResponse(p, splitsByPaymentId.getOrDefault(p.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId, Authentication authentication) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + paymentId));

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedMerchant merchant
                && !payment.getMerchantId().equals(merchant.id())) {
            throw new AccessDeniedException("Merchant autenticado não tem acesso a pagamentos de outro merchant.");
        }

        List<PaymentSplit> splits = paymentSplitRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        return toResponse(payment, splits);
    }

    private PaymentResponse executePaymentTransaction(
            CreatePaymentRequest request,
            UUID merchantId,
            String idempotencyKey,
            String correlationId
    ) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant não encontrado: " + merchantId));
        if (!merchant.isActive()) {
            throw new DomainValidationException("Merchant " + merchantId + " não está ACTIVE.");
        }
        if (!"BRL".equals(request.currency())) {
            throw new DomainValidationException("Moeda não suportada: apenas BRL é aceita no v1.");
        }

        List<UUID> recipientIds = request.splits().stream()
                .map(PaymentSplitRequest::recipientAccountId)
                .distinct()
                .toList();
        Map<UUID, Account> recipientAccounts = recipientIds.isEmpty()
                ? Map.of()
                : accountRepository.findAllById(recipientIds).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity()));

        SplitCalculator.SplitCalculationResult splitResult = splitCalculator.calculate(
                request.amountCents(),
                merchant.getDefaultFeeBps(),
                request.currency(),
                request.splits(),
                recipientAccounts
        );

        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                merchant.getId(),
                request.payerId(),
                request.amountCents(),
                splitResult.platformFeeCents(),
                splitResult.netMerchantCents(),
                request.currency(),
                request.paymentMethod(),
                request.description(),
                idempotencyKey,
                correlationId
        );
        payment.transitionTo(PaymentStatus.RISK_ANALYSIS);

        RiskEvaluator.RiskAssessment risk = metrics.recordRiskEvaluation(() ->
                riskEvaluator.evaluate(new RiskEvaluator.RiskEvaluationInput(
                        paymentId,
                        merchant.getId(),
                        request.payerId(),
                        request.amountCents(),
                        request.paymentMethod()
                ))
        );
        payment.recordRiskAssessment(
                risk.riskScore(),
                risk.decision(),
                settlementHelper.serializeReasonCodes(risk.reasonCodes())
        );

        if (risk.decision() == RiskDecision.APPROVE) {
            payment.transitionTo(PaymentStatus.AUTHORIZED);
            List<PaymentSettlementHelper.SplitLine> splitLines = splitResult.splits().stream()
                    .map(s -> new PaymentSettlementHelper.SplitLine(s.recipientAccountId(), s.amountCents()))
                    .toList();
            LedgerTransaction ledgerTx = settlementHelper.postSettlement(payment, splitLines, correlationId);
            payment.attachLedgerTransaction(ledgerTx.getId());
            payment.transitionTo(PaymentStatus.SETTLED);
        } else if (risk.decision() == RiskDecision.REVIEW) {
            payment.transitionTo(PaymentStatus.MANUAL_REVIEW);
        } else {
            payment.transitionTo(PaymentStatus.DECLINED);
        }

        Payment savedPayment = paymentRepository.save(payment);

        List<PaymentSplit> savedSplits = splitResult.splits().isEmpty()
                ? List.of()
                : paymentSplitRepository.saveAll(
                        splitResult.splits().stream()
                                .map(item -> new PaymentSplit(
                                        UUID.randomUUID(),
                                        savedPayment.getId(),
                                        item.recipientAccountId(),
                                        item.splitType(),
                                        item.percentageBps(),
                                        item.amountCents()
                                ))
                                .toList()
                );

        settlementHelper.recordPaymentOutboxEvent(savedPayment, risk.reasonCodes(), savedPayment.getCorrelationId());
        metrics.recordPaymentProcessed(savedPayment.getStatus(), savedPayment.getPaymentMethod(), risk.decision());
        return toResponse(savedPayment, savedSplits);
    }

    private UUID resolveMerchantId(UUID requestedMerchantId, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedMerchant merchant) {
            if (requestedMerchantId != null && !requestedMerchantId.equals(merchant.id())) {
                throw new AccessDeniedException("merchantId do corpo diverge do merchant autenticado na X-API-Key.");
            }
            return merchant.id();
        }
        if (requestedMerchantId == null) {
            throw new DomainValidationException("merchantId é obrigatório quando autenticado via JWT.");
        }
        return requestedMerchantId;
    }

    private PaymentResponse toResponse(Payment payment, List<PaymentSplit> splits) {
        return new PaymentResponse(
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
                payment.getLedgerTransactionId(),
                payment.getIdempotencyKey(),
                payment.getCorrelationId(),
                splits.stream().map(PaymentSplitResponse::from).toList(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

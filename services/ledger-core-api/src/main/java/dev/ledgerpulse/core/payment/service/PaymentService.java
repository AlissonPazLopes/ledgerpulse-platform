package dev.ledgerpulse.core.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.idempotency.IdempotencyService;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.service.LedgerPostingCommand;
import dev.ledgerpulse.core.ledger.service.LedgerService;
import dev.ledgerpulse.core.outbox.OutboxService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final RiskEvaluator riskEvaluator;
    private final SplitCalculator splitCalculator;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(
            MerchantRepository merchantRepository,
            AccountRepository accountRepository,
            PaymentRepository paymentRepository,
            PaymentSplitRepository paymentSplitRepository,
            LedgerService ledgerService,
            OutboxService outboxService,
            RiskEvaluator riskEvaluator,
            SplitCalculator splitCalculator,
            IdempotencyService idempotencyService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.merchantRepository = merchantRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.ledgerService = ledgerService;
        this.outboxService = outboxService;
        this.riskEvaluator = riskEvaluator;
        this.splitCalculator = splitCalculator;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
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

        RiskEvaluator.RiskAssessment risk = riskEvaluator.evaluate(new RiskEvaluator.RiskEvaluationInput(
                paymentId,
                merchant.getId(),
                request.payerId(),
                request.amountCents(),
                request.paymentMethod()
        ));
        payment.recordRiskAssessment(risk.riskScore(), risk.decision(), serializeReasonCodes(risk.reasonCodes()));

        if (risk.decision() == RiskDecision.APPROVE) {
            payment.transitionTo(PaymentStatus.AUTHORIZED);
            LedgerTransaction ledgerTx = postSettlementToLedger(payment, splitResult, correlationId);
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

        recordOutboxEvent(savedPayment, risk.reasonCodes());
        return toResponse(savedPayment, savedSplits);
    }

    private LedgerTransaction postSettlementToLedger(
            Payment payment,
            SplitCalculator.SplitCalculationResult splitResult,
            String correlationId
    ) {
        Account escrowAccount = accountRepository
                .findFirstByAccountTypeAndStatusOrderByCreatedAtAsc(AccountType.ESCROW, AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Conta sistêmica ESCROW ativa não encontrada."));
        Account platformFeeAccount = accountRepository
                .findFirstByAccountTypeAndStatusOrderByCreatedAtAsc(AccountType.PLATFORM_FEE, AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Conta sistêmica PLATFORM_FEE ativa não encontrada."));
        Account merchantAccount = accountRepository
                .findFirstByMerchantIdAndAccountTypeAndStatusOrderByCreatedAtAsc(
                        payment.getMerchantId(),
                        AccountType.MERCHANT,
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conta MERCHANT ativa não encontrada para merchant: " + payment.getMerchantId()
                ));

        List<LedgerPostingCommand.LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerPostingCommand.LedgerLeg(
                escrowAccount.getId(),
                EntryDirection.DEBIT,
                payment.getAmountCents()
        ));

        if (splitResult.platformFeeCents() > 0) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    platformFeeAccount.getId(),
                    EntryDirection.CREDIT,
                    splitResult.platformFeeCents()
            ));
        }
        if (splitResult.netMerchantCents() > 0) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    merchantAccount.getId(),
                    EntryDirection.CREDIT,
                    splitResult.netMerchantCents()
            ));
        }
        for (SplitCalculator.CalculatedSplit split : splitResult.splits()) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    split.recipientAccountId(),
                    EntryDirection.CREDIT,
                    split.amountCents()
            ));
        }

        String description = payment.getDescription() != null && !payment.getDescription().isBlank()
                ? "Liquidação: " + payment.getDescription()
                : "Liquidação de pagamento com split de marketplace";

        return ledgerService.postTransaction(new LedgerPostingCommand(
                LedgerReferenceType.PAYMENT_SETTLEMENT,
                payment.getId(),
                description,
                payment.getCurrency(),
                correlationId,
                legs
        ));
    }

    private void recordOutboxEvent(Payment payment, List<String> reasonCodes) {
        String eventType = switch (payment.getStatus()) {
            case SETTLED -> "payment.settled";
            case MANUAL_REVIEW -> "payment.manual_review_required";
            case DECLINED -> "payment.declined";
            default -> "payment.created";
        };

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("ledgerTransactionId", payment.getLedgerTransactionId());
        payload.put("amountCents", payment.getAmountCents());
        payload.put("platformFeeCents", payment.getPlatformFeeCents());
        payload.put("netMerchantCents", payment.getNetMerchantCents());
        payload.put("currency", payment.getCurrency());
        payload.put("status", payment.getStatus().name());
        payload.put("riskScore", payment.getRiskScore());
        payload.put("reasonCodes", reasonCodes);

        outboxService.recordEvent(
                "PAYMENT",
                payment.getId(),
                payment.getMerchantId(),
                eventType,
                payment.getCorrelationId(),
                payload
        );
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

    private String serializeReasonCodes(List<String> reasonCodes) {
        try {
            return objectMapper.writeValueAsString(reasonCodes == null ? List.of() : reasonCodes);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar reasonCodes.", ex);
        }
    }

    private List<String> deserializeReasonCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar reasonCodes.", ex);
        }
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
                deserializeReasonCodes(payment.getReasonCodesJson()),
                payment.getLedgerTransactionId(),
                payment.getIdempotencyKey(),
                payment.getCorrelationId(),
                splits.stream().map(PaymentSplitResponse::from).toList(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

package dev.ledgerpulse.core.refund.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.ledgerpulse.core.payment.domain.Payment;
import dev.ledgerpulse.core.payment.domain.PaymentSplit;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.payment.repository.PaymentSplitRepository;
import dev.ledgerpulse.core.refund.api.CreateRefundRequest;
import dev.ledgerpulse.core.refund.api.RecipientReversalDto;
import dev.ledgerpulse.core.refund.api.RefundListResponse;
import dev.ledgerpulse.core.refund.api.RefundResponse;
import dev.ledgerpulse.core.refund.domain.PaymentRefund;
import dev.ledgerpulse.core.refund.repository.PaymentRefundRepository;
import dev.ledgerpulse.core.security.AuthenticatedMerchant;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.InvalidPaymentStateException;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundService {

    private static final TypeReference<List<RecipientReversalDto>> REVERSAL_LIST_TYPE = new TypeReference<>() {
    };

    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final RefundCalculator refundCalculator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public RefundService(
            PaymentRepository paymentRepository,
            PaymentSplitRepository paymentSplitRepository,
            PaymentRefundRepository paymentRefundRepository,
            AccountRepository accountRepository,
            LedgerService ledgerService,
            OutboxService outboxService,
            IdempotencyService idempotencyService,
            RefundCalculator refundCalculator,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.outboxService = outboxService;
        this.idempotencyService = idempotencyService;
        this.refundCalculator = refundCalculator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public IdempotencyService.IdempotencyExecution<RefundResponse> createRefund(
            UUID paymentId,
            CreateRefundRequest request,
            String idempotencyKey,
            String correlationId,
            Authentication authentication
    ) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 128) {
            throw new DomainValidationException("Header Idempotency-Key é obrigatório e deve ter entre 1 e 128 caracteres.");
        }
        String trimmedReason = request.reason() == null ? "" : request.reason().trim();
        if (trimmedReason.length() < 3 || trimmedReason.length() > 255) {
            throw new DomainValidationException("reason deve ter entre 3 e 255 caracteres úteis.");
        }

        Payment payment = loadAuthorizedPayment(paymentId, authentication);
        CreateRefundRequest canonicalRequest = new CreateRefundRequest(request.amountCents(), trimmedReason);

        return idempotencyService.executeScoped(
                "refund:" + paymentId,
                payment.getMerchantId(),
                idempotencyKey,
                canonicalRequest,
                201,
                RefundResponse.class,
                () -> transactionTemplate.execute(status ->
                        executeRefundTransaction(paymentId, canonicalRequest, idempotencyKey.trim(), correlationId)
                )
        );
    }

    @Transactional(readOnly = true)
    public RefundListResponse listRefunds(UUID paymentId, Authentication authentication) {
        Payment payment = loadAuthorizedPayment(paymentId, authentication);
        List<PaymentRefund> chronological = paymentRefundRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);

        List<RefundResponse> responses = new ArrayList<>(chronological.size());
        long runningTotal = 0L;
        for (PaymentRefund refund : chronological) {
            runningTotal = Math.addExact(runningTotal, refund.getAmountCents());
            long remaining = Math.max(0L, payment.getAmountCents() - runningTotal);
            PaymentStatus statusAtStep = remaining == 0L ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            responses.add(new RefundResponse(
                    refund.getId(),
                    refund.getPaymentId(),
                    refund.getLedgerTransactionId(),
                    refund.getAmountCents(),
                    refund.getPlatformFeeReversedCents(),
                    refund.getNetMerchantReversedCents(),
                    deserializeReversals(refund.getRecipientReversalsJson()),
                    statusAtStep,
                    runningTotal,
                    remaining,
                    refund.getReason(),
                    refund.getIdempotencyKey(),
                    refund.getCorrelationId(),
                    refund.getCreatedAt()
            ));
        }

        Collections.reverse(responses);
        return new RefundListResponse(responses);
    }

    private RefundResponse executeRefundTransaction(
            UUID paymentId,
            CreateRefundRequest request,
            String idempotencyKey,
            String correlationId
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SETTLED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new InvalidPaymentStateException(
                    "Pagamento " + paymentId + " não aceita estorno no estado atual: " + payment.getStatus()
            );
        }

        List<PaymentRefund> priorRefunds = paymentRefundRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        RefundCalculator.PriorRefundTotals priorTotals = summarizePriorRefunds(priorRefunds);

        long remainingRefundable = Math.subtractExact(payment.getAmountCents(), priorTotals.alreadyRefundedCents());
        long requestedAmount = request.amountCents() == null ? remainingRefundable : request.amountCents();

        List<PaymentSplit> orderedSplits = paymentSplitRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        RefundCalculator.RefundBreakdown breakdown = refundCalculator.calculate(
                payment.getAmountCents(),
                payment.getPlatformFeeCents(),
                payment.getNetMerchantCents(),
                orderedSplits,
                priorTotals,
                requestedAmount
        );

        LedgerTransaction compensatingTx = postCompensatingLedgerTransaction(payment, breakdown, correlationId);

        PaymentStatus nextStatus = breakdown.remainingRefundableAfterCents() == 0L
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        payment.transitionTo(nextStatus);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        PaymentRefund savedRefund = paymentRefundRepository.saveAndFlush(new PaymentRefund(
                UUID.randomUUID(),
                savedPayment.getId(),
                savedPayment.getMerchantId(),
                compensatingTx.getId(),
                breakdown.refundAmountCents(),
                breakdown.platformFeeReversedCents(),
                breakdown.netMerchantReversedCents(),
                serializeReversals(breakdown.recipientReversals()),
                request.reason(),
                idempotencyKey,
                correlationId
        ));

        recordRefundOutboxEvent(savedPayment, savedRefund, breakdown, correlationId);

        return new RefundResponse(
                savedRefund.getId(),
                savedRefund.getPaymentId(),
                savedRefund.getLedgerTransactionId(),
                savedRefund.getAmountCents(),
                savedRefund.getPlatformFeeReversedCents(),
                savedRefund.getNetMerchantReversedCents(),
                breakdown.recipientReversals(),
                savedPayment.getStatus(),
                breakdown.totalRefundedAfterCents(),
                breakdown.remainingRefundableAfterCents(),
                savedRefund.getReason(),
                savedRefund.getIdempotencyKey(),
                savedRefund.getCorrelationId(),
                savedRefund.getCreatedAt()
        );
    }

    private LedgerTransaction postCompensatingLedgerTransaction(
            Payment payment,
            RefundCalculator.RefundBreakdown breakdown,
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
                EntryDirection.CREDIT,
                breakdown.refundAmountCents()
        ));

        if (breakdown.platformFeeReversedCents() > 0L) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    platformFeeAccount.getId(),
                    EntryDirection.DEBIT,
                    breakdown.platformFeeReversedCents()
            ));
        }
        if (breakdown.netMerchantReversedCents() > 0L) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    merchantAccount.getId(),
                    EntryDirection.DEBIT,
                    breakdown.netMerchantReversedCents()
            ));
        }
        for (RecipientReversalDto splitReversal : breakdown.recipientReversals()) {
            if (splitReversal.amountCents() > 0L) {
                legs.add(new LedgerPostingCommand.LedgerLeg(
                        splitReversal.recipientAccountId(),
                        EntryDirection.DEBIT,
                        splitReversal.amountCents()
                ));
            }
        }

        return ledgerService.postTransaction(new LedgerPostingCommand(
                LedgerReferenceType.PAYMENT_REFUND,
                payment.getId(),
                "Estorno compensatório de pagamento " + payment.getId(),
                payment.getCurrency(),
                correlationId,
                legs
        ));
    }

    private void recordRefundOutboxEvent(
            Payment payment,
            PaymentRefund refund,
            RefundCalculator.RefundBreakdown breakdown,
            String correlationId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refund.getId());
        payload.put("paymentId", payment.getId());
        payload.put("ledgerTransactionId", refund.getLedgerTransactionId());
        payload.put("amountCents", refund.getAmountCents());
        payload.put("platformFeeReversedCents", refund.getPlatformFeeReversedCents());
        payload.put("netMerchantReversedCents", refund.getNetMerchantReversedCents());
        payload.put("totalRefundedCents", breakdown.totalRefundedAfterCents());
        payload.put("remainingRefundableCents", breakdown.remainingRefundableAfterCents());
        payload.put("currency", payment.getCurrency());
        payload.put("status", payment.getStatus().name());
        payload.put("reason", refund.getReason());

        outboxService.recordEvent(
                "PAYMENT",
                payment.getId(),
                payment.getMerchantId(),
                "payment.refunded",
                correlationId,
                payload
        );
    }

    private RefundCalculator.PriorRefundTotals summarizePriorRefunds(List<PaymentRefund> priorRefunds) {
        long gross = 0L;
        long fee = 0L;
        long merchant = 0L;
        Map<UUID, Long> recipients = RefundCalculator.emptyRecipientMap();

        for (PaymentRefund prior : priorRefunds) {
            gross = Math.addExact(gross, prior.getAmountCents());
            fee = Math.addExact(fee, prior.getPlatformFeeReversedCents());
            merchant = Math.addExact(merchant, prior.getNetMerchantReversedCents());
            for (RecipientReversalDto rec : deserializeReversals(prior.getRecipientReversalsJson())) {
                recipients.merge(rec.recipientAccountId(), rec.amountCents(), Math::addExact);
            }
        }
        return new RefundCalculator.PriorRefundTotals(gross, fee, merchant, recipients);
    }

    private Payment loadAuthorizedPayment(UUID paymentId, Authentication authentication) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + paymentId));

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedMerchant merchant
                && !payment.getMerchantId().equals(merchant.id())) {
            throw new AccessDeniedException("Merchant autenticado não tem acesso a pagamentos de outro merchant.");
        }
        return payment;
    }

    private String serializeReversals(List<RecipientReversalDto> reversals) {
        try {
            return objectMapper.writeValueAsString(reversals);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar reversões de splits do estorno.", ex);
        }
    }

    private List<RecipientReversalDto> deserializeReversals(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, REVERSAL_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar reversões de splits do estorno.", ex);
        }
    }
}

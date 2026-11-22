package dev.ledgerpulse.core.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared helper for double-entry payment settlement and outbox event emission.
 */
@Component
public class PaymentSettlementHelper {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    public record SplitLine(UUID recipientAccountId, long amountCents) {
    }

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public PaymentSettlementHelper(
            AccountRepository accountRepository,
            LedgerService ledgerService,
            OutboxService outboxService,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    public LedgerTransaction postSettlement(
            Payment payment,
            List<SplitLine> splitLines,
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

        if (payment.getPlatformFeeCents() > 0) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    platformFeeAccount.getId(),
                    EntryDirection.CREDIT,
                    payment.getPlatformFeeCents()
            ));
        }
        if (payment.getNetMerchantCents() > 0) {
            legs.add(new LedgerPostingCommand.LedgerLeg(
                    merchantAccount.getId(),
                    EntryDirection.CREDIT,
                    payment.getNetMerchantCents()
            ));
        }
        for (SplitLine split : splitLines) {
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

    public List<SplitLine> fromEntities(List<PaymentSplit> splits) {
        return splits.stream()
                .map(s -> new SplitLine(s.getRecipientAccountId(), s.getAmountCents()))
                .toList();
    }

    public void recordPaymentOutboxEvent(Payment payment, List<String> reasonCodes, String correlationId) {
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
                correlationId,
                payload
        );
    }

    public String serializeReasonCodes(List<String> reasonCodes) {
        try {
            return objectMapper.writeValueAsString(reasonCodes == null ? List.of() : reasonCodes);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar reasonCodes.", ex);
        }
    }

    public List<String> deserializeReasonCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar reasonCodes.", ex);
        }
    }
}

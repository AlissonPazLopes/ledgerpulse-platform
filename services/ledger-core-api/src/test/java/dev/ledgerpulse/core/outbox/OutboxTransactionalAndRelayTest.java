package dev.ledgerpulse.core.outbox;

import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.service.LedgerPostingCommand;
import dev.ledgerpulse.core.ledger.service.LedgerService;
import dev.ledgerpulse.core.payment.api.CreatePaymentRequest;
import dev.ledgerpulse.core.payment.api.PaymentSplitRequest;
import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.SplitType;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.payment.service.PaymentService;
import dev.ledgerpulse.core.security.AuthenticatedOperator;
import dev.ledgerpulse.core.auth.domain.UserRole;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class OutboxTransactionalAndRelayTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldNotPersistOutboxEventWhenSqlTransactionRollsBack() {
        UUID aggregateId = UUID.randomUUID();
        long escrowBefore = accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            ledgerService.postTransaction(new LedgerPostingCommand(
                    LedgerReferenceType.PAYMENT_SETTLEMENT,
                    aggregateId,
                    "Rollback test",
                    "BRL",
                    "corr-rollback",
                    List.of(
                            new LedgerPostingCommand.LedgerLeg(DevIdentitySeeder.ESCROW_ACCOUNT_ID, EntryDirection.DEBIT, 2_000L),
                            new LedgerPostingCommand.LedgerLeg(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID, EntryDirection.CREDIT, 2_000L)
                    )
            ));
            outboxService.recordEvent(
                    "PAYMENT",
                    aggregateId,
                    DevIdentitySeeder.DEMO_MERCHANT_ID,
                    "payment.settled",
                    "corr-rollback",
                    Map.of("paymentId", aggregateId, "amountCents", 2_000L)
            );
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", aggregateId))
                .isEmpty();
        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents()).isEqualTo(escrowBefore);
    }

    @Test
    void shouldNotPersistPaymentOrOutboxWhenPaymentValidationFails() {
        long paymentsBefore = paymentRepository.count();
        long outboxBefore = outboxEventRepository.count();

        var operator = new AuthenticatedOperator(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "Admin",
                "admin@ledgerpulse.dev",
                UserRole.ADMIN
        );
        var auth = new UsernamePasswordAuthenticationToken(operator, null, List.of());

        CreatePaymentRequest badRequest = new CreatePaymentRequest(
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                "payer-bad-split",
                10_000L,
                "BRL",
                PaymentMethod.CARD,
                "Recipient missing",
                List.of(new PaymentSplitRequest(UUID.randomUUID(), SplitType.FIXED, null, 1_000L))
        );

        assertThatThrownBy(() -> paymentService.createPayment(badRequest, "idem-rollback-" + UUID.randomUUID(), "corr-bad", auth))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void shouldRelayPendingEventToPublishedAndMarkFailedAfterMaxAttempts() {
        UUID aggregateSuccess = UUID.randomUUID();
        OutboxEvent successEvent = transactionTemplate.execute(status -> outboxService.recordEvent(
                "PAYMENT",
                aggregateSuccess,
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                "payment.settled",
                "corr-relay-ok",
                Map.of("paymentId", aggregateSuccess, "amountCents", 5_000L)
        ));

        RabbitTemplate mockRabbitTemplate = Mockito.mock(RabbitTemplate.class);
        OutboxEventPublisher rabbitPublisher = new RabbitMqOutboxPublisher(mockRabbitTemplate, "ledger.events");
        OutboxRelayWorker successWorker = new OutboxRelayWorker(outboxEventRepository, rabbitPublisher, transactionTemplate, 3);

        successWorker.relayPendingEvents();

        OutboxEvent published = outboxEventRepository.findById(successEvent.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockRabbitTemplate).send(eq("ledger.events"), eq("payment.settled"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo(successEvent.getId().toString());
        assertThat((String) messageCaptor.getValue().getMessageProperties().getHeader("X-Correlation-Id"))
                .isEqualTo("corr-relay-ok");

        // Failure & retry -> FAILED after maxAttempts = 3
        UUID aggregateFail = UUID.randomUUID();
        OutboxEvent failingEvent = transactionTemplate.execute(status -> outboxService.recordEvent(
                "PAYMENT",
                aggregateFail,
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                "payment.declined",
                "corr-relay-fail",
                Map.of("paymentId", aggregateFail, "amountCents", 9_000L)
        ));

        OutboxEventPublisher failingPublisher = event -> {
            throw new RuntimeException("Broker unavailable");
        };
        OutboxRelayWorker retryWorker = new OutboxRelayWorker(outboxEventRepository, failingPublisher, transactionTemplate, 3);

        retryWorker.relayPendingEvents();
        OutboxEvent afterAttempt1 = outboxEventRepository.findById(failingEvent.getId()).orElseThrow();
        assertThat(afterAttempt1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterAttempt1.getAttempts()).isEqualTo(1);
        assertThat(afterAttempt1.getLastError()).contains("Broker unavailable");

        retryWorker.relayPendingEvents();
        OutboxEvent afterAttempt2 = outboxEventRepository.findById(failingEvent.getId()).orElseThrow();
        assertThat(afterAttempt2.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterAttempt2.getAttempts()).isEqualTo(2);

        retryWorker.relayPendingEvents();
        OutboxEvent afterAttempt3 = outboxEventRepository.findById(failingEvent.getId()).orElseThrow();
        assertThat(afterAttempt3.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(afterAttempt3.getAttempts()).isEqualTo(3);
    }
}

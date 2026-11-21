package dev.ledgerpulse.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    private record SamplePayload(String payerId, long amountCents) {
    }

    private record SampleResponse(String id, long amountCents) {
    }

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyService service = new IdempotencyService(store, new ObjectMapper(), 24);

    @Test
    void shouldExecuteOnceAndReplayCachedResponseOnIdenticalPayload() {
        UUID merchantId = UUID.randomUUID();
        SamplePayload payload = new SamplePayload("payer-1", 15_000L);
        AtomicInteger invocations = new AtomicInteger();

        var first = service.execute(
                merchantId,
                "idem-key-01",
                payload,
                201,
                SampleResponse.class,
                () -> {
                    invocations.incrementAndGet();
                    return new SampleResponse("pay-1", 15_000L);
                }
        );

        assertThat(first.replayed()).isFalse();
        assertThat(first.httpStatus()).isEqualTo(201);
        assertThat(first.body().id()).isEqualTo("pay-1");
        assertThat(invocations.get()).isEqualTo(1);

        var replay = service.execute(
                merchantId,
                "idem-key-01",
                payload,
                201,
                SampleResponse.class,
                () -> {
                    invocations.incrementAndGet();
                    return new SampleResponse("pay-should-not-run", 15_000L);
                }
        );

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.httpStatus()).isEqualTo(201);
        assertThat(replay.body().id()).isEqualTo("pay-1");
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void shouldThrowConflictWhenPayloadDivergesOrRequestIsInProgress() {
        UUID merchantId = UUID.randomUUID();
        service.execute(
                merchantId,
                "idem-key-02",
                new SamplePayload("payer-1", 10_000L),
                201,
                SampleResponse.class,
                () -> new SampleResponse("pay-2", 10_000L)
        );

        assertThatThrownBy(() -> service.execute(
                merchantId,
                "idem-key-02",
                new SamplePayload("payer-1", 20_000L),
                201,
                SampleResponse.class,
                () -> new SampleResponse("pay-2", 20_000L)
        )).isInstanceOf(IdempotencyConflictException.class);

        String inProgressPayloadHash = service.hashCanonicalPayload(new SamplePayload("payer-2", 5_000L));
        store.tryAcquireInProgress(merchantId + ":idem-key-03", inProgressPayloadHash, Duration.ofHours(24));

        assertThatThrownBy(() -> service.execute(
                merchantId,
                "idem-key-03",
                new SamplePayload("payer-2", 5_000L),
                201,
                SampleResponse.class,
                () -> new SampleResponse("pay-3", 5_000L)
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void shouldReleaseLockWhenActionThrowsException() {
        UUID merchantId = UUID.randomUUID();
        SamplePayload payload = new SamplePayload("payer-retry", 7_500L);

        assertThatThrownBy(() -> service.execute(
                merchantId,
                "idem-key-retry",
                payload,
                201,
                SampleResponse.class,
                () -> {
                    throw new IllegalStateException("transient error");
                }
        )).isInstanceOf(IllegalStateException.class);

        var recovered = service.execute(
                merchantId,
                "idem-key-retry",
                payload,
                201,
                SampleResponse.class,
                () -> new SampleResponse("pay-recovered", 7_500L)
        );

        assertThat(recovered.replayed()).isFalse();
        assertThat(recovered.body().id()).isEqualTo("pay-recovered");
    }
}

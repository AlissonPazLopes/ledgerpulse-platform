package dev.ledgerpulse.core.payment.api;

import dev.ledgerpulse.core.idempotency.IdempotencyService;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.service.PaymentService;
import dev.ledgerpulse.core.web.CorrelationIdFilter;
import dev.ledgerpulse.core.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER', 'MERCHANT')")
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(httpRequest);
        IdempotencyService.IdempotencyExecution<PaymentResponse> execution =
                paymentService.createPayment(request, idempotencyKey, correlationId, authentication);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (execution.replayed()) {
            builder.header("X-Idempotent-Replay", "true");
        }
        return builder.body(execution.body());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    public PageResponse<PaymentResponse> listPayments(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return paymentService.listPayments(status, merchantId, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER', 'MERCHANT')")
    public PaymentResponse getPaymentById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return paymentService.getPaymentById(id, authentication);
    }
}

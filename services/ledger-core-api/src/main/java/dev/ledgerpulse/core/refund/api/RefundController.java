package dev.ledgerpulse.core.refund.api;

import dev.ledgerpulse.core.idempotency.IdempotencyService;
import dev.ledgerpulse.core.refund.service.RefundService;
import dev.ledgerpulse.core.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{id}/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'MERCHANT')")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(httpRequest);
        IdempotencyService.IdempotencyExecution<RefundResponse> execution =
                refundService.createRefund(id, request, idempotencyKey, correlationId, authentication);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (execution.replayed()) {
            builder.header("X-Idempotent-Replay", "true");
        }
        return builder.body(execution.body());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER', 'MERCHANT')")
    public ResponseEntity<RefundListResponse> listRefunds(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(refundService.listRefunds(id, authentication));
    }
}

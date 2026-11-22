package dev.ledgerpulse.core.risk.review;

import dev.ledgerpulse.core.security.AuthenticatedOperator;
import dev.ledgerpulse.core.web.CorrelationIdFilter;
import dev.ledgerpulse.core.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/risk/reviews")
public class RiskReviewController {

    private final RiskReviewService riskReviewService;

    public RiskReviewController(RiskReviewService riskReviewService) {
        this.riskReviewService = riskReviewService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public PageResponse<RiskReviewItemResponse> listReviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minRiskScore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return riskReviewService.listPendingReviews(merchantId, minRiskScore, from, to, page, size);
    }

    @PostMapping("/{paymentId}/decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public RiskReviewDecisionResponse submitDecision(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RiskReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedOperator operator,
            HttpServletRequest httpRequest
    ) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(httpRequest);
        return riskReviewService.submitDecision(paymentId, request, operator, correlationId);
    }
}

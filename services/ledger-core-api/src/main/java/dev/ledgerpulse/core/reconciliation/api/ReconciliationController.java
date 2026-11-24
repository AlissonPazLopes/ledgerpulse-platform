package dev.ledgerpulse.core.reconciliation.api;

import dev.ledgerpulse.core.reconciliation.domain.ReconciliationTrigger;
import dev.ledgerpulse.core.reconciliation.service.ReconciliationService;
import dev.ledgerpulse.core.web.CorrelationIdFilter;
import dev.ledgerpulse.core.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReconciliationReportResponse> runManualReconciliation(HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(request);
        ReconciliationReportResponse report =
                reconciliationService.runReconciliation(ReconciliationTrigger.MANUAL, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    public PageResponse<ReconciliationReportResponse> listReports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return reconciliationService.listReports(page, size);
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'VIEWER')")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csvBytes = reconciliationService.exportCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ledgerpulse-reconciliation.csv\"")
                .body(csvBytes);
    }
}

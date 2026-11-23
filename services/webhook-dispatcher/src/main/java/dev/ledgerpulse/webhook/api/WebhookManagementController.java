package dev.ledgerpulse.webhook.api;

import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.service.WebhookManagementService;
import dev.ledgerpulse.webhook.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Validated
public class WebhookManagementController {

    private final WebhookManagementService managementService;

    public WebhookManagementController(WebhookManagementService managementService) {
        this.managementService = managementService;
    }

    @PostMapping("/endpoints")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookEndpointResponse> createEndpoint(
            @Valid @RequestBody CreateWebhookEndpointRequest request
    ) {
        WebhookEndpointResponse created = managementService.createEndpoint(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/endpoints")
    public ResponseEntity<PageResponse<WebhookEndpointResponse>> listEndpoints(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) WebhookEndpointStatus status
    ) {
        return ResponseEntity.ok(managementService.listEndpoints(merchantId, status, page, size));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<PageResponse<WebhookDeliveryResponse>> listDeliveries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID eventId
    ) {
        return ResponseEntity.ok(managementService.listDeliveries(status, merchantId, eventType, eventId, page, size));
    }

    @PostMapping("/dlq/{deliveryId}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<DlqReplayResponse> replayDlqDelivery(@PathVariable UUID deliveryId) {
        return ResponseEntity.ok(managementService.replayDlqDelivery(deliveryId));
    }
}

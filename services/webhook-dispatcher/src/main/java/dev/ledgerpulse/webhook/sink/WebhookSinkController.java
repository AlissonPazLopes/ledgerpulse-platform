package dev.ledgerpulse.webhook.sink;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sink")
public class WebhookSinkController {

    private final WebhookSinkStateService sinkStateService;

    public WebhookSinkController(WebhookSinkStateService sinkStateService) {
        this.sinkStateService = sinkStateService;
    }

    @GetMapping("/mode")
    public ResponseEntity<SinkModeResponse> getMode() {
        WebhookSinkStateService.SinkState state = sinkStateService.getCurrentState();
        return ResponseEntity.ok(new SinkModeResponse(
                state.mode(),
                sinkStateService.getTimeoutDelayMs(),
                state.updatedAt()
        ));
    }

    @PutMapping("/mode")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<SinkModeResponse> updateMode(@Valid @RequestBody UpdateSinkModeRequest request) {
        WebhookSinkStateService.SinkState updated = sinkStateService.updateMode(request.mode());
        return ResponseEntity.ok(new SinkModeResponse(
                updated.mode(),
                sinkStateService.getTimeoutDelayMs(),
                updated.updatedAt()
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<SinkTestResponse> receiveSinkWebhook(
            @RequestHeader(value = "X-LedgerPulse-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-LedgerPulse-Event-Id", required = false) String eventIdHeader,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        SinkMode mode = sinkStateService.getCurrentState().mode();
        String eventId = resolveEventId(eventIdHeader, payload);
        boolean signaturePresent = StringUtils.hasText(signatureHeader);

        return switch (mode) {
            case OK_200 -> ResponseEntity.ok(new SinkTestResponse(
                    true,
                    SinkMode.OK_200,
                    eventId,
                    signaturePresent,
                    Instant.now(),
                    null
            ));
            case FAIL_500 -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SinkTestResponse(
                    false,
                    SinkMode.FAIL_500,
                    eventId,
                    signaturePresent,
                    null,
                    "Simulated 500 Internal Server Error from Merchant Webhook Sink"
            ));
            case TIMEOUT -> simulateTimeout(eventId, signaturePresent);
        };
    }

    private ResponseEntity<SinkTestResponse> simulateTimeout(String eventId, boolean signaturePresent) {
        try {
            Thread.sleep(sinkStateService.getTimeoutDelayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new SinkTestResponse(
                false,
                SinkMode.TIMEOUT,
                eventId,
                signaturePresent,
                null,
                "Simulated timeout delay exceeded in Merchant Webhook Sink"
        ));
    }

    private String resolveEventId(String headerEventId, Map<String, Object> payload) {
        if (StringUtils.hasText(headerEventId)) {
            return headerEventId.trim();
        }
        if (payload != null && payload.get("eventId") != null) {
            String fromBody = String.valueOf(payload.get("eventId")).trim();
            if (StringUtils.hasText(fromBody)) {
                return fromBody;
            }
        }
        return null;
    }
}

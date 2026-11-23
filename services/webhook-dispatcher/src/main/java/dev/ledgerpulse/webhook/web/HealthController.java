package dev.ledgerpulse.webhook.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String applicationName;
    private final String version;
    private final boolean virtualThreadsEnabled;

    public HealthController(
            @Value("${spring.application.name:webhook-dispatcher}") String applicationName,
            @Value("${app.version:0.1.0}") String version,
            @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled
    ) {
        this.applicationName = applicationName;
        this.version = version;
        this.virtualThreadsEnabled = virtualThreadsEnabled;
    }

    @GetMapping
    public ResponseEntity<WebhookHealthResponse> health() {
        return ResponseEntity.ok(new WebhookHealthResponse(
                "UP",
                applicationName,
                version,
                virtualThreadsEnabled,
                Instant.now()
        ));
    }
}

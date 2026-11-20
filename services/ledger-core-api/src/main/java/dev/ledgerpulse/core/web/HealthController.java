package dev.ledgerpulse.core.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String serviceName;
    private final String appVersion;

    public HealthController(
            @Value("${spring.application.name:ledger-core-api}") String serviceName,
            @Value("${app.version:0.1.0}") String appVersion
    ) {
        this.serviceName = serviceName;
        this.appVersion = appVersion;
    }

    @GetMapping
    public HealthResponse getHealth() {
        return new HealthResponse("UP", serviceName, appVersion, Instant.now());
    }
}

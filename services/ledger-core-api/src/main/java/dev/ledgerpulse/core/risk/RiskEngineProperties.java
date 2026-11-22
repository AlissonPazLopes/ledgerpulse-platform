package dev.ledgerpulse.core.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.risk-engine")
public record RiskEngineProperties(
        String mode,
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}

package dev.ledgerpulse.core.outbox;

import org.springframework.context.annotation.Configuration;

/**
 * Retained for compatibility; @EnableScheduling is configured globally on LedgerCoreApplication
 * while individual schedulers opt in/out via @ConditionalOnProperty.
 */
@Configuration
public class OutboxSchedulingConfig {
}

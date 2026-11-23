package dev.ledgerpulse.webhook.migration;

import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import dev.ledgerpulse.webhook.service.DevWebhookSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayWebhooksMigrationsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Test
    void shouldApplyFlywayMigrationInWebhooksSchemaAndSeedDevEndpoint() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT LOWER(table_name)
                FROM information_schema.tables
                WHERE LOWER(table_schema) = 'webhooks'
                """,
                String.class
        );

        assertThat(tables).contains(
                "webhook_endpoints",
                "webhook_deliveries",
                "webhook_delivery_attempts"
        );

        WebhookEndpoint seeded = endpointRepository.findById(DevWebhookSeeder.DEFAULT_ENDPOINT_ID)
                .orElseThrow();
        assertThat(seeded.getMerchantId()).isEqualTo(DevWebhookSeeder.DEFAULT_MERCHANT_ID);
        assertThat(seeded.getStatus()).isEqualTo(WebhookEndpointStatus.ACTIVE);
        assertThat(seeded.getMaskedSecretPreview()).isEqualTo("whsec_****2026");
    }
}

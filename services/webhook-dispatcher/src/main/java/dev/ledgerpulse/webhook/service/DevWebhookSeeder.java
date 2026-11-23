package dev.ledgerpulse.webhook.service;

import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds the default Merchant Webhook Sink endpoint in local/dev/test profiles idempotently.
 */
@Component
@Profile({"local", "dev", "test"})
public class DevWebhookSeeder implements ApplicationRunner {

    public static final UUID DEFAULT_ENDPOINT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final UUID DEFAULT_MERCHANT_ID = UUID.fromString("7b92c3e0-1f42-4876-a1e2-901283471234");
    public static final String DEFAULT_SIGNING_SECRET = "whsec_ledgerpulse_dev_secret_2026";
    public static final String DEFAULT_DESCRIPTION = "Merchant Webhook Sink Local (Acme Fintech Store)";

    private final WebhookEndpointRepository endpointRepository;
    private final String defaultTargetUrl;

    public DevWebhookSeeder(
            WebhookEndpointRepository endpointRepository,
            @Value("${app.sink.default-target-url:http://localhost:8082/api/v1/sink/test}") String defaultTargetUrl
    ) {
        this.endpointRepository = endpointRepository;
        this.defaultTargetUrl = defaultTargetUrl;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (endpointRepository.existsById(DEFAULT_ENDPOINT_ID)) {
            return;
        }
        WebhookEndpoint endpoint = new WebhookEndpoint(
                DEFAULT_ENDPOINT_ID,
                DEFAULT_MERCHANT_ID,
                defaultTargetUrl,
                DEFAULT_SIGNING_SECRET,
                DEFAULT_DESCRIPTION,
                WebhookEndpointStatus.ACTIVE
        );
        endpointRepository.save(endpoint);
    }
}

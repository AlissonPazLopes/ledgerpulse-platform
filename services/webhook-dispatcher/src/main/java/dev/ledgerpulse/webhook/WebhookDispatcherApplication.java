package dev.ledgerpulse.webhook;

import dev.ledgerpulse.webhook.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class WebhookDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookDispatcherApplication.class, args);
    }
}

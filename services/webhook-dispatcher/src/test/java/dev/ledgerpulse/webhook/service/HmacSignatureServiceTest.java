package dev.ledgerpulse.webhook.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureServiceTest {

    private final HmacSignatureService signatureService = new HmacSignatureService();

    @Test
    void shouldSignAndVerifyPayloadInConstantTime() {
        String secret = "whsec_ledgerpulse_dev_secret_2026";
        String body = "{\"eventId\":\"c7a615f1-82b3-4e11-9c2a-5d2d1b63e701\",\"eventType\":\"payment.settled\"}";
        long timestamp = 1795200000L;

        String header = signatureService.sign(secret, body, timestamp);

        assertThat(header).matches("^t=1795200000,v1=[0-9a-f]{64}$");
        assertThat(signatureService.verifySignature(secret, body, header)).isTrue();
    }

    @Test
    void shouldRejectTamperedBodyWrongSecretOrMalformedHeader() {
        String secret = "whsec_ledgerpulse_dev_secret_2026";
        String body = "{\"amountCents\":15000}";
        String header = signatureService.sign(secret, body, 1795200000L);

        assertThat(signatureService.verifySignature(secret, "{\"amountCents\":15001}", header)).isFalse();
        assertThat(signatureService.verifySignature("whsec_wrong_secret_value_2026", body, header)).isFalse();
        assertThat(signatureService.verifySignature(secret, body, "t=1795200000,v1=deadbeef")).isFalse();
        assertThat(signatureService.verifySignature(secret, body, "invalid-header")).isFalse();
        assertThat(signatureService.verifySignature(secret, body, null)).isFalse();
    }
}

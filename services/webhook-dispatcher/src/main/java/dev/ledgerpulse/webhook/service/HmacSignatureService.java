package dev.ledgerpulse.webhook.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates and verifies constant-time HMAC-SHA256 signatures
 * using header format: X-LedgerPulse-Signature: t=<unix_epoch>,v1=<hex_hmac>.
 */
@Service
public class HmacSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    public String sign(String secret, String rawBody) {
        return sign(secret, rawBody, Instant.now().getEpochSecond());
    }

    public String sign(String secret, String rawBody, long timestampEpochSeconds) {
        String signatureHex = computeHex(secret, rawBody, timestampEpochSeconds);
        return "t=" + timestampEpochSeconds + ",v1=" + signatureHex;
    }

    public String computeHex(String secret, String rawBody, long timestampEpochSeconds) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("Webhook signing secret must not be blank");
        }
        String body = rawBody == null ? "" : rawBody;
        String signedPayload = timestampEpochSeconds + "." + body;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(rawHmac);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", ex);
        }
    }

    public boolean verifySignature(String secret, String rawBody, String signatureHeader) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(signatureHeader)) {
            return false;
        }
        String timestampPart = null;
        String v1Part = null;
        for (String segment : signatureHeader.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.startsWith("t=")) {
                timestampPart = trimmed.substring(2);
            } else if (trimmed.startsWith("v1=")) {
                v1Part = trimmed.substring(3);
            }
        }
        if (!StringUtils.hasText(timestampPart) || !StringUtils.hasText(v1Part)) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampPart);
        } catch (NumberFormatException ex) {
            return false;
        }

        String expectedHex = computeHex(secret, rawBody, timestamp);
        byte[] expectedBytes = expectedHex.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = v1Part.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}

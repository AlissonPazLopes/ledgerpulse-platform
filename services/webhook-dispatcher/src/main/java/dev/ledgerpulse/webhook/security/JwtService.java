package dev.ledgerpulse.webhook.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.security.jwt.secret must be at least 32 bytes long");
        }
        if (properties.expirationSeconds() <= 0) {
            throw new IllegalArgumentException("app.security.jwt.expiration-seconds must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
    }

    public String generateToken(AuthenticatedOperator operator) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(operator.id().toString())
                .claim("name", operator.name())
                .claim("email", operator.email())
                .claim("role", operator.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedOperator parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID id = UUID.fromString(claims.getSubject());
        String name = claims.get("name", String.class);
        String email = claims.get("email", String.class);
        String roleClaim = claims.get("role", String.class);
        if (name == null || email == null || roleClaim == null) {
            throw new IllegalArgumentException("Missing required JWT operator claims");
        }
        return new AuthenticatedOperator(id, name, email, UserRole.valueOf(roleClaim));
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}

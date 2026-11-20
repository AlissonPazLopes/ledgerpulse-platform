package dev.ledgerpulse.core.security;

import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.domain.UserRole;
import dev.ledgerpulse.core.auth.domain.UserStatus;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String VALID_SECRET = "ledgerpulse-unit-test-jwt-secret-key-32bytes+";

    @Test
    void shouldGenerateAndParseOperatorToken() {
        JwtService jwtService = new JwtService(new JwtProperties(VALID_SECRET, 3600));
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UserAccount user = new UserAccount(
                userId,
                "LedgerPulse Admin",
                "admin@ledgerpulse.dev",
                "$2a$10$hash",
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );

        String token = jwtService.generateToken(user);
        AuthenticatedOperator parsed = jwtService.parseToken(token);

        assertThat(parsed.id()).isEqualTo(userId);
        assertThat(parsed.name()).isEqualTo("LedgerPulse Admin");
        assertThat(parsed.email()).isEqualTo("admin@ledgerpulse.dev");
        assertThat(parsed.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void shouldRejectTamperedJwtToken() {
        JwtService jwtService = new JwtService(new JwtProperties(VALID_SECRET, 3600));
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                "Analyst",
                "analyst@ledgerpulse.dev",
                "$2a$10$hash",
                UserRole.ANALYST,
                UserStatus.ACTIVE
        );

        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectShortJwtSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

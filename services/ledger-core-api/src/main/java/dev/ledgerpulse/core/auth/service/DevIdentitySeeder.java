package dev.ledgerpulse.core.auth.service;

import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.domain.UserRole;
import dev.ledgerpulse.core.auth.domain.UserStatus;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.security.ApiKeyHasher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds deterministic operators and demo merchant only in non-production profiles.
 */
@Component
@Profile({"local", "dev", "test"})
public class DevIdentitySeeder implements ApplicationRunner {

    public static final String DEV_OPERATOR_PASSWORD = "LedgerPulse@2026";
    public static final String DEV_MERCHANT_API_KEY = "lp_test_merchant_key_v1";

    private final UserAccountRepository userAccountRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    public DevIdentitySeeder(
            UserAccountRepository userAccountRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedOperator(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "LedgerPulse Admin",
                "admin@ledgerpulse.dev",
                UserRole.ADMIN
        );
        seedOperator(
                UUID.fromString("11111111-1111-4111-8111-111111111112"),
                "LedgerPulse Analyst",
                "analyst@ledgerpulse.dev",
                UserRole.ANALYST
        );
        seedOperator(
                UUID.fromString("11111111-1111-4111-8111-111111111113"),
                "LedgerPulse Viewer",
                "viewer@ledgerpulse.dev",
                UserRole.VIEWER
        );

        UUID demoMerchantId = UUID.fromString("7b92c3e0-1f42-4876-a1e2-901283471234");
        String apiKeyHash = ApiKeyHasher.sha256Hex(DEV_MERCHANT_API_KEY);
        if (!merchantRepository.existsById(demoMerchantId)
                && merchantRepository.findByApiKeyHash(apiKeyHash).isEmpty()) {
            merchantRepository.save(new Merchant(
                    demoMerchantId,
                    "Acme Fintech Store",
                    apiKeyHash,
                    250,
                    MerchantStatus.ACTIVE
            ));
        }
    }

    private void seedOperator(UUID id, String name, String email, UserRole role) {
        if (userAccountRepository.existsById(id)
                || userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        userAccountRepository.save(new UserAccount(
                id,
                name,
                email,
                passwordEncoder.encode(DEV_OPERATOR_PASSWORD),
                role,
                UserStatus.ACTIVE
        ));
    }
}

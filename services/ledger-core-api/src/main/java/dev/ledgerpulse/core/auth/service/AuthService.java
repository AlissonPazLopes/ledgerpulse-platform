package dev.ledgerpulse.core.auth.service;

import dev.ledgerpulse.core.auth.api.LoginRequest;
import dev.ledgerpulse.core.auth.api.LoginResponse;
import dev.ledgerpulse.core.auth.api.UserProfileResponse;
import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(UserAccount::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas para o email informado."));

        String token = jwtService.generateToken(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserProfileResponse.from(user)
        );
    }
}

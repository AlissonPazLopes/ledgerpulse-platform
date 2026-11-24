package dev.ledgerpulse.core.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.security.AuthenticatedMerchant;
import dev.ledgerpulse.core.web.ProblemDetailFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Enforces per-merchant Token Bucket rate limiting on X-API-Key authenticated requests.
 */
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStore rateLimitStore;
    private final RateLimitProperties properties;
    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ApiKeyRateLimitFilter(
            RateLimitStore rateLimitStore,
            RateLimitProperties properties,
            ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper
    ) {
        this.rateLimitStore = rateLimitStore;
        this.properties = properties;
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedMerchant merchant)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimitStore.tryConsume("merchant:" + merchant.id());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            ProblemDetail problem = problemDetailFactory.create(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limit-exceeded",
                    "Rate Limit Exceeded",
                    "Limite de requisições excedido para a X-API-Key informada.",
                    request
            );
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }

        filterChain.doFilter(request, response);
    }
}

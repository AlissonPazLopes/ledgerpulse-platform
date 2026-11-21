package dev.ledgerpulse.core.security;

import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final MerchantRepository merchantRepository;

    public ApiKeyAuthenticationFilter(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawApiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(rawApiKey)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String keyHash = ApiKeyHasher.sha256Hex(rawApiKey.trim());
            merchantRepository.findByApiKeyHash(keyHash)
                    .filter(Merchant::isActive)
                    .ifPresent(merchant -> {
                        AuthenticatedMerchant principal = new AuthenticatedMerchant(
                                merchant.getId(),
                                merchant.getName(),
                                merchant.getDefaultFeeBps(),
                                merchant.getStatus()
                        );
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }
}

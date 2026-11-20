package dev.ledgerpulse.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every HTTP request carries an X-Correlation-Id across MDC logs,
 * error payloads, and response headers, executing before Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String CORRELATION_ID_ATTR = CorrelationIdFilter.class.getName() + ".CORRELATION_ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = StringUtils.hasText(incoming)
                ? sanitize(incoming.trim())
                : UUID.randomUUID().toString();

        request.setAttribute(CORRELATION_ID_ATTR, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    public static String resolveCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTR);
        if (attr instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String mdc = MDC.get(CORRELATION_ID_MDC_KEY);
        if (StringUtils.hasText(mdc)) {
            return mdc;
        }
        String header = request.getHeader(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(header)) {
            return sanitize(header.trim());
        }
        return UUID.randomUUID().toString();
    }

    private static String sanitize(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}

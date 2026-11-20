package dev.ledgerpulse.core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Shared RFC 7807 ProblemDetail builder for REST controllers and Spring Security handlers.
 */
@Component
public class ProblemDetailFactory {

    public static final String ERROR_BASE_URI = "https://ledgerpulse.dev/errors/";

    public ProblemDetail create(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        return create(status, slug, title, detail, request, List.of());
    }

    public ProblemDetail create(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request,
            List<ValidationFieldError> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_BASE_URI + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", CorrelationIdFilter.resolveCorrelationId(request));
        problem.setProperty("timestamp", Instant.now());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}

package dev.ledgerpulse.webhook.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.web.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(
            ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper
    ) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "Você não possui permissão para acessar este recurso.",
                request
        );
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

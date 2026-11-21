package dev.ledgerpulse.core.web;

import dev.ledgerpulse.core.auth.service.InvalidCredentialsException;
import dev.ledgerpulse.core.idempotency.IdempotencyConflictException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ValidationFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationFieldError(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value"
                ))
                .toList();

        return respond(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation Error",
                "Um ou mais campos da requisição são inválidos.",
                request,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ValidationFieldError> errors = ex.getConstraintViolations().stream()
                .map(cv -> new ValidationFieldError(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();

        return respond(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation Error",
                "Um ou mais parâmetros da requisição são inválidos.",
                request,
                errors
        );
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(
            DomainValidationException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation Error",
                ex.getMessage(),
                request,
                ex.getErrors()
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation Error",
                "Requisição malformada ou cabeçalho obrigatório ausente.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Invalid Credentials",
                ex.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Unauthorized",
                "Autenticação obrigatória ou credencial inválida.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "Você não possui permissão para acessar este recurso.",
                request,
                List.of()
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(
            Exception ex,
            HttpServletRequest request
    ) {
        String detail = ex instanceof ResourceNotFoundException
                ? ex.getMessage()
                : "Recurso solicitado não foi encontrado.";
        return respond(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                detail,
                request,
                List.of()
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONFLICT,
                "idempotency-conflict",
                "Idempotency Key Conflict",
                ex.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLockConflict(
            Exception ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONFLICT,
                "optimistic-lock-conflict",
                "Optimistic Lock Conflict",
                "Conflito de concorrência otimista ao atualizar saldo ou registro.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal Server Error",
                "Erro interno inesperado ao processar a requisição.",
                request,
                List.of()
        );
    }

    private ResponseEntity<ProblemDetail> respond(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request,
            List<ValidationFieldError> errors
    ) {
        ProblemDetail problem = problemDetailFactory.create(status, slug, title, detail, request, errors);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}

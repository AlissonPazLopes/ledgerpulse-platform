package dev.ledgerpulse.core.web;

import java.util.List;

public class DomainValidationException extends RuntimeException {

    private final List<ValidationFieldError> errors;

    public DomainValidationException(String message) {
        super(message);
        this.errors = List.of();
    }

    public DomainValidationException(String message, List<ValidationFieldError> errors) {
        super(message);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<ValidationFieldError> getErrors() {
        return errors;
    }
}

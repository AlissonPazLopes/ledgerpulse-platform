package dev.ledgerpulse.core.web;

public record ValidationFieldError(
        String field,
        String message
) {
}

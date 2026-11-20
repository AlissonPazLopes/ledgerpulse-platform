package dev.ledgerpulse.core.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 6, message = "must be at least 6 characters")
        String password
) {
}

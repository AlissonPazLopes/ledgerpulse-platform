package dev.ledgerpulse.core.auth.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStatusResponse getAdminStatus() {
        return new AdminStatusResponse("OK", "ADMIN", Instant.now());
    }
}

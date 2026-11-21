package dev.ledgerpulse.core.auth.api;

import dev.ledgerpulse.core.security.AuthenticatedMerchant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    @GetMapping("/me")
    @PreAuthorize("hasRole('MERCHANT')")
    public MerchantProfileResponse getCurrentMerchant(@AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return MerchantProfileResponse.from(merchant);
    }
}

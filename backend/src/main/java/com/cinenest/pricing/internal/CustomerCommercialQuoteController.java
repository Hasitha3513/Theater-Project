package com.cinenest.pricing.internal;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerCommercialQuoteController {
    private final CustomerCommercialQuoteConfiguration configuration;

    CustomerCommercialQuoteController(CustomerCommercialQuoteConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostMapping("/api/commercial-quotes")
    void issue(Principal principal) {
        if (principal == null) {
            throw new DraftFailure(401, "Sign-in required");
        }
        configuration.requireOperationalIssuance();
    }

    @ExceptionHandler(DraftFailure.class)
    ResponseEntity<Map<String, String>> denial(DraftFailure failure) {
        return ResponseEntity.status(failure.status).header("Cache-Control", "no-store")
            .body(Map.of("message", failure.getMessage()));
    }
}

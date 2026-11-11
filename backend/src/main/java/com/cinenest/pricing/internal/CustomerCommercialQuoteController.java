package com.cinenest.pricing.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerCommercialQuoteController {
    private final CustomerCommercialQuoteConfiguration configuration;
    private final OperationalCustomerQuoteService service;

    CustomerCommercialQuoteController(CustomerCommercialQuoteConfiguration configuration) {
        this(configuration, null);
    }

    @Autowired
    CustomerCommercialQuoteController(CustomerCommercialQuoteConfiguration configuration,
            OperationalCustomerQuoteService service) {
        this.configuration = configuration;
        this.service = service;
    }

    @PostMapping("/api/commercial-quotes")
    ResponseEntity<Map<String, UUID>> issue(Principal principal, HttpServletRequest request) {
        UUID actor = OperationalCommercialPolicyController.actor(principal);
        configuration.requireOperationalIssuance();
        if (service == null) {
            throw new DraftFailure(503, "Commercial quotation issuance is unavailable");
        }
        var command = OperationalCustomerQuoteService.command(OperationalCommercialPolicyController.body(request));
        return OperationalCommercialPolicyController.response(200, Map.of("quoteId", service.issue(actor, command)));
    }

    @ExceptionHandler(DraftFailure.class)
    ResponseEntity<Map<String, String>> denial(DraftFailure failure) {
        return OperationalCommercialPolicyController.response(failure.status, Map.of("message", failure.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> authentication() {
        return OperationalCommercialPolicyController.response(401, Map.of("message", "Session expired; sign in again"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Map<String, String>> persistence(RuntimeException failure) {
        return OperationalCommercialPolicyController.databaseFailure(failure);
    }
}

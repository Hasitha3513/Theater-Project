package com.cinenest.identity.internal;

import com.cinenest.identity.AccountView;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cinenest.identity.enabled", havingValue = "true")
class AccountController {
    private final IdentityStore store;
    AccountController(IdentityStore store) { this.store = store; }

    @GetMapping("/api/accounts/me")
    ResponseEntity<AccountView> own(Principal principal) {
        UUID actor = UUID.fromString(principal.getName());
        return response(store.read(actor, actor));
    }

    @GetMapping("/api/accounts/{accountId}")
    ResponseEntity<AccountView> target(Principal principal, @PathVariable String accountId) {
        UUID actor = UUID.fromString(principal.getName());
        try {
            UUID target = UUID.fromString(accountId);
            if (!target.toString().equals(accountId)) throw new IllegalArgumentException();
            return response(store.read(actor, target));
        } catch (IllegalArgumentException invalid) {
            store.audit(actor, null, "ACCOUNT_READ", false);
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/api/session/csrf")
    ResponseEntity<Map<String, String>> csrf(Principal principal, HttpServletRequest request) {
        UUID actor = UUID.fromString(principal.getName());
        if (!store.eligible(actor)) {
            store.audit(actor, null, "HTTP_DENIAL", false);
            return ResponseEntity.status(403).build();
        }
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    private ResponseEntity<AccountView> response(java.util.Optional<AccountView> result) {
        return result.map(value -> ResponseEntity.ok().header("Cache-Control", "no-store").body(value))
            .orElseGet(() -> ResponseEntity.status(403).build());
    }
}

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
        return ownAccount(actor);
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
        if (!csrfEligible(actor)) {
            store.audit(actor, null, "HTTP_DENIAL", false);
            return ResponseEntity.status(403).build();
        }
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    private ResponseEntity<AccountView> ownAccount(UUID actor) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (CredentialEpochSessions.operational(auth)) {
            var current = CredentialEpochSessions.operationalCredential(auth, store, false);
            if (!current.account().equals(actor)) return ResponseEntity.status(403).build();
            store.audit(actor, actor, "ACCOUNT_READ", true);
            return response(java.util.Optional.of(new AccountView(actor, "ELIGIBLE")));
        }
        return response(store.read(actor, actor));
    }

    private boolean csrfEligible(UUID actor) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return CredentialEpochSessions.operational(auth)
            ? CredentialEpochSessions.operationalCredential(auth, store, false).account().equals(actor)
            : store.eligible(actor);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(CredentialEpochSessions.Denied.class)
    ResponseEntity<Void> staleCredential() {
        return ResponseEntity.status(401).header("Cache-Control", "no-store").build();
    }

    private ResponseEntity<AccountView> response(java.util.Optional<AccountView> result) {
        return result.map(value -> ResponseEntity.ok().header("Cache-Control", "no-store").body(value))
            .orElseGet(() -> ResponseEntity.status(403).build());
    }
}

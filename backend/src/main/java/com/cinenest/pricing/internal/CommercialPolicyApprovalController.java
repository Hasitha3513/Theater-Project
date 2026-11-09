package com.cinenest.pricing.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

@RestController
@Profile("local-synthetic")
class CommercialPolicyApprovalController {
    private static final int MAX_BODY_BYTES = 32768;
    private static final JsonFactory JSON = JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final CommercialPolicyApprovalService service;
    private final DeniedCommercialPolicyAudit audit;

    CommercialPolicyApprovalController(CommercialPolicyApprovalService service, DeniedCommercialPolicyAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @PostMapping(path = "/api/admin/venues/{venueId}/commercial-policies/trusted-approvals",
        consumes = "application/json")
    ResponseEntity<?> intake(Principal principal, @PathVariable String venueId, HttpServletRequest request) throws IOException {
        if (principal == null) {
            throw new CommercialPolicyDraft.Failure(401, "Sign-in required");
        }
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            throw CommercialPolicyDraft.invalid();
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new CommercialPolicyDraft.Failure(413, "Trusted approval request is too large");
        }
        var body = readBody(bytes);
        var actor = CommercialPolicyDraftRules.id(principal.getName());
        var venue = CommercialPolicyDraftRules.id(venueId);
        var result = service.intake(actor, venue, CommercialPolicyDraftRules.id(body.get("policyId")), body.get("envelope"));
        return response(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201, result);
    }

    private static Map<String, String> readBody(byte[] bytes) {
        try (var parser = JSON.createParser(bytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw CommercialPolicyDraft.invalid();
            }
            var fields = new java.util.HashMap<String, String>();
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                var name = parser.currentName();
                if (!Set.of("policyId", "envelope").contains(name) || parser.nextToken() != JsonToken.VALUE_STRING) {
                    throw CommercialPolicyDraft.invalid();
                }
                fields.put(name, parser.getString());
            }
            if (parser.currentToken() != JsonToken.END_OBJECT || parser.nextToken() != null || fields.size() != 2) {
                throw CommercialPolicyDraft.invalid();
            }
            return fields;
        } catch (tools.jackson.core.JacksonException malformed) {
            throw CommercialPolicyDraft.invalid();
        }
    }

    @ExceptionHandler(CommercialPolicyDraft.Failure.class)
    ResponseEntity<?> rejected(CommercialPolicyDraft.Failure error, Principal principal) {
        if (error.status == 403) {
            return denied(principal);
        }
        return response(error.status, Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<?> authentication() {
        return response(401, Map.of("message", "Session expired; sign in again"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IOException.class})
    ResponseEntity<?> unavailable(Exception error, Principal principal) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                var state = sql.getSQLState();
                if ("28000".equals(state)) {
                    return authentication();
                }
                if ("42501".equals(state)) {
                    return denied(principal);
                }
                if (Set.of("23505", "23503", "40001").contains(state == null ? "" : state)) {
                    return response(409, Map.of("message", "Trusted approval conflicts with current state"));
                }
                if ("23514".equals(state)) {
                    return response(409, Map.of("message", "Trusted approval does not match the current policy"));
                }
                break;
            }
        }
        return response(503, Map.of("message", "Trusted approval operation unavailable"));
    }

    private ResponseEntity<?> denied(Principal principal) {
        try {
            audit.record(principal == null ? null : UUID.fromString(principal.getName()));
            return response(403, Map.of("message", "Trusted approval denied"));
        } catch (RuntimeException unavailable) {
            return response(503, Map.of("message", "Trusted approval audit unavailable"));
        }
    }

    private static ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).header("Cache-Control", "private, no-store")
            .header("X-Content-Type-Options", "nosniff").body(body);
    }
}

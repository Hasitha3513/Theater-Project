package com.cinenest.pricing.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

@RestController
@RequestMapping("/api/admin/venues/{venueId}/commercial-policies/{policyId}/activation")
class OperationalCommercialPolicyController {
    private static final JsonFactory JSON = JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final OperationalCommercialPolicyService policies;

    OperationalCommercialPolicyController(OperationalCommercialPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    ResponseEntity<Map<String, Object>> permission(Principal principal, @PathVariable String venueId,
            @PathVariable String policyId, HttpServletRequest request) {
        UUID actor = actor(principal);
        UUID venue = DraftRules.id(venueId);
        UUID policy = DraftRules.id(policyId);
        rejectQuery(request);
        return response(200, policies.activationContext(actor, venue, policy));
    }

    @PostMapping
    ResponseEntity<Map<String, UUID>> activate(Principal principal, @PathVariable String venueId,
            @PathVariable String policyId, HttpServletRequest request) {
        UUID actor = actor(principal);
        var command = OperationalCommercialPolicyService.Command.parse(DraftRules.id(venueId),
            DraftRules.id(policyId), body(request));
        return response(200, Map.of("activationId", policies.activate(actor, command)));
    }

    @ExceptionHandler(DraftFailure.class)
    ResponseEntity<Map<String, String>> denial(DraftFailure failure) {
        return response(failure.status, Map.of("message", failure.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> authentication() {
        return response(401, Map.of("message", "Session expired; sign in again"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Map<String, String>> persistence(RuntimeException failure) {
        return databaseFailure(failure);
    }

    static UUID actor(Principal principal) {
        if (principal == null) {
            throw new DraftFailure(401, "Sign-in required");
        }
        return DraftRules.id(principal.getName());
    }

    static Map<String, Object> body(HttpServletRequest request) {
        rejectQuery(request);
        if (request.getContentType() == null
            || !"application/json".equalsIgnoreCase(request.getContentType().split(";", 2)[0].strip())) {
            throw DraftRules.invalid("content type");
        }
        try {
            byte[] bytes = request.getInputStream().readNBytes(16385);
            if (bytes.length > 16384) {
                throw new DraftFailure(413, "Commercial request exceeds 16 KiB");
            }
            try (var parser = JSON.createParser(bytes)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw DraftRules.invalid("JSON object");
                }
                parser.skipChildren();
                if (parser.nextToken() != null) {
                    throw DraftRules.invalid("JSON object");
                }
            }
            var input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            return JSONObjectUtils.parse(input);
        } catch (DraftFailure invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw DraftRules.invalid("JSON object");
        }
    }

    private static void rejectQuery(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw DraftRules.invalid("query");
        }
    }

    static ResponseEntity<Map<String, String>> databaseFailure(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                && Set.of("23P01", "23505", "40001").contains(sql.getSQLState())) {
                return response(409, Map.of("message", "Commercial command conflicts with current state"));
            }
        }
        return response(503, Map.of("message", "Commercial operation unavailable"));
    }

    static <T> ResponseEntity<T> response(int status, T body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff").body(body);
    }
}

package com.cinenest.payments.internal;

import com.cinenest.booking.ExtensionReservationContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

@RestController
@RequestMapping("/api/bookings/{booking}/extension-reservations/{reservation}/funding-request")
class ExtensionFundingController {
    private static final JsonFactory JSON =
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ExtensionFundingService service;

    ExtensionFundingController(ExtensionFundingService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json")
    ResponseEntity<?> create(Principal principal, @PathVariable String booking,
                             @PathVariable String reservation, @RequestBody byte[] body) {
        return response(service.create(actor(principal), BankInstructions.canonical(booking),
                BankInstructions.canonical(reservation), parse(body)));
    }

    @GetMapping
    ResponseEntity<?> read(Principal principal, @PathVariable String booking, @PathVariable String reservation) {
        return response(service.read(actor(principal), BankInstructions.canonical(booking),
                BankInstructions.canonical(reservation), null));
    }

    @GetMapping("/commands/{key}")
    ResponseEntity<?> recover(Principal principal, @PathVariable String booking, @PathVariable String reservation,
                              @PathVariable String key) {
        return response(service.read(actor(principal), BankInstructions.canonical(booking),
                BankInstructions.canonical(reservation), BankInstructions.canonical(key)));
    }

    private static ResponseEntity<?> response(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);
    }

    private static UUID actor(Principal principal) {
        if (principal == null) throw new BankFailure(401, "Sign-in required");
        return BankService.id(principal.getName());
    }

    private static ExtensionFundingService.Command parse(byte[] body) {
        if (body.length > 8192) throw invalid();
        try {
            try (var parser = JSON.createParser(body)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
                parser.skipChildren();
                if (parser.nextToken() != null) throw invalid();
            }
            var value = JSONObjectUtils.parse(new String(body, StandardCharsets.UTF_8));
            BankInstructionPublicationPolicy.keys(value, "commandKey", "venueId", "purchasedRevision",
                    "reservationRevision", "packageVersionId",
                    "termsVersion", "amountMinor", "currency", "acknowledged");
            return new ExtensionFundingService.Command(
                    BankInstructionPublicationPolicy.id(value.get("commandKey")),
                    BankInstructionPublicationPolicy.id(value.get("venueId")),
                    BankInstructionPublicationPolicy.revision(value.get("purchasedRevision")),
                    BankInstructionPublicationPolicy.revision(value.get("reservationRevision")),
                    BankInstructionPublicationPolicy.id(value.get("packageVersionId")),
                    text(value, "termsVersion"), text(value, "amountMinor"),
                    text(value, "currency"), Boolean.TRUE.equals(value.get("acknowledged")));
        } catch (Exception invalid) {
            throw invalid();
        }
    }

    private static String text(Map<String, Object> value, String key) {
        if (!(value.get(key) instanceof String text)) throw invalid();
        return text;
    }

    private static BankFailure invalid() {
        return new BankFailure(400, "Invalid extension funding request");
    }

    @ExceptionHandler(BankFailure.class)
    ResponseEntity<?> rejected(BankFailure failure) {
        return error(failure.status, failure.getMessage());
    }

    @ExceptionHandler(ExtensionReservationContext.Rejected.class)
    ResponseEntity<?> reservationRejected(ExtensionReservationContext.Rejected failure) {
        return error(failure.status(), "Extension reservation unavailable");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> constraint() {
        return error(409, "Extension funding request could not be committed");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<?> unavailable() {
        return error(503, "Extension funding request unavailable");
    }

    private static ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore().cachePrivate())
                .body(Map.of("message", message));
    }
}

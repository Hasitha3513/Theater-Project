package com.cinenest.pricing;
import java.util.Optional;
import java.util.UUID;
/** Read-only owned synthetic quotation context. Never renews inventory or authorizes a purchase. */
public interface VerificationQuotation {
    record Context(UUID quote, UUID owner, UUID venue) {}
    Optional<Context> ownedSynthetic(UUID actor, UUID quote);
}

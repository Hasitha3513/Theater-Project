package com.cinenest.pricing;
import java.util.Optional;
import java.util.UUID;
/** Current accepted BANK synthetic handoff under the caller transaction. Pricing locks and
 * revalidates Booking facts; empty also covers missing/unowned quotes. Never renews inventory,
 * changes historical acceptance, or authorizes a purchase. Recheck at each admission boundary. */
public interface VerificationQuotation {
    record Context(UUID quote, UUID owner, UUID venue) {}
    Optional<Context> ownedSynthetic(UUID actor, UUID quote);
}

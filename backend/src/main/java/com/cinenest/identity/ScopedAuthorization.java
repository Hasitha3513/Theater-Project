package com.cinenest.identity;

import java.util.Set;
import java.util.UUID;

/** Identity-owned trusted grant boundary. Locking checks participate in the caller's transaction. */
public interface ScopedAuthorization {
    /** Trusted Identity evidence; callers cannot substitute approval-reference strings. */
    record MatchedGrant(UUID account, long accountRevision, long credentialEpoch,
                        UUID venue, UUID grantId, long grantRevision, String permission) {}
    /** Default-denied, epoch-bound exact grant evidence. Locking requires a caller transaction. */
    default java.util.Optional<MatchedGrant> matchedGrant(UUID actor, UUID venue, String permission, boolean lock) {
        return java.util.Optional.empty();
    }
    default boolean operationalPrincipal(UUID actor, boolean lock) { return false; }

    default java.util.Optional<MatchedGrant> matchedOperationalGrant(
        UUID actor, UUID venue, String permission, boolean lock) {
        return java.util.Optional.empty();
    }

    default boolean permitsSelf(UUID actor,String action,boolean lock){return false;}
    /** Current venue membership; default denies for adapters without this read contract. */
    default boolean member(UUID actor,UUID venue,boolean lock){return false;}
    boolean eligible(UUID actor);
    boolean eligible(UUID actor, boolean lock);
    boolean permits(UUID actor, UUID venue, String action, boolean lock);
    Set<UUID> venues(UUID actor, String action);
}

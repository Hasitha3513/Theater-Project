package com.cinenest.identity;

import java.util.Set;
import java.util.UUID;

/** Identity-owned trusted grant boundary. Locking checks participate in the caller's transaction. */
public interface ScopedAuthorization {
    boolean eligible(UUID actor);
    boolean permits(UUID actor, UUID venue, String action, boolean lock);
    Set<UUID> venues(UUID actor, String action);
}

package com.cinenest.identity;

import java.util.Set;
import java.util.UUID;

/** Identity-owned trusted grant boundary. Locking checks participate in the caller's transaction. */
public interface ScopedAuthorization {
    default boolean permitsSelf(UUID actor,String action,boolean lock){return false;}
    boolean eligible(UUID actor);
    boolean eligible(UUID actor, boolean lock);
    boolean permits(UUID actor, UUID venue, String action, boolean lock);
    Set<UUID> venues(UUID actor, String action);
}

package com.cinenest.verification.internal;
import java.util.*;
interface PrivateEvidenceStore {
    void put(UUID key,byte[] bytes);
    byte[] get(UUID key);
    void delete(UUID key);
    Set<UUID> olderThan(java.time.Instant cutoff);
}

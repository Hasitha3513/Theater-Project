package com.cinenest.iot.internal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Closed qualification catalogue; no native device identifiers, credentials or network I/O. */
@Component
final class LockRegistryReferenceResolver {
    record Reference(UUID id, UUID version) {
        @Override public String toString() { return "Reference[REDACTED]"; }
    }
    record Identity(UUID scope, UUID identity) {
        @Override public String toString(){return "Identity[REDACTED]";}
    }
    static UUID fixture(String text) { return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)); }
    Identity resolve(UUID venue, Reference ref) {
        if (!ref.version().equals(fixture("LOCK-V1"))) throw new LockRegistryPolicy.Failure(404);
        for (int n=1;n<=64;n++) {
            if (ref.id().equals(fixture("LOCK-REF-"+n)) || ref.id().equals(fixture("LOCK-ALIAS-"+n))) {
                if (!venue.equals(fixture("venue-a"))) throw new LockRegistryPolicy.Failure(403);
                return new Identity(fixture("LOCK-SCOPE"), fixture("LOCK-IDENTITY-"+n));
            }
        }
        throw new LockRegistryPolicy.Failure(404);
    }
    void gateway(UUID venue, Reference ref) {
        if (ref==null) return;
        if (!venue.equals(fixture("venue-a")) || !ref.id().equals(fixture("LOCK-GATEWAY")) ||
                !ref.version().equals(fixture("LOCK-V1"))) throw new LockRegistryPolicy.Failure(404);
    }
    void declaration(UUID venue, UUID ref) {
        if (ref!=null && (!venue.equals(fixture("venue-a")) || !ref.equals(fixture("LOCK-DOCUMENT"))))
            throw new LockRegistryPolicy.Failure(404);
    }
}

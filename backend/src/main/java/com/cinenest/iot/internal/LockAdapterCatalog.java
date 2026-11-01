package com.cinenest.iot.internal;

import com.cinenest.iot.LockIntegration.*;
import java.util.*;

/** Explicit inert descriptors only: never constructs, loads or invokes an adapter. */
final class LockAdapterCatalog {
    private final Map<AdapterId, Descriptor> descriptors;
    LockAdapterCatalog() { this(List.of()); }
    LockAdapterCatalog(Collection<Descriptor> declared) {
        Objects.requireNonNull(declared); var copy = new HashMap<AdapterId, Descriptor>();
        for (var descriptor : declared) {
            Objects.requireNonNull(descriptor);
            if (copy.putIfAbsent(descriptor.id(), descriptor) != null)
                throw new IllegalArgumentException("Duplicate adapter identity");
        }
        descriptors = Map.copyOf(copy);
    }
    Descriptor lookup(AdapterId exactIdentity) {
        var descriptor = descriptors.get(Objects.requireNonNull(exactIdentity));
        if (descriptor == null) throw new IllegalArgumentException("Unknown adapter identity");
        return descriptor;
    }
    int size() { return descriptors.size(); }
}

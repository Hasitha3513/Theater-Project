package com.cinenest.verification.internal;
import java.time.Instant;
import java.util.*;
/** Synthetic fault injection only; not an application provider or test success adapter. */
public final class VerificationFaults {
    private VerificationFaults(){}
    public static Object scannerUnavailable(){return new SyntheticEvidenceProcessor(){@Override Result process(byte[] bytes,String mime,VerificationPolicy.Policy policy){throw new VerificationFailure(503,"SIMULATED scanner unavailable");}};}
    public static Object deletionUnavailable(Object original){var delegate=(PrivateEvidenceStore)original;return new PrivateEvidenceStore(){public void put(UUID key,byte[] bytes){delegate.put(key,bytes);}public byte[] get(UUID key){return delegate.get(key);}public void delete(UUID key){throw new VerificationFailure(503,"SIMULATED deletion unavailable");}public Set<UUID> olderThan(Instant cutoff){return delegate.olderThan(cutoff);}};}
}

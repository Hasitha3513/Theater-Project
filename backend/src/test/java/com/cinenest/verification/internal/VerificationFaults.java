package com.cinenest.verification.internal;
import java.time.Instant;
import java.util.*;
/** Synthetic fault injection only; not an application provider or test success adapter. */
public final class VerificationFaults {
    private VerificationFaults(){}
    public static Object scannerUnavailable(){return new SyntheticEvidenceProcessor(){@Override Result process(byte[] bytes,String mime,VerificationPolicy.Policy policy){throw new VerificationFailure(503,"SIMULATED scanner unavailable");}};}
    public static Object deletionUnavailable(Object original){var delegate=(PrivateEvidenceStore)original;return new PrivateEvidenceStore(){public void put(UUID key,byte[] bytes){delegate.put(key,bytes);}public byte[] get(UUID key){return delegate.get(key);}public void delete(UUID key){throw new VerificationFailure(503,"SIMULATED deletion unavailable");}public Set<UUID> olderThan(Instant cutoff){return delegate.olderThan(cutoff);}};}
    public static Object firstScannerDelayed(java.util.concurrent.CountDownLatch entered,java.util.concurrent.CountDownLatch release){return new SyntheticEvidenceProcessor(){final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();@Override Result process(byte[] bytes,String mime,VerificationPolicy.Policy policy){if(calls.getAndIncrement()==0){entered.countDown();try{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Synthetic worker test timed out");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}throw new VerificationFailure(503,"SIMULATED expired worker failure");}return super.process(bytes,mime,policy);}};}

}

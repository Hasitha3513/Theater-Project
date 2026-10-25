package com.cinenest.payments.internal;
public final class BankFaults {
 private BankFaults(){}
 public static Object noticeFailure(){return (ReviewerNotice)request->{throw new BankFailure(503,"SIMULATED disabled-delivery failure");};}
 public static Object delayedScanner(java.util.concurrent.CountDownLatch entered,java.util.concurrent.CountDownLatch release){return new SyntheticEvidenceProcessor(){@Override public Result process(byte[] bytes,String mime,BankPolicy.Policy policy){entered.countDown();try{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Synthetic interruption timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new BankFailure(503,"SIMULATED interrupted worker");}};}
}

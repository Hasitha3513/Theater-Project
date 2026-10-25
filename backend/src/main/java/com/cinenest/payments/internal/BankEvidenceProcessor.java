package com.cinenest.payments.internal;

/** Payments-owned admission/processing seam. Admission never grants financial authority. */
interface BankEvidenceProcessor {
    record Result(String digest, String mime, byte[] preview) {}
    Result process(byte[] bytes, String mime, BankPolicy.Policy policy);
    default void admit(byte[] bytes, String mime, BankPolicy.Policy policy) {
        process(bytes, mime, policy);
    }
}

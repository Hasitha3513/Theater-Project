package com.cinenest.payments.internal;

/** Qualification-only boundary. No configuration, role or clean scan enables customer intake. */
final class ReceiptAdmissionPolicy {
    enum Decision { DENY }

    Decision decision() { return Decision.DENY; }

    void requireAdmission() {
        throw new BankFailure(503, "Real receipt admission is disabled");
    }
}

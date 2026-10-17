package com.cinenest.verification.internal;
final class VerificationFailure extends RuntimeException {
    final int status;
    VerificationFailure(int status,String safeMessage){super(safeMessage);this.status=status;}
}

package com.cinenest.payments.internal;
final class BankFailure extends RuntimeException {
    final int status;
    BankFailure(int status,String safeMessage){super(safeMessage);this.status=status;}
}

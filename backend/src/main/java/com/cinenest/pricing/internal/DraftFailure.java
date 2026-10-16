package com.cinenest.pricing.internal;
final class DraftFailure extends RuntimeException {
 final int status;
 DraftFailure(int status,String message){super(message);this.status=status;}
}

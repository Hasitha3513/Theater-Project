package com.cinenest.booking.internal;

final class InventoryFailure extends RuntimeException {
    final int status;
    final String reason;
    InventoryFailure(int status,String message) { this(status,message,null); }
    InventoryFailure(int status,String message,String reason) {super(message);this.status=status;this.reason=reason;}
}

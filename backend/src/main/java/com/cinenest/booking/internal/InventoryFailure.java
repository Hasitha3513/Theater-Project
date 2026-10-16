package com.cinenest.booking.internal;

final class InventoryFailure extends RuntimeException {
    final int status;
    InventoryFailure(int status,String message) { super(message); this.status=status; }
}

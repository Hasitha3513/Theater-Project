package com.cinenest.rooms.internal;

final class RoomFailure extends RuntimeException {
    final int status;
    RoomFailure(int status, String message) { super(message); this.status=status; }
}

package com.cinenest.rooms;

import java.util.Map;
import java.util.UUID;

/** Rooms-owned synthetic checkout handoff. No physical clearance or inventory authority. */
public interface TurnoverHandoff {
    final class Failure extends RuntimeException {
        private final int status;
        public Failure(int status,String message){super(message);this.status=status;}
        public int status(){return status;}
    }
    Map<String,Object> handoff(UUID actor, UUID venue, UUID booking, UUID checkout);
    Map<String,Object> handoffStatus(UUID actor, UUID venue, UUID booking, UUID checkout);
}

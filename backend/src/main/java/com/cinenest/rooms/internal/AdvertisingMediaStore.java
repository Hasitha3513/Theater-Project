package com.cinenest.rooms.internal;

import java.util.Objects;
import java.util.UUID;

/** Rooms-owned storage port. No runtime implementation, public delivery or provider dependency. */
interface AdvertisingMediaStore {
    record Identity(UUID objectId, UUID venueId, long revision) {
        public Identity { Objects.requireNonNull(objectId); Objects.requireNonNull(venueId); if(revision<1)throw new IllegalArgumentException("Invalid media revision"); }
    }
    record Version(String value) {
        public Version { if(value==null||value.isBlank()||value.length()>512||value.equals("null")||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid opaque storage version"); }
        @Override public String toString(){return "Version[redacted]";}
    }
    record Reference(Identity identity, Version version, String sha256, long bytes) {
        public Reference { Objects.requireNonNull(identity); Objects.requireNonNull(version); if(sha256==null||!sha256.matches("[a-f0-9]{64}")||bytes<1)throw new IllegalArgumentException("Invalid media reference"); }
        @Override public String toString(){return "MediaReference[redacted]";}
    }
    enum Outcome { INVALID, NOT_FOUND, STALE, INTEGRITY_FAILED, CONFLICT, UNAVAILABLE, UNSUPPORTED, LIMIT_EXCEEDED }
    final class Failure extends RuntimeException {
        private final Outcome outcome;
        public Failure(Outcome outcome){super("Local media storage: "+outcome);this.outcome=outcome;}
        public Outcome outcome(){return outcome;}
    }
    Reference store(Identity identity, UUID commandId, byte[] syntheticImage);
    byte[] readExact(Reference reference);
    void removeExact(Reference ownedReference);
}

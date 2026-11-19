package com.cinenest.rooms;
import java.util.*;
/** Consumer-owned read port; source owners validate their own facts. Never proves physical clearance. */
public interface OperationsEvidence {
 record Checkout(UUID event,UUID booking,UUID venue,UUID room,long bookingRevision) {}
 default Optional<Checkout> checkout(UUID event,UUID venue,UUID booking){return Optional.empty();}
 String source();
 Optional<Fact> inspect(UUID reference, UUID venue, UUID room);
 record Fact(UUID reference,long revision,String reportedState) {}
}

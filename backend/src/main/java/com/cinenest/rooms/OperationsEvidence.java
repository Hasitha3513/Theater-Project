package com.cinenest.rooms;
import java.util.*;
/** Consumer-owned read port; source owners validate their own facts. Never proves physical clearance. */
public interface OperationsEvidence {
 String source();
 Optional<Fact> inspect(UUID reference, UUID venue, UUID room);
 record Fact(UUID reference,long revision,String reportedState) {}
}

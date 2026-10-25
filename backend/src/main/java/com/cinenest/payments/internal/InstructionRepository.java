package com.cinenest.payments.internal;
import java.time.Instant;
import java.util.*;
interface InstructionRepository {
 record Revision(UUID id,UUID venue,String version,UUID reference,UUID referenceVersion,String digest,Instant from,Instant until,Instant approved){}
 record Target(UUID owner,Optional<Revision> revision){}
 Optional<Revision> revision(UUID venue,UUID id);
 Optional<Revision> version(UUID venue,String version);
 Optional<Target> request(UUID venue,UUID request);
 Instant now();
 void bind(UUID request,Revision revision);
 void audit(UUID actor,UUID venue,UUID revision,UUID request,String action,String outcome,String reason,Instant observed,UUID correlation);
}

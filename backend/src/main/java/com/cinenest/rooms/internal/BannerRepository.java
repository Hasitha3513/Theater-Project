package com.cinenest.rooms.internal;
import java.time.Instant;import java.util.*;
interface BannerRepository {
 record Banner(UUID id,UUID venue,long version,long draft,Long published){}
 record Revision(long number,BannerPolicy.Content content,UUID author,String hash,String assetHash){}
 record Command(UUID banner,UUID venue,String action,String hash,long result){}
 boolean venueVisible(UUID venue);Instant now();void commandLock(UUID actor,UUID command);Optional<Command> command(UUID actor,UUID command);
 Banner banner(UUID venue,UUID id,boolean lock);List<Banner> list(UUID venue);Revision revision(UUID id,long number);List<Revision> history(UUID id);
 void create(Banner b);void update(Banner b);void revision(Banner b,Revision r);UUID audit(UUID actor,UUID venue,UUID id,String action,String outcome,long before,long after);
 void command(UUID actor,UUID command,UUID venue,UUID id,String action,String hash,long result,UUID audit);List<Banner> live(UUID venue);
}

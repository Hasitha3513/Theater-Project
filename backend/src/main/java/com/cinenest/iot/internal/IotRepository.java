package com.cinenest.iot.internal;
import java.util.*;import java.time.*;import com.cinenest.booking.BookingAccess;
/** IoT-owned persistence port; all operations participate in the caller transaction. */
interface IotRepository {
 Instant now();Map<String,Object> device(UUID id,UUID venue,boolean lock);Map<String,Object> command(UUID id,UUID device);
 void audit(UUID actor,UUID venue,UUID device,UUID command,String action,String outcome);void rate(UUID actor,int seconds,int count);void expire(UUID device);void state(UUID command,String state);
 List<Map<String,Object>> commands(UUID device);List<Map<String,Object>> auditRows(UUID device);List<Map<String,Object>> devices(UUID venue,int page,int size);
 Optional<Map<String,Object>> receipt(UUID actor,UUID key);void receipt(UUID actor,UUID key,String hash,UUID command,String action);
 void create(UUID command,UUID device,UUID actor,BookingAccess.Context booking,String desired,Instant expiry);int attempts(UUID command);void evidence(UUID command,UUID device,UUID actor,String action,LightingSimulator.Evidence evidence);List<UUID> expiryDevices();void register(UUID id,UUID venue,UUID room);void configure(UUID id,UUID venue,boolean enabled,boolean online);
 static Instant time(Object x){return x instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)x).toInstant();}
}

package com.cinenest.booking.internal;
import com.cinenest.booking.BookingAccess;import com.cinenest.identity.ScopedAuthorization;import com.cinenest.rooms.PackageConstraints;
import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
/** Booking aggregate owner. Rooms/Identity facts only cross published ports. */
@Service
class GuestRosterService {
 private final GuestRosterStore store;private final BookingAccess bookings;private final ScopedAuthorization auth;private final PackageConstraints rooms;private final Environment env;private final boolean enabled;private InventoryRepository inventory;
 GuestRosterService(GuestRosterStore store,BookingAccess bookings,ScopedAuthorization auth,PackageConstraints rooms,Environment env,InventoryRepository inventory,@Value("${cinenest.booking.guests.enabled:false}")boolean enabled){this.store=store;this.bookings=bookings;this.auth=auth;this.rooms=rooms;this.env=env;this.inventory=inventory;this.enabled=enabled;}
 private void configured(){if(!enabled||!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic guest roster unavailable");}
 private BookingAccess.Context context(UUID actor,UUID booking,UUID venue){
  configured();if(!auth.eligible(actor,true))throw new InventoryFailure(403,"Access denied");
  var b=bookings.lock(booking).orElseThrow(()->new InventoryFailure(404,"Synthetic booking not found"));
  if(venue==null){if(!b.owner().equals(actor))throw new InventoryFailure(404,"Synthetic booking not found");}
  else if(!venue.equals(b.venue())||!auth.permits(actor,b.venue(),"guest.synthetic.read",true))throw new InventoryFailure(403,"Roster read denied");
  return b;
 }
 private int purchased(UUID booking){return store.purchased(booking);}
 private int capacity(BookingAccess.Context b){var facts=rooms.inspect(b.venue(),Set.of(b.room()),false,true).orElseThrow(()->new InventoryFailure(409,"Current room capacity unavailable"));return facts.rooms().stream().filter(r->r.roomId().equals(b.room())).findFirst().orElseThrow(()->new InventoryFailure(409,"Current room capacity unavailable")).capacity();}
 private Optional<Long> revision(UUID b){return store.revision(b);}
 private void audit(UUID a,UUID b,String action){store.audit(a,b,action);}
 private Map<String,Object> view(BookingAccess.Context b,boolean staff){
  int purchased=purchased(b.booking()),capacity=capacity(b);var revision=revision(b.booking());
  var entries=store.entries(b.booking());
  int remaining=Math.max(0,Math.min(purchased,capacity)-entries.size());boolean live=inventory.now().isBefore(b.start());
  Map<String,Object> out=new LinkedHashMap<>();out.put("bookingId",b.booking());out.put("bookingRevision",b.revision());out.put("venueId",b.venue());out.put("sessionStart",b.start().toString());out.put("serverNow",inventory.now().toString());out.put("purchasedLimit",purchased);out.put("roomCapacity",capacity);out.put("count",entries.size());out.put("remaining",remaining);out.put("revision",revision.orElse(0L));out.put("initialized",revision.isPresent());out.put("entries",entries);out.put("synthetic",true);out.put("status","UNVERIFIED");out.put("physicalAdmission",false);out.put("ageVerified",false);out.put("accessIssued",false);out.put("permittedActions",staff||!live||remaining==0?List.of():List.of(revision.isPresent()?"ADD":"INITIALIZE"));return out;
 }
 @Transactional public Object read(UUID actor,UUID booking,UUID venue){var b=context(actor,booking,venue);var result=view(b,venue!=null);audit(actor,booking,"READ");return result;}
 private static long number(Map<String,Object> body,String key){Object n=body.get(key);if(!(n instanceof Integer||n instanceof Long)||((Number)n).longValue()<0)throw InventoryRules.invalid();return ((Number)n).longValue();}
 private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional public Object command(UUID actor,UUID booking,boolean add,Map<String,Object> body){
  var b=context(actor,booking,null);var allowed=add?Set.of("commandKey","bookingRevision","revision","label"):Set.of("commandKey","bookingRevision","revision");if(!body.keySet().equals(allowed))throw InventoryRules.invalid();
  UUID key=InventoryRules.id(InventoryRules.text(body,"commandKey"));long expected=number(body,"revision"),bookingRevision=number(body,"bookingRevision");String label=add?GuestRules.label(body.get("label")):"BOOKER";
  String hash=digest(booking+":"+add+":"+bookingRevision+":"+expected+":"+label);
  var prior=store.command(actor,key);
  if(prior.isPresent()){if(!prior.get().equals(hash))throw new InventoryFailure(409,"Retry key belongs to another command");var result=view(b,false);audit(actor,booking,"REPLAY");return result;}
  if(!inventory.now().isBefore(b.start())||bookingRevision!=b.revision())throw new InventoryFailure(409,"Session started or booking changed");
  var revision=revision(booking);int limit=Math.min(purchased(booking),capacity(b));
  if(!add&&revision.isPresent()){var result=view(b,false);audit(actor,booking,"ALREADY_INITIALIZED");return result;}
  if(expected!=revision.orElse(0L)||add&&revision.isEmpty())throw new InventoryFailure(409,"Roster changed; refresh");
  int count=store.entries(booking).size();
  if(count>=limit)throw new InventoryFailure(409,"Purchased or room capacity reached");
  if(!add)store.initialize(booking);
  store.append(booking,add?"COMPANION":"BOOKER",label,add?null:b.owner());
  if(add)store.advance(booking);
  store.remember(actor,key,booking,hash);
  audit(actor,booking,add?"ADD":"INITIALIZE");return view(b,false);
 }
}

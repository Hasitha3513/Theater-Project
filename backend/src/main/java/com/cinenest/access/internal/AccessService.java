package com.cinenest.access.internal;
import com.cinenest.booking.BookingAccess;import com.cinenest.payments.AccessFunding;import com.cinenest.identity.ScopedAuthorization;import com.cinenest.verification.BankEligibility;
import java.util.*;import java.time.*;import java.security.MessageDigest;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
class AccessService {
 private final AccessRepository repo;private final AccessPolicy policy;private final BookingAccess bookings;private final AccessFunding funding;private final ScopedAuthorization auth;private final BankEligibility verification;private final AccessSimulator simulator;
 AccessService(AccessRepository repo,AccessPolicy policy,BookingAccess bookings,AccessFunding funding,ScopedAuthorization auth,BankEligibility verification,AccessSimulator simulator){this.repo=repo;this.policy=policy;this.bookings=bookings;this.funding=funding;this.auth=auth;this.verification=verification;this.simulator=simulator;}
 private static UUID id(Map<String,Object> r,String key){return (UUID)r.get(key);}
 private static long n(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
 private static Instant instant(Map<String,Object> r,String key){Object v=r.get(key);return v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();}
 private void permit(UUID actor,UUID venue,String action,boolean self){if(!(self?auth.permitsSelf(actor,action,true):auth.permits(actor,venue,action,true)))throw new AccessFailure(403);}
 private void rate(UUID actor){if(repo.rate(actor,policy.positive("rate-seconds"))>=policy.positive("rate-count"))throw new AccessFailure(429);}
 private BookingAccess.Context context(UUID booking){return bookings.lock(booking).orElseThrow(()->new AccessFailure(409));}
 private void qualified(BookingAccess.Context b,UUID actor,boolean self,String permission,boolean active){
  if(!auth.eligible(b.owner(),true))throw new AccessFailure(403);
  if(!funding.eligible(b.request(),b.owner(),b.venue(),b.amount(),b.currency())||!verification.eligible(b.owner(),b.quote(),b.venue()))throw new AccessFailure(409);
  if(self&&!b.owner().equals(actor))throw new AccessFailure(403);
  permit(actor,b.venue(),permission,self);
  if(!auth.eligible(b.owner(),false))throw new AccessFailure(403);
  Instant now=repo.now();if(!now.isBefore(b.end())||(active&&now.isBefore(b.start())))throw new AccessFailure(409);
 }
 private Map<String,Object> normalize(Map<String,Object> r){
  var now=repo.now();String state=r.get("state").toString();boolean expired=!now.isBefore(instant(r,"ends_at"));
  if((state.equals("SCHEDULED")||state.equals("ACTIVE"))&&(expired||state.equals("SCHEDULED")&&!now.isBefore(instant(r,"starts_at")))){r.put("state",expired?"EXPIRED":"ACTIVE");if(expired)r.put("protected_secret",null);repo.save(r);}
  if(r.get("protected_secret")!=null&&!now.isBefore(instant(r,"retain_until"))){r.put("protected_secret",null);repo.save(r);}
  return r;
 }
 private void binding(BookingAccess.Context b,Map<String,Object> r,long expected){if(b.revision()!=expected||n(r,"booking_revision")!=b.revision()||!b.venue().equals(id(r,"venue_id"))||!b.room().equals(id(r,"room_id"))||!b.owner().equals(id(r,"owner_id"))||!b.start().equals(instant(r,"starts_at"))||!b.end().equals(instant(r,"ends_at")))throw new AccessFailure(409);}
 private boolean installed(Map<String,Object> r){var w=repo.work(id(r,"credential_id"));return "SIMULATOR_INSTALLED".equals(w.get("state"))&&w.get("operation_id").equals(w.get("evidence_operation"))&&n(w,"credential_version")==n(r,"version")&&n(w,"evidence_version")==n(r,"version");}
 private Map<String,Object> view(UUID actor,Map<String,Object> r,boolean admin){
  normalize(r);UUID id=id(r,"credential_id");var w=repo.work(id);Map<String,Object> v=new LinkedHashMap<>();v.put("id",id);v.put("bookingId",r.get("booking_id"));v.put("bookingRevision",r.get("booking_revision"));v.put("venueId",r.get("venue_id"));v.put("roomId",r.get("room_id"));v.put("version",r.get("version"));v.put("revision",r.get("revision"));v.put("state",r.get("state"));v.put("provisioning",w.get("state"));v.put("operationId",w.get("operation_id"));v.put("sessionStart",instant(r,"starts_at").toString());v.put("sessionEnd",instant(r,"ends_at").toString());v.put("timezone",r.get("timezone"));v.put("disclosed",r.get("disclosed_at")!=null);v.put("synthetic",true);v.put("doorControlled",false);v.put("label","SYNTHETIC ACCESS TEST — NO DOOR CONTROL");
  var actions=new ArrayList<String>();String state=r.get("state").toString();boolean live=Set.of("SCHEDULED","ACTIVE").contains(state);
  if(admin){if(auth.permits(actor,id(r,"venue_id"),"access.synthetic.revoke",false))actions.add("REVOKE");if(auth.permits(actor,id(r,"venue_id"),"access.synthetic.simulate",false)&&live)actions.add("SIMULATE");}else{if(auth.permitsSelf(actor,"access.synthetic.issue",false))actions.add("REISSUE");if(live&&installed(r)&&r.get("protected_secret")!=null&&auth.permitsSelf(actor,"access.synthetic.reveal",false))actions.add("REVEAL");if(live&&installed(r)&&auth.permitsSelf(actor,"access.synthetic.attempt",false))actions.add("ATTEMPT");}
  v.put("history",repo.history(id));v.put("permittedActions",actions);return v;
 }
 @Transactional public void cleanup(){policy.configured();for(UUID id:repo.cleanupCandidates()){UUID booking=repo.bookingFor(id);repo.lock(booking);var r=repo.credential(id).orElseThrow();long before=n(r,"revision");normalize(r);if(n(r,"revision")!=before)repo.audit(null,id,booking,id(r,"venue_id"),"SECRET_RETENTION_OR_EXPIRY","APPLIED");}}
 @Transactional public Object status(UUID actor,UUID booking){policy.configured();repo.lock(booking);var b=context(booking);if(!b.owner().equals(actor))throw new AccessFailure(403);permit(actor,b.venue(),"access.synthetic.read",true);rate(actor);var r=repo.latest(booking);repo.audit(actor,r.map(x->id(x,"credential_id")).orElse(null),booking,b.venue(),"READ","ALLOW");return r.<Object>map(x->view(actor,x,false)).orElseGet(()->Map.of("synthetic",true,"doorControlled",false,"bookingId",booking,"bookingRevision",b.revision(),"state","NOT_ISSUED","permittedActions",auth.permitsSelf(actor,"access.synthetic.issue",false)?List.of("ISSUE"):List.of(),"label","SYNTHETIC ACCESS TEST — NO DOOR CONTROL"));}
 @Transactional public Object admin(UUID actor,UUID venue,UUID credential,int page,int size){policy.configured();permit(actor,venue,"access.synthetic.review",false);rate(actor);if(page<0||page>10000||size<1||size>50)throw new AccessFailure(400);List<Map<String,Object>> rows=new ArrayList<>();for(UUID id:credential==null?repo.ids(venue,page,size):List.of(credential)){UUID booking=repo.bookingFor(id);repo.lock(booking);var r=repo.credential(id).orElseThrow(()->new AccessFailure(404));if(!venue.equals(r.get("venue_id")))throw new AccessFailure(403);rows.add(view(actor,r,true));}repo.audit(actor,credential,null,venue,"ADMIN_READ","ALLOW");return credential==null?Map.of("items",rows,"page",page,"size",size,"synthetic",true,"doorControlled",false):rows.getFirst();}
 private static UUID uuid(Map<String,Object> body,String key){try{return UUID.fromString(Objects.toString(body.get(key),""));}catch(RuntimeException e){throw new AccessFailure(400);}}
 private static long number(Map<String,Object> body,String key){Object v=body.get(key);if(!(v instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())throw new AccessFailure(400);return n.longValue();}
 @Transactional public Map<String,Object> command(UUID actor,UUID booking,UUID credential,UUID venue,String kind,Map<String,Object> body){
  policy.configured();if(booking==null)booking=repo.bookingFor(credential);repo.lock(booking);
  // Serialize a principal's rate/grant checks; all commands for this booking share a durable lock.
  if(!auth.eligible(actor,true))throw new AccessFailure(403);rate(actor);
  String reason=Objects.toString(body.get("reason"),"");if(reason.isBlank()||reason.length()>240||body.size()>12)throw new AccessFailure(400);
  var allowed=new HashSet<>(List.of("reason","commandKey","expectedRevision","bookingRevision","expectedVersion"));switch(kind){case "ISSUE"->allowed.add("mode");case "ATTEMPT"->allowed.addAll(List.of("credentialId","token"));case "SIMULATE"->allowed.addAll(List.of("operationId","credentialVersion","fixture","reconcile"));default->{}}if(!allowed.containsAll(body.keySet()))throw new AccessFailure(400);
  UUID key=uuid(body,"commandKey");long expected=number(body,"expectedRevision"),expectedBooking=number(body,"bookingRevision"),expectedVersion=number(body,"expectedVersion");
  boolean self=venue==null;String action=switch(kind){case "ISSUE"->"access.synthetic.issue";case "REVEAL"->"access.synthetic.reveal";case "ATTEMPT"->"access.synthetic.attempt";case "REVOKE"->"access.synthetic.revoke";case "SIMULATE"->"access.synthetic.simulate";default->throw new AccessFailure(400);};
  var b=context(booking);if(self&&!b.owner().equals(actor)||!self&&!b.venue().equals(venue))throw new AccessFailure(403);permit(actor,b.venue(),action,self);
  var canonical=new TreeMap<String,Object>(body);String hash=HexFormat.of().formatHex(AccessPolicy.hash(kind+":"+booking+":"+credential+":"+venue+":"+com.nimbusds.jose.util.JSONObjectUtils.toJSONString(canonical)));
  var prior=repo.command(actor,key);if(prior.isPresent()){var p=prior.get();if(!hash.equals(p.get("payload_hash")))throw new AccessFailure(409);var r=repo.credential(id(p,"credential_id")).orElseThrow(()->new AccessFailure(409));var result=view(actor,r,!self);result.put("replayed",true);return result;}
  Map<String,Object> r;
  if(kind.equals("ISSUE")){
   qualified(b,actor,true,action,false);if(expectedBooking!=b.revision())throw new AccessFailure(409);
   var old=repo.latest(booking);String mode=Objects.toString(body.get("mode"),"");long version=1;
   if(old.isPresent()){var previous=normalize(old.get());if(!mode.equals("REISSUE")||(expected!=n(previous,"revision")||expectedVersion!=n(previous,"version")))throw new AccessFailure(409);if(Set.of("UNKNOWN","RECONCILIATION_REQUIRED").contains(repo.work(id(previous,"credential_id")).get("state")))throw new AccessFailure(409);version=n(previous,"version")+1;previous.put("state","SUPERSEDED");previous.put("protected_secret",null);repo.save(previous);repo.workState(id(previous,"credential_id"),"REVOKED",false,false);repo.audit(actor,id(previous,"credential_id"),booking,b.venue(),"SUPERSEDE","ALLOW");}else if(!mode.equals("ISSUE")||expected!=0||expectedVersion!=0)throw new AccessFailure(409);
   UUID id=UUID.randomUUID();String token=policy.token(),protection=policy.version();r=new HashMap<>();r.put("credential_id",id);r.put("booking_id",booking);r.put("booking_revision",b.revision());r.put("owner_id",b.owner());r.put("venue_id",b.venue());r.put("room_id",b.room());r.put("starts_at",OffsetDateTime.ofInstant(b.start(),ZoneOffset.UTC));r.put("ends_at",OffsetDateTime.ofInstant(b.end(),ZoneOffset.UTC));r.put("timezone",b.timezone());r.put("version",version);r.put("token_hash",AccessPolicy.hash(token));r.put("protected_secret",policy.protect(token,protection,id));r.put("key_version",protection);r.put("retain_until",OffsetDateTime.ofInstant(repo.now().plusSeconds(policy.positive("retention-seconds")),ZoneOffset.UTC));repo.create(r);r=repo.credential(id).orElseThrow();
  }else{
   if(credential==null)credential=uuid(body,"credentialId");r=repo.credential(credential).orElseThrow(()->new AccessFailure(404));if(!booking.equals(r.get("booking_id")))throw new AccessFailure(403);
   normalize(r);binding(b,r,expectedBooking);if(expected!=n(r,"revision")||expectedVersion!=n(r,"version"))throw new AccessFailure(409);
   if(kind.equals("REVOKE")){r.put("state","REVOKED");r.put("protected_secret",null);repo.save(r);repo.workState(credential,"REVOKED",false,false);}
   else{
    qualified(b,actor,self,action,kind.equals("REVEAL")||kind.equals("ATTEMPT"));
    if(!Set.of("SCHEDULED","ACTIVE").contains(r.get("state")))throw new AccessFailure(409);
    if(kind.equals("SIMULATE")){
     var w=repo.work(credential);if(!uuid(body,"operationId").equals(w.get("operation_id"))||number(body,"credentialVersion")!=n(r,"version"))throw new AccessFailure(409);
     String ws=w.get("state").toString();boolean reconcile=Boolean.TRUE.equals(body.get("reconcile"));
     if(reconcile&&!Set.of("UNKNOWN","RECONCILIATION_REQUIRED").contains(ws)||!reconcile&&!Set.of("PENDING_SIMULATION","FAILED").contains(ws))throw new AccessFailure(409);
     if(!reconcile&&n(w,"attempts")>=policy.positive("retry-limit"))throw new AccessFailure(429);
     String state=simulator.apply(id(w,"operation_id"),n(r,"version"),Objects.toString(body.get("fixture"),""),reconcile);repo.workState(credential,state,state.equals("SIMULATOR_INSTALLED"),!reconcile);repo.save(r);repo.audit(actor,credential,booking,b.venue(),reconcile?"RECONCILE_EVIDENCE":"SIMULATOR_EVIDENCE",state);
    }else if(kind.equals("REVEAL")){
     if(!installed(r)||r.get("protected_secret")==null||r.get("disclosed_at")!=null||repo.disclosures(credential)>=policy.positive("disclosure-limit"))throw new AccessFailure(409);
     String token=policy.reveal((byte[])r.get("protected_secret"),r.get("key_version").toString(),credential);r.put("protected_secret",null);r.put("disclosed_at",java.sql.Timestamp.from(repo.now()));repo.save(r);repo.command(actor,key,booking,credential,kind,hash);repo.audit(actor,credential,booking,b.venue(),kind,"ALLOW");var result=view(actor,r,false);result.put("token",token);return result;
    }else{
     if(!installed(r))throw new AccessFailure(409);if(repo.attempts(credential)>=policy.positive("attempt-limit"))throw new AccessFailure(429);
     String token=Objects.toString(body.get("token"),"");if(token.length()>100)throw new AccessFailure(400);boolean success=MessageDigest.isEqual((byte[])r.get("token_hash"),AccessPolicy.hash(token));repo.attempt(actor,credential,success);
     if(success){r.put("state","CONSUMED");r.put("protected_secret",null);repo.save(r);}repo.command(actor,key,booking,credential,kind,hash);repo.audit(actor,credential,booking,b.venue(),kind,success?"ALLOW":"DENY");var result=view(actor,r,false);result.put("attemptOutcome",success?"SIMULATED_ENTRY":"DENIED");return result;
    }
   }
  }
  UUID id=id(r,"credential_id");repo.command(actor,key,booking,id,kind,hash);repo.audit(actor,id,booking,b.venue(),kind,"ALLOW");return view(actor,r,!self);
 }
}

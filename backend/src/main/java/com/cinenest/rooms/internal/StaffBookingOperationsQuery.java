package com.cinenest.rooms.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.OperationsBookingContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
class StaffBookingOperationsQuery {
 record Case(UUID caseId,UUID roomId,long capturedBookingRevision,Instant capturedSessionStart,Instant capturedSessionEnd,
             long caseRevision,String existingCaseState,String assignmentStatus,Instant createdAt,Instant updatedAt,List<String> recordedExceptionCategories){}
 record Page(UUID bookingId,UUID venueId,UUID currentRoomId,long currentBookingRevision,Instant currentSessionStart,
             Instant currentSessionEnd,String timezone,Instant observedAt,int page,int pageSize,boolean hasNext,
             List<Case> items,boolean synthetic,String occupancy,String readiness,boolean inventoryChanged){}
 private final OperationsBookingContext bookings;private final TurnoverRepository cases;private final ScopedAuthorization auth;
 StaffBookingOperationsQuery(OperationsBookingContext bookings,TurnoverRepository cases,ScopedAuthorization auth){this.bookings=bookings;this.cases=cases;this.auth=auth;}
 private void permit(UUID actor,UUID venue){if(!auth.permits(actor,venue,"operations.synthetic.read",false))throw new RoomFailure(403,"Access denied");}
 private static void require(boolean condition){if(!condition)throw new RoomFailure(409,"Operations context changed or inconsistent");}
 private OperationsBookingContext.Context context(UUID id,UUID venue,Long revision){
  try{
   var found=bookings.inspect(id,venue,revision).orElseThrow(()->new RoomFailure(revision==null?404:409,"Booking context unavailable"));
   require(id.equals(found.bookingId())&&venue.equals(found.venueId())&&found.roomId()!=null&&found.purchasedRevision()>0&&
     (revision==null||revision==found.purchasedRevision())&&found.sessionStart()!=null&&found.sessionEnd()!=null&&found.sessionEnd().isAfter(found.sessionStart()));
   try{ZoneId.of(found.timezone());}catch(Exception e){throw new RoomFailure(409,"Invalid purchased context");}
   return found;
  }catch(OperationsBookingContext.InvalidContext e){throw new RoomFailure(409,"Invalid purchased context");}
 }
 Page read(UUID actor,UUID venue,UUID booking,int page){
  if(page<0||page>100000)throw new RoomFailure(400,"Invalid page");
  permit(actor,venue);var current=context(booking,venue,null);
  var observation=cases.bookingCases(booking,page);require(observation.observedAt()!=null&&observation.cases().size()<=26);
  var versions=new HashMap<Long,OperationsBookingContext.Context>();versions.put(current.purchasedRevision(),current);
  var result=new ArrayList<Case>();var seen=new HashSet<UUID>();
  for(var row:observation.cases()){
   require(booking.equals(row.get("booking_id"))&&venue.equals(row.get("venue_id")));
   long revision=number(row,"booking_revision"),caseRevision=number(row,"revision");require(revision>0&&caseRevision>=0);
   var captured=versions.computeIfAbsent(revision,r->context(booking,venue,r));
   UUID id=(UUID)row.get("case_id"),room=(UUID)row.get("room_id");
   require(id!=null&&seen.add(id)&&room!=null&&room.equals(captured.roomId()));
   String state=Objects.toString(row.get("state"),""),assignment=Objects.toString(row.get("assignment"),"");
   require(Set.of("OPEN","ACKNOWLEDGED","ASSERTED","RESOLVED").contains(state)&&Set.of("ASSIGNED","UNASSIGNED").contains(assignment));
   require(state.equals("OPEN")||assignment.equals("ASSIGNED"));
   List<String> categories=categories(row.get("categories"));
   Instant created=instant(row.get("created_at")),updated=instant(row.get("updated_at"));require(!updated.isBefore(created));
   if(result.size()<25)result.add(new Case(id,room,revision,captured.sessionStart(),captured.sessionEnd(),caseRevision,state,assignment,created,updated,categories));
  }
  // Fresh authoritative checks after the query and historical observations; no positive cache.
  permit(actor,venue);require(current.equals(context(booking,venue,null)));
  return new Page(booking,venue,current.roomId(),current.purchasedRevision(),current.sessionStart(),current.sessionEnd(),current.timezone(),observation.observedAt(),page,25,observation.cases().size()>25,List.copyOf(result),true,"UNKNOWN","UNVERIFIED",false);
 }
 private static long number(Map<String,Object> r,String key){var n=r.get(key);require(n instanceof Long||n instanceof Integer);return ((Number)n).longValue();}
 private static Instant instant(Object value){if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof OffsetDateTime t)return t.toInstant();throw new RoomFailure(409,"Invalid case timestamp");}
 private static List<String> categories(Object value){
  try{
   Object raw=JSONObjectUtils.parse("{\"items\":"+value+"}").get("items");require(raw instanceof List<?>);
   var list=(List<?>)raw;var result=new TreeSet<String>();
   for(Object item:list){require(item instanceof String&&Set.of("CLEANING","MAINTENANCE","UNKNOWN_DEVICE").contains(item));result.add((String)item);}
   return List.copyOf(result);
  }catch(java.text.ParseException e){throw new RoomFailure(409,"Invalid case categories");}
 }
}

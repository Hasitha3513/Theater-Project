package com.cinenest.booking.internal;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@RestController
class InventoryCalendarController {
 private final InventoryCalendar calendar;InventoryCalendarController(InventoryCalendar calendar){this.calendar=calendar;}
 @GetMapping("/api/rooms/{roomId}/availability/calendar") ResponseEntity<?> read(Principal p,@PathVariable String roomId,@RequestParam MultiValueMap<String,String> query){
  if(p==null)return error(401);
  try{
   if(!query.keySet().equals(Set.of("date","durationMinutes","guests"))||query.values().stream().anyMatch(v->v.size()!=1))throw InventoryRules.invalid();
   String date=query.getFirst("date"),duration=query.getFirst("durationMinutes"),guests=query.getFirst("guests");
   if(date==null||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||duration==null||!duration.matches("[1-9][0-9]{0,3}")||guests==null||!guests.matches("[1-9][0-9]{0,9}"))throw InventoryRules.invalid();
   byte[] body=calendar.read(UUID.fromString(p.getName()),InventoryRules.id(roomId),LocalDate.parse(date),Integer.parseInt(duration),Integer.parseInt(guests));
   return ResponseEntity.ok().header("Cache-Control","no-store").contentType(MediaType.APPLICATION_JSON).body(body);
  }catch(InventoryFailure e){return error(e.status);}catch(DateTimeException|IllegalArgumentException e){return error(400);}
 }
 @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class}) ResponseEntity<?> unavailable(){return error(503);}
 private ResponseEntity<?> error(int status){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message","Calendar unavailable, invalid or denied; reload before selecting a session."));}
}

package com.cinenest.reporting.internal;
import com.cinenest.booking.PopularSessionStarts;
import java.security.Principal;import java.time.LocalDate;import java.util.*;
import org.springframework.http.ResponseEntity;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController
class PopularSessionStartsController {
 private final PopularSessionStarts reports;PopularSessionStartsController(PopularSessionStarts reports){this.reports=reports;}
 @GetMapping("/api/admin/venues/{venue}/reports/synthetic-popular-session-starts")
 ResponseEntity<?> read(Principal principal,@PathVariable String venue,@RequestParam MultiValueMap<String,String> query){
  UUID correlation=UUID.randomUUID();if(principal==null)return result(401,null,correlation);UUID actor=UUID.fromString(principal.getName()),v=null;
  try{
   v=uuid(venue);if(!query.keySet().containsAll(Set.of("from","to"))||query.keySet().stream().anyMatch(k->!Set.of("from","to","roomId").contains(k)||query.get(k).size()!=1))throw new IllegalArgumentException();
   LocalDate from=date(query.getFirst("from")),to=date(query.getFirst("to"));UUID room=query.containsKey("roomId")?uuid(query.getFirst("roomId")):null;
   return result(200,reports.read(actor,v,room,from,to,correlation),correlation);
  }catch(PopularSessionStarts.Unavailable e){return result(e.status,null,correlation);}
  catch(IllegalArgumentException | java.time.DateTimeException e){try{reports.denied(actor,v,correlation,"INVALID_REQUEST");return result(400,null,correlation);}catch(RuntimeException unavailable){return result(503,null,correlation);}}
  catch(RuntimeException e){return result(503,null,correlation);}
 }
 private static UUID uuid(String s){UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException();return id;}
 private static LocalDate date(String s){if(s==null||!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();return LocalDate.parse(s);}
 private ResponseEntity<?> result(int status,Object body,UUID correlation){return ResponseEntity.status(status).header("Cache-Control","private, no-store").header("X-Correlation-ID",correlation.toString()).body(body);}
}

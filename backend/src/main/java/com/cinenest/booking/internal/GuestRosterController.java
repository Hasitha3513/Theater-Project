package com.cinenest.booking.internal;
import java.security.Principal;import java.util.*;import org.springframework.http.ResponseEntity;import org.springframework.dao.DataAccessException;import org.springframework.web.bind.annotation.*;
@RestController
class GuestRosterController {
 private final GuestRosterService service;private final InventoryDenialAudit audit;
 GuestRosterController(GuestRosterService service,InventoryDenialAudit audit){this.service=service;this.audit=audit;}
 private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Sign-in required");return InventoryRules.id(p.getName());}
 private ResponseEntity<?> ok(Object v){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(v);}
 @GetMapping("/api/bookings/{booking}/guest-roster") Object read(Principal p,@PathVariable UUID booking){return ok(service.read(actor(p),booking,null));}
 @PostMapping("/api/bookings/{booking}/guest-roster") Object initialize(Principal p,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),booking,false,body));}
 @PostMapping("/api/bookings/{booking}/guest-roster/companions") Object add(Principal p,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),booking,true,body));}
 @GetMapping("/api/admin/venues/{venue}/bookings/{booking}/guest-roster") Object staff(Principal p,@PathVariable UUID venue,@PathVariable UUID booking){return ok(service.read(actor(p),booking,venue));}
 @ExceptionHandler(InventoryFailure.class) Object failure(InventoryFailure f,Principal p){if(f.status==403||f.status==404)try{audit.denied(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException e){return error(503,"Audit unavailable");}return error(f.status,f.getMessage());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) Object conflict(){return error(409,"Duplicate label or roster conflict; refresh");}
 @ExceptionHandler(DataAccessException.class) Object unavailable(){return error(503,"Roster unavailable; refresh before retrying");}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) Object malformed(){return error(400,"Malformed roster request");}
 private Object error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}

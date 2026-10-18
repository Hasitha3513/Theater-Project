package com.cinenest.booking.internal;
import java.security.Principal;import java.util.*;
import org.springframework.dao.DataAccessException;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController
class AmendmentController {
 private final AmendmentService service;private final InventoryDenialAudit audit;
 AmendmentController(AmendmentService service,InventoryDenialAudit audit){this.service=service;this.audit=audit;}
 private static UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Sign-in required");return InventoryRules.id(p.getName());}
 private static ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
 @PostMapping("/api/bookings/{booking}/amendments") ResponseEntity<?> create(Principal p,@PathVariable String booking,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),InventoryRules.id(booking),null,"CREATE",body));}
 @GetMapping("/api/bookings/{booking}/amendments/{amendment}") ResponseEntity<?> read(Principal p,@PathVariable String booking,@PathVariable String amendment){return ok(service.read(actor(p),InventoryRules.id(booking),InventoryRules.id(amendment)));}
 @PostMapping("/api/bookings/{booking}/amendments/{amendment}/confirm") ResponseEntity<?> confirm(Principal p,@PathVariable String booking,@PathVariable String amendment,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),InventoryRules.id(booking),InventoryRules.id(amendment),"CONFIRM",body));}
 @PostMapping("/api/bookings/{booking}/amendments/{amendment}/withdrawals") ResponseEntity<?> withdraw(Principal p,@PathVariable String booking,@PathVariable String amendment,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),InventoryRules.id(booking),InventoryRules.id(amendment),"WITHDRAW",body));}
 @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> failure(InventoryFailure f,Principal p){if(f.status==403||f.status==404)try{audit.denied(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException unavailable){return error(503,"Amendment audit unavailable");}return error(f.status,f.getMessage());}
 @ExceptionHandler(com.cinenest.booking.AmendmentGuards.Rejected.class) ResponseEntity<?> rejected(){return error(409,"Amendment eligibility changed; original purchase retained");}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Amendment conflicts; refresh authoritative state");}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Amendment unavailable; refresh before retrying");}
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> malformed(){return error(400,"Malformed amendment request");}
 private static ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}

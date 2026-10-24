package com.cinenest.booking.internal;
import java.security.Principal;import java.util.*;
import org.springframework.http.ResponseEntity;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController
class ExtensionReservationController {
 private final ExtensionReservationService service;private final InventoryDenialAudit audit;
 ExtensionReservationController(ExtensionReservationService service,InventoryDenialAudit audit){this.service=service;this.audit=audit;}
 private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Session required");return InventoryRules.id(p.getName());}
 @PostMapping("/api/bookings/{booking}/extension-reservations") ResponseEntity<?> create(Principal p,@PathVariable String booking,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body){return response(200,service.create(actor(p),booking,body,!query.isEmpty()));}
 @GetMapping("/api/bookings/{booking}/extension-reservations/commands/{commandKey}") ResponseEntity<?> recover(Principal p,@PathVariable String booking,@PathVariable String commandKey,@RequestParam MultiValueMap<String,String> query){if(!query.isEmpty())throw InventoryRules.invalid();return response(200,service.recover(actor(p),booking,commandKey));}
 @GetMapping("/api/bookings/{booking}/extension-reservations/{reservation}") ResponseEntity<?> read(Principal p,@PathVariable String booking,@PathVariable String reservation,@RequestParam MultiValueMap<String,String> query){if(!query.isEmpty())throw InventoryRules.invalid();return response(200,service.read(actor(p),booking,reservation));}
 @PostMapping("/api/bookings/{booking}/extension-reservations/{reservation}/withdrawals") ResponseEntity<?> withdraw(Principal p,@PathVariable String booking,@PathVariable String reservation,@RequestParam MultiValueMap<String,String> query,@RequestBody String body){if(!query.isEmpty())throw InventoryRules.invalid();return response(200,service.withdraw(actor(p),booking,reservation,body));}
 @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> failure(InventoryFailure e,Principal p){if(e.status==403||e.status==404)try{audit.denied(p==null?null:UUID.fromString(p.getName()));}catch(RuntimeException unavailable){return unavailable();}return response(e.status,Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return response(409,Map.of("message","Reservation conflicts with current inventory;reload"));}
 @ExceptionHandler(RuntimeException.class) ResponseEntity<?> unavailable(){return response(503,Map.of("message","Extension reservation unavailable;reload before retrying the same command"));}
 private ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(body);}
}

package com.cinenest.booking.internal;
import java.security.Principal;import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.ResponseEntity;
@RestController
class CancellationController {
 private final CancellationService service;CancellationController(CancellationService service){this.service=service;}
 private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Session required");return InventoryRules.id(p.getName());}
 @GetMapping("/api/bookings/{booking}/cancellation") ResponseEntity<?> read(Principal p,@PathVariable UUID booking,@RequestParam Map<String,String> query){if(!query.isEmpty())throw InventoryRules.invalid();return ok(service.read(actor(p),null,booking));}
 @PostMapping("/api/bookings/{booking}/cancellations") ResponseEntity<?> cancel(Principal p,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.cancel(actor(p),null,booking,body));}
 @GetMapping("/api/admin/venues/{venue}/bookings/{booking}/cancellation") ResponseEntity<?> staffRead(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@RequestParam Map<String,String> query){if(!query.isEmpty())throw InventoryRules.invalid();return ok(service.read(actor(p),venue,booking));}
 @PostMapping("/api/admin/venues/{venue}/bookings/{booking}/cancellations") ResponseEntity<?> staffCancel(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.cancel(actor(p),venue,booking,body));}
 private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(body);}
 @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> failure(InventoryFailure e){return ResponseEntity.status(e.status).header("Cache-Control","private, no-store").body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(RuntimeException.class) ResponseEntity<?> unavailable(){return ResponseEntity.status(503).header("Cache-Control","private, no-store").body(Map.of("message","Cancellation unavailable;reload before retrying the same command"));}
}

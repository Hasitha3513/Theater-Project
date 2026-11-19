package com.cinenest.booking.internal;

import java.security.Principal;
import java.util.*;
import com.cinenest.rooms.TurnoverHandoff;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;

@RestController
@RequestMapping("/api/admin/venues/{venue}/bookings/{booking}/session")
class SyntheticSessionController {
    private final SyntheticSessionService service;
    private final TurnoverHandoff handoff;
    SyntheticSessionController(SyntheticSessionService service,TurnoverHandoff handoff){this.service=service;this.handoff=handoff;}
    private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Sign-in required");return UUID.fromString(p.getName());}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);}
    @GetMapping Object read(Principal p,@PathVariable UUID venue,@PathVariable UUID booking){return ok(service.read(actor(p),venue,booking));}
    @PostMapping("/starts") Object start(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.record(actor(p),venue,booking,"START",body));}
    @PostMapping("/checkouts") Object checkout(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@RequestBody Map<String,Object> body){return ok(service.record(actor(p),venue,booking,"CHECKOUT",body));}
    @GetMapping("/cleaning-handoff/{checkout}") Object status(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@PathVariable UUID checkout){service.read(actor(p),venue,booking);return ok(handoff.handoffStatus(actor(p),venue,booking,checkout));}
    @PostMapping("/cleaning-handoff") Object retry(Principal p,@PathVariable UUID venue,@PathVariable UUID booking,@RequestBody Map<String,Object> body){
        if(!body.keySet().equals(Set.of("checkoutEventId")))throw InventoryRules.invalid();
        service.read(actor(p),venue,booking);
        return ok(handoff.handoff(actor(p),venue,booking,InventoryRules.id(InventoryRules.text(body,"checkoutEventId"))));
    }
    @ExceptionHandler(TurnoverHandoff.Failure.class) Object handoffFailure(TurnoverHandoff.Failure e){return ResponseEntity.status(e.status()).header("Cache-Control","no-store").body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(InventoryFailure.class) Object failure(InventoryFailure e){return ResponseEntity.status(e.status).header("Cache-Control","no-store").body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) Object conflict(){return ResponseEntity.status(409).header("Cache-Control","no-store").body(Map.of("message","Session scope or command conflicts. Reload current state."));}
    @ExceptionHandler(DataAccessException.class) Object unavailable(){return ResponseEntity.status(503).header("Cache-Control","no-store").body(Map.of("message","Session recording unavailable. Recover before retrying."));}
}

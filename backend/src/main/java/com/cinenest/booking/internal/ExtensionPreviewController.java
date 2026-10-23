package com.cinenest.booking.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class ExtensionPreviewController {
    private final ExtensionPreviewService service;private final InventoryDenialAudit audit;
    ExtensionPreviewController(ExtensionPreviewService service,InventoryDenialAudit audit){this.service=service;this.audit=audit;}
    private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Session required");return InventoryRules.id(p.getName());}
    @GetMapping("/api/bookings/{booking}/extension-eligibility")
    ResponseEntity<?> eligibility(Principal p,@PathVariable String booking,@RequestParam Map<String,String> query){if(!query.isEmpty())throw InventoryRules.invalid();return response(200,service.read(actor(p),InventoryRules.id(booking),null));}
    @PostMapping("/api/bookings/{booking}/extension-quotes")
    ResponseEntity<?> preview(Principal p,@PathVariable String booking,@RequestParam Map<String,String> query,@RequestBody Map<String,Object> body){if(!query.isEmpty())throw InventoryRules.invalid();return response(200,service.read(actor(p),InventoryRules.id(booking),body));}
    @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> failure(InventoryFailure e,Principal p){
        if(e.status==403||e.status==404)try{audit.denied(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException unavailable){return response(503,Map.of("message","Extension preview unavailable"));}
        return response(e.status,Map.of("message",e.getMessage()));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> malformed(){return response(400,Map.of("message","Invalid extension preview request"));}
    @ExceptionHandler(RuntimeException.class) ResponseEntity<?> unavailable(){return response(503,Map.of("message","Extension preview unavailable"));}
    private ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(body);}
}

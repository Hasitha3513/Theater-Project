package com.cinenest.booking.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
class InventoryController {
    private final InventoryService service;
    private final InventoryDenialAudit audit;
    InventoryController(InventoryService service,InventoryDenialAudit audit) {this.service=service;this.audit=audit;}
    private UUID actor(Principal principal) {if(principal==null)throw new InventoryFailure(401,"Sign-in required");try{return UUID.fromString(principal.getName());}catch(IllegalArgumentException e){throw new InventoryFailure(403,"Access denied");}}
    @GetMapping("/api/rooms/{roomId}/availability") ResponseEntity<?> availability(Principal p,@PathVariable String roomId,@RequestParam String sessionStartLocal,@RequestParam int durationMinutes,@RequestParam int guests) {return ok(service.availability(actor(p),InventoryRules.id(roomId),sessionStartLocal,durationMinutes,guests));}
    @PostMapping("/api/holds") ResponseEntity<?> create(Principal p,@RequestBody Map<String,Object> body) {return ok(service.create(actor(p),body));}
    @GetMapping("/api/holds/{holdId}") ResponseEntity<?> read(Principal p,@PathVariable String holdId) {return ok(service.read(actor(p),InventoryRules.id(holdId)));}
    @PostMapping("/api/holds/{holdId}/release") ResponseEntity<?> release(Principal p,@PathVariable String holdId,@RequestBody Map<String,Object> body) {return ok(service.release(actor(p),InventoryRules.id(holdId),body));}
    @GetMapping("/api/admin/venues/{venueId}/inventory") ResponseEntity<?> inventory(Principal p,@PathVariable String venueId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size) {return ok(service.inventory(actor(p),InventoryRules.id(venueId),page,size));}
    private ResponseEntity<?> ok(Object result) {return ResponseEntity.ok().header("Cache-Control","no-store").body(result);}
    @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> failure(InventoryFailure failure,Principal p) {
        if(failure.status==403)try {audit.denied(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException unavailable){return error(503,"Inventory unavailable");}
        return error(failure.status,failure.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict() {return error(409,"Inventory changed or conflicts; refresh and retry");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable() {return error(503,"Inventory unavailable");}
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<?> malformed() {return error(400,"Malformed inventory request");}
    private ResponseEntity<?> error(int status,String message) {return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}

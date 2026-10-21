package com.cinenest.rooms.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class StaffBookingOperationsController {
 private final StaffBookingOperationsQuery service;private final DeniedConfigurationAudit audit;
 StaffBookingOperationsController(StaffBookingOperationsQuery service,DeniedConfigurationAudit audit){this.service=service;this.audit=audit;}
 @GetMapping("/api/admin/venues/{venue}/bookings/{booking}/operations/cases")
 ResponseEntity<?> read(Principal principal,@PathVariable UUID venue,@PathVariable UUID booking,@RequestParam(defaultValue="0")int page){
  if(principal==null)throw new RoomFailure(401,"Sign-in required");
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.read(UUID.fromString(principal.getName()),venue,booking,page));
 }
 @ExceptionHandler(RoomFailure.class) ResponseEntity<?> denied(RoomFailure e,Principal p){
  try{if(e.status==403)audit.record(p==null?null:UUID.fromString(p.getName()));}
  catch(DataAccessException failure){return error(503);}
  return error(e.status);
 }
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) ResponseEntity<?> invalid(){return error(400);}
 private ResponseEntity<?> error(int code){return ResponseEntity.status(code).header("Cache-Control","no-store").body(Map.of("message","Synthetic booking operations context unavailable, changed or denied. Reload before retrying.","synthetic",true,"occupancy","UNKNOWN","readiness","UNVERIFIED","inventoryChanged",false));}
}

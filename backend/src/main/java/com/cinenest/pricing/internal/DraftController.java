package com.cinenest.pricing.internal;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestController
@RequestMapping("/api/admin/venues/{venueId}")
class DraftController {
 private final DraftService service;private final DeniedDraftAudit audit;
 DraftController(DraftService service,DeniedDraftAudit audit){this.service=service;this.audit=audit;}
 private UUID actor(Principal p){if(p==null)throw new DraftFailure(401,"Sign-in required");try{return UUID.fromString(p.getName());}catch(IllegalArgumentException e){throw new DraftFailure(403,"Access denied");}}
 private UUID id(String id){return DraftRules.id(id);}
 private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 @GetMapping("/packages") ResponseEntity<?> list(Principal p,@PathVariable String venueId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return ok(service.list(actor(p),id(venueId),q,page,size));}
 @PostMapping("/packages") ResponseEntity<?> create(Principal p,@PathVariable String venueId,@RequestBody Map<String,Object>d){UUID key=UUID.randomUUID();return ResponseEntity.created(URI.create("/api/admin/venues/"+venueId+"/packages/"+key)).header("Cache-Control","no-store").body(service.save(actor(p),id(venueId),key,d,true));}
 @GetMapping("/packages/{packageId}") ResponseEntity<?> read(Principal p,@PathVariable String venueId,@PathVariable String packageId){return ok(service.read(actor(p),id(venueId),id(packageId)));}
 @PutMapping("/packages/{packageId}/draft") ResponseEntity<?> update(Principal p,@PathVariable String venueId,@PathVariable String packageId,@RequestBody Map<String,Object>d){return ok(service.save(actor(p),id(venueId),id(packageId),d,false));}
 @PostMapping("/packages/{packageId}/duplicate") ResponseEntity<?> duplicate(Principal p,@PathVariable String venueId,@PathVariable String packageId,@RequestBody Map<String,Object>d){var result=service.duplicate(actor(p),id(venueId),id(packageId),d);return ResponseEntity.created(URI.create("/api/admin/venues/"+venueId+"/packages/"+result.get("packageId"))).header("Cache-Control","no-store").body(result);}
 @PostMapping("/packages/{packageId}/preview") ResponseEntity<?> preview(Principal p,@PathVariable String venueId,@PathVariable String packageId,@RequestBody Map<String,Object>d){return ok(service.preview(actor(p),id(venueId),id(packageId),d));}
 @GetMapping("/packages/{packageId}/history") ResponseEntity<?> history(Principal p,@PathVariable String venueId,@PathVariable String packageId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.history(actor(p),id(venueId),id(packageId),page,size));}
 @GetMapping("/package-options") ResponseEntity<?> options(Principal p,@PathVariable String venueId){return ok(service.options(actor(p),id(venueId)));}
 @ExceptionHandler(DraftFailure.class) ResponseEntity<?> failure(DraftFailure e,Principal p){if(e.status==403)try{audit.record(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException unavailable){return error(503,"Service unavailable");}return error(e.status,e.getMessage());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Draft conflict; reload and compare");}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Service unavailable");}
 @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> malformed(){return error(400,"Malformed request");}
 private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}

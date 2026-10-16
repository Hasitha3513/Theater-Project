package com.cinenest.pricing.internal;
import java.security.Principal;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestController
class PublicationController {
 private final PublicationService service;private final DeniedDraftAudit audit;
 PublicationController(PublicationService service,DeniedDraftAudit audit){this.service=service;this.audit=audit;}
 private UUID actor(Principal p){if(p==null)throw new DraftFailure(401,"Sign-in required");return DraftRules.id(p.getName());}
 private UUID id(String x){return DraftRules.id(x);}
 private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 @PostMapping("/api/admin/venues/{v}/packages/{p}/publication-preview") ResponseEntity<?> preview(Principal a,@PathVariable String v,@PathVariable String p,@RequestBody Map<String,Object>d){return ok(service.preview(actor(a),id(v),id(p),d));}
 @PostMapping("/api/admin/venues/{v}/packages/{p}/publications") ResponseEntity<?> publish(Principal a,@PathVariable String v,@PathVariable String p,@RequestBody Map<String,Object>d){return ok(service.publish(actor(a),id(v),id(p),d));}
 @GetMapping("/api/admin/venues/{v}/packages/{p}/publications") ResponseEntity<?> list(Principal a,@PathVariable String v,@PathVariable String p){return ok(service.list(actor(a),id(v),id(p)));}
 @GetMapping("/api/admin/venues/{v}/packages/{p}/publications/{publication}") ResponseEntity<?> detail(Principal a,@PathVariable String v,@PathVariable String p,@PathVariable String publication){return ok(service.detail(actor(a),id(v),id(p),id(publication)));}
 @PostMapping("/api/admin/venues/{v}/packages/{p}/publications/{publication}/deactivate") ResponseEntity<?> deactivate(Principal a,@PathVariable String v,@PathVariable String p,@PathVariable String publication,@RequestBody Map<String,Object>d){return ok(service.deactivate(actor(a),id(v),id(p),id(publication),d));}
 @PostMapping("/api/admin/venues/{v}/packages/{p}/publications/{publication}/retry") ResponseEntity<?> retry(Principal a,@PathVariable String v,@PathVariable String p,@PathVariable String publication,@RequestBody Map<String,Object>d){return ok(service.retry(actor(a),id(v),id(p),id(publication),d));}
 @GetMapping("/api/venues/{v}/packages") ResponseEntity<?> catalog(@PathVariable String v,@RequestParam(required=false) String roomId,@RequestParam(required=false) String sessionStart,@RequestParam(required=false) String sessionEnd){return ok(service.catalog(id(v),null,roomId==null?null:id(roomId),sessionStart,sessionEnd));}
 @GetMapping("/api/venues/{v}/packages/{p}") ResponseEntity<?> publicDetail(@PathVariable String v,@PathVariable String p,@RequestParam(required=false) String roomId,@RequestParam(required=false) String sessionStart,@RequestParam(required=false) String sessionEnd){return ok(service.catalog(id(v),id(p),roomId==null?null:id(roomId),sessionStart,sessionEnd));}
 @ExceptionHandler(DraftFailure.class) ResponseEntity<?> failure(DraftFailure e,Principal p){if(e.status==403)try{audit.record(p==null?null:id(p.getName()));}catch(DataAccessException unavailable){return error(503,"Service unavailable");}return error(e.status,e.getMessage());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Publication conflict; reload and compare");}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Service unavailable");}
 @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> malformed(){return error(400,"Malformed request");}
 private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}

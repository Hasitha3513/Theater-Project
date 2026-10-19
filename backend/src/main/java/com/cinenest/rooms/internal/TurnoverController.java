package com.cinenest.rooms.internal;
import java.security.Principal;import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
@RestController
@RequestMapping("/api/admin/venues/{venue}/operations/cases")
class TurnoverController {
 private final TurnoverService service;private final DeniedConfigurationAudit audit;TurnoverController(TurnoverService service,DeniedConfigurationAudit audit){this.service=service;this.audit=audit;}
 private UUID actor(Principal p){if(p==null)throw new RoomFailure(401,"Sign-in required");return ConfigurationRules.uuid(p.getName());}
 @GetMapping ResponseEntity<?> list(Principal p,@PathVariable UUID venue,@RequestParam(defaultValue="0") int page){return ok(service.list(actor(p),venue,page));}
 @GetMapping("/{id}") ResponseEntity<?> detail(Principal p,@PathVariable UUID venue,@PathVariable UUID id){return ok(service.get(actor(p),venue,id));}
 @PostMapping ResponseEntity<?> create(Principal p,@PathVariable UUID venue,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),venue,null,"CREATE",body));}
 @PostMapping("/{id}/{action}") ResponseEntity<?> command(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@PathVariable String action,@RequestBody Map<String,Object> body){String value=switch(action){case "assignments"->"ASSIGN";case "acknowledgements"->"ACKNOWLEDGE";case "checklist"->"CHECKLIST";case "completion-assertions"->"COMPLETE";case "exceptions"->"EXCEPTION";case "exception-resolutions"->"RESOLVE_EXCEPTION";case "reviews"->"REVIEW";default->throw new RoomFailure(404,"Action not found");};return ok(service.command(actor(p),venue,id,value,body));}
 private ResponseEntity<?> ok(Object v){return ResponseEntity.ok().header("Cache-Control","no-store").body(v);}
 @ExceptionHandler(RoomFailure.class) ResponseEntity<?> failure(RoomFailure e,Principal p){if(e.status==403)audit.record(p==null?null:UUID.fromString(p.getName()));return ResponseEntity.status(e.status).header("Cache-Control","no-store").body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return ResponseEntity.status(503).header("Cache-Control","no-store").body(Map.of("message","Synthetic operations unavailable; reload before retry"));}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("message","Malformed request"));}
}

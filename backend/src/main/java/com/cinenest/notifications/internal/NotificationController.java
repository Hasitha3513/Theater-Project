package com.cinenest.notifications.internal;
import java.security.Principal;import java.util.*;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
@RestController
@ConditionalOnProperty(name="cinenest.notifications.enabled",havingValue="true")
class NotificationController {
 private final NotificationService service;private final NotificationRepository repo;private final TransactionTemplate tx;
 NotificationController(NotificationService service,NotificationRepository repo,PlatformTransactionManager manager){this.service=service;this.repo=repo;this.tx=new TransactionTemplate(manager);}
 private UUID actor(Principal p){try{return UUID.fromString(p.getName());}catch(Exception e){throw new NotificationFailure(401);}}
 private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 @GetMapping("/api/notifications")ResponseEntity<?> list(Principal p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return ok(service.inbox(actor(p),null,page,size));}
 @GetMapping("/api/notifications/{id}")ResponseEntity<?> detail(Principal p,@PathVariable UUID id){return ok(service.inbox(actor(p),id,0,1));}
 @PostMapping("/api/notifications/{id}/read")ResponseEntity<?> read(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object> b){return ok(service.command(actor(p),null,id,"read",b));}
 @GetMapping("/api/admin/venues/{v}/notifications")ResponseEntity<?> queue(Principal p,@PathVariable UUID v,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return ok(service.admin(actor(p),v,null,page,size));}
 @GetMapping("/api/admin/venues/{v}/notifications/{id}")ResponseEntity<?> admin(Principal p,@PathVariable UUID v,@PathVariable UUID id){return ok(service.admin(actor(p),v,id,0,1));}
 @PostMapping("/api/admin/venues/{v}/notifications/{id}/retries")ResponseEntity<?> retry(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,"retries",b));}
 @PostMapping("/api/admin/venues/{v}/notifications/{id}/reconciliations")ResponseEntity<?> reconcile(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,"reconciliations",b));}
 @PostMapping("/api/admin/venues/{v}/notifications/{id}/simulations")ResponseEntity<?> simulate(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,"simulations",b));}
 @ExceptionHandler(NotificationFailure.class)ResponseEntity<?> failure(NotificationFailure f,Principal p){try{tx.executeWithoutResult(s->repo.audit(p==null?null:actor(p),null,null,null,"DENIED","DENY"));}catch(RuntimeException e){return error(503);}return error(f.status);}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)ResponseEntity<?> conflict(){return error(409);}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})ResponseEntity<?> invalid(){return error(400);}
 private ResponseEntity<?> error(int s){return ResponseEntity.status(s).header("Cache-Control","no-store").body(Map.of("message","Synthetic notification unavailable, expired, changed or denied. Refresh before retrying.","liveDelivery",false));}
}

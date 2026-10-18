package com.cinenest.access.internal;
import java.security.Principal;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.dao.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
@RestController
@ConditionalOnProperty(name="cinenest.access.enabled",havingValue="true")
class AccessController {
 private final AccessService service;private final AccessRepository repo;private final TransactionTemplate tx;
 AccessController(AccessService service,AccessRepository repo,PlatformTransactionManager manager){this.service=service;this.repo=repo;tx=new TransactionTemplate(manager);}
 private UUID actor(Principal p){try{return UUID.fromString(p.getName());}catch(Exception e){throw new AccessFailure(401);}}
 private ResponseEntity<?> ok(Object b){int status=b instanceof Map<?,?> m&&"DENIED".equals(m.get("attemptOutcome"))?403:200;return ResponseEntity.status(status).header("Cache-Control","no-store").body(b);}
 @GetMapping("/api/bookings/{id}/access") ResponseEntity<?> read(Principal p,@PathVariable UUID id){return ok(service.status(actor(p),id));}
 @PostMapping("/api/bookings/{id}/access/credentials") ResponseEntity<?> issue(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,null,"ISSUE",b));}
 @PostMapping("/api/bookings/{id}/access/credentials/{credential}/reveal") ResponseEntity<?> reveal(Principal p,@PathVariable UUID id,@PathVariable UUID credential,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,credential,null,"REVEAL",b));}
 @PostMapping("/api/bookings/{id}/access/simulated-attempts") ResponseEntity<?> attempt(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,null,"ATTEMPT",b));}
 @GetMapping("/api/admin/venues/{v}/access/credentials") ResponseEntity<?> list(Principal p,@PathVariable UUID v,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return ok(service.admin(actor(p),v,null,page,size));}
 @GetMapping("/api/admin/venues/{v}/access/credentials/{id}") ResponseEntity<?> detail(Principal p,@PathVariable UUID v,@PathVariable UUID id){return ok(service.admin(actor(p),v,id,0,1));}
 @PostMapping("/api/admin/venues/{v}/access/credentials/{id}/revocations") ResponseEntity<?> revoke(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),null,id,v,"REVOKE",b));}
 @PostMapping("/api/admin/venues/{v}/access/credentials/{id}/simulations") ResponseEntity<?> simulate(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),null,id,v,"SIMULATE",b));}
 @ExceptionHandler(AccessFailure.class)ResponseEntity<?> failed(AccessFailure f,Principal p){try{tx.executeWithoutResult(t->repo.audit(p==null?null:actor(p),null,null,null,"DENIED","DENY"));}catch(RuntimeException e){return error(503);}return error(f.status);}
 @ExceptionHandler(DataIntegrityViolationException.class)ResponseEntity<?> conflict(){return error(409);}
 @ExceptionHandler(DataAccessException.class)ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})ResponseEntity<?> invalid(){return error(400);}
 private ResponseEntity<?> error(int s){return ResponseEntity.status(s).header("Cache-Control","no-store").body(Map.of("message","Synthetic access unavailable, changed or denied. Refresh before retrying.","synthetic",true,"doorControlled",false));}
}

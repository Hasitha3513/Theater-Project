package com.cinenest.iot.internal;
import java.security.Principal;import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.dao.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
@RestController
@ConditionalOnProperty(name="cinenest.iot.enabled",havingValue="true")
class IotController {
 private final IotService service;private final IotRepository repo;private final TransactionTemplate tx;
 IotController(IotService s,IotRepository r,PlatformTransactionManager t){service=s;repo=r;tx=new TransactionTemplate(t);}
 private UUID actor(Principal p){try{return UUID.fromString(p.getName());}catch(Exception e){throw new IotFailure(401);}}
 private ResponseEntity<?> ok(Object b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 @GetMapping("/api/admin/venues/{v}/iot/devices")ResponseEntity<?> list(Principal p,@PathVariable UUID v,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return ok(service.read(actor(p),v,null,page,size));}
 @GetMapping("/api/admin/venues/{v}/iot/devices/{d}")ResponseEntity<?> detail(Principal p,@PathVariable UUID v,@PathVariable UUID d){return ok(service.read(actor(p),v,d,0,1));}
 @PostMapping("/api/admin/venues/{v}/iot/devices/{d}/commands")ResponseEntity<?> command(Principal p,@PathVariable UUID v,@PathVariable UUID d,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,d,null,"COMMAND",b));}
 @PostMapping("/api/admin/venues/{v}/iot/devices/{d}/commands/{c}/simulations")ResponseEntity<?> simulate(Principal p,@PathVariable UUID v,@PathVariable UUID d,@PathVariable UUID c,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,d,c,"SIMULATE",b));}
 @PostMapping("/api/admin/venues/{v}/iot/devices/{d}/commands/{c}/reconciliations")ResponseEntity<?> reconcile(Principal p,@PathVariable UUID v,@PathVariable UUID d,@PathVariable UUID c,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,d,c,"RECONCILE",b));}
 @ExceptionHandler(IotFailure.class)ResponseEntity<?> denied(IotFailure f,Principal p){try{tx.executeWithoutResult(t->repo.audit(p==null?null:actor(p),null,null,null,"DENIED","DENY"));}catch(RuntimeException e){return error(503);}return error(f.status);}
 @ExceptionHandler(DataIntegrityViolationException.class)ResponseEntity<?> conflict(){return error(409);}
 @ExceptionHandler(DataAccessException.class)ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})ResponseEntity<?> invalid(){return error(400);}
 private ResponseEntity<?> error(int code){return ResponseEntity.status(code).header("Cache-Control","no-store").body(Map.of("message","Synthetic IoT unavailable, changed or denied. Refresh before retrying.","synthetic",true,"equipmentControlled",false));}
}

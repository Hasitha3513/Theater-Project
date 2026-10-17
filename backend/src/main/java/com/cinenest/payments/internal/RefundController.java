package com.cinenest.payments.internal;

import java.security.Principal;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;

@RestController
@ConditionalOnProperty(name="cinenest.bank.refunds.enabled",havingValue="true")
class RefundController {
 private final RefundService service;private final BankRepository bank;private final TransactionTemplate tx;
 RefundController(RefundService service,BankRepository bank,PlatformTransactionManager manager){this.service=service;this.bank=bank;tx=new TransactionTemplate(manager);}
 private UUID actor(Principal p){if(p==null)throw new BankFailure(401,"Sign-in required");return BankService.id(p.getName());}
 private ResponseEntity<?> ok(Object v){return ResponseEntity.ok().header("Cache-Control","no-store").body(v);}
 @PostMapping("/api/bank/requests/{id}/refunds") ResponseEntity<?> request(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.request(actor(p),id,b));}
 @GetMapping("/api/refunds/{id}") ResponseEntity<?> read(Principal p,@PathVariable UUID id){return ok(service.read(actor(p),null,id));}
 @GetMapping("/api/admin/venues/{v}/refunds") ResponseEntity<?> queue(Principal p,@PathVariable UUID v){return ok(service.read(actor(p),v,null));}
 @PostMapping("/api/admin/venues/{v}/refunds/{id}/destination-verifications") ResponseEntity<?> destination(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,null,"DESTINATION",b));}
 @PostMapping("/api/admin/venues/{v}/refunds/{id}/decisions") ResponseEntity<?> decision(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,null,"DECISION",b));}
 @PostMapping("/api/admin/venues/{v}/refunds/{id}/attempts") ResponseEntity<?> attempt(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,null,"ATTEMPT",b));}
 @PostMapping("/api/admin/venues/{v}/refunds/{id}/attempts/{attempt}/evidence") ResponseEntity<?> evidence(Principal p,@PathVariable UUID v,@PathVariable UUID id,@PathVariable UUID attempt,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,attempt,"EVIDENCE",b));}
 @PostMapping("/api/admin/venues/{v}/refunds/{id}/reconciliations") ResponseEntity<?> reconcile(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),v,id,null,"RECONCILE",b));}
 @ExceptionHandler(BankFailure.class) ResponseEntity<?> failure(BankFailure f,Principal p){try{tx.executeWithoutResult(s->bank.audit(p==null?null:actor(p),null,"REFUND_DENIED","DENIED"));}catch(DataAccessException e){return error(503);}return error(f.status);}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409);}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler({ArithmeticException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return error(400);}
 private ResponseEntity<?> error(int status){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message","Synthetic refund unavailable, changed or denied. Refresh before retrying.","synthetic",true,"realRepayment",false));}
}

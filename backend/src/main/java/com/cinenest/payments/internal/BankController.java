package com.cinenest.payments.internal;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
@RestController
@ConditionalOnProperty(name="cinenest.bank.enabled",havingValue="true")
class BankController {
    private final BankService service;
    private final BankRepository repository;
    private final TransactionTemplate denial;
    BankController(BankService service,BankRepository repository,PlatformTransactionManager manager){this.service=service;this.repository=repository;denial=new TransactionTemplate(manager);denial.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    UUID actor(Principal p){if(p==null)throw new BankFailure(401,"Sign-in required");return BankService.id(p.getName());}
    ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
    @PostMapping("/api/bank/requests") ResponseEntity<?> create(Principal p,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),null,null,"CREATE",b));}
    @PostMapping("/api/bank/requests/{id}/claims") ResponseEntity<?> claim(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,"CLAIM",b));}
    @PostMapping("/api/bank/claims/{id}/uploads") ResponseEntity<?> allocate(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.allocate(actor(p),id,b));}
    @PutMapping("/api/bank/uploads/{id}/content") ResponseEntity<?> upload(Principal p,@PathVariable UUID id,HttpServletRequest request)throws IOException{service.upload(actor(p),id,Objects.toString(request.getContentType(),""),request.getInputStream());return ok(Map.of("state","QUARANTINED","labels",BankPolicy.LABELS));}
    @PostMapping("/api/bank/uploads/{id}/complete") ResponseEntity<?> complete(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.complete(actor(p),id,b));}
    @GetMapping("/api/bank/requests") ResponseEntity<?> owned(Principal p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return ok(service.ownedList(actor(p),page,size));}
    @GetMapping("/api/bank/requests/{id}") ResponseEntity<?> read(Principal p,@PathVariable UUID id){return ok(service.read(actor(p),id,null));}
    @PostMapping("/api/bank/requests/{id}/resubmissions") ResponseEntity<?> resubmit(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,"RESUBMIT",b));}
    @GetMapping("/api/admin/venues/{venue}/bank/requests") ResponseEntity<?> queue(Principal p,@PathVariable UUID venue){return ok(service.queue(actor(p),venue));}
    @GetMapping("/api/admin/venues/{venue}/bank/requests/{id}") ResponseEntity<?> review(Principal p,@PathVariable UUID venue,@PathVariable UUID id){return ok(service.read(actor(p),id,venue));}
    @GetMapping("/api/admin/venues/{venue}/bank/requests/{id}/evidence/{evidence}") ResponseEntity<?> preview(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@PathVariable UUID evidence){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").header("Content-Disposition","inline; filename=synthetic-preview.png").body(service.preview(actor(p),venue,id,evidence));}
    @PostMapping("/api/admin/venues/{venue}/bank/requests/{id}/decisions") ResponseEntity<?> decide(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,venue,"DECIDE",b));}
    @PostMapping("/api/admin/venues/{venue}/bank/requests/{id}/escalations") ResponseEntity<?> escalate(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,venue,"ESCALATE",b));}
    @ExceptionHandler(com.cinenest.booking.BankRequestHolds.Rejected.class) ResponseEntity<?> inventoryChanged(){return error(409,"Inventory expired or changed; refresh current state");}
    @ExceptionHandler(BankFailure.class) ResponseEntity<?> failure(BankFailure failure,Principal p){try{denial.executeWithoutResult(s->repository.audit(p==null?null:actor(p),null,"HTTP_DENIAL","DENIED"));}catch(DataAccessException unavailable){return error(503,"Synthetic bank review unavailable");}return error(failure.status,failure.getMessage());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Bank conflict; refresh authoritative history");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Synthetic bank review unavailable");}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return error(400,"Malformed verification request");}
    @ExceptionHandler(IOException.class) ResponseEntity<?> interrupted(){return error(503,"Upload interrupted; refresh before retrying");}
    ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message,"labels",BankPolicy.LABELS));}
}

package com.cinenest.verification.internal;
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
@ConditionalOnProperty(name="cinenest.verification.enabled",havingValue="true")
class VerificationController {
    private final VerificationService service;
    private final VerificationRepository repository;
    private final TransactionTemplate denial;
    VerificationController(VerificationService service,VerificationRepository repository,PlatformTransactionManager manager){this.service=service;this.repository=repository;denial=new TransactionTemplate(manager);denial.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    UUID actor(Principal p){if(p==null)throw new VerificationFailure(401,"Sign-in required");return VerificationService.id(p.getName());}
    ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
    @PostMapping("/api/verification/requests") ResponseEntity<?> create(Principal p,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),null,null,"CREATE",b));}
    @PostMapping("/api/verification/requests/{id}/uploads") ResponseEntity<?> allocate(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,"ALLOCATE",b));}
    @PutMapping("/api/verification/uploads/{id}/content") ResponseEntity<?> upload(Principal p,@PathVariable UUID id,HttpServletRequest request)throws IOException{service.upload(actor(p),id,Objects.toString(request.getContentType(),""),request.getInputStream());return ok(Map.of("state","QUARANTINED","labels",VerificationPolicy.LABELS));}
    @PostMapping("/api/verification/uploads/{id}/complete") ResponseEntity<?> complete(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.complete(actor(p),id,b));}
    @GetMapping("/api/verification/requests/{id}") ResponseEntity<?> read(Principal p,@PathVariable UUID id){return ok(service.read(actor(p),id,null));}
    @PostMapping("/api/verification/requests/{id}/resubmissions") ResponseEntity<?> resubmit(Principal p,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,null,"RESUBMIT",b));}
    @GetMapping("/api/admin/venues/{venue}/verification/requests") ResponseEntity<?> queue(Principal p,@PathVariable UUID venue){return ok(service.queue(actor(p),venue));}
    @GetMapping("/api/admin/venues/{venue}/verification/requests/{id}") ResponseEntity<?> review(Principal p,@PathVariable UUID venue,@PathVariable UUID id){return ok(service.read(actor(p),id,venue));}
    @GetMapping("/api/admin/venues/{venue}/verification/requests/{id}/evidence/{evidence}") ResponseEntity<?> preview(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@PathVariable UUID evidence){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").header("Content-Disposition","inline; filename=synthetic-preview.png").body(service.preview(actor(p),venue,id,evidence));}
    @PostMapping("/api/admin/venues/{venue}/verification/requests/{id}/decisions") ResponseEntity<?> decide(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(service.command(actor(p),id,venue,"DECIDE",b));}
    @ExceptionHandler(VerificationFailure.class) ResponseEntity<?> failure(VerificationFailure failure,Principal p){try{denial.executeWithoutResult(s->repository.audit(p==null?null:actor(p),null,"HTTP_DENIAL","DENIED"));}catch(DataAccessException unavailable){return error(503,"Synthetic verification unavailable");}return error(failure.status,failure.getMessage());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Verification conflict; refresh authoritative history");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Synthetic verification unavailable");}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return error(400,"Malformed verification request");}
    @ExceptionHandler(IOException.class) ResponseEntity<?> interrupted(){return error(503,"Upload interrupted; refresh before retrying");}
    ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message,"labels",VerificationPolicy.LABELS));}
}

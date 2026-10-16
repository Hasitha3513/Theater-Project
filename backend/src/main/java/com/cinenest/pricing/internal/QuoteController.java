package com.cinenest.pricing.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@ConditionalOnProperty(name="cinenest.pricing.synthetic-quotes.enabled",havingValue="true")
class QuoteController {
    private final QuoteService service;
    private final QuoteDenialAudit audit;
    QuoteController(QuoteService service,QuoteDenialAudit audit){this.service=service;this.audit=audit;}
    private UUID actor(Principal principal){if(principal==null)throw new DraftFailure(401,"Sign-in required");return DraftRules.id(principal.getName());}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
    @PostMapping("/api/quotes") ResponseEntity<?> create(Principal p,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),null,"CREATE",body));}
    @GetMapping("/api/quotes/{quoteId}") ResponseEntity<?> read(Principal p,@PathVariable String quoteId){return ok(service.read(actor(p),DraftRules.id(quoteId)));}
    @PostMapping("/api/quotes/{quoteId}/revisions") ResponseEntity<?> revise(Principal p,@PathVariable String quoteId,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),DraftRules.id(quoteId),"REVISE",body));}
    @PostMapping("/api/quotes/{quoteId}/accept") ResponseEntity<?> accept(Principal p,@PathVariable String quoteId,@RequestBody Map<String,Object> body){return ok(service.command(actor(p),DraftRules.id(quoteId),"ACCEPT",body));}
    @ExceptionHandler(DraftFailure.class) ResponseEntity<?> failure(DraftFailure failure,Principal p){try{audit.denied(p==null?null:DraftRules.id(p.getName()));}catch(DataAccessException unavailable){return error(503,"Quotation unavailable");}return error(failure.status,failure.getMessage());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Quotation conflict; refresh authoritative state");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Quotation unavailable");}
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed(){return error(400,"Malformed quotation request");}
    private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message,"disclosures",QuoteRules.DISCLOSURES));}
}

package com.cinenest.pricing.internal;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local-synthetic")
@RequestMapping("/api/admin/venues/{venueId}/commercial-policies")
class CommercialPolicyDraftController {
    private final CommercialPolicyDraftService service;private final DeniedCommercialPolicyAudit audit;
    CommercialPolicyDraftController(CommercialPolicyDraftService service,DeniedCommercialPolicyAudit audit){this.service=service;this.audit=audit;}
    UUID actor(Principal p){if(p==null)throw new CommercialPolicyDraft.Failure(401,"Sign-in required");return CommercialPolicyDraftRules.id(p.getName());}
    Map<String,Object> body(HttpServletRequest request){
        if(request.getQueryString()!=null&&!request.getQueryString().isEmpty())throw CommercialPolicyDraft.invalid();
        try{if(request.getContentType()==null||!request.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw CommercialPolicyDraft.invalid();byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new CommercialPolicyDraft.Failure(413,"Policy request exceeds 64 KiB");
            try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)throw CommercialPolicyDraft.invalid();parser.skipChildren();if(parser.nextToken()!=null)throw CommercialPolicyDraft.invalid();}
            var text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();return com.nimbusds.jose.util.JSONObjectUtils.parse(text);
        }catch(CommercialPolicyDraft.Failure e){throw e;}catch(Exception malformed){throw CommercialPolicyDraft.invalid();}
    }
    void query(HttpServletRequest request,Set<String> keys){if(request.getParameterMap().entrySet().stream().anyMatch(e->!keys.contains(e.getKey())||e.getValue().length!=1))throw CommercialPolicyDraft.invalid();}
    ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(body);}
    @GetMapping ResponseEntity<?> list(Principal p,@PathVariable String venueId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size,HttpServletRequest request){query(request,Set.of("q","page","size"));return response(200,service.list(actor(p),CommercialPolicyDraftRules.id(venueId),q,page,size));}
    @GetMapping("/{policyId}") ResponseEntity<?> detail(Principal p,@PathVariable String venueId,@PathVariable String policyId,HttpServletRequest request){query(request,Set.of());return response(200,service.detail(actor(p),CommercialPolicyDraftRules.id(venueId),CommercialPolicyDraftRules.id(policyId)));}
    @GetMapping("/{policyId}/history") ResponseEntity<?> history(Principal p,@PathVariable String venueId,@PathVariable String policyId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size,HttpServletRequest request){query(request,Set.of("page","size"));return response(200,service.history(actor(p),CommercialPolicyDraftRules.id(venueId),CommercialPolicyDraftRules.id(policyId),page,size));}
    @PostMapping ResponseEntity<?> create(Principal p,@PathVariable String venueId,HttpServletRequest request){var result=service.mutate(actor(p),CommercialPolicyDraftRules.id(venueId),null,"CREATE",body(request));return response(Boolean.TRUE.equals(result.get("replayed"))?200:201,result);}
    @PutMapping("/{policyId}") ResponseEntity<?> revise(Principal p,@PathVariable String venueId,@PathVariable String policyId,HttpServletRequest request){return response(200,service.mutate(actor(p),CommercialPolicyDraftRules.id(venueId),CommercialPolicyDraftRules.id(policyId),"REVISE",body(request)));}
    @PostMapping("/{policyId}/reviews") ResponseEntity<?> review(Principal p,@PathVariable String venueId,@PathVariable String policyId,HttpServletRequest request){return response(200,service.mutate(actor(p),CommercialPolicyDraftRules.id(venueId),CommercialPolicyDraftRules.id(policyId),"REVIEW",body(request)));}
    @ExceptionHandler(CommercialPolicyDraft.Failure.class) ResponseEntity<?> failure(CommercialPolicyDraft.Failure e,Principal p){if(e.status==403){try{audit.record(p==null?null:UUID.fromString(p.getName()));}catch(RuntimeException unavailable){return response(503,Map.of("message","Policy audit unavailable"));}}return response(e.status,Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class) ResponseEntity<?> authentication(){return response(401,Map.of("message","Session expired; sign in again"));}
    @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class}) ResponseEntity<?> database(RuntimeException failure,Principal p){String state=null;for(Throwable t=failure;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException sql){state=sql.getSQLState();break;}int status="28000".equals(state)?401:"42501".equals(state)?403:Set.of("23505","40001","23503").contains(state==null?"":state)?409:"23514".equals(state)?400:503;if(status==403){try{audit.record(p==null?null:UUID.fromString(p.getName()));}catch(RuntimeException unavailable){return response(503,Map.of("message","Policy audit unavailable"));}}return response(status,Map.of("message",status==409?"Draft changed or review conflicts; reload and compare":status==403?"Review or venue authority denied":status==400?"Policy fields do not satisfy the supported draft contract":"Policy operation unavailable"));}
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) ResponseEntity<?> malformed(){return response(400,Map.of("message","Invalid policy request"));}
}

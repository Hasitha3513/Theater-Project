package com.cinenest.verification.internal;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.pricing.VerificationQuotation;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service
class VerificationService {
    final VerificationRepository repository;
    final ScopedAuthorization authorization;
    final VerificationQuotation quotations;
    final VerificationPolicy policies;
    final PrivateEvidenceStore storage;
    final SyntheticEvidenceProcessor processor;
    final TransactionTemplate tx;
    VerificationService(VerificationRepository repository,ScopedAuthorization authorization,VerificationQuotation quotations,VerificationPolicy policies,PrivateEvidenceStore storage,SyntheticEvidenceProcessor processor,PlatformTransactionManager manager){this.repository=repository;this.authorization=authorization;this.quotations=quotations;this.policies=policies;this.storage=storage;this.processor=processor;tx=new TransactionTemplate(manager);}
    static UUID id(Object value){try{return UUID.fromString(String.valueOf(value));}catch(RuntimeException invalid){throw new VerificationFailure(400,"Valid identifier required");}}
    static long revision(Map<String,Object> body){Object v=body.get("revision");if(!(v instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())throw new VerificationFailure(400,"Exact revision required");return n.longValue();}
    void eligible(UUID actor){if(!authorization.eligible(actor,true))throw new VerificationFailure(403,"Current eligible account required");}
    void reviewer(UUID actor,UUID venue){if(!authorization.permits(actor,venue,"verification.review",true)||!authorization.permits(actor,venue,"verification.evidence.read",true))throw new VerificationFailure(403,"Current venue review and evidence permissions required");}
    VerificationRepository.Request request(UUID actor,UUID id,UUID venue){
        var r=repository.request(id,true).orElseThrow(()->new VerificationFailure(404,"Verification request not found"));
        if(venue==null){if(!r.owner().equals(actor))throw new VerificationFailure(404,"Verification request not found");}
        else if(!venue.equals(r.venue())||r.owner().equals(actor))throw new VerificationFailure(403,"Review scope denied");
        return r;
    }
    void current(VerificationRepository.Request r,long revision){if(r.superseded()||r.revision()!=revision)throw new VerificationFailure(409,"Verification changed; refresh current history");}
    Map<String,Object> view(VerificationRepository.Request r){
        String state=r.state();if(state.equals("VERIFIED")&&(r.validUntil()==null||!r.validUntil().isAfter(repository.now())||repository.evidenceFor(r.id()).stream().anyMatch(e->!e.retain().isAfter(repository.now())||!e.state().equals("READY"))))state="EXPIRED";
        if(Set.of("SUBMITTED","PROCESSING","REVIEW_REQUIRED").contains(state)&&repository.evidenceFor(r.id()).stream().anyMatch(e->!e.retain().isAfter(repository.now())||Set.of("DELETED","DELETION_PENDING","CLEANUP_FAILED").contains(e.state())||Set.of("ALLOCATED","UPLOADING").contains(e.state())&&!e.expires().isAfter(repository.now())))state="EXPIRED";
        var out=new LinkedHashMap<String,Object>();out.put("id",r.id().toString());out.put("venueId",r.venue().toString());out.put("quoteId",r.quote().toString());out.put("revision",r.revision());out.put("generation",r.generation());out.put("superseded",r.superseded());out.put("previousId",r.previous()==null?null:r.previous().toString());out.put("state",state);out.put("validUntil",r.validUntil()==null?null:r.validUntil().toString());out.put("labels",VerificationPolicy.LABELS);out.put("synthetic",true);out.put("physicalAdmission",false);
        out.put("evidence",repository.evidenceFor(r.id()).stream().map(e->Map.of("id",e.id().toString(),"state",e.state(),"type",e.type(),"expiresAt",e.expires().toString())).toList());out.put("history",repository.history(r.id()));return out;
    }
    Map<String,Object> read(UUID actor,UUID id,UUID venue){policies.current();return tx.execute(status->{eligible(actor);if(venue!=null)reviewer(actor,venue);var r=request(actor,id,venue);repository.audit(actor,id,venue==null?"STATUS":"REVIEW_CONTEXT","PERMIT");return view(r);});}
    List<Map<String,Object>> queue(UUID actor,UUID venue){policies.current();return tx.execute(status->{eligible(actor);reviewer(actor,venue);repository.audit(actor,null,"SCOPED_QUEUE","PERMIT");return repository.queue(venue,actor).stream().map(this::view).toList();});}
    Map<String,Object> command(UUID actor,UUID target,UUID venue,String action,Map<String,Object> body){
        var policy=policies.current();UUID key=id(body.get("commandKey"));String hash=VerificationPolicy.hash(action+":"+target+":"+venue+":"+new TreeMap<>(body));
        return tx.execute(status->{eligible(actor);if(venue!=null)reviewer(actor,venue);
            // Re-authorize target even for a persisted replay; no stale authority cache.
            var r=target==null?null:request(actor,target,venue);
            var replay=repository.command(actor,key,hash);if(replay.isPresent())return replay.get();
            Map<String,Object> result;
            if(action.equals("CREATE")||action.equals("RESUBMIT")){
                if(!Boolean.TRUE.equals(body.get("consent"))||!policy.version().equals(body.get("policyVersion")))throw new VerificationFailure(400,"Explicit current synthetic consent required");
                UUID quote=id(body.get("quoteId"));var context=quotations.ownedSynthetic(actor,quote).orElseThrow(()->new VerificationFailure(403,"Owned synthetic quotation required"));
                if(r!=null){current(r,revision(body));if(!r.quote().equals(quote))throw new VerificationFailure(409,"Resubmission must retain its original quotation owner");repository.supersede(r.id());}
                repository.policy(policy);UUID next=UUID.randomUUID();repository.create(new VerificationRepository.Request(next,actor,context.venue(),quote,policy.id(),r==null?null:r.id(),r==null?1:r.generation()+1,0,"NOT_SUBMITTED",false,null));
                r=repository.request(next,true).orElseThrow();result=view(r);
            }else{
                current(r,revision(body));if(!r.policy().equals(policy.id()))throw new VerificationFailure(409,"Policy changed; submit a new consent generation");
                if(action.equals("ALLOCATE")){
                    if(!Set.of("NOT_SUBMITTED","SUBMITTED","REVIEW_REQUIRED").contains(r.state()))throw new VerificationFailure(409,"Submission already processing");
                    String type=String.valueOf(body.get("documentType"));if(!Set.of("NIC","PASSPORT").contains(type))throw new VerificationFailure(400,"Synthetic NIC or passport only");
                    int count=repository.evidenceFor(r.id()).size();if(count>=2)throw new VerificationFailure(409,"Maximum two images per submission");
                    var now=repository.now();var e=new VerificationRepository.Evidence(UUID.randomUUID(),r.id(),UUID.randomUUID(),UUID.randomUUID(),type,"ALLOCATED",now.plus(policy.upload()),now.plus(policy.retention()),0,null);repository.allocate(e,count);repository.transition(r.id(),"SUBMITTED",null);result=new LinkedHashMap<>(view(repository.request(r.id(),true).orElseThrow()));result.put("uploadId",e.id().toString());
                }else if(action.equals("DECIDE")){
                    if(venue==null)throw new VerificationFailure(403,"Reviewer workflow required");
                    String verdict=String.valueOf(body.get("verdict")),reason=String.valueOf(body.get("reason"));
                    if(!Set.of("VERIFIED","REJECTED","REVOKED").contains(verdict)||!Set.of("SYNTHETIC_MATCH_18_PLUS","INCONSISTENT","UNREADABLE","EXPIRED_EVIDENCE","CORRECTION_REQUIRED","WITHDRAWN").contains(reason))throw new VerificationFailure(400,"Approved decision and reason required");
                    if(verdict.equals("VERIFIED")&&!reason.equals("SYNTHETIC_MATCH_18_PLUS")||!verdict.equals("VERIFIED")&&reason.equals("SYNTHETIC_MATCH_18_PLUS"))throw new VerificationFailure(400,"Decision and reason mismatch");
                    if(verdict.equals("REVOKED")?!r.state().equals("VERIFIED"):!r.state().equals("REVIEW_REQUIRED"))throw new VerificationFailure(409,"Current manual review state required");
                    var evidence=repository.evidenceFor(r.id());if(!verdict.equals("REVOKED")&&(evidence.isEmpty()||evidence.stream().anyMatch(e->!e.state().equals("READY")||!e.retain().isAfter(repository.now()))))throw new VerificationFailure(409,"Complete unexpired simulated scan required");
                    Instant until=repository.now().plus(policy.decision());for(var e:evidence)if(e.retain().isBefore(until))until=e.retain();
                    repository.decision(UUID.randomUUID(),r,actor,verdict,reason,until);repository.transition(r.id(),verdict,until);result=view(repository.request(r.id(),true).orElseThrow());
                }else throw new VerificationFailure(400,"Unsupported operation");
            }
            repository.audit(actor,r.id(),action,"PERMIT");repository.command(actor,key,hash,result);return result;
        });
    }
    void upload(UUID actor,UUID upload,String mime,InputStream input)throws IOException{
        var policy=policies.current();
        var initial=tx.execute(s->{eligible(actor);var e=repository.evidence(upload,false).orElseThrow(()->new VerificationFailure(404,"Upload not found"));var r=request(actor,e.request(),null);if(r.superseded()||!Set.of("ALLOCATED").contains(e.state())||!e.expires().isAfter(repository.now())||!e.retain().isAfter(repository.now()))throw new VerificationFailure(409,"Upload expired or already completed");repository.evidenceState(upload,"UPLOADING");repository.audit(actor,r.id(),"UPLOAD_BEGIN","PERMIT");return e;});
        byte[] bytes=input.readNBytes(VerificationPolicy.MAX_BYTES+1);if(bytes.length>VerificationPolicy.MAX_BYTES)throw new VerificationFailure(413,"Synthetic image exceeds 5 MiB");
        // Admit only configured synthetic bytes before image decoding or persistent storage.
        if(!policy.fixtures().contains(VerificationPolicy.hash(bytes)))throw new VerificationFailure(415,"Only explicitly approved synthetic fixtures are allowed");
        if(!Set.of("image/png","image/jpeg").contains(mime))throw new VerificationFailure(415,"Synthetic image type rejected");
        storage.put(initial.object(),bytes);
        tx.executeWithoutResult(s->{eligible(actor);var r=request(actor,initial.request(),null);var e=repository.evidence(upload,true).orElseThrow();if(r.superseded()||!e.state().equals("UPLOADING")||!e.expires().isAfter(repository.now()))throw new VerificationFailure(409,"Upload changed; refresh authoritative state");repository.uploaded(upload,VerificationPolicy.hash(bytes),mime,bytes.length);repository.audit(actor,r.id(),"UPLOAD_QUARANTINED","PERMIT");});
    }
    Map<String,Object> complete(UUID actor,UUID upload,Map<String,Object> body){var policy=policies.current();UUID key=id(body.get("commandKey"));String hash=VerificationPolicy.hash("COMPLETE"+upload+new TreeMap<>(body));return tx.execute(s->{eligible(actor);var e=repository.evidence(upload,false).orElseThrow(()->new VerificationFailure(404,"Upload not found"));var r=request(actor,e.request(),null);var replay=repository.command(actor,key,hash);if(replay.isPresent())return replay.get();current(r,revision(body));if(!e.state().equals("QUARANTINED")||!e.expires().isAfter(repository.now())||!r.policy().equals(policy.id()))throw new VerificationFailure(409,"Current quarantined upload required");repository.work(upload);repository.transition(r.id(),"PROCESSING",null);repository.audit(actor,r.id(),"SIMULATED_SCAN_REQUESTED","PERMIT");var result=view(repository.request(r.id(),true).orElseThrow());repository.command(actor,key,hash,result);return result;});}
    byte[] preview(UUID actor,UUID venue,UUID request,UUID evidence){policies.current();return tx.execute(s->{eligible(actor);reviewer(actor,venue);var r=request(actor,request,venue);var e=repository.evidence(evidence,true).filter(v->v.request().equals(r.id())).orElseThrow(()->new VerificationFailure(404,"Evidence not found"));if(r.superseded()||!e.state().equals("READY")||!e.retain().isAfter(repository.now()))throw new VerificationFailure(409,"Evidence is unavailable");repository.audit(actor,r.id(),"PRIVATE_PREVIEW","PERMIT");return storage.get(e.preview());});}
    void reconcile(){
        var policy=policies.maintenance();
        for(UUID id:policies.env.getProperty("cinenest.verification.enabled",Boolean.class,false)?repository.pending(repository.now()):List.<UUID>of()){
            var e=tx.execute(s->{var item=repository.evidence(id,false).orElseThrow();repository.request(item.request(),true);if(!item.state().equals("QUARANTINED")||!repository.claim(id,repository.now().plus(policy.cleanup())))return null;return item;});if(e==null)continue;
            try{var result=processor.process(storage.get(e.object()),e.mime(),policy);storage.put(e.preview(),result.preview());tx.executeWithoutResult(s->{var r=repository.request(e.request(),true).orElseThrow();var current=repository.evidence(id,true).orElseThrow();if(!current.state().equals("QUARANTINED")||r.superseded()||!current.retain().isAfter(repository.now())){repository.evidenceState(id,"DELETION_PENDING");}else{repository.evidenceState(id,"READY");if(repository.evidenceFor(r.id()).stream().allMatch(v->v.state().equals("READY")))repository.transition(r.id(),"REVIEW_REQUIRED",null);}repository.workState(id,"DONE");repository.audit(null,r.id(),"SIMULATED_SCAN_COMPLETE","NOT_IDENTITY_APPROVAL");});}
            catch(VerificationFailure failure){tx.executeWithoutResult(s->{repository.request(e.request(),true);if(failure.status==415){repository.evidenceState(id,"REJECTED");repository.workState(id,"DONE");}else repository.workState(id,"FAILED");repository.audit(null,e.request(),"SIMULATED_PROCESSING_FAILURE","QUARANTINED_OR_REJECTED");});}
        }
        for(var item:repository.cleanup(repository.now())){
            tx.executeWithoutResult(s->{repository.request(item.request(),true);repository.evidence(item.id(),true);repository.evidenceState(item.id(),"DELETION_PENDING");repository.audit(null,item.request(),"CLEANUP_REQUESTED","PENDING");});
            try{storage.delete(item.object());storage.delete(item.preview());tx.executeWithoutResult(s->{repository.request(item.request(),true);repository.evidenceState(item.id(),"DELETED");repository.workState(item.id(),"DONE");repository.audit(null,item.request(),"CLEANUP_COMPLETE","LOCAL_OBJECTS_ONLY");});}
            catch(VerificationFailure unavailable){tx.executeWithoutResult(s->{repository.evidenceState(item.id(),"CLEANUP_FAILED");repository.audit(null,item.request(),"CLEANUP_FAILURE","RETRY_REQUIRED");});}
        }
        for(UUID orphan:storage.olderThan(repository.now().minus(policy.cleanup())))if(!repository.referenced(orphan)){
            try{storage.delete(orphan);}catch(VerificationFailure unavailable){tx.executeWithoutResult(s->repository.audit(null,null,"ORPHAN_CLEANUP_FAILURE","RETRY_REQUIRED"));}
        }
    }
}

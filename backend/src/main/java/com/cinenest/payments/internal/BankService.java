package com.cinenest.payments.internal;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.pricing.BankQuotation;
import com.cinenest.booking.BankRequestHolds;
import com.cinenest.verification.BankEligibility;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service
class BankService {
 final BankRepository repo;final ScopedAuthorization auth;final BankQuotation quotes;final BankRequestHolds holds;final BankEligibility verification;
 final ReviewerNotice notice;final BankPolicy policy;final PrivateEvidenceStore storage;final SyntheticEvidenceProcessor scanner;final TransactionTemplate tx;
 BankService(BankRepository repo,ScopedAuthorization auth,BankQuotation quotes,BankRequestHolds holds,BankEligibility verification,BankPolicy policy,PrivateEvidenceStore storage,SyntheticEvidenceProcessor scanner,ReviewerNotice notice,PlatformTransactionManager manager){this.notice=notice;this.repo=repo;this.auth=auth;this.quotes=quotes;this.holds=holds;this.verification=verification;this.policy=policy;this.storage=storage;this.scanner=scanner;tx=new TransactionTemplate(manager);}
 static UUID id(Object v){try{return UUID.fromString(String.valueOf(v));}catch(RuntimeException e){throw new BankFailure(400,"Valid identifier required");}}
 static long number(Object v){try{if(v==null||!v.toString().matches("[0-9]{1,19}"))throw new IllegalArgumentException();return Long.parseLong(v.toString());}catch(RuntimeException e){throw new BankFailure(400,"Exact nonnegative integer required");}}
 static void keys(Map<String,Object> b,String... allowed){if(!Set.of(allowed).containsAll(b.keySet()))throw new BankFailure(400,"Unexpected request fields");}
 void eligible(UUID a){if(!auth.eligible(a,true))throw new BankFailure(403,"Current synthetic eligible account required");}
 void reviewer(UUID a,UUID venue,String action){if(!auth.permits(a,venue,"bank.claim.review",true)||!auth.permits(a,venue,"bank.evidence.read",true)||action.equals("ESCALATE")&&!auth.permits(a,venue,"bank.claim.escalate",true))throw new BankFailure(403,"Current matching venue grants required");}
 BankRepository.Request owned(UUID a,UUID id,UUID venue){var r=repo.request(id,true).orElseThrow(()->new BankFailure(404,"Request not found"));if(venue==null?!r.owner().equals(a):!r.venue().equals(venue)||r.owner().equals(a))throw new BankFailure(404,"Request not found");return r;}
 void revision(BankRepository.Request r,Map<String,Object>b){if(r.revision()!=number(b.get("revision")))throw new BankFailure(409,"Request changed; refresh history");}
 Map<String,Object> view(BankRepository.Request r){
  var now=repo.now();var out=new LinkedHashMap<String,Object>();out.put("id",r.id().toString());out.put("venueId",r.venue().toString());out.put("quoteId",r.quote().toString());out.put("revision",r.revision());out.put("requestState",now.isBefore(r.expires())?r.state():"EXPIRED");out.put("reviewState",r.review());out.put("initiateBy",r.initiate().toString());out.put("escalateAt",r.escalate().toString());out.put("reviewWindow",repo.policy(r.policy()).get("reviewWindow"));out.put("expiresAt",r.expires().toString());out.put("submitBy",r.submit().toString());out.put("reviewBy",r.reviewBy().toString());out.put("overdue",now.isAfter(r.reviewBy()));out.put("amountMinor",r.amount());out.put("currency",r.currency());out.put("labels",BankPolicy.LABELS);out.put("synthetic",true);out.put("paymentVerified",false);out.put("bookingConfirmed",false);out.put("instructionVersion",repo.policy(r.policy()).get("version"));out.put("instructions","SYNTHETIC ONLY — NO RECEIVING ACCOUNT — DO NOT TRANSFER MONEY");
  var inventory=holds.inspect(r.owner(),r.hold());out.put("inventoryState",inventory.state());out.put("inventoryExpiresAt",inventory.expires().toString());
  var claim=repo.latest(r.id());out.put("claimId",claim.map(c->c.id().toString()).orElse(null));out.put("submissionTimely",claim.map(c->repo.timely(c.id(),r.submit())).orElse(false));out.put("evidence",claim.map(c->repo.evidence(c.id()).stream().map(e->Map.of("id",e.id().toString(),"state",e.state(),"expiresAt",e.expires().toString(),"possibleDuplicate",repo.duplicate(e.id()))).toList()).orElse(List.of()));out.put("work",repo.workSummary(r.id()));out.put("history",repo.history(r.id()));out.put("claims",repo.claims(r.id()));return out;
 }
 Map<String,Object> read(UUID a,UUID id,UUID venue){policy.current();return tx.execute(s->{eligible(a);if(venue!=null)reviewer(a,venue,"READ");var r=owned(a,id,venue);repo.audit(a,id,"STATUS","PERMIT");return view(r);});}
 List<Map<String,Object>> queue(UUID a,UUID venue){policy.current();return tx.execute(s->{eligible(a);reviewer(a,venue,"READ");repo.audit(a,null,"QUEUE","PERMIT");return repo.queue(venue,a).stream().map(this::view).toList();});}
 Map<String,Object> command(UUID a,UUID target,UUID venue,String action,Map<String,Object>b){
  var p=policy.current();switch(action){case "CREATE"->keys(b,"quoteId","quoteRevision","contentHash","commandKey");case "CLAIM","RESUBMIT"->keys(b,"revision","commandKey","declaredAmountMinor","declaredPaidAt","declaredReference");case "DECIDE"->keys(b,"revision","commandKey","action","reason");case "ESCALATE"->keys(b,"revision","commandKey","action","reason","handoverTo");default->throw new BankFailure(400,"Unsupported command");}UUID key=id(b.get("commandKey"));String hash=BankPolicy.commandHash(action+":"+target+":"+venue,b);
  return tx.execute(s->{eligible(a);if(venue!=null)reviewer(a,venue,action);var r=target==null?null:owned(a,target,venue);var replay=repo.command(a,key,hash);if(replay.isPresent())return replay.get();
   if(action.equals("CREATE")){
    UUID q=id(b.get("quoteId"));var facts=quotes.accepted(a,q,number(b.get("quoteRevision")),Objects.toString(b.get("contentHash"),"")).orElseThrow(()->new BankFailure(409,"Current accepted synthetic BANK quotation required"));
    if(!verification.eligible(a,q,facts.venue()))throw new BankFailure(409,"Current synthetic verification required");
    if(!Boolean.TRUE.equals(facts.snapshot().get("synthetic"))||!Boolean.FALSE.equals(facts.snapshot().get("commercialIssuance"))||!"LKR".equals(facts.snapshot().get("currency")))throw new BankFailure(409,"Synthetic terms required");
    Instant now=repo.now(),start=Instant.parse(facts.snapshot().get("sessionStart").toString());if(!start.isAfter(now.plus(p.lead()))||!start.isAfter(now.plus(p.hold()))||!start.isAfter(now.plus(p.request())))throw new BankFailure(409,"Configured synthetic lead/deadlines exceed session start");
    LocalTime local=now.atZone(ZoneId.of(facts.snapshot().get("timezone").toString())).toLocalTime();if(local.isBefore(p.opens())||!local.isBefore(p.closes()))throw new BankFailure(409,"Outside configured synthetic review window");
    UUID next=UUID.randomUUID();var times=Map.of("expires",now.plus(p.request()),"initiate",now.plus(p.initiation()),"submit",now.plus(p.submission()),"review",now.plus(p.review()),"escalate",now.plus(p.escalation()),"hold",now.plus(p.hold()));
    holds.convert(a,facts.hold(),facts.holdRevision(),next,times.get("hold"));repo.create(next,facts,p,times);repo.work(next,null,"EXPIRY",times.get("expires"));if(times.get("hold").isBefore(times.get("expires")))repo.work(next,null,"EXPIRY",times.get("hold"));repo.work(next,null,"ESCALATION",times.get("escalate"));r=repo.request(next,true).orElseThrow();
   }else{
    revision(r,b);if(!r.policy().equals(p.id()))throw new BankFailure(409,"Policy changed; original request retained for resolution");
    if(action.equals("CLAIM")||action.equals("RESUBMIT")){
     if(venue!=null)throw new BankFailure(403,"Owner submission required");var prior=repo.latest(r.id());if(action.equals("CLAIM")&&prior.isPresent())throw new BankFailure(409,"Use explicit resubmission");
     Instant paid;try{paid=Instant.parse(Objects.toString(b.get("declaredPaidAt"),""));}catch(RuntimeException invalid){throw new BankFailure(400,"Declared timestamp required");}
     if(b.containsKey("declaredReference")&&!(b.get("declaredReference") instanceof String))throw new BankFailure(400,"Declared reference must be text");String ref=Objects.toString(b.get("declaredReference"),"");if(ref.length()>100||ref.chars().anyMatch(Character::isISOControl))throw new BankFailure(400,"Safe declared reference required");
     repo.claim(UUID.randomUUID(),r.id(),prior.map(c->c.generation()+1).orElse(1),number(b.get("declaredAmountMinor")),paid,ref);repo.transition(r.id(),r.expires().isAfter(repo.now())?"REVIEW_PENDING":"EXPIRED","PENDING");repo.work(r.id(),null,"NOTIFY",repo.now());
    }else if(action.equals("DECIDE")||action.equals("ESCALATE")){
     if(venue==null)throw new BankFailure(403,"Reviewer workflow required");String verdict=action.equals("ESCALATE")?Objects.toString(b.get("action"),"ESCALATE"):Objects.toString(b.get("action"),"");String reason=Objects.toString(b.get("reason"),"");
     if(!(action.equals("DECIDE")?Set.of("REVIEWED","NEEDS_INFORMATION","REJECTED"):Set.of("ESCALATE","HANDOVER","DISPUTE")).contains(verdict)||!Set.of("SYNTHETIC_REVIEW","CORRECTION_REQUIRED","UNREADABLE","OVERDUE","DISPUTED","STAFF_HANDOVER").contains(reason))throw new BankFailure(400,"Supported review action/reason required");
     var claim=repo.latest(r.id()).orElseThrow(()->new BankFailure(409,"No claim submitted"));if(verdict.equals("REVIEWED")){var evidence=repo.evidence(claim.id());if(evidence.isEmpty()||evidence.stream().anyMatch(e->!e.state().equals("READY")||!e.retain().isAfter(repo.now())))throw new BankFailure(409,"Unexpired safely processed synthetic evidence required");}
     UUID handover=verdict.equals("HANDOVER")?id(b.get("handoverTo")):null;if(handover!=null&&(handover.equals(a)||handover.equals(r.owner())||!auth.permits(handover,r.venue(),"bank.claim.review",false)||!auth.permits(handover,r.venue(),"bank.evidence.read",false)))throw new BankFailure(403,"Handover requires another currently eligible scoped reviewer");
     repo.decision(r,claim.id(),a,verdict,reason,handover);repo.transition(r.id(),r.expires().isAfter(repo.now())?(verdict.equals("REVIEWED")?"REVIEWED":r.state()):"EXPIRED",action.equals("DECIDE")?verdict:r.review());repo.work(r.id(),null,"NOTIFY",repo.now());
    }else throw new BankFailure(400,"Unsupported command");
   }
   repo.audit(a,r.id(),action,"PERMIT_NOT_PAYMENT");var result=view(repo.request(r.id(),true).orElseThrow());repo.command(a,key,hash,result);return result;
  });
 }
 Map<String,Object> allocate(UUID a,UUID claimId,Map<String,Object>b){var p=policy.current();keys(b,"revision","commandKey");UUID key=id(b.get("commandKey"));String hash=BankPolicy.commandHash("ALLOCATE:"+claimId,b);return tx.execute(s->{eligible(a);var c=repo.claim(claimId).orElseThrow(()->new BankFailure(404,"Claim not found"));var r=owned(a,c.request(),null);var replay=repo.command(a,key,hash);if(replay.isPresent())return replay.get();revision(r,b);if(!repo.latest(r.id()).orElseThrow().id().equals(claimId)||!r.policy().equals(p.id()))throw new BankFailure(409,"Current claim/policy required");var all=repo.evidence(c.id());if(all.size()>=2)throw new BankFailure(409,"Two images maximum");Instant now=repo.now();var e=new BankRepository.Evidence(UUID.randomUUID(),r.id(),c.id(),UUID.randomUUID(),UUID.randomUUID(),"ALLOCATED",now.plus(p.upload()),now.plus(p.retention()),null);repo.allocate(e,all.size());repo.transition(r.id(),r.state(),r.review());repo.audit(a,r.id(),"ALLOCATE_UPLOAD","PERMIT");var out=new LinkedHashMap<>(view(repo.request(r.id(),true).orElseThrow()));out.put("uploadId",e.id().toString());repo.command(a,key,hash,out);return out;});}
 void upload(UUID a,UUID id,String mime,InputStream input)throws IOException{
  var p=policy.current();var e=tx.execute(s->{eligible(a);var item=repo.upload(id,false).orElseThrow(()->new BankFailure(404,"Upload not found"));var r=owned(a,item.request(),null);if(!repo.latest(r.id()).orElseThrow().id().equals(item.claim())||!item.expires().isAfter(repo.now())||!item.state().equals("ALLOCATED"))throw new BankFailure(409,"Current upload required");repo.evidenceState(id,"UPLOADING");return item;});
  byte[] bytes=input.readNBytes(BankPolicy.MAX_BYTES+1);if(bytes.length>BankPolicy.MAX_BYTES)throw new BankFailure(413,"Image exceeds 5 MiB");scanner.process(bytes,mime,p);storage.put(e.object(),bytes);
  tx.executeWithoutResult(s->{eligible(a);var r=owned(a,e.request(),null);var current=repo.upload(id,true).orElseThrow();if(!current.state().equals("UPLOADING")||!current.expires().isAfter(repo.now())||!repo.latest(r.id()).orElseThrow().id().equals(e.claim()))throw new BankFailure(409,"Upload changed");repo.uploaded(id,BankPolicy.hash(bytes),mime,bytes.length);repo.audit(a,r.id(),"UPLOAD","QUARANTINED_NOT_PAYMENT");});
 }
 Map<String,Object> complete(UUID a,UUID id,Map<String,Object>b){policy.current();keys(b,"revision","commandKey");UUID key=id(b.get("commandKey"));String hash=BankPolicy.commandHash("COMPLETE:"+id,b);return tx.execute(s->{eligible(a);var e=repo.upload(id,false).orElseThrow(()->new BankFailure(404,"Upload not found"));var r=owned(a,e.request(),null);var replay=repo.command(a,key,hash);if(replay.isPresent())return replay.get();revision(r,b);if(!e.state().equals("QUARANTINED")||!e.expires().isAfter(repo.now())||!repo.latest(r.id()).orElseThrow().id().equals(e.claim()))throw new BankFailure(409,"Current quarantined upload required");repo.completeEvidence(id);repo.work(r.id(),id,"SCAN",repo.now());repo.transition(r.id(),r.state(),r.review());repo.audit(a,r.id(),"SCAN_QUEUED","SIMULATED");var out=view(repo.request(r.id(),true).orElseThrow());repo.command(a,key,hash,out);return out;});}
 byte[] preview(UUID a,UUID venue,UUID request,UUID evidence){policy.current();return tx.execute(s->{eligible(a);reviewer(a,venue,"READ");var r=owned(a,request,venue);var e=repo.upload(evidence,true).filter(v->v.request().equals(r.id())).orElseThrow(()->new BankFailure(404,"Evidence not found"));if(!e.state().equals("READY")||!e.retain().isAfter(repo.now()))throw new BankFailure(409,"Preview unavailable");repo.audit(a,request,"PREVIEW","PERMIT");return storage.get(e.preview());});}
 void reconcile(){var p=policy.maintenance();
  for(var work:repo.pending()){
   UUID id=(UUID)work.get("id"),request=(UUID)work.get("request"),evidence=(UUID)work.get("evidence");String kind=work.get("kind").toString();
   int attempt=tx.execute(s->repo.claimWork(id,repo.now().plus(p.cleanup())));if(attempt==0)continue;
   try{
    if(kind.equals("SCAN")){var e=repo.upload(evidence,false).orElseThrow();if(e.retain().isAfter(repo.now())&&e.state().equals("QUARANTINED")){var processed=scanner.process(storage.get(e.object()),e.mime(),p);storage.put(e.preview(),processed.preview());}}
    final String delivery=kind.equals("NOTIFY")?notice.deliver(request):"DONE";
    tx.executeWithoutResult(s->{var r=repo.request(request,true).orElseThrow();if(!repo.owns(id,attempt))return;
     if(kind.equals("SCAN")){var e=repo.upload(evidence,true).orElseThrow();if(e.state().equals("QUARANTINED"))repo.evidenceState(evidence,e.retain().isAfter(repo.now())?"READY":"DELETION_PENDING");}
     if(kind.equals("EXPIRY")){holds.inspect(r.owner(),r.hold());if(!r.expires().isAfter(repo.now())&&!r.state().equals("EXPIRED"))repo.transition(request,"EXPIRED",r.review());}
     repo.workState(id,kind.equals("NOTIFY")?delivery:"DONE",repo.now().plus(p.cleanup()));repo.audit(null,request,"WORK_"+kind,kind.equals("NOTIFY")?"DELIVERY_DISABLED":kind.equals("ESCALATION")?"OVERDUE_REQUIRES_STAFF":"DONE");});
   }catch(RuntimeException failure){tx.executeWithoutResult(s->{repo.request(request,true).orElseThrow();if(repo.owns(id,attempt)){var current=repo.request(request,true).orElseThrow();boolean stop=kind.equals("NOTIFY")&&!current.escalate().isAfter(repo.now());repo.workState(id,stop?"DONE":"FAILED",repo.now().plus(p.cleanup()));repo.audit(null,request,"WORK_FAILED",stop?"DELIVERY_UNAVAILABLE_ESCALATED":"RETRY_REQUIRED");}});}
  }
  for(var e:repo.cleanup()){
   tx.executeWithoutResult(s->{repo.request(e.request(),true);repo.evidenceState(e.id(),"DELETION_PENDING");repo.audit(null,e.request(),"CLEANUP","PENDING");});
   try{storage.delete(e.object());storage.delete(e.preview());tx.executeWithoutResult(s->{repo.request(e.request(),true);repo.evidenceState(e.id(),"DELETED");repo.audit(null,e.request(),"CLEANUP","LOCAL_OBJECTS_DELETED");});}catch(RuntimeException failure){tx.executeWithoutResult(s->repo.evidenceState(e.id(),"CLEANUP_FAILED"));}
  }
  for(UUID orphan:storage.olderThan(repo.now().minus(p.cleanup())))if(!repo.referenced(orphan))storage.delete(orphan);
 }
}

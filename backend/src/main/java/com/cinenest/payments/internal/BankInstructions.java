package com.cinenest.payments.internal;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
@Service
class BankInstructions {
 record View(UUID venueId,UUID instructionRevisionId,String instructionVersion,String mode,Instant effectiveFrom,Instant effectiveUntil,Instant approvedAt,Instant observedAt,String approvalEvidence,boolean independentApprovalRecorded,InstructionResolver.Fixture instruction,boolean synthetic,boolean collectionEnabled,UUID requestId){}
 record Result(int status,View body){}
 final InstructionRepository repo;final InstructionResolver resolver;final ScopedAuthorization auth;final Environment env;final TransactionTemplate snapshot,audit;
 BankInstructions(InstructionRepository repo,InstructionResolver resolver,ScopedAuthorization auth,Environment env,PlatformTransactionManager manager){this.repo=repo;this.resolver=resolver;this.auth=auth;this.env=env;snapshot=new TransactionTemplate(manager);snapshot.setReadOnly(true);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(10);audit=new TransactionTemplate(manager);audit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);audit.setTimeout(10);}
 boolean enabled(){return env.getProperty("cinenest.bank.instructions.enabled",Boolean.class,false);}
 void configured(){if(!enabled())throw new BankFailure(404,"Staging instructions unavailable");var profiles=Set.of(env.getActiveProfiles());if(!(profiles.equals(Set.of("synthetic-quotation"))||profiles.equals(Set.of("local-synthetic","synthetic-quotation")))||!"STAGING_ONLY".equals(env.getProperty("cinenest.bank.instructions.mode")))throw new BankFailure(503,"Staging instructions unavailable");}
 static UUID canonical(String s){try{UUID v=UUID.fromString(s);if(!v.toString().equals(s))throw new IllegalArgumentException();return v;}catch(RuntimeException e){throw new BankFailure(400,"Malformed instruction reference");}}
 boolean permitted(UUID actor,UUID venue,boolean request,boolean lock){return auth.eligible(actor,lock)&&auth.member(actor,venue,lock)&&auth.permits(actor,venue,"bank.instructions.read",lock)&&(!request||auth.permits(actor,venue,"bank.claim.review",lock)&&auth.permits(actor,venue,"bank.evidence.read",lock));}
 View observe(UUID actor,UUID venue,UUID target,boolean request){
  InstructionRepository.Revision r;
  if(request){var found=repo.request(venue,target).orElseThrow(()->new BankFailure(404,"Instruction context unavailable"));if(found.owner().equals(actor))throw new BankFailure(404,"Instruction context unavailable");r=found.revision().orElseThrow(()->new BankFailure(409,"No staging instruction binding recorded"));}
  else r=repo.revision(venue,target).orElseThrow(()->new BankFailure(404,"Instruction context unavailable"));
  var fixture=resolver.resolve(r.reference(),r.referenceVersion(),r.digest());var at=repo.now();
  return new View(venue,r.id(),r.version(),"STAGING_ONLY",r.from(),r.until(),r.approved(),at,"STAGING_FIXTURE",true,fixture,true,false,request?target:null);
 }
 Result read(UUID actor,String rawVenue,String rawTarget,boolean request,boolean invalidParameters,UUID correlation){
  String action=request?"REQUEST_READ":"DETAIL_READ";UUID venue=null;
  try{
   configured();venue=canonical(rawVenue);UUID target=canonical(rawTarget);
   if(!permitted(actor,venue,request,false))throw new BankFailure(403,"Instruction access denied");
   if(invalidParameters)throw new BankFailure(400,"Instruction queries and bodies are not supported");
   final UUID v=venue;View view=snapshot.execute(s->observe(actor,v,target,request));
   return audit.execute(s->{
    if(!permitted(actor,v,request,true)){repo.audit(actor,null,null,null,action,"DENY","ACCESS_DENIED",repo.now(),correlation);return new Result(403,null);}
    repo.audit(actor,v,view.instructionRevisionId(),view.requestId(),action,"ALLOW","SCOPED_READ",view.observedAt(),correlation);return new Result(200,view);
   });
  }catch(BankFailure e){String reason=switch(e.status){case 400->"INVALID_REQUEST";case 403->"ACCESS_DENIED";case 404->"NOT_FOUND";case 409->"UNBOUND_REQUEST";default->"UNAVAILABLE";};return denied(actor,action,e.status,reason,correlation);}
  catch(RuntimeException unavailable){return denied(actor,action,503,"UNAVAILABLE",correlation);}
 }
 private Result denied(UUID actor,String action,int status,String reason,UUID correlation){try{return audit.execute(s->{repo.audit(actor,null,null,null,action,"DENY",reason,repo.now(),correlation);return new Result(status,null);});}catch(RuntimeException unavailable){return new Result(503,null);}}
 /** Invoked only inside the existing explicit CREATE transaction; never by reads or replay. */
 void bind(UUID actor,UUID request,UUID venue,String version){
  if(!enabled())return;configured();
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new BankFailure(503,"Staging binding requires the request transaction");
  var revision=repo.version(venue,version).orElseThrow(()->new BankFailure(503,"Approved staging instruction version unavailable"));
  resolver.resolve(revision.reference(),revision.referenceVersion(),revision.digest());
  var now=repo.now();if(now.isBefore(revision.from())||!now.isBefore(revision.until()))throw new BankFailure(409,"Staging instruction version is not effective");
  repo.bind(request,revision);try{repo.audit(actor,venue,revision.id(),request,"REQUEST_BOUND","ALLOW","BOUND",now,UUID.randomUUID());}catch(org.springframework.dao.DataAccessException failure){throw new BankFailure(503,"Staging binding audit unavailable");}
 }
}

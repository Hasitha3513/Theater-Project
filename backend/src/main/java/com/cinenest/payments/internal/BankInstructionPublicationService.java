package com.cinenest.payments.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.cinenest.payments.internal.BankInstructionPublicationRepository.*;

@Service
final class BankInstructionPublicationService {
    record Result(int status,Map<String,Object> body) {}
    private final BankInstructionPublicationPolicy policy;
    private final BankInstructionPublicationRepository repo;
    private final ScopedAuthorization auth;
    private final TransactionTemplate tx,denial;
    BankInstructionPublicationService(BankInstructionPublicationPolicy policy,BankInstructionPublicationRepository repo,
                                     ScopedAuthorization auth,PlatformTransactionManager manager) {
        this.policy=policy;this.repo=repo;this.auth=auth;tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(10);
        denial=new TransactionTemplate(manager);denial.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);denial.setTimeout(10);
    }
    private void permit(UUID actor,UUID venue,String action,boolean lock) {
        if(!auth.eligible(actor,lock)||!auth.member(actor,venue,lock)||!auth.permits(actor,venue,action,lock))throw new BankFailure(403,"Staging publication access denied");
    }
    Result command(UUID actor,UUID venue,UUID target,String action,Map<String,Object> body,UUID correlation) {
        try {
            policy.configured();if(actor==null)throw new BankFailure(401,"Authentication required");
            if(!Set.of("PROPOSE","REVIEW","PUBLISH").contains(action))throw new BankFailure(400,"Unsupported command");
            var draft=action.equals("PROPOSE")?policy.draft(body):null;
            if(draft==null)BankInstructionPublicationPolicy.keys(body,"commandKey","expectedRevision","snapshotHash");
            UUID key=BankInstructionPublicationPolicy.id(body.get("commandKey"));long expected=BankInstructionPublicationPolicy.revision(body.get("expectedRevision"));
            String reviewed=draft==null?BankInstructionPublicationPolicy.hash(body.get("snapshotHash")):draft.hash();
            String hash=policy.commandHash(venue,target,action,body),permission=action.equals("PROPOSE")?BankInstructionPublicationPolicy.PROPOSE:BankInstructionPublicationPolicy.PUBLISH;
            return tx.execute(status->{
                permit(actor,venue,permission,false);
                Snapshot discovered=target==null?null:repo.find(venue,target,false).orElseThrow(()->new BankFailure(404,"Proposal unavailable"));
                var actors=new TreeSet<UUID>();actors.add(actor);if(discovered!=null)actors.add(discovered.proposal().proposer());
                for(UUID principal:actors)if(!auth.eligible(principal,true))throw new BankFailure(403,"Eligible independent operators required");
                permit(actor,venue,permission,true);
                Snapshot current=target==null?null:repo.find(venue,target,true).orElseThrow(()->new BankFailure(404,"Proposal unavailable"));
                // Recheck grant expiry after waiting for the immutable proposal lock.
                permit(actor,venue,permission,false);
                if(current!=null && actor.equals(current.proposal().proposer()))throw new BankFailure(403,"Self review/publication denied");
                var prior=repo.receipt(actor,key);
                if(prior.isPresent()) {
                    var event=prior.get();if(!event.payloadHash().equals(hash)||!event.venue().equals(venue))throw new BankFailure(409,"Changed command payload");
                    var original=repo.find(venue,event.proposal(),false).orElseThrow(()->new BankFailure(503,"Original proposal unavailable"));
                    return new Result(200,view(original.proposal(),event,true));
                }
                Proposal proposal;String state;UUID reviewer=null,published=null;long next;var now=repo.now();
                if(draft!=null) {
                    if(target!=null)throw new BankFailure(400,"Initial command cannot target a proposal");
                    proposal=new Proposal(UUID.randomUUID(),venue,actor,draft);state="PROPOSED";next=1;
                } else {
                    if(current==null)throw new BankFailure(400,"Proposal required");proposal=current.proposal();
                    if(current.event().revision()!=expected||!proposal.draft().hash().equals(reviewed))throw new BankFailure(409,"Stale proposal or snapshot");
                    if(action.equals("REVIEW")) {if(!current.event().state().equals("PROPOSED"))throw new BankFailure(409,"Proposal is not pending review");state="REVIEWED";reviewer=actor;next=2;}
                    else {if(!current.event().state().equals("REVIEWED")||!actor.equals(current.event().reviewer()))throw new BankFailure(409,"Publisher must review this exact snapshot");state="PUBLISHED";reviewer=actor;published=UUID.randomUUID();next=3;}
                }
                if(now.isAfter(proposal.draft().from()))throw new BankFailure(409,"Approval must precede the effective interval");
                // Resolve again at the write boundary; never admit mutable/latest/arbitrary bank content.
                new FixedInstructionResolver().resolve(proposal.draft().reference(),proposal.draft().referenceVersion(),proposal.draft().digest());
                Event event=new Event(UUID.randomUUID(),actor,key,venue,proposal.id(),action,hash,expected,next,state,reviewer,published,now,correlation,UUID.randomUUID());
                if(draft!=null)repo.insertProposal(proposal);
                if(published!=null)repo.publish(proposal,event);
                repo.insertEvent(event);try{repo.audit(event);}catch(RuntimeException auditFailure){throw new BankFailure(503,"Publication audit unavailable");}
                return new Result(action.equals("PROPOSE")?201:200,view(proposal,event,false));
            });
        }catch(BankFailure failure){return rejected(actor,venue,action,failure.status,correlation);}
        catch(DataIntegrityViolationException conflict){return rejected(actor,venue,action,409,correlation);}
        catch(RuntimeException unavailable){return rejected(actor,venue,action,503,correlation);}
    }
    Result read(UUID actor,UUID venue,UUID target,UUID correlation) {
        try {policy.configured();if(actor==null)throw new BankFailure(401,"Authentication required");return tx.execute(s->{
            permit(actor,venue,BankInstructionPublicationPolicy.READ,true);var snapshot=repo.find(venue,target,true).orElseThrow(()->new BankFailure(404,"Proposal unavailable"));
            permit(actor,venue,BankInstructionPublicationPolicy.READ,false);repo.readAudit(actor,snapshot,correlation);return new Result(200,view(snapshot.proposal(),snapshot.event(),false));
        });}catch(BankFailure failure){return rejected(actor,venue,"READ",failure.status,correlation);}catch(RuntimeException failure){return rejected(actor,venue,"READ",503,correlation);}
    }
    private Result rejected(UUID actor,UUID venue,String action,int status,UUID correlation) {
        Result configFailure=configFailure();if(configFailure!=null)return configFailure;
        int denialStatus=status;
        try {denial.executeWithoutResult(s->repo.denied(actor,venue,Set.of("PROPOSE","REVIEW","PUBLISH","READ").contains(action)?action:"INVALID",denialStatus==403?"ACCESS_DENIED":denialStatus==409?"CONFLICT":denialStatus==400?"INVALID_REQUEST":"UNAVAILABLE",correlation));}
        catch(RuntimeException failure){status=503;}
        return new Result(status,Map.of("code",status==403?"ACCESS_DENIED":status==409?"CONFLICT":status==400?"INVALID_REQUEST":"UNAVAILABLE"));
    }
    private Result configFailure(){try{policy.configured();return null;}catch(BankFailure e){return new Result(e.status,Map.of("code",e.status==404?"DISABLED":"UNAVAILABLE"));}}
    Result invalid(UUID actor,UUID venue,UUID correlation){return rejected(actor,venue,"INVALID",400,correlation);}
    private Map<String,Object> view(Proposal p,Event e,boolean replay) {
        var content=new FixedInstructionResolver().resolve(p.draft().reference(),p.draft().referenceVersion(),p.draft().digest());var out=new LinkedHashMap<String,Object>();
        out.put("proposalId",p.id());out.put("venueId",p.venue());out.put("instructionVersion",p.draft().version());out.put("revision",e.revision());out.put("state",e.state());out.put("snapshotHash",p.draft().hash());
        out.put("effectiveFrom",p.draft().from());out.put("effectiveUntil",p.draft().until());out.put("mode","STAGING_ONLY");out.put("instruction",content);out.put("independentReviewRecorded",e.reviewer()!=null);out.put("replayed",replay);
        if(e.published()!=null)out.put("instructionRevisionId",e.published());out.put("realPaymentEnabled",false);out.put("realReceiptAdmission","DENY");out.put("synthetic",true);return out;
    }
}

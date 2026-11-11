package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CommercialPolicyDraftService {
    private final ScopedAuthorization authorization;
    private final CommercialPolicyDraftRepository repository;
    private OperationalCommercialPolicyService operationalPolicies;

    @org.springframework.beans.factory.annotation.Autowired
    void configureOperationalPolicies(OperationalCommercialPolicyService policies) {
        this.operationalPolicies = policies;
    }
    CommercialPolicyDraftService(ScopedAuthorization authorization,CommercialPolicyDraftRepository repository){this.authorization=authorization;this.repository=repository;}
    MatchedGrant require(UUID actor,UUID venue,String permission,boolean lock){if(actor==null)throw new CommercialPolicyDraft.Failure(401,"Sign-in required");return authorization.matchedGrant(actor,venue,permission,lock).filter(g->g.account().equals(actor)&&g.venue().equals(venue)&&g.permission().equals(permission)).orElseThrow(()->new CommercialPolicyDraft.Failure(403,"This venue requires "+permission));}
    void pagination(int page,int size){if(page<0||page>10000||size<1||size>100)throw CommercialPolicyDraft.invalid();}
    @Transactional(readOnly=true) public Map<String,Object> list(UUID actor,UUID venue,String query,int page,int size){require(actor,venue,"commercial.policy.read",false);pagination(page,size);CommercialPolicyDraftRules.text(query,100,true);var result=new LinkedHashMap<String,Object>(CommercialPolicyDraft.flags());result.putAll(Map.of("items",repository.list(venue,query,page,size),"page",page,"size",size));return result;}
    @Transactional(readOnly=true) public Map<String,Object> detail(UUID actor,UUID venue,UUID policy){require(actor,venue,"commercial.policy.read",false);var result=repository.detail(venue,policy);var actions=new ArrayList<String>();for(String p:List.of("commercial.policy.edit","commercial.policy.review.finance","commercial.policy.review.legal","commercial.policy.review.operations"))if(authorization.matchedGrant(actor,venue,p,false).isPresent())actions.add(p);if(operationalPolicies!=null&&operationalPolicies.mayActivate(actor,venue))actions.add(OperationalCommercialPolicyService.ACTIVATE);result.put("permittedActions",actions);return result;}
    @Transactional(readOnly=true) public Map<String,Object> history(UUID actor,UUID venue,UUID policy,int page,int size){require(actor,venue,"commercial.policy.read",false);pagination(page,size);return Map.of("items",repository.history(venue,policy,page,size),"page",page,"size",size);}
    @Transactional public Map<String,Object> mutate(UUID actor,UUID venue,UUID policy,String operation,Map<String,Object> payload){
        Set<String> fields=operation.equals("CREATE")?Set.of("idempotencyKey","reason","scopeKind","packageId","document"):operation.equals("REVISE")?Set.of("idempotencyKey","reason","expectedRevision","document"):Set.of("idempotencyKey","reason","expectedRevision","expectedContentHash","domain","outcome","evidenceReference");
        var required=new HashSet<>(fields);required.remove("packageId");CommercialPolicyDraftRules.keys(payload,fields,required);
        String domain=null;if(operation.equals("REVIEW")){domain=CommercialPolicyDraftRules.text(payload.get("domain"),10,false);if(!CommercialPolicyDraft.DOMAINS.contains(domain))throw CommercialPolicyDraft.invalid();CommercialPolicyDraftRules.choice(payload,"outcome","APPROVE","REJECT","REQUEST_CHANGES");CommercialPolicyDraftRules.text(payload.get("evidenceReference"),500,false);if(!(payload.get("expectedContentHash") instanceof String h)||!h.matches("[0-9a-f]{64}"))throw CommercialPolicyDraft.invalid();}
        var grant=require(actor,venue,operation.equals("REVIEW")?"commercial.policy.review."+domain.toLowerCase(Locale.ROOT):"commercial.policy.edit",true);
        UUID key=CommercialPolicyDraftRules.id(payload.get("idempotencyKey"));String reason=CommercialPolicyDraftRules.text(payload.get("reason"),500,false);
        long expected=operation.equals("CREATE")?0:CommercialPolicyDraftRules.number(payload.get("expectedRevision"),Long.MAX_VALUE-1);if(!operation.equals("CREATE")&&expected==0)throw CommercialPolicyDraft.invalid();
        UUID packageId=null;if(operation.equals("CREATE")){CommercialPolicyDraftRules.choice(payload,"scopeKind","VENUE","PACKAGE");if(payload.get("packageId")!=null)packageId=CommercialPolicyDraftRules.id(payload.get("packageId"));if(payload.get("scopeKind").equals("PACKAGE")!=(packageId!=null))throw CommercialPolicyDraft.invalid();}
        Map<String,Object> document=operation.equals("REVIEW")?null:CommercialPolicyDraftRules.document(payload.get("document"));
        var hashInput=new LinkedHashMap<String,Object>();hashInput.put("venue",venue.toString());hashInput.put("policy",policy==null?null:policy.toString());hashInput.put("operation",operation);hashInput.put("payload",payload);String hash=CommercialPolicyDraftRules.hash(hashInput);
        var replay=repository.replay(actor,venue,key);if(replay.isPresent()){var saved=replay.get();if(!hash.equals(saved.get("request_hash")))throw new CommercialPolicyDraft.Failure(409,"Idempotency key already belongs to a different request");return JdbcCommercialPolicyDraftRepository.receipt(saved,true);}
        if(policy==null)policy=UUID.randomUUID();else{var head=repository.head(venue,policy,true);if(((Number)head.get("current_revision")).longValue()!=expected)throw new CommercialPolicyDraft.Failure(409,"Draft changed; reload and compare before submitting");}
        return repository.write(grant,policy,packageId,key,operation,expected,hash,reason,document,operation.equals("REVIEW")?payload:null);
    }
}

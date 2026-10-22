package com.cinenest.identity.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
class ManagedGrantQuery {
    record Result(int status,Object body) {}
    record Page(String kind,UUID venueId,UUID accountId,String targetEligibility,Instant observedAt,int limit,
                boolean hasNext,String nextCursor,String snapshotPolicy,String overlapBasis,List<ManagedGrantRepository.Grant> items) {}
    private final ScopedAuthorization auth;private final ManagedGrantRepository repo;private final IdentityStore security;private final TransactionTemplate tx;
    private final byte[] key=new byte[32];
    ManagedGrantQuery(ScopedAuthorization auth,ManagedGrantRepository repo,IdentityStore security,PlatformTransactionManager manager){this.auth=auth;this.repo=repo;this.security=security;tx=new TransactionTemplate(manager);new SecureRandom().nextBytes(key);}
    private String sign(String value){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private String encode(String context,UUID last,String fingerprint){var body=Base64.getUrlEncoder().withoutPadding().encodeToString((last+";"+fingerprint).getBytes(StandardCharsets.UTF_8));return body+"."+sign(context+body);}
    private String[] decode(String context,String token){
        if(token.length()>1024)throw new IllegalArgumentException();var parts=token.split("\\.",-1);
        if(parts.length!=2||!MessageDigest.isEqual(sign(context+parts[0]).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
        var values=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split(";",-1);if(values.length!=2)throw new IllegalArgumentException();AdminService.uuid(values[0]);return values;
    }
    Result read(UUID actor,UUID venue,UUID target,Map<String,String> input){return tx.execute(status->{
        if(!auth.permits(actor,venue,"identity.grant.read",false)){security.audit(actor,null,"HTTP_DENIAL",false);return new Result(403,null);}
        repo.lock(actor,target,venue);
        if(!repo.member(actor,venue)||!auth.permits(actor,venue,"identity.grant.read",true)){security.audit(actor,null,"HTTP_DENIAL",false);return new Result(403,null);}
        Instant at=repo.now();var account=repo.target(target);
        if(account.isEmpty()||!account.get().synthetic()||!repo.member(target,venue)){repo.audit(actor,venue,null,at,"DENY","NOT_FOUND");return new Result(404,null);}
        try{
            if(!Set.of("limit","cursor").containsAll(input.keySet())||input.values().stream().anyMatch(v->v.length()>1024))throw new IllegalArgumentException();
            String value=input.getOrDefault("limit","20");if(!value.matches("[1-9][0-9]{0,2}"))throw new IllegalArgumentException();int limit=Integer.parseInt(value);if(limit>100)throw new IllegalArgumentException();
            String context=actor+"/"+venue+"/"+target+"/"+limit+"/",fingerprint=repo.fingerprint(target,venue,at,account.get().eligibility());UUID after=null;
            if(input.containsKey("cursor")){var cursor=decode(context,input.get("cursor"));if(!cursor[1].equals(fingerprint)){repo.audit(actor,venue,target,at,"DENY","STALE_CONTEXT");return new Result(409,null);}after=AdminService.uuid(cursor[0]);}
            var rows=repo.rows(target,venue,at,account.get().eligibility().equals("ELIGIBLE"),after,limit+1);boolean hasNext=rows.size()>limit;
            var items=hasNext?List.copyOf(rows.subList(0,limit)):List.copyOf(rows);String next=hasNext?encode(context,items.getLast().grantId(),fingerprint):null;
            // A deadline may pass while reading: recheck caller authority immediately before mandatory audit/disclosure.
            if(!auth.permits(actor,venue,"identity.grant.read",true)){security.audit(actor,null,"HTTP_DENIAL",false);return new Result(403,null);}
            repo.audit(actor,venue,target,at,"ALLOW","SCOPED_READ");
            return new Result(200,new Page("MANAGED_GRANT_SUBSET",venue,target,account.get().eligibility(),at,limit,hasNext,next,"VERSION_BOUND_CURRENT_REQUEST","ACTIVE_STORED_INTERVALS_HALF_OPEN",items));
        }catch(IllegalArgumentException invalid){repo.audit(actor,venue,target,at,"DENY","INVALID_QUERY");return new Result(400,null);}
    });}
}

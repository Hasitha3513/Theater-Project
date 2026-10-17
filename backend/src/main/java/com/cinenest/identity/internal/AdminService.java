package com.cinenest.identity.internal;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.identity.ScopedAuditSource;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
class AdminService {
    record Result(int status,Object body){}
    record Page(List<?> items,String nextCursor,boolean synthetic){}
    private final ScopedAuthorization auth;private final AdminRepository repo;private final List<ScopedAuditSource> sources;
    private final byte[] cursorKey=new byte[32];
    AdminService(ScopedAuthorization auth,AdminRepository repo,List<ScopedAuditSource> sources){this.auth=auth;this.repo=repo;this.sources=sources;new java.security.SecureRandom().nextBytes(cursorKey);}
    static UUID uuid(String s){UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException();return id;}
    static String optional(Map<String,String> p,String k){String s=p.get(k);return s==null||s.isBlank()?null:s;}
    private String sign(String value){try{var m=javax.crypto.Mac.getInstance("HmacSHA256");m.init(new javax.crypto.spec.SecretKeySpec(cursorKey,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String encode(String context,String value){String body=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));return body+"."+sign(context+body);}
    private String decode(String context,String token){if(token.length()>1024)throw new IllegalArgumentException();var parts=token.split("\\.",-1);if(parts.length!=2||!java.security.MessageDigest.isEqual(sign(context+parts[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8),parts[1].getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new IllegalArgumentException();return new String(Base64.getUrlDecoder().decode(parts[0]),java.nio.charset.StandardCharsets.UTF_8);}
    @Transactional
    public Result read(UUID actor,UUID venue,String kind,UUID detail,Map<String,String> input){
        Map<String,String> p=new TreeMap<>(input);boolean accounts=kind.equals("accounts");String action=accounts?(detail==null?"ACCOUNTS_LIST":"ACCOUNT_DETAIL"):(detail==null?"AUDIT_LIST":"AUDIT_DETAIL");
        // Lock account and applicable grants before fresh evaluation; no cached positive authority.
        String source=accounts?"IDENTITY":p.getOrDefault("source","");
        String permission=accounts?"account.read":source.equals("IDENTITY")?"audit.account.read":source.equals("VERIFICATION")?"audit.verification.read":null;
        if(permission==null)return new Result(400,null);
        if(!auth.permits(actor,venue,permission,true)) {repo.denied(actor);return new Result(403,null);}
        try{
            Set<String> allowed=accounts?Set.of("limit","cursor","accountId","eligibility"):Set.of("limit","cursor","source","from","to","action","outcome");
            if(!allowed.containsAll(p.keySet())||p.values().stream().anyMatch(v->v.length()>1024)||detail!=null&&p.containsKey("cursor"))throw new IllegalArgumentException();
            int limit=Integer.parseInt(p.getOrDefault("limit","20"));if(limit<1||limit>100)throw new IllegalArgumentException();
            String token=optional(p,"cursor");p.remove("cursor");String context=actor+"/"+venue+"/"+kind+"/"+p;
            List<?> rows;String next=null;
            if(accounts){
                String eligibility=optional(p,"eligibility");if(eligibility!=null&&!Set.of("ELIGIBLE","SUSPENDED","INELIGIBLE").contains(eligibility))throw new IllegalArgumentException();
                UUID target=detail!=null?detail:optional(p,"accountId")==null?null:uuid(p.get("accountId"));
                var data=repo.accounts(venue,target,eligibility,token==null?null:uuid(decode(context,token)),detail==null?limit+1:1);
                rows=data;if(detail==null&&data.size()>limit){rows=data.subList(0,limit);next=encode(context,data.get(limit-1).accountId().toString());}
            }else{
                Instant from=Instant.parse(p.get("from")),to=Instant.parse(p.get("to"));if(!from.isBefore(to))throw new IllegalArgumentException();
                String filter=optional(p,"action"),outcome=optional(p,"outcome");
                if(filter!=null&&!(source.equals("IDENTITY")?Set.of("ACCOUNTS_LIST","ACCOUNT_DETAIL","AUDIT_LIST","AUDIT_DETAIL"):Set.of("VERIFICATION_EVENT")).contains(filter)||outcome!=null&&!Set.of("ALLOW","DENY").contains(outcome))throw new IllegalArgumentException();
                String[] cursor=token==null?null:decode(context,token).split(";",-1);if(cursor!=null&&cursor.length!=2)throw new IllegalArgumentException();
                var query=new ScopedAuditSource.Query(actor,venue,from,to,cursor==null?null:Instant.parse(cursor[0]),cursor==null?null:uuid(cursor[1]),detail,filter,outcome,detail==null?limit+1:1);
                List<ScopedAuditSource.Event> data=source.equals("IDENTITY")?repo.audit(query):sources.stream().filter(s->s.source().equals(source)).findFirst().orElseThrow().read(query);
                rows=data;if(detail==null&&data.size()>limit){rows=data.subList(0,limit);var last=data.get(limit-1);next=encode(context,last.occurredAt()+";"+last.eventId());}
            }
            boolean found=detail==null||!rows.isEmpty();repo.record(actor,venue,found?detail:null,action,found?"ALLOW":"DENY",found?"SCOPED_READ":"NOT_FOUND");
            return new Result(found?200:404,found?(detail==null?new Page(rows,next,true):rows.getFirst()):null);
        }catch(IllegalArgumentException|NullPointerException|java.time.DateTimeException invalid){repo.record(actor,venue,null,action,"DENY","ACCESS_DENIED");return new Result(400,null);}
    }
}

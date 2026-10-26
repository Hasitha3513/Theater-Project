package com.cinenest.iot.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.*;

@Service
final class LockRegistryService {
    record Result(int status,Object body){}
    private final LockRegistryPolicy policy;
    private final LockRegistryRepository repo;
    private final LockRegistryReferenceResolver resolver;
    private final ScopedAuthorization auth;
    private final TransactionTemplate tx;
    LockRegistryService(LockRegistryPolicy p,LockRegistryRepository r,LockRegistryReferenceResolver x,ScopedAuthorization a,PlatformTransactionManager manager){policy=p;repo=r;resolver=x;auth=a;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    private void permit(UUID actor,UUID venue,String permission){if(actor==null)throw new LockRegistryPolicy.Failure(401);
        if(!auth.eligible(actor,true)||!auth.member(actor,venue,true)||!auth.permits(actor,venue,permission,true))throw new LockRegistryPolicy.Failure(403);}
    Result run(UUID actor,String rawVenue,String rawDevice,String rawRevision,String rawPage,boolean invalidQuery,byte[] body,String operation){
        UUID correlation=UUID.randomUUID();
        try{
            policy.configured();UUID venue=LockRegistryPolicy.uuid(rawVenue);
            return tx.execute(t->{permit(actor,venue,operation.equals("REGISTER")?"iot.lock.registry.register":"iot.lock.registry.read");
                if(invalidQuery)throw new LockRegistryPolicy.Failure(400);
                UUID device=rawDevice==null?null:LockRegistryPolicy.uuid(rawDevice);
                if(operation.equals("REGISTER")){
                    var r=policy.parse(venue,body);var prior=repo.receipt(actor,r.key());
                    if(prior.isPresent()){
                        var c=prior.get();if(!venue.equals(c.get("venue_id"))||!MessageDigest.isEqual(r.hash(),(byte[])c.get("request_sha256")))throw new LockRegistryPolicy.Failure(409);
                        repo.audit(actor,venue,(UUID)c.get("device_id"),1L,"REPLAY",null,correlation,repo.now());return new Result(200,registered(c,true));
                    }
                    var identity=resolver.resolve(venue,r.reference());resolver.gateway(venue,r.gateway());resolver.declaration(venue,r.sourceReference());
                    return new Result(201,registered(repo.register(actor,venue,r,identity,correlation),false));
                }
                if(operation.equals("LIST")||operation.equals("REVISIONS")){
                    int page=0;try{if(rawPage!=null){if(!rawPage.matches("0|[1-9][0-9]{0,4}"))throw new IllegalArgumentException();page=Integer.parseInt(rawPage);if(page>10000)throw new IllegalArgumentException();}}catch(RuntimeException e){throw new LockRegistryPolicy.Failure(400);}
                    if(device!=null&&repo.detail(venue,device,null).isEmpty())throw new LockRegistryPolicy.Failure(404);
                    var rows=repo.page(venue,device,page);var at=repo.now();repo.audit(actor,venue,device,null,device==null?"LIST_READ":"REVISION_LIST",null,correlation,at);
                    return new Result(200,Map.of("items",rows.stream().limit(25).map(this::view).toList(),"page",page,"size",25,"hasNext",rows.size()>25,"observedAt",at.toString(),"mode","QUALIFICATION_ONLY"));
                }
                Long revision=null;if(rawRevision!=null){try{if(!rawRevision.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();revision=Long.parseLong(rawRevision);}catch(RuntimeException e){throw new LockRegistryPolicy.Failure(400);}}
                var row=repo.detail(venue,device,revision).orElseThrow(()->new LockRegistryPolicy.Failure(404));var at=repo.now();
                repo.audit(actor,venue,device,((Number)row.get("revision")).longValue(),revision==null?"DETAIL_READ":"REVISION_READ",null,correlation,at);
                var result=view(row);result.put("observedAt",at.toString());return new Result(200,result);
            });
        }catch(LockRegistryPolicy.Failure f){return denied(actor,correlation,f.status);}
        catch(DataIntegrityViolationException conflict){return denied(actor,correlation,409);}
        catch(RuntimeException failure){return denied(actor,correlation,503);}
    }
    private Result denied(UUID actor,UUID correlation,int status){int initialStatus=status;try{tx.executeWithoutResult(t->repo.audit(actor,null,null,null,"DENIED",switch(initialStatus){case 400->"INVALID_REQUEST";case 401,403->"ACCESS_DENIED";case 404->"NOT_FOUND";case 409->"CONFLICT";default->"UNAVAILABLE";},correlation,repo.now()));}
        catch(RuntimeException failure){status=503;}
        return new Result(status,Map.of("message","Lock registry unavailable, changed or denied.","mode","QUALIFICATION_ONLY","customerAccessEnabled",false));
    }
    private static java.time.Instant time(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():((java.time.OffsetDateTime)value).toInstant();}
    private Map<String,Object> registered(Map<String,Object> c,boolean replay){return Map.of("deviceId",c.get("device_id"),"venueId",c.get("venue_id"),"registeredMetadataRevision",1,"registeredAt",time(c.get("registered_at")).toString(),"registrationStatus","REGISTERED","activationPerformed",false,"replayed",replay);}
    private Map<String,Object> view(Map<String,Object> row){
        var m=new LinkedHashMap<String,Object>();for(var e:Map.of("deviceId","device_id","venueId","venue_id","provider","provider","revision","revision","manufacturer","manufacturer","model","model","sku","sku","hardwareRevision","hardware_revision","firmwareRevision","firmware_revision").entrySet())m.put(e.getKey(),row.get(e.getValue()));
        try{m.put("declaredCapabilities",JSONObjectUtils.parse("{\"v\":"+row.get("declared_capabilities")+"}").get("v"));}catch(Exception e){throw new IllegalStateException("Registry data unavailable");}
        m.put("declarationSource",row.get("declaration_source"));m.put("registrationStatus","REGISTERED");m.put("qualification","UNQUALIFIED");m.put("physicalAcceptance","NOT_VERIFIED");m.put("providerOwnership","UNVERIFIED");m.put("customerAccessEnabled",false);m.put("roomId",null);m.put("assignmentId",null);m.put("health","UNKNOWN");m.put("lastDeviceObservationAt",null);m.put("mode","QUALIFICATION_ONLY");m.put("registeredAt",time(row.get("registered_at")).toString());m.put("recordedAt",time(row.get("recorded_at")).toString());return m;
    }
}

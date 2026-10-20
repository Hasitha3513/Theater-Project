package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;
import static com.cinenest.booking.internal.StaffBookingReadRules.*;

@Service
class StaffBookingReadService {
 record Page(UUID venueId,List<StaffBookingReadStore.Item> items,int page,int size,boolean hasNext,Instant observedAt,boolean synthetic,boolean realBooking){}
 record Detail(StaffBookingReadStore.Item booking,Instant observedAt,boolean synthetic,boolean realBooking){}
 private final StaffBookingReadStore store;private final ScopedAuthorization auth;private final StaffBookingReadAudit audit;private final Environment env;private final TransactionTemplate tx;
 StaffBookingReadService(StaffBookingReadStore store,ScopedAuthorization auth,StaffBookingReadAudit audit,Environment env,PlatformTransactionManager manager){this.store=store;this.auth=auth;this.audit=audit;this.env=env;tx=new TransactionTemplate(manager);}
 Object read(UUID actor,String rawVenue,String rawBooking,MultiValueMap<String,String> params,UUID correlation){
  UUID venue=null,booking=null;String action=rawBooking==null?"LIST":"DETAIL";
  try{
   venue=id(rawVenue);booking=rawBooking==null?null:id(rawBooking);final UUID v=venue,b=booking;
   return tx.execute(status->{
    if(!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new Failure(503,"NOT_CONFIGURED");
    if(actor==null)throw new Failure(401,"SESSION_REQUIRED");
    if(!auth.permits(actor,v,"booking.synthetic.read",true))throw new Failure(403,"ACCESS_DENIED");
    var query=query(v,params,b!=null);
    Object result;String reason;UUID target=b;
    if(b==null){var rows=store.list(query);result=new Page(v,rows.stream().limit(query.size()).toList(),query.page(),query.size(),rows.size()>query.size(),store.now(),true,false);reason=rows.isEmpty()?"EMPTY":"SCOPED_READ";target=query.reference();}
    else{var item=store.detail(v,b).orElseThrow(()->new Failure(404,"NOT_FOUND"));result=new Detail(item,store.now(),true,false);reason="SCOPED_READ";}
    store.audit(actor,v,target,action,"ALLOW",reason,correlation);return result;
   });
  }catch(RuntimeException e){
   Failure failure=e instanceof Failure f?f:new Failure(503,"UNAVAILABLE");
   try{audit.refused(actor,venue,booking,action,failure.reason,correlation);}catch(RuntimeException auditUnavailable){throw new Failure(503,"UNAVAILABLE");}
   throw failure;
  }
 }
}

package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.util.*;

interface CommercialPolicyDraftRepository {
    Optional<Map<String,Object>> replay(UUID actor,UUID venue,UUID key);
    Map<String,Object> head(UUID venue,UUID policy,boolean lock);
    List<Map<String,Object>> list(UUID venue,String query,int page,int size);
    Map<String,Object> detail(UUID venue,UUID policy);
    List<Map<String,Object>> history(UUID venue,UUID policy,int page,int size);
    Map<String,Object> write(MatchedGrant grant,UUID policy,UUID packageId,UUID key,String operation,long expected,String requestHash,String reason,Map<String,Object> document,Map<String,Object> review);
}

package com.cinenest.iot.internal;

import java.time.Instant;
import java.util.*;

/** IoT-owned registry persistence. Caller controls transaction and authorization. */
interface LockRegistryRepository {
    Instant now();
    Optional<Map<String,Object>> receipt(UUID actor,UUID key);
    Map<String,Object> register(UUID actor,UUID venue,LockRegistryPolicy.Registration request,
        LockRegistryReferenceResolver.Identity identity,UUID correlation);
    Optional<Map<String,Object>> detail(UUID venue,UUID device,Long revision);
    List<Map<String,Object>> page(UUID venue,UUID device,int page);
    void audit(UUID actor,UUID venue,UUID device,Long revision,String kind,String denial,UUID correlation,Instant at);
}

package com.cinenest.identity.internal;

import java.security.Principal;import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cinenest.identity.grant-read.enabled",havingValue="true")
class ManagedGrantController {
    private final ManagedGrantQuery query;
    ManagedGrantController(ManagedGrantQuery query){this.query=query;}
    @GetMapping("/api/admin/venues/{venueId}/accounts/{accountId}/grants")
    ResponseEntity<?> read(Principal p,@PathVariable String venueId,@PathVariable String accountId,@RequestParam org.springframework.util.MultiValueMap<String,String> params){
        if(p==null)return response(401,null);
        if(params.values().stream().anyMatch(v->v.size()!=1))return response(400,null);
        try{var result=query.read(UUID.fromString(p.getName()),AdminService.uuid(venueId),AdminService.uuid(accountId),params.toSingleValueMap());return response(result.status(),result.body());}
        catch(IllegalArgumentException invalid){return response(404,null);}
        catch(org.springframework.dao.DataAccessException unavailable){return response(503,null);}
    }
    private static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","no-store").body(body);}
}

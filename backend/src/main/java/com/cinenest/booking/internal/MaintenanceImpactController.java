package com.cinenest.booking.internal;
import java.security.Principal;import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@RestController
class MaintenanceImpactController {
 private final MaintenanceImpactQuery query;MaintenanceImpactController(MaintenanceImpactQuery query){this.query=query;}
 @GetMapping("/api/admin/venues/{venueId}/rooms/{roomId}/maintenance-impact")
 ResponseEntity<?> read(Principal principal,@PathVariable String venueId,@PathVariable String roomId,@RequestParam MultiValueMap<String,String> params){
  UUID correlation=UUID.randomUUID();if(principal==null)return response(401,null,correlation);
  var result=query.read(UUID.fromString(principal.getName()),venueId,roomId,params,correlation);return response(result.status(),result.body(),correlation);
 }
 private ResponseEntity<?> response(int status,Object body,UUID correlation){return ResponseEntity.status(status).header("Cache-Control","no-store").header("X-Correlation-ID",correlation.toString()).body(body);}
}

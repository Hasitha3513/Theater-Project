package com.cinenest.payments.internal;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
final class BankInstructionPublicationController {
    private final BankInstructionPublicationService service;private final BankInstructionPublicationPolicy policy;
    BankInstructionPublicationController(BankInstructionPublicationService service,BankInstructionPublicationPolicy policy){this.service=service;this.policy=policy;}
    @PostMapping("/api/admin/venues/{venueId}/bank/instruction-proposals")
    ResponseEntity<?> propose(Principal p,@PathVariable String venueId,HttpServletRequest r){return command(p,venueId,null,"PROPOSE",null,r);}
    @PostMapping("/api/admin/venues/{venueId}/bank/instruction-proposals/{id}/review")
    ResponseEntity<?> review(Principal p,@PathVariable String venueId,@PathVariable String id,HttpServletRequest r){return command(p,venueId,id,"REVIEW",null,r);}
    @PostMapping("/api/admin/venues/{venueId}/bank/instruction-proposals/{id}/publish")
    ResponseEntity<?> publish(Principal p,@PathVariable String venueId,@PathVariable String id,HttpServletRequest r){return command(p,venueId,id,"PUBLISH",null,r);}
    @GetMapping("/api/admin/venues/{venueId}/bank/instruction-proposals/{id}")
    ResponseEntity<?> read(Principal p,@PathVariable String venueId,@PathVariable String id,HttpServletRequest r){return command(p,venueId,id,"READ",null,r);}
    private ResponseEntity<?> command(Principal principal,String rawVenue,String target,String action,byte[] bytes,HttpServletRequest request){
        UUID actor=null,venue=null,correlation=UUID.randomUUID();BankInstructionPublicationService.Result result;
        try {if(principal==null)return response(new BankInstructionPublicationService.Result(401,Map.of("code","AUTHENTICATION_REQUIRED")),correlation);
            actor=BankInstructions.canonical(principal.getName());venue=BankInstructions.canonical(rawVenue);policy.configured();
            if(request.getQueryString()!=null || (action.equals("READ")&&(request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null)))throw new BankFailure(400,"Invalid request");
            if(!action.equals("READ")&&(request.getContentType()==null||!request.getContentType().split(";")[0].trim().equalsIgnoreCase("application/json")))throw new BankFailure(400,"JSON command required");
            UUID id=target==null?null:BankInstructions.canonical(target);
            result=action.equals("READ")?service.read(actor,venue,id,correlation):service.command(actor,venue,id,action,policy.parse(request.getInputStream().readNBytes(16385)),correlation);
        }catch(Exception failure){result=service.invalid(actor,venue,correlation);}
        return response(result,correlation);
    }
    private ResponseEntity<?> response(BankInstructionPublicationService.Result result,UUID correlation){return ResponseEntity.status(result.status()).header("Cache-Control","private, no-store").header("X-Correlation-ID",correlation.toString()).body(result.body());}
}

package com.cinenest.payments.internal;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
class BankInstructionController {
 final BankInstructions service;BankInstructionController(BankInstructions service){this.service=service;}
 @GetMapping("/api/admin/venues/{venueId}/bank/instructions/{id}")
 ResponseEntity<?> detail(Principal p,@PathVariable String venueId,@PathVariable String id,HttpServletRequest request){return read(p,venueId,id,false,request);}
 @GetMapping("/api/admin/venues/{venueId}/bank/requests/{id}/instructions")
 ResponseEntity<?> bound(Principal p,@PathVariable String venueId,@PathVariable String id,HttpServletRequest request){return read(p,venueId,id,true,request);}
 private ResponseEntity<?> read(Principal p,String venue,String target,boolean bound,HttpServletRequest request){
  UUID correlation=UUID.randomUUID();var result=p==null?new BankInstructions.Result(401,null):service.read(BankService.id(p.getName()),venue,target,bound,request.getQueryString()!=null||request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null,correlation);
  return ResponseEntity.status(result.status()).header("Cache-Control","private, no-store").header("X-Correlation-ID",correlation.toString()).body(result.body());
 }
}

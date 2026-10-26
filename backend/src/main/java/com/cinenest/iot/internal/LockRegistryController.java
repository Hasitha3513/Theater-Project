package com.cinenest.iot.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cinenest.iot.lock-registry.enabled",havingValue="true")
@RequestMapping("/api/admin/venues/{venue}/iot/lock-devices")
final class LockRegistryController {
    private final LockRegistryService service;
    LockRegistryController(LockRegistryService service){this.service=service;}
    private ResponseEntity<?> call(Principal principal,String venue,String device,String revision,HttpServletRequest request,String operation,byte[] body,boolean invalid){
        UUID actor=null;try{actor=UUID.fromString(principal.getName());}catch(RuntimeException ignored){}
        var params=request.getParameterMap();boolean paged=operation.equals("LIST")||operation.equals("REVISIONS");
        invalid=invalid||params.size()>(paged?1:0)||params.keySet().stream().anyMatch(k->!paged||!k.equals("page"))||params.values().stream().anyMatch(v->v.length!=1);
        var result=service.run(actor,venue,device,revision,request.getParameter("page"),invalid,body,operation);
        return ResponseEntity.status(result.status()).header("Cache-Control","no-store").body(result.body());
    }
    @PostMapping ResponseEntity<?> register(Principal p,@PathVariable String venue,HttpServletRequest req){
        byte[] bytes;boolean invalid=false;
        try{bytes=req.getInputStream().readNBytes(16385);String type=req.getContentType();invalid=type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json");}catch(java.io.IOException e){bytes=new byte[0];invalid=true;}
        return call(p,venue,null,null,req,"REGISTER",bytes,invalid);
    }
    @GetMapping ResponseEntity<?> list(Principal p,@PathVariable String venue,HttpServletRequest req){return call(p,venue,null,null,req,"LIST",null,false);}
    @GetMapping("/{device}") ResponseEntity<?> detail(Principal p,@PathVariable String venue,@PathVariable String device,HttpServletRequest req){return call(p,venue,device,null,req,"DETAIL",null,false);}
    @GetMapping("/{device}/revisions") ResponseEntity<?> revisions(Principal p,@PathVariable String venue,@PathVariable String device,HttpServletRequest req){return call(p,venue,device,null,req,"REVISIONS",null,false);}
    @GetMapping("/{device}/revisions/{revision}") ResponseEntity<?> revision(Principal p,@PathVariable String venue,@PathVariable String device,@PathVariable String revision,HttpServletRequest req){return call(p,venue,device,revision,req,"REVISION",null,false);}
}

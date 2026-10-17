package com.cinenest.identity.internal;
import java.security.Principal;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="cinenest.identity.admin-read.enabled",havingValue="true")
class AdminController {
    private final AdminService service;
    AdminController(AdminService service){this.service=service;}
    @GetMapping({"/api/admin/venues/{venue}/accounts","/api/admin/venues/{venue}/audit"})
    ResponseEntity<?> list(Principal principal,@PathVariable String venue,@RequestParam org.springframework.util.MultiValueMap<String,String> params,jakarta.servlet.http.HttpServletRequest request){return run(principal,venue,null,params,request);}
    @GetMapping({"/api/admin/venues/{venue}/accounts/{id}","/api/admin/venues/{venue}/audit/{id}"})
    ResponseEntity<?> detail(Principal principal,@PathVariable String venue,@PathVariable String id,@RequestParam org.springframework.util.MultiValueMap<String,String> params,jakarta.servlet.http.HttpServletRequest request){return run(principal,venue,id,params,request);}
    private ResponseEntity<?> run(Principal principal,String venue,String id,org.springframework.util.MultiValueMap<String,String> params,jakarta.servlet.http.HttpServletRequest request){
        try{
            if(params.values().stream().anyMatch(v->v.size()!=1))return response(400,null);
            var result=service.read(UUID.fromString(principal.getName()),AdminService.uuid(venue),request.getRequestURI().contains("/accounts")?"accounts":"audit",id==null?null:AdminService.uuid(id),params.toSingleValueMap());return response(result.status(),result.body());
        }catch(IllegalArgumentException invalid){return response(404,null);}catch(org.springframework.dao.DataAccessException unavailable){return response(503,null);}
    }
    private ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","no-store").body(body);}
}

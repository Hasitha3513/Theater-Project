package com.cinenest.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import com.nimbusds.jose.util.JSONObjectUtils;

@RestController
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.manual.enabled",havingValue="true")
class LocalCustomerController {
    final LocalCustomerIdentity identity;
    LocalCustomerController(LocalCustomerIdentity identity) { this.identity=identity; }
    @GetMapping("/api/auth/csrf") ResponseEntity<?> csrf(HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()) return response(400);
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("token",token.getToken(),"headerName",token.getHeaderName()));
    }
    @PostMapping({"/api/auth/registrations","/api/auth/email-verifications","/api/auth/login"})
    ResponseEntity<?> command(HttpServletRequest request,HttpServletResponse response) {
        boolean login=request.getRequestURI().equals("/api/auth/login");
        String action=login?"LOGIN":request.getRequestURI().endsWith("email-verifications")?"VERIFY":"REGISTER";
        if(login) {SecurityContextHolder.clearContext();var previous=request.getSession(false);if(previous!=null)previous.invalidate();}
        try {
            if(!request.getParameterMap().isEmpty() || request.getContentType()==null || !request.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json")) return refusal(400,action);
            byte[] body=request.getInputStream().readNBytes(2049);
            if(body.length>2048) return refusal(400,action);
            try(var parser=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(body)) {
                if(parser.nextToken()!=JsonToken.START_OBJECT) return refusal(400,action);
                parser.skipChildren();if(parser.nextToken()!=null) return refusal(400,action);
            } catch(Exception invalid) { return refusal(400,action); }
            Map<String,Object> value;
            try { value=JSONObjectUtils.parse(new String(body,StandardCharsets.UTF_8)); } catch(Exception invalid) { return refusal(400,action); }
            if(request.getRequestURI().equals("/api/auth/email-verifications")) {
                if(!value.keySet().equals(Set.of("token")) || !(value.get("token") instanceof String token)) return refusal(400,action);
                return response(identity.verify(token));
            }
            if(!value.keySet().equals(Set.of("email","password")) || !(value.get("email") instanceof String email) || !(value.get("password") instanceof String password)) return refusal(400,action);
            if(!login) return response(identity.register(email,password));
            var result=identity.login(email,password);
            if(result.status()!=204) return response(result.status());
            var session=request.getSession(true);request.changeSessionId();
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(result.account().toString(),null,List.of()));
            SecurityContextHolder.setContext(context);
            new HttpSessionSecurityContextRepository().saveContext(context,request,response);
            session.removeAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
            return response(204);
        } catch(IllegalArgumentException failure) { return refusal(login?401:400,action); }
        catch(LocalCustomerIdentity.Busy failure) { return refusal(503,action); }
        catch(Exception failure) {
            if(login) { SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null)session.invalidate(); }
            return response(503);
        }
    }
    private ResponseEntity<Void> refusal(int status,String action) {
        try {identity.tx.executeWithoutResult(tx -> identity.audit(action,null,false));return response(status);}
        catch(RuntimeException unavailable){return response(503);}
    }
    private static ResponseEntity<Void> response(int status) {return ResponseEntity.status(status).header("Cache-Control","no-store").build();}
}

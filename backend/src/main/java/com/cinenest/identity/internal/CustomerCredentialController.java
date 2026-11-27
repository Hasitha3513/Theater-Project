package com.cinenest.identity.internal;

import jakarta.servlet.http.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import com.nimbusds.jose.util.JSONObjectUtils;

@RestController
@ConditionalOnProperty(name="cinenest.identity.customer.enabled",havingValue="true")
final class CustomerCredentialController {
    final CustomerCredentialService service;
    CustomerCredentialController(CustomerCredentialService service){this.service=service;}
    @GetMapping("/api/auth/csrf") ResponseEntity<?> csrf(HttpServletRequest request){
        if(!request.getParameterMap().isEmpty())return response(400);
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("token",token.getToken(),"headerName",token.getHeaderName(),"mode","CUSTOMER_CREDENTIAL","privacyRevision",service.settings.privacy(),"policyRevision",service.settings.policy(),"deliveryMode",service.settings.qualificationOnly()?"QUALIFICATION_ONLY":"APPROVED"));
    }
    @PostMapping({"/api/auth/registrations","/api/auth/email-verifications","/api/auth/login","/api/auth/recovery-requests","/api/auth/recoveries"})
    ResponseEntity<?> command(HttpServletRequest request,HttpServletResponse response){
        boolean login=request.getRequestURI().equals("/api/auth/login");if(login)clear(request);
        try{
            Map<String,Object> body=parse(request);UUID command=UUID.fromString(text(body,"commandId"));if(!command.toString().equals(text(body,"commandId")))throw new IllegalArgumentException();String subject=request.getRemoteAddr();
            CustomerCredentialService.Result result=switch(request.getRequestURI()){
                case "/api/auth/registrations"->{keys(body,"commandId","email","password","privacyRevision");yield service.register(command,text(body,"email"),text(body,"password"),text(body,"privacyRevision"),subject);}
                case "/api/auth/email-verifications"->{keys(body,"commandId","token","password");yield service.verify(command,text(body,"token"),text(body,"password"),subject);}
                case "/api/auth/login"->{keys(body,"commandId","email","password");yield service.login(command,text(body,"email"),text(body,"password"),subject);}
                case "/api/auth/recovery-requests"->{keys(body,"commandId","email");yield service.requestRecovery(command,text(body,"email"),subject);}
                case "/api/auth/recoveries"->{keys(body,"commandId","token","newPassword");yield service.recover(command,text(body,"token"),text(body,"newPassword"),subject);}
                default->throw new IllegalArgumentException();
            };
            if(login&&result.status()==204){var session=request.getSession(true);request.changeSessionId();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(CredentialEpochSessions.customerAuthentication(result.account(),result.epoch()));SecurityContextHolder.setContext(context);CredentialEpochSessions.contexts().saveContext(context,request,response);session.removeAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");}
            return response(result.status());
        }catch(CustomerCredentialService.Refused denied){if(login)clear(request);return response(denied.status);}
        catch(IllegalArgumentException invalid){if(login)clear(request);return response(400);}
        catch(Exception unavailable){if(login)clear(request);return response(503);}
    }
    static Map<String,Object> parse(HttpServletRequest request)throws Exception{
        if(!request.getParameterMap().isEmpty()||request.getContentType()==null||!request.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new IllegalArgumentException();byte[] bytes=request.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new IllegalArgumentException();String raw;try{raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException invalid){throw new IllegalArgumentException();}
        try(var parser=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(raw)){if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException();parser.skipChildren();if(parser.nextToken()!=null)throw new IllegalArgumentException();}catch(Exception invalid){throw new IllegalArgumentException();}
        try{return JSONObjectUtils.parse(raw);}catch(Exception invalid){throw new IllegalArgumentException();}
    }
    static void keys(Map<String,Object> body,String...keys){if(!body.keySet().equals(Set.of(keys)))throw new IllegalArgumentException();}
    static String text(Map<String,Object> body,String key){if(!(body.get(key) instanceof String value))throw new IllegalArgumentException();return value;}
    static void clear(HttpServletRequest request){SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null)session.invalidate();}
    static ResponseEntity<Void> response(int status){return ResponseEntity.status(status).header("Cache-Control","no-store").header("Pragma","no-cache").build();}
}

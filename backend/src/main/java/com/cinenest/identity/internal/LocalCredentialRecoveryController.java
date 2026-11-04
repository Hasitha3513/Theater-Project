package com.cinenest.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import com.nimbusds.jose.util.JSONObjectUtils;

@RestController
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.manual.recovery.enabled",havingValue="true")
class LocalCredentialRecoveryController {
    final LocalCredentialRecoveryService service;
    LocalCredentialRecoveryController(LocalCredentialRecoveryService service){this.service=service;}
    @PostMapping("/api/admin/venues/{venue}/accounts/{account}/credential-recovery/{operation:capabilities|commands}")
    ResponseEntity<?> command(@PathVariable UUID venue,@PathVariable UUID account,@PathVariable String operation,Authentication authentication,HttpServletRequest request) {
        try {
            UUID actor=CredentialEpochSessions.binding(authentication).account();
            if(!request.getParameterMap().isEmpty()||request.getContentType()==null||!request.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new IllegalArgumentException();
            byte[] raw=request.getInputStream().readNBytes(4097);if(raw.length>4096)throw new IllegalArgumentException();
            try(var parser=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(raw)) {
                if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException();parser.skipChildren();if(parser.nextToken()!=null)throw new IllegalArgumentException();
            }catch(Exception invalid){throw new IllegalArgumentException();}
            Map<String,Object> body;
            try{body=JSONObjectUtils.parse(new String(raw,StandardCharsets.UTF_8));}catch(Exception invalid){throw new IllegalArgumentException();}
            if(operation.equals("capabilities")) {
                if(!body.keySet().equals(Set.of("commandId","issuerGrantId","expectedIssuerGrantRevision","expectedAccountRevision","expectedCredentialRevision","lifetimeSeconds","approvalReference")))throw new IllegalArgumentException();
                var issued=service.issue(actor,account,venue,new LocalCredentialRecoveryService.Issuance(id(body,"commandId"),id(body,"issuerGrantId"),number(body,"expectedIssuerGrantRevision"),number(body,"expectedAccountRevision"),number(body,"expectedCredentialRevision"),Math.toIntExact(number(body,"lifetimeSeconds")),text(body,"approvalReference")));
                return response(201,Map.of("capabilityId",issued.capability(),"token",issued.token(),"expiresAt",issued.expiresAt().toString()));
            }
            if(!body.keySet().equals(Set.of("commandId","capabilityId","expectedAccountRevision","expectedCredentialRevision","token","newPassword")))throw new IllegalArgumentException();
            var result=service.consume(actor,account,venue,id(body,"capabilityId"),new LocalCredentialRecovery.Command(id(body,"commandId"),account,actor,venue,number(body,"expectedAccountRevision"),number(body,"expectedCredentialRevision"),text(body,"token"),text(body,"newPassword")));
            return response(200,Map.of("commandId",result.command(),"credentialRevision",result.credentialRevision(),"auditId",result.audit()));
        }catch(LocalCredentialRecovery.Denied denied){int status=switch(denied.reason){case DISABLED->404;case AUTHORITY,ACCOUNT->403;case UNAVAILABLE->503;default->409;};return response(status,Map.of("code","RECOVERY_DENIED"));}
        catch(CredentialEpochSessions.Denied denied){return response(401,Map.of("code","AUTHENTICATION_REQUIRED"));}
        catch(IllegalArgumentException|ArithmeticException invalid){return response(400,Map.of("code","INVALID_REQUEST"));}
        catch(Exception unavailable){return response(503,Map.of("code","RECOVERY_UNAVAILABLE"));}
    }
    static UUID id(Map<String,Object> b,String key){return UUID.fromString(text(b,key));}
    static String text(Map<String,Object> b,String key){if(!(b.get(key) instanceof String s))throw new IllegalArgumentException();return s;}
    static long number(Map<String,Object> b,String key){Object v=b.get(key);if(!(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long))throw new IllegalArgumentException();long n=((Number)v).longValue();if(n<0)throw new IllegalArgumentException();return n;}
    static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","no-store").header("Pragma","no-cache").body(body);}
}

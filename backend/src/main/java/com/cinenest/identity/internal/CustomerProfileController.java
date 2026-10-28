package com.cinenest.identity.internal;
import java.security.Principal;import java.nio.charset.StandardCharsets;import java.util.*;import jakarta.servlet.http.HttpServletRequest;
import com.nimbusds.jose.util.JSONObjectUtils;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="cinenest.identity.profile.enabled",havingValue="true")
class CustomerProfileController {
 private final CustomerProfileService profiles;CustomerProfileController(CustomerProfileService profiles){this.profiles=profiles;}
 @GetMapping("/api/accounts/me/profile") ResponseEntity<?> read(Principal principal,HttpServletRequest request){try{if(request.getQueryString()!=null)return result(profiles.invalid(UUID.fromString(principal.getName()),"READ"));return result(profiles.read(UUID.fromString(principal.getName())));}catch(Exception failure){return response(503,null);}}
 @PutMapping("/api/accounts/me/profile") ResponseEntity<?> update(Principal principal,HttpServletRequest request){UUID actor=UUID.fromString(principal.getName());try{
  if(request.getQueryString()!=null||!Objects.toString(request.getContentType(),"").split(";",2)[0].equals("application/json"))return result(profiles.invalid(actor));
  byte[] bytes=request.getInputStream().readNBytes(2049);if(bytes.length>2048)return result(profiles.invalid(actor));
  try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)return result(profiles.invalid(actor));parser.skipChildren();if(parser.nextToken()!=null)return result(profiles.invalid(actor));}catch(Exception invalid){return result(profiles.invalid(actor));}
  Map<String,Object> v;try{v=JSONObjectUtils.parse(new String(bytes,StandardCharsets.UTF_8));}catch(Exception invalid){return result(profiles.invalid(actor));}
  if(!v.keySet().equals(Set.of("fullName","phone","expectedRevision"))||!(v.get("fullName") instanceof String)||!(v.get("phone")==null||v.get("phone") instanceof String)||!(v.get("expectedRevision") instanceof Long||v.get("expectedRevision") instanceof Integer))return result(profiles.invalid(actor));
  return result(profiles.update(actor,((Number)v.get("expectedRevision")).longValue(),(String)v.get("fullName"),(String)v.get("phone")));
 }catch(Exception failure){return response(503,null);}}
 private static ResponseEntity<?> result(CustomerProfileService.Result r){return response(r.status(),r.profile());}
 private static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).header("Cache-Control","no-store").body(body);}
}

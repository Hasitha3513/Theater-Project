package com.cinenest.iot.internal;

import com.cinenest.access.SmartLock.Capability;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;

@Component
final class LockRegistryPolicy {
    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status) { super("Lock registry unavailable, changed or denied");this.status=status; }
    }
    record Registration(UUID key, LockRegistryReferenceResolver.Reference reference,
        String manufacturer,String model,String sku,String hardware,String firmware,
        LockRegistryReferenceResolver.Reference gateway,String source,UUID sourceReference,List<String> capabilities,byte[] hash) {
        Registration { capabilities=List.copyOf(capabilities);hash=hash.clone(); }
        @Override public byte[] hash(){return hash.clone();}
        @Override public String toString(){return "Registration[REDACTED]";}
    }
    private final Environment env;
    LockRegistryPolicy(Environment env){this.env=env;}
    void configured(){if(!env.getProperty("cinenest.iot.lock-registry.enabled",Boolean.class,false)||
        !"QUALIFICATION_ONLY".equals(env.getProperty("cinenest.iot.lock-registry.mode")))throw new Failure(503);}
    static UUID uuid(String value){try{if(value==null||!value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException();return UUID.fromString(value);}catch(RuntimeException e){throw new Failure(400);}}
    private static String text(Object x,boolean optional){if(x==null&&optional)return null;if(!(x instanceof String s)||s.isBlank()||s.codePointCount(0,s.length())>128||s.chars().anyMatch(Character::isISOControl))throw new Failure(400);return s;}
    private static Map<String,Object> object(Object x,Set<String> fields){if(!(x instanceof Map<?,?> m)||!m.keySet().equals(fields))throw new Failure(400);@SuppressWarnings("unchecked") var v=(Map<String,Object>)m;return v;}
    private static LockRegistryReferenceResolver.Reference reference(Object x){var m=object(x,Set.of("id","version"));return new LockRegistryReferenceResolver.Reference(uuid(text(m.get("id"),false)),uuid(text(m.get("version"),false)));}
    Registration parse(UUID venue,byte[] body){
        if(body.length>16384)throw new Failure(400);
        try {
            // Strict duplicate member detection, including nested objects, before conversion.
            try(var parser=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(body)){
                if(parser.nextToken()!=JsonToken.START_OBJECT)throw new Failure(400);
                parser.skipChildren();if(parser.nextToken()!=null)throw new Failure(400); }
            var m=JSONObjectUtils.parse(new String(body,StandardCharsets.UTF_8));
            if(!m.keySet().equals(Set.of("commandKey","expectedRevision","provider","providerDeviceReference","manufacturer","model","sku","hardwareRevision","firmwareRevision","gatewayReference","capabilityDeclaration")))throw new Failure(400);
            if(!(m.get("expectedRevision") instanceof Number n)||!n.toString().equals("0")||!"TUYA".equals(m.get("provider")))throw new Failure(400);
            var d=object(m.get("capabilityDeclaration"),Set.of("source","sourceReference","capabilities"));
            String source=text(d.get("source"),false);if(!Set.of("OPERATOR_DECLARATION","VENDOR_DOCUMENT","CONTRACT_FIXTURE").contains(source))throw new Failure(400);
            UUID sr=d.get("sourceReference")==null?null:uuid(text(d.get("sourceReference"),false));if(source.equals("VENDOR_DOCUMENT")&&sr==null)throw new Failure(400);
            if(!(d.get("capabilities") instanceof List<?> list)||list.size()>5)throw new Failure(400);
            var caps=new TreeSet<String>();for(Object c:list){String v=text(c,false);Capability.valueOf(v);if(!caps.add(v))throw new Failure(400);}
            // Canonical structured request includes route venue and action, not generated IDs/times.
            var canonical=new TreeMap<String,Object>(m);canonical.put("venueId",venue.toString());canonical.put("action","REGISTER_V1");
            canonical.put("providerDeviceReference",new TreeMap<>(object(m.get("providerDeviceReference"),Set.of("id","version"))));
            if(m.get("gatewayReference")!=null)canonical.put("gatewayReference",new TreeMap<>(object(m.get("gatewayReference"),Set.of("id","version"))));
            var cd=new TreeMap<String,Object>(d);cd.put("capabilities",List.copyOf(caps));canonical.put("capabilityDeclaration",cd);
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(JSONObjectUtils.toJSONString(canonical).getBytes(StandardCharsets.UTF_8));
            return new Registration(uuid(text(m.get("commandKey"),false)),reference(m.get("providerDeviceReference")),
                text(m.get("manufacturer"),false),text(m.get("model"),false),text(m.get("sku"),true),text(m.get("hardwareRevision"),true),text(m.get("firmwareRevision"),true),
                m.get("gatewayReference")==null?null:reference(m.get("gatewayReference")),source,sr,List.copyOf(caps),hash);
        }catch(Failure f){throw f;}catch(Exception e){throw new Failure(400);}
    }
}

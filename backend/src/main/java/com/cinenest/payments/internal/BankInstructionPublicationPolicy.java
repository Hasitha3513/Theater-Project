package com.cinenest.payments.internal;

import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.util.JSONObjectUtils;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;

@Component
final class BankInstructionPublicationPolicy {
    static final String PROPOSE="bank.instructions.propose", PUBLISH="bank.instructions.publish", READ="bank.instructions.read";
    record Draft(String version, UUID reference, UUID referenceVersion, String digest, Instant from, Instant until, String hash) {}
    private final Environment env;
    BankInstructionPublicationPolicy(Environment env) { this.env=env; }
    void configured() {
        if (!env.getProperty("cinenest.bank.instruction-publication.enabled",Boolean.class,false)) throw new BankFailure(404,"Staging publication disabled");
        var profiles=Set.copyOf(Arrays.asList(env.getActiveProfiles()));
        if (!(profiles.equals(Set.of("synthetic-quotation")) || profiles.equals(Set.of("local-synthetic","synthetic-quotation"))) ||
            !"STAGING_ONLY".equals(env.getProperty("cinenest.bank.instruction-publication.mode"))) throw new BankFailure(503,"Staging publication unavailable");
    }
    Map<String,Object> parse(byte[] body) {
        if(body==null || body.length>16384)throw new BankFailure(400,"Invalid publication command");
        try {
            try(var parser=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(body)) {
                if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException();parser.skipChildren();if(parser.nextToken()!=null)throw new IllegalArgumentException();
            }
            return JSONObjectUtils.parse(new String(body,java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception failure) { throw new BankFailure(400,"Invalid publication command"); }
    }
    static UUID id(Object value) { if(!(value instanceof String s))throw new BankFailure(400,"Canonical reference required");return BankInstructions.canonical(s); }
    static long revision(Object value) {
        if(!(value instanceof Number n)||!n.toString().matches("0|[1-9][0-9]{0,18}"))throw new BankFailure(400,"Invalid revision");
        try{return Long.parseLong(n.toString());}catch(RuntimeException failure){throw new BankFailure(400,"Invalid revision");}
    }
    static String hash(Object value) { if(!(value instanceof String s)||!s.matches("[0-9a-f]{64}"))throw new BankFailure(400,"Invalid snapshot digest");return s; }
    static void keys(Map<String,Object> body,String... fields) { if(body==null||!body.keySet().equals(Set.of(fields)))throw new BankFailure(400,"Invalid publication command"); }
    Draft draft(Map<String,Object> b) {
        keys(b,"commandKey","expectedRevision","instructionVersion","reference","referenceVersion","contentSha256","effectiveFrom","effectiveUntil");
        if(revision(b.get("expectedRevision"))!=0)throw new BankFailure(409,"Initial revision required");
        if(!(b.get("instructionVersion") instanceof String version)||!version.matches("SYNTHETIC-[A-Za-z0-9_-]{1,80}"))throw new BankFailure(400,"Non-payable staging version required");
        UUID ref=id(b.get("reference")),rv=id(b.get("referenceVersion"));String digest=hash(b.get("contentSha256"));
        // Deliberately instantiate only the existing closed non-payable catalogue. No replaceable provider resolver.
        try{new FixedInstructionResolver().resolve(ref,rv,digest);}catch(BankFailure denied){throw new BankFailure(400,"Non-payable staging reference required");}
        try {
            Instant from=Instant.parse((String)b.get("effectiveFrom")),until=Instant.parse((String)b.get("effectiveUntil"));
            if(!from.isBefore(until))throw new IllegalArgumentException();
            var canonical=new TreeMap<String,Object>();canonical.put("version",version);canonical.put("reference",ref.toString());canonical.put("referenceVersion",rv.toString());canonical.put("digest",digest);canonical.put("from",from.toString());canonical.put("until",until.toString());
            return new Draft(version,ref,rv,digest,from,until,BankPolicy.hash(JSONObjectUtils.toJSONString(canonical)));
        }catch(RuntimeException failure){throw new BankFailure(400,"Explicit valid staging interval required");}
    }
    String commandHash(UUID venue,UUID target,String action,Map<String,Object> b) { return BankPolicy.commandHash("INSTRUCTION_PUBLICATION:"+venue+":"+target+":"+action,new TreeMap<>(b)); }
}

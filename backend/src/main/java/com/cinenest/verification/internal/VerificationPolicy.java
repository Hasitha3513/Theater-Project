package com.cinenest.verification.internal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** Explicit test policy; absent values fail closed without production defaults. */
@Component
class VerificationPolicy {
    static final int MAX_BYTES=5*1024*1024, MAX_PIXELS=20_000_000;
    static final List<String> LABELS=List.of("TEST / SYNTHETIC IDENTITY VERIFICATION","NO REAL IDENTITY DOCUMENTS","NOT VALID FOR PHYSICAL ADMISSION");
    final Environment env;
    VerificationPolicy(Environment env){this.env=env;}
    record Policy(String version,String hash,UUID id,Duration upload,Duration retention,Duration decision,Duration cleanup,Set<String> fixtures){}
    Policy current(){return load(true);}
    Policy maintenance(){return load(false);}
    private Policy load(boolean commands){
        try {
            if((commands&&!env.getProperty("cinenest.verification.enabled",Boolean.class,false))||!Arrays.asList(env.getActiveProfiles()).contains("synthetic-quotation"))throw new IllegalArgumentException();
            String version=env.getRequiredProperty("cinenest.verification.policy-version");
            if(!version.matches("SYNTHETIC-[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException();
            var values=new ArrayList<Long>();
            for(String name:List.of("upload","retention","decision","cleanup")){
                long value=Long.parseLong(env.getRequiredProperty("cinenest.verification."+name+"-seconds"));
                if(value<=0||value>Integer.MAX_VALUE)throw new IllegalArgumentException(); values.add(value);
            }
            Set<String> hashes=new TreeSet<>(Arrays.asList(env.getRequiredProperty("cinenest.verification.fixture-sha256").split(",")));
            if(hashes.isEmpty()||hashes.size()>32||hashes.stream().anyMatch(h->!h.matches("[0-9a-f]{64}"))||!SyntheticFixtures.hashes().containsAll(hashes))throw new IllegalArgumentException();
            String hash=hash(version+values+hashes+"18:2:5242880:20000000:MANUAL_SIMULATED");
            return new Policy(version,hash,UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)),Duration.ofSeconds(values.get(0)),Duration.ofSeconds(values.get(1)),Duration.ofSeconds(values.get(2)),Duration.ofSeconds(values.get(3)),hashes);
        }catch(RuntimeException invalid){throw new VerificationFailure(503,"Synthetic verification policy is not configured");}
    }
    static String hash(String text){return hash(text.getBytes(StandardCharsets.UTF_8));}
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}

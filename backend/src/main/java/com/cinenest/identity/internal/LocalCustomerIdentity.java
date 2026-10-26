package com.cinenest.identity.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private local-test Identity adapter. Never a production admission or email-delivery service. */
@Service
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.manual.enabled", havingValue="true")
class LocalCustomerIdentity {
    final JdbcClient jdbc;
    final IdentityStore store;
    final TransactionTemplate tx;
    final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("",16,600000,256);
    final Semaphore hashing = new Semaphore(2);
    final String dummy;
    final Path outbox;
    final int limit, windowSeconds, tokenSeconds;
    LocalCustomerIdentity(JdbcClient jdbc, TransactionTemplate tx, IdentityStore store,
            @Value("${cinenest.identity.manual.outbox}") String outbox,
            @Value("${cinenest.identity.manual.attempt-limit:5}") int limit,
            @Value("${cinenest.identity.manual.window-seconds:900}") int windowSeconds,
            @Value("${cinenest.identity.manual.verification-seconds:900}") int tokenSeconds) throws Exception {
        this.jdbc=jdbc;this.store=store;this.tx=tx;this.limit=limit;this.windowSeconds=windowSeconds;this.tokenSeconds=tokenSeconds;
        if(limit<1 || limit>100 || windowSeconds<1 || windowSeconds>3600 || tokenSeconds<1 || tokenSeconds>3600)
            throw new IllegalArgumentException("Invalid local identity limits");
        this.outbox=Path.of(outbox);
        if(!this.outbox.isAbsolute() || Files.isSymbolicLink(this.outbox)) throw new IllegalArgumentException("Private absolute outbox required");
        Files.createDirectories(this.outbox,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if(!Files.getPosixFilePermissions(this.outbox,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("Private outbox permissions required");
        encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        dummy=encoder.encode(UUID.randomUUID().toString());
    }
    static String email(String raw) {
        if(raw==null || raw.length()>254) throw new IllegalArgumentException();
        String value=raw.strip().toLowerCase(Locale.ROOT);
        if(!value.matches("[a-z0-9][a-z0-9._+\\-]{0,63}@example\\.test")) throw new IllegalArgumentException();
        return value;
    }
    static String password(String raw) {
        if(raw==null || raw.length()>256) throw new IllegalArgumentException();
        String p=Normalizer.normalize(raw,Normalizer.Form.NFC);
        int size=p.codePointCount(0,p.length());
        if(size<15 || size>128 || p.codePoints().anyMatch(Character::isISOControl)
                || java.util.Set.of("passwordpassword","123456789012345","qwertyuiopasdfgh","letmeinletmeinletmein").contains(p.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
        return p;
    }
    static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception failure) { throw new IllegalStateException("Digest unavailable"); }
    }
    boolean throttle(String action,String subject) {
        long bucket=Instant.now().getEpochSecond()/windowSeconds;
        int attempts=jdbc.sql("INSERT INTO cinenest_identity.manual_throttle VALUES (:action,:subject,:bucket,1) ON CONFLICT (action,subject_digest,bucket) DO UPDATE SET attempts=manual_throttle.attempts+1 RETURNING attempts")
            .param("action",action).param("subject",digest(subject)).param("bucket",bucket).query(Integer.class).single();
        return attempts<=(action.equals("GLOBAL")?limit*10:limit);
    }
    void audit(String action,UUID account,boolean allowed) {
        store.audit(account,account,"AUTHENTICATION",allowed);
        jdbc.sql("INSERT INTO cinenest_identity.manual_audit(event_id,account_id,action,outcome) VALUES (:id,:account,:action,:outcome)")
            .param("id",UUID.randomUUID()).param("account",account,java.sql.Types.OTHER).param("action",action).param("outcome",allowed?"ALLOW":"DENY").update();
    }
    int register(String input,String raw) {
        String email=email(input),password=password(raw);
        if(!hashing.tryAcquire()) return 503;
        try {
            return tx.execute(status -> {
                if(!throttle("GLOBAL","REGISTER") || !throttle("REGISTER",email)) { audit("REGISTER",null,false);return 429; }
                String hash="{pbkdf2-v1}"+encoder.encode(password);
                jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:email,16828))").param("email",email).query(rs -> {rs.next();return true;});
                if(jdbc.sql("SELECT count(*) FROM cinenest_identity.manual_customer WHERE email=:email").param("email",email).query(Integer.class).single()>0) {
                    audit("REGISTER",null,false);return 202;
                }
                UUID id=UUID.randomUUID();byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
                String token=HexFormat.of().formatHex(bytes);
                jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'INELIGIBLE',0)").param("id",id).update();
                jdbc.sql("INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at) VALUES (:id,:email,:hash,:digest,clock_timestamp()+make_interval(secs=>:seconds))")
                    .param("id",id).param("email",email).param("hash",hash).param("digest",digest(token)).param("seconds",tokenSeconds).update();
                Path file=outbox.resolve(id+".txt");
                boolean[] created={false};
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int state) {
                        if(state!=STATUS_COMMITTED && created[0]) try {Files.deleteIfExists(file);}catch(Exception ignored) { /* private orphan; never delivered by API */ }
                    }
                });
                try {
                    try(var entries=Files.list(outbox)) { if(entries.limit(1001).count()>=1000) throw new IllegalStateException("Outbox full"); }
                    Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    created[0]=true;
                    Files.writeString(file,"LOCAL SYNTHETIC TEST ONLY\n"+email+"\n"+token+"\n");
                } catch(Exception failure) { throw new IllegalStateException("Private test delivery unavailable"); }
                audit("REGISTER",id,true);return 202;
            });
        } finally { hashing.release(); }
    }
    int verify(String token) {
        if(token==null || !token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
        return tx.execute(status -> {
            if(!throttle("VERIFY","local-verification")) {audit("VERIFY",null,false);return 429;}
            var ids=jdbc.sql("SELECT m.account_id FROM cinenest_identity.manual_customer m JOIN cinenest_identity.account a USING(account_id) WHERE verification_digest=:digest AND verified_at IS NULL AND verification_expires_at>clock_timestamp() AND a.synthetic=TRUE AND a.eligibility='INELIGIBLE' AND a.revision=0 FOR UPDATE OF m,a")
                .param("digest",digest(token)).query(UUID.class).list();
            // Recheck after acquiring locks; a concurrent verification/suspension must not reactivate an account.
            if(ids.isEmpty()) {audit("VERIFY",null,false);return 400;}
            UUID id=ids.getFirst();
            int changed=jdbc.sql("UPDATE cinenest_identity.manual_customer SET verified_at=clock_timestamp() WHERE account_id=:id AND verified_at IS NULL AND verification_expires_at>clock_timestamp()")
                .param("id",id).update();
            if(changed!=1) {audit("VERIFY",null,false);return 400;}
            jdbc.sql("UPDATE cinenest_identity.account SET eligibility='ELIGIBLE',revision=revision+1 WHERE account_id=:id AND synthetic=TRUE AND eligibility='INELIGIBLE' AND revision=0").param("id",id).update();
            audit("VERIFY",id,true);return 204;
        });
    }
    Login login(String input,String raw) {
        String email=email(input),password=password(raw);
        if(!hashing.tryAcquire()) throw new Busy();
        try {
            return tx.execute(status -> {
                if(!throttle("GLOBAL","LOGIN") || !throttle("LOGIN",email)) {audit("LOGIN",null,false);return new Login(429,null);}
                var rows=jdbc.sql("SELECT m.account_id,m.password_hash,m.verified_at,a.eligibility,a.synthetic FROM cinenest_identity.manual_customer m JOIN cinenest_identity.account a USING(account_id) WHERE email=:email FOR SHARE OF m,a")
                    .param("email",email).query((rs,n)->new Credential(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3)!=null,rs.getString(4),rs.getBoolean(5))).list();
                String hash=rows.isEmpty()?dummy:rows.getFirst().hash().substring("{pbkdf2-v1}".length());
                boolean correct=encoder.matches(password,hash);
                UUID account=rows.isEmpty()?null:rows.getFirst().id();
                boolean allowed=correct && !rows.isEmpty() && rows.getFirst().verified() && rows.getFirst().synthetic() && rows.getFirst().eligibility().equals("ELIGIBLE");
                audit("LOGIN",account,allowed);
                return new Login(allowed?204:401,allowed?account:null);
            });
        } finally {hashing.release();}
    }
    record Credential(UUID id,String hash,boolean verified,String eligibility,boolean synthetic) { @Override public String toString(){return "Credential[REDACTED]";} }
    record Login(int status,UUID account) {}
    static class Busy extends RuntimeException {}
}

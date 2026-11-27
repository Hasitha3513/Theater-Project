package com.cinenest.identity.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** No production key source or delivery provider is selected or installed by this configuration. */
@org.springframework.scheduling.annotation.EnableScheduling
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="cinenest.identity.customer.enabled",havingValue="true")
public class CustomerCredentialConfiguration {
    public interface KeySource { Keys keys(); }
    public interface DeliveryGateway {
        boolean qualified();
        boolean qualificationOnly();
        DeliveryStatus send(DeliveryMessage message);
    }
    public enum DeliveryStatus { ACCEPTED, UNKNOWN }
    public record Keys(String version, byte[] encryption, byte[] lookup, byte[] commands) {
        public Keys {
            if(version==null||!version.matches("[A-Za-z0-9._:/-]{1,128}"))throw new IllegalArgumentException("Invalid protected key reference");
            for(byte[] key:List.of(encryption,lookup,commands))if(key==null||key.length!=32)throw new IllegalArgumentException("Protected keys required");
            encryption=encryption.clone();lookup=lookup.clone();commands=commands.clone();
            if(Arrays.equals(encryption,lookup)||Arrays.equals(encryption,commands)||Arrays.equals(lookup,commands))throw new IllegalArgumentException("Separate protected keys required");
        }
        @Override public byte[] encryption(){return encryption.clone();}
        @Override public byte[] lookup(){return lookup.clone();}
        @Override public byte[] commands(){return commands.clone();}
        @Override public String toString(){return "CustomerCredentialKeys[REDACTED]";}
    }
    public record DeliveryMessage(UUID id, String address, String token, String purpose, Instant expires) {
        @Override public String toString(){return "CustomerCredentialDelivery[REDACTED]";}
    }
    record Settings(String policy, String privacy, String delivery, String addressPolicy, int capabilitySeconds, int limit,
                    int windowSeconds, int hashes, int registrationSeconds, int resendCooldownSeconds, boolean qualificationOnly) {
        Settings {
            if(!"ASCII_CASEFOLD_V1".equals(addressPolicy))throw new IllegalStateException("Explicit supported address normalization policy required");
            for(String value:List.of(policy,privacy,delivery))if(value==null||!value.matches("[A-Za-z0-9._:/-]{1,128}"))throw new IllegalStateException("Explicit customer policy references required");
            if(registrationSeconds<1||registrationSeconds>604800||resendCooldownSeconds<1||resendCooldownSeconds>3600)throw new IllegalStateException("Explicit registration and resend policy required");
            if(capabilitySeconds<1||capabilitySeconds>3600||limit<1||limit>1000||windowSeconds<1||windowSeconds>3600||hashes<1||hashes>8)throw new IllegalStateException("Explicit bounded customer limits required");
        }
        CustomerCredentialLifecycle.Policy core(){return new CustomerCredentialLifecycle.Policy(true,policy,privacy,delivery,Duration.ofSeconds(capabilitySeconds),hashes);}
    }
    @Bean Settings customerCredentialSettings(Environment env, DeliveryGateway gateway) {
        if(env.getProperty("spring.flyway.enabled",Boolean.class,true)||!"never".equals(env.getProperty("spring.sql.init.mode")))throw new IllegalStateException("Customer runtime must not mutate its schema");
        if(!env.getProperty("cinenest.identity.enabled",Boolean.class,false)||env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false)
            ||Arrays.stream(env.getActiveProfiles()).anyMatch(x->x.equals("local-synthetic")||x.equals("synthetic-quotation"))
            ||!gateway.qualified())throw new IllegalStateException("Qualified isolated customer configuration required");
        if(gateway.qualificationOnly()&&!env.acceptsProfiles(org.springframework.core.env.Profiles.of("customer-qualification")))throw new IllegalStateException("Test delivery requires isolated qualification profile");
        if(!gateway.qualificationOnly()&&!env.getProperty("cinenest.identity.customer.delivery-worker.enabled",Boolean.class,false))throw new IllegalStateException("Durable delivery worker configuration required");
        return new Settings(env.getRequiredProperty("cinenest.identity.customer.policy-revision"),env.getRequiredProperty("cinenest.identity.customer.privacy-revision"),env.getRequiredProperty("cinenest.identity.customer.delivery-revision"),env.getRequiredProperty("cinenest.identity.customer.address-policy"),
            env.getRequiredProperty("cinenest.identity.customer.capability-seconds",Integer.class),env.getRequiredProperty("cinenest.identity.customer.attempt-limit",Integer.class),
            env.getRequiredProperty("cinenest.identity.customer.window-seconds",Integer.class),env.getRequiredProperty("cinenest.identity.customer.hash-concurrency",Integer.class),env.getRequiredProperty("cinenest.identity.customer.registration-seconds",Integer.class),env.getRequiredProperty("cinenest.identity.customer.resend-cooldown-seconds",Integer.class),gateway.qualificationOnly());
    }
    /** Read-only schema inspection bean: automatic Flyway migration remains disabled. */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.flywaydb.core.Flyway.class)
    org.flywaydb.core.Flyway customerCredentialSchema(javax.sql.DataSource dataSource,Settings settings){
        var flyway=schemaInspector(dataSource);
        validateExistingSchema(flyway);
        return flyway;
    }
    static org.flywaydb.core.Flyway schemaInspector(javax.sql.DataSource dataSource){
        return org.flywaydb.core.Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
            .ignoreMigrationPatterns(new String[0]).createSchemas(false).cleanDisabled(true).load();
    }
    static void validateExistingSchema(org.flywaydb.core.Flyway flyway){
        flyway.validate();
        var info=flyway.info();
        if(info.current()==null||!org.flywaydb.core.api.MigrationVersion.fromVersion("46").equals(info.current().getVersion())
                ||info.applied().length!=45||info.pending().length!=0)throw new IllegalStateException("Pre-migrated customer schema required");
    }
    @Bean Crypto customerCredentialCrypto(KeySource source){return new Crypto(source.keys());}
    @Bean CustomerCredentialRepository customerCredentialRepository(JdbcClient jdbc,PlatformTransactionManager manager){return new JdbcCustomerCredentialRepository(jdbc,manager);}
    @Bean CustomerCredentialDeliveryDispatcher customerCredentialDispatcher(CustomerCredentialRepository repository,Crypto crypto,DeliveryGateway gateway,Settings settings){return new CustomerCredentialDeliveryDispatcher(repository,crypto,gateway,settings);}
    @Bean CustomerCredentialService customerCredentialService(CustomerCredentialRepository repository,Crypto crypto,Settings settings,CustomerCredentialDeliveryDispatcher dispatcher){return new CustomerCredentialService(repository,crypto,settings,dispatcher);}

    @Bean
    @ConditionalOnProperty(name="cinenest.identity.customer.delivery-worker.enabled",havingValue="true")
    DeliveryPoller customerCredentialPoller(CustomerCredentialDeliveryDispatcher dispatcher,Environment env){int ms=env.getRequiredProperty("cinenest.identity.customer.delivery-worker.poll-ms",Integer.class);if(ms<1000||ms>60000)throw new IllegalStateException("Bounded delivery poll required");return new DeliveryPoller(dispatcher);}
    static final class DeliveryPoller {final CustomerCredentialDeliveryDispatcher dispatcher;DeliveryPoller(CustomerCredentialDeliveryDispatcher dispatcher){this.dispatcher=dispatcher;}
        @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${cinenest.identity.customer.delivery-worker.poll-ms}") void tick(){try{dispatcher.dispatchPending(4);}catch(RuntimeException unavailable){/* Durable pending/uncertain states remain; no payload diagnostics. */}}}
    static final class Crypto {
        final Keys keys; final SecureRandom random=new SecureRandom();
        Crypto(Keys keys){this.keys=Objects.requireNonNull(keys);}
        String mac(String domain,String value){try{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(domain.equals("ADDRESS")?keys.lookup():keys.commands(),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal((domain+":"+value).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException("Protected operation unavailable");}}
        byte[] seal(UUID reference,String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.encryption(),"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(reference.toString().getBytes(StandardCharsets.US_ASCII));var out=new ByteArrayOutputStream();out.write(nonce);out.write(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));return out.toByteArray();}catch(IOException|GeneralSecurityException e){throw new IllegalStateException("Protected operation unavailable");}}
        String open(UUID reference,byte[] value,String version){try{if(!keys.version().equals(version)||value==null||value.length<28||value.length>8192)throw new GeneralSecurityException();var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.encryption(),"AES"),new GCMParameterSpec(128,Arrays.copyOf(value,12)));c.updateAAD(reference.toString().getBytes(StandardCharsets.US_ASCII));return new String(c.doFinal(Arrays.copyOfRange(value,12,value.length)),StandardCharsets.UTF_8);}catch(GeneralSecurityException e){throw new IllegalStateException("Protected operation unavailable");}}
        String keyFingerprint(byte[] key){try{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal("CINENEST_CUSTOMER_KEY_CONTINUITY_V1".getBytes(StandardCharsets.US_ASCII)));}catch(GeneralSecurityException unavailable){throw new IllegalStateException("Protected operation unavailable");}}
        String token(){byte[] value=new byte[32];random.nextBytes(value);return HexFormat.of().formatHex(value);}
    }
}

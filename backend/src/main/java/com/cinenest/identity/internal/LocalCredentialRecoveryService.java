package com.cinenest.identity.internal;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class LocalCredentialRecoveryService {
    record Issuance(UUID command,UUID grant,long grantRevision,long accountRevision,long credentialRevision,int seconds,String reference) { @Override public String toString(){return "RecoveryIssuance[REDACTED]";} }
    record Issued(UUID capability,String token,Instant expiresAt) { @Override public String toString(){return "IssuedCapability[REDACTED]";} }
    final JdbcLocalCredentialRecoveryRepository repository;final IdentityStore store;final TransactionTemplate tx;
    final LocalCredentialRecovery planner;final boolean enabled;final int limit,window;final Semaphore hashing=new Semaphore(2);
    LocalCredentialRecoveryService(JdbcLocalCredentialRecoveryRepository repository,IdentityStore store,PlatformTransactionManager manager,Environment env) {
        this.repository=repository;this.store=store;this.tx=CredentialEpochSessions.transaction(manager);
        enabled=env.getProperty("cinenest.identity.manual.recovery.enabled",Boolean.class,false);
        Set<String> profiles=Set.copyOf(Arrays.asList(env.getActiveProfiles()));
        if(enabled&&(!env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false)
            ||!Set.of(Set.of("local-synthetic"),Set.of("local-synthetic","synthetic-quotation")).contains(profiles)))throw new IllegalStateException("Isolated synthetic recovery configuration required");
        limit=env.getProperty("cinenest.identity.manual.attempt-limit",Integer.class,5);window=env.getProperty("cinenest.identity.manual.window-seconds",Integer.class,900);
        if(limit<1||limit>100||window<1||window>3600)throw new IllegalStateException("Invalid recovery limits");
        planner=new LocalCredentialRecovery(new LocalCredentialRecovery.Policy(enabled,profiles,Duration.ofMinutes(15)),Clock.systemUTC());
    }
    void admission(UUID actor) {
        if(!enabled)throw new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.DISABLED);
        if(actor==null)throw JdbcLocalCredentialRecoveryRepository.denied();
        // Attempt accounting commits separately; neither a failed credential write nor a replay
        // rolls back the rate limit. It uses only the existing permitted GLOBAL throttle action.
        if(!Boolean.TRUE.equals(tx.execute(s->repository.attempt(actor,limit,window)))){denyAudit(actor,null);throw new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.UNAVAILABLE);}
    }
    Issued issue(UUID actor,UUID target,UUID venue,Issuance request) {
        admission(actor);
        try {
            if(request==null||request.command()==null||request.grant()==null||target==null||venue==null||request.grantRevision()<0||request.accountRevision()<0||request.credentialRevision()<0
                ||request.seconds()<1||request.seconds()>900||request.reference()==null||request.reference().isBlank()||request.reference().length()>200)throw JdbcLocalCredentialRecoveryRepository.denied();
            return tx.execute(status->{
                var authority=new JdbcLocalCredentialRecoveryRepository.Authority(request.grant(),request.grantRevision());
                var snapshot=repository.locked(target,actor,venue,authority,null);
                if(snapshot.account().revision()!=request.accountRevision()||snapshot.account().credentialRevision()!=request.credentialRevision())throw new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.REVISION);
                byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
                var cap=repository.issue(UUID.randomUUID(),request.command(),target,actor,venue,authority,request.accountRevision(),request.credentialRevision(),LocalCustomerIdentity.digest(token),request.seconds(),request.reference());
                return new Issued(cap.id(),token,cap.expiresAt());
            });
        }catch(RuntimeException failure){denyAudit(actor,target);throw sanitized(failure);}
    }
    LocalCredentialRecovery.Receipt consume(UUID actor,UUID target,UUID venue,UUID capability,LocalCredentialRecovery.Command command) {
        admission(actor);
        if(!hashing.tryAcquire())throw new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.UNAVAILABLE);
        try {
            if(command==null||!Objects.equals(actor,command.actor())||!Objects.equals(target,command.account())||!Objects.equals(venue,command.venue())||capability==null)throw JdbcLocalCredentialRecoveryRepository.denied();
            return tx.execute(status->{
                var cap=repository.capability(capability);
                if(!actor.equals(cap.issuer())||!actor.equals(cap.capability().actor())||!target.equals(cap.capability().account())||!venue.equals(cap.capability().venue()))throw JdbcLocalCredentialRecoveryRepository.denied();
                var snapshot=repository.locked(target,actor,venue,cap.authority(),cap);
                // The consumed bit is re-read after locks. V35 repeats current-revision and
                // single-consumption checks and samples its own server admission timestamp.
                cap=repository.capability(capability);snapshot=new LocalCredentialRecovery.Snapshot(snapshot.account(),snapshot.actor(),cap.capability());
                var append=planner.prepare(command,snapshot);var receipt=repository.commit(append);
                LocalCredentialRecovery.verifyReceipt(append,receipt);return receipt;
            });
        }catch(RuntimeException failure){denyAudit(actor,target);throw sanitized(failure);}
        finally{hashing.release();}
    }
    void denyAudit(UUID actor,UUID target){tx.executeWithoutResult(s->store.audit(actor,target,"AUTHENTICATION",false));}
    RuntimeException sanitized(RuntimeException failure){
        if(failure instanceof LocalCredentialRecovery.Denied d)return d;
        for(Throwable c=failure;c!=null;c=c.getCause())if(c instanceof java.sql.SQLException sql&&"23505".equals(sql.getSQLState()))return new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.REPLAY);
        return new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.UNAVAILABLE);
    }
}

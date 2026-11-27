package com.cinenest.identity.internal;

import java.util.*;
import static com.cinenest.identity.internal.CustomerCredentialLifecycle.*;

/** Complete application sequencing; external transport and protected persistence remain ports. */
final class CustomerCredentialService {
    record Result(int status, UUID account, long epoch) {}
    static final class Refused extends RuntimeException {
        final int status; Refused(int status){super("Customer operation unavailable");this.status=status;}
    }
    final CustomerCredentialRepository repository;
    final CustomerCredentialConfiguration.Crypto crypto;
    final CustomerCredentialConfiguration.Settings settings;
    final CustomerCredentialDeliveryDispatcher dispatcher;
    final String dummy=CustomerCredentialSecurity.encode(UUID.randomUUID()+" unknown credential");
    CustomerCredentialService(CustomerCredentialRepository repository,CustomerCredentialConfiguration.Crypto crypto,
                              CustomerCredentialConfiguration.Settings settings,CustomerCredentialDeliveryDispatcher dispatcher){
        this.repository=repository;this.crypto=crypto;this.settings=settings;this.dispatcher=dispatcher;
        repository.keyContinuity(crypto.keys.version(),crypto.keyFingerprint(crypto.keys.lookup()),crypto.keyFingerprint(crypto.keys.commands()),crypto.keyFingerprint(crypto.keys.encryption()),settings.addressPolicy());
    }
    static String email(String input){
        if(input==null||input.length()>254||!input.equals(input.strip())||!input.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?"))throw new Refused(400);
        String result=input.toLowerCase(Locale.ROOT);String[] parts=result.split("@",-1);
        if(parts.length!=2||parts[0].startsWith(".")||parts[0].endsWith(".")||parts[0].contains("..")||!parts[1].contains(".")||parts[1].contains(".."))throw new Refused(400);
        for(String label:parts[1].split("\\."))if(label.length()>63||label.startsWith("-")||label.endsWith("-"))throw new Refused(400);
        return result;
    }
    Result register(UUID command,String email,String password,String privacy,String subject){return execute(Action.REGISTER,command,email,password,null,privacy,subject);}
    Result verify(UUID command,String token,String password,String subject){return execute(Action.VERIFY,command,null,password,token,null,subject);}
    Result login(UUID command,String email,String password,String subject){return execute(Action.LOGIN,command,email,password,null,null,subject);}
    Result requestRecovery(UUID command,String email,String subject){return execute(Action.RECOVERY_REQUEST,command,email,null,null,null,subject);}
    Result recover(UUID command,String token,String password,String subject){return execute(Action.RECOVER,command,null,password,token,null,subject);}
    private Result execute(Action action,UUID command,String rawEmail,String password,String token,String privacy,String subject){
        if(command==null||subject==null||subject.length()>256)throw new Refused(400);
        String address=rawEmail==null?null:email(rawEmail);
        if((action==Action.REGISTER||action==Action.LOGIN||action==Action.RECOVERY_REQUEST)&&address==null)throw new Refused(400);
        if((action==Action.VERIFY||action==Action.RECOVER)&&(token==null||!token.matches("[0-9a-f]{64}")))throw new Refused(400);
        if((action==Action.REGISTER&&!settings.privacy().equals(privacy))||(token!=null&&!token.matches("[0-9a-f]{64}")))throw new Refused(400);
        if(action==Action.REGISTER||action==Action.LOGIN||action==Action.RECOVER||action==Action.VERIFY)try{if(password==null)throw new IllegalArgumentException();for(int i=0;i<password.length();i++){char c=password.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=password.length()||!Character.isLowSurrogate(password.charAt(++i)))throw new IllegalArgumentException();}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}LocalCustomerIdentity.password(password);}catch(RuntimeException invalid){throw new Refused(400);}
        String lookup=address==null?null:crypto.mac("ADDRESS",address);
        String tokenDigest=token==null?null:CustomerCredentialSecurity.digest(token);
        StringBuilder material=new StringBuilder();for(Object v:new Object[]{action,address,password,token,privacy}){if(v==null){material.append("-1:");}else{String s=v.toString();material.append(s.length()).append(':').append(s);}}
        String digest=crypto.mac("COMMAND",material.toString());
        UUID lease=repository.admit(command,action.name(),crypto.mac("ABUSE",subject)+":"+crypto.mac("ABUSE_ID",lookup==null?String.valueOf(tokenDigest):lookup),
            action==Action.REGISTER||action==Action.LOGIN||action==Action.RECOVER||action==Action.VERIFY,settings);
        if(lease==null)throw new Refused(429);

        try{
            Result result=repository.transaction(()->{
                repository.lockCommand(command);
                var prior=repository.receipt(command);
                if(prior.isPresent()){
                    var receipt=prior.get();
                    if(!receipt.requestDigest().equals(digest)||!receipt.action().equals(action.name()))throw new Refused(409);
                    if(action!=Action.LOGIN||receipt.status()!=204)return new Result(receipt.status(),null,0);
                    var current=repository.account(receipt.account(),true).orElseThrow(()->new Refused(401));
                    if(current.state()!=State.VERIFIED||current.epoch()!=receipt.epoch()||!current.policy().equals(settings.policy())||!current.privacy().equals(settings.privacy())||!current.delivery().equals(settings.delivery())||!CustomerCredentialSecurity.matches(password,current.password()))throw new Refused(401);
                    return new Result(204,current.account(),current.epoch());
                }
                if(lookup!=null)repository.lockAddress(lookup);
                CustomerCredentialRepository.Capability capability=null;
                CustomerCredentialRepository.Head head;
                if(tokenDigest!=null){
                    capability=repository.capability(tokenDigest).orElseThrow(()->new Refused(400));
                    head=repository.account(capability.core().account(),true).orElseThrow(()->new Refused(400));
                    capability=repository.capability(tokenDigest).orElseThrow(()->new Refused(400));
                }else head=repository.address(lookup,true).orElse(null);
                if(action==Action.REGISTER&&head!=null&&head.state()==State.PENDING&&repository.abandonExpired(head,command))head=null;
                if(action==Action.REGISTER&&head!=null||action==Action.RECOVERY_REQUEST&&(head==null||head.state()!=State.VERIFIED)){
                    if(action==Action.REGISTER){boolean matches=CustomerCredentialSecurity.matches(password,head.password());if(matches&&head.state()==State.PENDING&&head.policy().equals(settings.policy())&&head.privacy().equals(settings.privacy())&&head.delivery().equals(settings.delivery())&&repository.resendDue(head,settings.resendCooldownSeconds())){repository.permit(lease,command,head.account(),Action.REGISTER,settings.policy());repository.reissue(head,command,digest,lease,issue(head,repository.now(),true));return new Result(202,null,0);}}
                    repository.neutral(command,digest,action.name(),202);return new Result(202,null,0);
                }
                if(head==null&&action!=Action.REGISTER){
                    if(action==Action.LOGIN)CustomerCredentialSecurity.matches(password,dummy);
                    repository.neutral(command,digest,action.name(),action==Action.LOGIN?401:400);return new Result(action==Action.LOGIN?401:400,null,0);
                }
                if(head==null){UUID account=UUID.randomUUID(),reference=UUID.randomUUID();head=new CustomerCredentialRepository.Head(account,reference,lookup,crypto.seal(reference,address),crypto.keys.version(),State.ABSENT,0,0,null,settings.privacy(),settings.policy(),settings.delivery(),repository.now().plusSeconds(settings.registrationSeconds()));}
                if(action==Action.LOGIN&&head.state()!=State.VERIFIED){CustomerCredentialSecurity.matches(password,dummy);repository.neutral(command,digest,action.name(),401);return new Result(401,null,0);}
                if(!head.policy().equals(settings.policy())||!head.privacy().equals(settings.privacy())||!head.delivery().equals(settings.delivery())){
                    if(action==Action.RECOVERY_REQUEST){repository.neutral(command,digest,action.name(),202);return new Result(202,null,0);}
                    if(action==Action.LOGIN){CustomerCredentialSecurity.matches(password,dummy);repository.neutral(command,digest,action.name(),401);return new Result(401,null,0);}
                    throw new Refused(409);
                }
                if(action==Action.VERIFY&&(!head.registrationExpires().isAfter(repository.now())||!CustomerCredentialSecurity.matches(password,head.password())))throw new Refused(401);
                var permit=repository.permit(lease,command,head.account(),action,settings.policy());
                var planner=new CustomerCredentialLifecycle(settings.core(),repository::now);
                Plan plan;
                try{plan=planner.prepare(new Command(command,head.account(),action,head.revision(),head.epoch(),action==Action.VERIFY?null:password,token),new Snapshot(head.core(),capability==null?null:capability.core(),permit,settings.policy()));}
                catch(Denied denied){throw new Refused(action==Action.LOGIN?401:409);}
                CustomerCredentialRepository.Issue issue=plan.deliveryRequired()?issue(head,plan.occurredAt(),action==Action.REGISTER):null;
                var receipt=repository.commit(plan,head,digest,lease,issue);
                if(CustomerCredentialPorts.verify(plan,receipt)!=CustomerCredentialPorts.Outcome.RECORDED)throw new Refused(503);
                return new Result(action==Action.REGISTER||action==Action.RECOVERY_REQUEST?202:204,action==Action.LOGIN?head.account():null,plan.nextEpoch());
            });
            return result;
        }catch(Refused denied){repository.denied(command,digest,action.name(),denied.status);throw denied;}finally{repository.release(lease);}
    }    private CustomerCredentialRepository.Issue issue(CustomerCredentialRepository.Head head,java.time.Instant now,boolean verify){UUID cap=UUID.randomUUID(),outbox=UUID.randomUUID();String secret=crypto.token();String destination=crypto.open(head.address(),head.sealedAddress(),head.keyVersion());java.time.Instant expires=now.plusSeconds(settings.capabilitySeconds());if(verify&&head.registrationExpires().isBefore(expires))expires=head.registrationExpires();return new CustomerCredentialRepository.Issue(cap,CustomerCredentialSecurity.digest(secret),verify?"VERIFY":"RECOVER",expires,outbox,crypto.seal(outbox,destination+"\n"+secret),crypto.keys.version());}

}

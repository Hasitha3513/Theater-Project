package com.cinenest.payments.internal;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
/** Test-only barrier; production API has no hooks or caller-controlled resolver. */
public final class InstructionSnapshotSupport {
 public static int read(ApplicationContext c,PlatformTransactionManager tx,UUID a,UUID v,UUID i,Runnable after){var real=c.getBean(InstructionResolver.class);InstructionResolver resolver=(ref,version,digest)->{var value=real.resolve(ref,version,digest);after.run();return value;};var service=new BankInstructions(c.getBean(InstructionRepository.class),resolver,c.getBean(com.cinenest.identity.ScopedAuthorization.class),c.getEnvironment(),tx);return service.read(a,v.toString(),i.toString(),false,false,UUID.randomUUID()).status();}
}

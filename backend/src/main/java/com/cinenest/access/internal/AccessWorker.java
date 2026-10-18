package com.cinenest.access.internal;
import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.scheduling.annotation.*;import org.springframework.core.env.Environment;
/** Only retention/expiry, never unsolicited provisioning, retry, delivery or hardware. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="cinenest.access.worker-enabled",havingValue="true")
class AccessWorker {
 private final AccessService service;
 AccessWorker(AccessService service,Environment env){this.service=service;if(env.getRequiredProperty("cinenest.access.worker-delay-ms",Long.class)<=0)throw new IllegalStateException("Explicit positive synthetic worker delay required");}
 @Scheduled(fixedDelayString="${cinenest.access.worker-delay-ms}")void run(){service.cleanup();}
}

package com.cinenest.payments.internal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="cinenest.bank.worker-enabled",havingValue="true")
class BankWorker {
 private final BankService service;BankWorker(BankService service,org.springframework.core.env.Environment env){this.service=service;if(env.getRequiredProperty("cinenest.bank.worker-delay-ms",Long.class)<=0)throw new IllegalStateException("Explicit positive test worker delay required");}
 @Scheduled(fixedDelayString="${cinenest.bank.worker-delay-ms}") void run(){service.reconcile();}
}

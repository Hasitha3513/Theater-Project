package com.cinenest.verification.internal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="cinenest.verification.worker-enabled",havingValue="true")
class VerificationWorker {
    private final VerificationService service;
    VerificationWorker(VerificationService service){this.service=service;}
    @Scheduled(fixedDelayString="${cinenest.verification.worker-delay-ms}")
    void reconcile(){try{service.reconcile();}catch(VerificationFailure unavailable){/* Durable state retained; no private data logged. Next configured run retries. */}}
}

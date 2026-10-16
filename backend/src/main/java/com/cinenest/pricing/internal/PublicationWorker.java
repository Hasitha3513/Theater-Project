package com.cinenest.pricing.internal;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Configuration
@EnableScheduling
class PublicationScheduling {}
@Component
@ConditionalOnProperty(name="cinenest.pricing.publication-worker.enabled",havingValue="true",matchIfMissing=true)
class PublicationWorker {
 private final PublicationService service;
 PublicationWorker(PublicationService service){this.service=service;}
 @Scheduled(fixedDelayString="${cinenest.pricing.publication-worker.poll-ms:5000}")
 void reconcile(){for(var id:service.due())try{service.activate(id);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(PublicationWorker.class).warn("Publication reconciliation failed; durable work retained for retry");}}
}

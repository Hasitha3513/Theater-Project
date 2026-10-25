package com.cinenest.payments.internal;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Explicit opt-in within the existing synthetic profile. No production configuration is supplied. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="cinenest.bank.local-docker.enabled",havingValue="true")
class LocalDockerEvidenceConfiguration {
    private final Environment env;
    LocalDockerEvidenceConfiguration(Environment env){this.env=env;
        if(!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation"))||!env.getRequiredProperty("spring.datasource.url").matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/receipt_qualification"))throw LocalEvidenceReceiptJournal.unavailable();
    }
    private String value(String key){return env.getRequiredProperty("cinenest.bank.local-docker."+key);}
    @Bean(destroyMethod="close") LocalEvidenceReceiptJournal localReceiptJournal(){return new LocalEvidenceReceiptJournal(Path.of(value("journal")),UUID.fromString(value("namespace")),Base64.getDecoder().decode(env.getRequiredProperty("cinenest.bank.encryption-key")));}
    @Bean @Primary PrivateEvidenceStore localDockerEvidenceStore(LocalEvidenceReceiptJournal journal){return new LocalS3EvidenceStore(URI.create(value("endpoint")),value("bucket"),value("access"),value("secret"),journal);}
    @Bean @Primary BankEvidenceProcessor localDockerEvidenceProcessor(SyntheticEvidenceProcessor synthetic){return new ClamAvEvidenceProcessor(synthetic,new ClamAvSocketClient(Path.of(value("socket")),Integer.parseInt(env.getProperty("cinenest.bank.local-docker.scan-seconds","60"))));}
}

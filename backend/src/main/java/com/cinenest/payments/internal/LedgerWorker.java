package com.cinenest.payments.internal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="cinenest.bank.ledger.worker-enabled",havingValue="true")
class LedgerWorker {
 private final LedgerService ledger;LedgerWorker(LedgerService ledger){this.ledger=ledger;}
 @Scheduled(fixedDelayString="${cinenest.bank.ledger.worker-delay-ms}") void run(){ledger.reconcile();}
}

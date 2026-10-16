package com.cinenest.booking.internal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="cinenest.booking.worker.enabled",havingValue="true",matchIfMissing=true)
class InventoryWorker {
    private final InventoryService service;
    InventoryWorker(InventoryService service) {this.service=service;}
    @Scheduled(fixedDelayString="${cinenest.booking.worker.poll-ms:5000}")
    void reconcile() {
        try {for(var venue:service.pendingVenues())service.reconcile(venue);}
        catch(RuntimeException unavailable) {org.slf4j.LoggerFactory.getLogger(InventoryWorker.class).warn("Inventory reconciliation failed; durable holds retained for retry");}
    }
}

package com.cinenest.booking.internal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="cinenest.booking.worker.enabled",havingValue="true",matchIfMissing=true)
class InventoryWorker {
    private final InventoryService service;private final AmendmentService amendments;
    InventoryWorker(InventoryService service,AmendmentService amendments) {this.service=service;this.amendments=amendments;}
    @Scheduled(fixedDelayString="${cinenest.booking.worker.poll-ms:5000}")
    void reconcile() {
        try {amendments.maintenance();for(var venue:service.pendingVenues())service.reconcile(venue);}
        catch(RuntimeException unavailable) {org.slf4j.LoggerFactory.getLogger(InventoryWorker.class).warn("Inventory reconciliation failed; durable holds retained for retry");}
    }
}

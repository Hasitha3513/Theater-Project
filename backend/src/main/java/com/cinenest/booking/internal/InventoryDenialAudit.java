package com.cinenest.booking.internal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
class InventoryDenialAudit {
    private final InventoryRepository repository;
    InventoryDenialAudit(InventoryRepository repository) {this.repository=repository;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void denied(UUID actor) {repository.audit(actor,null,"DENIED",null,null);}
}

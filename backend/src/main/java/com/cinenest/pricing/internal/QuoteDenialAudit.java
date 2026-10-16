package com.cinenest.pricing.internal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
class QuoteDenialAudit {
    private final QuoteRepository store;
    QuoteDenialAudit(QuoteRepository store){this.store=store;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void denied(UUID actor){store.audit(actor,null,null,"DENIED");}
}

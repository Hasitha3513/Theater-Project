package com.cinenest.pricing.internal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
class DeniedDraftAudit {
 private final DraftRepository repository;
 DeniedDraftAudit(DraftRepository repository){this.repository=repository;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void record(UUID actor){repository.audit(actor,null,null,"DENIED",false,null,null,"Access denied");}
}

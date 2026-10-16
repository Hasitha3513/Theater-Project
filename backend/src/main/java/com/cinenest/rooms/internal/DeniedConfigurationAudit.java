package com.cinenest.rooms.internal;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeniedConfigurationAudit {
    private final RoomRepository repository;
    DeniedConfigurationAudit(RoomRepository repository){this.repository=repository;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID actor){repository.audit(actor,null,null,"REQUEST","DENIED",false,null,null);}
}

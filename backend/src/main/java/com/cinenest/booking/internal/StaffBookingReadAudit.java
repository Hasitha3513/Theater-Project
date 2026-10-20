package com.cinenest.booking.internal;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
class StaffBookingReadAudit {
 private final StaffBookingReadStore store;
 StaffBookingReadAudit(StaffBookingReadStore store){this.store=store;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void refused(UUID actor,UUID venue,UUID booking,String action,String reason,UUID correlation){store.audit(actor,venue,booking,action,"DENY",reason,correlation);}
}

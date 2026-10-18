package com.cinenest.access.internal;
import com.cinenest.access.DeviceAccess;import com.cinenest.booking.BookingAccess;import com.cinenest.identity.ScopedAuthorization;import com.cinenest.payments.AccessFunding;import com.cinenest.verification.BankEligibility;
import org.springframework.stereotype.Component;import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;import java.time.*;
@Component
class DeviceAccessAdapter implements DeviceAccess {
 private final AccessRepository repo;private final AccessPolicy policy;private final ScopedAuthorization auth;private final AccessFunding funding;private final BankEligibility verification;
 DeviceAccessAdapter(AccessRepository r,AccessPolicy p,ScopedAuthorization a,AccessFunding f,BankEligibility v){repo=r;policy=p;auth=a;funding=f;verification=v;}
 private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");}
 public void lock(UUID booking){transaction();repo.lock(booking);}
 public boolean eligible(BookingAccess.Context b){transaction();policy.configured();
  if(!auth.eligible(b.owner(),true)||!funding.eligible(b.request(),b.owner(),b.venue(),b.amount(),b.currency())||!verification.eligible(b.owner(),b.quote(),b.venue()))return false;
  var c=repo.latest(b.booking());if(c.isEmpty())return false;var r=c.get();var w=repo.work((UUID)r.get("credential_id"));
  return Set.of("SCHEDULED","ACTIVE","CONSUMED").contains(r.get("state"))&&((Number)r.get("booking_revision")).longValue()==b.revision()&&b.venue().equals(r.get("venue_id"))&&b.room().equals(r.get("room_id"))&&b.owner().equals(r.get("owner_id"))&&time(r.get("starts_at")).equals(b.start())&&time(r.get("ends_at")).equals(b.end())&&"SIMULATOR_INSTALLED".equals(w.get("state"))&&w.get("operation_id").equals(w.get("evidence_operation"))&&Objects.equals(w.get("evidence_version"),r.get("version"));
 }
 private static Instant time(Object x){return x instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)x).toInstant();}
}

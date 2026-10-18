package com.cinenest.access.internal;
import com.cinenest.booking.AmendmentGuards;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class AmendmentAccessAdapter implements AmendmentGuards.Access {
 private final AccessRepository repository;
 AmendmentAccessAdapter(AccessRepository repository){this.repository=repository;}
 private static void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");}
 public void lock(UUID booking){transaction();repository.lock(booking);}
 public boolean neverIssued(UUID booking){transaction();return repository.latest(booking).isEmpty();}
}

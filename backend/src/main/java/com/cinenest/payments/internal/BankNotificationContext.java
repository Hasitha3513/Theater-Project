package com.cinenest.payments.internal;
import com.cinenest.notifications.NotificationContext;import com.cinenest.identity.ScopedAuthorization;
import java.util.UUID;import org.springframework.stereotype.Component;
@Component
class BankNotificationContext implements NotificationContext {
 private final BankRepository repo;private final ScopedAuthorization auth;
 BankNotificationContext(BankRepository repo,ScopedAuthorization auth){this.repo=repo;this.auth=auth;}
 public String source(){return "PAYMENTS";}
 public boolean current(UUID resource,long revision){return repo.request(resource,false).map(r->r.revision()==revision).orElse(false);}
 public boolean staffMayRead(UUID actor,UUID venue){return auth.permits(actor,venue,"bank.claim.review",false)&&auth.permits(actor,venue,"bank.evidence.read",false);}
}

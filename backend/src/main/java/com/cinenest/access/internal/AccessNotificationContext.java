package com.cinenest.access.internal;
import com.cinenest.notifications.NotificationContext;import com.cinenest.identity.ScopedAuthorization;
import java.util.UUID;import org.springframework.stereotype.Component;
@Component
class AccessNotificationContext implements NotificationContext {
 private final AccessRepository repo;private final ScopedAuthorization auth;
 AccessNotificationContext(AccessRepository repo,ScopedAuthorization auth){this.repo=repo;this.auth=auth;}
 public String source(){return "ACCESS";}
 public boolean current(UUID resource,long revision){return repo.credential(resource).map(r->((Number)r.get("revision")).longValue()==revision).orElse(false);}
 public boolean staffMayRead(UUID actor,UUID venue){return auth.permits(actor,venue,"access.synthetic.review",false);}
}

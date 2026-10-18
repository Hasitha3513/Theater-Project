package com.cinenest.identity.internal;
import com.cinenest.identity.NotificationRecipients;
import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
@Component
class DatabaseNotificationRecipients implements NotificationRecipients {
 private final JdbcClient jdbc; private final ScopedAuthorization auth;
 DatabaseNotificationRecipients(JdbcClient jdbc,ScopedAuthorization auth){this.jdbc=jdbc;this.auth=auth;}
 public Set<UUID> bankReviewers(UUID venue){
  var result=new HashSet<UUID>();
  for(UUID actor:jdbc.sql("SELECT DISTINCT account_id FROM cinenest_identity.access_grant WHERE venue_id=:v AND permission='bank.claim.review' AND scope_kind='VENUE'").param("v",venue).query(UUID.class).list())
   if(auth.permits(actor,venue,"bank.claim.review",false)&&auth.permits(actor,venue,"bank.evidence.read",false))result.add(actor);
  return Set.copyOf(result);
 }
}

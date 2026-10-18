package com.cinenest.notifications.internal;
import java.util.UUID;import java.util.Set;import org.springframework.stereotype.Component;
@Component
class LocalNotificationCapture implements NotificationCapture {
 public Result capture(UUID operation,long fence,String fixture){
  if(operation==null||fence<=0||!Set.of("ACCEPTED","FAILED","UNKNOWN_ACCEPTED","UNKNOWN_FAILED").contains(fixture))throw new NotificationFailure(400);
  String evidence=fixture.endsWith("FAILED")?"FAILED":"ACCEPTED";
  return new Result(fixture.startsWith("UNKNOWN")?"DELIVERY_UNKNOWN":fixture.equals("FAILED")?"DELIVERY_FAILED":"SIMULATOR_ACCEPTED",evidence);
 }
}

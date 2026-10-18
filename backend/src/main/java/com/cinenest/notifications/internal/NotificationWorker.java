package com.cinenest.notifications.internal;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** No network transport. Dispatch occurs after the source transaction; durable operation/fence survives restart. */
@Component
@ConditionalOnProperty(name="cinenest.notifications.worker-enabled",havingValue="true")
class NotificationWorker {
 private final NotificationCapture capture;private final NotificationService service;private final NotificationRepository repo;
 NotificationWorker(NotificationService service,NotificationRepository repo,NotificationCapture capture,NotificationPolicy policy){this.service=service;this.repo=repo;this.capture=capture;policy.positive("worker-delay-ms");}
 @Scheduled(fixedDelayString="${cinenest.notifications.worker-delay-ms}") public void tick(){
  service.resolve();for(var op:repo.pending()){var r=service.claim(op);if(r.isEmpty())continue;var result=capture.capture((java.util.UUID)r.get("operation_id"),((Number)r.get("fence")).longValue(),r.get("fixture").toString());service.finish(r,result.outcome(),result.evidence());}
 }
}

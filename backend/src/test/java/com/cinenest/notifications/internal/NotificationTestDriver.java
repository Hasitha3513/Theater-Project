package com.cinenest.notifications.internal;
import java.util.*;import org.springframework.context.ApplicationContext;
public final class NotificationTestDriver {
 private NotificationTestDriver(){}
 public static void resolve(ApplicationContext c){c.getBean(NotificationService.class).resolve();}
 public static void tick(ApplicationContext c){new NotificationWorker(c.getBean(NotificationService.class),c.getBean(NotificationRepository.class),c.getBean(NotificationCapture.class),c.getBean(NotificationPolicy.class)).tick();}
 public static Map<String,Object> claim(ApplicationContext c,UUID op){return c.getBean(NotificationService.class).claim(op);}
 public static void finish(ApplicationContext c,Map<String,Object> r){c.getBean(NotificationService.class).finish(r,"SIMULATOR_ACCEPTED","ACCEPTED");}
}

package com.cinenest.notifications.internal;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
class NotificationPolicy {
 private final Environment env; NotificationPolicy(Environment env){this.env=env;}
 int positive(String name){Integer n=env.getProperty("cinenest.notifications."+name,Integer.class);if(n==null||n<=0||n>10000000)throw new NotificationFailure(503);return n;}
 Set<UUID> accounts(){try{var set=new HashSet<UUID>();for(String s:env.getRequiredProperty("cinenest.notifications.synthetic-accounts").split(","))set.add(UUID.fromString(s.trim()));if(set.isEmpty())throw new IllegalArgumentException();return set;}catch(RuntimeException e){throw new NotificationFailure(503);}}
 void configured(){try{if(!env.getProperty("cinenest.notifications.enabled",Boolean.class,false)||!"IN_APP_SYNTHETIC".equals(env.getProperty("cinenest.notifications.channel"))||!"LOCAL_CAPTURE_SIMULATOR".equals(env.getProperty("cinenest.notifications.adapter")))throw new NotificationFailure(503);for(String n:List.of("expiry-seconds","retention-seconds","attempt-limit","retry-seconds","lease-seconds","rate-count","rate-seconds"))positive(n);if(positive("retention-seconds")<positive("expiry-seconds"))throw new NotificationFailure(503);accounts();}catch(RuntimeException e){throw new NotificationFailure(503);}}
}

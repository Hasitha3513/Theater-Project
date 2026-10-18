package com.cinenest.iot.internal;
import org.springframework.stereotype.Component;import org.springframework.core.env.Environment;
@Component
class IotPolicy {
 private final Environment env;IotPolicy(Environment env){this.env=env;}
 void configured(){if(!env.getProperty("cinenest.iot.enabled",Boolean.class,false)||!"SYNTHETIC_LIGHTING".equals(env.getProperty("cinenest.iot.mode")))throw new IotFailure(503);for(String key:java.util.List.of("ttl-seconds","freshness-seconds","rate-count","rate-seconds","attempt-limit"))positive(key);}
 int positive(String key){try{int v=env.getRequiredProperty("cinenest.iot."+key,Integer.class);if(v<=0)throw new IllegalArgumentException();return v;}catch(RuntimeException e){throw new IotFailure(503);}}
}

package com.cinenest.iot.internal;
import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.scheduling.annotation.*;import org.springframework.core.env.Environment;
/** Durable expiry only: never unsolicited dispatch or UNKNOWN retry. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="cinenest.iot.worker-enabled",havingValue="true")
class IotWorker {
 private final IotService service;IotWorker(IotService s,Environment e){service=s;if(e.getRequiredProperty("cinenest.iot.worker-delay-ms",Long.class)<=0)throw new IllegalStateException("Positive synthetic expiry delay required");}
 @Scheduled(fixedDelayString="${cinenest.iot.worker-delay-ms}")void run(){service.expire();}
}

package com.cinenest.booking.internal;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
/** Durable ACTIVE rows are the work queue. Terminal transitions are safe under multiple workers. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="cinenest.booking.extension-reservations.worker-enabled",havingValue="true",matchIfMissing=true)
class ExtensionReservationWorker {
 private final ExtensionReservationService service;ExtensionReservationWorker(ExtensionReservationService service){this.service=service;}
 @Scheduled(fixedDelayString="${cinenest.booking.extension-reservations.poll-ms:5000}") void tick(){try{service.maintenance();}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(ExtensionReservationWorker.class).warn("Extension cleanup unavailable;durable coverage retained for retry");}}
}

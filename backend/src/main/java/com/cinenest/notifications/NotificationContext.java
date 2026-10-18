package com.cinenest.notifications;
import java.util.UUID;
/** Implemented by each source owner. Never exposes evidence, money or credentials. */
public interface NotificationContext {
 String source();
 boolean current(UUID resource,long revision);
 boolean staffMayRead(UUID actor,UUID venue);
}

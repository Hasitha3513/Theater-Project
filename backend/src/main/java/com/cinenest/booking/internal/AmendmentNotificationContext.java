package com.cinenest.booking.internal;
import com.cinenest.notifications.NotificationContext;import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;
@Component
class AmendmentNotificationContext implements NotificationContext {
 private final JdbcClient jdbc;AmendmentNotificationContext(JdbcClient jdbc){this.jdbc=jdbc;}
 public String source(){return "BOOKING";}
 public boolean current(UUID resource,long revision){return jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='CONFIRMED' AND committed_revision=:r) OR EXISTS(SELECT 1 FROM cinenest_booking.booking_cancellation WHERE booking_id=:b AND notification_revision=:r)").param("b",resource).param("r",revision).query(Boolean.class).single();}
 public boolean staffMayRead(UUID actor,UUID venue){return false;}
}

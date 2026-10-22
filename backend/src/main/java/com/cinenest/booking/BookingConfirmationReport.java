package com.cinenest.booking;
import java.time.*;import java.util.*;
/** Booking owns the complete snapshot and durable, attributable disclosure audit. No business commands. */
public interface BookingConfirmationReport {
 String VERSION="SYNTHETIC_BOOKING_CONFIRMATIONS_V1";
 record Day(LocalDate date,String count) {}
 record Room(UUID roomId,String count) {}
 record Snapshot(UUID venueId,UUID roomId,LocalDate from,LocalDate to,String timezone,Instant observedAt,
  String metricVersion,String cohortBasis,String state,boolean synthetic,boolean complete,String cancellationStatistics,
  String total,List<Day> daily,List<Room> rooms) {}
 final class Unavailable extends RuntimeException {public final int status;public Unavailable(int status){super("Report unavailable");this.status=status;}}
 Snapshot read(UUID actor,UUID venue,UUID room,LocalDate from,LocalDate to,UUID correlation);
 void denied(UUID actor,UUID venue,UUID correlation,String reason);
}

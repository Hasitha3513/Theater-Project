package com.cinenest.booking;
import java.time.*;import java.util.*;
/** Aggregate-only current purchased revision observation; durable disclosure audit is Booking-owned. */
public interface PopularSessionStarts {
 String VERSION="SYNTHETIC_POPULAR_SESSION_STARTS_V1", BANDS="LOCAL_HOURLY_24_V1";
 record Bucket(String bandId,int startHour,int endHour,String count) {}
 record Snapshot(String metricId,String metricVersion,String bandDefinitionVersion,UUID venueId,UUID roomId,LocalDate from,LocalDate to,String timezone,Instant observedAt,String revisionBasis,String cohortBasis,boolean synthetic,boolean complete,String total,List<Bucket> buckets) {}
 final class Unavailable extends RuntimeException {public final int status;public Unavailable(int status){super("Report unavailable");this.status=status;}}
 Snapshot read(UUID actor,UUID venue,UUID room,LocalDate from,LocalDate to,UUID correlation);
 void denied(UUID actor,UUID venue,UUID correlation,String reason);
}

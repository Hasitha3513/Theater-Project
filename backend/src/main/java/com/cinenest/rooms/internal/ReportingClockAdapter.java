package com.cinenest.rooms.internal;
import com.cinenest.rooms.VenueReportingClock;import java.util.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;
@Component
class ReportingClockAdapter implements VenueReportingClock {
 private final JdbcClient jdbc;ReportingClockAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public Optional<String> timezone(UUID venue){return jdbc.sql("SELECT timezone FROM cinenest_rooms.venue WHERE venue_id=:v").param("v",venue).query(String.class).optional();}
}

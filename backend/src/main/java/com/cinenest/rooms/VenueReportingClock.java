package com.cinenest.rooms;
import java.util.*;
/** Venue-owned IANA timezone lookup, no physical readiness or inventory inference. */
public interface VenueReportingClock {Optional<String> timezone(UUID venue);}

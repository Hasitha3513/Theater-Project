package com.cinenest.booking.internal;
import com.cinenest.rooms.InventoryConstraints;import com.nimbusds.jose.util.JSONObjectUtils;import java.time.*;import java.util.*;
/** Pure interpretation of bulk-loaded Booking-owned extension rows. No reconciliation. */
final class ExtensionInventoryObservation {
 private ExtensionInventoryObservation(){}
 static Map<String,Object> record(Map<String,Object> row){try{if(row.get("extension_record")==null)throw new IllegalStateException();var e=JSONObjectUtils.parse(row.get("extension_record").toString());
  if(!"ACTIVE".equals(e.get("state"))||!Objects.toString(row.get("venue_id")).equals(e.get("venue_id"))||!Objects.toString(row.get("room_id")).equals(e.get("room_id"))||!ExtensionReservationService.instant(row.get("session_start")).equals(ExtensionReservationService.instant(e.get("original_until")))||!ExtensionReservationService.instant(row.get("blocked_until")).equals(ExtensionReservationService.instant(e.get("proposed_until")))||((Number)row.get("buffer_minutes")).intValue()!=0)throw new IllegalStateException();return e;
 }catch(Exception invalid){throw new IllegalStateException("Inconsistent extension coverage");}}
 static boolean expired(Map<String,Object> e,Instant now){return !now.isBefore(ExtensionReservationService.instant(e.get("expires_at")));}
 static boolean pending(Map<String,Object> row,InventoryConstraints.Facts f,Instant now){var e=record(row);return expired(e,now)||!ExtensionReservationService.valid(e,f);}
}

package com.cinenest.booking.internal;
import com.cinenest.rooms.OperationsEvidence;
import com.cinenest.booking.BookingAccess;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
class TurnoverBookingEvidence implements OperationsEvidence {
 private final BookingAccess bookings;
 private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
 TurnoverBookingEvidence(BookingAccess bookings,org.springframework.jdbc.core.simple.JdbcClient jdbc){this.bookings=bookings;this.jdbc=jdbc;}
 public Optional<Checkout> checkout(UUID event,UUID venue,UUID booking){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Transaction required");
  var current=bookings.lock(booking);
  if(current.isEmpty()||!current.get().venue().equals(venue))return Optional.empty();
  return jdbc.sql("SELECT event_id,booking_id,venue_id,room_id,booking_revision FROM cinenest_booking.synthetic_staff_session_event WHERE event_id=:e AND booking_id=:b AND venue_id=:v AND event_type='CHECKOUT'")
   .param("e",event).param("b",booking).param("v",venue)
   .query((rs,n)->new Checkout(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getLong(5))).optional()
   .filter(row->row.bookingRevision()==current.get().revision()&&row.room().equals(current.get().room()));
 }
 public String source(){return "BOOKING";}
 public Optional<Fact> inspect(UUID id,UUID venue,UUID room){return bookings.lock(id).filter(b->b.venue().equals(venue)&&b.room().equals(room)).map(b->new Fact(id,b.revision(),"SIMULATED_CONFIRMED"));}
}

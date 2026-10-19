package com.cinenest.booking.internal;
import com.cinenest.rooms.OperationsEvidence;
import com.cinenest.booking.BookingAccess;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
class TurnoverBookingEvidence implements OperationsEvidence {
 private final BookingAccess bookings;TurnoverBookingEvidence(BookingAccess bookings){this.bookings=bookings;}
 public String source(){return "BOOKING";}
 public Optional<Fact> inspect(UUID id,UUID venue,UUID room){return bookings.lock(id).filter(b->b.venue().equals(venue)&&b.room().equals(room)).map(b->new Fact(id,b.revision(),"SIMULATED_CONFIRMED"));}
}

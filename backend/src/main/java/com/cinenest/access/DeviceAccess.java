package com.cinenest.access;
import com.cinenest.booking.BookingAccess;
import java.util.UUID;
/** Access-owned current synthetic facts. Caller transaction required; lock before Identity/Booking locks. */
public interface DeviceAccess {
 void lock(UUID booking);
 boolean eligible(BookingAccess.Context booking);
}

package com.cinenest.access.internal;

import com.cinenest.booking.BookingAccess;
import com.cinenest.identity.ScopedAuthorization;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffBookingAccessTest {
    final AccessRepository repo=mock(AccessRepository.class);
    final AccessPolicy policy=mock(AccessPolicy.class);
    final BookingAccess bookings=mock(BookingAccess.class);
    final ScopedAuthorization auth=mock(ScopedAuthorization.class);
    final com.cinenest.notifications.NotificationFacts notifications=mock(com.cinenest.notifications.NotificationFacts.class);
    final AccessService service=new AccessService(repo,policy,bookings,null,auth,null,null,notifications);
    final UUID actor=UUID.randomUUID(),venue=UUID.randomUUID(),booking=UUID.randomUUID(),owner=UUID.randomUUID(),room=UUID.randomUUID(),credential=UUID.randomUUID();
    final Instant start=Instant.parse("2030-01-01T10:00:00Z"),end=start.plusSeconds(3600);
    Map<String,Object> row;
    @BeforeEach void setup(){
        when(policy.positive("rate-seconds")).thenReturn(60);when(policy.positive("rate-count")).thenReturn(100);
        when(auth.permits(actor,venue,"access.synthetic.review",true)).thenReturn(true);
        when(bookings.lock(booking)).thenReturn(Optional.of(new BookingAccess.Context(booking,UUID.randomUUID(),owner,venue,room,UUID.randomUUID(),1,100,"LKR",start,end,"Asia/Colombo")));
        when(repo.now()).thenReturn(start);
        row=new HashMap<>(Map.of("credential_id",credential,"booking_id",booking,"owner_id",owner,"venue_id",venue,"room_id",room,"booking_revision",1L,"version",1L,"revision",0L,"state","SCHEDULED","timezone","Asia/Colombo"));
        row.put("starts_at",OffsetDateTime.ofInstant(start,ZoneOffset.UTC));row.put("ends_at",OffsetDateTime.ofInstant(end,ZoneOffset.UTC));
        when(repo.bookingCredentials(booking)).thenReturn(List.of(row));
        when(repo.bookingWork(credential)).thenReturn(Optional.of(Map.of("credential_id",credential,"credential_version",1L,"operation_id",UUID.randomUUID(),"state","UNKNOWN")));
    }
    void conflict(){assertThatThrownBy(()->service.staffBooking(actor,venue,booking)).isInstanceOfSatisfying(AccessFailure.class,f->assertThat(f.status).isEqualTo(409));}
    @Test void effectiveWindowIsHalfOpenAndObservational(){
        when(repo.now()).thenReturn(start.minusNanos(1),start,end);
        assertThat(service.staffBooking(actor,venue,booking).state()).isEqualTo("SCHEDULED");
        assertThat(service.staffBooking(actor,venue,booking).state()).isEqualTo("ACTIVE");
        assertThat(service.staffBooking(actor,venue,booking).state()).isEqualTo("EXPIRED");
        assertThat(row.get("state")).isEqualTo("SCHEDULED");
        verify(repo,never()).save(any());verify(repo,never()).create(any());verify(repo,never()).workState(any(),any(),anyBoolean(),anyBoolean());
        verify(repo,never()).command(any(),any(),any(),any(),any(),any());verify(repo,never()).attempt(any(),any(),anyBoolean());verifyNoInteractions(notifications);
    }
    @Test void currentReviewRecheckedAfterCredentialWait(){
        when(auth.permits(actor,venue,"access.synthetic.review",true)).thenReturn(true,false);
        assertThatThrownBy(()->service.staffBooking(actor,venue,booking)).isInstanceOfSatisfying(AccessFailure.class,f->assertThat(f.status).isEqualTo(403));
        verify(repo,never()).bookingWork(any());verify(repo,never()).audit(any(),any(),any(),any(),any(),any());
    }
    @Test void authorityRecheckedAfterWorkWait(){
        when(auth.permits(actor,venue,"access.synthetic.review",true)).thenReturn(true,true,false);
        assertThatThrownBy(()->service.staffBooking(actor,venue,booking)).isInstanceOfSatisfying(AccessFailure.class,f->assertThat(f.status).isEqualTo(403));
        verify(repo,never()).now();verify(repo,never()).audit(any(),any(),any(),any(),any(),any());
    }
    @Test void duplicateAndContradictoryActiveVersionsFailClosed(){
        when(repo.bookingCredentials(booking)).thenReturn(List.of(row,row));conflict();
        var older=new HashMap<>(row);older.put("version",0L);when(repo.bookingCredentials(booking)).thenReturn(List.of(row,older));conflict();
        row.put("version",2L);older.put("version",1L);when(repo.bookingCredentials(booking)).thenReturn(List.of(row,older));conflict();
    }
    @Test void mismatchedBindingsFailClosed(){
        for(String key:List.of("owner_id","venue_id","room_id","booking_id")){
            Object old=row.put(key,UUID.randomUUID());conflict();row.put(key,old);
        }
        row.put("booking_revision",2L);conflict();row.put("booking_revision",1L);
        row.put("ends_at",OffsetDateTime.ofInstant(end.plusSeconds(1),ZoneOffset.UTC));conflict();
    }
    @Test void missingAndMismatchedWorkFailClosed(){
        when(repo.bookingWork(credential)).thenReturn(Optional.empty());conflict();
        when(repo.bookingWork(credential)).thenReturn(Optional.of(Map.of("credential_id",credential,"credential_version",2L,"operation_id",UUID.randomUUID(),"state","UNKNOWN")));conflict();
    }
    @Test void domainTerminalStatesAreNotTimeNormalized(){
        for(String state:List.of("CONSUMED","REVOKED","SUPERSEDED","EXPIRED")){
            row.put("state",state);assertThat(service.staffBooking(actor,venue,booking).state()).isEqualTo(state);
        }
    }
    @Test void invalidPurchasedContextIsUnavailableNotUnissued(){
        when(bookings.lock(booking)).thenThrow(new IllegalStateException("Invalid synthetic purchased context"));
        assertThatThrownBy(()->service.staffBooking(actor,venue,booking)).isInstanceOfSatisfying(AccessFailure.class,f->assertThat(f.status).isEqualTo(503));
        verify(repo,never()).bookingCredentials(any());
    }
    @Test void rateLimitRefusesBeforePurchasedRead(){
        when(repo.rate(actor,60)).thenReturn(100);
        assertThatThrownBy(()->service.staffBooking(actor,venue,booking)).isInstanceOfSatisfying(AccessFailure.class,f->assertThat(f.status).isEqualTo(429));
        verifyNoInteractions(bookings);
    }
    @Test void latestPositiveBigintVersionHasNoInventedUpperLimit(){
        row.put("version",Long.MAX_VALUE);
        when(repo.bookingWork(credential)).thenReturn(Optional.of(Map.of("credential_id",credential,"credential_version",Long.MAX_VALUE,"operation_id",UUID.randomUUID(),"state","UNKNOWN")));
        assertThat(service.staffBooking(actor,venue,booking).credential().version()).isEqualTo(Long.MAX_VALUE);
    }
    @Test void emptyLookupHasNoCredentialOrActions(){
        when(repo.bookingCredentials(booking)).thenReturn(List.of());var result=service.staffBooking(actor,venue,booking);
        assertThat(result.state()).isEqualTo("NOT_ISSUED");assertThat(result.credential()).isNull();assertThat(result.credentialExists()).isFalse();assertThat(result.permittedActions()).isEmpty();
        verify(repo,never()).bookingWork(any());
    }
}

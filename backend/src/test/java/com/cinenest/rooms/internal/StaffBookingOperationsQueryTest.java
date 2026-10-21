package com.cinenest.rooms.internal;
import com.cinenest.rooms.OperationsBookingContext;
import com.cinenest.identity.ScopedAuthorization;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.BeforeEach;import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class StaffBookingOperationsQueryTest {
 final OperationsBookingContext booking=mock(OperationsBookingContext.class);final TurnoverRepository repo=mock(TurnoverRepository.class);final ScopedAuthorization auth=mock(ScopedAuthorization.class);
 final StaffBookingOperationsQuery query=new StaffBookingOperationsQuery(booking,repo,auth);
 final UUID actor=UUID.randomUUID(),venue=UUID.randomUUID(),id=UUID.randomUUID(),room=UUID.randomUUID(),caseId=UUID.randomUUID();
 final Instant start=Instant.parse("2030-01-01T00:00:00Z");OperationsBookingContext.Context current;Map<String,Object> row;
 @BeforeEach void setup(){
  current=new OperationsBookingContext.Context(id,venue,room,2,start,start.plusSeconds(3600),"Asia/Colombo");when(booking.inspect(id,venue,null)).thenReturn(Optional.of(current));
  when(auth.permits(actor,venue,"operations.synthetic.read",false)).thenReturn(true);
  row=new HashMap<>(Map.of("case_id",caseId,"booking_id",id,"venue_id",venue,"room_id",room,"booking_revision",2L,"revision",0L,"state","OPEN","assignment","UNASSIGNED","created_at",java.sql.Timestamp.from(start),"updated_at",java.sql.Timestamp.from(start)));row.put("categories","[\"MAINTENANCE\"]");
  when(repo.bookingCases(id,0)).thenReturn(new TurnoverRepository.BookingObservation(start,List.of(row)));
 }
 void fails(int status){assertThatThrownBy(()->query.read(actor,venue,id,0)).isInstanceOfSatisfying(RoomFailure.class,f->assertThat(f.status).isEqualTo(status));}
 @Test void noGrantMeansNoProtectedLookup(){when(auth.permits(actor,venue,"operations.synthetic.read",false)).thenReturn(false);fails(403);verifyNoInteractions(booking,repo);}
 @Test void currentProjectionIsReadOnlyAndNotPhysical(){var r=query.read(actor,venue,id,0);assertThat(r.currentBookingRevision()).isEqualTo(2);assertThat(r.items().getFirst().recordedExceptionCategories()).containsExactly("MAINTENANCE");assertThat(r.occupancy()).isEqualTo("UNKNOWN");assertThat(r.readiness()).isEqualTo("UNVERIFIED");assertThat(r.inventoryChanged()).isFalse();verify(repo,never()).lock(any());verify(repo,never()).update(any(),any(),any(),any());verify(repo,never()).create(any(),any(),any(),any(),any(),any(),any(),any());}
 @Test void historicalContextUsesItsOwnRoomAndWindowWithoutEnablingRoomChanges(){UUID oldRoom=UUID.randomUUID();var old=new OperationsBookingContext.Context(id,venue,oldRoom,1,start.minusSeconds(7200),start.minusSeconds(3600),"Asia/Colombo");row.put("booking_revision",1L);row.put("room_id",oldRoom);when(booking.inspect(id,venue,1L)).thenReturn(Optional.of(old));var r=query.read(actor,venue,id,0);assertThat(r.currentRoomId()).isEqualTo(room);assertThat(r.items().getFirst().roomId()).isEqualTo(oldRoom);assertThat(r.items().getFirst().capturedSessionStart()).isEqualTo(old.sessionStart());}
 @Test void missingHistoricalRevisionFailsClosed(){row.put("booking_revision",1L);fails(409);}
 @Test void roomOrVenueCorruptionFailsClosed(){row.put("room_id",UUID.randomUUID());fails(409);row.put("room_id",room);row.put("venue_id",UUID.randomUUID());fails(409);}
 @Test void otherBookingInProjectionFailsClosed(){row.put("booking_id",UUID.randomUUID());fails(409);}
 @Test void staleBookingRecheckIsConflict(){when(booking.inspect(id,venue,null)).thenReturn(Optional.of(current),Optional.of(new OperationsBookingContext.Context(id,venue,room,3,start,start.plusSeconds(3600),"Asia/Colombo")));fails(409);}
 @Test void revokedGrantAfterQueryIsDenied(){when(auth.permits(actor,venue,"operations.synthetic.read",false)).thenReturn(true,false);fails(403);}
 @Test void invalidStatesCategoriesAndAssignmentFailClosed(){row.put("state","READY");fails(409);row.put("state","ACKNOWLEDGED");fails(409);row.put("state","OPEN");row.put("categories","[\"SAFE\"]");fails(409);}
 @Test void invalidTimestampAndRevisionFailClosed(){row.put("updated_at",java.sql.Timestamp.from(start.minusSeconds(1)));fails(409);row.put("updated_at",java.sql.Timestamp.from(start));row.put("booking_revision",0L);fails(409);}
 @Test void sourceAndDatabaseFailureNeverProduceEmptySuccess(){when(booking.inspect(id,venue,null)).thenThrow(new OperationsBookingContext.InvalidContext());fails(409);doReturn(Optional.of(current)).when(booking).inspect(id,venue,null);when(repo.bookingCases(id,0)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));assertThatThrownBy(()->query.read(actor,venue,id,0)).isInstanceOf(org.springframework.dao.DataAccessException.class);}
 @Test void paginationAndMissingBookingRejectBeforeData(){assertThatThrownBy(()->query.read(actor,venue,id,-1)).isInstanceOf(RoomFailure.class);verifyNoInteractions(booking,repo);when(booking.inspect(id,venue,null)).thenReturn(Optional.empty());fails(404);}
}

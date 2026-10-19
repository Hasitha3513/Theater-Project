package com.cinenest.rooms.internal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cinenest.identity.ScopedAuthorization;
class TurnoverServiceTest {
 @Test void disablingMutationsRetainsAuthorizedReadHistory(){var repository=mock(TurnoverRepository.class);var rooms=mock(RoomRepository.class);var identity=mock(ScopedAuthorization.class);UUID actor=UUID.randomUUID(),venue=UUID.randomUUID();when(identity.permits(actor,venue,"operations.synthetic.read",false)).thenReturn(true);when(repository.list(venue,0)).thenReturn(List.of());var service=new TurnoverService(repository,rooms,identity,List.of(),false,"");assertThat(service.list(actor,venue,0)).containsKey("items");assertThatThrownBy(()->service.command(actor,venue,null,"CREATE",Map.of())).isInstanceOf(RoomFailure.class).hasMessageContaining("not configured");verify(repository,never()).create(any(),any(),any(),any(),any(),any(),any(),any());}
 @Test void missingOrMalformedSyntheticChecklistCannotCreateCases(){for(String definition:List.of("","version:real certificate","V1:","V1:A,A")){var repository=mock(TurnoverRepository.class);var rooms=mock(RoomRepository.class);var identity=mock(ScopedAuthorization.class);UUID actor=UUID.randomUUID(),venue=UUID.randomUUID(),room=UUID.randomUUID();when(identity.permits(eq(actor),eq(venue),anyString(),eq(true))).thenReturn(true);when(repository.receipt(any(),any())).thenReturn(Optional.empty());when(rooms.room(venue,room)).thenReturn(Optional.of(Map.of()));var service=new TurnoverService(repository,rooms,identity,List.of(),true,definition);assertThatThrownBy(()->service.command(actor,venue,null,"CREATE",Map.of("roomId",room.toString(),"commandKey",UUID.randomUUID().toString(),"expectedVersion",0))).isInstanceOf(RoomFailure.class);verify(repository,never()).create(any(),any(),any(),any(),any(),any(),any(),any());}}
}

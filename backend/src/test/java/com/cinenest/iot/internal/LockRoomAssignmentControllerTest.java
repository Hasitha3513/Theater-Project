package com.cinenest.iot.internal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class LockRoomAssignmentControllerTest {
 final LockRoomAssignmentService service=mock(LockRoomAssignmentService.class);
 final UUID actor=UUID.randomUUID(),venue=UUID.randomUUID(),room=UUID.randomUUID();
 String root(){return "/api/admin/venues/"+venue+"/rooms/"+room+"/lock-assignments";}
 MockMvc mvc;
 @BeforeEach void setup(){when(service.run(any(),any(),any(),any(),any(),anyBoolean(),any(),any())).thenReturn(new LockRoomAssignmentService.Result(400,Map.of("customerAccessEnabled",false)));mvc=MockMvcBuilders.standaloneSetup(new LockRoomAssignmentController(service)).build();}
 @Test void exactCreateForwardsBoundedBodyAndNoStore()throws Exception{mvc.perform(post(root()).principal(()->actor.toString()).contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));verify(service).run(eq(actor),eq(venue.toString()),eq(room.toString()),isNull(),isNull(),eq(false),eq("{}".getBytes()),eq("CREATE"));}
 @Test void invalidMediaRepeatedQueryAndUnexpectedQueriesAreFlagged()throws Exception{mvc.perform(post(root()).principal(()->actor.toString()).contentType("text/plain").content("{}"));verify(service).run(any(),any(),any(),isNull(),isNull(),eq(true),any(),eq("CREATE"));mvc.perform(get(root()).principal(()->actor.toString()).param("page","0","1"));verify(service).run(any(),any(),any(),isNull(),eq("0"),eq(true),isNull(),eq("LIST"));mvc.perform(get(root()+"/"+UUID.randomUUID()).principal(()->actor.toString()).param("secret","x"));verify(service).run(any(),any(),any(),anyString(),isNull(),eq(true),isNull(),eq("DETAIL"));}
}

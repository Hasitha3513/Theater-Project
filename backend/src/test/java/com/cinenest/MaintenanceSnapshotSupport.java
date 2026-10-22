package com.cinenest;
import com.cinenest.rooms.InventoryConstraints;import com.cinenest.identity.ScopedAuthorization;
import java.util.*;import org.springframework.context.ApplicationContext;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.util.LinkedMultiValueMap;
/** Test-only barrier after actual Rooms observation pins the real PostgreSQL snapshot. */
final class MaintenanceSnapshotSupport {
 static Object read(ApplicationContext context,JdbcClient jdbc,PlatformTransactionManager tx,UUID actor,UUID venue,UUID room,String start,String end,Runnable afterFacts){
  var delegate=context.getBean(InventoryConstraints.class);
  InventoryConstraints wrapper=new InventoryConstraints(){public Optional<UUID> venue(UUID r){return delegate.venue(r);}public void lock(UUID v){throw new AssertionError("Impact must not lock inventory");}public Optional<Facts> inspect(UUID r){throw new AssertionError("Impact must observe");}public Optional<Facts> observe(UUID r){var facts=delegate.observe(r);afterFacts.run();return facts;}};
  try{var type=Class.forName("com.cinenest.booking.internal.MaintenanceImpactQuery");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);var service=constructor.newInstance(jdbc,wrapper,context.getBean(ScopedAuthorization.class),tx);var query=new LinkedMultiValueMap<String,String>();query.add("startLocal",start);query.add("endLocal",end);return org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"read",actor,venue.toString(),room.toString(),query,UUID.randomUUID());}catch(ReflectiveOperationException e){throw new AssertionError(e);}
 }
 static Object body(Object result){org.assertj.core.api.Assertions.assertThat((Integer)org.springframework.test.util.ReflectionTestUtils.invokeMethod(result,"status")).isEqualTo(200);return org.springframework.test.util.ReflectionTestUtils.invokeMethod(result,"body");}
 static int count(Object body,String field){Object counts=org.springframework.test.util.ReflectionTestUtils.invokeMethod(body,"counts");return (Integer)org.springframework.test.util.ReflectionTestUtils.invokeMethod(counts,field);}
}

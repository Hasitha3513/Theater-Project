package com.cinenest;
import com.cinenest.rooms.InventoryConstraints;
import com.cinenest.identity.ScopedAuthorization;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
/** Test-only barrier after real Rooms reads pin the actual PostgreSQL snapshot. */
final class CalendarSnapshotSupport {
 static byte[] read(ApplicationContext context,JdbcClient jdbc,PlatformTransactionManager tx,UUID actor,UUID room,LocalDate date,Runnable afterFacts)throws Exception{
  var delegate=context.getBean(InventoryConstraints.class);
  InventoryConstraints wrapper=new InventoryConstraints(){
   public Optional<UUID> venue(UUID r){return delegate.venue(r);}public void lock(UUID v){throw new AssertionError("Calendar must not lock");}
   public Optional<Facts> inspect(UUID r){throw new AssertionError("Calendar must use observe");}
   public Optional<Facts> observe(UUID r){var facts=delegate.observe(r);afterFacts.run();return facts;}
  };
  var type=Class.forName("com.cinenest.booking.internal.InventoryCalendar");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);var service=constructor.newInstance(jdbc,wrapper,context.getBean(ScopedAuthorization.class),tx);
  return org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"read",actor,room,date,180,4);
 }
}

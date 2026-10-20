package com.cinenest.booking.internal;
import java.time.Instant;
import org.springframework.context.ApplicationContext;import org.springframework.test.util.ReflectionTestUtils;
/** Test-classpath clock control only; no production endpoint or configuration. */
public final class GuestRosterFaults {
 private GuestRosterFaults(){}
 public static Object at(ApplicationContext context,Instant now){var service=context.getBean(GuestRosterService.class);Object old=ReflectionTestUtils.getField(service,"inventory");var proxy=java.lang.reflect.Proxy.newProxyInstance(InventoryRepository.class.getClassLoader(),new Class<?>[]{InventoryRepository.class},(p,m,args)->m.getName().equals("now")?now:m.invoke(old,args));ReflectionTestUtils.setField(service,"inventory",proxy);return old;}
 public static void restore(ApplicationContext context,Object old){ReflectionTestUtils.setField(context.getBean(GuestRosterService.class),"inventory",old);}
}

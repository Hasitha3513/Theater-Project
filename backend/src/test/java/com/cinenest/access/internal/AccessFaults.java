package com.cinenest.access.internal;
import java.time.Instant;import java.lang.reflect.Proxy;
/** Test-only authoritative-clock adapter; production always reads PostgreSQL clock_timestamp. */
public final class AccessFaults {
 public static Object clock(Object bean,Instant instant)throws Exception{Object target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(bean);Object original=org.springframework.test.util.ReflectionTestUtils.getField(target,"repo");AccessRepository proxy=(AccessRepository)Proxy.newProxyInstance(AccessRepository.class.getClassLoader(),new Class<?>[]{AccessRepository.class},(p,m,a)->{if(m.getName().equals("now"))return instant;try{return m.invoke(original,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});org.springframework.test.util.ReflectionTestUtils.setField(target,"repo",proxy);return original;}
 public static void restore(Object bean,Object repo)throws Exception{org.springframework.test.util.ReflectionTestUtils.setField((Object)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(bean),"repo",repo);}
}

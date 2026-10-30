package com.cinenest.booking.internal;
import java.util.*;
import java.time.Instant;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
/** Isolated test-only transaction failure. No production fault endpoint or bean. */
public final class RoomBlockFaults {
 private RoomBlockFaults(){}
 public static AutoCloseable failAfterCreate(ApplicationContext context){
  var service=context.getBean(RoomBlocks.class);var original=(RoomBlockRepository)ReflectionTestUtils.getField(service,"repository");
  RoomBlockRepository failing=new RoomBlockRepository(){
   public Instant now(){return original.now();}
   public Optional<Block> command(UUID a,UUID k){return original.command(a,k);}
   public Optional<Block> find(UUID v,UUID r,UUID b){return original.find(v,r,b);}
   public List<Block> list(UUID v,UUID r,int p,int s){return original.list(v,r,p,s);}
   public void audit(UUID e,UUID a,UUID v,UUID r,UUID b,UUID k,String action){original.audit(e,a,v,r,b,k,action);}
   public void create(Block b,UUID e){original.create(b,e);throw new org.springframework.dao.DataAccessResourceFailureException("Synthetic mandatory audit transaction failure");}
  };
  ReflectionTestUtils.setField(service,"repository",failing);return ()->ReflectionTestUtils.setField(service,"repository",original);
 }
}

package com.cinenest.rooms.internal;
import com.cinenest.rooms.PackageConstraints;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
class PackageConstraintAdapter implements PackageConstraints {
 private final JdbcClient jdbc;
 PackageConstraintAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 @Transactional
 public Optional<Facts> inspect(UUID venue,Set<UUID> selected,boolean wide,boolean lock){
  var versions=jdbc.sql("SELECT version FROM cinenest_rooms.venue WHERE venue_id=:v"+(lock?" FOR SHARE":"")).param("v",venue).query(Long.class).list();
  if(versions.isEmpty())return Optional.empty();
  var policy=jdbc.sql("SELECT policy_id,revision,minimum_minutes FROM cinenest_rooms.minimum_session_policy WHERE venue_id=:v AND effective_from<=clock_timestamp() ORDER BY effective_from DESC LIMIT 1").param("v",venue).query((rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getLong(2),rs.getInt(3)}).optional();
  if(policy.isEmpty() || !wide && selected.isEmpty())return Optional.empty();
  var query=jdbc.sql("SELECT room_id,capacity,version FROM cinenest_rooms.room WHERE venue_id=:v"+(wide?"":" AND room_id IN (:ids)")+" ORDER BY room_id"+(lock?" FOR SHARE":"")).param("v",venue);
  if(!wide)query.param("ids",selected);
  var rooms=query.query((rs,n)->new RoomFact(rs.getObject(1,UUID.class),rs.getInt(2),rs.getLong(3),Set.copyOf(jdbc.sql("SELECT code FROM cinenest_rooms.room_facility WHERE room_id=:id").param("id",rs.getObject(1,UUID.class)).query(String.class).list()))).list();
  if(rooms.isEmpty() || !wide && rooms.size()!=selected.size())return Optional.empty();
  var p=policy.get();return Optional.of(new Facts(venue,versions.getFirst(),(UUID)p[0],(Long)p[1],(Integer)p[2],rooms));
 }
}

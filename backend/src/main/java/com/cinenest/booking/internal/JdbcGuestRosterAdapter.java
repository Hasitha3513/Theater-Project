package com.cinenest.booking.internal;
import java.util.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Repository;
@Repository
class JdbcGuestRosterAdapter implements GuestRosterStore {
 private final JdbcClient jdbc;JdbcGuestRosterAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public int purchased(UUID b){return jdbc.sql("SELECT (snapshot->>'guests')::INTEGER FROM cinenest_booking.booking_revision WHERE booking_id=:b ORDER BY revision DESC LIMIT 1").param("b",b).query(Integer.class).single();}
 public Optional<Long> revision(UUID b){return jdbc.sql("SELECT revision FROM cinenest_booking.guest_roster WHERE booking_id=:b").param("b",b).query(Long.class).optional();}
 public List<Map<String,String>> entries(UUID b){return jdbc.sql("SELECT entry_id,kind,label FROM cinenest_booking.guest_entry WHERE booking_id=:b ORDER BY CASE kind WHEN 'BOOKER' THEN 0 ELSE 1 END,created_at,entry_id").param("b",b).query((r,n)->Map.of("id",r.getObject(1).toString(),"kind",r.getString(2),"label",r.getString(3),"status","UNVERIFIED")).list();}
 public Optional<String> command(UUID a,UUID k){return jdbc.sql("SELECT payload_hash FROM cinenest_booking.guest_command WHERE actor_id=:a AND command_key=:k").param("a",a).param("k",k).query(String.class).optional();}
 public void initialize(UUID b){jdbc.sql("INSERT INTO cinenest_booking.guest_roster(booking_id) VALUES(:b)").param("b",b).update();}
 public void append(UUID b,String kind,String label,UUID owner){jdbc.sql("INSERT INTO cinenest_booking.guest_entry(entry_id,booking_id,kind,label,booker_account_id) VALUES(:id,:b,:kind,:label,:owner)").param("id",UUID.randomUUID()).param("b",b).param("kind",kind).param("label",label).param("owner",owner,java.sql.Types.OTHER).update();}
 public void advance(UUID b){jdbc.sql("UPDATE cinenest_booking.guest_roster SET revision=revision+1 WHERE booking_id=:b").param("b",b).update();}
 public void remember(UUID a,UUID k,UUID b,String hash){jdbc.sql("INSERT INTO cinenest_booking.guest_command VALUES(:a,:k,:b,:hash)").param("a",a).param("k",k).param("b",b).param("hash",hash).update();}
 public void audit(UUID a,UUID b,String action){jdbc.sql("INSERT INTO cinenest_booking.guest_audit(event_id,actor_id,booking_id,action) VALUES(:id,:a,:b,:action)").param("id",UUID.randomUUID()).param("a",a).param("b",b).param("action",action).update();}
}

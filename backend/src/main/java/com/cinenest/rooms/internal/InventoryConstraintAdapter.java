package com.cinenest.rooms.internal;

import com.cinenest.rooms.InventoryConstraints;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class InventoryConstraintAdapter implements InventoryConstraints {
    private final JdbcClient jdbc;
    InventoryConstraintAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<UUID> venue(UUID room) {
        return jdbc.sql("SELECT venue_id FROM cinenest_rooms.room WHERE room_id=:r").param("r",room).query(UUID.class).optional();
    }
    public void lock(UUID venue) {
        jdbc.sql("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=:v FOR UPDATE").param("v",venue).query(UUID.class).list();
    }
    public Optional<Facts> inspect(UUID room) {
        return jdbc.sql("""
            SELECT r.venue_id,r.room_id,v.timezone,(r.enabled AND v.enabled) enabled,r.capacity,
                   v.cleaning_buffer_minutes,
                   (v.post_close_cleaning_approved AND length(trim(v.operational_approval_reference))>0) approved,
                   v.version::text || ':' || r.version::text configuration
            FROM cinenest_rooms.room r JOIN cinenest_rooms.venue v USING(venue_id) WHERE r.room_id=:r
            """).param("r",room).query((rs,n)-> {
                UUID venue=rs.getObject("venue_id",UUID.class);
                var windows=jdbc.sql("SELECT weekday,opening_minute,closing_minute FROM cinenest_rooms.venue_operating_window WHERE venue_id=:v ORDER BY weekday")
                    .param("v",venue).query((row,i)->new Window(row.getInt(1),row.getInt(2),row.getInt(3))).list();
                var minimums=jdbc.sql("SELECT policy_id,minimum_minutes,effective_from FROM cinenest_rooms.minimum_session_policy WHERE venue_id=:v ORDER BY effective_from")
                    .param("v",venue).query((row,i)->new Minimum(row.getObject(1,UUID.class),row.getInt(2),row.getTimestamp(3).toInstant())).list();
                // Policy IDs are immutable; include the entire known schedule in the review token.
                String config=rs.getString("configuration")+":"+minimums.stream().map(m->m.id().toString()).reduce("",(a,b)->a+","+b);
                return new Facts(venue,room,rs.getString("timezone"),rs.getBoolean("enabled"),rs.getInt("capacity"),
                    (Integer)rs.getObject("cleaning_buffer_minutes"),rs.getBoolean("approved"),config,windows,minimums);
            }).optional();
    }
}

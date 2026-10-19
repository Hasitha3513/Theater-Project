package com.cinenest.iot.internal;
import com.cinenest.rooms.OperationsEvidence;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
@Component
class TurnoverIotEvidence implements OperationsEvidence {
 private final JdbcClient jdbc;TurnoverIotEvidence(JdbcClient jdbc){this.jdbc=jdbc;}
 public String source(){return "IOT";}
 public Optional<Fact> inspect(UUID id,UUID venue,UUID room){return jdbc.sql("SELECT revision,state FROM cinenest_iot.command WHERE command_id=:id AND venue_id=:v AND room_id=:r").param("id",id).param("v",venue).param("r",room).query((rs,n)->new Fact(id,rs.getLong(1),rs.getString(2))).optional();}
}

package com.cinenest.rooms.internal;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit server-operator bootstrap, disabled by default. Never callable through package administration. */
@Component
@ConditionalOnProperty(name="cinenest.rooms.minimum-bootstrap.enabled",havingValue="true")
class MinimumPolicyBootstrap implements ApplicationRunner {
 private final JdbcClient jdbc;private final ScopedAuthorization identity;private final UUID venue,actor;private final int minimum;private final Instant effective;private final String reference;
 MinimumPolicyBootstrap(JdbcClient jdbc,ScopedAuthorization identity,
  @Value("${cinenest.rooms.minimum-bootstrap.venue-id}") UUID venue,
  @Value("${cinenest.rooms.minimum-bootstrap.actor-id}") UUID actor,
  @Value("${cinenest.rooms.minimum-bootstrap.minimum-minutes}") int minimum,
  @Value("${cinenest.rooms.minimum-bootstrap.effective-from}") String effective,
  @Value("${cinenest.rooms.minimum-bootstrap.approval-reference}") String reference){this.jdbc=jdbc;this.identity=identity;this.venue=venue;this.actor=actor;this.minimum=minimum;this.effective=Instant.parse(effective);this.reference=reference;}
 @Transactional public void run(ApplicationArguments ignored){
  if(minimum!=180 || reference.isBlank() || !identity.eligible(actor))throw new IllegalStateException("Explicit approved initial minimum/bootstrap provenance required");
  if(jdbc.sql("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=:v FOR UPDATE").param("v",venue).query(UUID.class).list().isEmpty())throw new IllegalStateException("Assigned venue required");
  UUID policy=UUID.nameUUIDFromBytes((venue+":"+reference+":"+effective).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var existing=jdbc.sql("SELECT minimum_minutes,approved_by FROM cinenest_rooms.minimum_session_policy WHERE policy_id=:id").param("id",policy).query((rs,n)->rs.getInt(1)==minimum && actor.equals(rs.getObject(2,UUID.class))).list();
  if(!existing.isEmpty()){if(!existing.getFirst())throw new IllegalStateException("Bootstrap differs from stored policy");return;}
  if(jdbc.sql("SELECT count(*) FROM cinenest_rooms.minimum_session_policy WHERE venue_id=:v").param("v",venue).query(Integer.class).single()!=0)throw new IllegalStateException("Existing policy cannot be replaced by bootstrap");
  jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES(:id,:v,1,:m,:effective,:ref,:actor)").param("id",policy).param("v",venue).param("m",minimum).param("effective",effective.atOffset(java.time.ZoneOffset.UTC)).param("ref",reference).param("actor",actor).update();
  jdbc.sql("INSERT INTO cinenest_rooms.configuration_audit(event_id,actor_id,venue_id,resource_type,resource_id,action,outcome,new_version,changed_fields,correlation_id) VALUES(:id,:actor,:v,'POLICY',:policy,'BOOTSTRAP','ALLOW',1,'minimum_minutes,effective_from,approval_reference',:correlation)").param("id",UUID.randomUUID()).param("actor",actor).param("v",venue).param("policy",policy).param("correlation",UUID.randomUUID()).update();
 }
}

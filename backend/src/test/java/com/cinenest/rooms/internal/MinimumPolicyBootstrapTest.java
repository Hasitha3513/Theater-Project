package com.cinenest.rooms.internal;
import com.cinenest.identity.ScopedAuthorization;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
class MinimumPolicyBootstrapTest {
 @Autowired JdbcClient jdbc;@Autowired ScopedAuthorization identity;@Autowired PlatformTransactionManager transactions;
 UUID[] fixtures(){UUID venue=UUID.randomUUID(),actor=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_identity.account VALUES(:a,TRUE,'ELIGIBLE',0)").param("a",actor).update();jdbc.sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES(:v,:code,'Synthetic bootstrap venue','Asia/Colombo')").param("v",venue).param("code","BOOTSTRAP-"+venue.toString().substring(0,8).toUpperCase()).update();return new UUID[]{venue,actor};}
 @Test void explicitBootstrapIsAttributableRepeatableAndCannotReplacePolicy(){var ids=fixtures();var bootstrap=new MinimumPolicyBootstrap(jdbc,identity,ids[0],ids[1],180,Instant.now().minusSeconds(1).toString(),"SYNTHETIC owner-approved OD-21 bootstrap");var tx=new TransactionTemplate(transactions);tx.executeWithoutResult(s->bootstrap.run(null));tx.executeWithoutResult(s->bootstrap.run(null));assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.minimum_session_policy WHERE venue_id=:v").param("v",ids[0]).query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.configuration_audit WHERE venue_id=:v AND action='BOOTSTRAP'").param("v",ids[0]).query(Integer.class).single()).isEqualTo(1);var changed=new MinimumPolicyBootstrap(jdbc,identity,ids[0],ids[1],180,Instant.now().toString(),"SYNTHETIC different instruction");assertThatThrownBy(()->tx.executeWithoutResult(s->changed.run(null))).isInstanceOf(IllegalStateException.class);}
 @Test void MissingProvenanceOrDifferentUnapprovedInitialValueFailsClosed(){var ids=fixtures();var tx=new TransactionTemplate(transactions);var wrong=new MinimumPolicyBootstrap(jdbc,identity,ids[0],ids[1],120,Instant.now().toString(),"SYNTHETIC");assertThatThrownBy(()->tx.executeWithoutResult(s->wrong.run(null))).isInstanceOf(IllegalStateException.class);var missing=new MinimumPolicyBootstrap(jdbc,identity,ids[0],ids[1],180,Instant.now().toString(),"");assertThatThrownBy(()->tx.executeWithoutResult(s->missing.run(null))).isInstanceOf(IllegalStateException.class);}
}

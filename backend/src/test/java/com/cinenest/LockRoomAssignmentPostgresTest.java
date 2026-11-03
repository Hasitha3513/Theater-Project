package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

/** Future opt-in only: exact DDL AND a fresh runtime/fixture authorization are prerequisites.
 * No automatic database creation, migration resource, reset, truncate or cleanup is supplied. */
@EnabledIfEnvironmentVariable(named="CINENEST_LOCK_ASSIGNMENT_PG_TESTS",matches="AUTHORIZED_DISPOSABLE")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","cinenest.identity.manual.enabled=false","cinenest.identity.google.enabled=false","cinenest.identity.facebook.enabled=false","cinenest.access.enabled=false","cinenest.iot.enabled=false","cinenest.notifications.enabled=false","cinenest.bank.enabled=false","cinenest.booking.holds.enabled=false","cinenest.pricing.synthetic-quotes.enabled=false"})
@ActiveProfiles("local-synthetic")
@Import(LockRoomAssignmentPostgresTest.FaultInjection.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LockRoomAssignmentPostgresTest {
    static final AtomicBoolean FAIL_AFTER_PERSIST=new AtomicBoolean();
    @TestConfiguration static class FaultInjection {
        @Bean static BeanPostProcessor rollbackProbe(){return new BeanPostProcessor(){
            public Object postProcessAfterInitialization(Object bean,String name){
                if(!name.equals("jdbcLockRoomAssignmentRepository"))return bean;
                return java.lang.reflect.Proxy.newProxyInstance(bean.getClass().getClassLoader(),org.springframework.util.ClassUtils.getAllInterfaces(bean),(proxy,method,args)->{
                    try{method.setAccessible(true);Object value=method.invoke(bean,args);
                        if(method.getName().equals("create")&&FAIL_AFTER_PERSIST.compareAndSet(true,false))throw new IllegalStateException("Synthetic transaction failure after persistence");return value;
                    }catch(InvocationTargetException failure){throw failure.getCause();}
                });
            }
        };}
    }
    @DynamicPropertySource static void settings(DynamicPropertyRegistry p){
        String url=System.getenv("CINENEST_DB_URL");
        if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/lock_assignment_qual_[0-9a-f]{32}"))throw new IllegalStateException("Fresh separately authorized disposable database required");
        LockRegistryIntegrationTest.properties(p);
        p.add("cinenest.iot.lock-room-assignment.enabled",()->"true");p.add("cinenest.iot.lock-room-assignment.mode",()->"QUALIFICATION_ONLY");
    }
    @Autowired ConfigurableApplicationContext context;@Autowired JdbcClient jdbc;
    final LockRegistryIntegrationTest helper=new LockRegistryIntegrationTest();
    @BeforeEach void recordOwnedPorts()throws Exception{
        String run=System.getenv("CINENEST_ASSIGNMENT_RUN");if(run==null)throw new IllegalStateException("Owned runtime manifest required");
        java.nio.file.Files.writeString(java.nio.file.Path.of(run).resolve("backend-ports.json"),JSONObjectUtils.toJSONString(Map.of("ports",List.of(context.getEnvironment().getRequiredProperty("local.server.port",Integer.class),URI.create(LockRegistryIntegrationTest.IDP.issuer()).getPort()))));
    }
    @AfterAll static void stopIssuer(){LockRegistryIntegrationTest.IDP.close();}
    String path(UUID venue,UUID room){return "/api/admin/venues/"+venue+"/rooms/"+room+"/lock-assignments";}
    String command(UUID key,UUID device){return JSONObjectUtils.toJSONString(Map.of("commandKey",key.toString(),"deviceId",device.toString(),"expectedAssignmentRevision",0,"expectedRoomConfiguration","0:0:","expectedDeviceMetadataRevision",1));}
    HttpResponse<String> post(LockRegistryIntegrationTest.Browser browser,UUID venue,UUID room,String body,boolean csrf)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(helper.base()+path(venue,room))).header("Content-Type","application/json").header("Cookie",browser.cookie).POST(HttpRequest.BodyPublishers.ofString(body));
        if(csrf){var token=helper.csrf(browser);builder.header(token.get("headerName").toString(),token.get("token").toString());}
        return helper.client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    int count(String table){return jdbc.sql("SELECT count(*) FROM cinenest_iot."+table).query(Integer.class).single();}
    void grant(UUID actor,String action){jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:id,:actor,:permission,'VENUE',:venue,'ACTIVE',clock_timestamp()-INTERVAL '1 minute',clock_timestamp()+INTERVAL '5 minutes',0)").param("id",UUID.randomUUID()).param("actor",actor).param("permission",action).param("venue",LockRegistryIntegrationTest.VA).update();}
    @Test void exactScopeRacesReplayAndMandatoryAuditRollback()throws Exception{
        context.getAutowireCapableBeanFactory().autowireBean(helper);
        assertThat(jdbc.sql("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version='33'").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single()).isEqualTo(34);
        assertThat(jdbc.sql("SELECT count(*) FROM public.flyway_schema_history WHERE version='31'").query(Integer.class).single()).isZero();
        assertThat(count("lock_room_assignment")).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isZero();
        for(String subject:List.of("staff","customer-a")){
            UUID actor=LockRegistryIntegrationTest.id(subject);jdbc.sql("INSERT INTO cinenest_identity.account VALUES(:id,true,'ELIGIBLE',0)").param("id",actor).update();
            jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,:subject,:id)").param("issuer",LockRegistryIntegrationTest.IDP.issuer()).param("subject",subject).param("id",actor).update();helper.scope(actor,LockRegistryIntegrationTest.VA);
        }
        for(UUID venue:List.of(LockRegistryIntegrationTest.VA,LockRegistryIntegrationTest.VB))jdbc.sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES(:id,:code,'Synthetic assignment venue','Asia/Colombo')").param("id",venue).param("code","V"+venue.toString().replace("-","").toUpperCase(Locale.ROOT)).update();
        UUID[] rooms={UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        for(int i=0;i<rooms.length;i++)jdbc.sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity) VALUES(:id,:venue,:code,'Synthetic assignment room',1)").param("id",rooms[i]).param("venue",i==4?LockRegistryIntegrationTest.VB:LockRegistryIntegrationTest.VA).param("code","R"+i).update();
        helper.grants();grant(LockRegistryIntegrationTest.STAFF,"iot.lock.assignment.create");grant(LockRegistryIntegrationTest.STAFF,"iot.lock.assignment.read");grant(LockRegistryIntegrationTest.A,"iot.lock.assignment.read");
        var operator=helper.signedIn("staff");var reader=helper.signedIn("customer-a");UUID[] devices=new UUID[4];
        for(int i=0;i<4;i++)devices[i]=helper.register(operator,helper.payload(i+1,UUID.randomUUID()));
        UUID venue=LockRegistryIntegrationTest.VA;String initial=command(UUID.randomUUID(),devices[0]);
        assertThat(helper.new Browser().get(path(venue,rooms[0])).statusCode()).isEqualTo(401);
        assertThat(post(operator,venue,rooms[0],initial,false).statusCode()).isEqualTo(403);
        assertThat(post(reader,venue,rooms[0],initial,true).statusCode()).isEqualTo(403);
        assertThat(post(operator,LockRegistryIntegrationTest.VB,rooms[4],initial,true).statusCode()).isEqualTo(403);
        assertThat(post(operator,venue,rooms[4],initial,true).statusCode()).isEqualTo(404);
        assertThat(post(operator,venue,rooms[0],initial.replace("0:0:","stale"),true).statusCode()).isEqualTo(409);
        assertThat(post(operator,venue,rooms[0],initial.replace("\"expectedDeviceMetadataRevision\":1","\"expectedDeviceMetadataRevision\":2"),true).statusCode()).isEqualTo(409);
        var created=post(operator,venue,rooms[0],initial,true);assertThat(created.statusCode()).isEqualTo(201);var view=JSONObjectUtils.parse(created.body());assertThat(view.get("state")).isEqualTo("UNQUALIFIED");assertThat(view.get("customerAccessEnabled")).isEqualTo(false);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED',revision=revision+1 WHERE account_id=:actor AND venue_id=:venue AND permission='iot.lock.assignment.read'").param("actor",LockRegistryIntegrationTest.STAFF).param("venue",venue).update();
        assertThat(operator.get(path(venue,rooms[0])).statusCode()).isEqualTo(403);
        assertThat(post(operator,venue,rooms[0],initial,true).statusCode()).isEqualTo(200);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='ACTIVE',revision=revision+1 WHERE account_id=:actor AND venue_id=:venue AND permission='iot.lock.assignment.read'").param("actor",LockRegistryIntegrationTest.STAFF).param("venue",venue).update();
        assertThat(post(operator,venue,rooms[0],initial.replace(devices[0].toString(),devices[1].toString()),true).statusCode()).isEqualTo(409);
        assertThat(post(operator,venue,rooms[0],command(UUID.randomUUID(),devices[1]),true).statusCode()).isEqualTo(409);
        assertThat(post(operator,venue,rooms[1],command(UUID.randomUUID(),devices[0]),true).statusCode()).isEqualTo(409);
        assertThat(reader.get(path(venue,rooms[0])+"/"+view.get("assignmentId")).statusCode()).isEqualTo(200);assertThat(reader.get(path(venue,rooms[1])+"/"+view.get("assignmentId")).statusCode()).isEqualTo(404);
        var pool=Executors.newFixedThreadPool(2);
        try{
            helper.csrf(operator);var first=pool.submit(()->post(operator,venue,rooms[1],command(UUID.randomUUID(),devices[1]),true).statusCode());var second=pool.submit(()->post(operator,venue,rooms[1],command(UUID.randomUUID(),devices[2]),true).statusCode());assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            String replay=command(UUID.randomUUID(),devices[3]);var x=pool.submit(()->post(operator,venue,rooms[2],replay,true));var y=pool.submit(()->post(operator,venue,rooms[2],replay,true));var xx=x.get(10,TimeUnit.SECONDS);var yy=y.get(10,TimeUnit.SECONDS);assertThat(List.of(xx.statusCode(),yy.statusCode())).containsExactlyInAnyOrder(201,200);assertThat(JSONObjectUtils.parse(xx.body()).get("assignmentId")).isEqualTo(JSONObjectUtils.parse(yy.body()).get("assignmentId"));
        }finally{pool.shutdownNow();}
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1 WHERE account_id=:actor").param("actor",LockRegistryIntegrationTest.A).update();
        assertThat(reader.get(path(venue,rooms[0])).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='ELIGIBLE',revision=revision+1 WHERE account_id=:actor").param("actor",LockRegistryIntegrationTest.A).update();
        int before=count("lock_room_assignment"),receipts=count("lock_room_assignment_command"),audits=count("lock_room_assignment_audit");
        UUID unusedDevice=Arrays.stream(devices).filter(d -> jdbc.sql("SELECT count(*) FROM cinenest_iot.lock_room_assignment WHERE device_id=:d").param("d",d).query(Integer.class).single()==0).findFirst().orElseThrow();
        FAIL_AFTER_PERSIST.set(true);assertThat(post(operator,venue,rooms[3],command(UUID.randomUUID(),unusedDevice),true).statusCode()).isEqualTo(503);assertThat(count("lock_room_assignment")).isEqualTo(before);assertThat(count("lock_room_assignment_command")).isEqualTo(receipts);assertThat(count("lock_room_assignment_audit")).isEqualTo(audits+1); // independent DENIED event only
        for(String table:List.of("lock_room_assignment","lock_room_assignment_command","lock_room_assignment_audit")){
            String actorColumn=table.equals("lock_room_assignment")?"created_by":"actor_id";
            for(String mutation:List.of("UPDATE cinenest_iot."+table+" SET "+actorColumn+"="+actorColumn,"DELETE FROM cinenest_iot."+table,"TRUNCATE cinenest_iot."+table+" CASCADE")){
                try(var connection=DriverManager.getConnection(System.getenv("CINENEST_DB_URL"),System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"))){connection.setAutoCommit(false);try(var statement=connection.createStatement()){var failure=catchThrowable(()->statement.execute(mutation));assertThat(failure).isInstanceOf(SQLException.class);assertThat(((SQLException)failure).getSQLState()).isEqualTo("P0001");}finally{connection.rollback();}}
            }
        }
        assertThat(count("lock_room_assignment")).isEqualTo(3);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_iot.lock_room_assignment WHERE state<>'UNQUALIFIED' OR customer_access_enabled").query(Integer.class).single()).isZero();
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("CINENEST_ASSIGNMENT_RUN")).resolve("assignment-scenarios.json"),JSONObjectUtils.toJSONString(Map.of("cleanMigration",true,"assignmentCount",count("lock_room_assignment"),"commandCount",count("lock_room_assignment_command"),"auditCount",count("lock_room_assignment_audit"),"fixtureAccounts",2,"fixtureVenues",2,"fixtureRooms",5,"fixtureDevices",4,"state","UNQUALIFIED","customerAccessEnabled",false)));
        for(String table:List.of("cinenest_booking.booking","cinenest_booking.inventory_block","cinenest_payments.bank_payment_request","cinenest_access.access_credential","cinenest_iot.command"))assertThat(jdbc.sql("SELECT count(*) FROM "+table).query(Integer.class).single()).isZero();
    }
    @Test void populatedV32RegistryUpgradeIsAdditiveAndRepeatable()throws Exception{
        String url=System.getenv("CINENEST_ASSIGNMENT_UPGRADE_DB_URL");
        if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/lock_assignment_upgrade_[0-9a-f]{32}"))throw new IllegalStateException("Second exact-owned database required");
        String user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");
        var old=org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("32").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(31);old.validate();
        UUID d=UUID.randomUUID(),event=UUID.randomUUID(),key=UUID.randomUUID(),actor=LockRegistryIntegrationTest.STAFF,venue=LockRegistryIntegrationTest.VA;
        String before;
        try(var c=DriverManager.getConnection(url,user,password)){
            c.setAutoCommit(false);
            try(var q=c.createStatement()){
                q.execute("INSERT INTO cinenest_iot.lock_registry_device(device_id,venue_id,provider,provider_scope_id,provider_identity_id,registered_by,registration_command_key) VALUES('"+d+"','"+venue+"','TUYA','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+actor+"','"+key+"')");
                q.execute("INSERT INTO cinenest_iot.lock_registry_audit(event_id,actor_id,venue_id,device_id,metadata_revision,event_kind,outcome,correlation_id,observed_at) VALUES('"+event+"','"+actor+"','"+venue+"','"+d+"',1,'REGISTER','ALLOW','"+UUID.randomUUID()+"',clock_timestamp())");
                q.execute("INSERT INTO cinenest_iot.lock_registry_revision(device_id,venue_id,revision,manufacturer,model,provider_reference_id,provider_reference_version,declaration_source,declared_capabilities,recorded_by,audit_event_id,audit_kind) VALUES('"+d+"','"+venue+"',1,'Synthetic','Unqualified upgrade fixture','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','CONTRACT_FIXTURE','[]','"+actor+"','"+event+"','REGISTER')");
                q.execute("INSERT INTO cinenest_iot.lock_registry_command(actor_id,command_key,venue_id,device_id,request_sha256,audit_event_id) VALUES('"+actor+"','"+key+"','"+venue+"','"+d+"',decode(repeat('ab',32),'hex'),'"+event+"')");
            }c.commit();before=registryFingerprint(c);
        }
        var upgrade=org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("33").load();assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);upgrade.validate();assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,user,password);var q=c.createStatement()){
            assertThat(registryFingerprint(c)).isEqualTo(before);
            for(String t:List.of("lock_room_assignment","lock_room_assignment_command","lock_room_assignment_audit")){try(var r=q.executeQuery("SELECT count(*) FROM cinenest_iot."+t)){r.next();assertThat(r.getInt(1)).isZero();}}
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("CINENEST_ASSIGNMENT_RUN")).resolve("upgrade-result.json"),JSONObjectUtils.toJSONString(Map.of("v32Migrations",31,"upgradeMigrations",1,"repeatMigrations",0,"registryFingerprintUnchanged",true,"fixtureDevices",1,"additionalAccounts",0,"additionalVenues",0,"additionalRooms",0)));
    }
    String registryFingerprint(Connection c)throws Exception{
        var hash=java.security.MessageDigest.getInstance("SHA-256");
        for(String t:List.of("lock_registry_device","lock_registry_revision","lock_registry_command","lock_registry_audit"))try(var q=c.createStatement();var r=q.executeQuery("SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY to_jsonb(x)::text),'[]'::jsonb)::text FROM cinenest_iot."+t+" x")){r.next();hash.update(r.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        return java.util.HexFormat.of().formatHex(hash.digest());
    }

}

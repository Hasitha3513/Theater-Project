package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"cinenest.booking.room-blocks.enabled=true","cinenest.booking.room-blocks.mode=LOCAL_SYNTHETIC_ONLY","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=1000","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC room-blocking qualification","cinenest.identity.google.enabled=false","cinenest.identity.facebook.enabled=false","spring.datasource.hikari.maximum-pool-size=6"})
@ActiveProfiles({"local-synthetic","synthetic-quotation"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RoomBlockingIntegrationTest {
    static final Path OUTBOX;
    static final String DB,URL;
    static {try{
        OUTBOX=Files.createTempDirectory("cinenest-block-mail-");DB="room_blocks_"+UUID.randomUUID().toString().replace("-","");
        String source=System.getenv("CINENEST_DB_URL");if(source==null||!source.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test"))throw new IllegalStateException("Dedicated test-owned PostgreSQL runner required");
        URL=source.substring(0,source.lastIndexOf('/')+1)+DB;
        try(var c=DriverManager.getConnection(source,System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("CREATE DATABASE "+DB);}
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->URL);r.add("cinenest.identity.enabled",()->"true");r.add("cinenest.identity.manual.enabled",()->"true");r.add("cinenest.identity.session-timeout-seconds",()->"120");r.add("cinenest.identity.manual.outbox",OUTBOX::toString);}
    @Autowired org.springframework.context.ApplicationContext context;@Autowired JdbcClient jdbc;@Autowired Environment env;@Autowired PlatformTransactionManager manager;
    record Fixture(UUID actor,UUID venue,UUID room,Browser browser){}
    String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
    final class Browser {
        String cookie,csrf,email;
        final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> call(String method,String path,String body,boolean token)throws Exception{var b=HttpRequest.newBuilder(URI.create(base()+path)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));if(cookie!=null)b.header("Cookie",cookie);if(body!=null)b.header("Content-Type","application/json");if(token&&csrf!=null)b.header("X-CSRF-TOKEN",csrf);var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());for(String c:r.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return r;}
        void csrf(String path)throws Exception{var r=call("GET",path,null,false);assertThat(r.statusCode()).isEqualTo(200);csrf=JSONObjectUtils.parse(r.body()).get("token").toString();}
        UUID login()throws Exception{
            email="rb"+UUID.randomUUID().toString().replace("-","")+"@example.test";String password="Room blocking synthetic password 2026!";csrf("/api/auth/csrf");assertThat(call("POST","/api/auth/registrations",json(Map.of("email",email,"password",password)),true).statusCode()).isEqualTo(202);
            String token=null;try(var files=Files.list(OUTBOX)){for(var f:files.toList()){var lines=Files.readAllLines(f);if(lines.get(1).equals(email))token=lines.get(2);}}
            assertThat(token).isNotNull();assertThat(call("POST","/api/auth/email-verifications",json(Map.of("token",token)),true).statusCode()).isEqualTo(204);String before=cookie;assertThat(call("POST","/api/auth/login",json(Map.of("email",email,"password",password)),true).statusCode()).isEqualTo(204);assertThat(cookie).isNotEqualTo(before);csrf("/api/session/csrf");return jdbc.sql("SELECT account_id FROM cinenest_identity.manual_customer WHERE email=:email").param("email",email).query(UUID.class).single();
        }
    }
    static String json(Object value){return JSONObjectUtils.toJSONString((Map<String,?>)value);}
    Fixture fixture()throws Exception{
        jdbc.sql("TRUNCATE cinenest_identity.manual_throttle").update();var browser=new Browser();UUID actor=browser.login(),venue=UUID.randomUUID(),room=UUID.randomUUID();
        jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v)").param("a",actor).param("v",venue).update();
        jdbc.sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone,cleaning_buffer_minutes,enabled) VALUES(:v,:code,'Room block test venue','Asia/Colombo',7,true)").param("v",venue).param("code","RB"+venue.toString().replace("-","").substring(0,12).toUpperCase(java.util.Locale.ROOT)).update();
        jdbc.sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled) VALUES(:r,:v,'ROOM','Room block test room',5,true)").param("r",room).param("v",venue).update();
        jdbc.sql("INSERT INTO cinenest_rooms.venue_operating_window SELECT :v,day,0,1440 FROM generate_series(1,7) day").param("v",venue).update();
        jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES(:id,:v,1,180,clock_timestamp()-INTERVAL '1 hour','SYNTHETIC room-block fixture',:a)").param("id",UUID.randomUUID()).param("v",venue).param("a",actor).update();
        grant(actor,venue,"inventory.read");grant(actor,venue,"inventory.block.create");return new Fixture(actor,venue,room,browser);
    }
    void grant(UUID actor,UUID venue,String permission){jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:id,:a,:p,'VENUE',:v,'ACTIVE',clock_timestamp()-INTERVAL '1 minute',clock_timestamp()+INTERVAL '1 hour',0)").param("id",UUID.randomUUID()).param("a",actor).param("p",permission).param("v",venue).update();}
    String path(Fixture f){return "/api/admin/venues/"+f.venue()+"/rooms/"+f.room()+"/blocks";}
    String configuration(Fixture f)throws Exception{var r=f.browser().call("GET",path(f),null,false);assertThat(r.statusCode()).isEqualTo(200);return JSONObjectUtils.parse(r.body()).get("configuration").toString();}
    Map<String,Object> command(Fixture f)throws Exception{return new HashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"startAt","2030-01-01T10:00:00+05:30","endAt","2030-01-01T12:00:00+05:30","expectedConfiguration",configuration(f)));}
    HttpResponse<String> create(Fixture f,Map<String,Object> c)throws Exception{return f.browser().call("POST",path(f),json(c),true);}
    int count(String table,UUID venue){return jdbc.sql("SELECT count(*) FROM cinenest_booking."+table+" WHERE venue_id=:v").param("v",venue).query(Integer.class).single();}

    @Test void createReadReplayAndImmutableHistory()throws Exception{
        var f=fixture();var c=command(f);var first=f.browser().call("POST",path(f)+"?",json(c),true);assertThat(first.statusCode()).isEqualTo(200);var b=JSONObjectUtils.parse(first.body());String id=b.get("blockId").toString();
        assertThat(create(f,c).body()).isEqualTo(first.body());assertThat(count("room_block_command",f.venue())).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.room_block_audit WHERE venue_id=:v AND action='CREATE'").param("v",f.venue()).query(Integer.class).single()).isEqualTo(1);
        assertThat(f.browser().call("GET",path(f)+"/"+id,null,false).statusCode()).isEqualTo(200);
        for(String table:List.of("room_block_command","room_block_audit")){assertThatThrownBy(()->jdbc.sql("TRUNCATE cinenest_booking."+table+" CASCADE").update()).isInstanceOf(Exception.class);assertThatThrownBy(()->jdbc.sql("DELETE FROM cinenest_booking."+table+" WHERE venue_id=:v").param("v",f.venue()).update()).isInstanceOf(Exception.class);}
        for(String mutation:List.of("blocking=false","revision=revision+1","room_id=gen_random_uuid()","venue_id=gen_random_uuid()","session_end=session_end+INTERVAL '1 hour',blocked_until=blocked_until+INTERVAL '1 hour'"))
            assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_booking.inventory_block SET "+mutation+" WHERE block_id=:b").param("b",UUID.fromString(id)).update()).isInstanceOf(Exception.class);
        c.put("endAt","2030-01-01T13:00:00+05:30");assertThat(create(f,c).statusCode()).isEqualTo(409);assertThat(count("room_block_command",f.venue())).isEqualTo(1);
    }
    @Test void concurrentDuplicateAndOverlappingCreates()throws Exception{
        var f=fixture();var c=command(f);var pool=Executors.newFixedThreadPool(2);
        try{var one=pool.submit(()->create(f,c));var two=pool.submit(()->create(f,c));var a=one.get();var b=two.get();assertThat(a.statusCode()).isEqualTo(200);assertThat(b.statusCode()).isEqualTo(200);assertThat(a.body()).isEqualTo(b.body());assertThat(count("room_block_command",f.venue())).isEqualTo(1);
            c.put("idempotencyKey",UUID.randomUUID().toString());assertThat(create(f,c).statusCode()).isEqualTo(409);
            c.put("startAt","2030-01-02T10:00:00Z");c.put("endAt","2030-01-02T12:00:00Z");var d=new HashMap<>(c);d.put("idempotencyKey",UUID.randomUUID().toString());var x=pool.submit(()->create(f,c).statusCode());var y=pool.submit(()->create(f,d).statusCode());assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(200,409);
        }finally{pool.shutdownNow();}
    }
    @Test void authorityScopeCsrfRevisionAndSessionDenials()throws Exception{
        var f=fixture();var c=command(f);assertThat(f.browser().call("POST",path(f),json(c),false).statusCode()).isEqualTo(403);
        assertThat(new Browser().call("GET",path(f),null,false).statusCode()).isEqualTo(401);
        assertThat(f.browser().call("GET",path(f)+"?accountId="+f.actor(),null,false).statusCode()).isEqualTo(400);
        assertThat(f.browser().call("POST",path(f),"{\"idempotencyKey\":\"a\",\"idempotencyKey\":\"b\"}",true).statusCode()).isEqualTo(400);
        String wrong=path(f).replace(f.venue().toString(),UUID.randomUUID().toString());assertThat(f.browser().call("POST",wrong,json(c),true).statusCode()).isEqualTo(403);
        c.put("expectedConfiguration","stale");assertThat(create(f,c).statusCode()).isEqualTo(409);c.put("expectedConfiguration",configuration(f));
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED',revision=revision+1 WHERE account_id=:a AND permission='inventory.block.create'").param("a",f.actor()).update();assertThat(create(f,c).statusCode()).isEqualTo(403);assertThat(f.browser().call("GET",path(f),null,false).statusCode()).isEqualTo(200);
        grant(f.actor(),f.venue(),"inventory.block.create");jdbc.sql("DELETE FROM cinenest_identity.account_scope WHERE account_id=:a").param("a",f.actor()).update();assertThat(create(f,c).statusCode()).isEqualTo(403);
        jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v)").param("a",f.actor()).param("v",f.venue()).update();jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1 WHERE account_id=:a").param("a",f.actor()).update();assertThat(create(f,c).statusCode()).isEqualTo(403);assertThat(count("room_block_command",f.venue())).isZero();
    }
    @Test void calendarAndExistingHoldAdmissionRespectBlockAndCleaning()throws Exception{
        var f=fixture();var c=command(f);assertThat(create(f,c).statusCode()).isEqualTo(200);
        var result=f.browser().call("GET","/api/rooms/"+f.room()+"/availability/calendar?date=2030-01-01&durationMinutes=180&guests=1",null,false);assertThat(result.statusCode()).isEqualTo(200);assertThat(result.body()).contains("MAINTENANCE_BLOCK");
        var hold=new HashMap<String,Object>(Map.of("roomId",f.room().toString(),"sessionStartLocal","2030-01-01T09:00","durationMinutes",180,"guests",1,"expectedConfiguration",configuration(f),"idempotencyKey",UUID.randomUUID().toString()));
        assertThat(f.browser().call("POST","/api/holds",json(hold),true).statusCode()).isEqualTo(409);
        hold.put("sessionStartLocal","2030-01-02T09:00");assertThat(f.browser().call("POST","/api/holds",json(hold),true).statusCode()).isEqualTo(200);
        c.put("idempotencyKey",UUID.randomUUID().toString());c.put("startAt","2030-01-02T12:05:00+05:30");c.put("endAt","2030-01-02T13:00:00+05:30");assertThat(create(f,c).statusCode()).isEqualTo(409);
        c.put("startAt","2030-01-02T12:07:00+05:30");assertThat(create(f,c).statusCode()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.reservation_hold WHERE owner_id=:a AND state='ACTIVE'").param("a",f.actor()).query(Integer.class).single()).isEqualTo(1);
    }
    @Test void confirmedAndExtensionCoverageCannotBeOverridden()throws Exception{
        for(String kind:List.of("CONFIRMED")){
            var f=fixture();jdbc.sql("INSERT INTO cinenest_booking.inventory_block VALUES(:id,:v,:r,'2030-01-01T04:30:00Z','2030-01-01T06:30:00Z',7,'2030-01-01T06:37:00Z',true,:kind,4)").param("id",UUID.randomUUID()).param("v",f.venue()).param("r",f.room()).param("kind",kind).update();assertThat(create(f,command(f)).statusCode()).isEqualTo(409);assertThat(count("room_block_command",f.venue())).isZero();
        }
    }
    @Test void missingAuditRollsBackInventoryAndReceipt()throws Exception{
        var f=fixture();var c=command(f);String hash="0".repeat(64);var tx=new TransactionTemplate(manager);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{UUID block=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_booking.inventory_block VALUES(:b,:v,:r,'2030-02-01T00:00Z','2030-02-01T01:00Z',0,'2030-02-01T01:00Z',true,'MAINTENANCE',0)").param("b",block).param("v",f.venue()).param("r",f.room()).update();jdbc.sql("INSERT INTO cinenest_booking.room_block_command(actor_id,command_key,request_hash,block_id,venue_id,room_id,start_at,end_at,expected_configuration,blocked_until,audit_event_id) VALUES(:a,:k,:h,:b,:v,:r,'2030-02-01T00:00Z','2030-02-01T01:00Z','test','2030-02-01T01:00Z',:e)").param("a",f.actor()).param("k",UUID.randomUUID()).param("h",hash).param("b",block).param("v",f.venue()).param("r",f.room()).param("e",UUID.randomUUID()).update();})).isInstanceOf(Exception.class);
        assertThat(count("room_block_command",f.venue())).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE venue_id=:v").param("v",f.venue()).query(Integer.class).single()).isZero();assertThat(create(f,c).statusCode()).isEqualTo(200);
    }
    @Test void cleanUpgradeAndHistoricalMigrationIntegrity()throws Exception{
        var f=Flyway.configure().dataSource(URL,System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD")).load();f.validate();assertThat(f.info().current().getVersion().toString()).isEqualTo("32");assertThat(Arrays.stream(f.info().all()).filter(i->i.getState().isFailed()).count()).isZero();
        String name="room_block_upgrade_"+UUID.randomUUID().toString().replace("-","");jdbc.sql("CREATE DATABASE "+name).update();String url=URL.substring(0,URL.lastIndexOf('/')+1)+name;String user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");Flyway.configure().dataSource(url,user,password).target("30").load().migrate();UUID id=UUID.randomUUID();try(var db=DriverManager.getConnection(url,user,password);var q=db.createStatement()){q.execute("INSERT INTO cinenest_booking.inventory_block VALUES('"+id+"',gen_random_uuid(),gen_random_uuid(),'2030-01-01T00:00Z','2030-01-01T01:00Z',0,'2030-01-01T01:00Z',true,'MAINTENANCE',9)");}var latest=Flyway.configure().dataSource(url,user,password).load();assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();assertThat(latest.migrate().migrationsExecuted).isZero();try(var db=DriverManager.getConnection(url,user,password);var q=db.createStatement();var row=q.executeQuery("SELECT revision FROM cinenest_booking.inventory_block WHERE block_id='"+id+"'")){assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isEqualTo(9);}
    }
    String newSchemaDatabase()throws Exception{
        String name="room_block_guard_"+UUID.randomUUID().toString().replace("-","");jdbc.sql("CREATE DATABASE "+name).update();String url=URL.substring(0,URL.lastIndexOf('/')+1)+name;
        Flyway.configure().dataSource(url,System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD")).load().migrate();return url;
    }
    Connection connect(String url)throws Exception{return DriverManager.getConnection(url,System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"));}
    void insertJournal(Connection c)throws Exception{
        String actor=UUID.randomUUID().toString(),venue=UUID.randomUUID().toString(),room=UUID.randomUUID().toString(),block=UUID.randomUUID().toString(),key=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        try(var q=c.createStatement()){
            q.execute("INSERT INTO cinenest_booking.inventory_block VALUES('"+block+"','"+venue+"','"+room+"','2030-01-01T00:00Z','2030-01-01T01:00Z',0,'2030-01-01T01:00Z',true,'MAINTENANCE',0)");
            q.execute("INSERT INTO cinenest_booking.room_block_command(actor_id,command_key,request_hash,block_id,venue_id,room_id,start_at,end_at,expected_configuration,blocked_until,audit_event_id) VALUES('"+actor+"','"+key+"',repeat('a',64),'"+block+"','"+venue+"','"+room+"','2030-01-01T00:00Z','2030-01-01T01:00Z','fixture','2030-01-01T01:00Z','"+event+"')");
            q.execute("INSERT INTO cinenest_booking.room_block_audit VALUES('"+event+"','"+actor+"','"+venue+"','"+room+"','"+block+"','"+key+"','CREATE',gen_random_uuid(),clock_timestamp())");
        }
    }
    @Test void emptyOnlyTruncateRejectsStaleSnapshotsAndCommittedHistory()throws Exception{
        String url=newSchemaDatabase();try(var c=connect(url);var q=c.createStatement()){
            q.execute("TRUNCATE cinenest_booking.room_block_command,cinenest_booking.room_block_audit");
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);q.execute("SELECT count(*) FROM cinenest_booking.room_block_command");
            assertThatThrownBy(()->q.execute("TRUNCATE cinenest_booking.room_block_command,cinenest_booking.room_block_audit")).isInstanceOf(SQLException.class);c.rollback();
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            q.execute("INSERT INTO cinenest_booking.room_block_audit VALUES(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),NULL,NULL,'BLOCK_LIST_READ',gen_random_uuid(),clock_timestamp())");c.commit();
            assertThatThrownBy(()->q.execute("TRUNCATE cinenest_booking.room_block_command,cinenest_booking.room_block_audit")).isInstanceOf(SQLException.class);c.rollback();
            insertJournal(c);c.commit();
            assertThatThrownBy(()->q.execute("TRUNCATE cinenest_booking.room_block_command,cinenest_booking.room_block_audit")).isInstanceOf(SQLException.class);c.rollback();
            try(var rows=q.executeQuery("SELECT (SELECT count(*) FROM cinenest_booking.room_block_command),(SELECT count(*) FROM cinenest_booking.room_block_audit)")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);assertThat(rows.getInt(2)).isEqualTo(2);}
        }
    }
    @Test void concurrentInsertThenTruncateCannotEraseNewHistory()throws Exception{
        String url=newSchemaDatabase();var pool=Executors.newSingleThreadExecutor();
        try(var writer=connect(url);var truncate=connect(url);var observer=connect(url)){
            writer.setAutoCommit(false);insertJournal(writer);int pid;try(var q=truncate.createStatement();var row=q.executeQuery("SELECT pg_backend_pid()")){row.next();pid=row.getInt(1);}
            var outcome=pool.submit(()->{try(var q=truncate.createStatement()){q.setQueryTimeout(8);q.execute("TRUNCATE cinenest_booking.room_block_command,cinenest_booking.room_block_audit");return "UNEXPECTED_SUCCESS";}catch(SQLException denied){return "DENIED";}});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
            while(System.nanoTime()<deadline){try(var q=observer.createStatement();var row=q.executeQuery("SELECT count(*) FROM pg_locks WHERE pid="+pid+" AND mode='AccessExclusiveLock' AND NOT granted")){row.next();if(row.getInt(1)>0){waiting=true;break;}}Thread.sleep(10);}
            assertThat(waiting).as("Truncate must wait for the writer's table lock").isTrue();writer.commit();assertThat(outcome.get(10,TimeUnit.SECONDS)).isEqualTo("DENIED");
            try(var q=observer.createStatement();var rows=q.executeQuery("SELECT count(*) FROM cinenest_booking.room_block_command")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
        }finally{pool.shutdownNow();}
    }

    @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CINENEST_ROOM_BLOCKING_E2E",matches="true")
    void realAdminBrowserJourney()throws Exception{
        var f=fixture();var run=Files.createTempDirectory("cinenest-room-block-e2e-");
        var file=run.resolve("config.json");Files.writeString(file,json(Map.of("backend",base(),"email",f.browser().email,"password","Room blocking synthetic password 2026!","venue",f.venue().toString(),"room",f.room().toString())));Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var process=new ProcessBuilder("node",Path.of("../frontend/tests/room-blocks-e2e.mjs").toAbsolutePath().toString(),run.toString()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile()).start();
        try{
            assertThat(process.waitFor(3,TimeUnit.MINUTES)).isTrue();assertThat(process.exitValue()).as("Private browser evidence at "+run).isZero();
            var result=JSONObjectUtils.parse(Files.readString(run.resolve("results.json")));assertThat(result.get("status")).isEqualTo("PASS");assertThat(result.get("interception")).isEqualTo(false);assertThat(result.get("cookieInjection")).isEqualTo(false);
            assertThat(count("room_block_command",f.venue())).isEqualTo(1);
            assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.room_block_audit WHERE venue_id=:v AND action='CREATE'").param("v",f.venue()).query(Integer.class).single()).isEqualTo(1);
            for(String table:List.of("cinenest_booking.booking","cinenest_payments.bank_payment_request","cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_access.access_credential","cinenest_iot.device"))assertThat(jdbc.sql("SELECT count(*) FROM "+table).query(Integer.class).single()).isZero();
        }finally{if(process.isAlive()){process.destroy();process.waitFor(10,TimeUnit.SECONDS);}}
    }

    @Test void injectedAuditTransactionFailureRollsBackAndSameCommandRecovers()throws Exception{
        var f=fixture();var c=command(f);
        try(var fault=com.cinenest.booking.internal.RoomBlockFaults.failAfterCreate(context)){
            assertThat(create(f,c).statusCode()).isEqualTo(503);assertThat(count("room_block_command",f.venue())).isZero();assertThat(count("inventory_block",f.venue())).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.room_block_audit WHERE venue_id=:v AND action='CREATE'").param("v",f.venue()).query(Integer.class).single()).isZero();
        }
        assertThat(create(f,c).statusCode()).isEqualTo(200);assertThat(count("room_block_command",f.venue())).isEqualTo(1);
    }
    @Test void concurrentRoomConfigurationChangeRejectsStaleCommand()throws Exception{
        var f=fixture();var c=command(f);var pool=Executors.newSingleThreadExecutor();
        try(var db=connect(URL)){
            db.setAutoCommit(false);try(var q=db.prepareStatement("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=? FOR UPDATE")){q.setObject(1,f.venue());q.executeQuery().close();}
            try(var q=db.prepareStatement("UPDATE cinenest_rooms.room SET version=version+1 WHERE room_id=?")){q.setObject(1,f.room());q.executeUpdate();}
            var waiting=pool.submit(()->create(f,c).statusCode());assertThatThrownBy(()->waiting.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);db.commit();assertThat(waiting.get(5,TimeUnit.SECONDS)).isEqualTo(409);assertThat(count("room_block_command",f.venue())).isZero();
        }finally{pool.shutdownNow();}
    }

    @Test void concurrentCustomerHoldAndRoomBlockHaveExactlyOneWinner()throws Exception{
        var f=fixture();var customer=fixture();var block=command(f);var hold=Map.of("roomId",f.room().toString(),"sessionStartLocal","2030-01-01T10:00","durationMinutes",180,"guests",1,"expectedConfiguration",configuration(f),"idempotencyKey",UUID.randomUUID().toString());
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var one=pool.submit(()->{start.await();return create(f,block).statusCode();});var two=pool.submit(()->{start.await();return customer.browser().call("POST","/api/holds",json(hold),true).statusCode();});start.countDown();assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);assertThat(count("inventory_block",f.venue())).isEqualTo(1);}finally{pool.shutdownNow();}
    }

}

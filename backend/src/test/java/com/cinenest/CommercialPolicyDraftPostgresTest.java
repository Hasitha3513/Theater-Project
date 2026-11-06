package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Bounded real PostgreSQL + real HTTP qualification, enabled only by its pinned private manifest. */
@EnabledIfEnvironmentVariable(named="CINENEST_COMMERCIAL_RUN",matches=".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommercialPolicyDraftPostgresTest {
    static boolean failed;
    @RegisterExtension static final TestWatcher STOP=new TestWatcher(){@Override public void testFailed(ExtensionContext c,Throwable e){failed=true;}};
    final Path run=Path.of(System.getenv().getOrDefault("CINENEST_COMMERCIAL_RUN","/tmp/cn-commercial-NOT-AUTHORIZED"));
    Map<String,Object> manifest;Map<String,String> ids;
    String cleanUrl,appUrl;String user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");
    Process backend,frontend,browserProcess;Map<String,Browser> browsers=new HashMap<>();
    Connection repeatable,serializable;UUID policy;String snapshot,legacySnapshot;
    final List<Map<String,Object>> observations=new ArrayList<>();
    @BeforeEach void stopAfterFailure(){Assumptions.assumeFalse(failed,"Earlier mandatory qualification failed");}
    @SuppressWarnings("unchecked") @BeforeAll void setup()throws Exception {
        manifest=JSONObjectUtils.parse(Files.readString(run.resolve("preflight.json")));ids=(Map<String,String>)manifest.get("fixtureIds");
        cleanUrl=System.getenv("CINENEST_DB_URL");assertThat(cleanUrl).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test");
        appUrl=cleanUrl.substring(0,cleanUrl.lastIndexOf('/')+1)+"commercial_upgrade";
        if(Files.exists(run.resolve("legacy-snapshot.sha256")))legacySnapshot=Files.readString(run.resolve("legacy-snapshot.sha256")).trim();
        if(Files.exists(run.resolve("protected-snapshot.json")))snapshot=Files.readString(run.resolve("protected-snapshot.json"));
        if("TARGETED_FINAL".equals(System.getenv("CINENEST_COMMERCIAL_SELECTION")) && !Files.exists(run.resolve("legacy-snapshot.sha256"))){
            flyway(cleanUrl,null).migrate();
            try(var c=connection(cleanUrl)){execute(c,"CREATE DATABASE commercial_upgrade");}
            flyway(appUrl,"35").migrate();seedFixtures();legacySnapshot=oldSnapshots();
            Files.writeString(run.resolve("legacy-snapshot.sha256"),legacySnapshot);flyway(appUrl,"36").migrate();
        }
    }
    UUID id(String name){return UUID.fromString(ids.get(name));}
    Connection connection(String url)throws SQLException {return DriverManager.getConnection(url,user,password);}
    Connection db()throws SQLException{return connection(appUrl);}
    void sql(String text,Object...args)throws SQLException {try(var c=db()){execute(c,text,args);}}
    void execute(Connection c,String text,Object...args)throws SQLException {try(var q=c.prepareStatement(text)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.execute();}}
    String scalar(Connection c,String text)throws SQLException {try(var q=c.createStatement();var r=q.executeQuery(text)){assertThat(r.next()).isTrue();return r.getString(1);}}
    String scalar(String text)throws SQLException {try(var c=db()){return scalar(c,text);}}
    String digest(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    void observe(String name,Object expected,Object actual)throws Exception {observations.add(Map.of("check",name,"expected",expected,"actual",actual));Files.writeString(run.resolve("sql-observations.json"),JSONObjectUtils.toJSONString(Map.of("observations",observations)));}
    String rejected(Connection c,String text,String expected)throws Exception {
        c.setAutoCommit(false);String state="SUCCESS";
        try{execute(c,text);c.commit();}catch(SQLException e){state=e.getSQLState();c.rollback();}
        observe(text,expected,state);assertThat(state).as(text).isEqualTo(expected);return state;
    }
    Flyway flyway(String url,String target){var b=Flyway.configure().dataSource(url,user,password);if(target!=null)b.target(target);return b.load();}
    @Test @Order(1) void cleanFlywayAndEmptyResetOnlyAtReadCommitted()throws Exception {
        var f=flyway(cleanUrl,null);assertThat(f.migrate().migrationsExecuted).isEqualTo(35);f.validate();assertThat(f.info().current().getVersion().toString()).isEqualTo("36");assertThat(f.migrate().migrationsExecuted).isZero();
        try(var c=connection(cleanUrl)){assertThat(scalar(c,"SELECT count(*) FROM public.flyway_schema_history WHERE version='31'")).isEqualTo("0");execute(c,"TRUNCATE cinenest_identity.account CASCADE");observe("A empty READ COMMITTED account reset","SUCCESS","SUCCESS");
            rejected(c,"TRUNCATE cinenest_pricing.commercial_policy_revision","0A000");
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);rejected(c,tables("TRUNCATE TABLE "),"23514");
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);rejected(c,tables("TRUNCATE TABLE "),"23514");}
    }
    String tables(String prefix){return prefix+"cinenest_pricing.commercial_policy_revision,cinenest_pricing.commercial_policy_review,cinenest_pricing.commercial_policy_command,cinenest_pricing.commercial_policy_audit,cinenest_pricing.commercial_policy_draft";}
    @Test @Order(2) void populatedV35UpgradePreservesCredentialsAcceptedQuotesAndPurchases()throws Exception {
        try(var c=connection(cleanUrl)){execute(c,"CREATE DATABASE commercial_upgrade");}
        var old=flyway(appUrl,"35");assertThat(old.migrate().migrationsExecuted).isEqualTo(34);old.validate();
        seedFixtures();String before=oldSnapshots();legacySnapshot=before;Files.writeString(run.resolve("legacy-snapshot.sha256"),before);var history=scalar("SELECT jsonb_object_agg(version,checksum)::text FROM public.flyway_schema_history WHERE success AND version IS NOT NULL");
        var upgraded=flyway(appUrl,"36");assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);upgraded.validate();assertThat(upgraded.migrate().migrationsExecuted).isZero();assertThat(oldSnapshots()).isEqualTo(before);
        assertThat(scalar("SELECT jsonb_object_agg(version,checksum)::text FROM public.flyway_schema_history WHERE success AND version IS NOT NULL AND version<>'36'")).isEqualTo(history);
        assertThat(scalar("SELECT count(*) FROM cinenest_pricing.quotation_acceptance")).isEqualTo("1");assertThat(scalar("SELECT count(*) FROM cinenest_booking.booking_revision")).isEqualTo("1");observe("V35 historical snapshots",before,oldSnapshots());
    }
    String oldSnapshots()throws Exception{return digest(scalar("SELECT jsonb_build_array((SELECT jsonb_agg(to_jsonb(t) ORDER BY account_id) FROM cinenest_identity.manual_customer t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY quote_id,revision) FROM cinenest_pricing.quotation_revision t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY quote_id) FROM cinenest_pricing.quotation_acceptance t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY booking_id,revision) FROM cinenest_booking.booking_revision t))::text"));}
    void seedFixtures()throws Exception {
        var encoder=new Pbkdf2PasswordEncoder("",16,600000,256);encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        for(String role:List.of("creator","finance","legal","operations","outsider")){
            sql("INSERT INTO cinenest_identity.account VALUES (?,TRUE,'ELIGIBLE',0)",id(role));
            sql("INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at,verified_at) VALUES (?,?,?,?,clock_timestamp()+INTERVAL '1 hour',clock_timestamp())",id(role),role+"-commercial@example.test","{pbkdf2-v1}"+encoder.encode(System.getenv("CINENEST_SYNTHETIC_PASSWORD")),digest(role+run));
        }
        for(String v:List.of("venue","otherVenue"))sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES (?,?,'Synthetic Commercial Venue','Asia/Colombo')",id(v),v.toUpperCase(Locale.ROOT));
        for(String role:List.of("creator","finance","legal","operations","outsider"))sql("INSERT INTO cinenest_identity.account_scope VALUES (?,?)",id(role),id("venue"));
        sql("INSERT INTO cinenest_identity.account_scope VALUES (?,?)",id("outsider"),id("otherVenue"));
        for(String k:List.of("package","otherPackage"))sql("INSERT INTO cinenest_pricing.package(package_id,venue_id,code) VALUES (?,?,?)",id(k),id(k.equals("package")?"venue":"otherVenue"),k.toUpperCase(Locale.ROOT));
        @SuppressWarnings("unchecked") var grants=(List<Map<String,Object>>)manifest.get("grants");
        for(var g:grants)sql("INSERT INTO cinenest_identity.access_grant(grant_id,account_id,permission,scope_kind,venue_id,state,valid_from,valid_until,revision) VALUES (?,?,?,'VENUE',?,?,?,?,0)",UUID.fromString(g.get("id").toString()),id(g.get("actor").toString()),g.get("permission"),id(g.get("venue").toString()),g.get("state"),Timestamp.from(Instant.parse(g.get("from").toString())),Timestamp.from(Instant.parse(g.get("until").toString())));
        // Synthetic purchased snapshot fixture only; no financial sources, credit or real payments.
        sql("INSERT INTO cinenest_pricing.package_version(version_id,package_id,version_no,source_revision,content_hash,snapshot,policy_refs,author_id,publisher_id,reason) VALUES (?,?,1,0,?,?::jsonb,'{}',?,?,'Synthetic V35 upgrade fixture')",id("version"),id("package"),"a".repeat(64),"{\"synthetic\":true,\"commercialIssuance\":false}",id("creator"),id("finance"));
        sql("INSERT INTO cinenest_pricing.quotation_policy_revision(policy_id,profile,currency,synthetic,tax_minor,bank_fee_minor,gateway_fee_minor,approval_reference) VALUES (?,'SYNTHETIC_FIXED_MINOR_V1','LKR',TRUE,0,0,0,'SYNTHETIC-ONLY')",id("quotePolicy"));
        sql("INSERT INTO cinenest_booking.hold_policy(policy_id,ttl_seconds,rate_count,rate_seconds,approval_reference) VALUES (?,600,10,60,'SYNTHETIC-ONLY')",id("holdPolicy"));
        sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity) VALUES (?,?,'SYNTHETIC_ROOM','Synthetic fixture room',5)",id("room"),id("venue"));
        sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES (?,?,?,'2030-01-01T00:00Z','2030-01-01T03:00Z',0,'2030-01-01T03:00Z',TRUE,'CONFIRMED')",id("block"),id("venue"),id("room"));
        sql("INSERT INTO cinenest_booking.reservation_hold(hold_id,owner_id,block_id,policy_id,guests,configuration,timezone,minimum_policy_ids,created_at,expires_at,state) VALUES (?,?,?,?,1,'SYNTHETIC','Asia/Colombo','',clock_timestamp(),'2035-01-01T00:00Z','CONSUMED')",id("hold"),id("creator"),id("block"),id("holdPolicy"));
        sql("INSERT INTO cinenest_pricing.quotation(quote_id,owner_id,hold_id,venue_id,room_id,package_id,current_revision) VALUES (?,?,?,?,?,?,1)",id("quote"),id("creator"),id("hold"),id("venue"),id("room"),id("package"));
        sql("INSERT INTO cinenest_pricing.quotation_revision(quote_id,revision,version_id,policy_id,hold_revision,expires_at,payment_method,snapshot,content_hash,total_minor) VALUES (?,1,?,?,0,'2035-01-01T00:00Z','BANK',?::jsonb,?,1)",id("quote"),id("version"),id("quotePolicy"),"{\"synthetic\":true,\"commercialIssuance\":false,\"fixture\":\"V35\"}","a".repeat(64));
        sql("INSERT INTO cinenest_pricing.quotation_acceptance(quote_id,revision,actor_id,content_hash,synthetic) VALUES (?,1,?,?,TRUE)",id("quote"),id("creator"),"a".repeat(64));
        sql("INSERT INTO cinenest_booking.booking(booking_id,request_id,hold_id,owner_id,venue_id,state,synthetic) VALUES (?,?,?,?,?,'SIMULATED_CONFIRMED',TRUE)",id("booking"),id("request"),id("hold"),id("creator"),id("venue"));
        sql("INSERT INTO cinenest_booking.booking_revision(booking_id,revision,quote_id,quote_revision,quote_hash,amount_minor,currency,snapshot) VALUES (?,1,?,1,?,1,'LKR',?::jsonb)",id("booking"),id("quote"),"a".repeat(64),"{\"synthetic\":true,\"commercialIssuance\":false,\"fixture\":\"purchased-V35\"}");
        Files.writeString(run.resolve("fixture-provisioning.json"),JSONObjectUtils.toJSONString(Map.of("classification","AUTHORIZED_SYNTHETIC_FIXTURES_NOT_OPERATIONAL_AUTHORITY","accounts",5,"venues",2,"rooms",1,"packages",2,"grants",grants.size(),"manifest",run.resolve("preflight.json").toString())));
    }
    String base(){return "http://127.0.0.1:"+manifest.get("backendPort");}
    String path(UUID p){return "/api/admin/venues/"+id("venue")+"/commercial-policies"+(p==null?"":"/"+p);}
    class Browser {
        final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();String cookie,token;
        HttpResponse<String> request(String method,String path,String body,boolean csrf)throws Exception {
            var q=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(20)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(cookie!=null)q.header("Cookie",cookie);if(body!=null)q.header("Content-Type","application/json");if(csrf&&token!=null)q.header("X-CSRF-TOKEN",token);
            var r=client.send(q.build(),HttpResponse.BodyHandlers.ofString());for(String c:r.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return r;
        }
        void csrf()throws Exception{var r=request("GET","/api/auth/csrf",null,false);assertThat(r.statusCode()).isEqualTo(200);token=JSONObjectUtils.parse(r.body()).get("token").toString();}
        HttpResponse<String> write(String method,String path,Map<String,Object> body)throws Exception {csrf();return request(method,path,JSONObjectUtils.toJSONString(body),true);}
        void login(String role)throws Exception {csrf();var r=request("POST","/api/auth/login",JSONObjectUtils.toJSONString(Map.of("email",role+"-commercial@example.test","password",System.getenv("CINENEST_SYNTHETIC_PASSWORD"))),true);assertThat(r.statusCode()).as("Genuine login "+role).isEqualTo(204);}
    }
    Browser browser(String role)throws Exception{ensureBackend();if(!browsers.containsKey(role)){var b=new Browser();b.login(role);browsers.put(role,b);}return browsers.get(role);}
    Map<String,Object> json(HttpResponse<String> r,int status)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return JSONObjectUtils.parse(r.body());}
    Map<String,Object> document(){var d=new LinkedHashMap<String,Object>();d.put("title","Synthetic qualified policy");d.put("effectiveFromProposal","2030-01-01T00:00:00Z");d.put("effectiveUntilProposal",null);d.put("taxApplicability","NOT_APPLICABLE");d.put("taxType","NONE");d.put("taxRateBasisPoints",null);d.put("taxAmountMinor",null);d.put("taxTreatment","NONE");d.put("taxBasis","NONE");d.put("taxRounding","NONE");d.put("paymentMethods",List.of("BANK"));d.put("feeChannels",List.of("ONLINE"));d.put("fees",List.of(Map.of("method","BANK","channel","ONLINE","type","NONE","amountMinor","0","currency","LKR","refundability","REFUNDABLE")));d.put("cancellationEligibility","NOT_ALLOWED");d.put("cancellationCutoffMinutes",null);d.put("cancellationTimeBasis","VENUE_TIMEZONE");d.put("noShowTreatment","NO_REFUND");d.put("exceptionHandling","NONE");d.put("exceptionNotes","");d.put("refundEligibility","NONE");d.put("refundCalculation","NONE");d.put("refundBasis","NONE");d.put("refundRateBasisPoints",null);d.put("refundAmountMinor",null);d.put("nonRefundableComponents",List.of());d.put("refundDestination","NONE");d.put("suppliedReferences",List.of("SYNTHETIC-NOT-AUTHORITY"));d.put("submittedForReview",true);return d;}
    Map<String,Object> create(){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic qualification","scopeKind","VENUE","document",document()));}
    String historySnapshot()throws Exception{return scalar("SELECT jsonb_build_array((SELECT jsonb_agg(to_jsonb(t) ORDER BY policy_id) FROM cinenest_pricing.commercial_policy_draft t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY policy_id,revision) FROM cinenest_pricing.commercial_policy_revision t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY review_id) FROM cinenest_pricing.commercial_policy_review t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY command_id) FROM cinenest_pricing.commercial_policy_command t),(SELECT jsonb_agg(to_jsonb(t) ORDER BY event_id) FROM cinenest_pricing.commercial_policy_audit t))::text");}
    void ensureBackend()throws Exception {
        if(backend!=null && backend.isAlive())return;
        var repo=Path.of(System.getProperty("user.dir"));
        var args=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-jar",repo.resolve("target/cinenest-0.0.1-SNAPSHOT.jar").toString(),"--spring.datasource.url="+appUrl,"--server.port="+manifest.get("backendPort"),"--spring.profiles.active=local-synthetic","--cinenest.identity.enabled=true","--cinenest.identity.manual.enabled=true","--cinenest.identity.session-timeout-seconds=120","--server.address=127.0.0.1","--cinenest.identity.manual.attempt-limit=5","--cinenest.identity.manual.window-seconds=900","--cinenest.identity.manual.verification-seconds=900","--cinenest.identity.manual.recovery.enabled=false","--cinenest.identity.google.enabled=false","--cinenest.identity.facebook.enabled=false","--cinenest.identity.manual.outbox="+run.resolve("outbox"),"--spring.datasource.hikari.maximum-pool-size=6","--spring.datasource.hikari.minimum-idle=0"));
        backend=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(run.resolve("backend.log").toFile()).start();
        Files.writeString(run.resolve("backend-owned.json"),JSONObjectUtils.toJSONString(Map.of("pid",backend.pid(),"port",manifest.get("backendPort"))));
        boolean ready=false;var probe=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        for(int i=0;i<150;i++){if(!backend.isAlive())break;try{if(probe.send(HttpRequest.newBuilder(URI.create(base()+"/api/auth/csrf")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200){ready=true;break;}}catch(Exception notReady){}Thread.sleep(100);}
        assertThat(ready).as("Production backend ready; inspect sanitized backend.log").isTrue();
    }
    String cleanHistorySnapshot()throws Exception {try(var c=connection(cleanUrl)){return scalar(c,"SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.commercial_policy_draft),(SELECT count(*) FROM cinenest_pricing.commercial_policy_revision),(SELECT count(*) FROM cinenest_pricing.commercial_policy_review),(SELECT count(*) FROM cinenest_pricing.commercial_policy_command),(SELECT jsonb_agg(to_jsonb(t) ORDER BY event_id) FROM cinenest_pricing.commercial_policy_audit t))::text");}}
    @Test @Order(3) void synchronizedEmptyTruncateAndCreatePreserveCommittedHistory()throws Exception {
        // Use the clean database: no additional accounts/grants and no deletion of the prior API result.
        try(var c=connection(cleanUrl)){assertThat(scalar(c,"SELECT count(*) FROM cinenest_pricing.commercial_policy_audit")).isEqualTo("0");}
        UUID event=UUID.randomUUID(),correlation=UUID.randomUUID();
        Files.writeString(run.resolve("concurrency-probe-manifest.json"),JSONObjectUtils.toJSONString(Map.of("eventId",event.toString(),"correlationId",correlation.toString(),"classification","SYNTHETIC_SECURITY_PROBE_NO_HUMAN_APPROVAL")));
        repeatable=connection(cleanUrl);repeatable.setAutoCommit(false);repeatable.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);scalar(repeatable,"SELECT pg_current_snapshot()::text");
        serializable=connection(cleanUrl);serializable.setAutoCommit(false);serializable.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);scalar(serializable,"SELECT pg_current_snapshot()::text");
        try(var blocker=connection(cleanUrl);var writer=connection(cleanUrl);var pool=Executors.newSingleThreadExecutor()){
            blocker.setAutoCommit(false);execute(blocker,"LOCK TABLE cinenest_pricing.commercial_policy_audit IN ACCESS EXCLUSIVE MODE");
            int writerPid=Integer.parseInt(scalar(writer,"SELECT pg_backend_pid()"));
            var future=pool.submit(()->{writer.setAutoCommit(false);execute(writer,"INSERT INTO cinenest_pricing.commercial_policy_audit(event_id,operation,outcome,reason,correlation_id,occurred_at) VALUES (?,'DENY','DENY','Synthetic concurrent history probe',?,clock_timestamp())",event,correlation);writer.commit();return true;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
            while(System.nanoTime()<deadline){try(var probe=connection(cleanUrl)){if(!"0".equals(scalar(probe,"SELECT count(*) FROM pg_stat_activity WHERE pid="+writerPid+" AND wait_event_type='Lock' AND query LIKE '%INSERT INTO cinenest_pricing.commercial_policy_audit%'"))){waiting=true;break;}}Thread.sleep(20);}
            assertThat(waiting).as("Actual INSERT blocked on the owned audit table lock").isTrue();
            execute(blocker,"TRUNCATE cinenest_pricing.commercial_policy_audit");blocker.commit();assertThat(future.get(10,TimeUnit.SECONDS)).isTrue();
        }
        snapshot=cleanHistorySnapshot();Files.writeString(run.resolve("protected-snapshot.json"),snapshot);
        try(var c=connection(cleanUrl)){assertThat(scalar(c,"SELECT count(*) FROM cinenest_pricing.commercial_policy_audit")).isEqualTo("1");}
        observe("E synchronized empty TRUNCATE then waiting INSERT","committed protected audit survives","committed protected audit survives");
    }
    @Test @Order(4) void staleRepeatableAndSerializableSnapshotsCannotDeleteHistory()throws Exception {
        try{rejected(repeatable,tables("TRUNCATE TABLE "),"23514");rejected(serializable,tables("TRUNCATE TABLE "),"23514");assertThat(cleanHistorySnapshot()).isEqualTo(snapshot);}finally{repeatable.close();repeatable=null;serializable.close();serializable=null;}
    }
    @Test @Order(5) void populatedTruncateCascadeUpdateAndDeleteRejectedWithoutLoss()throws Exception {
        String before=historySnapshot();
        try(var c=db()){
            rejected(c,tables("TRUNCATE TABLE "),"23514");rejected(c,"TRUNCATE cinenest_identity.account CASCADE","23514");
            for(String t:List.of("revision","review","command","audit")){rejected(c,"UPDATE cinenest_pricing.commercial_policy_"+t+" SET "+(t.equals("revision")?"title=title":"reason=reason"),"23514");rejected(c,"DELETE FROM cinenest_pricing.commercial_policy_"+t,"23514");}
        }
        assertThat(historySnapshot()).isEqualTo(before);assertThat(scalar("SELECT count(*) FROM cinenest_identity.manual_customer")).isEqualTo("5");
    }
    @Test @Order(6) void authenticatedCrudReplayConcurrencyAndReview()throws Exception {
        var b=browser("creator");var request=create();var receipt=json(b.write("POST",path(null),request),201);var p=UUID.fromString(receipt.get("policyId").toString());
        assertThat(json(b.write("POST",path(null),request),200).get("commandId")).isEqualTo(receipt.get("commandId"));request.put("reason","Changed payload");assertThat(b.write("POST",path(null),request).statusCode()).isEqualTo(409);
        var detail=json(b.request("GET",path(p),null,false),200);assertThat(detail).containsEntry("status","PENDING_REVIEW").containsEntry("commercialIssuanceEnabled",false).containsEntry("checkoutEnabled",false).containsEntry("customerAccessEnabled",false);
        var revise=new LinkedHashMap<String,Object>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic revise","expectedRevision","1","document",document()));
        try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->b.write("PUT",path(p),revise));var second=pool.submit(()->{var other=new LinkedHashMap<>(revise);other.put("idempotencyKey",UUID.randomUUID().toString());return b.write("PUT",path(p),other);});assertThat(List.of(first.get().statusCode(),second.get().statusCode())).containsExactlyInAnyOrder(200,409);}
        detail=json(b.request("GET",path(p),null,false),200);assertThat(detail.get("revision")).isEqualTo("2");
        var self=review(detail,"FINANCE");assertThat(b.write("POST",path(p)+"/reviews",self).statusCode()).isEqualTo(403);
        for(String role:List.of("finance","legal","operations"))json(browser(role).write("POST",path(p)+"/reviews",review(detail,role.toUpperCase(Locale.ROOT))),200);
        assertThat(json(b.request("GET",path(p),null,false),200).get("status")).isEqualTo("APPROVED");assertThat(((List<?>)json(b.request("GET",path(p)+"/history",null,false),200).get("items"))).hasSize(2);
        String reviewed=historySnapshot();try(var c=db()){
            rejected(c,"UPDATE cinenest_pricing.commercial_policy_review SET reason=reason","23514");
            rejected(c,"DELETE FROM cinenest_pricing.commercial_policy_review","23514");
            rejected(c,tables("TRUNCATE TABLE "),"23514");
        }assertThat(historySnapshot()).isEqualTo(reviewed);
        var stale=review(detail,"LEGAL");stale.put("expectedRevision","1");assertThat(browser("legal").write("POST",path(p)+"/reviews",stale).statusCode()).isEqualTo(409);
    }
    Map<String,Object> review(Map<String,Object> d,String domain){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic domain review, not human approval","expectedRevision",d.get("revision"),"expectedContentHash",d.get("contentHash"),"domain",domain,"outcome","APPROVE","evidenceReference","SYNTHETIC-NOT-AUTHORITY"));}
    @Test @Order(7) void permissionsCsrfStrictInputsAndForgedApprovalFailClosed()throws Exception {
        assertThat(new Browser().request("GET",path(null),null,false).statusCode()).isEqualTo(401);var b=browser("creator");var outsider=browser("outsider");
        assertThat(outsider.request("GET",path(null),null,false).statusCode()).isEqualTo(403);assertThat(outsider.write("POST",path(null),create()).statusCode()).isEqualTo(403);
        assertThat(b.request("GET","/api/admin/venues/"+id("otherVenue")+"/commercial-policies",null,false).statusCode()).isEqualTo(403);
        assertThat(b.request("POST",path(null),JSONObjectUtils.toJSONString(create()),false).statusCode()).isEqualTo(403);
        b.csrf();assertThat(b.request("POST",path(null),"{\"reason\":\"one\",\"reason\":\"two\"}",true).statusCode()).isEqualTo(400);
        var invalid=create();invalid.put("grantId",id("creator").toString());assertThat(b.write("POST",path(null),invalid).statusCode()).isEqualTo(400);
        var cross=create();cross.put("scopeKind","PACKAGE");cross.put("packageId",id("otherPackage").toString());assertThat(b.write("POST",path(null),cross).statusCode()).isEqualTo(400);
        assertThat(browser("finance").write("POST",path(null),create()).statusCode()).isEqualTo(403);
        assertThat(b.request("POST",path(null),"x".repeat(65537),true).statusCode()).isEqualTo(413);
        assertThat(scalar("SELECT count(*) FROM cinenest_pricing.commercial_policy_audit WHERE operation='DENY'")).isNotEqualTo("0");
    }
    @Test @Order(8) void mandatoryAuditFailureRollsBackAllCommandRows()throws Exception {
        var b=browser("creator");String before=historySnapshot();sql("ALTER TABLE cinenest_pricing.commercial_policy_audit ADD CONSTRAINT task_commercial_audit_fault CHECK(FALSE) NOT VALID");
        try{assertThat(b.write("POST",path(null),create()).statusCode()).isEqualTo(503);}finally{sql("ALTER TABLE cinenest_pricing.commercial_policy_audit DROP CONSTRAINT task_commercial_audit_fault");}
        assertThat(historySnapshot()).isEqualTo(before);observe("G audit fault rollback","all records unchanged","all records unchanged");
    }
    @Test @Order(9) void authenticatedBrowserAndResponsiveAdminWorkflow()throws Exception {
        ensureBackend();
        var origin="http://127.0.0.1:"+manifest.get("frontendPort");var node=System.getenv("CINENEST_NODE");var repo=Path.of(System.getProperty("user.dir")).getParent();
        var pb=new ProcessBuilder(node,"node_modules/next/dist/bin/next","start","--hostname","127.0.0.1","--port",manifest.get("frontendPort").toString()).directory(repo.resolve("frontend").toFile()).redirectErrorStream(true).redirectOutput(run.resolve("frontend.log").toFile());pb.environment().put("CINENEST_AUTH_ORIGIN",base());pb.environment().put("NEXT_TELEMETRY_DISABLED","1");pb.environment().put("NODE_OPTIONS","--max-old-space-size=512");frontend=pb.start();Files.writeString(run.resolve("frontend-owned.json"),JSONObjectUtils.toJSONString(Map.of("pid",frontend.pid(),"port",manifest.get("frontendPort"))));
        var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();var readiness=new ArrayList<Map<String,Object>>();boolean ready=false;for(int i=0;i<80;i++){if(!frontend.isAlive())break;try{var response=client.send(HttpRequest.newBuilder(URI.create(origin+"/login")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());readiness.add(Map.of("status",response.statusCode(),"version",response.version().toString()));if(response.statusCode()==200){ready=true;break;}}catch(Exception notReady){readiness.add(Map.of("error",notReady.getClass().getSimpleName()));}Thread.sleep(100);}Files.writeString(run.resolve("frontend-readiness.json"),JSONObjectUtils.toJSONString(Map.of("attempts",readiness)));assertThat(ready).as("Frontend ready; inspect exact HTTP diagnostics").isTrue();
        var browser=new ProcessBuilder(node,"tests/commercial-policies-browser.mjs").directory(repo.resolve("frontend").toFile()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile());browser.environment().put("CINENEST_COMMERCIAL_BROWSER","AUTHORIZED");browser.environment().put("CINENEST_COMMERCIAL_ORIGIN",origin);browserProcess=browser.start();assertThat(browserProcess.waitFor(240,TimeUnit.SECONDS)).as("Bounded browser deadline").isTrue();assertThat(browserProcess.exitValue()).as(Files.readString(run.resolve("browser.log"))).isZero();
    }
    @Test @Order(10) void sessionExpiryAndFinalFlagsPreserveExistingSnapshots()throws Exception {
        var b=browser("outsider");assertThat(b.request("GET","/api/admin/venues/"+ids.get("otherVenue")+"/commercial-policies",null,false).statusCode()).isEqualTo(200);
        String beforeExpiry=scalar("SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.commercial_policy_revision),(SELECT count(*) FROM cinenest_pricing.commercial_policy_review),(SELECT count(*) FROM cinenest_pricing.commercial_policy_command))::text");
        long expiryStarted=System.nanoTime();Thread.sleep(125000);
        assertThat(b.request("GET","/api/admin/venues/"+ids.get("otherVenue")+"/commercial-policies",null,false).statusCode()).isEqualTo(401);
        for(String method:List.of("POST","PUT"))assertThat(b.request(method,"/api/admin/venues/"+ids.get("otherVenue")+"/commercial-policies","{}",true).statusCode()).isIn(401,403);
        assertThat(b.request("POST",path(UUID.randomUUID())+"/reviews","{}",true).statusCode()).isIn(401,403);
        assertThat(scalar("SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.commercial_policy_revision),(SELECT count(*) FROM cinenest_pricing.commercial_policy_review),(SELECT count(*) FROM cinenest_pricing.commercial_policy_command))::text")).isEqualTo(beforeExpiry);
        observe("actual idle session expiry seconds",">=120",TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-expiryStarted));
        b.login("outsider");assertThat(b.request("GET","/api/admin/venues/"+ids.get("otherVenue")+"/commercial-policies",null,false).statusCode()).isEqualTo(200);
        assertThat(scalar("SELECT count(*) FROM cinenest_pricing.commercial_policy_draft WHERE commercial_issuance_enabled OR checkout_enabled OR customer_access_enabled")).isEqualTo("0");assertThat(scalar("SELECT count(*) FROM cinenest_identity.account")).isEqualTo("5");assertThat(scalar("SELECT count(*) FROM cinenest_identity.access_grant")).isEqualTo("15");
        assertThat(oldSnapshots()).isEqualTo(legacySnapshot);observe("final safety","all activation flags FALSE","all activation flags FALSE");
    }
    @AfterAll void close()throws Exception {
        if(repeatable!=null)repeatable.close();if(serializable!=null)serializable.close();
        if(browserProcess!=null && browserProcess.isAlive()){
            var children=browserProcess.descendants().toList();browserProcess.destroy();
            for(var child:children)child.destroy();
            if(!browserProcess.waitFor(3,TimeUnit.SECONDS))browserProcess.destroyForcibly();
            for(var child:children)if(child.isAlive())child.destroyForcibly();
        }
        if(frontend!=null){frontend.destroy();if(!frontend.waitFor(5,TimeUnit.SECONDS))frontend.destroyForcibly();}
        if(backend!=null){backend.destroy();if(!backend.waitFor(5,TimeUnit.SECONDS))backend.destroyForcibly();}
        Files.writeString(run.resolve("application-cleanup.json"),JSONObjectUtils.toJSONString(Map.of("backendClosed",backend==null||!backend.isAlive(),"frontendClosed",frontend==null||!frontend.isAlive())));
    }
}

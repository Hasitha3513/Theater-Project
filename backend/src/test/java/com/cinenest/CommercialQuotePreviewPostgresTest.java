package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.*;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** One manifest-bound disposable database. No database creation/reset and no operational authority. */
@EnabledIfEnvironmentVariable(named="CINENEST_PREVIEW_RUN",matches="/tmp/cn-part1-preview-[^/]+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommercialQuotePreviewPostgresTest {
    static boolean failed;
    @RegisterExtension static final TestWatcher STOP=new TestWatcher(){@Override public void testFailed(ExtensionContext c,Throwable e){failed=true;}};
    final Path run=Path.of(System.getenv().getOrDefault("CINENEST_PREVIEW_RUN","/tmp/cn-part1-preview-NOT-AUTHORIZED"));
    final Path repository=Path.of(System.getProperty("user.dir")).getParent();
    final String appUrl=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");
    Map<String,Object> manifest;Map<String,String> ids;Map<String,Browser> browsers=new HashMap<>();
    Process backend,frontend,browserProcess;String legacyDigest;Map<String,Object> hold,policyDetail;UUID policy;
    @BeforeEach void failStop(TestInfo info)throws Exception{
        boolean independentAudit=info.getTestMethod().map(m->m.getName().equals("browserAuditImmutablePurchaseAndProtectionRemainIntact")).orElse(false)
            &&backend!=null&&backend.isAlive()&&Files.exists(run.resolve("room-blocks-results.json"))
            &&"PASS".equals(JSONObjectUtils.parse(Files.readString(run.resolve("room-blocks-results.json"))).get("status"));
        Assumptions.assumeFalse(failed&&!independentAudit,"Earlier mandatory prerequisite failed; no automatic retry");
    }
    @SuppressWarnings("unchecked") @BeforeAll void setup()throws Exception {
        assertThat(Files.isSymbolicLink(run)).isFalse();assertThat(run.toRealPath()).isEqualTo(run);
        manifest=JSONObjectUtils.parse(Files.readString(run.resolve("preflight.json")));ids=(Map<String,String>)manifest.get("fixtureIds");
        assertThat(manifest.get("runId")).isEqualTo(run.getFileName().toString());assertThat(manifest.get("database")).isEqualTo("cinenest_test");
        assertThat(ids).hasSize(30);assertThat(new HashSet<>(ids.values())).hasSize(30);ids.values().forEach(UUID::fromString);
        assertThat(appUrl).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test");
        assertThat(System.getenv("CINENEST_SYNTHETIC_PASSWORD")).isNotBlank();
        var migrations=(List<Map<String,Object>>)manifest.get("migrations");assertThat(migrations).hasSize(35);
        for(var m:migrations){var p=repository.resolve(m.get("path").toString());assertThat(sha(Files.readAllBytes(p))).as(p.toString()).isEqualTo(m.get("sha256"));}
        assertThat(migrations.stream().noneMatch(m->m.get("path").toString().contains("/V31__"))).isTrue();
        String manifestHash=sha(Files.readAllBytes(run.resolve("preflight.json")));Path marker=run.resolve("preview-fixture-marker.json");
        var flyway=Flyway.configure().dataSource(appUrl,user,password).target("36").load();flyway.migrate();flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("36");assertThat(scalar("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL")).isEqualTo("35");
        assertThat(scalar("SELECT count(*) FROM public.flyway_schema_history WHERE version='31'")).isEqualTo("0");
        if(Files.exists(marker)){
            var prior=JSONObjectUtils.parse(Files.readString(marker));assertThat(prior.get("runId")).isEqualTo(manifest.get("runId"));assertThat(prior.get("manifestSha256")).isEqualTo(manifestHash);assertThat(prior.get("database")).isEqualTo(appUrl);legacyDigest=prior.get("legacyDigest").toString();verifyFixtures();
        }else{
            assertThat(scalar("SELECT count(*) FROM cinenest_identity.account")).isEqualTo("0");seed();legacyDigest=legacySnapshot();
            evidence("preview-fixture-marker.json",Map.of("runId",manifest.get("runId"),"manifestSha256",manifestHash,"database",appUrl,"legacyDigest",legacyDigest,"fixtureIds",ids,"classification","SYNTHETIC_FIXTURES_NOT_OPERATIONAL_AUTHORITY"));
        }
        verifyFixtures();
        assertThat(scalar("SELECT count(*) FROM cinenest_identity.access_grant WHERE account_id='"+id("outsider")+"' AND venue_id='"+id("venue")+"' AND state='ACTIVE' AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()")).as("Negative actor has no effective primary venue grant").isEqualTo("0");
        var spec=new LinkedHashMap<String,Object>();spec.put("frontendOrigin","http://127.0.0.1:"+manifest.get("frontendPort"));spec.put("fixtureIds",ids);
        spec.put("preview",Map.of("venueId",ids.get("venue"),"roomId",ids.get("room"),"packageId",ids.get("package"),"packageVersionId",ids.get("version"),"sessionStartLocal","2030-02-02T10:00","durationMinutes",180,"guests",4));if(Files.exists(run.resolve("room-blocks-results.json"))){var priorRoom=JSONObjectUtils.parse(Files.readString(run.resolve("room-blocks-results.json")));if("PASS".equals(priorRoom.get("status")))spec.put("roomBlockingEvidenceSha256",sha(Files.readAllBytes(run.resolve("room-blocks-results.json"))));}evidence("runtime-spec.json",spec);
    }
    UUID id(String key){return UUID.fromString(ids.get(key));}
    Connection db()throws SQLException{return DriverManager.getConnection(appUrl,user,password);}
    void sql(String text,Object...args)throws SQLException{try(var c=db();var q=c.prepareStatement(text)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.execute();}}
    String scalar(String text)throws SQLException{try(var c=db();var q=c.createStatement();var r=q.executeQuery(text)){assertThat(r.next()).isTrue();return r.getString(1);}}
    String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    String digest(String s)throws Exception{return sha(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void evidence(String name,Map<String,?> content)throws Exception{var p=run.resolve(name);Files.writeString(p,JSONObjectUtils.toJSONString(content));Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rw-------"));}
    @SuppressWarnings("unchecked") void verifyFixtures()throws Exception{
        assertThat(scalar("SELECT count(*) FROM cinenest_identity.account")).isEqualTo("5");assertThat(scalar("SELECT count(*) FROM cinenest_identity.access_grant")).isEqualTo("17");
        for(String role:List.of("creator","finance","legal","operations","outsider"))assertThat(scalar("SELECT count(*) FROM cinenest_identity.manual_customer WHERE account_id='"+id(role)+"' AND email='"+role+"-commercial@example.test' AND verified_at IS NOT NULL")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM cinenest_rooms.venue")).isEqualTo("2");
        assertThat(scalar("SELECT count(*) FROM cinenest_rooms.room")).isEqualTo("2");
        assertThat(scalar("SELECT count(*) FROM cinenest_pricing.package")).isEqualTo("2");
        for(boolean other:List.of(false,true)){
            String venue=other?"otherVenue":"venue",room=other?"otherRoom":"room",pkg=other?"otherPackage":"package";
            assertThat(scalar("SELECT count(*) FROM cinenest_rooms.venue WHERE venue_id='"+id(venue)+"' AND timezone='Asia/Colombo' AND enabled")).isEqualTo("1");
            assertThat(scalar("SELECT count(*) FROM cinenest_rooms.room WHERE room_id='"+id(room)+"' AND venue_id='"+id(venue)+"' AND capacity=5 AND enabled")).isEqualTo("1");
            assertThat(scalar("SELECT count(*) FROM cinenest_pricing.package WHERE package_id='"+id(pkg)+"' AND venue_id='"+id(venue)+"'")).isEqualTo("1");
        }
        var grants=(List<Map<String,Object>>)manifest.get("grants");assertThat(grants).hasSize(17);
        for(var g:grants){
            UUID grant=UUID.fromString(g.get("id").toString());
            try(var c=db();var q=c.prepareStatement("SELECT count(*) FROM cinenest_identity.access_grant WHERE grant_id=? AND account_id=? AND permission=? AND scope_kind='VENUE' AND venue_id=? AND state=? AND valid_from=? AND valid_until=? AND revision=0")){
                q.setObject(1,grant);q.setObject(2,id(g.get("actor").toString()));q.setString(3,g.get("permission").toString());q.setObject(4,id(g.get("venue").toString()));q.setString(5,g.get("state").equals("EXPIRED_FIXTURE")?"ACTIVE":g.get("state").toString());q.setTimestamp(6,Timestamp.from(Instant.parse(g.get("from").toString())));q.setTimestamp(7,Timestamp.from(Instant.parse(g.get("until").toString())));
                try(var r=q.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).as("Exact manifest grant "+grant).isEqualTo(1);}
            }
        }
        assertThat(legacySnapshot()).isEqualTo(legacyDigest);
    }
    @SuppressWarnings("unchecked") void seed()throws Exception{
        var encoder=new Pbkdf2PasswordEncoder("",16,600000,256);encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        for(String role:List.of("creator","finance","legal","operations","outsider")){
            sql("INSERT INTO cinenest_identity.account VALUES (?,TRUE,'ELIGIBLE',0)",id(role));
            sql("INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at,verified_at) VALUES (?,?,?,?,clock_timestamp()+INTERVAL '1 hour',clock_timestamp())",id(role),role+"-commercial@example.test","{pbkdf2-v1}"+encoder.encode(System.getenv("CINENEST_SYNTHETIC_PASSWORD")),digest(role+run));
        }
        for(String v:List.of("venue","otherVenue")){
            sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone,cleaning_buffer_minutes,enabled) VALUES (?,?,'Synthetic Commercial Venue','Asia/Colombo',15,TRUE)",id(v),v.toUpperCase(Locale.ROOT));
            sql("INSERT INTO cinenest_rooms.venue_operating_window SELECT ?,day,0,1440 FROM generate_series(1,7) day",id(v));
            boolean primary=v.equals("venue");String room=primary?"room":"otherRoom";
            sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled) VALUES (?,?,'SYNTHETIC_ROOM','Synthetic Cinema Room',5,TRUE)",id(room),id(v));
            sql("INSERT INTO cinenest_rooms.room_facility VALUES (?,'HDMI','Synthetic HDMI',0)",id(room));
            sql("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES (?,?,1,180,clock_timestamp()-INTERVAL '1 hour','SYNTHETIC ONLY',?)",id(primary?"minimumPolicy":"otherMinimumPolicy"),id(v),id("creator"));
        }
        for(String role:List.of("creator","finance","legal","operations","outsider"))sql("INSERT INTO cinenest_identity.account_scope VALUES (?,?)",id(role),id("venue"));
        sql("INSERT INTO cinenest_identity.account_scope VALUES (?,?)",id("outsider"),id("otherVenue"));
        var grants=(List<Map<String,Object>>)manifest.get("grants");assertThat(grants).hasSize(17);assertThat(grants.stream().map(g->g.get("id")).distinct().count()).isEqualTo(17);
        for(var g:grants){Instant from=Instant.parse(g.get("from").toString()),until=Instant.parse(g.get("until").toString());assertThat(Duration.between(from,until)).isLessThanOrEqualTo(Duration.ofMinutes(30));
            sql("INSERT INTO cinenest_identity.access_grant(grant_id,account_id,permission,scope_kind,venue_id,state,valid_from,valid_until,revision) VALUES (?,?,?,'VENUE',?,?,?,?,0)",UUID.fromString(g.get("id").toString()),id(g.get("actor").toString()),g.get("permission"),id(g.get("venue").toString()),g.get("state").equals("EXPIRED_FIXTURE")?"ACTIVE":g.get("state"),Timestamp.from(from),Timestamp.from(until));}
        sql("INSERT INTO cinenest_pricing.service_definition VALUES (?,?,'HDMI-SERVICE','Synthetic HDMI equipment','HDMI',TRUE,'SYNTHETIC fixture not physical qualification')",id("service"),id("venue"));
        sql("INSERT INTO cinenest_pricing.service_rate_version(rate_version_id,service_id,revision,currency,unit_price_minor,pricing_basis) VALUES (?,?,1,'LKR',100000,'PER_SESSION')",id("serviceRate"),id("service"));
        publication(false);publication(true);
        sql("INSERT INTO cinenest_pricing.quotation_policy_revision(policy_id,profile,currency,synthetic,tax_minor,bank_fee_minor,gateway_fee_minor,approval_reference) VALUES (?,'SYNTHETIC_FIXED_MINOR_V1','LKR',TRUE,0,0,0,'SYNTHETIC-ONLY')",id("legacyQuotePolicy"));
        sql("INSERT INTO cinenest_booking.hold_policy(policy_id,ttl_seconds,rate_count,rate_seconds,approval_reference) VALUES (?,600,10,60,'SYNTHETIC-ONLY')",id("holdPolicy"));
        sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES (?,?,?,'2030-01-01T00:00Z','2030-01-01T03:00Z',0,'2030-01-01T03:00Z',TRUE,'CONFIRMED')",id("legacyBlock"),id("venue"),id("room"));
        sql("INSERT INTO cinenest_booking.reservation_hold(hold_id,owner_id,block_id,policy_id,guests,configuration,timezone,minimum_policy_ids,created_at,expires_at,state) VALUES (?,?,?,?,1,'SYNTHETIC','Asia/Colombo','',clock_timestamp(),'2035-01-01T00:00Z','CONSUMED')",id("legacyHold"),id("creator"),id("legacyBlock"),id("holdPolicy"));
        sql("INSERT INTO cinenest_pricing.quotation(quote_id,owner_id,hold_id,venue_id,room_id,package_id,current_revision) VALUES (?,?,?,?,?,?,1)",id("legacyQuote"),id("creator"),id("legacyHold"),id("venue"),id("room"),id("package"));
        sql("INSERT INTO cinenest_pricing.quotation_revision(quote_id,revision,version_id,policy_id,hold_revision,expires_at,payment_method,snapshot,content_hash,total_minor) VALUES (?,1,?,?,0,'2035-01-01T00:00Z','BANK',?::jsonb,?,1)",id("legacyQuote"),id("version"),id("legacyQuotePolicy"),"{\"synthetic\":true,\"commercialIssuance\":false,\"fixture\":\"V35\"}","a".repeat(64));
        sql("INSERT INTO cinenest_pricing.quotation_acceptance(quote_id,revision,actor_id,content_hash,synthetic) VALUES (?,1,?,?,TRUE)",id("legacyQuote"),id("creator"),"a".repeat(64));
        sql("INSERT INTO cinenest_booking.booking(booking_id,request_id,hold_id,owner_id,venue_id,state,synthetic) VALUES (?,?,?,?,?,'SIMULATED_CONFIRMED',TRUE)",id("legacyBooking"),id("legacyRequest"),id("legacyHold"),id("creator"),id("venue"));
        sql("INSERT INTO cinenest_booking.booking_revision(booking_id,revision,quote_id,quote_revision,quote_hash,amount_minor,currency,snapshot) VALUES (?,1,?,1,?,1,'LKR',?::jsonb)",id("legacyBooking"),id("legacyQuote"),"a".repeat(64),"{\"synthetic\":true,\"commercialIssuance\":false,\"fixture\":\"purchased-V35\"}");
        sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES (?,?,?,'2030-02-03T04:30Z','2030-02-03T07:30Z',15,'2030-02-03T07:45Z',TRUE,'CONFIRMED')",id("confirmedBlock"),id("venue"),id("room"));
    }
    void publication(boolean other)throws Exception{
        String pkg=other?"otherPackage":"package",venue=other?"otherVenue":"venue",room=other?"otherRoom":"room",version=other?"otherVersion":"version";
        sql("INSERT INTO cinenest_pricing.package(package_id,venue_id,code) VALUES (?,?,?)",id(pkg),id(venue),pkg.toUpperCase(Locale.ROOT));
        var snapshot=new LinkedHashMap<String,Object>();snapshot.putAll(Map.of("name","Synthetic Preview Package","currency","LKR","basePriceMinor","250000","includedMinutes",180,"minimumMinutes",180,"maximumMinutes",360,"extensionsEnabled",true,"extensionStepMinutes",60,"extensionPriceMinor","100000","includedGuests",4));snapshot.put("description","Synthetic noncommercial package");snapshot.put("maximumGuests",5);snapshot.put("extraGuestPriceMinor","80000");snapshot.put("roomIds",List.of(ids.get(room)));
        var option=Map.of("serviceId",ids.get("service"),"rateVersionId",ids.get("serviceRate"),"label","Synthetic HDMI equipment","requiredFacilityCode","HDMI","mode","OPTIONAL","pricingBasis","PER_SESSION","currency","LKR","unitPriceMinor","100000","additionalChargeMinor","100000");snapshot.put("options",other?List.of():List.of(option));
        sql("INSERT INTO cinenest_pricing.package_version(version_id,package_id,version_no,source_revision,content_hash,snapshot,policy_refs,author_id,publisher_id,reason) VALUES (?,?,1,0,?,?::jsonb,'{}',?,?,'Synthetic fixture not commercial approval')",id(version),id(pkg),(other?"b":"a").repeat(64),JSONObjectUtils.toJSONString(snapshot),id("creator"),id("finance"));
        sql("INSERT INTO cinenest_pricing.package_version_room VALUES (?,?)",id(version),id(room));
        if(!other)sql("INSERT INTO cinenest_pricing.package_version_option VALUES (?,?,?,?::jsonb)",id(version),id("service"),id("serviceRate"),JSONObjectUtils.toJSONString(option));
        sql("INSERT INTO cinenest_pricing.package_publication(publication_id,version_id,package_id,room_ids,sales_from,sales_to,session_from,session_to,timezone,local_dates,state,idempotency_key,request_hash) VALUES (?,?,?,ARRAY[?]::uuid[],clock_timestamp()-INTERVAL '1 day',clock_timestamp()+INTERVAL '1 day','2030-01-01T00:00Z','2031-01-01T00:00Z','Asia/Colombo','{}','ACTIVE',?,?)",id(other?"otherPublication":"publication"),id(version),id(pkg),id(room),id(other?"otherPublicationKey":"publicationKey"),"a".repeat(64));
    }
    String legacySnapshot()throws Exception{return digest(scalar("SELECT jsonb_build_array((SELECT jsonb_agg(to_jsonb(t) ORDER BY account_id) FROM cinenest_identity.manual_customer t),(SELECT to_jsonb(t) FROM cinenest_pricing.quotation_revision t WHERE quote_id='"+id("legacyQuote")+"'),(SELECT to_jsonb(t) FROM cinenest_pricing.quotation_acceptance t WHERE quote_id='"+id("legacyQuote")+"'),(SELECT to_jsonb(t) FROM cinenest_booking.booking_revision t WHERE booking_id='"+id("legacyBooking")+"'))::text"));}
    String quoteCounts()throws Exception{return scalar("SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.quotation),(SELECT count(*) FROM cinenest_pricing.quotation_revision),(SELECT count(*) FROM cinenest_pricing.quotation_acceptance),(SELECT count(*) FROM cinenest_pricing.quotation_command) )::text");}
    String base(){return "http://127.0.0.1:"+manifest.get("backendPort");}
    String path(UUID p){return "/api/admin/venues/"+id("venue")+"/commercial-policies"+(p==null?"":"/"+p);}
    class Browser {
        final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();String cookie,token;
        HttpResponse<String> request(String method,String path,String body,boolean csrf)throws Exception {
            var q=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(20)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(cookie!=null)q.header("Cookie",cookie);if(body!=null)q.header("Content-Type","application/json");if(csrf&&token!=null)q.header("X-CSRF-TOKEN",token);
            var r=client.send(q.build(),HttpResponse.BodyHandlers.ofString());for(String c:r.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return r;
        }
        void csrf()throws Exception{var r=request("GET","/api/auth/csrf",null,false);assertThat(r.statusCode()).isEqualTo(200);token=JSONObjectUtils.parse(r.body()).get("token").toString();}
        HttpResponse<String> write(String method,String path,Map<String,Object> body)throws Exception {csrf();return request(method,path,JSONObjectUtils.toJSONString(body),true);}
        void login(String role)throws Exception {csrf();var r=request("POST","/api/auth/login",JSONObjectUtils.toJSONString(Map.of("email",role+"-commercial@example.test","password",System.getenv("CINENEST_SYNTHETIC_PASSWORD"))),true);assertThat(r.statusCode()).as("Genuine login "+role).isEqualTo(204);}
    }
    Browser browser(String role)throws Exception{startBackend();if(!browsers.containsKey(role)){var b=new Browser();b.login(role);browsers.put(role,b);}return browsers.get(role);}
    Map<String,Object> json(HttpResponse<String> r,int status)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return JSONObjectUtils.parse(r.body());}
    Map<String,Object> document(){var d=new LinkedHashMap<String,Object>();d.put("title","Synthetic qualified policy");d.put("effectiveFromProposal","2030-01-01T00:00:00Z");d.put("effectiveUntilProposal",null);d.put("taxApplicability","NOT_APPLICABLE");d.put("taxType","NONE");d.put("taxRateBasisPoints",null);d.put("taxAmountMinor",null);d.put("taxTreatment","NONE");d.put("taxBasis","NONE");d.put("taxRounding","NONE");d.put("paymentMethods",List.of("BANK"));d.put("feeChannels",List.of("ONLINE"));d.put("fees",List.of(Map.of("method","BANK","channel","ONLINE","type","NONE","amountMinor","0","currency","LKR","refundability","REFUNDABLE")));d.put("cancellationEligibility","NOT_ALLOWED");d.put("cancellationCutoffMinutes",null);d.put("cancellationTimeBasis","VENUE_TIMEZONE");d.put("noShowTreatment","NO_REFUND");d.put("exceptionHandling","NONE");d.put("exceptionNotes","");d.put("refundEligibility","NONE");d.put("refundCalculation","NONE");d.put("refundBasis","NONE");d.put("refundRateBasisPoints",null);d.put("refundAmountMinor",null);d.put("nonRefundableComponents",List.of());d.put("refundDestination","NONE");d.put("suppliedReferences",List.of("SYNTHETIC-NOT-AUTHORITY"));d.put("submittedForReview",true);return d;}
    Map<String,Object> create(){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic qualification","scopeKind","VENUE","document",document()));}

    Map<String,Object> review(Map<String,Object> d,String domain){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic domain review, not human approval","expectedRevision",d.get("revision"),"expectedContentHash",d.get("contentHash"),"domain",domain,"outcome","APPROVE","evidenceReference","SYNTHETIC-NOT-AUTHORITY"));}

    String query(Map<String,Object> h){return "/api/quotes/preview?holdId="+h.get("holdId")+"&expectedHoldRevision="+h.get("revision")+"&packageId="+id("package")+"&packageVersionId="+id("version")+"&paymentMethod=BANK&optionalServiceIds=";}
    Map<String,Object> hold(Browser b,String local)throws Exception{
        var a=json(b.request("GET","/api/rooms/"+id("room")+"/availability?sessionStartLocal="+local+"&durationMinutes=180&guests=4",null,false),200);assertThat(a.get("available")).isEqualTo(true);
        return json(b.write("POST","/api/holds",Map.of("roomId",ids.get("room"),"sessionStartLocal",local,"durationMinutes",180,"guests",4,"expectedConfiguration",a.get("configuration"),"idempotencyKey",UUID.randomUUID().toString())),200);
    }
    void release(Browser b,Map<String,Object> h)throws Exception{json(b.write("POST","/api/holds/"+h.get("holdId")+"/release",Map.of("expectedRevision",h.get("revision"),"idempotencyKey",UUID.randomUUID().toString())),200);}
    void flags(Map<String,Object> p){assertThat(p).containsEntry("classification","SYNTHETIC_PREVIEW_NOT_OFFER").containsEntry("commerciallyEligible",false).containsEntry("commercialIssuanceEnabled",false).containsEntry("checkoutEnabled",false).containsEntry("customerAccessEnabled",false);assertThat(p).doesNotContainKeys("quoteId","totalMinor","payableTotal");}
    @Test @Order(1) void realFlywayAndPinnedFixtures()throws Exception{verifyFixtures();assertThat(scalar("SELECT count(*) FROM cinenest_rooms.room")).isEqualTo("2");assertThat(scalar("SELECT count(*) FROM cinenest_pricing.package")).isEqualTo("2");evidence("migration-results.json",Map.of("schemaHead","36","successfulMigrations",35,"v31Absent",true,"databaseCount",1,"flywayValidation","PASS"));}
    @Test @Order(2) void previewIsReadOnlyAndUnknownCommercialValuesNeverBecomeZero()throws Exception{
        var b=browser("creator");String priorHold=scalar("SELECT coalesce((SELECT hold_id::text FROM cinenest_booking.reservation_hold WHERE owner_id='"+id("creator")+"' AND state='ACTIVE'),'')");hold=priorHold.isEmpty()?hold(b,"2030-01-30T10:00"):json(b.request("GET","/api/holds/"+UUID.fromString(priorHold),null,false),200);String before=quoteCounts();String auditBefore=scalar("SELECT count(*) FROM cinenest_pricing.quotation_audit");var p=json(b.request("GET",query(hold),null,false),200);flags(p);assertThat(p.get("knownComponentsMinor")).isEqualTo("250000");assertThat(((List<?>)p.get("blockers")).contains("POLICY_NOT_CONFIGURED")).isTrue();assertThat(quoteCounts()).isEqualTo(before);assertThat(scalar("SELECT count(*) FROM cinenest_pricing.quotation_audit")).isEqualTo(auditBefore);evidence("preview-no-write-results.json",Map.of("response",p,"quotationCountsBefore",before,"quotationCountsAfter",quoteCounts()));
    }
    @Test @Order(3) void reviewedDraftIsNotAuthorityAndStalePinsAreRejected()throws Exception{
        var b=browser("creator");var receipt=json(b.write("POST",path(null),create()),201);policy=UUID.fromString(receipt.get("policyId").toString());policyDetail=json(b.request("GET",path(policy),null,false),200);
        assertThat(b.write("POST",path(policy)+"/reviews",review(policyDetail,"FINANCE")).statusCode()).isEqualTo(403);
        for(String role:List.of("finance","legal","operations"))json(browser(role).write("POST",path(policy)+"/reviews",review(policyDetail,role.toUpperCase(Locale.ROOT))),200);
        policyDetail=json(b.request("GET",path(policy),null,false),200);assertThat(policyDetail.get("status")).isEqualTo("APPROVED");
        var p=json(b.request("GET",query(hold),null,false),200);flags(p);assertThat(((Map<?,?>)p.get("policyCandidate")).get("draftReviewStatus")).isEqualTo("APPROVED");
        String pinned=query(hold)+"&policyId="+policy+"&policyRevision="+policyDetail.get("revision")+"&policyContentHash="+policyDetail.get("contentHash");json(b.request("GET",pinned,null,false),200);
        assertThat(b.request("GET",pinned.replace(policyDetail.get("contentHash").toString(),"f".repeat(64)),null,false).statusCode()).isEqualTo(409);
        json(b.write("PUT",path(policy),Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic changed draft","expectedRevision",policyDetail.get("revision"),"document",document())),200);
        assertThat(b.request("GET",pinned,null,false).statusCode()).isEqualTo(409);
    }
    @Test @Order(4) void ownershipVenueQueryAndRevisionDenialsKeepQuotesUnchanged()throws Exception{
        var b=browser("creator");String before=quoteCounts();assertThat(new Browser().request("GET",query(hold),null,false).statusCode()).isEqualTo(401);
        assertThat(browser("outsider").request("GET",query(hold),null,false).statusCode()).isEqualTo(404);
        assertThat(b.request("GET",query(hold)+"&grantId="+id("creator"),null,false).statusCode()).isEqualTo(400);
        assertThat(b.request("GET",query(hold)+"&holdId="+hold.get("holdId"),null,false).statusCode()).isEqualTo(400);
        assertThat(b.request("GET",query(hold).replace("expectedHoldRevision=0","expectedHoldRevision=1"),null,false).statusCode()).isEqualTo(409);
        assertThat(b.request("GET",query(hold).replace(ids.get("version"),ids.get("otherVersion")),null,false).statusCode()).isEqualTo(409);
        assertThat(quoteCounts()).isEqualTo(before);assertThat(legacySnapshot()).isEqualTo(legacyDigest);
    }
    @Test @Order(5) void legacySyntheticQuoteCreateReviseAcceptStillUsesOriginalTaxAndDisclosures()throws Exception{
        var b=browser("creator");var request=new LinkedHashMap<String,Object>();request.putAll(Map.of("holdId",hold.get("holdId"),"packageId",ids.get("package"),"packageVersionId",ids.get("version"),"expectedHoldRevision",hold.get("revision"),"optionalServiceIds",List.of(),"paymentMethod","BANK","idempotencyKey",UUID.randomUUID().toString()));
        var q=json(b.write("POST","/api/quotes",request),200);assertThat(q.get("totalMinor")).isEqualTo("250007");String endpoint="/api/quotes/"+q.get("quoteId");
        q=json(b.write("POST",endpoint+"/revisions",Map.of("expectedRevision",q.get("revision"),"optionalServiceIds",List.of(ids.get("service")),"paymentMethod","GATEWAY","idempotencyKey",UUID.randomUUID().toString())),200);assertThat(q.get("totalMinor")).isEqualTo("350007");
        q=json(b.write("POST",endpoint+"/accept",Map.of("expectedRevision",q.get("revision"),"contentHash",q.get("contentHash"),"disclosures",q.get("disclosures"),"idempotencyKey",UUID.randomUUID().toString())),200);assertThat(q.get("state")).isEqualTo("ACCEPTED");assertThat(q.get("commercialIssuance")).isEqualTo(false);assertThat(q.get("checkoutEnabled")).isEqualTo(false);
        assertThat(json(b.request("GET",endpoint,null,false),200).get("totalMinor")).isEqualTo("350007");release(b,hold);
        assertThat(b.request("GET",query(hold),null,false).statusCode()).isEqualTo(409);assertThat(legacySnapshot()).isEqualTo(legacyDigest);
    }
    @Test @Order(6) void oneAuthenticatedBrowserContextExercisesRoomBlockingAndPreview()throws Exception{
        startBackend();String origin="http://127.0.0.1:"+manifest.get("frontendPort"),node=System.getenv("CINENEST_NODE");
        var p=new ProcessBuilder(node,"node_modules/next/dist/bin/next","start","--hostname","127.0.0.1","--port",manifest.get("frontendPort").toString()).directory(repository.resolve("frontend").toFile()).redirectErrorStream(true).redirectOutput(run.resolve("frontend.log").toFile());sanitize(p,false);p.environment().put("CINENEST_AUTH_ORIGIN",base());p.environment().put("NEXT_TELEMETRY_DISABLED","1");p.environment().put("NODE_OPTIONS","--max-old-space-size=512");Files.createFile(run.resolve("frontend-launch-once"));frontend=p.start();evidence("frontend-owned.json",Map.of("pid",frontend.pid(),"port",manifest.get("frontendPort")));ready(origin+"/login",frontend);
        var command=new ProcessBuilder(node,"tests/quotes-browser.mjs","--authorized-real-stack",run.toString()).directory(repository.resolve("frontend").toFile()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile());sanitize(command,true);command.environment().put("CINENEST_PREVIEW_BROWSER_MODE","AUTHORIZED");Files.createFile(run.resolve("browser-launch-once"));browserProcess=command.start();evidence("browser-owned.json",Map.of("pid",browserProcess.pid()));assertThat(browserProcess.waitFor(240,TimeUnit.SECONDS)).as("Single bounded browser invocation").isTrue();assertThat(browserProcess.exitValue()).as(Files.readString(run.resolve("browser.log"))).isZero();
    }
    @Test @Order(7) void browserAuditImmutablePurchaseAndProtectionRemainIntact()throws Exception{
        var authenticated=browser("creator");
        Map<String,Object> blockReceipt;
        if(Files.exists(run.resolve("room-blocks-results.json"))){
            var roomResult=JSONObjectUtils.parse(Files.readString(run.resolve("room-blocks-results.json")));assertThat(roomResult.get("status")).isEqualTo("PASS");
            blockReceipt=(Map<String,Object>)roomResult.get("blockReceipt");
        }else{
            String blocks="/api/admin/venues/"+id("venue")+"/rooms/"+id("room")+"/blocks";
            var context=json(authenticated.request("GET",blocks,null,false),200);
            blockReceipt=json(authenticated.write("POST",blocks,Map.of("idempotencyKey",UUID.randomUUID().toString(),"startAt","2030-02-01T04:30:00Z","endAt","2030-02-01T06:30:00Z","expectedConfiguration",context.get("configuration"))),200);
            evidence("audit-block-api-receipt.json",Map.of("classification","API_FIXTURE_NOT_BROWSER_OR_OWNER_UAT","receipt",blockReceipt));
        }
        if(policy==null){var created=json(authenticated.write("POST",path(null),create()),201);policy=UUID.fromString(created.get("policyId").toString());}
        UUID createdBlock=UUID.fromString(blockReceipt.get("blockId").toString());
        assertThat(scalar("SELECT count(*) FROM cinenest_booking.room_block_audit WHERE action='CREATE' AND block_id='"+createdBlock+"' AND actor_id='"+id("creator")+"' AND venue_id='"+id("venue")+"' AND room_id='"+id("room")+"'")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM cinenest_booking.room_block_audit WHERE action='CREATE' AND actor_id='"+id("creator")+"' AND venue_id='"+id("venue")+"' AND room_id='"+id("room")+"'")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM cinenest_booking.inventory_block WHERE block_id='"+id("confirmedBlock")+"' AND blocking AND kind='CONFIRMED'")).isEqualTo("1");
        var protectedInterval=json(authenticated.request("GET","/api/rooms/"+id("room")+"/availability?sessionStartLocal=2030-02-03T10:00&durationMinutes=180&guests=4",null,false),200);assertThat(protectedInterval.get("available")).isEqualTo(false);
        assertThat(authenticated.write("POST","/api/holds",Map.of("roomId",ids.get("room"),"sessionStartLocal","2030-02-03T10:00","durationMinutes",180,"guests",4,"expectedConfiguration",protectedInterval.get("configuration"),"idempotencyKey",UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
        String noWriteBefore=quoteCounts();String bookingBefore=scalar("SELECT count(*) FROM cinenest_booking.booking");
        var previewHold=hold(authenticated,"2030-02-02T10:00");String holdCountBefore=scalar("SELECT count(*) FROM cinenest_booking.reservation_hold");
        flags(json(authenticated.request("GET",query(previewHold),null,false),200));
        assertThat(quoteCounts()).isEqualTo(noWriteBefore);assertThat(scalar("SELECT count(*) FROM cinenest_booking.booking")).isEqualTo(bookingBefore);assertThat(scalar("SELECT count(*) FROM cinenest_booking.reservation_hold")).isEqualTo(holdCountBefore);
        release(authenticated,previewHold);
        String policyBefore=scalar("SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.commercial_policy_draft),(SELECT count(*) FROM cinenest_pricing.commercial_policy_revision),(SELECT count(*) FROM cinenest_pricing.commercial_policy_command),(SELECT count(*) FROM cinenest_pricing.commercial_policy_audit))::text");
        sql("ALTER TABLE cinenest_pricing.commercial_policy_audit ADD CONSTRAINT task_preview_audit_fault CHECK(FALSE) NOT VALID");
        try{assertThat(authenticated.write("POST",path(null),create()).statusCode()).isEqualTo(503);}finally{sql("ALTER TABLE cinenest_pricing.commercial_policy_audit DROP CONSTRAINT task_preview_audit_fault");}
        assertThat(scalar("SELECT jsonb_build_array((SELECT count(*) FROM cinenest_pricing.commercial_policy_draft),(SELECT count(*) FROM cinenest_pricing.commercial_policy_revision),(SELECT count(*) FROM cinenest_pricing.commercial_policy_command),(SELECT count(*) FROM cinenest_pricing.commercial_policy_audit))::text")).isEqualTo(policyBefore);
        try(var c=db()){c.setAutoCommit(false);try(var st=c.createStatement()){assertThatThrownBy(()->st.executeUpdate("UPDATE cinenest_pricing.commercial_policy_revision SET title=title")).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));}finally{c.rollback();}}
        assertThat(browser("outsider").request("GET",path(policy)+"/history",null,false).statusCode()).isEqualTo(403);
        evidence("audit-integration.json",Map.of("faultResponse",503,"rollbackCountsUnchanged",true,"immutableHistorySqlstate","23514","wrongVenueHistory",403,"roomBlockId",createdBlock.toString(),"actor",ids.get("creator"),"venue",ids.get("venue")));
        assertThat(legacySnapshot()).isEqualTo(legacyDigest);evidence("final-invariants.json",Map.of("legacyDigest",legacyDigest,"commercialIssuanceEnabled",false,"checkoutEnabled",false,"customerAccessEnabled",false,"accounts",5,"grants",17,"ownerUat","NOT_AUTHORIZED","independentReview","NOT_SATISFIED"));
    }
    void sanitize(ProcessBuilder pb,boolean browser){
        var env=pb.environment();var allowed=Set.of("PATH","HOME","TMPDIR","LANG","LC_ALL","USER","LOGNAME","XDG_RUNTIME_DIR","PLAYWRIGHT_BROWSERS_PATH","PLAYWRIGHT_MODULE_PATH","CINENEST_NODE");
        env.keySet().removeIf(k->!allowed.contains(k)&&!(browser&&k.equals("CINENEST_SYNTHETIC_PASSWORD")));
    }
    void ready(String uri,Process process)throws Exception{var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();boolean ok=false;for(int i=0;i<150;i++){if(!process.isAlive())break;try{if(client.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200){ok=true;break;}}catch(Exception unavailable){}Thread.sleep(100);}assertThat(ok).as("Task-owned loopback readiness").isTrue();}
    void startBackend()throws Exception{
        if(backend!=null){assertThat(backend.isAlive()).as("Existing task-owned backend must not restart automatically").isTrue();return;}
        var args=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-jar",repository.resolve("backend/target/cinenest-0.0.1-SNAPSHOT.jar").toString(),"--server.address=127.0.0.1","--server.port="+manifest.get("backendPort"),"--spring.profiles.active=local-synthetic,synthetic-quotation","--cinenest.identity.enabled=true","--cinenest.identity.manual.enabled=true","--cinenest.identity.session-timeout-seconds=120","--cinenest.identity.manual.attempt-limit=20","--cinenest.identity.manual.window-seconds=900","--cinenest.identity.manual.verification-seconds=900","--cinenest.identity.manual.recovery.enabled=false","--cinenest.identity.google.enabled=false","--cinenest.identity.facebook.enabled=false","--cinenest.identity.manual.outbox="+run.resolve("outbox"),"--spring.datasource.hikari.maximum-pool-size=6","--spring.datasource.hikari.minimum-idle=0","--cinenest.pricing.synthetic-quotes.enabled=true","--cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","--cinenest.pricing.synthetic-quotes.tax-minor=7","--cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC-ONLY","--cinenest.pricing.synthetic-quotes.rate-count=100","--cinenest.pricing.synthetic-quotes.rate-seconds=60","--cinenest.booking.holds.enabled=true","--cinenest.booking.holds.ttl-seconds=600","--cinenest.booking.holds.rate-count=100","--cinenest.booking.holds.rate-seconds=60","--cinenest.booking.holds.approval-reference=SYNTHETIC-ONLY","--cinenest.booking.room-blocks.enabled=true","--cinenest.booking.room-blocks.mode=LOCAL_SYNTHETIC_ONLY","--cinenest.pricing.publication-worker.enabled=false"));
        var pb=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(run.resolve("backend.log").toFile());sanitize(pb,false);pb.environment().put("SPRING_FLYWAY_TARGET","36");pb.environment().put("SPRING_DATASOURCE_URL",appUrl);pb.environment().put("SPRING_DATASOURCE_USERNAME",user);pb.environment().put("SPRING_DATASOURCE_PASSWORD",password);Files.createFile(run.resolve("backend-launch-once"));backend=pb.start();evidence("backend-owned.json",Map.of("pid",backend.pid(),"port",manifest.get("backendPort")));ready(base()+"/api/auth/csrf",backend);
    }
    void stop(Process p)throws Exception{if(p==null)return;var children=p.descendants().toList();children.forEach(ProcessHandle::destroy);p.destroy();if(!p.waitFor(3,TimeUnit.SECONDS))p.destroyForcibly();for(var child:children)if(child.isAlive())child.destroyForcibly();assertThat(p.isAlive()).isFalse();assertThat(children.stream().anyMatch(ProcessHandle::isAlive)).isFalse();}
    @AfterAll void cleanup()throws Exception{try{stop(browserProcess);}finally{try{stop(frontend);}finally{stop(backend);}}evidence("junit-owned-cleanup.json",Map.of("backendStopped",backend==null||!backend.isAlive(),"frontendStopped",frontend==null||!frontend.isAlive(),"browserStopped",browserProcess==null||!browserProcess.isAlive()));}
}

package com.cinenest.payments.internal;
import com.cinenest.identity.ScopedAuthorization;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import static com.cinenest.payments.internal.BankInstructionPublicationRepository.*;

/** In-memory transactional model: verifies orchestration, not PostgreSQL locking/rollback. */
class BankInstructionPublicationServiceTest {
 final UUID author=UUID.randomUUID(),publisher=UUID.randomUUID(),other=UUID.randomUUID(),venue=UUID.randomUUID();
 final ScopedAuthorization auth=mock(ScopedAuthorization.class);final Model model=new Model();BankInstructionPublicationService service;
 static class Model implements BankInstructionPublicationRepository {
  Map<UUID,Proposal> proposals=new HashMap<>();List<Event> events=new ArrayList<>();Map<UUID,UUID> published=new HashMap<>();List<String> audits=new ArrayList<>();boolean failAudit;int touches;
  public Instant now(){touches++;return Instant.parse("2030-01-01T00:00:00Z");}
  public Optional<Snapshot> find(UUID v,UUID p,boolean lock){touches++;return Optional.ofNullable(proposals.get(p)).filter(x->x.venue().equals(v)).flatMap(x->events.stream().filter(e->e.proposal().equals(p)).max(Comparator.comparingLong(Event::revision)).map(e->new Snapshot(x,e)));}
  public Optional<Event> receipt(UUID a,UUID k){return events.stream().filter(e->e.actor().equals(a)&&e.key().equals(k)).findFirst();}
  public void insertProposal(Proposal p){proposals.put(p.id(),p);}
  public void insertEvent(Event e){if(events.stream().anyMatch(x->x.proposal().equals(e.proposal())&&x.revision()==e.revision()))throw new IllegalStateException("Revision conflict");events.add(e);}
  public void audit(Event e){if(failAudit)throw new IllegalStateException("Model audit unavailable");audits.add(e.action());}
  public void readAudit(UUID a,Snapshot s,UUID c){if(failAudit)throw new IllegalStateException();audits.add("READ");}
  public void denied(UUID a,UUID v,String action,String reason,UUID c){audits.add("DENY");}
  public void publish(Proposal p,Event e){published.put(p.id(),e.published());}
 }
 static class Manager implements PlatformTransactionManager {
  final Model m;final ReentrantLock lock=new ReentrantLock();Manager(Model m){this.m=m;}
  static class Status extends SimpleTransactionStatus {Map<UUID,Proposal> p;List<Event> e;Map<UUID,UUID> published;List<String> audits;}
  public TransactionStatus getTransaction(TransactionDefinition d){lock.lock();var s=new Status();s.p=new HashMap<>(m.proposals);s.e=new ArrayList<>(m.events);s.published=new HashMap<>(m.published);s.audits=new ArrayList<>(m.audits);return s;}
  public void commit(TransactionStatus s){lock.unlock();}
  public void rollback(TransactionStatus value){var s=(Status)value;m.proposals=s.p;m.events=s.e;m.published=s.published;m.audits=s.audits;lock.unlock();}
 }
 @BeforeEach void setup(){when(auth.eligible(any(),anyBoolean())).thenReturn(true);when(auth.member(any(),eq(venue),anyBoolean())).thenReturn(true);when(auth.permits(any(),eq(venue),anyString(),anyBoolean())).thenReturn(false);for(boolean l:List.of(false,true)){when(auth.permits(author,venue,BankInstructionPublicationPolicy.PROPOSE,l)).thenReturn(true);when(auth.permits(publisher,venue,BankInstructionPublicationPolicy.PUBLISH,l)).thenReturn(true);when(auth.permits(publisher,venue,BankInstructionPublicationPolicy.READ,l)).thenReturn(true);}service=new BankInstructionPublicationService(new BankInstructionPublicationPolicy(BankInstructionPublicationPolicyTest.env("local-synthetic","synthetic-quotation")),model,auth,new Manager(model));}
 BankInstructionPublicationService.Result command(UUID a,UUID id,String action,Map<String,Object> b){return service.command(a,venue,id,action,b,UUID.randomUUID());}
 UUID propose(){var r=command(author,null,"PROPOSE",BankInstructionPublicationPolicyTest.draft());assertThat(r.status()).isEqualTo(201);return (UUID)r.body().get("proposalId");}
 Map<String,Object> transition(UUID id,long revision){return Map.of("commandKey",UUID.randomUUID().toString(),"expectedRevision",revision,"snapshotHash",model.proposals.get(id).draft().hash());}
 @Test void independentReviewThenPublishUsesExactImmutableSnapshot(){UUID id=propose();var d=model.proposals.get(id).draft();assertThat(command(publisher,id,"REVIEW",transition(id,1)).body()).containsEntry("state","REVIEWED");var r=command(publisher,id,"PUBLISH",transition(id,2));assertThat(r.status()).isEqualTo(200);assertThat(r.body()).containsEntry("state","PUBLISHED").containsEntry("realPaymentEnabled",false).containsEntry("realReceiptAdmission","DENY").containsEntry("mode","STAGING_ONLY");assertThat(model.proposals.get(id).draft()).isEqualTo(d);assertThat(model.events).hasSize(3);assertThat(model.audits).containsExactly("PROPOSE","REVIEW","PUBLISH");}
 @Test void selfReviewAndPublishDeniedEvenWithPublisherGrant(){UUID id=propose();when(auth.permits(eq(author),eq(venue),eq(BankInstructionPublicationPolicy.PUBLISH),anyBoolean())).thenReturn(true);assertThat(command(author,id,"REVIEW",transition(id,1)).status()).isEqualTo(403);assertThat(command(author,id,"PUBLISH",transition(id,1)).status()).isEqualTo(403);assertThat(model.events).hasSize(1);}
 @Test void wrongVenueReadOnlyAndIneligibleDenied(){UUID id=propose();assertThat(command(other,id,"REVIEW",transition(id,1)).status()).isEqualTo(403);assertThat(service.read(publisher,UUID.randomUUID(),id,UUID.randomUUID()).status()).isEqualTo(403);when(auth.eligible(eq(publisher),anyBoolean())).thenReturn(false);assertThat(command(publisher,id,"REVIEW",transition(id,1)).status()).isEqualTo(403);}
 @Test void staleRevisionChangedSnapshotAndPublishBeforeReviewRejected(){UUID id=propose();assertThat(command(publisher,id,"REVIEW",transition(id,2)).status()).isEqualTo(409);var b=new HashMap<>(transition(id,1));b.put("snapshotHash","b".repeat(64));assertThat(command(publisher,id,"REVIEW",b).status()).isEqualTo(409);assertThat(command(publisher,id,"PUBLISH",transition(id,1)).status()).isEqualTo(409);assertThat(model.events).hasSize(1);}
 @Test void idempotentReplayRetainsOriginalResultAndChangedPayloadConflicts(){var b=BankInstructionPublicationPolicyTest.draft();var first=command(author,null,"PROPOSE",b);var replay=command(author,null,"PROPOSE",b);assertThat(replay.status()).isEqualTo(200);assertThat(replay.body().get("proposalId")).isEqualTo(first.body().get("proposalId"));b.put("effectiveUntil","2030-01-05T00:00:00Z");assertThat(command(author,null,"PROPOSE",b).status()).isEqualTo(409);assertThat(model.proposals).hasSize(1);assertThat(model.events).hasSize(1);}
 @Test void reviewReplayAfterPublicationDoesNotRebindOrRegressState(){UUID id=propose();var b=transition(id,1);command(publisher,id,"REVIEW",b);command(publisher,id,"PUBLISH",transition(id,2));UUID published=model.published.get(id);var replay=command(publisher,id,"REVIEW",b);assertThat(replay.body()).containsEntry("revision",2L).containsEntry("state","REVIEWED").containsEntry("replayed",true);assertThat(model.events).hasSize(3);assertThat(model.published.get(id)).isEqualTo(published);}
 @Test void publisherPermissionCannotReplaceTheExactReviewer(){UUID id=propose();command(publisher,id,"REVIEW",transition(id,1));when(auth.permits(eq(other),eq(venue),eq(BankInstructionPublicationPolicy.PUBLISH),anyBoolean())).thenReturn(true);assertThat(command(other,id,"PUBLISH",transition(id,2)).status()).isEqualTo(409);assertThat(model.events).hasSize(2);assertThat(model.published).isEmpty();}
 @Test void auditFailureRollsBackModelPublicationAndKeepsReviewedHistory(){UUID id=propose();command(publisher,id,"REVIEW",transition(id,1));model.failAudit=true;assertThat(command(publisher,id,"PUBLISH",transition(id,2)).status()).isEqualTo(503);assertThat(model.events).hasSize(2);assertThat(model.published).isEmpty();assertThat(model.proposals).hasSize(1);}
 @Test void grantExpiryAfterLockDeniedAndDisabledNeverTouchesRepository(){UUID id=propose();when(auth.permits(publisher,venue,BankInstructionPublicationPolicy.PUBLISH,false)).thenReturn(true,false);assertThat(command(publisher,id,"REVIEW",transition(id,1)).status()).isEqualTo(403);var repo=mock(BankInstructionPublicationRepository.class);var disabled=new BankInstructionPublicationService(new BankInstructionPublicationPolicy(new org.springframework.mock.env.MockEnvironment()),repo,auth,new Manager(model));assertThat(disabled.command(author,venue,null,"PROPOSE",Map.of(),UUID.randomUUID()).status()).isEqualTo(404);verifyNoInteractions(repo);var bad=new BankInstructionPublicationService(new BankInstructionPublicationPolicy(BankInstructionPublicationPolicyTest.env("production")),repo,auth,new Manager(model));assertThat(bad.command(author,venue,null,"PROPOSE",Map.of(),UUID.randomUUID()).status()).isEqualTo(503);verifyNoInteractions(repo);}
 @Test void concurrentModelPublicationHasOneWinnerAndOneConflict()throws Exception{UUID id=propose();command(publisher,id,"REVIEW",transition(id,1));var pool=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);var a=pool.submit(()->{start.await();return command(publisher,id,"PUBLISH",transition(id,2)).status();});var b=pool.submit(()->{start.await();return command(publisher,id,"PUBLISH",transition(id,2)).status();});start.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);assertThat(model.events).hasSize(3);assertThat(model.published).hasSize(1);}finally{pool.shutdownNow();}}
 @Test void readRequiresIndependentReadGrantAndAudit(){UUID id=propose();assertThat(service.read(publisher,venue,id,UUID.randomUUID()).status()).isEqualTo(200);assertThat(service.read(author,venue,id,UUID.randomUUID()).status()).isEqualTo(403);model.failAudit=true;assertThat(service.read(publisher,venue,id,UUID.randomUUID()).status()).isEqualTo(503);}
 @Test void realControllerBoundsBodiesAndRejectsDuplicateJsonAndQueries()throws Exception{
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new BankInstructionPublicationController(service,new BankInstructionPublicationPolicy(BankInstructionPublicationPolicyTest.env("synthetic-quotation")))).build();
  String path="/api/admin/venues/"+venue+"/bank/instruction-proposals";
  var valid=com.nimbusds.jose.util.JSONObjectUtils.toJSONString(BankInstructionPublicationPolicyTest.draft());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).principal(()->author.toString()).contentType("application/json").content(valid)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"));
  for(String body:List.of("{\"x\":1,\"x\":2}","x".repeat(16385)))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).principal(()->author.toString()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"?unapproved=1").principal(()->author.toString()).contentType("application/json").content(valid)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());assertThat(model.proposals).hasSize(1);
 }


 @org.junit.jupiter.api.Test void jdbcRepositorySupportsClassBasedExceptionTranslationProxy() {
  var jdbc=org.springframework.jdbc.core.simple.JdbcClient.create(new org.springframework.jdbc.datasource.DriverManagerDataSource());
  var factory=new org.springframework.aop.framework.ProxyFactory(new JdbcBankInstructionPublicationRepository(jdbc));
  factory.setProxyTargetClass(true);
  org.assertj.core.api.Assertions.assertThatCode(()->factory.getProxy()).doesNotThrowAnyException();
 }
}

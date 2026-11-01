package com.cinenest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import org.postgresql.util.PSQLException;
import static org.assertj.core.api.Assertions.*;
/** Opt-in disposable-only checks. Committed fixture history is deliberately retained. */
@EnabledIfEnvironmentVariable(named="CINENEST_BANK_INSTRUCTION_PUBLICATION_PG_TESTS",matches="AUTHORIZED_DISPOSABLE")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankInstructionPublicationPostgresTest {
 private static final String TABLES="cinenest_payments.bank_instruction_proposal,cinenest_payments.bank_instruction_publication_command,cinenest_payments.bank_instruction_publication_audit";
 private Connection connection()throws Exception{String url=System.getenv("CINENEST_BANK_INSTRUCTION_PUBLICATION_PG_URL");if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/bank_instruction_pub_[a-f0-9]{32}"))throw new IllegalArgumentException("Exact owned disposable database required");return DriverManager.getConnection(url,System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"));}
 @Test @Order(2) void approvedSchemaAndDeferredReceiptAuditBindingsExist()throws Exception{try(var c=connection();var q=c.createStatement()){c.setReadOnly(true);try(var r=q.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='cinenest_payments' AND table_name IN ('bank_instruction_proposal','bank_instruction_publication_command','bank_instruction_publication_audit')")){r.next();assertThat(r.getInt(1)).isEqualTo(3);}try(var r=q.executeQuery("SELECT count(*) FROM pg_constraint WHERE connamespace='cinenest_payments'::regnamespace AND conname IN ('publication_command_audit_fk','publication_audit_command_fk') AND condeferrable AND condeferred")){r.next();assertThat(r.getInt(1)).isEqualTo(2);}}}
 @Test @Order(3) void allNewJournalsRejectStatementMutationAndTruncate()throws Exception{
  String acceptedBefore=acceptedVersions();UUID proposal=fixture();String baseline=journals();
  // A separate connection sees the committed complete baseline, not pending trigger work.
  assertCommittedFixture(proposal);assertThat(acceptedVersions()).isEqualTo(acceptedBefore);
  try(var c=connection();var q=c.createStatement()){
   c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
   for(var entry:new TreeMap<>(Map.of("bank_instruction_proposal","proposal_id","bank_instruction_publication_command","command_id","bank_instruction_publication_audit","audit_id")).entrySet())
    for(String operation:List.of("UPDATE","DELETE","TRUNCATE")){
     var point=c.setSavepoint();
     try{
      if(operation.equals("TRUNCATE"))q.execute("LOCK TABLE "+TABLES+" IN ACCESS EXCLUSIVE MODE");
      String sql=switch(operation){case "UPDATE"->"UPDATE cinenest_payments."+entry.getKey()+" SET "+entry.getValue()+"="+entry.getValue()+" WHERE proposal_id='"+proposal+"'";case "DELETE"->"DELETE FROM cinenest_payments."+entry.getKey()+" WHERE proposal_id='"+proposal+"'";default->"TRUNCATE cinenest_payments."+entry.getKey()+" CASCADE";};
      SQLException error=catchThrowableOfType(()->q.execute(sql),SQLException.class);
      assertThat((Throwable)error).as("%s COMMITTED_BASELINE",operation).isNotNull();
      diagnostic(error,operation);assertThat(error.getSQLState()).isEqualTo("23514");
     }finally{c.rollback(point);c.releaseSavepoint(point);}
    }
   c.rollback();
  }
  assertCommittedFixture(proposal);assertThat(journals()).isEqualTo(baseline);assertThat(acceptedVersions()).isEqualTo(acceptedBefore);
 }
 private UUID fixture()throws Exception{
  UUID proposal=UUID.randomUUID(),actor=UUID.randomUUID(),venue=UUID.randomUUID(),command=UUID.randomUUID(),audit=UUID.randomUUID(),correlation=UUID.randomUUID();
  var ref=UUID.nameUUIDFromBytes("CN-R1-BI-NONPAYABLE".getBytes(java.nio.charset.StandardCharsets.UTF_8));var version=UUID.nameUUIDFromBytes("CN-R1-BI-V1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(("NON-PAYABLE STAGING FIXTURE|NO REAL RECEIVING ACCOUNT|DO NOT TRANSFER MONEY|"+version).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  try(var c=connection();var q=c.createStatement()){
   c.setAutoCommit(false);
   try{
    q.execute("INSERT INTO cinenest_payments.bank_instruction_proposal(proposal_id,venue_id,proposer_id,instruction_version,reference_id,reference_version,content_sha256,effective_from,effective_until,snapshot_sha256) VALUES('"+proposal+"','"+venue+"','"+actor+"','SYNTHETIC-PG-IMMUTABLE','"+ref+"','"+version+"','"+digest+"',clock_timestamp()+interval '1 day',clock_timestamp()+interval '2 days','"+"a".repeat(64)+"')");
    // One server timestamp is shared by the command and attributable audit composite FK.
    q.execute("WITH event_time AS (SELECT clock_timestamp() AS at), receipt AS (INSERT INTO cinenest_payments.bank_instruction_publication_command(command_id,actor_id,command_key,venue_id,proposal_id,action,payload_sha256,expected_revision,revision,state,occurred_at,correlation_id,audit_id) SELECT '"+command+"','"+actor+"','"+UUID.randomUUID()+"','"+venue+"','"+proposal+"','PROPOSE','"+"b".repeat(64)+"',0,1,'PROPOSED',at,'"+correlation+"','"+audit+"' FROM event_time RETURNING *) INSERT INTO cinenest_payments.bank_instruction_publication_audit(audit_id,command_id,actor_id,venue_id,proposal_id,action,outcome,reason_code,occurred_at,correlation_id) SELECT audit_id,command_id,actor_id,venue_id,proposal_id,action,'ALLOW','STAGING_COMMAND',occurred_at,correlation_id FROM receipt");
    q.execute("SET CONSTRAINTS ALL IMMEDIATE");c.commit();
   }catch(SQLException e){diagnostic(e,"FIXTURE");c.rollback();throw e;}
  }
  return proposal;
 }
 private void assertCommittedFixture(UUID proposal)throws Exception{try(var c=connection();var q=c.prepareStatement("SELECT count(*) FROM cinenest_payments.bank_instruction_proposal p JOIN cinenest_payments.bank_instruction_publication_command c ON c.proposal_id=p.proposal_id AND c.venue_id=p.venue_id AND c.actor_id=p.proposer_id JOIN cinenest_payments.bank_instruction_publication_audit a ON a.audit_id=c.audit_id AND a.command_id=c.command_id AND a.actor_id=c.actor_id AND a.venue_id=c.venue_id AND a.proposal_id=c.proposal_id AND a.occurred_at=c.occurred_at AND a.correlation_id=c.correlation_id WHERE p.proposal_id=? AND c.action='PROPOSE' AND c.revision=1 AND a.outcome='ALLOW' AND a.reason_code='STAGING_COMMAND'")){c.setReadOnly(true);q.setObject(1,proposal);try(var r=q.executeQuery()){r.next();assertThat(r.getLong(1)).isEqualTo(1);}}}
 private String journals()throws Exception{return fingerprints(List.of("bank_instruction_proposal","bank_instruction_publication_command","bank_instruction_publication_audit"));}
 private String acceptedVersions()throws Exception{return fingerprints(List.of("bank_instruction_revision","bank_instruction_request_binding","bank_payment_request"));}
 private String fingerprints(List<String> tables)throws Exception{var out=new StringBuilder();try(var c=connection();var q=c.createStatement()){c.setReadOnly(true);for(String table:tables)try(var r=q.executeQuery("SELECT count(*),md5(coalesce(string_agg(row_to_json(t)::text,'' ORDER BY row_to_json(t)::text),'')) FROM cinenest_payments."+table+" t")){r.next();out.append(table).append(':').append(r.getLong(1)).append(':').append(r.getString(2)).append(';');}}return out.toString();}
 private void diagnostic(SQLException error,String operation){String category="OTHER_DATABASE_CONDITION";if("23514".equals(error.getSQLState()))category="INTEGRITY_GUARD";else if("55006".equals(error.getSQLState())){category="OBJECT_IN_USE";if(error instanceof PSQLException p&&p.getServerErrorMessage()!=null&&p.getServerErrorMessage().getMessage()!=null&&p.getServerErrorMessage().getMessage().contains("pending trigger events"))category="PENDING_TRIGGER_EVENTS";}System.out.printf("V34_DIAGNOSTIC operation=%s phase=%s sqlstate=%s category=%s%n",operation,operation.equals("FIXTURE")?"DEFERRED_COMPLETION_OR_COMMIT":"COMMITTED_BASELINE",error.getSQLState(),category);}
 @Test @Order(1) void emptyResetNeedsReadCommittedAllLocksAndNoStoredHistory()throws Exception{try(var c=connection();var q=c.createStatement()){c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
  q.execute("LOCK TABLE "+TABLES+" IN ACCESS EXCLUSIVE MODE");for(String table:List.of("bank_instruction_proposal","bank_instruction_publication_command","bank_instruction_publication_audit"))try(var r=q.executeQuery("SELECT count(*) FROM cinenest_payments."+table)){r.next();assertThat(r.getLong(1)).isZero();}q.execute("TRUNCATE "+TABLES);c.rollback();}}
}

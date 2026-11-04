package com.cinenest.identity.internal;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalCredentialRecoveryServiceTest {
    final UUID target=UUID.randomUUID(),actor=UUID.randomUUID(),venue=UUID.randomUUID(),grant=UUID.randomUUID(),capability=UUID.randomUUID();
    final String token="a".repeat(64),password="Synthetic recovery test 2026!";
    JdbcLocalCredentialRecoveryRepository repo;IdentityStore store;PlatformTransactionManager manager;LocalCredentialRecoveryService service;
    MockEnvironment environment(String...profiles){var e=new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true").withProperty("cinenest.identity.manual.recovery.enabled","true");e.setActiveProfiles(profiles);return e;}
    @BeforeEach void setup(){repo=mock(JdbcLocalCredentialRecoveryRepository.class);store=mock(IdentityStore.class);manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(repo.attempt(any(),anyInt(),anyInt())).thenReturn(true);service=new LocalCredentialRecoveryService(repo,store,manager,environment("local-synthetic"));}
    LocalCredentialRecovery.Capability cap(boolean consumed,Instant expiry){return new LocalCredentialRecovery.Capability(capability,target,actor,venue,1,0,LocalCustomerIdentity.digest(token),Instant.now().minusSeconds(5),expiry,consumed);}
    void snapshot(LocalCredentialRecovery.Capability cap,boolean synthetic){var stored=new JdbcLocalCredentialRecoveryRepository.Stored(cap,actor,new JdbcLocalCredentialRecoveryRepository.Authority(grant,0));when(repo.capability(capability)).thenReturn(stored);when(repo.locked(eq(target),eq(actor),eq(venue),any(),any())).thenReturn(new LocalCredentialRecovery.Snapshot(new LocalCredentialRecovery.Account(target,synthetic,true,true,1,0,Set.of(venue)),new LocalCredentialRecovery.Actor(actor,true,true,Set.of(venue)),cap));}
    LocalCredentialRecovery.Command command(){return new LocalCredentialRecovery.Command(UUID.randomUUID(),target,actor,venue,1,0,token,password);}
    @Test void defaultDisabledNeverTouchesDatabase(){var s=new LocalCredentialRecoveryService(repo,store,manager,new MockEnvironment());assertThatThrownBy(()->s.consume(actor,target,venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);verifyNoInteractions(repo,store);}
    @Test void exactLocalAndCombinedProfilesInEitherOrderAreAccepted(){for(var p:List.of(new String[]{"local-synthetic"},new String[]{"local-synthetic","synthetic-quotation"},new String[]{"synthetic-quotation","local-synthetic"}))assertThatCode(()->new LocalCredentialRecoveryService(repo,store,manager,environment(p))).doesNotThrowAnyException();}
    @Test void productionDefaultLocalMissingAndExtraProfilesFailClosed(){for(var p:List.of(new String[]{},new String[]{"production"},new String[]{"synthetic-quotation"},new String[]{"local-synthetic","production"}))assertThatThrownBy(()->new LocalCredentialRecoveryService(repo,store,manager,environment(p))).isInstanceOf(IllegalStateException.class);}
    @Test void authorizedSyntheticConsumptionProducesMatchingReceipt(){snapshot(cap(false,Instant.now().plusSeconds(120)),true);when(repo.commit(any())).thenAnswer(i->{var a=i.getArgument(0,LocalCredentialRecovery.Append.class);return new LocalCredentialRecovery.Receipt(a.command(),a.capability(),a.account(),a.nextCredentialRevision(),a.audit().id(),a.sessionInvalidation(),true);});var result=service.consume(actor,target,venue,capability,command());assertThat(result.credentialRevision()).isEqualTo(1);assertThat(result.sessionInvalidation().allSessions()).isTrue();verify(repo).commit(any());}
    @Test void wrongActorVenueAndAccountAreDeniedBeforeAppend(){snapshot(cap(false,Instant.now().plusSeconds(120)),true);for(UUID wrong:List.of(UUID.randomUUID(),UUID.randomUUID()))assertThatThrownBy(()->service.consume(wrong,target,venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);assertThatThrownBy(()->service.consume(actor,target,UUID.randomUUID(),capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);assertThatThrownBy(()->service.consume(actor,UUID.randomUUID(),venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);verify(repo,never()).commit(any());}
    @Test void nonSyntheticTargetDenied(){snapshot(cap(false,Instant.now().plusSeconds(120)),false);assertThatThrownBy(()->service.consume(actor,target,venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);verify(repo,never()).commit(any());}
    @Test void expiredAndConsumedCapabilitiesDenied(){for(var cap:List.of(cap(false,Instant.now().minusSeconds(1)),cap(true,Instant.now().plusSeconds(120)))){snapshot(cap,true);assertThatThrownBy(()->service.consume(actor,target,venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);}verify(repo,never()).commit(any());}
    @Test void tokenMismatchDenied(){snapshot(cap(false,Instant.now().plusSeconds(120)),true);var c=command();var bad=new LocalCredentialRecovery.Command(c.id(),target,actor,venue,1,0,"b".repeat(64),password);assertThatThrownBy(()->service.consume(actor,target,venue,capability,bad)).isInstanceOf(LocalCredentialRecovery.Denied.class);verify(repo,never()).commit(any());}
    @Test void missingAuditReceiptFailsAndRollsBack(){snapshot(cap(false,Instant.now().plusSeconds(120)),true);when(repo.commit(any())).thenAnswer(i->{var a=i.getArgument(0,LocalCredentialRecovery.Append.class);return new LocalCredentialRecovery.Receipt(a.command(),a.capability(),a.account(),1,UUID.randomUUID(),a.sessionInvalidation(),true);});assertThatThrownBy(()->service.consume(actor,target,venue,capability,command())).isInstanceOf(LocalCredentialRecovery.Denied.class);verify(manager).rollback(any());verify(store).audit(actor,target,"AUTHENTICATION",false);}
    @Test void appendFailureCannotReturnSuccess(){snapshot(cap(false,Instant.now().plusSeconds(120)),true);when(repo.commit(any())).thenThrow(new IllegalStateException("private SQL failure"));assertThatThrownBy(()->service.consume(actor,target,venue,capability,command())).hasMessage("Local synthetic credential recovery denied");verify(manager).rollback(any());}
    @Test void unauthorizedIssuerCannotIssueCapability(){when(repo.locked(any(),any(),any(),any(),isNull())).thenThrow(JdbcLocalCredentialRecoveryRepository.denied());assertThatThrownBy(()->service.issue(actor,target,venue,new LocalCredentialRecoveryService.Issuance(UUID.randomUUID(),grant,0,1,0,60,"Approval text is not authority"))).isInstanceOf(LocalCredentialRecovery.Denied.class);verify(repo,never()).issue(any(),any(),any(),any(),any(),any(),anyLong(),anyLong(),anyString(),anyInt(),anyString());}
    @Test void issuancePersistsDigestNotRawToken(){when(repo.locked(any(),any(),any(),any(),isNull())).thenReturn(new LocalCredentialRecovery.Snapshot(new LocalCredentialRecovery.Account(target,true,true,true,1,0,Set.of(venue)),new LocalCredentialRecovery.Actor(actor,true,true,Set.of(venue)),null));when(repo.issue(any(),any(),any(),any(),any(),any(),anyLong(),anyLong(),anyString(),anyInt(),anyString())).thenAnswer(i->new LocalCredentialRecovery.Capability(i.getArgument(0),target,actor,venue,1,0,i.getArgument(8),Instant.now(),Instant.now().plusSeconds(60),false));var r=service.issue(actor,target,venue,new LocalCredentialRecoveryService.Issuance(UUID.randomUUID(),grant,0,1,0,60,"Synthetic approval"));assertThat(r.token()).matches("[0-9a-f]{64}");verify(repo).issue(any(),any(),any(),any(),any(),any(),eq(1L),eq(0L),eq(LocalCustomerIdentity.digest(r.token())),eq(60),anyString());assertThat(r.toString()).doesNotContain(r.token());}
    LocalCustomerIdentity loginAdapter(java.nio.file.Path outbox,String newest,boolean malformed)throws Exception {
        var jdbc=mock(org.springframework.jdbc.core.simple.JdbcClient.class);var statement=mock(org.springframework.jdbc.core.simple.JdbcClient.StatementSpec.class,RETURNS_SELF);
        var mapped=mock(org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);when(statement.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of(new LocalCustomerIdentity.Credential(target,"{pbkdf2-v1}"+LocalCustomerIdentity.credentialEncoder().encode("Historical test password 2026!"),true,"ELIGIBLE",true)));
        if(malformed)when(store.credentialState(target,true)).thenThrow(new CredentialEpochSessions.Denied());
        else when(store.credentialState(target,true)).thenReturn(new IdentityStore.CredentialState(1,2,newest,true,true,"ELIGIBLE"));
        return new LocalCustomerIdentity(jdbc,CredentialEpochSessions.transaction(manager),store,outbox.toString(),5,900,900){
            @Override boolean throttle(String action,String subject){return true;}
            @Override void audit(String action,UUID account,boolean allowed){}
        };
    }
    @Test void newestCredentialAuthenticatesAndCarriesItsVerifiedEpoch(@org.junit.jupiter.api.io.TempDir java.nio.file.Path outbox)throws Exception {
        var identity=loginAdapter(outbox,"{pbkdf2-v1}"+LocalCustomerIdentity.credentialEncoder().encode(password),false);
        var result=identity.login("fixture@example.test",password);assertThat(result.status()).isEqualTo(204);assertThat(result.epoch()).isEqualTo(2);assertThat(result.account()).isEqualTo(target);
    }
    @Test void historicalPasswordNeverAuthenticatesAfterRotation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path outbox)throws Exception {
        var identity=loginAdapter(outbox,"{pbkdf2-v1}"+LocalCustomerIdentity.credentialEncoder().encode(password),false);
        assertThat(identity.login("fixture@example.test","Historical test password 2026!").status()).isEqualTo(401);
    }
    @Test void malformedLatestStateCannotFallbackToHistoricalPassword(@org.junit.jupiter.api.io.TempDir java.nio.file.Path outbox)throws Exception {
        var identity=loginAdapter(outbox,null,true);assertThat(identity.login("fixture@example.test","Historical test password 2026!").status()).isEqualTo(401);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) IdentityStore realStore(List<?> manual,List<?> versions,boolean synthetic) {
        var jdbc=mock(org.springframework.jdbc.core.simple.JdbcClient.class);
        when(jdbc.sql(anyString())).thenAnswer(i->{
            String sql=i.getArgument(0);List<?> rows=sql.contains("FROM cinenest_identity.account WHERE")?Collections.singletonList(new Object[]{1L,synthetic,"ELIGIBLE"}):sql.contains("FROM cinenest_identity.manual_customer")?manual:versions;
            var statement=mock(org.springframework.jdbc.core.simple.JdbcClient.StatementSpec.class,RETURNS_SELF);var mapped=mock(org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec.class);
            when(statement.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(mapped);when(mapped.list()).thenReturn((List)rows);return statement;
        });return new IdentityStore(jdbc);
    }
    @Test void jdbcReaderSelectsNewestValidVersionAndNotV28Hash(){
        String old="{pbkdf2-v1}"+"a".repeat(96),fresh="{pbkdf2-v1}"+"b".repeat(96);
        var reader=realStore(Collections.singletonList(new Object[]{old,true}),Collections.singletonList(new Object[]{fresh,2L,1L,2L,true}),true);
        var state=reader.credentialState(target,false);assertThat(state.hash()).isEqualTo(fresh);assertThat(state.epoch()).isEqualTo(2);
    }
    @Test void jdbcReaderFallsBackToV28OnlyWhenNoVersionExists(){
        String old="{pbkdf2-v1}"+"a".repeat(96);var state=realStore(Collections.singletonList(new Object[]{old,true}),List.of(),true).credentialState(target,false);
        assertThat(state.hash()).isEqualTo(old);assertThat(state.epoch()).isZero();
    }
    @Test void missingAuditAndMalformedNewestHashCannotFallback(){
        var manual=Collections.singletonList(new Object[]{"{pbkdf2-v1}"+"a".repeat(96),true});
        for(Object[] row:List.of(new Object[]{"{pbkdf2-v1}"+"b".repeat(96),2L,1L,null,true},new Object[]{"malformed",2L,1L,2L,true},new Object[]{"{pbkdf2-v1}"+"b".repeat(96),2L,1L,2L,false})){
            var reader=realStore(manual,Collections.singletonList(row),true);assertThatThrownBy(()->reader.credentialState(target,false)).isInstanceOf(CredentialEpochSessions.Denied.class);
        }
    }
    @Test void associatedOidcOnlyAccountUsesEpochZeroButNonSyntheticAccountIsDenied(){
        assertThat(realStore(List.of(),List.of(),true).credentialEpoch(target,false)).isZero();assertThatThrownBy(()->realStore(List.of(),List.of(),false).credentialEpoch(target,false)).isInstanceOf(CredentialEpochSessions.Denied.class);
    }
}

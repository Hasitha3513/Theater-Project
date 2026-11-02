package com.cinenest.identity.internal;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LocalCredentialRecoveryTest {
    final UUID account=UUID.randomUUID(),actor=UUID.randomUUID(),venue=UUID.randomUUID(),capabilityId=UUID.randomUUID();
    final Instant now=Instant.parse("2026-11-03T00:00:00Z");
    final String token="a".repeat(64),password="Synthetic recovery secret 2026!";
    LocalCredentialRecovery.Policy policy(Set<String> profiles) {
        return new LocalCredentialRecovery.Policy(true,profiles,Duration.ofMinutes(5));
    }
    LocalCredentialRecovery enabled() {
        return new LocalCredentialRecovery(policy(Set.of("local-synthetic","synthetic-quotation")),Clock.fixed(now,ZoneOffset.UTC));
    }
    LocalCredentialRecovery.Account account(boolean synthetic,boolean eligible,boolean verified,long revision,long credentialRevision,Set<UUID> venues) {
        return new LocalCredentialRecovery.Account(account,synthetic,eligible,verified,revision,credentialRevision,venues);
    }
    LocalCredentialRecovery.Capability capability(UUID target,Instant issued,Instant expires,boolean consumed) {
        return new LocalCredentialRecovery.Capability(capabilityId,target,actor,venue,2,0,LocalCustomerIdentity.digest(token),issued,expires,consumed);
    }
    LocalCredentialRecovery.Snapshot snapshot() {
        return new LocalCredentialRecovery.Snapshot(account(true,true,true,2,0,Set.of(venue)),
            new LocalCredentialRecovery.Actor(actor,true,true,Set.of(venue)),capability(account,now.minusSeconds(10),now.plusSeconds(60),false));
    }
    LocalCredentialRecovery.Command command() {
        return new LocalCredentialRecovery.Command(UUID.randomUUID(),account,actor,venue,2,0,token,password);
    }
    void denied(LocalCredentialRecovery recovery,LocalCredentialRecovery.Command command,LocalCredentialRecovery.Snapshot snapshot,LocalCredentialRecovery.Reason reason) {
        assertThatThrownBy(()->recovery.prepare(command,snapshot)).isInstanceOfSatisfying(LocalCredentialRecovery.Denied.class,
            e->{assertThat(e.reason).isEqualTo(reason);assertThat(e).hasMessage("Local synthetic credential recovery denied");});
    }
    @Test void disabledByDefault() { denied(new LocalCredentialRecovery(),command(),snapshot(),LocalCredentialRecovery.Reason.DISABLED); }
    @ParameterizedTest @ValueSource(strings={"production","default","synthetic-quotation","unknown"})
    void nonLocalProfilesDenied(String profile) {
        denied(new LocalCredentialRecovery(policy(Set.of(profile)),Clock.fixed(now,ZoneOffset.UTC)),command(),snapshot(),LocalCredentialRecovery.Reason.ENVIRONMENT);
    }
    @Test void additionalProfilesDeniedAndOrderingDoesNotMatter() {
        denied(new LocalCredentialRecovery(policy(Set.of("local-synthetic","synthetic-quotation","production")),Clock.fixed(now,ZoneOffset.UTC)),command(),snapshot(),LocalCredentialRecovery.Reason.ENVIRONMENT);
        var first=new LocalCredentialRecovery(policy(new LinkedHashSet<>(List.of("local-synthetic","synthetic-quotation"))),Clock.fixed(now,ZoneOffset.UTC)).prepare(command(),snapshot());
        var second=new LocalCredentialRecovery(policy(new LinkedHashSet<>(List.of("synthetic-quotation","local-synthetic"))),Clock.fixed(now,ZoneOffset.UTC)).prepare(command(),snapshot());
        assertThat(first.nextCredentialRevision()).isEqualTo(second.nextCredentialRevision());
    }
    @Test void nonSyntheticUnknownSuspendedAndUnverifiedAccountsDenied() {
        var s=snapshot();
        denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(null,s.actor(),s.capability()),LocalCredentialRecovery.Reason.ACCOUNT);
        for(var a:List.of(account(false,true,true,2,0,Set.of(venue)),account(true,false,true,2,0,Set.of(venue)),account(true,true,false,2,0,Set.of(venue))))
            denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(a,s.actor(),s.capability()),LocalCredentialRecovery.Reason.ACCOUNT);
    }
    @Test void expiredFutureAndOversizedCapabilityWindowsDenied() {
        var s=snapshot();
        for(var c:List.of(capability(account,now.minusSeconds(10),now,false),capability(account,now.plusSeconds(1),now.plusSeconds(30),false),capability(account,now.minusSeconds(300),now.plusSeconds(1),false)))
            denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(s.account(),s.actor(),c),LocalCredentialRecovery.Reason.EXPIRED);
    }
    @Test void consumedCapabilityReplayDenied() {
        var s=snapshot();denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(s.account(),s.actor(),capability(account,now.minusSeconds(10),now.plusSeconds(60),true)),LocalCredentialRecovery.Reason.REPLAY);
    }
    @Test void wrongAccountAndWrongTokenDenied() {
        var s=snapshot();denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(s.account(),s.actor(),capability(UUID.randomUUID(),now.minusSeconds(10),now.plusSeconds(60),false)),LocalCredentialRecovery.Reason.CAPABILITY);
        var c=command();denied(enabled(),new LocalCredentialRecovery.Command(c.id(),account,actor,venue,2,0,"b".repeat(64),password),s,LocalCredentialRecovery.Reason.CAPABILITY);
    }
    @Test void expectedAccountAndCredentialRevisionsMustMatch() {
        var s=snapshot();
        for(var a:List.of(account(true,true,true,3,0,Set.of(venue)),account(true,true,true,2,1,Set.of(venue))))
            denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(a,s.actor(),s.capability()),LocalCredentialRecovery.Reason.REVISION);
    }
    @Test void exactActorAndBothVenueBindingsRequired() {
        var s=snapshot();var other=UUID.randomUUID();
        for(var a:List.of(new LocalCredentialRecovery.Actor(actor,false,true,Set.of(venue)),new LocalCredentialRecovery.Actor(actor,true,false,Set.of(venue)),new LocalCredentialRecovery.Actor(actor,true,true,Set.of(other)),new LocalCredentialRecovery.Actor(other,true,true,Set.of(venue))))
            denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(s.account(),a,s.capability()),LocalCredentialRecovery.Reason.AUTHORITY);
        denied(enabled(),command(),new LocalCredentialRecovery.Snapshot(account(true,true,true,2,0,Set.of(other)),s.actor(),s.capability()),LocalCredentialRecovery.Reason.AUTHORITY);
        var c=command();denied(enabled(),new LocalCredentialRecovery.Command(c.id(),account,actor,other,2,0,token,password),s,LocalCredentialRecovery.Reason.AUTHORITY);
    }
    @Test void planAppendsNextVersionWithoutMutatingSnapshotOrContainingHistoricCredential() {
        var s=snapshot();var original=s.account();var result=enabled().prepare(command(),s);
        assertThat(result.previousCredentialRevision()).isZero();assertThat(result.nextCredentialRevision()).isEqualTo(1);
        assertThat(s.account()).isEqualTo(original);assertThat(s.account().credentialRevision()).isZero();
        assertThat(result.encodedPassword()).startsWith("{pbkdf2-v1}").doesNotContain(password);
        assertThat(LocalCustomerIdentity.credentialEncoder().matches(password,result.encodedPassword().substring("{pbkdf2-v1}".length()))).isTrue();
        assertThat(result.account()).isEqualTo(account);assertThat(result.actor()).isEqualTo(actor);assertThat(result.venue()).isEqualTo(venue);
        assertThat(s.account().venues()).containsExactly(venue);assertThatThrownBy(()->s.account().venues().add(UUID.randomUUID())).isInstanceOf(UnsupportedOperationException.class);
    }
    LocalCredentialRecovery.Receipt receipt(LocalCredentialRecovery.Append p,UUID audit,LocalCredentialRecovery.SessionInvalidation invalidation,boolean consumed) {
        return new LocalCredentialRecovery.Receipt(p.command(),p.capability(),p.account(),p.nextCredentialRevision(),audit,invalidation,consumed);
    }
    @Test void mandatoryAttributableAuditContractCannotBeOmittedOrSubstituted() {
        var p=enabled().prepare(command(),snapshot());
        assertThat(p.audit().actor()).isEqualTo(actor);assertThat(p.audit().account()).isEqualTo(account);assertThat(p.audit().venue()).isEqualTo(venue);
        assertThat(p.audit().command()).isEqualTo(p.command());assertThat(p.audit().capability()).isEqualTo(capabilityId);assertThat(p.audit().occurredAt()).isEqualTo(now);
        for(UUID id:Arrays.asList(null,UUID.randomUUID()))assertThatThrownBy(()->LocalCredentialRecovery.verifyReceipt(p,receipt(p,id,p.sessionInvalidation(),true))).isInstanceOf(LocalCredentialRecovery.Denied.class);
    }
    @Test void allSessionInvalidationAndCapabilityConsumptionAreMandatoryAcknowledgements() {
        var p=enabled().prepare(command(),snapshot());assertThat(p.sessionInvalidation()).isEqualTo(new LocalCredentialRecovery.SessionInvalidation(account,1,true));
        for(var invalidation:Arrays.asList(null,new LocalCredentialRecovery.SessionInvalidation(account,1,false),new LocalCredentialRecovery.SessionInvalidation(UUID.randomUUID(),1,true),new LocalCredentialRecovery.SessionInvalidation(account,2,true)))
            assertThatThrownBy(()->LocalCredentialRecovery.verifyReceipt(p,receipt(p,p.audit().id(),invalidation,true))).isInstanceOf(LocalCredentialRecovery.Denied.class);
        assertThatThrownBy(()->LocalCredentialRecovery.verifyReceipt(p,receipt(p,p.audit().id(),p.sessionInvalidation(),false))).isInstanceOf(LocalCredentialRecovery.Denied.class);
        assertThatCode(()->LocalCredentialRecovery.verifyReceipt(p,receipt(p,p.audit().id(),p.sessionInvalidation(),true))).doesNotThrowAnyException();
    }
    @Test void receiptMustMatchExactCommandAccountCapabilityAndRevision() {
        var p=enabled().prepare(command(),snapshot());
        for(var r:List.of(new LocalCredentialRecovery.Receipt(UUID.randomUUID(),p.capability(),account,1,p.audit().id(),p.sessionInvalidation(),true),new LocalCredentialRecovery.Receipt(p.command(),UUID.randomUUID(),account,1,p.audit().id(),p.sessionInvalidation(),true),new LocalCredentialRecovery.Receipt(p.command(),p.capability(),UUID.randomUUID(),1,p.audit().id(),p.sessionInvalidation(),true),new LocalCredentialRecovery.Receipt(p.command(),p.capability(),account,2,p.audit().id(),p.sessionInvalidation(),true)))
            assertThatThrownBy(()->LocalCredentialRecovery.verifyReceipt(p,r)).isInstanceOf(LocalCredentialRecovery.Denied.class);
    }
    @Test void malformedInputsAndPasswordPolicyFailClosed() {
        denied(enabled(),null,snapshot(),LocalCredentialRecovery.Reason.CAPABILITY);var c=command();
        denied(enabled(),new LocalCredentialRecovery.Command(c.id(),account,actor,venue,2,0,token,"short"),snapshot(),LocalCredentialRecovery.Reason.PASSWORD);
        denied(new LocalCredentialRecovery(new LocalCredentialRecovery.Policy(true,Set.of("local-synthetic"),Duration.ofMinutes(16)),Clock.fixed(now,ZoneOffset.UTC)),c,snapshot(),LocalCredentialRecovery.Reason.CAPABILITY);
    }
    @Test void sensitiveContractDiagnosticsAreRedactedAndNoRuntimeBeanOrEndpointExists() {
        var p=enabled().prepare(command(),snapshot());
        assertThat(command().toString()).isEqualTo("RecoveryCommand[REDACTED]");assertThat(snapshot().capability().toString()).isEqualTo("Capability[REDACTED]");assertThat(p.toString()).isEqualTo("CredentialAppend[REDACTED]");
        assertThat(LocalCredentialRecovery.class.getAnnotations()).isEmpty();
        assertThat(Arrays.stream(LocalCredentialRecovery.class.getDeclaredMethods()).noneMatch(m->m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class))).isTrue();
    }
}

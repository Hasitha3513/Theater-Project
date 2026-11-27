package com.cinenest.identity.internal;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cinenest.identity.internal.CustomerCredentialLifecycle.*;

class CustomerCredentialServiceTest {
    static final Instant NOW=Instant.parse("2026-11-27T00:00:00Z");
    static final String PASSWORD="Qualification customer password 2026!";
    static CustomerCredentialConfiguration.Settings settings(){return new CustomerCredentialConfiguration.Settings("TEST_POLICY_V1","TEST_PRIVACY_V1","TEST_DELIVERY_V1","ASCII_CASEFOLD_V1",900,100,60,2,3600,1,true);}
    static CustomerCredentialConfiguration.Keys keys(){byte[] a=new byte[32],b=new byte[32],c=new byte[32];new java.security.SecureRandom().nextBytes(a);new java.security.SecureRandom().nextBytes(b);new java.security.SecureRandom().nextBytes(c);return new CustomerCredentialConfiguration.Keys("TEST_ONLY_KEYS_V1",a,b,c);}
    static CustomerCredentialPorts.CommitReceipt receipt(Plan p){return new CustomerCredentialPorts.CommitReceipt(p.command(),p.account(),p.digest(),p.audit(),p.permit(),p.nextState(),p.capability(),p.nextAccountRevision(),p.nextEpoch(),true,true,true,p.consumeCapability(),p.invalidateSessions());}
    CustomerCredentialRepository repository;CustomerCredentialService service;CustomerCredentialConfiguration.Crypto crypto;
    @BeforeEach void setup(){
        repository=mock(CustomerCredentialRepository.class);crypto=new CustomerCredentialConfiguration.Crypto(keys());
        when(repository.transaction(any())).thenAnswer(x->((java.util.function.Supplier<?>)x.getArgument(0)).get());
        when(repository.deliveryCurrent(any(),any())).thenReturn(true);when(repository.now()).thenReturn(NOW);when(repository.receipt(any())).thenReturn(Optional.empty());when(repository.address(anyString(),anyBoolean())).thenReturn(Optional.empty());
        when(repository.admit(any(),anyString(),anyString(),anyBoolean(),any())).thenReturn(UUID.randomUUID());
        when(repository.permit(any(),any(),any(),any(),anyString())).thenAnswer(x->new Permit(x.getArgument(0),x.getArgument(1),x.getArgument(2),x.getArgument(3),x.getArgument(4),NOW.minusSeconds(1),NOW.plusSeconds(20),true,false,0));
        when(repository.commit(any(),any(),anyString(),any(),any())).thenAnswer(x->receipt(x.getArgument(0)));
        var gateway=mock(CustomerCredentialConfiguration.DeliveryGateway.class);
        service=new CustomerCredentialService(repository,crypto,settings(),new CustomerCredentialDeliveryDispatcher(repository,crypto,gateway,CustomerCredentialServiceTest.settings()));clearInvocations(repository);
    }
    @Test void registrationUsesCoreAndAtomicProtectedOutbox(){
        assertThat(service.register(UUID.randomUUID(),"Customer@Example.org",PASSWORD,"TEST_PRIVACY_V1","loopback").status()).isEqualTo(202);
        verify(repository).commit(argThat(p->p.nextState()==State.PENDING&&p.deliveryRequired()&&p.evidence()==AuditEvidence.ANONYMOUS_REQUEST),any(),anyString(),any(),argThat(i->{String clear=crypto.open(i.outbox(),i.sealedPayload(),i.keyVersion());return clear.startsWith("customer@example.org\n")&&clear.split("\n")[1].matches("[0-9a-f]{64}");}));
        verify(repository).release(any());
    }
    @Test void missingPrivacyDeniesBeforeAbuseOrHash(){assertThatThrownBy(()->service.register(UUID.randomUUID(),"a@example.org",PASSWORD,"wrong","loopback")).isInstanceOf(CustomerCredentialService.Refused.class);verifyNoInteractions(repository);}
    @Test void durableAbuseRefusalPrecedesCredentialTransaction(){when(repository.admit(any(),anyString(),anyString(),anyBoolean(),any())).thenReturn(null);assertThatThrownBy(()->service.login(UUID.randomUUID(),"a@example.org",PASSWORD,"loopback")).isInstanceOf(CustomerCredentialService.Refused.class);verify(repository,never()).transaction(any());}
    @Test void unknownLoginIsDeniedAndNeverCreatesSession(){when(repository.neutral(any(),anyString(),anyString(),anyInt())).thenAnswer(x->new CustomerCredentialRepository.Receipt(x.getArgument(0),x.getArgument(1),null,x.getArgument(2),x.getArgument(3),0));assertThat(service.login(UUID.randomUUID(),"unknown@example.org",PASSWORD,"loopback").status()).isEqualTo(401);verify(repository,never()).commit(any(),any(),anyString(),any(),any());}
    @Test void changedCommandConflictsBeforeAddressLookup(){when(repository.receipt(any())).thenReturn(Optional.of(new CustomerCredentialRepository.Receipt(UUID.randomUUID(),"0".repeat(64),null,"REGISTER",202,0)));assertThatThrownBy(()->service.register(UUID.randomUUID(),"a@example.org",PASSWORD,"TEST_PRIVACY_V1","loopback")).isInstanceOf(CustomerCredentialService.Refused.class);verify(repository,never()).address(anyString(),anyBoolean());}
    @Test void missingAtomicReceiptCannotReportSuccess(){doReturn(null).when(repository).commit(any(),any(),anyString(),any(),any());assertThatThrownBy(()->service.register(UUID.randomUUID(),"a@example.org",PASSWORD,"TEST_PRIVACY_V1","loopback")).isInstanceOf(CustomerCredentialService.Refused.class);verify(repository).release(any());}
    @Test void recoveryUnknownAddressHasGenericAcceptedOutcome(){assertThat(service.requestRecovery(UUID.randomUUID(),"unknown@example.org","loopback").status()).isEqualTo(202);verify(repository,never()).commit(any(),any(),anyString(),any(),any());}
    @Test void addressPolicyDoesNotApplyProviderAliases(){assertThat(CustomerCredentialService.email("A+tag@Example.ORG")).isEqualTo("a+tag@example.org");assertThat(CustomerCredentialService.email("a.tag@example.org")).isNotEqualTo(CustomerCredentialService.email("atag@example.org"));}
    @Test void malformedAddressesDenied(){for(String value:List.of(" a@example.org","a@example.org ","a..b@example.org","a@localhost","a@-example.org","a\n@example.org"))assertThatThrownBy(()->CustomerCredentialService.email(value)).isInstanceOf(CustomerCredentialService.Refused.class);}
    @Test void sealedPayloadCannotOpenUnderAnotherReferenceOrVersion(){UUID id=UUID.randomUUID();byte[] sealed=crypto.seal(id,"private-address\nprivate-token");assertThat(crypto.open(id,sealed,crypto.keys.version())).isEqualTo("private-address\nprivate-token");assertThatThrownBy(()->crypto.open(UUID.randomUUID(),sealed,crypto.keys.version())).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->crypto.open(id,sealed,"wrong-version")).isInstanceOf(IllegalStateException.class);}
    @Test void addressAndCommandFingerprintsAreKeyedAndDomainSeparated(){assertThat(crypto.mac("ADDRESS","a@example.org")).isNotEqualTo(crypto.mac("COMMAND","a@example.org"));assertThat(crypto.mac("ADDRESS","a@example.org")).hasSize(64);}
    @Test void keyAndPayloadRepresentationsAreRedacted(){assertThat(keys().toString()).isEqualTo("CustomerCredentialKeys[REDACTED]");assertThat(new CustomerCredentialConfiguration.DeliveryMessage(UUID.randomUUID(),"a@example.org","a".repeat(64),"VERIFY",NOW).toString()).doesNotContain("example.org","aaaa");}
    @Test void unknownDeliveryIsNeverAutomaticallyResent(){UUID id=UUID.randomUUID();when(repository.claim(eq(id),any())).thenReturn(Optional.of(new CustomerCredentialRepository.Outbox(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VERIFY",crypto.seal(id,"a@example.org\n"+"a".repeat(64)),crypto.keys.version(),"TEST_POLICY_V1",NOW.plusSeconds(20),"SENDING")),Optional.empty());var gateway=mock(CustomerCredentialConfiguration.DeliveryGateway.class);when(gateway.send(any())).thenThrow(new IllegalStateException("secret provider response"));var dispatcher=new CustomerCredentialDeliveryDispatcher(repository,crypto,gateway,CustomerCredentialServiceTest.settings());dispatcher.dispatch(id);dispatcher.dispatch(id);verify(gateway,times(1)).send(any());verify(repository).deliveryResult(id,"UNKNOWN");}    @Test void malformedSurrogatesDenyBeforeAbuseOrHash(){for(String password:List.of("Qualification password \ud800 2026!","Qualification password \udc00 2026!"))assertThatThrownBy(()->service.register(UUID.randomUUID(),"a@example.org",password,"TEST_PRIVACY_V1","loopback")).isInstanceOf(CustomerCredentialService.Refused.class);verifyNoInteractions(repository);}
    @Test void explicitAddressPolicyIsRequired(){assertThatThrownBy(()->new CustomerCredentialConfiguration.Settings("TEST_POLICY_V1","TEST_PRIVACY_V1","TEST_DELIVERY_V1",null,900,100,60,2,3600,1,true)).isInstanceOf(IllegalStateException.class);}
    @Test void enabledCustomerConfigurationRequiresNoRuntimeMigrations(){var env=new org.springframework.mock.env.MockEnvironment();var gateway=mock(CustomerCredentialConfiguration.DeliveryGateway.class);assertThatThrownBy(()->new CustomerCredentialConfiguration().customerCredentialSettings(env,gateway)).isInstanceOf(IllegalStateException.class);}

    @Test void customerSchemaInspectionRejectsMissingFuturePendingAndIncompleteHistoryWithoutMigration(){
        var flyway=mock(org.flywaydb.core.Flyway.class);var info=mock(org.flywaydb.core.api.MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);when(info.pending()).thenReturn(new org.flywaydb.core.api.MigrationInfo[0]);
        when(info.applied()).thenReturn(new org.flywaydb.core.api.MigrationInfo[45]);
        assertThatThrownBy(()->CustomerCredentialConfiguration.validateExistingSchema(flyway)).isInstanceOf(IllegalStateException.class);
        var current=mock(org.flywaydb.core.api.MigrationInfo.class);when(info.current()).thenReturn(current);
        when(current.getVersion()).thenReturn(org.flywaydb.core.api.MigrationVersion.fromVersion("47"));
        assertThatThrownBy(()->CustomerCredentialConfiguration.validateExistingSchema(flyway)).isInstanceOf(IllegalStateException.class);
        when(current.getVersion()).thenReturn(org.flywaydb.core.api.MigrationVersion.fromVersion("46"));
        when(info.pending()).thenReturn(new org.flywaydb.core.api.MigrationInfo[]{mock(org.flywaydb.core.api.MigrationInfo.class)});
        assertThatThrownBy(()->CustomerCredentialConfiguration.validateExistingSchema(flyway)).isInstanceOf(IllegalStateException.class);
        when(info.pending()).thenReturn(new org.flywaydb.core.api.MigrationInfo[0]);when(info.applied()).thenReturn(new org.flywaydb.core.api.MigrationInfo[44]);
        assertThatThrownBy(()->CustomerCredentialConfiguration.validateExistingSchema(flyway)).isInstanceOf(IllegalStateException.class);
        when(info.applied()).thenReturn(new org.flywaydb.core.api.MigrationInfo[45]);CustomerCredentialConfiguration.validateExistingSchema(flyway);
        verify(flyway,times(5)).validate();verify(flyway,never()).migrate();verify(flyway,never()).clean();verify(flyway,never()).repair();
    }
    @Test void schemaInspectorDoesNotIgnoreFutureMigrationsOrCreateSchemas(){
        var ds=mock(javax.sql.DataSource.class);var config=CustomerCredentialConfiguration.schemaInspector(ds).getConfiguration();
        assertThat(config.getIgnoreMigrationPatterns()).isEmpty();assertThat(config.isCreateSchemas()).isFalse();assertThat(config.isCleanDisabled()).isTrue();verifyNoInteractions(ds);
    }
    @Test void stalePolicyLoginAndRecoveryHaveTheSamePublicOutcomesAsUnknownAddresses(){
        var id=UUID.randomUUID();var ref=UUID.randomUUID();var head=new CustomerCredentialRepository.Head(id,ref,crypto.mac("ADDRESS","stale@example.org"),crypto.seal(ref,"stale@example.org"),crypto.keys.version(),State.VERIFIED,1,0,CustomerCredentialSecurity.encode(PASSWORD),"OLD_PRIVACY","OLD_POLICY","OLD_DELIVERY",NOW.plusSeconds(3600));
        when(repository.address(anyString(),anyBoolean())).thenReturn(Optional.of(head));
        assertThat(service.login(UUID.randomUUID(),"stale@example.org",PASSWORD,"loopback").status()).isEqualTo(401);
        assertThat(service.requestRecovery(UUID.randomUUID(),"stale@example.org","loopback").status()).isEqualTo(202);
        when(repository.address(anyString(),anyBoolean())).thenReturn(Optional.empty());
        assertThat(service.login(UUID.randomUUID(),"unknown@example.org",PASSWORD,"loopback").status()).isEqualTo(401);
        assertThat(service.requestRecovery(UUID.randomUUID(),"unknown@example.org","loopback").status()).isEqualTo(202);
        verify(repository,never()).commit(any(),any(),anyString(),any(),any());
    }

    @Test void inactiveAndUnknownLoginAllPerformTheSameDummyPasswordWork(){
        String dummy=(String)org.springframework.test.util.ReflectionTestUtils.getField(service,"dummy");
        for(State state:List.of(State.PENDING,State.SUSPENDED)){
            var id=UUID.randomUUID();var address=UUID.randomUUID();var head=new CustomerCredentialRepository.Head(id,address,"lookup",new byte[32],crypto.keys.version(),state,1,0,CustomerCredentialSecurity.encode(PASSWORD),"TEST_PRIVACY_V1","TEST_POLICY_V1","TEST_DELIVERY_V1",NOW.plusSeconds(3600));
            when(repository.address(anyString(),anyBoolean())).thenReturn(Optional.of(head));
            try(var hashes=mockStatic(CustomerCredentialSecurity.class,CALLS_REAL_METHODS)){
                assertThat(service.login(UUID.randomUUID(),"inactive@example.org",PASSWORD,"loopback").status()).isEqualTo(401);
                hashes.verify(()->CustomerCredentialSecurity.matches(PASSWORD,dummy),times(1));
            }
        }
        when(repository.address(anyString(),anyBoolean())).thenReturn(Optional.empty());
        try(var hashes=mockStatic(CustomerCredentialSecurity.class,CALLS_REAL_METHODS)){
            assertThat(service.login(UUID.randomUUID(),"unknown@example.org",PASSWORD,"loopback").status()).isEqualTo(401);
            hashes.verify(()->CustomerCredentialSecurity.matches(PASSWORD,dummy),times(1));
        }
        verify(repository,never()).commit(any(),any(),anyString(),any(),any());
    }

}

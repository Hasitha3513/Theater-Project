package com.cinenest.iot.internal;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class LockRegistryPolicyTest {
    final LockRegistryPolicy policy=new LockRegistryPolicy(new MockEnvironment());
    static UUID id(String s){return LockRegistryReferenceResolver.fixture(s);}
    static String payload(){return """
        {"commandKey":"%s","expectedRevision":0,"provider":"TUYA",
        "providerDeviceReference":{"id":"%s","version":"%s"},
        "manufacturer":"Fixture","model":"Unqualified", "sku":null,"hardwareRevision":null,"firmwareRevision":null,
        "gatewayReference":null,"capabilityDeclaration":{"source":"CONTRACT_FIXTURE","sourceReference":null,"capabilities":["REVOKE","SCHEDULED_PIN"]}}
        """.formatted(id("command"),id("LOCK-REF-1"),id("LOCK-V1"));}
    LockRegistryPolicy.Registration parse(String json){return policy.parse(id("venue-a"),json.getBytes(StandardCharsets.UTF_8));}
    @Test void disabledAndWrongModeFailClosed(){assertThatThrownBy(policy::configured).isInstanceOf(LockRegistryPolicy.Failure.class);
        var env=new MockEnvironment().withProperty("cinenest.iot.lock-registry.enabled","true").withProperty("cinenest.iot.lock-registry.mode","PRODUCTION");assertThatThrownBy(()->new LockRegistryPolicy(env).configured()).isInstanceOf(LockRegistryPolicy.Failure.class);}
    @Test void controllerIsNotRegisteredWithoutExplicitOptIn(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(LockRegistryController.class).run(ctx->assertThat(ctx).doesNotHaveBean(LockRegistryController.class));
    }
    @Test void validCanonicalPayloadAndSecretsAreProtected(){var r=parse(payload());assertThat(r.capabilities()).containsExactly("REVOKE","SCHEDULED_PIN");assertThat(r.toString()).isEqualTo("Registration[REDACTED]");assertThat(r.reference().toString()).isEqualTo("Reference[REDACTED]");byte[] h=r.hash();h[0]++;assertThat(r.hash()).isNotEqualTo(h);}
    @Test void capabilityOrderingDoesNotChangeCommandHash(){assertThat(parse(payload()).hash()).isEqualTo(parse(payload().replace("\"REVOKE\",\"SCHEDULED_PIN\"","\"SCHEDULED_PIN\",\"REVOKE\"")).hash());}
    @ParameterizedTest @ValueSource(strings={"\"expectedRevision\":0.0","\"expectedRevision\":-1","\"expectedRevision\":1","\"expectedRevision\":\"0\""})
    void rejectsInvalidExpectedRevision(String value){assertThatThrownBy(()->parse(payload().replace("\"expectedRevision\":0",value))).isInstanceOf(LockRegistryPolicy.Failure.class);}
    @Test void rejectsMalformedUnknownDuplicateAndOversizedPayloads(){for(String s:List.of("{}",payload()+payload(),payload().replace("\"provider\":\"TUYA\"","\"provider\":\"TUYA\",\"provider\":\"TUYA\""),payload().replace("\"source\":","\"secret\":\"private\",\"source\":"),payload().replace("\"REVOKE\",","\"REVOKE\",\"REVOKE\","),payload().replace("Unqualified","x".repeat(129))," ".repeat(16385)))assertThatThrownBy(()->parse(s)).isInstanceOf(LockRegistryPolicy.Failure.class);}
    @Test void resolvesOnlyVersionBoundNetworkFreeFixtureIdentity(){var resolver=new LockRegistryReferenceResolver();var ref=parse(payload()).reference();assertThat(resolver.resolve(id("venue-a"),ref)).isEqualTo(resolver.resolve(id("venue-a"),new LockRegistryReferenceResolver.Reference(id("LOCK-ALIAS-1"),id("LOCK-V1"))));assertThatThrownBy(()->resolver.resolve(id("venue-b"),ref)).isInstanceOf(LockRegistryPolicy.Failure.class);assertThatThrownBy(()->resolver.resolve(id("venue-a"),new LockRegistryReferenceResolver.Reference(ref.id(),UUID.randomUUID()))).isInstanceOf(LockRegistryPolicy.Failure.class);assertThatThrownBy(()->resolver.declaration(id("venue-a"),UUID.randomUUID())).isInstanceOf(LockRegistryPolicy.Failure.class);}
}

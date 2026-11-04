package com.cinenest.identity.internal;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalCredentialRecoveryArchitectureTest {
    final Path root=Path.of("src/main/java/com/cinenest/identity/internal");
    @Test void v35QualifiedBytesAndExactObjectCountsRemainUnchanged()throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of("src/main/resources/db/migration/V35__local_synthetic_credential_recovery.sql"));
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo("2aca51352c8d3a8352e1df8bdbeea9661210a4b21a51ced5bb7046e25bd32aaa");
        String s=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);assertThat(s.split("CREATE TABLE ").length-1).isEqualTo(3);assertThat(s.split("CREATE FUNCTION ").length-1).isEqualTo(4);assertThat(s.split("CREATE (?:CONSTRAINT )?TRIGGER ").length-1).isEqualTo(9);
    }
    @Test void identityAdapterCannotWriteHistoricalCredentialsOrOtherModules()throws Exception {
        String s=Files.readString(root.resolve("JdbcLocalCredentialRecoveryRepository.java"));
        assertThat(s).contains("identity.credential.recovery.issue","FOR NO KEY UPDATE","FOR SHARE","RETURNING admitted_at");
        assertThat(s).doesNotContain("UPDATE cinenest_identity.manual_customer","DELETE FROM cinenest_identity.manual_","cinenest_payments.","cinenest_booking.","cinenest_iot.","ALTER TABLE","DISABLE TRIGGER");
    }
    @Test void featureDefaultsDisabledAndControllerIsAuthenticatedSyntheticOnly()throws Exception {
        assertThat(Files.readString(root.resolve("LocalCredentialRecoveryService.java"))).contains("manual.recovery.enabled\",Boolean.class,false","Set.of(\"local-synthetic\")");
        assertThat(Files.readString(root.resolve("LocalCredentialRecoveryController.java"))).contains("@Profile(\"local-synthetic\")","CredentialEpochSessions.binding(authentication)","Cache-Control","no-store");
        assertThat(Files.readString(root.resolve("IdentitySecurity.java"))).contains("CredentialEpochSessions.requests(store)","credential-recovery/capabilities").doesNotContain("csrf.disable");
    }
    @Test void springSessionIsReusedWithRequiredTransactionsAndNoEpochCache()throws Exception {
        String s=Files.readString(root.resolve("CredentialEpochSessions.java"));assertThat(s).contains("setTransactionOperations(tx())","PROPAGATION_REQUIRED","FOR UPDATE","credentialEpoch(b.account(),lock)");
        assertThat(s).doesNotContain("PROPAGATION_REQUIRES_NEW","setUseSecureCookie(false)","DELETE FROM cinenest_identity.http_session WHERE principal_name");
    }
}

package com.cinenest.identity.internal;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CustomerCredentialApplicationArchitectureTest {
    @Test void applicationReusesQualifiedCoreAndHasNoSqlOrProviderProtocol()throws Exception{String s=Files.readString(Path.of("src/main/java/com/cinenest/identity/internal/CustomerCredentialService.java"));assertThat(s).contains("new CustomerCredentialLifecycle","CustomerCredentialPorts.verify","repository.admit","repository.transaction").doesNotContain("JdbcClient","java.net","smtp","INSERT INTO","cinenest_payments","cinenest_booking");}
    @Test void configurationDoesNotChooseProviderAndFailsClosed()throws Exception{String s=Files.readString(Path.of("src/main/java/com/cinenest/identity/internal/CustomerCredentialConfiguration.java"));assertThat(s).contains("KeySource","DeliveryGateway","getRequiredProperty","qualificationOnly","customer-qualification").doesNotContain("spring.mail","smtp.gmail","SendGrid","AmazonSES");}
    @Test void migrationOwnsOnlyIdentityAndProtectsHistory()throws Exception{String s=Files.readString(Path.of("src/main/resources/db/migration/V46__customer_credential_lifecycle.sql"));assertThat(s).contains("customer_command","customer_capability","customer_outbox","customer_lease","read committed","ACCESS EXCLUSIVE","REVOKE ALL").doesNotContain("ALTER TABLE cinenest_identity.manual","cinenest_booking.","cinenest_payments.","CREATE ROLE","DROP TABLE");}
}

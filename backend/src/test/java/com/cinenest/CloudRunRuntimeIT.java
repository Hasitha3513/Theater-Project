package com.cinenest;

import com.cinenest.foundation.internal.CloudRunRuntimeConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** Focused opt-in packaging qualification; never selected by the ordinary *Test convention. */
class CloudRunRuntimeIT {
    MockEnvironment configuration() throws Exception {
        Path ca = Files.createTempFile("cloudrun-unit-ca-", ".pem");
        ca.toFile().deleteOnExit();
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("cloud-run");
        return env.withProperty("PORT", "8081").withProperty("CINENEST_DB_POOL_MAX", "2")
            .withProperty("CINENEST_DB_USER", "fixture")
            .withProperty("CINENEST_DB_PASSWORD", "never-log-this-fixture")
            .withProperty("CINENEST_DB_URL", "jdbc:postgresql://localhost:5432/cloudrun_qualification?sslmode=verify-full&sslrootcert=" + ca);
    }
    @Test void acceptsExplicitConfiguration() throws Exception { assertThatCode(() -> CloudRunRuntimeConfiguration.validate(configuration())).doesNotThrowAnyException(); }
    @Test void refusesMissingSecretWithoutEcho() throws Exception {
        var env = configuration(); env.setProperty("CINENEST_DB_PASSWORD", "");
        assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Cloud Run configuration unavailable or denied").hasNoCause();
    }
    @Test void rejectsSyntheticAndFinancialActivation() throws Exception {
        for (String name : new String[]{"cinenest.bank.enabled", "cinenest.identity.manual.enabled", "CINENEST_BANK_LEDGER_ENABLED", "cinenest.identity.google.live-execution", "cinenest.booking.synthetic-check-in.enabled"}) {
            var env = configuration().withProperty(name, "true");
            assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void refusesMixedProfiles() throws Exception {
        var env = configuration(); env.setActiveProfiles("cloud-run", "local-synthetic");
        assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesMalformedPortsAndPool() throws Exception {
        for (var pair : Map.of("PORT", "0", "CINENEST_DB_POOL_MAX", "33", "CINENEST_BIND_ADDRESS", "external.test").entrySet()) {
            var env = configuration().withProperty(pair.getKey(), pair.getValue());
            assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void refusesTlsBypassAndUrlCredentials() throws Exception {
        for (String url : new String[]{"jdbc:postgresql://localhost/db?sslmode=require", "jdbc:postgresql://user:secret@localhost/db?sslmode=disable", "jdbc:postgresql://localhost/db?sslmode=verify-full&sslrootcert=/missing", "jdbc:postgresql://localhost/db?sslmode=verify-full&sslmode=disable"}) {
            var env = configuration().withProperty("CINENEST_DB_URL", url);
            assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).hasMessage("Cloud Run configuration unavailable or denied").hasNoCause();
        }
    }
    @Test void refusesFlywayDisablingOrCleanOverride() throws Exception {
        for (String property : new String[]{"spring.flyway.enabled", "spring.flyway.clean-disabled"}) {
            var env = configuration().withProperty(property, "false");
            assertThatThrownBy(() -> CloudRunRuntimeConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void startupValidatesWithoutMigrating() throws Exception {
        var flyway=org.mockito.Mockito.mock(Flyway.class);
        var info=org.mockito.Mockito.mock(org.flywaydb.core.api.MigrationInfoService.class);
        org.mockito.Mockito.when(flyway.info()).thenReturn(info);
        org.mockito.Mockito.when(info.current()).thenReturn(org.mockito.Mockito.mock(org.flywaydb.core.api.MigrationInfo.class));
        org.mockito.Mockito.when(info.pending()).thenReturn(new org.flywaydb.core.api.MigrationInfo[0]);
        var configuration=new CloudRunRuntimeConfiguration(configuration());
        var method=CloudRunRuntimeConfiguration.class.getDeclaredMethod("validateDeploymentSchema");method.setAccessible(true);
        var strategy=(org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy)method.invoke(configuration);
        strategy.migrate(flyway);
        org.mockito.Mockito.verify(flyway).validate();
        org.mockito.Mockito.verify(flyway,org.mockito.Mockito.never()).migrate();
    }
    @Test void missingOrPendingSchemaCannotStartOrMigrate() throws Exception {
        for (boolean pending : new boolean[]{false,true}) {
            var flyway=org.mockito.Mockito.mock(Flyway.class);
            var info=org.mockito.Mockito.mock(org.flywaydb.core.api.MigrationInfoService.class);
            org.mockito.Mockito.when(flyway.info()).thenReturn(info);
            if(pending)org.mockito.Mockito.when(info.current()).thenReturn(org.mockito.Mockito.mock(org.flywaydb.core.api.MigrationInfo.class));
            org.mockito.Mockito.when(info.pending()).thenReturn(pending?new org.flywaydb.core.api.MigrationInfo[1]:new org.flywaydb.core.api.MigrationInfo[0]);
            var method=CloudRunRuntimeConfiguration.class.getDeclaredMethod("validateDeploymentSchema");method.setAccessible(true);
            var strategy=(org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy)method.invoke(new CloudRunRuntimeConfiguration(configuration()));
            assertThatThrownBy(()->strategy.migrate(flyway)).hasMessageContaining("separate migration approval required");
            org.mockito.Mockito.verify(flyway,org.mockito.Mockito.never()).migrate();
        }
    }
    @Test void readinessFailureIsRedactedAndLivenessIsIndependent() throws Exception {
        var flyway=org.mockito.Mockito.mock(Flyway.class);
        org.mockito.Mockito.when(flyway.info()).thenThrow(new IllegalStateException("private-diagnostic-sentinel"));
        var type=Class.forName("com.cinenest.foundation.internal.CloudRunRuntimeConfiguration$DeploymentHealth");
        var constructor=type.getDeclaredConstructor(Flyway.class);constructor.setAccessible(true);var controller=constructor.newInstance(flyway);
        var ready=type.getDeclaredMethod("ready");ready.setAccessible(true);
        var response=(org.springframework.http.ResponseEntity<?>)ready.invoke(controller);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(Map.of("status","DOWN"));
        assertThat(response.toString()).doesNotContain("private-diagnostic-sentinel");
        org.mockito.Mockito.clearInvocations(flyway);
        var live=type.getDeclaredMethod("live");live.setAccessible(true);
        assertThat(((org.springframework.http.ResponseEntity<?>)live.invoke(controller)).getStatusCode().value()).isEqualTo(200);
        org.mockito.Mockito.verifyNoInteractions(flyway);
    }
    @Test
    @EnabledIfEnvironmentVariable(named="CINENEST_CLOUD_RUN_DB_TEST",matches="true")
    void prepareIsolatedSchema() throws Exception {
        String url = System.getenv("CINENEST_PACKAGING_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").contains("/cloudrun_qualification?sslmode=verify-full&sslrootcert=/tmp/");
        String owner = System.getenv("CINENEST_PACKAGING_DB_OWNER");
        String password = System.getenv("CINENEST_PACKAGING_DB_PASSWORD");
        String runtimePassword = System.getenv("CINENEST_PACKAGING_RUNTIME_PASSWORD");
        assertThat(runtimePassword).matches("[a-f0-9]{48}");
        var flyway = Flyway.configure().dataSource(url, owner, password).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(44);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("45");
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, owner, password); var sql = connection.createStatement()) {
            var ssl = sql.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()"); ssl.next(); assertThat(ssl.getBoolean(1)).isTrue();
            sql.execute("CREATE ROLE cloudrun_runtime LOGIN PASSWORD '" + runtimePassword + "'");
            sql.execute("GRANT CONNECT ON DATABASE cloudrun_qualification TO cloudrun_runtime");
            sql.execute("GRANT USAGE ON SCHEMA public TO cloudrun_runtime");
            sql.execute("GRANT SELECT ON TABLE public.flyway_schema_history TO cloudrun_runtime");
        }
        try (var connection = DriverManager.getConnection(url, "cloudrun_runtime", runtimePassword); var sql = connection.createStatement()) {
            assertThatThrownBy(() -> sql.execute("CREATE TABLE public.forbidden_packaging_write (id integer)"))
                .isInstanceOf(java.sql.SQLException.class).satisfies(e -> assertThat(((java.sql.SQLException)e).getSQLState()).isEqualTo("42501"));
        }
        var runtime = Flyway.configure().dataSource(url, "cloudrun_runtime", runtimePassword).load();
        runtime.validate(); assertThat(runtime.info().pending()).isEmpty();
    }
}

/** Ordinary Surefire discovery for offline cases within the explicitly authorized single test-source path. */
class CloudRunRuntimeTest extends CloudRunRuntimeIT {}

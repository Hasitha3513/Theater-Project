package com.cinenest;

import com.cinenest.foundation.MigrationStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired MigrationStatus migrations;
    @Autowired Environment environment;

    @Test
    void postgres18AndMigrationAreRealAndRepeatable() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            try (var query = connection.createStatement();
                 var result = query.executeQuery("select schema_name from information_schema.schemata where schema_name = 'cinenest_foundation'")) {
                assertThat(result.next()).isTrue();
            }
        }
        assertThat(migrations.isCurrent()).isTrue();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void nonexistentCapabilitiesAreDeniedOverRealHttp() throws Exception {
        var client = HttpClient.newHttpClient();
        var port = environment.getRequiredProperty("local.server.port");
        for (String path : new String[]{"/", "/api/bookings", "/admin", "/login"}) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isIn(401, 403);
            assertThat(response.body()).doesNotContain("password", "jdbc:");
        }
    }

    @Test void publicCatalogIsReadableWhenIdentityIsDisabled() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getRequiredProperty("local.server.port")+"/api/catalog/venues")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).doesNotContain("operationalApprovalReference","jdbc:");
    }
}

package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.SNAPSHOTS;
import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.flyway;
import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.id;
import static com.cinenest.pricing.internal.CustomerCommercialQuoteServiceTest.rejects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

@EnabledIfEnvironmentVariable(named = "CINENEST_V41_QUALIFICATION", matches = "AUTHORIZED_DISPOSABLE")
class CustomerCommercialQuotePostgresTest {
    @Test
    void cleanV41AndDefaultDisabledBoundary() {
        var db = new CommercialPolicyActivationPostgresTest();
        db.init("cinenest_test");
        var latest = flyway("cinenest_test", "41");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(40);
        latest.validate();
        assertThat(latest.info().current().getVersion().toString()).isEqualTo("41");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='31'",Integer.class)).isZero();
        rejects(new CustomerCommercialQuoteConfiguration()::requireOperationalIssuance,503);
        assertThat(db.count("customer_commercial_quote")).isZero();
    }

    @Test
    void upgradePreservesHistoryAndQuoteRequiresAtomicAudit() {
        String database = "cinenest_v40_upgrade";
        var db = new CommercialPolicyActivationPostgresTest();
        db.init(database);
        assertThat(flyway(database, "40").migrate().migrationsExecuted).isEqualTo(39);
        db.seedHistory();
        String checksumSql = """
            SELECT jsonb_object_agg(version,checksum)::text FROM flyway_schema_history
            WHERE success AND version IS NOT NULL AND version <> '41'
            """;
        var history = db.jdbc.queryForObject(SNAPSHOTS,String.class);
        var checksums = db.jdbc.queryForObject(checksumSql,String.class);
        assertThat(history).contains("SIMULATED");
        var latest = flyway(database,"41");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        latest.validate();
        assertThat(db.jdbc.queryForObject(checksumSql,String.class)).isEqualTo(checksums);

        var repository = new JdbcCustomerCommercialQuoteRepository(db.client);
        var setup = new CustomerCommercialQuoteServiceTest.Setup(repository.now());
        var service = setup.service(repository,db.tx);
        rejects(() -> service.qualify(id("finance"),setup.command),404);
        rejects(() -> service.qualify(setup.actor,setup.command(2,id("venue_B"),setup.room,setup.version,"BANK")),404);
        rejects(() -> service.qualify(setup.actor,setup.command(1,setup.venue,setup.room,setup.version,"BANK")),409);
        setup.lockedHold(setup.now.minusSeconds(1));
        rejects(() -> service.qualify(setup.actor,setup.command),409);
        setup.lockedHold(setup.now.plusSeconds(300));
        setup.current=Optional.empty();
        rejects(() -> service.qualify(setup.actor,setup.command),409);
        setup.current=Optional.of(setup.policy(Set.of(),setup.now));

        db.installAuditFailure("customer_commercial_quote_audit");
        try {
            assertThatThrownBy(() -> service.qualify(setup.actor,setup.command)).isInstanceOf(DataAccessException.class);
            assertThat(db.count("customer_commercial_quote")).isZero();
        } finally {
            db.removeAuditFailure("customer_commercial_quote_audit");
        }
        var quote = service.qualify(setup.actor,setup.command);
        assertThat(service.qualify(setup.actor,setup.command)).isEqualTo(quote);
        rejects(() -> service.qualify(setup.actor,setup.command(2,setup.venue,setup.room,setup.version,"GATEWAY")),409);
        setup.current=Optional.of(setup.policy(Set.of(id("FINANCE-appointment")),setup.now));
        rejects(() -> service.qualify(setup.actor,setup.command),409);
        assertThat(db.jdbc.queryForObject(
            "SELECT total_minor FROM cinenest_pricing.customer_commercial_quote WHERE quote_id=?",
            Long.class,quote)).isEqualTo(276100L);
        assertThatThrownBy(() -> db.jdbc.update("UPDATE cinenest_pricing.customer_commercial_quote SET total_minor=1"))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> db.jdbc.update("DELETE FROM cinenest_pricing.customer_commercial_quote_audit"))
            .isInstanceOf(DataAccessException.class);
        assertThat(db.count("customer_commercial_quote")).isEqualTo(1);
        assertThat(db.count("customer_commercial_quote_audit")).isEqualTo(1);
        assertThat(db.jdbc.queryForObject(SNAPSHOTS,String.class)).isEqualTo(history);
    }
}

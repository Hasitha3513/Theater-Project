package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.rooms.PackageConstraints;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

@EnabledIfEnvironmentVariable(named="CINENEST_V40_QUALIFICATION", matches="AUTHORIZED_DISPOSABLE")
class CommercialQuotationIssuancePostgresTest {
    @Test
    void populatedUpgradeAndFixtureQuotationIntegrity() throws Exception {
        String database = "cinenest_v40_upgrade";
        var fixture = new CommercialPolicyActivationPostgresTest();
        fixture.init(database);
        assertThat(flyway(database, "38").migrate().migrationsExecuted).isEqualTo(37);
        fixture.seedHistory();
        var history = fixture.jdbc.queryForObject(SNAPSHOTS, String.class);
        var checksums = fixture.jdbc.queryForObject(MIGRATION_HISTORY, String.class);
        assertThat(history).contains("SIMULATED");
        var flyway = flyway(database, "40");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        fixture.validate(flyway);
        assertThat(fixture.jdbc.queryForObject(MIGRATION_HISTORY, String.class)).isEqualTo(checksums);
        assertThat(fixture.jdbc.queryForObject(SNAPSHOTS, String.class)).isEqualTo(history);

        fixture.prepareActivation();
        UUID actor = id("submitter");
        UUID venue = id("venue_A");
        UUID room = id("room_A1");
        UUID pkg = id("package");
        UUID version = id("package_version");
        UUID holdId = id("new_hold");
        UUID activation = fixture.service.activateFixture(id("admin"), UUID.randomUUID(),
            fixture.scope, fixture.approvals, fixture.checkpoint);
        when(fixture.identity.eligible(actor, true)).thenReturn(true);
        when(fixture.identity.eligible(id("finance"), true)).thenReturn(true);
        var holds = mock(QuotationHolds.class);
        var hold = new QuotationHolds.Facts(holdId, actor, venue, room, fixture.now.plusSeconds(60),
            fixture.now.plusSeconds(60 + 180*60), fixture.now.plusSeconds(180), "ACTIVE", 2, 4,
            "Asia/Colombo", 15, fixture.now.plusSeconds(60 + 195*60), "SYNTHETIC");
        when(holds.locate(holdId)).thenReturn(Optional.of(hold));
        when(holds.lockAndInspect(actor, holdId)).thenReturn(Optional.of(hold));
        var publications = mock(PublicationRepository.class);
        var snapshot = new QuoteRulesTest().pkg("OPTIONAL");
        snapshot.put("options", List.of());
        snapshot.put("roomIds", List.of(room.toString()));
        when(publications.catalog(venue)).thenReturn(List.of(Map.of("packageId",pkg,"versionId",version,
            "sessionFrom",fixture.now.toString(),"sessionTo",fixture.now.plusSeconds(86400).toString(),"snapshot",snapshot)));
        var rooms = mock(PackageConstraints.class);
        when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.of(
            new PackageConstraints.Facts(venue,0,id("room_policy"),0,180,
                List.of(new PackageConstraints.RoomFact(room,5,0,Set.of())))));
        var drafts = mock(DraftRepository.class);
        when(drafts.options(venue)).thenReturn(List.of());
        var service = new CommercialQuotationIssuanceService(fixture.identity,publications,drafts,rooms,holds,
            fixture.repository,new JdbcCommercialQuotationRepository(fixture.client),fixture.tx);
        UUID key = UUID.randomUUID();
        var command = new CommercialQuotationIssuanceService.Command(holdId,2,pkg,version,activation,key,
            "BANK","ONLINE",Set.of());
        assertThatThrownBy(() -> service.issueFixture(id("finance"), command)).isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> service.issueFixture(actor,new CommercialQuotationIssuanceService.Command(
            holdId,1,pkg,version,activation,key,"BANK","ONLINE",Set.of()))).isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> service.issueFixture(actor,new CommercialQuotationIssuanceService.Command(
            holdId,2,pkg,id("wrong-version"),activation,key,"BANK","ONLINE",Set.of()))).isInstanceOf(DraftFailure.class);

        var expired = new QuotationHolds.Facts(hold.id(),hold.owner(),hold.venue(),hold.room(),hold.start(),hold.end(),
            fixture.now.minusSeconds(1),hold.state(),hold.revision(),hold.guests(),hold.timezone(),hold.bufferMinutes(),
            hold.blockedUntil(),hold.configuration());
        when(holds.lockAndInspect(actor, holdId)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.issueFixture(actor,command)).isInstanceOf(DraftFailure.class);
        when(holds.lockAndInspect(actor, holdId)).thenReturn(Optional.of(hold));

        fixture.installAuditFailure("fixture_commercial_quote_audit");
        assertThatThrownBy(() -> service.issueFixture(actor,command)).isInstanceOf(DataAccessException.class);
        assertThat(fixture.count("fixture_commercial_quotation")).isZero();
        fixture.removeAuditFailure("fixture_commercial_quote_audit");
        UUID quote = service.issueFixture(actor,command);
        assertThat(service.issueFixture(actor,command)).isEqualTo(quote);
        assertThatThrownBy(() -> service.issueFixture(actor,new CommercialQuotationIssuanceService.Command(
            holdId,2,pkg,version,activation,key,"GATEWAY","ONLINE",Set.of()))).isInstanceOf(DraftFailure.class);
        assertThat(fixture.jdbc.queryForObject(
            "SELECT total_minor FROM cinenest_pricing.fixture_commercial_quotation WHERE quote_id=?",
            Long.class,quote)).isEqualTo(250_000L);
        assertThatThrownBy(() -> fixture.jdbc.update("UPDATE cinenest_pricing.fixture_commercial_quotation SET total_minor=1"))
            .isInstanceOf(DataAccessException.class);
        fixture.service.reconcileFixtureRevocations(fixture.revocations(2,
            Set.of(fixture.approvals.getFirst().appointment().id())));
        assertThatThrownBy(() -> service.issueFixture(actor,command)).isInstanceOf(DraftFailure.class);
        assertThat(fixture.count("fixture_commercial_quotation")).isEqualTo(1);
        assertThat(fixture.count("fixture_commercial_quote_audit")).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject(SNAPSHOTS,String.class)).isEqualTo(history);
    }
}

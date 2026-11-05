package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommercialConfigurationTest {
    @Test void ownerValuesRemainPlanningPreferencesWithNoInventedScopeOrProvenance() {
        var c = CommercialConfiguration.ownerPreferences();
        assertThat(c.identifier()).isEqualTo("DAY05_INITIAL_VENUE_COMMERCIAL_V1");
        assertThat(c.version()).isEqualTo(1);
        assertThat(c.status()).isEqualTo(CommercialConfiguration.Status.OWNER_PREFERENCES_APPROVED_SCOPE_PENDING);
        assertThat(c.currency()).isEqualTo("LKR");
        assertThat(c.prices()).isEqualTo(new CommercialConfiguration.Prices(250000,100000,80000,100000,10000));
        assertThat(c.includedMinutes()).isEqualTo(180);
        assertThat(c.extensionStepMinutes()).isEqualTo(60);
        assertThat(c.includedGuests()).isEqualTo(4);
        assertThat(c.maximumGuests()).isEqualTo(5);
        assertThat(c.cleaningBufferMinutes()).isEqualTo(15);
        assertThat(c.venueId()).isNull();
        assertThat(c.roomCapacities()).isEmpty();
        assertThat(c.maximumMinutes()).isNull();
        assertThat(c.effectiveAt()).isNull();
        assertThat(c.decisionReference()).isNull();
        assertThat(c.reviews()).isEmpty();
    }

    @Test void collectionsAreDefensivelyCopiedAndCannotMutateHistory() {
        var rooms = new HashMap<UUID,Integer>();
        var room = UUID.randomUUID(); rooms.put(room,5);
        var reviews = new HashMap<CommercialConfiguration.Requirement,CommercialConfiguration.Review>();
        var original = CommercialConfigurationRulesTest.configuration(1,rooms,reviews);
        rooms.clear(); reviews.put(CommercialConfiguration.Requirement.TAX,null);
        assertThat(original.roomCapacities()).containsEntry(room,5);
        assertThat(original.reviews()).isEmpty();
        assertThatThrownBy(() -> original.roomCapacities().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> original.reviews().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void revisedPriceRequiresIncreasingVersionAndPreservesOriginal() {
        var original = CommercialConfigurationRulesTest.complete();
        var revised = new CommercialConfiguration(original.identifier(),2,"new-owner-decision",original.status(),
                original.venueId(),original.roomCapacities(),"LKR",new CommercialConfiguration.Prices(300000,100000,80000,100000,10000),
                180,60,4,5,15,300,Instant.parse("2026-12-01T00:00:00Z"),false,Map.of());
        CommercialConfigurationRules.requireSuccessor(original,revised);
        assertThat(original.version()).isEqualTo(1);
        assertThat(original.prices().baseMinor()).isEqualTo(250000);
        assertThat(revised.prices().baseMinor()).isEqualTo(300000);
        assertThatThrownBy(() -> CommercialConfigurationRules.requireSuccessor(original,original)).isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> CommercialConfigurationRules.requireSuccessor(revised,original)).isInstanceOf(DraftFailure.class);
    }
}

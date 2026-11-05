package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommercialConfigurationRulesTest {
    static final UUID VENUE = UUID.randomUUID(), ROOM = UUID.randomUUID();
    static CommercialConfiguration configuration(long version, Map<UUID,Integer> rooms,
            Map<CommercialConfiguration.Requirement,CommercialConfiguration.Review> reviews) {
        return new CommercialConfiguration("DAY05_INITIAL_VENUE_COMMERCIAL_V1",version,"test-decision-not-authority",
                CommercialConfiguration.Status.OWNER_PREFERENCES_APPROVED_SCOPE_PENDING,VENUE,rooms,"LKR",
                CommercialConfiguration.ownerPreferences().prices(),180,60,4,5,15,300,
                Instant.parse("2026-12-01T00:00:00Z"),false,reviews);
    }
    static CommercialConfiguration complete() { return configuration(1,Map.of(ROOM,5),Map.of()); }
    static CommercialConfiguration change(CommercialConfiguration c, UUID venue, Map<UUID,Integer> rooms,
            CommercialConfiguration.Prices prices, int included, int step, int guests, int maximumGuests,
            Integer maximum, Instant effective, boolean synthetic) {
        return new CommercialConfiguration(c.identifier(),c.version(),c.decisionReference(),c.status(),venue,rooms,
                c.currency(),prices,included,step,guests,maximumGuests,c.cleaningBufferMinutes(),maximum,effective,synthetic,c.reviews());
    }
    static java.util.List<String> blockers(CommercialConfiguration c, boolean ps4) {
        var result = CommercialConfigurationRules.assess(c,ps4);
        assertThat(result.commerciallyEligible()).isFalse(); return result.blockers();
    }

    @Test void unresolvedOwnerPreferencesFailClosedWithExplicitReasons() {
        assertThat(blockers(CommercialConfiguration.ownerPreferences(),true)).contains(
                "OFFLINE_PLANNING_ONLY","MISSING_DECISION_REFERENCE","MISSING_VENUE_OR_ROOM_SCOPE",
                "MISSING_MAXIMUM_DURATION","MISSING_EFFECTIVE_DATE","PENDING_TAX","PENDING_FINANCE",
                "PENDING_LEGAL","PENDING_CANCELLATION_REFUND","PENDING_OPERATIONS","PENDING_PS4_EQUIPMENT","PENDING_PS4_RIGHTS");
    }

    @Test void exactComponentsExcludeUnapprovedFeesAndTaxWithoutInventingPayableTotal() {
        var c=complete();
        assertThat(CommercialConfigurationRules.knownPackageComponents(c,180,4,false)).isEqualTo(250000);
        assertThat(CommercialConfigurationRules.knownPackageComponents(c,240,5,true)).isEqualTo(530000);
        assertThat(blockers(c,false)).contains("PENDING_TAX","PENDING_FINANCE");
    }

    @Test void checkedArithmeticRejectsOverflow() {
        var c=complete();
        var huge=change(c,VENUE,c.roomCapacities(),new CommercialConfiguration.Prices(Long.MAX_VALUE,1,1,1,1),180,60,4,5,300,c.effectiveAt(),false);
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(huge,240,4,false)).isInstanceOf(DraftFailure.class);
        var multiply=change(c,VENUE,c.roomCapacities(),new CommercialConfiguration.Prices(1,Long.MAX_VALUE,1,1,1),180,60,4,5,300,c.effectiveAt(),false);
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(multiply,300,4,false)).isInstanceOf(DraftFailure.class);
    }

    @Test void invalidSelectionsAndDurationsAreRejected() {
        var c=complete();
        for(int minutes:new int[]{0,179,181,210,301,Integer.MAX_VALUE})
            assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(c,minutes,4,false)).isInstanceOf(DraftFailure.class);
        for(int guests:new int[]{0,-1,6})
            assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(c,180,guests,false)).isInstanceOf(DraftFailure.class);
        var invalid=change(c,VENUE,c.roomCapacities(),c.prices(),180,0,4,5,300,c.effectiveAt(),false);
        assertThat(blockers(invalid,false)).contains("INVALID_DURATION","INVALID_MAXIMUM_DURATION");
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(invalid,180,4,false)).isInstanceOf(DraftFailure.class);
    }

    @Test void scopeMaximumDateAndCapacityAreMandatory() {
        var c=complete();
        var missing=change(c,null,Map.of(),c.prices(),180,60,4,5,null,null,false);
        assertThat(blockers(missing,false)).contains("MISSING_VENUE_OR_ROOM_SCOPE","MISSING_MAXIMUM_DURATION","MISSING_EFFECTIVE_DATE");
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(missing,180,4,false)).isInstanceOf(DraftFailure.class);
        var small=change(c,VENUE,Map.of(ROOM,4),c.prices(),180,60,4,5,300,c.effectiveAt(),false);
        assertThat(blockers(small,false)).contains("INVALID_ROOM_CAPACITY");
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(small,180,5,false)).isInstanceOf(DraftFailure.class);
    }

    @Test void invalidGuestMaximumAndNegativeAmountsAreRejected() {
        var c=complete();
        var invalid=change(c,VENUE,c.roomCapacities(),new CommercialConfiguration.Prices(0,-1,-1,-1,-1),180,60,5,4,180,c.effectiveAt(),false);
        assertThat(blockers(invalid,false)).contains("INVALID_AMOUNTS","INVALID_GUEST_COUNTS");
        assertThatThrownBy(() -> CommercialConfigurationRules.knownPackageComponents(invalid,180,4,false)).isInstanceOf(DraftFailure.class);
        assertThat(blockers(change(c,VENUE,c.roomCapacities(),c.prices(),180,60,4,5,181,c.effectiveAt(),false),false)).contains("INVALID_MAXIMUM_DURATION");
    }

    @Test void referenceStringsAndCompleteLookingApprovalsCannotCertifyAuthority() {
        var reviews=new EnumMap<CommercialConfiguration.Requirement,CommercialConfiguration.Review>(CommercialConfiguration.Requirement.class);
        for(var r:CommercialConfiguration.Requirement.values()) reviews.put(r,new CommercialConfiguration.Review(
                CommercialConfiguration.Approval.APPROVED,"claimed-review",UUID.randomUUID(),VENUE,1,Instant.parse("2026-11-01T00:00:00Z")));
        assertThat(blockers(configuration(1,Map.of(ROOM,5),reviews),true)).contains("UNVERIFIED_APPROVAL_TAX","UNVERIFIED_APPROVAL_FINANCE","UNVERIFIED_APPROVAL_LEGAL");
        reviews.put(CommercialConfiguration.Requirement.TAX,new CommercialConfiguration.Review(CommercialConfiguration.Approval.APPROVED,"reference-only",null,null,1,null));
        assertThat(blockers(configuration(1,Map.of(ROOM,5),reviews),true)).contains("INVALID_PROVENANCE_TAX");
        assertThat(blockers(configuration(2,Map.of(ROOM,5),reviews),true)).contains("INVALID_PROVENANCE_FINANCE");
    }

    @Test void pendingRejectedWrongVenueAndSyntheticFactsStayIneligible() {
        var reviews=Map.of(CommercialConfiguration.Requirement.FINANCE,new CommercialConfiguration.Review(CommercialConfiguration.Approval.REJECTED,"rejected",UUID.randomUUID(),VENUE,1,Instant.now()),
                CommercialConfiguration.Requirement.LEGAL,new CommercialConfiguration.Review(CommercialConfiguration.Approval.APPROVED,"other-venue",UUID.randomUUID(),UUID.randomUUID(),1,Instant.now()));
        assertThat(blockers(configuration(1,Map.of(ROOM,5),reviews),false)).contains("PENDING_FINANCE","INVALID_PROVENANCE_LEGAL");
        var c=complete();
        assertThat(blockers(change(c,VENUE,c.roomCapacities(),c.prices(),180,60,4,5,300,c.effectiveAt(),true),true)).contains("SYNTHETIC_CONFIGURATION");
    }

    @Test void ps4RequirementsApplyOnlyWhenSelectedAndDoNotImplyAvailability() {
        assertThat(blockers(complete(),false)).doesNotContain("PENDING_PS4_EQUIPMENT","PENDING_PS4_RIGHTS");
        assertThat(blockers(complete(),true)).contains("PENDING_PS4_EQUIPMENT","PENDING_PS4_RIGHTS");
    }

    @Test void invalidVersionFailsClosedAndSyntheticQuoteRemainsSynthetic() {
        assertThat(blockers(configuration(0,Map.of(ROOM,5),Map.of()),false)).contains("INVALID_VERSION_OR_IDENTITY");
        var fixture=new QuoteRulesTest();
        var pkg=fixture.pkg("OPTIONAL"); var hold=fixture.hold(180,4);
        var before=QuoteRules.calculate(pkg,hold,java.util.Set.of(),"BANK",7);
        CommercialConfigurationRules.assess(CommercialConfiguration.ownerPreferences(),true);
        var after=QuoteRules.calculate(pkg,hold,java.util.Set.of(),"BANK",7);
        assertThat(after).isEqualTo(before).containsEntry("synthetic",true).containsEntry("commercialIssuance",false).containsEntry("checkoutEnabled",false);
        assertThat(after.get("totalMinor")).isEqualTo("250007");
    }
}

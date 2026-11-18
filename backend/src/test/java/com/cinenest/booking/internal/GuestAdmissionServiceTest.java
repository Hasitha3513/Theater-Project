package com.cinenest.booking.internal;

import static com.cinenest.booking.internal.GuestAdmissionService.Status.*;
import static com.cinenest.booking.SyntheticGuestAdmission.Outcome.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.cinenest.booking.SyntheticGuestAdmission.Decision;
import com.cinenest.booking.SyntheticGuestAdmission.Scope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestAdmissionServiceTest {
    private final UUID guest = UUID.randomUUID();
    private final Scope scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, 3, "v1");

    @Test void onlyExactCurrentDecisionsForEveryPurchasedPlaceAllowReview() {
        var decisions = Map.of(guest, new Decision(scope, ELIGIBLE, false));
        assertThat(GuestAdmissionService.evaluate(1, List.of(guest), scope, decisions).status())
                .isEqualTo(ELIGIBLE_FOR_STAFF_REVIEW);
        assertThat(GuestAdmissionService.evaluate(2, List.of(guest), scope, decisions).status())
                .isEqualTo(PENDING_VERIFICATION);
        assertThat(GuestAdmissionService.evaluate(1, List.of(guest), scope, Map.of()).status())
                .isEqualTo(PENDING_VERIFICATION);
    }

    @Test void revokedAndOldDecisionsCannotAuthorizeReview() {
        var oldRoster = new Scope(scope.booking(), scope.venue(), scope.room(), 2, 2, "v1");
        var oldBooking = new Scope(scope.booking(), scope.venue(), scope.room(), 1, 3, "v1");
        var oldPolicy = new Scope(scope.booking(), scope.venue(), scope.room(), 2, 3, "old");
        for (var stale : List.of(new Decision(oldRoster, ELIGIBLE, false), new Decision(oldBooking, ELIGIBLE, false),
                new Decision(oldPolicy, ELIGIBLE, false), new Decision(scope, ELIGIBLE, true))) {
            assertThat(GuestAdmissionService.evaluate(1, List.of(guest), scope, Map.of(guest, stale)).status())
                    .isEqualTo(PENDING_VERIFICATION);
        }
    }

    @Test void deniedAndInvalidRostersFailClosed() {
        assertThat(GuestAdmissionService.evaluate(1, List.of(guest), scope,
                Map.of(guest, new Decision(scope, DENIED, false))).status()).isEqualTo(INELIGIBLE);
        assertThat(GuestAdmissionService.evaluate(2, List.of(guest, guest), scope, Map.of()).status())
                .isEqualTo(INELIGIBLE);
    }
}

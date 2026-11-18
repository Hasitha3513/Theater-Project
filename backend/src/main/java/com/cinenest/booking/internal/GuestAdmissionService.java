package com.cinenest.booking.internal;

import com.cinenest.booking.BookingAccess;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import com.cinenest.booking.SyntheticGuestAdmission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GuestAdmissionService {
    static final String POLICY = "SYNTHETIC-STAFF-ADMISSION-V1";
    private static final String CONFIRMED = "SIMULATED_CONFIRMED";
    private final StaffBookingReadStore staffBookings;
    private final BookingAccess bookings;
    private final GuestRosterStore rosters;
    private final ScopedAuthorization authorization;
    private final PackageConstraints rooms;
    private final ObjectProvider<SyntheticGuestAdmission> decisions;
    private final Environment environment;
    private final boolean enabled;

    GuestAdmissionService(StaffBookingReadStore staffBookings, BookingAccess bookings, GuestRosterStore rosters,
                          ScopedAuthorization authorization, PackageConstraints rooms,
                          ObjectProvider<SyntheticGuestAdmission> decisions, Environment environment,
                          @Value("${cinenest.booking.guest-admission.enabled:false}") boolean enabled) {
        this.staffBookings = staffBookings;
        this.bookings = bookings;
        this.rosters = rosters;
        this.authorization = authorization;
        this.rooms = rooms;
        this.decisions = decisions;
        this.environment = environment;
        this.enabled = enabled;
    }

    enum Status { NOT_CONFIRMED, PENDING_VERIFICATION, INELIGIBLE, ELIGIBLE_FOR_STAFF_REVIEW, UNAVAILABLE }

    record Assessment(Status status, int checkedGuestCount) {}

    record Summary(UUID bookingId, UUID venueId, UUID roomId, long bookingRevision, Instant scheduledStart,
                   Instant scheduledEnd, String bookingState, int permittedGuestCount, int checkedGuestCount,
                   Status status, boolean synthetic, boolean physicalEntryAuthorized) {}

    @Transactional
    public Summary read(UUID actor, UUID venue, UUID booking) {
        if (!enabled || !Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation"))) {
            throw new InventoryFailure(503, "Synthetic admission assessment unavailable");
        }
        if (!authorization.member(actor, venue, true)
                || !authorization.permits(actor, venue, "booking.synthetic.read", true)
                || !authorization.permits(actor, venue, "guest.admission.synthetic.read", true)) {
            throw new InventoryFailure(403, "Admission assessment denied");
        }
        var initial = staffBookings.detail(venue, booking)
                .orElseThrow(() -> new InventoryFailure(404, "Synthetic booking not found"));
        Assessment assessment = new Assessment(Status.NOT_CONFIRMED, 0);
        if (CONFIRMED.equals(initial.state())) {
            var locked = bookings.lock(booking);
            assessment = locked.filter(context -> matches(initial, context))
                    .map(context -> assessRoster(initial))
                    .orElse(new Assessment(Status.UNAVAILABLE, 0));
        }
        rosters.audit(actor, booking, "ADMISSION_READ");
        return new Summary(initial.bookingId(), initial.venueId(), initial.roomId(), initial.revision(),
                initial.scheduledStart(), initial.scheduledEnd(), initial.state(), initial.purchasedGuestCount(),
                assessment.checkedGuestCount(), assessment.status(), true, false);
    }

    private static boolean matches(StaffBookingReadStore.Item item, BookingAccess.Context current) {
        return item.synthetic() && !item.realBooking() && item.bookingId().equals(current.booking())
                && item.venueId().equals(current.venue()) && item.roomId().equals(current.room())
                && item.revision() == current.revision() && item.scheduledStart().equals(current.start())
                && item.scheduledEnd().equals(current.end());
    }

    private Assessment assessRoster(StaffBookingReadStore.Item item) {
        var capacity = rooms.inspect(item.venueId(), Set.of(item.roomId()), false, true)
                .flatMap(facts -> facts.rooms().stream().filter(room -> room.roomId().equals(item.roomId()))
                        .findFirst()).map(PackageConstraints.RoomFact::capacity);
        if (capacity.isEmpty()) return new Assessment(Status.UNAVAILABLE, 0);
        if (item.purchasedGuestCount() > capacity.get()) return new Assessment(Status.INELIGIBLE, 0);
        var revision = rosters.revision(item.bookingId());
        if (revision.isEmpty()) return new Assessment(Status.PENDING_VERIFICATION, 0);
        var guests = rosters.entries(item.bookingId()).stream()
                .map(entry -> UUID.fromString(entry.get("id"))).toList();
        var source = decisions.getIfAvailable();
        var scope = new SyntheticGuestAdmission.Scope(item.bookingId(), item.venueId(), item.roomId(),
                item.revision(), revision.get(), POLICY);
        return evaluate(item.purchasedGuestCount(), guests, scope, source == null ? Map.of() : source.currentDecisions(scope));
    }

    static Assessment evaluate(int permitted, List<UUID> guests, SyntheticGuestAdmission.Scope scope,
                               Map<UUID, SyntheticGuestAdmission.Decision> outcomes) {
        if (guests.size() > permitted || Set.copyOf(guests).size() != guests.size()) {
            return new Assessment(Status.INELIGIBLE, 0);
        }
        int checked = 0;
        boolean denied = false;
        for (UUID guest : guests) {
            var decision = outcomes.get(guest);
            if (decision == null || decision.revoked() || !scope.equals(decision.scope())) continue;
            var outcome = decision.outcome();
            if (outcome == SyntheticGuestAdmission.Outcome.ELIGIBLE) checked++;
            if (outcome == SyntheticGuestAdmission.Outcome.DENIED) denied = true;
        }
        if (denied) return new Assessment(Status.INELIGIBLE, checked);
        // The roster does not establish actual attendance. Require evidence for every purchased place.
        return new Assessment(permitted > 0 && checked == permitted
                ? Status.ELIGIBLE_FOR_STAFF_REVIEW : Status.PENDING_VERIFICATION, checked);
    }
}

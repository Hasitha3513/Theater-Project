package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SyntheticStaffCheckInService {
    private static final String HISTORY = "cinenest_booking.synthetic_staff_check_in";
    private static final String PERMISSION = "guest.check-in.synthetic.record";
    private final JdbcClient jdbc;
    private final GuestAdmissionService admission;
    private final GuestRosterStore rosters;
    private final StaffBookingReadStore staffBookings;
    private final ScopedAuthorization authorization;
    private final Environment environment;
    private final boolean enabled;

    SyntheticStaffCheckInService(JdbcClient jdbc, GuestAdmissionService admission, GuestRosterStore rosters,
            StaffBookingReadStore staffBookings, ScopedAuthorization authorization, Environment environment,
            @Value("${cinenest.booking.staff-check-in.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.admission = admission;
        this.rosters = rosters;
        this.staffBookings = staffBookings;
        this.authorization = authorization;
        this.environment = environment;
        this.enabled = enabled;
    }

    record Receipt(UUID actorId, UUID bookingId, UUID venueId, UUID roomId, long bookingRevision,
                   long rosterRevision, String policyVersion, String outcome, Instant recordedAt,
                   boolean physicalEntryAuthorized) {}
    record View(boolean available, Long rosterRevision, Receipt decision) {}
    private record Recorded(Receipt receipt, String digest) {}

    @Transactional
    public View read(UUID actor, UUID venue, UUID booking) {
        requireAuthority(actor, venue);
        var summary = admission.read(actor, venue, booking);
        Long roster = rosterRevision(summary);
        var decision = lookup(jdbc.sql("SELECT * FROM " + HISTORY + " " + """
                WHERE venue_id=:venue AND booking_id=:booking AND booking_revision=:revision
                  AND roster_revision=:roster AND policy_version=:policy
                """).param("venue", venue).param("booking", booking).param("revision", summary.bookingRevision())
                .param("roster", roster, java.sql.Types.BIGINT).param("policy", GuestAdmissionService.POLICY));
        return new View(decision.isEmpty() && roster != null && canRecord(summary), roster,
                decision.map(Recorded::receipt).orElse(null));
    }

    @Transactional
    public Receipt record(UUID actor, UUID venue, UUID booking, Map<String,Object> body) {
        requireAuthority(actor, venue);
        if (!body.keySet().equals(Set.of("commandKey", "roomId", "bookingRevision", "rosterRevision"))) {
            throw InventoryRules.invalid();
        }
        UUID key = InventoryRules.id(InventoryRules.text(body, "commandKey"));
        UUID room = InventoryRules.id(InventoryRules.text(body, "roomId"));
        long revision = number(body.get("bookingRevision"));
        long roster = number(body.get("rosterRevision"));
        String digest = digest(venue + ":" + booking + ":" + room + ":" + revision + ":" + roster);
        var existing = byKey(actor, key);
        if (existing.isPresent()) return replay(existing.get(), digest);

        var summary = admission.read(actor, venue, booking);
        if (!room.equals(summary.roomId()) || revision != summary.bookingRevision()
                || !Long.valueOf(roster).equals(rosterRevision(summary)) || !canRecord(summary)) {
            throw new InventoryFailure(409, "Current booking, guest checks or session window prevent recording");
        }
        requireAuthority(actor, venue);
        var inserted = lookup(jdbc.sql("""
                INSERT INTO cinenest_booking.synthetic_staff_check_in
                    (actor_id,command_key,payload_sha256,booking_id,booking_revision,roster_revision,venue_id,
                     room_id,policy_version,outcome)
                VALUES (:actor,:key,:digest,:booking,:revision,:roster,:venue,:room,:policy,'SYNTHETIC_CHECK_IN_RECORDED')
                ON CONFLICT DO NOTHING RETURNING *
                """).param("actor", actor).param("key", key).param("digest", digest).param("booking", booking)
                .param("revision", revision).param("roster", roster).param("venue", venue).param("room", room)
                .param("policy", GuestAdmissionService.POLICY));
        if (inserted.isPresent()) return inserted.get().receipt();
        return byKey(actor, key).map(value -> replay(value, digest))
                .orElseThrow(() -> new InventoryFailure(409, "A decision already exists for this scope"));
    }

    private boolean canRecord(GuestAdmissionService.Summary summary) {
        return summary.status() == GuestAdmissionService.Status.ELIGIBLE_FOR_STAFF_REVIEW
                && within(staffBookings.now(), summary.scheduledStart(), summary.scheduledEnd());
    }

    static boolean within(Instant now, Instant start, Instant end) {
        return !now.isBefore(start) && now.isBefore(end);
    }

    private Long rosterRevision(GuestAdmissionService.Summary summary) {
        // Positive eligibility was evaluated while holding the Booking lock.
        return summary.status() == GuestAdmissionService.Status.ELIGIBLE_FOR_STAFF_REVIEW
                ? rosters.revision(summary.bookingId()).orElse(null) : null;
    }

    private void requireAuthority(UUID actor, UUID venue) {
        if (!enabled || !Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation"))) {
            throw new InventoryFailure(503, "Synthetic staff check-in unavailable");
        }
        if (!authorization.permits(actor, venue, PERMISSION, true) || !authorization.member(actor, venue, true)
                || !authorization.permits(actor, venue, "booking.synthetic.read", true)
                || !authorization.permits(actor, venue, "guest.admission.synthetic.read", true)) {
            throw new InventoryFailure(403, "Synthetic check-in denied");
        }
    }

    private Optional<Recorded> byKey(UUID actor, UUID key) {
        return lookup(jdbc.sql("SELECT * FROM " + HISTORY + " WHERE actor_id=:actor AND command_key=:key")
                .param("actor", actor).param("key", key));
    }

    private static Optional<Recorded> lookup(JdbcClient.StatementSpec statement) {
        return statement.query((rs, n) -> new Recorded(new Receipt(rs.getObject("actor_id", UUID.class),
                rs.getObject("booking_id", UUID.class), rs.getObject("venue_id", UUID.class),
                rs.getObject("room_id", UUID.class), rs.getLong("booking_revision"),
                rs.getLong("roster_revision"), rs.getString("policy_version"), rs.getString("outcome"),
                rs.getTimestamp("recorded_at").toInstant(), false), rs.getString("payload_sha256"))).optional();
    }

    private static Receipt replay(Recorded recorded, String digest) {
        if (!recorded.digest().equals(digest)) {
            throw new InventoryFailure(409, "Command key belongs to a different check-in request");
        }
        return recorded.receipt();
    }

    private static long number(Object value) {
        if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 0) {
            return ((Number) value).longValue();
        }
        throw InventoryRules.invalid();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

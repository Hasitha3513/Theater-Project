package com.cinenest.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cinenest.booking.ExtensionReservationContext;
import com.cinenest.identity.ScopedAuthorization;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ExtensionFundingArchitectureTest {
    private final UUID owner = UUID.randomUUID(), booking = UUID.randomUUID(), reservation = UUID.randomUUID(),
            venue = UUID.randomUUID(), version = UUID.randomUUID(), key = UUID.randomUUID();
    private final Instant deadline = Instant.parse("2030-03-01T10:00:00Z");
    private final TestRepository repository = new TestRepository();
    private final TestTransaction tx = new TestTransaction();
    private ExtensionReservationContext.Observation currentObservation;
    private int observations;

    private final class TestRepository extends ExtensionFundingRepository {
        Optional<Request> stored = Optional.empty();
        Optional<Request> nextInsert = Optional.empty();
        Instant clock = deadline;
        int auditWrites;
        boolean failAudit;

        TestRepository() { super(null); }

        @Override Optional<Request> byKey(UUID actor, UUID commandKey) {
            return stored.filter(row -> row.owner().equals(actor) && row.key().equals(commandKey));
        }

        @Override Optional<Request> owned(UUID actor, UUID bookingId, UUID reservationId) {
            return stored.filter(row -> row.owner().equals(actor) && row.booking().equals(bookingId)
                    && row.reservation().equals(reservationId));
        }

        @Override Optional<Request> insert(UUID actor, UUID venueId, UUID bookingId, UUID reservationId,
                UUID commandKey, String digest, Terms terms, Instant expiresAt) {
            return nextInsert.filter(row -> row.owner().equals(actor) && row.venue().equals(venueId)
                    && row.booking().equals(bookingId) && row.reservation().equals(reservationId)
                    && row.key().equals(commandKey) && row.commandHash().equals(digest)
                    && row.terms().equals(terms) && row.expiresAt().equals(expiresAt));
        }

        @Override void audit(Request request) {
            if (failAudit) throw new IllegalStateException("injected audit failure");
            auditWrites++;
        }

        @Override Instant now() { return clock; }
    }

    private static final class TestTransaction implements PlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { commits++; }
        @Override public void rollback(TransactionStatus status) { rollbacks++; }
    }

    private ExtensionFundingService service(boolean enabled) {
        var env = new MockEnvironment();
        env.setActiveProfiles("synthetic-quotation");
        ScopedAuthorization identity = new ScopedAuthorization() {
            @Override public boolean eligible(UUID actor) { return owner.equals(actor); }
            @Override public boolean eligible(UUID actor, boolean lock) { return eligible(actor); }
            @Override public boolean permits(UUID actor, UUID venueId, String permission, boolean lock) { return false; }
            @Override public Set<UUID> venues(UUID actor, String permission) { return Set.of(); }
        };
        ExtensionReservationContext port = (actor, bookingId, reservationId) -> {
            observations++;
            assertThat(actor).isEqualTo(owner);
            assertThat(bookingId).isEqualTo(booking);
            assertThat(reservationId).isEqualTo(reservation);
            return currentObservation;
        };
        return new ExtensionFundingService(repository, port, identity, env, tx, enabled);
    }

    private ExtensionFundingService.Command command(UUID requestedVenue, String amount) {
        return new ExtensionFundingService.Command(key, requestedVenue, 1, 0, version,
                "SYNTHETIC_EXTENSION_RESERVATION_V1", amount, "LKR", true);
    }

    private ExtensionReservationContext.Observation observation(boolean effective) {
        return new ExtensionReservationContext.Observation(booking, reservation, venue, 1, 0, 0,
                ExtensionReservationContext.State.ACTIVE, "frozen", null, null, null, 1200, "LKR",
                version, "FROZEN_PURCHASED_TERMS", 30, "SYNTHETIC_EXTENSION_RESERVATION_V1",
                deadline.minusSeconds(600), deadline, deadline.minusSeconds(1), effective, !effective, List.of());
    }

    private ExtensionFundingRepository.Request row(ExtensionFundingService.Command cmd) {
        return new ExtensionFundingRepository.Request(UUID.randomUUID(), owner, venue, booking, reservation, key,
                cmd.hash(owner, booking, reservation), new ExtensionFundingRepository.Terms(1, 0, 0, version, "frozen",
                        "SYNTHETIC_EXTENSION_RESERVATION_V1", 1200L, "LKR"), deadline.minusSeconds(1), deadline);
    }

    @Test void ownerAndVenueDenialDoNotPersist() {
        var service = service(true);
        var otherOwner = UUID.randomUUID();
        assertThatThrownBy(() -> service.read(otherOwner, booking, reservation, key))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(403));
        currentObservation = observation(true);
        assertThatThrownBy(() -> service.create(owner, booking, reservation, command(UUID.randomUUID(), "1200")))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(404));
        assertThat(repository.auditWrites).isZero();
    }

    @Test void disabledStaleAndExpiredCreationFailClosed() {
        var command = command(venue, "1200");
        assertThatThrownBy(() -> service(false).create(owner, booking, reservation, command))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(503));
        var service = service(true);
        currentObservation = observation(false);
        assertThatThrownBy(() -> service.create(owner, booking, reservation, command))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(409));
        currentObservation = observation(true);
        assertThatThrownBy(() -> service.create(owner, booking, reservation, command(venue, "1201")))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(409));
        assertThat(repository.auditWrites).isZero();
    }

    @Test void identicalReplayAndRecoverySurviveExactExpiryWithNoNewMutation() {
        var service = service(false);
        var command = command(venue, "1200");
        var row = row(command);
        repository.stored = Optional.of(row);
        repository.clock = deadline;
        assertThat(service.create(owner, booking, reservation, command).state()).isEqualTo("EXPIRED");
        assertThat(service.read(owner, booking, reservation, key).requestId()).isEqualTo(row.id());
        assertThat(service.read(owner, booking, reservation, key).funded()).isFalse();
        assertThatThrownBy(() -> service.create(owner, booking, reservation, command(venue, "1201")))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(409));
        assertThatThrownBy(() -> service.read(owner, booking, reservation, UUID.randomUUID()))
                .isInstanceOfSatisfying(BankFailure.class, ex -> assertThat(ex.status).isEqualTo(404));
        assertThat(observations).isZero();
        assertThat(repository.auditWrites).isZero();
    }

    @Test void committedRequestWritesAuditBeforeReturningAndRollsBackOnAuditFailure() {
        var service = service(true);
        var command = command(venue, "1200");
        var row = row(command);
        currentObservation = observation(true);
        repository.nextInsert = Optional.of(row);
        repository.clock = deadline.minusSeconds(1);
        assertThat(service.create(owner, booking, reservation, command).funded()).isFalse();
        assertThat(repository.auditWrites).isEqualTo(1);
        assertThat(tx.commits).isEqualTo(1);
        repository.failAudit = true;
        assertThatThrownBy(() -> service.create(owner, booking, reservation, command))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tx.rollbacks).isEqualTo(1);
    }

    @Test void controllerDeniesAnonymousAndRejectsDuplicateJsonFields() throws Exception {
        var guardedService = service(false);
        var mvc = MockMvcBuilders.standaloneSetup(new ExtensionFundingController(guardedService)).build();
        var path = "/api/bookings/" + booking + "/extension-reservations/" + reservation + "/funding-request";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .principal(owner::toString).contentType("application/json")
                .content("{\"amountMinor\":\"1200\",\"amountMinor\":\"1300\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertThat(tx.commits + tx.rollbacks).isZero();
    }

    @Test void missingCsrfIsRejectedBeforeTheControllerCanCreate() throws Exception {
        var guardedService = service(false);
        var mvc = MockMvcBuilders.standaloneSetup(new ExtensionFundingController(guardedService))
                .addFilters(new CsrfFilter(new HttpSessionCsrfTokenRepository())).build();
        var path = "/api/bookings/" + booking + "/extension-reservations/" + reservation + "/funding-request";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .principal(owner::toString).contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertThat(tx.commits + tx.rollbacks).isZero();
    }

    @Test void onlyPaymentsOwnsNewPersistenceAndIdentityDoesNotExemptTheRouteFromCsrf() throws Exception {
        var migration = Files.readString(Path.of("src/main/resources/db/migration/V43__synthetic_extension_funding_requests.sql"));
        var repositorySource = Files.readString(Path.of("src/main/java/com/cinenest/payments/internal/ExtensionFundingRepository.java"));
        var security = Files.readString(Path.of("src/main/java/com/cinenest/identity/internal/IdentitySecurity.java"));
        assertThat(migration).contains("DEFERRABLE INITIALLY DEFERRED", "clock_timestamp() >= NEW.expires_at",
                "UNIQUE (owner_id, command_key)", "BEFORE UPDATE OR DELETE OR TRUNCATE", "FOREIGN KEY")
                .doesNotContain("cinenest_booking.", "incoming_payment_credit", "payment_allocation");
        assertThat(repositorySource).contains("clock_timestamp() < :deadline", "ON CONFLICT DO NOTHING")
                .doesNotContain("cinenest_booking.", "cinenest_identity.", "payment_allocation");
        assertThat(security).contains(".requestMatchers(org.springframework.http.HttpMethod.POST,\n"
                + "                        \"/api/bookings/*/extension-reservations/*/funding-request\").authenticated()")
                .doesNotContain(".csrf(csrf -> csrf.disable()", "ignoringRequestMatchers(\"/api/bookings");
    }
}

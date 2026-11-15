package com.cinenest.payments.internal;

import com.cinenest.booking.ExtensionReservationContext;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ExtensionFundingService {
    record Command(UUID commandKey, UUID venueId, long purchasedRevision, long reservationRevision,
                   UUID packageVersionId, String termsVersion, String amountMinor, String currency,
                   boolean acknowledged) {
        void validate() {
            long amount = BankService.number(amountMinor);
            if (!acknowledged || commandKey == null || venueId == null || packageVersionId == null
                    || purchasedRevision < 1 || reservationRevision < 0
                    || termsVersion == null || termsVersion.isBlank() || termsVersion.length() > 128
                    || amount < 1 || !Long.toString(amount).equals(amountMinor) || !"LKR".equals(currency)) {
                throw new BankFailure(400, "Invalid extension funding request");
            }
        }

        String hash(UUID owner, UUID booking, UUID reservation) {
            return BankPolicy.commandHash("EXTENSION_FUNDING:" + owner + ":" + booking + ":" + reservation,
                    Map.of("commandKey", commandKey.toString(), "venueId", venueId.toString(),
                           "purchasedRevision", purchasedRevision, "reservationRevision", reservationRevision,
                           "packageVersionId", packageVersionId.toString(), "termsVersion", termsVersion,
                           "amountMinor", amountMinor, "currency", currency, "acknowledged", acknowledged));
        }

        boolean matches(ExtensionFundingRepository.Terms terms) {
            return purchasedRevision == terms.purchasedRevision()
                    && reservationRevision == terms.reservationRevision()
                    && packageVersionId.equals(terms.packageVersionId())
                    && termsVersion.equals(terms.termsVersion())
                    && amountMinor.equals(Long.toString(terms.amountMinor()))
                    && currency.equals(terms.currency());
        }
    }

    record View(UUID requestId, UUID bookingId, UUID reservationId, UUID venueId, UUID commandKey,
                String amountMinor, String currency, String termsVersion, UUID packageVersionId,
                long purchasedRevision, long reservationRevision, Instant expiresAt, Instant observedAt,
                String state, boolean synthetic, boolean funded) {}

    private final ExtensionFundingRepository requests;
    private final ExtensionReservationContext reservations;
    private final ScopedAuthorization identity;
    private final Environment environment;
    private final TransactionTemplate transaction;
    private final boolean createEnabled;

    ExtensionFundingService(ExtensionFundingRepository requests, ExtensionReservationContext reservations,
                            ScopedAuthorization identity, Environment environment,
                            PlatformTransactionManager manager,
                            @Value("${cinenest.payments.extension-funding.enabled:false}") boolean createEnabled) {
        this.requests = requests;
        this.reservations = reservations;
        this.identity = identity;
        this.environment = environment;
        this.createEnabled = createEnabled;
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(10);
    }

    View create(UUID owner, UUID booking, UUID reservation, Command command) {
        synthetic();
        command.validate();
        return transaction.execute(status -> {
            requireEligible(owner);
            var prior = requests.byKey(owner, command.commandKey());
            if (prior.isPresent()) {
                return replay(prior.orElseThrow(), booking, reservation, command);
            }
            if (!createEnabled) throw new BankFailure(503, "Synthetic extension funding requests unavailable");
            var observation = reservations.observeOwned(owner, booking, reservation);
            if (!booking.equals(observation.bookingId()) || !reservation.equals(observation.reservationId())
                    || !command.venueId().equals(observation.venueId())) {
                throw unavailable();
            }
            var captured = new ExtensionFundingRepository.Terms(
                    observation.purchasedRevision(), observation.lifecycleVersion(), observation.proposalRevision(),
                    observation.packageVersionId(), observation.configuration(), observation.termsVersion(),
                    observation.additionalMinor(), observation.currency());
            var committedWhileWaiting = requests.byKey(owner, command.commandKey());
            if (committedWhileWaiting.isPresent()) {
                return replay(committedWhileWaiting.orElseThrow(), booking, reservation, command);
            }
            if (!observation.effective() || !command.matches(captured)) throw conflict();
            var inserted = requests.insert(owner, observation.venueId(), booking, reservation,
                    command.commandKey(), command.hash(owner, booking, reservation), captured, observation.expiresAt());
            if (inserted.isEmpty()) {
                return replay(requests.byKey(owner, command.commandKey()).orElseThrow(ExtensionFundingService::conflict),
                        booking, reservation, command);
            }
            var row = inserted.orElseThrow();
            requests.audit(row);
            return project(row, observation, requests.now());
        });
    }

    View read(UUID owner, UUID booking, UUID reservation, UUID key) {
        synthetic();
        return transaction.execute(status -> {
            requireEligible(owner);
            var row = requests.owned(owner, booking, reservation).orElseThrow(ExtensionFundingService::unavailable);
            if (key != null && !key.equals(row.key())) throw unavailable();
            return current(row);
        });
    }

    private View replay(ExtensionFundingRepository.Request row, UUID booking, UUID reservation, Command command) {
        if (!row.booking().equals(booking) || !row.reservation().equals(reservation)
                || !row.venue().equals(command.venueId())
                || !row.commandHash().equals(command.hash(row.owner(), booking, reservation))) throw conflict();
        return current(row);
    }

    private View current(ExtensionFundingRepository.Request row) {
        Instant now = requests.now();
        if (!now.isBefore(row.expiresAt())) return project(row, null, now);
        try {
            var observation = reservations.observeOwned(row.owner(), row.booking(), row.reservation());
            if (!row.venue().equals(observation.venueId())) throw unavailable();
            return project(row, observation, requests.now());
        } catch (ExtensionReservationContext.Rejected rejected) {
            if (rejected.status() != 503) throw rejected;
            return project(row, null, now);
        }
    }

    private View project(ExtensionFundingRepository.Request row, ExtensionReservationContext.Observation observation,
                         Instant now) {
        String state = !now.isBefore(row.expiresAt()) ? "EXPIRED"
                : observation == null ? "UNAVAILABLE"
                : observation.effective() && observation.proposalRevision() == row.terms().reservationRevision()
                    ? "PENDING" : "INACTIVE";
        var terms = row.terms();
        return new View(row.id(), row.booking(), row.reservation(), row.venue(), row.key(),
                Long.toString(terms.amountMinor()), terms.currency(), terms.termsVersion(), terms.packageVersionId(),
                terms.purchasedRevision(), terms.reservationRevision(), row.expiresAt(), now, state, true, false);
    }

    private void synthetic() {
        if (!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation"))) {
            throw new BankFailure(503, "Synthetic extension funding requests unavailable");
        }
    }

    private void requireEligible(UUID owner) {
        if (owner == null) throw new BankFailure(401, "Sign-in required");
        if (!identity.eligible(owner)) throw new BankFailure(403, "Current eligibility required");
    }

    private static BankFailure unavailable() {
        return new BankFailure(404, "Extension funding request unavailable");
    }

    private static BankFailure conflict() {
        return new BankFailure(409, "Extension request changed or is no longer eligible");
    }
}

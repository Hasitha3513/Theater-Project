package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("local-synthetic")
class JdbcCommercialPolicyApprovalRepository {
    private final JdbcClient jdbc;

    JdbcCommercialPolicyApprovalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Object> append(CommercialPolicyApproval approval, MatchedGrant grant,
                               UUID signer, String envelopeHash, Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Trusted approval intake requires an active transaction");
        }
        boolean revoking = "REVOKE".equals(approval.outcome());
        boolean signerIsReviewer = approval.reviewer().equals(signer);
        if (signer == null || signer.equals(grant.account()) || approval.reviewer().equals(grant.account())
                || (revoking ? signerIsReviewer : !signerIsReviewer)
                || !approval.venue().equals(grant.venue())
                || !grant.permission().equals("commercial.policy.review." + approval.domain().toLowerCase(Locale.ROOT))) {
            throw new CommercialPolicyDraft.Failure(403, "Trusted approval scope denied");
        }
        if (approval.issuedAt().isAfter(now) || !now.isBefore(approval.expiresAt())) {
            throw new CommercialPolicyDraft.Failure(403, "Trusted approval validity denied");
        }

        if (!"read committed".equals(jdbc.sql("SHOW transaction_isolation").query(String.class).single())) {
            throw new CommercialPolicyDraft.Failure(409, "Trusted approval intake requires read committed isolation");
        }
        // Order nonce before appointment for both new writes and existing receipts.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:nonce, 3801))")
            .param("nonce", approval.nonce().toString()).query((row, index) -> true).single();
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:appointment, 3802))")
            .param("appointment", approval.appointmentId().toString()).query((row, index) -> true).single();

        var receipt = jdbc.sql("""
            SELECT receipt.event_id, receipt.envelope_hash, event.actor_id,
                   event.expires_at > clock_timestamp() AS active,
                   EXISTS (
                       SELECT 1 FROM cinenest_pricing.commercial_policy_approval_event latest
                       WHERE latest.appointment_id = event.appointment_id AND latest.event_id <> event.event_id
                         AND (latest.outcome = 'REVOKE' OR latest.revocation_version > event.revocation_version)
                   ) AS superseded
            FROM cinenest_pricing.commercial_policy_approval_receipt receipt
            JOIN cinenest_pricing.commercial_policy_approval_event event USING (event_id)
            WHERE receipt.nonce = :nonce
            """).param("nonce", approval.nonce()).query((row, index) -> {
                if (!grant.account().equals(row.getObject("actor_id", UUID.class))) {
                    throw new CommercialPolicyDraft.Failure(403, "Trusted approval receipt scope denied");
                }
                if (!envelopeHash.equals(row.getString("envelope_hash"))) {
                    throw new CommercialPolicyDraft.Failure(409, "Trusted approval nonce already used");
                }
                if (!row.getBoolean("active") || row.getBoolean("superseded")) {
                    throw new CommercialPolicyDraft.Failure(403, "Trusted approval validity denied");
                }
                return row.getObject("event_id", UUID.class);
            }).optional();
        if (receipt.isPresent()) {
            jdbc.sql("SELECT cinenest_pricing.require_binding_authority(:actor,:venue,:permission,:grant,:revision)")
                .param("actor", grant.account()).param("venue", grant.venue()).param("permission", grant.permission())
                .param("grant", grant.grantId()).param("revision", grant.grantRevision())
                .query((row, index) -> true).single();
            return Map.of("eventId", receipt.get(), "replayed", true);
        }

        var event = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_approval_event
                (event_id, nonce, envelope_hash, appointment_id, actor_id, signer_id, reviewer_id, venue_id, room_id,
                 version_id, policy_id, revision, content_hash, domain, outcome, key_id, revocation_version,
                 issued_at, expires_at, grant_id, grant_revision, account_revision, credential_epoch)
            VALUES (:event, :nonce, :envelopeHash, :appointment, :actor, :signer, :reviewer, :venue, :room, :version,
                    :policy, :revision, :contentHash, :domain, :outcome, :key, :revocationVersion,
                    :issued, :expires, :grant, :grantRevision, :accountRevision, :credentialEpoch)
            """).param("event", event).param("nonce", approval.nonce()).param("envelopeHash", envelopeHash)
            .param("appointment", approval.appointmentId()).param("actor", grant.account()).param("signer", signer)
            .param("reviewer", approval.reviewer())
            .param("venue", approval.venue()).param("room", approval.room()).param("version", approval.packageVersion())
            .param("policy", approval.policy()).param("revision", approval.revision()).param("contentHash", approval.contentHash())
            .param("domain", approval.domain()).param("outcome", approval.outcome()).param("key", approval.keyId())
            .param("revocationVersion", approval.revocationVersion())
            .param("issued", OffsetDateTime.ofInstant(approval.issuedAt(), ZoneOffset.UTC))
            .param("expires", OffsetDateTime.ofInstant(approval.expiresAt(), ZoneOffset.UTC))
            .param("grant", grant.grantId()).param("grantRevision", grant.grantRevision())
            .param("accountRevision", grant.accountRevision()).param("credentialEpoch", grant.credentialEpoch()).update();

        jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_approval_receipt (event_id, nonce, envelope_hash)
            SELECT event_id, nonce, envelope_hash FROM cinenest_pricing.commercial_policy_approval_event
            WHERE event_id = :event
            """).param("event", event).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_approval_audit
                (event_id, actor_id, signer_id, reviewer_id, venue_id, recorded_at)
            SELECT event_id, actor_id, signer_id, reviewer_id, venue_id, recorded_at FROM cinenest_pricing.commercial_policy_approval_event
            WHERE event_id = :event
            """).param("event", event).update();
        return Map.of("eventId", event, "replayed", false);
    }
}

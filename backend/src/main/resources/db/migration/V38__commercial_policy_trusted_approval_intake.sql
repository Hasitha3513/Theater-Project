-- Synthetic intake evidence only. No appointment seeds, grants, issuance or binding activation.
CREATE TABLE cinenest_pricing.commercial_policy_approval_event (
    event_id UUID PRIMARY KEY,
    nonce UUID NOT NULL UNIQUE,
    envelope_hash TEXT NOT NULL CHECK (envelope_hash ~ '^[0-9a-f]{64}$'),
    appointment_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES cinenest_identity.account (account_id),
    signer_id UUID NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES cinenest_identity.account (account_id),
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL REFERENCES cinenest_rooms.room (room_id),
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    domain TEXT NOT NULL CHECK (domain IN ('FINANCE', 'LEGAL', 'OPERATIONS')),
    outcome TEXT NOT NULL CHECK (outcome IN ('APPROVE', 'REJECT', 'REVOKE')),
    key_id TEXT NOT NULL CHECK (key_id ~ '^[A-Za-z0-9_-]{1,80}$'),
    revocation_version BIGINT NOT NULL CHECK (revocation_version > 0),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    grant_id UUID NOT NULL,
    grant_revision BIGINT NOT NULL CHECK (grant_revision >= 0),
    account_revision BIGINT NOT NULL CHECK (account_revision >= 0),
    credential_epoch BIGINT NOT NULL CHECK (credential_epoch >= 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    provenance TEXT NOT NULL DEFAULT 'SYNTHETIC_SIGNED_ENVELOPE'
        CHECK (provenance = 'SYNTHETIC_SIGNED_ENVELOPE'),
    CHECK (actor_id <> signer_id AND actor_id <> reviewer_id),
    CHECK ((outcome = 'REVOKE' AND signer_id <> reviewer_id)
        OR (outcome IN ('APPROVE', 'REJECT') AND signer_id = reviewer_id)),
    CHECK (isfinite(issued_at) AND isfinite(expires_at) AND issued_at <= recorded_at AND recorded_at < expires_at),
    FOREIGN KEY (policy_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_draft (policy_id, venue_id),
    FOREIGN KEY (policy_id, revision, content_hash)
        REFERENCES cinenest_pricing.commercial_policy_revision (policy_id, revision, content_hash),
    FOREIGN KEY (version_id, room_id)
        REFERENCES cinenest_pricing.package_version_room (version_id, room_id),
    UNIQUE (event_id, nonce, envelope_hash),
    UNIQUE (event_id, actor_id, signer_id, reviewer_id, venue_id, recorded_at)
);

CREATE INDEX commercial_approval_appointment_idx
    ON cinenest_pricing.commercial_policy_approval_event (appointment_id, revocation_version);

CREATE TABLE cinenest_pricing.commercial_policy_approval_receipt (
    event_id UUID PRIMARY KEY,
    nonce UUID NOT NULL UNIQUE,
    envelope_hash TEXT NOT NULL,
    FOREIGN KEY (event_id, nonce, envelope_hash)
        REFERENCES cinenest_pricing.commercial_policy_approval_event (event_id, nonce, envelope_hash)
);

CREATE TABLE cinenest_pricing.commercial_policy_approval_audit (
    event_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    signer_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (event_id, actor_id, signer_id, reviewer_id, venue_id, recorded_at)
        REFERENCES cinenest_pricing.commercial_policy_approval_event (event_id, actor_id, signer_id, reviewer_id, venue_id, recorded_at)
);

ALTER TABLE cinenest_pricing.commercial_policy_approval_event
    ADD FOREIGN KEY (event_id) REFERENCES cinenest_pricing.commercial_policy_approval_receipt
        DEFERRABLE INITIALLY DEFERRED,
    ADD FOREIGN KEY (event_id) REFERENCES cinenest_pricing.commercial_policy_approval_audit
        DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION cinenest_pricing.guard_commercial_policy_approval()
RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SET search_path = pg_catalog AS $$
DECLARE
    latest_revocation BIGINT;
    previously_revoked BOOLEAN;
    scope_changed BOOLEAN;
BEGIN
    -- A snapshot predating a competing revocation cannot be used to admit an event.
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Approval intake requires read committed isolation' USING ERRCODE = '23514';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.appointment_id::TEXT, 3802));
    SELECT max(e.revocation_version), bool_or(e.outcome = 'REVOKE'),
           bool_or((e.reviewer_id, e.venue_id, e.room_id, e.version_id)
                <> (NEW.reviewer_id, NEW.venue_id, NEW.room_id, NEW.version_id))
    INTO latest_revocation, previously_revoked, scope_changed
    FROM cinenest_pricing.commercial_policy_approval_event e
    WHERE e.appointment_id = NEW.appointment_id;

    IF previously_revoked OR scope_changed OR NEW.revocation_version < latest_revocation
       OR (NEW.outcome = 'REVOKE' AND NEW.revocation_version <= latest_revocation) THEN
        RAISE EXCEPTION 'Approval appointment is revoked, stale or out of scope' USING ERRCODE = '23514';
    END IF;

    PERFORM cinenest_pricing.require_binding_authority(NEW.actor_id, NEW.venue_id,
        'commercial.policy.review.' || lower(NEW.domain), NEW.grant_id, NEW.grant_revision);

    PERFORM 1
    FROM cinenest_identity.account a
    WHERE a.account_id = NEW.actor_id AND a.revision = NEW.account_revision
      AND NEW.credential_epoch = coalesce((
          SELECT max(c.credential_revision)
          FROM cinenest_identity.manual_credential_version c WHERE c.account_id = a.account_id), 0);
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Current account revision and credential epoch required' USING ERRCODE = '42501';
    END IF;

    PERFORM 1 FROM cinenest_identity.account reviewer
    WHERE reviewer.account_id = NEW.reviewer_id AND reviewer.synthetic
      AND (NEW.outcome = 'REVOKE' OR reviewer.eligibility = 'ELIGIBLE')
    FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Synthetic reviewer identity required' USING ERRCODE = '42501';
    END IF;

    PERFORM 1
    FROM cinenest_pricing.commercial_policy_draft d
    JOIN cinenest_pricing.commercial_policy_revision r
      ON r.policy_id = d.policy_id AND r.revision = d.current_revision
    JOIN cinenest_pricing.package_version v ON v.version_id = NEW.version_id
    JOIN cinenest_pricing.package p ON p.package_id = v.package_id
    JOIN cinenest_rooms.room room ON room.room_id = NEW.room_id
    WHERE d.policy_id = NEW.policy_id AND d.venue_id = NEW.venue_id
      AND r.revision = NEW.revision AND r.content_hash = NEW.content_hash
      AND p.venue_id = NEW.venue_id AND room.venue_id = NEW.venue_id
      AND (d.package_id IS NULL OR d.package_id = p.package_id)
      AND r.submitted_for_review AND cinenest_pricing.commercial_policy_is_complete(r)
    FOR SHARE OF d, p, room;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Approval policy scope or current terms do not match' USING ERRCODE = '23514';
    END IF;

    IF NEW.outcome <> 'REVOKE' AND (
        EXISTS (
            SELECT 1 FROM cinenest_pricing.commercial_policy_draft d
            WHERE d.policy_id = NEW.policy_id AND d.created_by = NEW.reviewer_id
        ) OR EXISTS (
            SELECT 1 FROM cinenest_pricing.commercial_policy_revision r
            WHERE r.policy_id = NEW.policy_id AND r.revision <= NEW.revision AND r.authored_by = NEW.reviewer_id
        )
    ) THEN
        RAISE EXCEPTION 'Independent policy reviewer required' USING ERRCODE = '42501';
    END IF;

    NEW.recorded_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER commercial_policy_approval_scope BEFORE INSERT
    ON cinenest_pricing.commercial_policy_approval_event
    FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_commercial_policy_approval();

CREATE FUNCTION cinenest_pricing.commercial_policy_approval_immutable()
RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SET search_path = pg_catalog AS $$
BEGIN
    -- A coordinated empty reset is compatible with existing synthetic test cleanup.
    IF TG_OP = 'TRUNCATE'
       AND current_setting('transaction_isolation') = 'read committed'
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_approval_event)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_approval_receipt)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_approval_audit) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Commercial approval history is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER commercial_policy_approval_event_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.commercial_policy_approval_event
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.commercial_policy_approval_immutable();
CREATE TRIGGER commercial_policy_approval_receipt_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.commercial_policy_approval_receipt
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.commercial_policy_approval_immutable();
CREATE TRIGGER commercial_policy_approval_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.commercial_policy_approval_audit
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.commercial_policy_approval_immutable();

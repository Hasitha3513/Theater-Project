-- Fixture-only activation ledger. No operational trust or grant is seeded.
CREATE TABLE cinenest_pricing.fixture_policy_revocations (
    sequence BIGINT PRIMARY KEY CHECK (sequence > 0),
    observed_at TIMESTAMPTZ NOT NULL CHECK (isfinite(observed_at)),
    payload_hash TEXT NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    revoked UUID[] NOT NULL
);
CREATE TABLE cinenest_pricing.fixture_policy_activation (
    activation_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES cinenest_identity.account,
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL REFERENCES cinenest_rooms.room,
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ NOT NULL,
    approval_until TIMESTAMPTZ NOT NULL,
    revocation_sequence BIGINT NOT NULL REFERENCES cinenest_pricing.fixture_policy_revocations,
    appointments UUID[] NOT NULL CHECK (cardinality(appointments)=3),
    approval_evidence JSONB NOT NULL CHECK (jsonb_typeof(approval_evidence)='array' AND jsonb_array_length(approval_evidence)=3),
    grant_id UUID NOT NULL,
    grant_revision BIGINT NOT NULL CHECK (grant_revision >= 0),
    command_key UUID NOT NULL,
    command_hash TEXT NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (isfinite(effective_from) AND isfinite(effective_until) AND effective_from < effective_until),
    CHECK (isfinite(approval_until) AND recorded_at < approval_until),
    FOREIGN KEY (policy_id, venue_id) REFERENCES cinenest_pricing.commercial_policy_draft (policy_id, venue_id),
    FOREIGN KEY (policy_id, revision, content_hash) REFERENCES cinenest_pricing.commercial_policy_revision (policy_id, revision, content_hash),
    FOREIGN KEY (version_id, room_id) REFERENCES cinenest_pricing.package_version_room,
    UNIQUE (actor_id, venue_id, command_key),
    UNIQUE (activation_id, actor_id, venue_id),
    UNIQUE (activation_id, venue_id, room_id, version_id, policy_id, revision, content_hash),
    EXCLUDE USING gist (venue_id WITH =, room_id WITH =, version_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&)
);
CREATE TABLE cinenest_pricing.fixture_policy_activation_audit (
    activation_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (activation_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.fixture_policy_activation (activation_id, actor_id, venue_id)
);
ALTER TABLE cinenest_pricing.fixture_policy_activation
    ADD FOREIGN KEY (activation_id) REFERENCES cinenest_pricing.fixture_policy_activation_audit
    DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION cinenest_pricing.guard_fixture_policy_activation()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
    PERFORM cinenest_pricing.require_binding_authority(
        NEW.actor_id, NEW.venue_id, 'commercial.policy.edit', NEW.grant_id, NEW.grant_revision);
    PERFORM 1 FROM cinenest_pricing.commercial_policy_draft d
        JOIN cinenest_pricing.commercial_policy_revision r ON r.policy_id=d.policy_id AND r.revision=d.current_revision
        JOIN cinenest_pricing.package_version v ON v.version_id=NEW.version_id
        JOIN cinenest_pricing.package p ON p.package_id=v.package_id
        JOIN cinenest_rooms.room rm ON rm.room_id=NEW.room_id
        WHERE d.policy_id=NEW.policy_id AND d.venue_id=NEW.venue_id AND r.revision=NEW.revision
            AND r.content_hash=NEW.content_hash AND p.venue_id=NEW.venue_id AND rm.venue_id=NEW.venue_id
            AND (d.package_id IS NULL OR d.package_id=p.package_id)
            AND r.submitted_for_review AND cinenest_pricing.commercial_policy_is_complete(r)
            AND r.effective_from_proposal<=NEW.effective_from
            AND (r.effective_until_proposal IS NULL OR NEW.effective_until<=r.effective_until_proposal)
        FOR SHARE OF d,p,rm;
    IF NOT FOUND THEN RAISE EXCEPTION 'Invalid fixture activation scope or terms' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_fixture_policy_activation BEFORE INSERT ON cinenest_pricing.fixture_policy_activation
    FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_fixture_policy_activation();

CREATE FUNCTION cinenest_pricing.fixture_commercial_immutable()
RETURNS trigger LANGUAGE plpgsql VOLATILE SET search_path=pg_catalog AS $$
DECLARE populated BOOLEAN;
BEGIN
    IF TG_OP='TRUNCATE' AND current_setting('transaction_isolation')='read committed' THEN
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I.%I)', TG_TABLE_SCHEMA, TG_TABLE_NAME) INTO populated;
        IF NOT populated THEN RETURN NULL; END IF;
    END IF;
    RAISE EXCEPTION 'Fixture commercial history is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER immutable_fixture_activation BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_pricing.fixture_policy_activation
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_fixture_activation_audit BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_pricing.fixture_policy_activation_audit
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_fixture_revocations BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_pricing.fixture_policy_revocations
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();

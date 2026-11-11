-- Operational storage only. No accounts, grants, trust roots, or policies are seeded.
CREATE TABLE cinenest_identity.operational_credential (
    account_id UUID PRIMARY KEY REFERENCES cinenest_identity.account,
    issuer TEXT NOT NULL,
    subject TEXT NOT NULL,
    account_class TEXT NOT NULL CHECK (account_class IN ('STAFF', 'CUSTOMER')),
    epoch BIGINT NOT NULL CHECK (epoch >= 0),
    provenance_hash TEXT NOT NULL CHECK (provenance_hash ~ '^[0-9a-f]{64}$'),
    verified_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    UNIQUE (issuer, subject),
    CHECK (length(issuer) BETWEEN 1 AND 512 AND length(subject) BETWEEN 1 AND 512),
    CHECK (isfinite(verified_at) AND isfinite(expires_at) AND expires_at > verified_at)
);

CREATE FUNCTION cinenest_identity.guard_operational_credential_history()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Revoke the operational credential instead' USING ERRCODE = '23514';
    END IF;
    IF NEW.account_id <> OLD.account_id OR NEW.epoch <= OLD.epoch THEN
        RAISE EXCEPTION 'Operational credential changes require a higher epoch' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION cinenest_identity.guard_operational_credential_truncate()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF current_setting('transaction_isolation') = 'read committed'
        AND NOT EXISTS (SELECT 1 FROM cinenest_identity.operational_credential) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Operational credential history is protected' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER guard_operational_credential_truncate BEFORE TRUNCATE
    ON cinenest_identity.operational_credential FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_identity.guard_operational_credential_truncate();

CREATE TRIGGER guard_operational_credential_history BEFORE UPDATE OR DELETE
    ON cinenest_identity.operational_credential FOR EACH ROW
    EXECUTE FUNCTION cinenest_identity.guard_operational_credential_history();

CREATE TABLE cinenest_booking.operational_hold_provenance (
    hold_id UUID PRIMARY KEY REFERENCES cinenest_booking.reservation_hold,
    package_id UUID NOT NULL,
    version_id UUID NOT NULL,
    grant_id UUID NOT NULL,
    grant_revision BIGINT NOT NULL CHECK (grant_revision >= 0),
    credential_epoch BIGINT NOT NULL CHECK (credential_epoch >= 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE cinenest_pricing.operational_revocation_observation (
    namespace TEXT NOT NULL CHECK (namespace ~ '^[a-zA-Z0-9._-]{1,64}$'),
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    digest TEXT NOT NULL CHECK (digest ~ '^[0-9a-f]{64}$'),
    observed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (namespace, sequence)
);

CREATE TABLE cinenest_pricing.operational_policy_activation (
    activation_id UUID PRIMARY KEY,
    qualification_only BOOLEAN NOT NULL,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    policy_revision BIGINT NOT NULL CHECK (policy_revision >= 0),
    policy_hash CHAR(64) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ NOT NULL,
    authority_until TIMESTAMPTZ NOT NULL,
    trust_namespace TEXT NOT NULL,
    revocation_sequence BIGINT NOT NULL,
    grant_id UUID NOT NULL,
    grant_revision BIGINT NOT NULL CHECK (grant_revision >= 0),
    command_key UUID NOT NULL,
    command_hash TEXT NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (isfinite(effective_from) AND isfinite(effective_until) AND effective_from < effective_until),
    CHECK (isfinite(authority_until) AND authority_until > recorded_at),
    FOREIGN KEY (trust_namespace, revocation_sequence)
        REFERENCES cinenest_pricing.operational_revocation_observation (namespace, sequence),
    FOREIGN KEY (policy_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_draft (policy_id, venue_id),
    FOREIGN KEY (policy_id, policy_revision, policy_hash)
        REFERENCES cinenest_pricing.commercial_policy_revision (policy_id, revision, content_hash),
    FOREIGN KEY (version_id, room_id) REFERENCES cinenest_pricing.package_version_room,
    UNIQUE (actor_id, venue_id, command_key),
    UNIQUE (activation_id, actor_id, venue_id),
    UNIQUE (activation_id, venue_id, room_id, version_id, policy_id, policy_revision, policy_hash),
    EXCLUDE USING gist (venue_id WITH =, room_id WITH =, version_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&)
);

CREATE TABLE cinenest_pricing.operational_quote (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    hold_id UUID NOT NULL UNIQUE,
    hold_revision BIGINT NOT NULL CHECK (hold_revision >= 0),
    activation_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    package_id UUID NOT NULL,
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    policy_revision BIGINT NOT NULL,
    policy_hash CHAR(64) NOT NULL,
    trust_namespace TEXT NOT NULL,
    revocation_sequence BIGINT NOT NULL,
    command_key UUID NOT NULL,
    command_hash TEXT NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    content_hash TEXT NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object' AND (snapshot ->> 'currency' = 'LKR') IS TRUE),
    total_minor BIGINT NOT NULL CHECK (total_minor > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (isfinite(expires_at) AND expires_at > recorded_at),
    FOREIGN KEY (trust_namespace, revocation_sequence)
        REFERENCES cinenest_pricing.operational_revocation_observation (namespace, sequence),
    FOREIGN KEY (version_id, package_id) REFERENCES cinenest_pricing.package_version (version_id, package_id),
    FOREIGN KEY (activation_id, venue_id, room_id, version_id, policy_id, policy_revision, policy_hash)
        REFERENCES cinenest_pricing.operational_policy_activation
            (activation_id, venue_id, room_id, version_id, policy_id, policy_revision, policy_hash),
    UNIQUE (actor_id, command_key),
    UNIQUE (quote_id, actor_id, venue_id)
);

CREATE TABLE cinenest_pricing.operational_policy_activation_audit (
    activation_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (activation_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.operational_policy_activation (activation_id, actor_id, venue_id)
);

CREATE TABLE cinenest_pricing.operational_quote_audit (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (quote_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.operational_quote (quote_id, actor_id, venue_id)
);

ALTER TABLE cinenest_pricing.operational_policy_activation
    ADD FOREIGN KEY (activation_id) REFERENCES cinenest_pricing.operational_policy_activation_audit
    DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE cinenest_pricing.operational_quote
    ADD FOREIGN KEY (quote_id) REFERENCES cinenest_pricing.operational_quote_audit
    DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION cinenest_booking.guard_operational_hold_provenance()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' AND current_setting('transaction_isolation') = 'read committed'
        AND NOT EXISTS (SELECT 1 FROM cinenest_booking.operational_hold_provenance) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Operational hold provenance is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER immutable_operational_hold_provenance BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_booking.operational_hold_provenance FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_booking.guard_operational_hold_provenance();
CREATE TRIGGER immutable_operational_revocation BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.operational_revocation_observation FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_operational_activation BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.operational_policy_activation FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_operational_quote BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.operational_quote FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_operational_activation_audit BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.operational_policy_activation_audit FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_operational_quote_audit BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.operational_quote_audit FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();

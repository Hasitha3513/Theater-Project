-- Qualification-only snapshots. No link to fixture activation, payment or historical quotations.
CREATE TABLE cinenest_pricing.customer_commercial_quote (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES cinenest_identity.account,
    hold_id UUID NOT NULL UNIQUE,
    hold_revision BIGINT NOT NULL CHECK (hold_revision >= 0),
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    package_id UUID NOT NULL,
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    policy_revision BIGINT NOT NULL CHECK (policy_revision >= 0),
    policy_hash TEXT NOT NULL CHECK (policy_hash ~ '^[0-9a-f]{64}$'),
    command_key UUID NOT NULL,
    command_hash TEXT NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    snapshot JSONB NOT NULL CHECK (
        jsonb_typeof(snapshot) = 'object'
        AND (snapshot ->> 'classification' = 'SYNTHETIC_QUALIFICATION_NOT_OFFER') IS TRUE
        AND (snapshot ->> 'currency' = 'LKR') IS TRUE
    ),
    content_hash TEXT NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    total_minor BIGINT NOT NULL CHECK (total_minor > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id, command_key),
    UNIQUE (quote_id, actor_id, venue_id),
    CHECK (isfinite(expires_at) AND expires_at > recorded_at)
);

CREATE TABLE cinenest_pricing.customer_commercial_quote_audit (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (quote_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.customer_commercial_quote (quote_id, actor_id, venue_id)
);

ALTER TABLE cinenest_pricing.customer_commercial_quote
    ADD FOREIGN KEY (quote_id) REFERENCES cinenest_pricing.customer_commercial_quote_audit (quote_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TRIGGER immutable_customer_commercial_quote BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.customer_commercial_quote FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_customer_commercial_quote_audit BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.customer_commercial_quote_audit FOR EACH STATEMENT
    EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();

-- Isolated internal fixture snapshots. V7 quote and booking histories are unchanged.
CREATE TABLE cinenest_pricing.fixture_commercial_quotation (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES cinenest_identity.account,
    hold_id UUID NOT NULL UNIQUE,
    hold_revision BIGINT NOT NULL CHECK (hold_revision >= 0),
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    version_id UUID NOT NULL,
    activation_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    policy_revision BIGINT NOT NULL,
    policy_hash CHAR(64) NOT NULL,
    snapshot JSONB NOT NULL,
    content_hash TEXT NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    total_minor BIGINT NOT NULL CHECK (total_minor > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    command_key UUID NOT NULL,
    command_hash TEXT NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    UNIQUE (actor_id, command_key),
    UNIQUE (quote_id, actor_id, venue_id),
    CHECK (isfinite(expires_at) AND expires_at > recorded_at),
    FOREIGN KEY (activation_id, venue_id, room_id, version_id, policy_id, policy_revision, policy_hash)
        REFERENCES cinenest_pricing.fixture_policy_activation
            (activation_id, venue_id, room_id, version_id, policy_id, revision, content_hash)
);
CREATE TABLE cinenest_pricing.fixture_commercial_quote_audit (
    quote_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (quote_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.fixture_commercial_quotation (quote_id, actor_id, venue_id)
);
ALTER TABLE cinenest_pricing.fixture_commercial_quotation
    ADD FOREIGN KEY (quote_id) REFERENCES cinenest_pricing.fixture_commercial_quote_audit
    DEFERRABLE INITIALLY DEFERRED;
CREATE TRIGGER immutable_fixture_quote BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_pricing.fixture_commercial_quotation
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();
CREATE TRIGGER immutable_fixture_quote_audit BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_pricing.fixture_commercial_quote_audit
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.fixture_commercial_immutable();

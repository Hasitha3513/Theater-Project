CREATE TABLE cinenest_payments.extension_funding_request (
    request_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    reservation_id UUID NOT NULL UNIQUE,
    command_key UUID NOT NULL,
    command_sha256 CHAR(64) NOT NULL CHECK (command_sha256 ~ '^[0-9a-f]{64}$'),
    purchased_revision BIGINT NOT NULL CHECK (purchased_revision > 0),
    lifecycle_version BIGINT NOT NULL CHECK (lifecycle_version >= 0),
    reservation_revision BIGINT NOT NULL CHECK (reservation_revision >= 0),
    package_version_id UUID NOT NULL,
    configuration TEXT NOT NULL CHECK (configuration <> ''),
    terms_version TEXT NOT NULL CHECK (terms_version <> ''),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'LKR'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (owner_id, command_key),
    UNIQUE (request_id, owner_id, venue_id),
    CHECK (created_at < expires_at)
);
CREATE TABLE cinenest_payments.extension_funding_audit (
    request_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (request_id, actor_id, venue_id)
        REFERENCES cinenest_payments.extension_funding_request (request_id, owner_id, venue_id)
);

CREATE FUNCTION cinenest_payments.reject_extension_funding_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Extension funding history is immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER extension_funding_request_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_payments.extension_funding_request
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.reject_extension_funding_history_mutation();
CREATE TRIGGER extension_funding_audit_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_payments.extension_funding_audit
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.reject_extension_funding_history_mutation();

CREATE FUNCTION cinenest_payments.check_extension_funding_request()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF clock_timestamp() >= NEW.expires_at OR NOT EXISTS (
        SELECT 1 FROM cinenest_payments.extension_funding_audit WHERE request_id = NEW.request_id
    ) THEN
        RAISE EXCEPTION 'Extension funding request deadline or audit constraint' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER extension_funding_request_complete
    AFTER INSERT ON cinenest_payments.extension_funding_request DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cinenest_payments.check_extension_funding_request();

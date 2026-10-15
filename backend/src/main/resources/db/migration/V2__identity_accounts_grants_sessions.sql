CREATE SCHEMA cinenest_identity;

CREATE TABLE cinenest_identity.account (
    account_id UUID PRIMARY KEY,
    synthetic BOOLEAN NOT NULL,
    eligibility VARCHAR(16) NOT NULL CHECK (eligibility IN ('ELIGIBLE', 'SUSPENDED', 'INELIGIBLE')),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
CREATE TABLE cinenest_identity.external_identity (
    issuer TEXT NOT NULL CHECK (length(issuer) > 0),
    subject TEXT NOT NULL CHECK (length(subject) > 0),
    account_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
    PRIMARY KEY (issuer, subject)
);
-- Identity-owned trusted scope assignments; not a replacement venue catalog or tenant hierarchy.
CREATE TABLE cinenest_identity.account_scope (
    account_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
    venue_id UUID NOT NULL,
    PRIMARY KEY (account_id, venue_id)
);
CREATE TABLE cinenest_identity.access_grant (
    grant_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
    permission TEXT NOT NULL CHECK (length(permission) > 0),
    scope_kind VARCHAR(8) NOT NULL CHECK (scope_kind IN ('SELF', 'VENUE')),
    venue_id UUID,
    state VARCHAR(8) NOT NULL CHECK (state IN ('ACTIVE', 'REVOKED')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CHECK (valid_until > valid_from),
    CHECK ((scope_kind = 'SELF' AND venue_id IS NULL) OR (scope_kind = 'VENUE' AND venue_id IS NOT NULL)),
    FOREIGN KEY (account_id, venue_id) REFERENCES cinenest_identity.account_scope(account_id, venue_id) ON DELETE CASCADE
);
CREATE INDEX access_grant_principal_idx ON cinenest_identity.access_grant(account_id);
CREATE TABLE cinenest_identity.security_audit (
    event_id UUID PRIMARY KEY,
    actor_id UUID,
    target_id UUID,
    event_kind VARCHAR(24) NOT NULL CHECK (event_kind IN ('AUTHENTICATION', 'ACCOUNT_READ', 'LOGOUT', 'HTTP_DENIAL')),
    outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('ALLOW', 'DENY')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Spring Session 4.1.1 PostgreSQL schema, qualified with the module-owned table name.
CREATE TABLE cinenest_identity.http_session (
    primary_id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL UNIQUE,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100)
);
CREATE INDEX http_session_expiry_idx ON cinenest_identity.http_session(expiry_time);
CREATE INDEX http_session_principal_idx ON cinenest_identity.http_session(principal_name);
CREATE TABLE cinenest_identity.http_session_attributes (
    session_primary_id CHAR(36) NOT NULL REFERENCES cinenest_identity.http_session(primary_id) ON DELETE CASCADE,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

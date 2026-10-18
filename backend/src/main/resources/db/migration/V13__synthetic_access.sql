-- Access owns all five records. No hardware/production credentials or foreign table ownership.
CREATE SCHEMA cinenest_access;
CREATE TABLE cinenest_access.access_credential (
 credential_id UUID PRIMARY KEY, booking_id UUID NOT NULL, booking_revision BIGINT NOT NULL CHECK(booking_revision>0),
 owner_id UUID NOT NULL,venue_id UUID NOT NULL,room_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL,ends_at TIMESTAMPTZ NOT NULL,timezone TEXT NOT NULL,
 version BIGINT NOT NULL CHECK(version>0),revision BIGINT NOT NULL CHECK(revision>=0),
 state TEXT NOT NULL CHECK(state IN('SCHEDULED','ACTIVE','CONSUMED','EXPIRED','REVOKED','SUPERSEDED')),
 token_hash BYTEA NOT NULL CHECK(octet_length(token_hash)=32),protected_secret BYTEA,key_version TEXT NOT NULL,
 retain_until TIMESTAMPTZ NOT NULL,disclosed_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(ends_at>starts_at),UNIQUE(booking_id,version)
);
CREATE UNIQUE INDEX one_active_access_version ON cinenest_access.access_credential(booking_id) WHERE state IN('SCHEDULED','ACTIVE');
CREATE TABLE cinenest_access.access_work (
 operation_id UUID PRIMARY KEY,credential_id UUID NOT NULL UNIQUE REFERENCES cinenest_access.access_credential,
 credential_version BIGINT NOT NULL,state TEXT NOT NULL CHECK(state IN('PENDING_SIMULATION','SIMULATOR_INSTALLED','FAILED','UNKNOWN','RECONCILIATION_REQUIRED','REVOKED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),evidence_operation UUID,evidence_version BIGINT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((state='SIMULATOR_INSTALLED')=(evidence_operation IS NOT NULL AND evidence_version IS NOT NULL))
);
CREATE TABLE cinenest_access.access_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,booking_id UUID NOT NULL,credential_id UUID NOT NULL REFERENCES cinenest_access.access_credential,
 kind TEXT NOT NULL,payload_hash CHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_access.access_attempt (
 attempt_id UUID PRIMARY KEY,credential_id UUID NOT NULL REFERENCES cinenest_access.access_credential,
 actor_id UUID NOT NULL,outcome TEXT NOT NULL CHECK(outcome IN('SIMULATED_ENTRY','DENIED')),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_access.access_audit (
 event_id UUID PRIMARY KEY,actor_id UUID,credential_id UUID,booking_id UUID,venue_id UUID,
 action TEXT NOT NULL,outcome TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX access_actor_rate ON cinenest_access.access_audit(actor_id,created_at);
CREATE FUNCTION cinenest_access.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Immutable access evidence'; END $$;
CREATE TRIGGER immutable_access_audit BEFORE UPDATE OR DELETE ON cinenest_access.access_audit FOR EACH ROW EXECUTE FUNCTION cinenest_access.immutable_history();
CREATE TRIGGER immutable_access_attempt BEFORE UPDATE OR DELETE ON cinenest_access.access_attempt FOR EACH ROW EXECUTE FUNCTION cinenest_access.immutable_history();
CREATE TRIGGER immutable_access_command BEFORE UPDATE OR DELETE ON cinenest_access.access_command FOR EACH ROW EXECUTE FUNCTION cinenest_access.immutable_history();

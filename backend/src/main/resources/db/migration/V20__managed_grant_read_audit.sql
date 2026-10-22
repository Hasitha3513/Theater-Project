-- Protected synthetic grant reads only: no grant, membership, role or bootstrap changes.
CREATE TABLE cinenest_identity.grant_read_audit (
 event_id UUID PRIMARY KEY,
 venue_id UUID NOT NULL,
 actor_id UUID NOT NULL,
 target_id UUID,
 operation TEXT NOT NULL CHECK (operation = 'MANAGED_GRANTS_READ'),
 outcome TEXT NOT NULL CHECK (outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK (reason_code IN ('SCOPED_READ','NOT_FOUND','INVALID_QUERY','STALE_CONTEXT')),
 observed_at TIMESTAMPTZ NOT NULL,
 correlation_id UUID NOT NULL
);
CREATE INDEX grant_read_audit_venue_time ON cinenest_identity.grant_read_audit(venue_id, observed_at DESC, event_id DESC);
CREATE FUNCTION cinenest_identity.immutable_grant_read_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Grant read audit is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_grant_read_audit BEFORE UPDATE OR DELETE ON cinenest_identity.grant_read_audit
 FOR EACH ROW EXECUTE FUNCTION cinenest_identity.immutable_grant_read_audit();

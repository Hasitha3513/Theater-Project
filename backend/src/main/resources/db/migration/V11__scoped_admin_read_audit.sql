-- Synthetic administrative reads; no backfill or production policy seed.
CREATE TABLE cinenest_identity.admin_read_audit (
 event_id UUID PRIMARY KEY, venue_id UUID NOT NULL, actor_id UUID NOT NULL,
 target_id UUID, action TEXT NOT NULL CHECK(action IN ('ACCOUNTS_LIST','ACCOUNT_DETAIL','AUDIT_LIST','AUDIT_DETAIL')),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('SCOPED_READ','ACCESS_DENIED','NOT_FOUND')),
 correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX admin_read_venue_time ON cinenest_identity.admin_read_audit(venue_id,occurred_at DESC,event_id DESC);
CREATE FUNCTION cinenest_identity.immutable_admin_read() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Administrative read audit is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_admin_read BEFORE UPDATE OR DELETE ON cinenest_identity.admin_read_audit
 FOR EACH ROW EXECUTE FUNCTION cinenest_identity.immutable_admin_read();

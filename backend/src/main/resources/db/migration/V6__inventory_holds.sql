CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA cinenest_booking;
CREATE TABLE cinenest_booking.hold_policy (
 policy_id UUID PRIMARY KEY, ttl_seconds INTEGER NOT NULL CHECK(ttl_seconds>0),
 rate_count INTEGER NOT NULL CHECK(rate_count>0), rate_seconds INTEGER NOT NULL CHECK(rate_seconds>0),
 approval_reference TEXT NOT NULL CHECK(length(trim(approval_reference))>0),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_booking.inventory_block (
 block_id UUID PRIMARY KEY, venue_id UUID NOT NULL, room_id UUID NOT NULL,
 session_start TIMESTAMPTZ NOT NULL, session_end TIMESTAMPTZ NOT NULL,
 buffer_minutes INTEGER NOT NULL CHECK(buffer_minutes>=0), blocked_until TIMESTAMPTZ NOT NULL,
 blocking BOOLEAN NOT NULL, kind VARCHAR(16) NOT NULL CHECK(kind IN ('HOLD','CONFIRMED','MAINTENANCE')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 CHECK(session_start<session_end AND blocked_until=session_end+buffer_minutes*INTERVAL '1 minute'),
 EXCLUDE USING gist (room_id WITH =, tstzrange(session_start,blocked_until,'[)') WITH &&) WHERE (blocking)
);
CREATE TABLE cinenest_booking.reservation_hold (
 hold_id UUID PRIMARY KEY, owner_id UUID NOT NULL, block_id UUID NOT NULL UNIQUE REFERENCES cinenest_booking.inventory_block,
 policy_id UUID NOT NULL REFERENCES cinenest_booking.hold_policy,
 guests INTEGER NOT NULL CHECK(guests>0), configuration TEXT NOT NULL, timezone TEXT NOT NULL,
 minimum_policy_ids TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','RELEASED','EXPIRED','INVALIDATED')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), CHECK(expires_at>created_at)
);
CREATE UNIQUE INDEX one_active_hold_per_owner ON cinenest_booking.reservation_hold(owner_id) WHERE state='ACTIVE';
CREATE INDEX pending_hold_expiry ON cinenest_booking.reservation_hold(expires_at) WHERE state='ACTIVE';
CREATE TABLE cinenest_booking.inventory_command (
 owner_id UUID NOT NULL, command_key UUID NOT NULL, request_hash CHAR(64) NOT NULL,
 hold_id UUID NOT NULL REFERENCES cinenest_booking.reservation_hold,
 action VARCHAR(16) NOT NULL CHECK(action IN ('CREATE','RELEASE')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), PRIMARY KEY(owner_id,command_key)
);
CREATE TABLE cinenest_booking.inventory_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, hold_id UUID REFERENCES cinenest_booking.reservation_hold,
 action VARCHAR(24) NOT NULL, old_state VARCHAR(16), new_state VARCHAR(16),
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), correlation_id UUID NOT NULL
);
CREATE INDEX inventory_admission_audit ON cinenest_booking.inventory_audit(actor_id,occurred_at) WHERE action='CREATE_ATTEMPT';
CREATE FUNCTION cinenest_booking.reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Immutable inventory history'; END $$;
CREATE TRIGGER immutable_hold_policy BEFORE UPDATE OR DELETE ON cinenest_booking.hold_policy FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_inventory_command BEFORE UPDATE OR DELETE ON cinenest_booking.inventory_command FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_inventory_audit BEFORE UPDATE OR DELETE ON cinenest_booking.inventory_audit FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

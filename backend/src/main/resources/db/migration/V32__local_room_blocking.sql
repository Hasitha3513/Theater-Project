-- Exact owner-approved local synthetic Room Blocking schema; V31 is a paused unpublished workstream.
CREATE UNIQUE INDEX room_block_inventory_binding
 ON cinenest_booking.inventory_block
 (block_id,venue_id,room_id,session_start,session_end,buffer_minutes,
  blocked_until,blocking,kind,revision);

CREATE TABLE cinenest_booking.room_block_command (
 actor_id UUID NOT NULL,
 command_key UUID NOT NULL,
 request_hash CHAR(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 block_id UUID NOT NULL UNIQUE,
 venue_id UUID NOT NULL,
 room_id UUID NOT NULL,
 start_at TIMESTAMPTZ NOT NULL,
 end_at TIMESTAMPTZ NOT NULL,
 expected_configuration TEXT NOT NULL
  CHECK(length(expected_configuration) BETWEEN 1 AND 16384),
 buffer_minutes INTEGER NOT NULL DEFAULT 0 CHECK(buffer_minutes=0),
 blocked_until TIMESTAMPTZ NOT NULL,
 blocking BOOLEAN NOT NULL DEFAULT TRUE CHECK(blocking),
 kind VARCHAR(16) NOT NULL DEFAULT 'MAINTENANCE' CHECK(kind='MAINTENANCE'),
 inventory_revision BIGINT NOT NULL DEFAULT 0 CHECK(inventory_revision=0),
 audit_event_id UUID NOT NULL UNIQUE,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(actor_id,command_key),
 UNIQUE(actor_id,command_key,block_id,venue_id,room_id),
 UNIQUE(block_id,venue_id,room_id),
 CHECK(isfinite(start_at) AND isfinite(end_at) AND start_at<end_at),
 CHECK(blocked_until=end_at),
 FOREIGN KEY(block_id,venue_id,room_id,start_at,end_at,buffer_minutes,
             blocked_until,blocking,kind,inventory_revision)
 REFERENCES cinenest_booking.inventory_block
 (block_id,venue_id,room_id,session_start,session_end,buffer_minutes,
  blocked_until,blocking,kind,revision)
 ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE cinenest_booking.room_block_audit (
 event_id UUID PRIMARY KEY,
 actor_id UUID NOT NULL,
 venue_id UUID NOT NULL,
 room_id UUID NOT NULL,
 block_id UUID,
 command_key UUID,
 action VARCHAR(24) NOT NULL
  CHECK(action IN ('CREATE','BLOCK_DETAIL_READ','BLOCK_LIST_READ')),
 correlation_id UUID NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((action='CREATE' AND block_id IS NOT NULL AND command_key IS NOT NULL)
    OR (action='BLOCK_DETAIL_READ' AND block_id IS NOT NULL AND command_key IS NULL)
    OR (action='BLOCK_LIST_READ' AND block_id IS NULL AND command_key IS NULL)),
 UNIQUE(actor_id,command_key),
 UNIQUE(event_id,actor_id,command_key,block_id,venue_id,room_id),
 FOREIGN KEY(actor_id,command_key,block_id,venue_id,room_id)
 REFERENCES cinenest_booking.room_block_command
 (actor_id,command_key,block_id,venue_id,room_id)
 DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(block_id,venue_id,room_id)
 REFERENCES cinenest_booking.room_block_command(block_id,venue_id,room_id)
 DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE cinenest_booking.room_block_command
 ADD CONSTRAINT room_block_required_create_audit
 FOREIGN KEY(audit_event_id,actor_id,command_key,block_id,venue_id,room_id)
 REFERENCES cinenest_booking.room_block_audit
 (event_id,actor_id,command_key,block_id,venue_id,room_id)
 DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX room_block_scope_list
 ON cinenest_booking.room_block_command(venue_id,room_id,start_at,block_id);
CREATE INDEX room_block_audit_scope_time
 ON cinenest_booking.room_block_audit(venue_id,room_id,occurred_at,event_id);

CREATE FUNCTION cinenest_booking.room_block_empty_truncate_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'TRUNCATE' OR TG_LEVEL<>'STATEMENT'
    OR TG_TABLE_SCHEMA<>'cinenest_booking'
    OR TG_TABLE_NAME NOT IN ('room_block_command','room_block_audit')
    OR current_setting('transaction_isolation')<>'read committed' THEN
  RAISE EXCEPTION 'Immutable room block history';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid()
     AND locktype='relation' AND relation='cinenest_booking.room_block_command'::regclass
     AND mode='AccessExclusiveLock' AND granted)
    OR NOT EXISTS (SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid()
     AND locktype='relation' AND relation='cinenest_booking.room_block_audit'::regclass
     AND mode='AccessExclusiveLock' AND granted) THEN
  RAISE EXCEPTION 'Immutable room block history';
 END IF;
 IF EXISTS (SELECT 1 FROM cinenest_booking.room_block_command)
    OR EXISTS (SELECT 1 FROM cinenest_booking.room_block_audit) THEN
  RAISE EXCEPTION 'Immutable room block history';
 END IF;
 RETURN NULL;
END $$;

CREATE TRIGGER immutable_room_block_command
 BEFORE UPDATE OR DELETE ON cinenest_booking.room_block_command
 FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_room_block_command_truncate
 BEFORE TRUNCATE ON cinenest_booking.room_block_command
 FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.room_block_empty_truncate_guard();
CREATE TRIGGER immutable_room_block_audit
 BEFORE UPDATE OR DELETE ON cinenest_booking.room_block_audit
 FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_room_block_audit_truncate
 BEFORE TRUNCATE ON cinenest_booking.room_block_audit
 FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.room_block_empty_truncate_guard();

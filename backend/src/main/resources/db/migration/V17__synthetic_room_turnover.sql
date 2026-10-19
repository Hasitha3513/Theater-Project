-- Synthetic software evidence only. No inventory, credential or physical-state writes.
ALTER TABLE cinenest_rooms.room ADD CONSTRAINT room_venue_binding UNIQUE(room_id,venue_id);
CREATE TABLE cinenest_rooms.turnover_case (
 case_id UUID PRIMARY KEY, venue_id UUID NOT NULL REFERENCES cinenest_rooms.venue,
 room_id UUID NOT NULL REFERENCES cinenest_rooms.room, booking_id UUID,
 booking_revision BIGINT, synthetic BOOLEAN NOT NULL DEFAULT TRUE CHECK(synthetic),
 state TEXT NOT NULL CHECK(state IN ('OPEN','ACKNOWLEDGED','ASSERTED','RESOLVED')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), checklist_version VARCHAR(64) NOT NULL,
 checklist JSONB NOT NULL CHECK(jsonb_typeof(checklist)='array' AND jsonb_array_length(checklist)>0),
 assignee_id UUID, acknowledged_by UUID, created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((booking_id IS NULL)=(booking_revision IS NULL)),
 CHECK(state='OPEN' OR (assignee_id IS NOT NULL AND acknowledged_by=assignee_id)),
 FOREIGN KEY(room_id,venue_id) REFERENCES cinenest_rooms.room(room_id,venue_id),
 UNIQUE(case_id,venue_id,room_id)
);
CREATE TABLE cinenest_rooms.turnover_entry (
 entry_id UUID PRIMARY KEY,case_id UUID NOT NULL REFERENCES cinenest_rooms.turnover_case,
 actor_id UUID NOT NULL,case_revision BIGINT NOT NULL,kind TEXT NOT NULL,
 data JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(kind IN ('CREATE','ASSIGN','ACKNOWLEDGE','CHECKLIST','COMPLETE','EXCEPTION','RESOLVE_EXCEPTION','REVIEW'))
);
CREATE TABLE cinenest_rooms.turnover_receipt (
 actor_id UUID NOT NULL, command_key UUID NOT NULL, payload_hash CHAR(64) NOT NULL,
 case_id UUID NOT NULL REFERENCES cinenest_rooms.turnover_case, created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_rooms.turnover_audit (
 audit_id UUID PRIMARY KEY, actor_id UUID NOT NULL,venue_id UUID NOT NULL,
 case_id UUID NOT NULL REFERENCES cinenest_rooms.turnover_case, action TEXT NOT NULL,
 revision BIGINT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX turnover_queue ON cinenest_rooms.turnover_case(venue_id,state,created_at,case_id);
CREATE INDEX turnover_history ON cinenest_rooms.turnover_entry(case_id,case_revision);
CREATE FUNCTION cinenest_rooms.turnover_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Synthetic turnover evidence is append only'; END $$;
CREATE TRIGGER turnover_entry_immutable BEFORE UPDATE OR DELETE ON cinenest_rooms.turnover_entry FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.turnover_immutable();
CREATE TRIGGER turnover_receipt_immutable BEFORE UPDATE OR DELETE ON cinenest_rooms.turnover_receipt FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.turnover_immutable();
CREATE TRIGGER turnover_audit_immutable BEFORE UPDATE OR DELETE ON cinenest_rooms.turnover_audit FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.turnover_immutable();
CREATE FUNCTION cinenest_rooms.turnover_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Turnover cases must be retained'; END IF;
 IF ROW(NEW.case_id,NEW.venue_id,NEW.room_id,NEW.booking_id,NEW.booking_revision,NEW.synthetic,NEW.checklist_version,NEW.checklist,NEW.created_by,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.case_id,OLD.venue_id,OLD.room_id,OLD.booking_id,OLD.booking_revision,OLD.synthetic,OLD.checklist_version,OLD.checklist,OLD.created_by,OLD.created_at)
 OR NEW.revision<>OLD.revision+1 OR OLD.state='RESOLVED' THEN RAISE EXCEPTION 'Invalid turnover revision/context'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER turnover_case_guard BEFORE UPDATE OR DELETE ON cinenest_rooms.turnover_case FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.turnover_guard();
-- A checklist version identifies one immutable definition across cases, not just one row.
CREATE FUNCTION cinenest_rooms.turnover_definition_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(NEW.checklist_version,17018));
 IF EXISTS(SELECT 1 FROM cinenest_rooms.turnover_case WHERE checklist_version=NEW.checklist_version AND checklist<>NEW.checklist) THEN
  RAISE EXCEPTION 'Checklist version definition conflict';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER turnover_definition BEFORE INSERT ON cinenest_rooms.turnover_case FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.turnover_definition_guard();

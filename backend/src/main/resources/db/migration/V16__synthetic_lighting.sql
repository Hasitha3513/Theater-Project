CREATE SCHEMA cinenest_iot;
CREATE TABLE cinenest_iot.device (
 device_id UUID PRIMARY KEY, venue_id UUID NOT NULL, room_id UUID NOT NULL,
 capability TEXT NOT NULL CHECK(capability='LIGHT_SET_STATE'), synthetic BOOLEAN NOT NULL CHECK(synthetic),
 enabled BOOLEAN NOT NULL, online BOOLEAN NOT NULL, revision BIGINT NOT NULL DEFAULT 1 CHECK(revision>0),
 desired TEXT CHECK(desired IN ('ON','OFF')), observed TEXT CHECK(observed IN ('ON','OFF')),
 observation_sequence BIGINT NOT NULL DEFAULT 0 CHECK(observation_sequence>=0), observed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((observed IS NULL AND observed_at IS NULL) OR (observed IS NOT NULL AND observed_at IS NOT NULL)),
 UNIQUE(venue_id,room_id), UNIQUE(device_id,venue_id,room_id)
);
CREATE TABLE cinenest_iot.assignment (
 assignment_id UUID PRIMARY KEY, device_id UUID NOT NULL REFERENCES cinenest_iot.device,
 venue_id UUID NOT NULL,room_id UUID NOT NULL,assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(device_id,venue_id,room_id) REFERENCES cinenest_iot.device(device_id,venue_id,room_id)
);
CREATE TABLE cinenest_iot.command (
 command_id UUID PRIMARY KEY, device_id UUID NOT NULL REFERENCES cinenest_iot.device,
 actor_id UUID NOT NULL,venue_id UUID NOT NULL,room_id UUID NOT NULL,booking_id UUID NOT NULL,booking_revision BIGINT NOT NULL CHECK(booking_revision>0),
 starts_at TIMESTAMPTZ NOT NULL,ends_at TIMESTAMPTZ NOT NULL,timezone TEXT NOT NULL,
 desired TEXT NOT NULL CHECK(desired IN ('ON','OFF')),state TEXT NOT NULL CHECK(state IN ('PENDING','ACKNOWLEDGED','FAILED','EXPIRED','UNKNOWN')),
 revision BIGINT NOT NULL DEFAULT 1 CHECK(revision>0), correlation_id UUID NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),expires_at TIMESTAMPTZ NOT NULL,
 CHECK(starts_at<ends_at AND expires_at<=ends_at AND expires_at>created_at),
 FOREIGN KEY(device_id,venue_id,room_id) REFERENCES cinenest_iot.device(device_id,venue_id,room_id)
);
CREATE UNIQUE INDEX one_pending_device_command ON cinenest_iot.command(device_id) WHERE state IN ('PENDING','UNKNOWN');
CREATE TABLE cinenest_iot.receipt (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,payload_hash TEXT NOT NULL CHECK(length(payload_hash)=64),command_id UUID NOT NULL REFERENCES cinenest_iot.command,
 action TEXT NOT NULL CHECK(action IN ('COMMAND','SIMULATE','RECONCILE')),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_iot.attempt (
 attempt_id UUID PRIMARY KEY,command_id UUID NOT NULL REFERENCES cinenest_iot.command,actor_id UUID NOT NULL,
 action TEXT NOT NULL CHECK(action IN ('SIMULATE','RECONCILE')),outcome TEXT NOT NULL CHECK(outcome IN ('ACKNOWLEDGED','FAILED','UNKNOWN')),
 observed TEXT CHECK(observed IN ('ON','OFF')),observation_sequence BIGINT,observed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_iot.audit (
 audit_id UUID PRIMARY KEY,actor_id UUID,venue_id UUID,device_id UUID,command_id UUID,
 action TEXT NOT NULL,outcome TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE FUNCTION cinenest_iot.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'IoT history is append-only'; END $$;
CREATE TRIGGER assignment_history BEFORE UPDATE OR DELETE ON cinenest_iot.assignment FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER attempt_history BEFORE UPDATE OR DELETE ON cinenest_iot.attempt FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER receipt_history BEFORE UPDATE OR DELETE ON cinenest_iot.receipt FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER audit_history BEFORE UPDATE OR DELETE ON cinenest_iot.audit FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE FUNCTION cinenest_iot.command_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Command history retained'; END IF;
 IF (to_jsonb(OLD)-'state'-'revision') IS DISTINCT FROM (to_jsonb(NEW)-'state'-'revision') OR NEW.revision<>OLD.revision+1
 OR NOT ((OLD.state='PENDING' AND NEW.state IN ('ACKNOWLEDGED','FAILED','EXPIRED','UNKNOWN')) OR (OLD.state='UNKNOWN' AND NEW.state IN ('ACKNOWLEDGED','FAILED','UNKNOWN'))) THEN RAISE EXCEPTION 'Invalid command transition'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER command_guard BEFORE UPDATE OR DELETE ON cinenest_iot.command FOR EACH ROW EXECUTE FUNCTION cinenest_iot.command_guard();
CREATE FUNCTION cinenest_iot.device_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Device history retained'; END IF;
 IF NEW.device_id<>OLD.device_id OR NEW.venue_id<>OLD.venue_id OR NEW.room_id<>OLD.room_id OR NEW.capability<>OLD.capability OR NEW.synthetic<>OLD.synthetic OR NEW.revision<>OLD.revision+1 OR NEW.observation_sequence<OLD.observation_sequence OR ((NEW.observed IS DISTINCT FROM OLD.observed OR NEW.observed_at IS DISTINCT FROM OLD.observed_at) AND (NEW.observation_sequence<=OLD.observation_sequence OR NEW.observed_at IS NULL OR (OLD.observed_at IS NOT NULL AND NEW.observed_at<=OLD.observed_at))) THEN RAISE EXCEPTION 'Invalid device revision/assignment'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER device_guard BEFORE UPDATE OR DELETE ON cinenest_iot.device FOR EACH ROW EXECUTE FUNCTION cinenest_iot.device_guard();

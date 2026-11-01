CREATE TABLE cinenest_iot.lock_room_assignment (
 assignment_id UUID PRIMARY KEY,
 venue_id UUID NOT NULL,
 room_id UUID NOT NULL UNIQUE,
 device_id UUID NOT NULL UNIQUE,
 assignment_revision BIGINT NOT NULL DEFAULT 1 CHECK(assignment_revision=1),
 state TEXT NOT NULL DEFAULT 'UNQUALIFIED' CHECK(state='UNQUALIFIED'),
 customer_access_enabled BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT customer_access_enabled),
 room_configuration TEXT NOT NULL CHECK(length(room_configuration) BETWEEN 1 AND 16384),
 device_metadata_revision BIGINT NOT NULL CHECK(device_metadata_revision>0),
 created_by UUID NOT NULL,
 command_key UUID NOT NULL,
 audit_event_id UUID NOT NULL UNIQUE,
 audit_action TEXT NOT NULL DEFAULT 'CREATE' CHECK(audit_action='CREATE'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(created_at)),
 UNIQUE(created_by,command_key),
 UNIQUE(assignment_id,venue_id,room_id),
 UNIQUE(assignment_id,venue_id,room_id,device_id),
 UNIQUE(assignment_id,venue_id,room_id,device_id,created_by,command_key,audit_event_id),
 FOREIGN KEY(device_id,venue_id)
  REFERENCES cinenest_iot.lock_registry_device(device_id,venue_id),
 FOREIGN KEY(device_id,venue_id,device_metadata_revision)
  REFERENCES cinenest_iot.lock_registry_revision(device_id,venue_id,revision)
);

CREATE TABLE cinenest_iot.lock_room_assignment_command (
 actor_id UUID NOT NULL,
 command_key UUID NOT NULL,
 assignment_id UUID NOT NULL UNIQUE,
 venue_id UUID NOT NULL,
 room_id UUID NOT NULL,
 device_id UUID NOT NULL,
 audit_event_id UUID NOT NULL,
 request_sha256 BYTEA NOT NULL CHECK(octet_length(request_sha256)=32),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(recorded_at)),
 PRIMARY KEY(actor_id,command_key),
 FOREIGN KEY(assignment_id,venue_id,room_id,device_id,actor_id,command_key,audit_event_id)
  REFERENCES cinenest_iot.lock_room_assignment
   (assignment_id,venue_id,room_id,device_id,created_by,command_key,audit_event_id)
  DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE cinenest_iot.lock_room_assignment_audit (
 event_id UUID PRIMARY KEY,
 actor_id UUID,
 venue_id UUID,
 room_id UUID,
 assignment_id UUID,
 command_key UUID,
 action TEXT NOT NULL CHECK(action IN('CREATE','REPLAY','LIST_READ','DETAIL_READ','DENIED')),
 outcome TEXT NOT NULL CHECK(outcome IN('ALLOW','DENY')),
 denial_code TEXT CHECK(denial_code IN('INVALID_REQUEST','ACCESS_DENIED','NOT_FOUND','CONFLICT','UNAVAILABLE')),
 correlation_id UUID NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(occurred_at)),
 CHECK(
  (action='DENIED' AND outcome='DENY' AND denial_code IS NOT NULL
   AND venue_id IS NULL AND room_id IS NULL AND assignment_id IS NULL AND command_key IS NULL)
  OR
  (action IN('CREATE','REPLAY','LIST_READ','DETAIL_READ') AND outcome='ALLOW'
   AND denial_code IS NULL AND actor_id IS NOT NULL AND venue_id IS NOT NULL AND room_id IS NOT NULL
   AND ((action='CREATE' AND assignment_id IS NOT NULL AND command_key IS NOT NULL)
     OR (action IN('REPLAY','DETAIL_READ') AND assignment_id IS NOT NULL AND command_key IS NULL)
     OR (action='LIST_READ' AND assignment_id IS NULL AND command_key IS NULL)))
 ),
 UNIQUE(event_id,actor_id,venue_id,room_id,assignment_id,command_key,action),
 FOREIGN KEY(assignment_id,venue_id,room_id)
  REFERENCES cinenest_iot.lock_room_assignment(assignment_id,venue_id,room_id)
  DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE cinenest_iot.lock_room_assignment
 ADD CONSTRAINT lock_assignment_required_command
 FOREIGN KEY(created_by,command_key)
 REFERENCES cinenest_iot.lock_room_assignment_command(actor_id,command_key)
 DEFERRABLE INITIALLY DEFERRED,
 ADD CONSTRAINT lock_assignment_required_audit
 FOREIGN KEY(audit_event_id,created_by,venue_id,room_id,assignment_id,command_key,audit_action)
 REFERENCES cinenest_iot.lock_room_assignment_audit
  (event_id,actor_id,venue_id,room_id,assignment_id,command_key,action)
 DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX lock_assignment_one_create_audit
 ON cinenest_iot.lock_room_assignment_audit(assignment_id) WHERE action='CREATE';
CREATE INDEX lock_assignment_scope_page
 ON cinenest_iot.lock_room_assignment(venue_id,room_id,created_at DESC,assignment_id DESC);
CREATE INDEX lock_assignment_audit_scope
 ON cinenest_iot.lock_room_assignment_audit(venue_id,room_id,occurred_at DESC,event_id);

CREATE TRIGGER lock_assignment_history BEFORE UPDATE OR DELETE
 ON cinenest_iot.lock_room_assignment FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER lock_assignment_no_truncate BEFORE TRUNCATE
 ON cinenest_iot.lock_room_assignment FOR EACH STATEMENT EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER lock_assignment_command_history BEFORE UPDATE OR DELETE
 ON cinenest_iot.lock_room_assignment_command FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER lock_assignment_command_no_truncate BEFORE TRUNCATE
 ON cinenest_iot.lock_room_assignment_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER lock_assignment_audit_history BEFORE UPDATE OR DELETE
 ON cinenest_iot.lock_room_assignment_audit FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history();
CREATE TRIGGER lock_assignment_audit_no_truncate BEFORE TRUNCATE
 ON cinenest_iot.lock_room_assignment_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_iot.immutable_history();

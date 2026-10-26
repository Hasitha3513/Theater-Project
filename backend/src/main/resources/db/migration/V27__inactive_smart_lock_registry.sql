-- Qualification-only registry. No assignments, activation, credentials or grants.
CREATE TABLE cinenest_iot.lock_registry_device (
 device_id UUID PRIMARY KEY,venue_id UUID NOT NULL,
 provider TEXT NOT NULL CHECK(provider='TUYA'),
 provider_scope_id UUID NOT NULL,provider_identity_id UUID NOT NULL,
 registration_status TEXT NOT NULL DEFAULT 'REGISTERED' CHECK(registration_status='REGISTERED'),
 initial_revision BIGINT NOT NULL DEFAULT 1 CHECK(initial_revision=1),
 registered_by UUID NOT NULL,registration_command_key UUID NOT NULL,
 registered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(device_id,venue_id),UNIQUE(provider,provider_scope_id,provider_identity_id)
);
CREATE TABLE cinenest_iot.lock_registry_audit (
 event_id UUID PRIMARY KEY,actor_id UUID,venue_id UUID,device_id UUID,
 metadata_revision BIGINT CHECK(metadata_revision>0),
 event_kind TEXT NOT NULL CHECK(event_kind IN('REGISTER','METADATA_RECORDED','REPLAY','LIST_READ','DETAIL_READ','REVISION_LIST','REVISION_READ','DENIED')),
 outcome TEXT NOT NULL CHECK(outcome IN('ALLOW','DENY')),denial_code TEXT,
 correlation_id UUID NOT NULL,observed_at TIMESTAMPTZ NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((event_kind='DENIED' AND outcome='DENY' AND denial_code IS NOT NULL AND denial_code IN('INVALID_REQUEST','ACCESS_DENIED','NOT_FOUND','CONFLICT','UNAVAILABLE') AND venue_id IS NULL)
 OR(event_kind<>'DENIED' AND outcome='ALLOW' AND denial_code IS NULL AND actor_id IS NOT NULL AND venue_id IS NOT NULL)),
 CHECK((event_kind IN('DENIED','LIST_READ') AND device_id IS NULL AND metadata_revision IS NULL)
 OR(event_kind='REVISION_LIST' AND device_id IS NOT NULL AND metadata_revision IS NULL)
 OR(event_kind IN('REGISTER','METADATA_RECORDED','REPLAY','DETAIL_READ','REVISION_READ') AND device_id IS NOT NULL AND metadata_revision IS NOT NULL)),
 UNIQUE(event_id,device_id,venue_id,actor_id,metadata_revision,event_kind),
 FOREIGN KEY(device_id,venue_id) REFERENCES cinenest_iot.lock_registry_device(device_id,venue_id)
);
CREATE TABLE cinenest_iot.lock_registry_revision (
 device_id UUID NOT NULL,venue_id UUID NOT NULL,revision BIGINT NOT NULL CHECK(revision>0),
 manufacturer VARCHAR(128) NOT NULL CHECK(length(btrim(manufacturer))>0),
 model VARCHAR(128) NOT NULL CHECK(length(btrim(model))>0),
 sku VARCHAR(128) CHECK(length(btrim(sku))>0),
 hardware_revision VARCHAR(128) CHECK(length(btrim(hardware_revision))>0),
 firmware_revision VARCHAR(128) CHECK(length(btrim(firmware_revision))>0),
 provider_reference_id UUID NOT NULL,provider_reference_version UUID NOT NULL,
 gateway_reference_id UUID,gateway_reference_version UUID,
 declaration_source TEXT NOT NULL CHECK(declaration_source IN('OPERATOR_DECLARATION','VENDOR_DOCUMENT','CONTRACT_FIXTURE')),
 declaration_reference UUID,declared_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
 qualification TEXT NOT NULL DEFAULT 'UNQUALIFIED' CHECK(qualification='UNQUALIFIED'),
 recorded_by UUID NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 audit_event_id UUID NOT NULL,audit_kind TEXT NOT NULL,
 PRIMARY KEY(device_id,revision),UNIQUE(device_id,venue_id,revision),UNIQUE(device_id,venue_id,revision,audit_event_id),
 CHECK((gateway_reference_id IS NULL)=(gateway_reference_version IS NULL)),
 CHECK(declaration_source<>'VENDOR_DOCUMENT' OR declaration_reference IS NOT NULL),
 CHECK(jsonb_typeof(declared_capabilities)='array' AND declared_capabilities <@ '["SCHEDULED_PIN","STATUS_READBACK","REVOKE","RECONCILE","REENTRY"]'::jsonb),
 CHECK((revision=1 AND audit_kind='REGISTER') OR(revision>1 AND audit_kind='METADATA_RECORDED')),
 FOREIGN KEY(device_id,venue_id) REFERENCES cinenest_iot.lock_registry_device(device_id,venue_id),
 FOREIGN KEY(audit_event_id,device_id,venue_id,recorded_by,revision,audit_kind)
 REFERENCES cinenest_iot.lock_registry_audit(event_id,device_id,venue_id,actor_id,metadata_revision,event_kind) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE cinenest_iot.lock_registry_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,venue_id UUID NOT NULL,device_id UUID NOT NULL,
 initial_revision BIGINT NOT NULL DEFAULT 1 CHECK(initial_revision=1),
 request_sha256 BYTEA NOT NULL CHECK(octet_length(request_sha256)=32),
 audit_event_id UUID NOT NULL,audit_kind TEXT NOT NULL DEFAULT 'REGISTER' CHECK(audit_kind='REGISTER'),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(actor_id,command_key),UNIQUE(device_id),UNIQUE(device_id,venue_id,actor_id,command_key),
 FOREIGN KEY(device_id,venue_id) REFERENCES cinenest_iot.lock_registry_device(device_id,venue_id),
 FOREIGN KEY(device_id,venue_id,initial_revision,audit_event_id) REFERENCES cinenest_iot.lock_registry_revision(device_id,venue_id,revision,audit_event_id) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(audit_event_id,device_id,venue_id,actor_id,initial_revision,audit_kind)
 REFERENCES cinenest_iot.lock_registry_audit(event_id,device_id,venue_id,actor_id,metadata_revision,event_kind) DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE cinenest_iot.lock_registry_device
 ADD CONSTRAINT lock_registry_initial_revision FOREIGN KEY(device_id,venue_id,initial_revision) REFERENCES cinenest_iot.lock_registry_revision(device_id,venue_id,revision) DEFERRABLE INITIALLY DEFERRED,
 ADD CONSTRAINT lock_registry_initial_command FOREIGN KEY(device_id,venue_id,registered_by,registration_command_key) REFERENCES cinenest_iot.lock_registry_command(device_id,venue_id,actor_id,command_key) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE cinenest_iot.lock_registry_audit
 ADD CONSTRAINT lock_registry_audit_revision FOREIGN KEY(device_id,venue_id,metadata_revision) REFERENCES cinenest_iot.lock_registry_revision(device_id,venue_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE FUNCTION cinenest_iot.lock_registry_revision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE next_revision BIGINT;
BEGIN
 PERFORM 1 FROM cinenest_iot.lock_registry_device WHERE device_id=NEW.device_id AND venue_id=NEW.venue_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Registry context unavailable' USING ERRCODE='23514';END IF;
 SELECT COALESCE(MAX(revision),0)+1 INTO next_revision FROM cinenest_iot.lock_registry_revision WHERE device_id=NEW.device_id;
 IF NEW.revision<>next_revision THEN RAISE EXCEPTION 'Registry revision conflict' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER registry_revision_sequence BEFORE INSERT ON cinenest_iot.lock_registry_revision FOR EACH ROW EXECUTE FUNCTION cinenest_iot.lock_registry_revision_guard();
DO $$ DECLARE table_name TEXT;BEGIN
 FOREACH table_name IN ARRAY ARRAY['lock_registry_device','lock_registry_revision','lock_registry_command','lock_registry_audit'] LOOP
  EXECUTE format('CREATE TRIGGER registry_history BEFORE UPDATE OR DELETE ON cinenest_iot.%I FOR EACH ROW EXECUTE FUNCTION cinenest_iot.immutable_history()',table_name);
  EXECUTE format('CREATE TRIGGER registry_no_truncate BEFORE TRUNCATE ON cinenest_iot.%I FOR EACH STATEMENT EXECUTE FUNCTION cinenest_iot.immutable_history()',table_name);
 END LOOP;
END $$;
CREATE INDEX lock_registry_device_page ON cinenest_iot.lock_registry_device(venue_id,registered_at DESC,device_id DESC);
CREATE INDEX lock_registry_audit_page ON cinenest_iot.lock_registry_audit(venue_id,recorded_at DESC,event_id);

-- Notifications alone owns these records. Historical business records and V1-V13 remain unchanged.
CREATE SCHEMA cinenest_notifications;
CREATE TABLE cinenest_notifications.notification_event (
 event_id UUID PRIMARY KEY, source TEXT NOT NULL CHECK(source IN('PAYMENTS','ACCESS')), resource_id UUID NOT NULL,
 source_revision BIGINT NOT NULL CHECK(source_revision>=0), owner_id UUID NOT NULL, venue_id UUID NOT NULL,
 kind TEXT NOT NULL CHECK(kind IN('BANK_CLAIM_RECEIVED','BANK_REVIEW_COMPLETED','BANK_REVIEW_NEEDS_INFORMATION','BANK_REVIEW_REJECTED','SYNTHETIC_ACCESS_READY','SYNTHETIC_ACCESS_EXPIRED','SYNTHETIC_ACCESS_REVOKED','SYNTHETIC_OPERATION_EXCEPTION')),
 reviewers BOOLEAN NOT NULL, observed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),UNIQUE(event_id,venue_id,owner_id)
);
CREATE TABLE cinenest_notifications.notification_intent (
 intent_id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES cinenest_notifications.notification_event,
 recipient_id UUID NOT NULL,venue_id UUID NOT NULL,source_owner_id UUID NOT NULL, audience TEXT NOT NULL CHECK(audience IN('OWNER','STAFF')),
 channel TEXT NOT NULL CHECK(channel='IN_APP_SYNTHETIC'),template_version INTEGER NOT NULL CHECK(template_version=1),
 destination TEXT NOT NULL CHECK(destination IN('/bank','/access')),expires_at TIMESTAMPTZ NOT NULL,retain_until TIMESTAMPTZ NOT NULL,
 available_at TIMESTAMPTZ,read_at TIMESTAMPTZ,revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 UNIQUE(event_id,recipient_id,channel),FOREIGN KEY(event_id,venue_id,source_owner_id) REFERENCES cinenest_notifications.notification_event(event_id,venue_id,owner_id),CHECK(audience<>'OWNER' OR recipient_id=source_owner_id),CHECK(retain_until>=expires_at),CHECK(read_at IS NULL OR available_at IS NOT NULL)
);
CREATE TABLE cinenest_notifications.notification_work (
 operation_id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES cinenest_notifications.notification_event,
 intent_id UUID UNIQUE REFERENCES cinenest_notifications.notification_intent,
 state TEXT NOT NULL CHECK(state IN('UNASSIGNED','READY','LEASED','SIMULATOR_ACCEPTED','DELIVERY_FAILED','DELIVERY_UNKNOWN','SUPPRESSED')),
 fixture TEXT NOT NULL DEFAULT 'ACCEPTED' CHECK(fixture IN('ACCEPTED','FAILED','UNKNOWN_ACCEPTED','UNKNOWN_FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),due_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 lease_until TIMESTAMPTZ,fence BIGINT NOT NULL DEFAULT 0 CHECK(fence>=0),UNIQUE(event_id,intent_id)
);
CREATE UNIQUE INDEX notification_unassigned ON cinenest_notifications.notification_work(event_id) WHERE intent_id IS NULL;
CREATE TABLE cinenest_notifications.notification_attempt (
 attempt_id UUID PRIMARY KEY,operation_id UUID NOT NULL REFERENCES cinenest_notifications.notification_work,
 fence BIGINT NOT NULL CHECK(fence>0),outcome TEXT NOT NULL CHECK(outcome IN('DELIVERY_ATTEMPTED','SIMULATOR_ACCEPTED','DELIVERY_FAILED','DELIVERY_UNKNOWN','RECONCILED_ACCEPTED','RECONCILED_FAILED')),
 capture_evidence TEXT CHECK(capture_evidence IN('ACCEPTED','FAILED')),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(operation_id,fence,outcome)
);
CREATE TABLE cinenest_notifications.notification_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,intent_id UUID NOT NULL REFERENCES cinenest_notifications.notification_intent,
 kind TEXT NOT NULL,payload_hash CHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_notifications.notification_audit (
 audit_id UUID PRIMARY KEY,actor_id UUID,event_id UUID,intent_id UUID,venue_id UUID,action TEXT NOT NULL,outcome TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX notification_inbox ON cinenest_notifications.notification_intent(recipient_id,expires_at,intent_id);
CREATE INDEX notification_rate ON cinenest_notifications.notification_audit(actor_id,created_at);
CREATE FUNCTION cinenest_notifications.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Immutable notification evidence'; END $$;
CREATE TRIGGER immutable_notification_audit BEFORE UPDATE OR DELETE ON cinenest_notifications.notification_audit FOR EACH ROW EXECUTE FUNCTION cinenest_notifications.immutable_history();
CREATE TRIGGER immutable_notification_attempt BEFORE UPDATE OR DELETE ON cinenest_notifications.notification_attempt FOR EACH ROW EXECUTE FUNCTION cinenest_notifications.immutable_history();
CREATE TRIGGER immutable_notification_command BEFORE UPDATE OR DELETE ON cinenest_notifications.notification_command FOR EACH ROW EXECUTE FUNCTION cinenest_notifications.immutable_history();
CREATE TRIGGER immutable_notification_event BEFORE UPDATE OR DELETE ON cinenest_notifications.notification_event FOR EACH ROW EXECUTE FUNCTION cinenest_notifications.immutable_history();

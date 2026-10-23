-- Owner-authorized pre-start, never-issued synthetic booking cancellation only.
ALTER TABLE cinenest_booking.booking DROP CONSTRAINT booking_state_check;
ALTER TABLE cinenest_booking.booking ADD CONSTRAINT booking_state_check CHECK(state IN ('SIMULATED_CONFIRMED','SIMULATED_CANCELLED'));
ALTER TABLE cinenest_booking.booking ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0 CHECK(lifecycle_version IN(0,1));
CREATE TABLE cinenest_booking.booking_cancellation (
 cancellation_id UUID PRIMARY KEY,booking_id UUID NOT NULL UNIQUE REFERENCES cinenest_booking.booking,
 actor_id UUID NOT NULL,purchased_revision BIGINT NOT NULL,lifecycle_version BIGINT NOT NULL CHECK(lifecycle_version=1),
 policy_version TEXT NOT NULL CHECK(policy_version='SYNTHETIC_PRESTART_CANCEL_V1'),reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 240),
 obligation_id UUID NOT NULL UNIQUE,block_id UUID NOT NULL UNIQUE REFERENCES cinenest_booking.inventory_block,
 notification_revision BIGINT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(booking_id,purchased_revision) REFERENCES cinenest_booking.booking_revision(booking_id,revision),
 CHECK(notification_revision=purchased_revision+1)
);
CREATE TABLE cinenest_booking.cancellation_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,payload_hash CHAR(64) NOT NULL,
 cancellation_id UUID NOT NULL REFERENCES cinenest_booking.booking_cancellation,result JSONB NOT NULL,
 PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_booking.cancellation_audit (
 event_id UUID PRIMARY KEY,cancellation_id UUID NOT NULL REFERENCES cinenest_booking.booking_cancellation,
 actor_id UUID NOT NULL,booking_id UUID NOT NULL,venue_id UUID NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_booking_cancellation BEFORE UPDATE OR DELETE ON cinenest_booking.booking_cancellation FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_cancellation_command BEFORE UPDATE OR DELETE ON cinenest_booking.cancellation_command FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_cancellation_audit BEFORE UPDATE OR DELETE ON cinenest_booking.cancellation_audit FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TABLE cinenest_payments.cancellation_refund_obligation (
 obligation_id UUID PRIMARY KEY,cancellation_id UUID NOT NULL UNIQUE,booking_id UUID NOT NULL UNIQUE,
 request_id UUID NOT NULL UNIQUE REFERENCES cinenest_payments.bank_payment_request,
 owner_id UUID NOT NULL,venue_id UUID NOT NULL,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 state TEXT NOT NULL CHECK(state='RESERVED_REVIEW_REQUIRED'),created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE cinenest_payments.cancellation_refund_source (
 obligation_id UUID NOT NULL REFERENCES cinenest_payments.cancellation_refund_obligation,
 allocation_id UUID NOT NULL UNIQUE REFERENCES cinenest_payments.payment_allocation,
 amount_minor BIGINT NOT NULL CHECK(amount_minor>0),PRIMARY KEY(obligation_id,allocation_id)
);
CREATE TRIGGER immutable_cancellation_refund_obligation BEFORE UPDATE OR DELETE ON cinenest_payments.cancellation_refund_obligation FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_cancellation_refund_source BEFORE UPDATE OR DELETE ON cinenest_payments.cancellation_refund_source FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE FUNCTION cinenest_payments.check_cancellation_refund() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE principal BIGINT;linked NUMERIC;
BEGIN
 SELECT amount_minor INTO principal FROM cinenest_payments.cancellation_refund_obligation WHERE obligation_id=NEW.obligation_id;
 SELECT sum(s.amount_minor) INTO linked FROM cinenest_payments.cancellation_refund_source s
 JOIN cinenest_payments.payment_allocation a USING(allocation_id)
 JOIN cinenest_payments.cancellation_refund_obligation o USING(obligation_id)
 WHERE s.obligation_id=NEW.obligation_id AND s.amount_minor=a.amount_minor AND a.request_id=o.request_id;
 IF principal IS NULL OR linked IS DISTINCT FROM principal::numeric OR EXISTS(SELECT 1 FROM cinenest_payments.cancellation_refund_source s JOIN cinenest_payments.payment_allocation a USING(allocation_id) JOIN cinenest_payments.cancellation_refund_obligation o USING(obligation_id) WHERE s.obligation_id=NEW.obligation_id AND (s.amount_minor<>a.amount_minor OR a.request_id<>o.request_id)) THEN RAISE EXCEPTION 'Incomplete cancellation entitlement';END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER cancellation_refund_complete AFTER INSERT ON cinenest_payments.cancellation_refund_obligation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_payments.check_cancellation_refund();
CREATE CONSTRAINT TRIGGER cancellation_refund_source_complete AFTER INSERT ON cinenest_payments.cancellation_refund_source DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_payments.check_cancellation_refund();
DROP TRIGGER immutable_booking ON cinenest_booking.booking;
CREATE FUNCTION cinenest_booking.guard_booking_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Immutable booking history';END IF;
 IF OLD.state<>'SIMULATED_CONFIRMED' OR NEW.state<>'SIMULATED_CANCELLED' OR OLD.lifecycle_version<>0 OR NEW.lifecycle_version<>1
 OR (to_jsonb(NEW)-'state'-'lifecycle_version') IS DISTINCT FROM (to_jsonb(OLD)-'state'-'lifecycle_version')
 OR NOT EXISTS(SELECT 1 FROM cinenest_booking.booking_cancellation c JOIN cinenest_booking.inventory_block i USING(block_id)
 WHERE c.booking_id=NEW.booking_id AND c.purchased_revision=(SELECT max(revision) FROM cinenest_booking.booking_revision WHERE booking_id=NEW.booking_id)
 AND NOT i.blocking AND i.kind='CONFIRMED' AND i.venue_id=NEW.venue_id AND i.session_start>clock_timestamp())
 THEN RAISE EXCEPTION 'Invalid synthetic cancellation';END IF;RETURN NEW;
END $$;
CREATE TRIGGER booking_cancellation_guard BEFORE UPDATE OR DELETE ON cinenest_booking.booking FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_booking_cancellation();
ALTER TABLE cinenest_notifications.notification_event DROP CONSTRAINT notification_event_kind_check;
ALTER TABLE cinenest_notifications.notification_event ADD CHECK(kind IN('BANK_CLAIM_RECEIVED','BANK_REVIEW_COMPLETED','BANK_REVIEW_NEEDS_INFORMATION','BANK_REVIEW_REJECTED','SYNTHETIC_ACCESS_READY','SYNTHETIC_ACCESS_EXPIRED','SYNTHETIC_ACCESS_REVOKED','SYNTHETIC_OPERATION_EXCEPTION','AMENDMENT_CONFIRMED','BOOKING_CANCELLED'));
-- V15's expression CHECK name is discovered deterministically; preserve its exact meaning plus cancellation.
DO $$ DECLARE c RECORD;BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='cinenest_notifications.notification_event'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%source%BOOKING%AMENDMENT_CONFIRMED%' LOOP
 EXECUTE format('ALTER TABLE cinenest_notifications.notification_event DROP CONSTRAINT %I',c.conname);
 END LOOP;
END $$;
ALTER TABLE cinenest_notifications.notification_event ADD CHECK((source='BOOKING')=(kind IN('AMENDMENT_CONFIRMED','BOOKING_CANCELLED')));

ALTER TABLE cinenest_booking.booking ADD CHECK((state='SIMULATED_CANCELLED')=(lifecycle_version=1));
CREATE FUNCTION cinenest_booking.check_complete_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_booking.booking b
 JOIN cinenest_booking.booking_cancellation c USING(booking_id)
 JOIN cinenest_booking.cancellation_audit a USING(cancellation_id)
 JOIN cinenest_booking.cancellation_command cmd USING(cancellation_id)
 JOIN cinenest_booking.inventory_block i ON i.block_id=c.block_id
 JOIN cinenest_booking.booking_revision r ON r.booking_id=b.booking_id AND r.revision=c.purchased_revision
 JOIN cinenest_booking.reservation_hold h ON h.hold_id=(r.snapshot->>'holdId')::uuid AND h.block_id=i.block_id
 WHERE b.booking_id=NEW.booking_id AND b.state='SIMULATED_CANCELLED' AND b.lifecycle_version=c.lifecycle_version
 AND a.actor_id=c.actor_id AND cmd.actor_id=c.actor_id AND NOT i.blocking AND i.kind='CONFIRMED'
 AND i.venue_id=b.venue_id AND i.room_id=(r.snapshot->>'roomId')::uuid
 AND i.session_start=(r.snapshot->>'sessionStart')::timestamptz AND i.session_start>clock_timestamp())
 THEN RAISE EXCEPTION 'Incomplete cancellation';END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER cancellation_complete AFTER INSERT ON cinenest_booking.booking_cancellation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_booking.check_complete_cancellation();

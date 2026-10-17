-- Internal dry runs only: no real funds, allocations or confirmation records.
CREATE SCHEMA cinenest_payments;
CREATE TABLE cinenest_payments.bank_policy_revision (
 policy_id UUID PRIMARY KEY, version TEXT NOT NULL UNIQUE, content_hash CHAR(64) NOT NULL,
 policy JSONB NOT NULL, synthetic BOOLEAN NOT NULL CHECK(synthetic), created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_payments.bank_payment_request (
 request_id UUID PRIMARY KEY, owner_id UUID NOT NULL, venue_id UUID NOT NULL, quote_id UUID NOT NULL UNIQUE, hold_id UUID NOT NULL UNIQUE,
 policy_id UUID NOT NULL REFERENCES cinenest_payments.bank_policy_revision,
 quote_revision BIGINT NOT NULL CHECK(quote_revision>0), quote_hash CHAR(64) NOT NULL, snapshot JSONB NOT NULL,
 amount_minor BIGINT NOT NULL CHECK(amount_minor>=0), currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 timezone TEXT NOT NULL, instruction_version TEXT NOT NULL CHECK(instruction_version LIKE 'SYNTHETIC-%'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), expires_at TIMESTAMPTZ NOT NULL,
 initiate_by TIMESTAMPTZ NOT NULL, submit_by TIMESTAMPTZ NOT NULL, review_by TIMESTAMPTZ NOT NULL, escalate_at TIMESTAMPTZ NOT NULL, hold_until TIMESTAMPTZ NOT NULL,
 state TEXT NOT NULL CHECK(state IN ('OPEN','REVIEW_PENDING','REVIEWED','EXPIRED','CANCELLED')),
 review_state TEXT NOT NULL CHECK(review_state IN ('PENDING','NEEDS_INFORMATION','REVIEWED','REJECTED')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 CHECK(created_at<expires_at AND created_at<initiate_by AND initiate_by<=submit_by AND submit_by<=expires_at AND created_at<hold_until AND hold_until<=expires_at AND review_by<=escalate_at)
);
CREATE TABLE cinenest_payments.bank_receipt_claim (
 claim_id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES cinenest_payments.bank_payment_request,
 generation INTEGER NOT NULL CHECK(generation>0), declared_amount_minor BIGINT NOT NULL CHECK(declared_amount_minor>=0),
 declared_paid_at TIMESTAMPTZ NOT NULL, declared_reference TEXT NOT NULL CHECK(length(declared_reference)<=100),
 submitted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), UNIQUE(request_id,generation), UNIQUE(request_id,claim_id)
);
CREATE TABLE cinenest_payments.bank_receipt_evidence (
 evidence_id UUID PRIMARY KEY, request_id UUID NOT NULL, claim_id UUID NOT NULL,
 FOREIGN KEY(request_id,claim_id) REFERENCES cinenest_payments.bank_receipt_claim(request_id,claim_id),
 position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 1), object_key UUID NOT NULL UNIQUE,preview_key UUID NOT NULL UNIQUE,
 state TEXT NOT NULL CHECK(state IN ('ALLOCATED','UPLOADING','QUARANTINED','READY','REJECTED','DELETION_PENDING','CLEANUP_FAILED','DELETED')),
 completed_at TIMESTAMPTZ, digest CHAR(64),mime TEXT CHECK(mime IN ('image/png','image/jpeg')),byte_size BIGINT CHECK(byte_size BETWEEN 1 AND 5242880),
 expires_at TIMESTAMPTZ NOT NULL,retain_until TIMESTAMPTZ NOT NULL,revision BIGINT NOT NULL DEFAULT 0,
 UNIQUE(claim_id,position),UNIQUE(request_id,evidence_id)
);
CREATE TABLE cinenest_payments.bank_review_decision (
 decision_id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES cinenest_payments.bank_payment_request,
 claim_id UUID, FOREIGN KEY(request_id,claim_id) REFERENCES cinenest_payments.bank_receipt_claim(request_id,claim_id),
 actor_id UUID NOT NULL,handover_to UUID,revision BIGINT NOT NULL,action TEXT NOT NULL CHECK(action IN ('REVIEWED','NEEDS_INFORMATION','REJECTED','ESCALATE','HANDOVER','DISPUTE')),
 reason TEXT NOT NULL CHECK(reason IN ('SYNTHETIC_REVIEW','CORRECTION_REQUIRED','UNREADABLE','OVERDUE','DISPUTED','STAFF_HANDOVER')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),CHECK((action='HANDOVER')=(handover_to IS NOT NULL)),UNIQUE(request_id,revision)
);
CREATE TABLE cinenest_payments.bank_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,content_hash CHAR(64) NOT NULL,response JSONB NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_payments.bank_work (
 work_id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES cinenest_payments.bank_payment_request,
 evidence_id UUID REFERENCES cinenest_payments.bank_receipt_evidence,kind TEXT NOT NULL CHECK(kind IN ('SCAN','NOTIFY','EXPIRY','ESCALATION','CLEANUP')),
 state TEXT NOT NULL CHECK(state IN ('PENDING','RUNNING','DONE','FAILED','DELIVERY_DISABLED')),
 due_at TIMESTAMPTZ NOT NULL,lease_until TIMESTAMPTZ,attempts INTEGER NOT NULL DEFAULT 0,updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(evidence_id,kind)
);
CREATE TABLE cinenest_payments.bank_audit (
 event_id UUID PRIMARY KEY,actor_id UUID,request_id UUID,action TEXT NOT NULL,outcome TEXT NOT NULL,
 correlation_id UUID NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_booking.booking_bank_request_hold (
 request_id UUID PRIMARY KEY,hold_id UUID NOT NULL UNIQUE REFERENCES cinenest_booking.reservation_hold,
 owner_id UUID NOT NULL,venue_id UUID NOT NULL,original_expires_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),CHECK(expires_at>created_at)
);
CREATE INDEX bank_review_queue ON cinenest_payments.bank_payment_request(venue_id,review_by,created_at);
CREATE INDEX bank_work_due ON cinenest_payments.bank_work(state,due_at);
CREATE INDEX bank_evidence_cleanup ON cinenest_payments.bank_receipt_evidence(retain_until,state);
CREATE FUNCTION cinenest_payments.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Bank history immutable' USING ERRCODE='23514';END $$;
CREATE TRIGGER immutable_policy BEFORE UPDATE OR DELETE ON cinenest_payments.bank_policy_revision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_claim BEFORE UPDATE OR DELETE ON cinenest_payments.bank_receipt_claim FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_review BEFORE UPDATE OR DELETE ON cinenest_payments.bank_review_decision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_command BEFORE UPDATE OR DELETE ON cinenest_payments.bank_command FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON cinenest_payments.bank_audit FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_conversion BEFORE UPDATE OR DELETE ON cinenest_booking.booking_bank_request_hold FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE FUNCTION cinenest_payments.guard_request() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (to_jsonb(NEW)-'revision'-'state'-'review_state')<>(to_jsonb(OLD)-'revision'-'state'-'review_state') OR NEW.revision<>OLD.revision+1 THEN RAISE EXCEPTION 'Bank request identity immutable' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER bank_request_guard BEFORE UPDATE ON cinenest_payments.bank_payment_request FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_request();
CREATE FUNCTION cinenest_payments.guard_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (to_jsonb(NEW)-'revision'-'state'-'digest'-'mime'-'byte_size'-'completed_at')<>(to_jsonb(OLD)-'revision'-'state'-'digest'-'mime'-'byte_size'-'completed_at') OR NEW.revision<>OLD.revision+1 OR (OLD.completed_at IS NOT NULL AND NEW.completed_at IS DISTINCT FROM OLD.completed_at) OR (OLD.digest IS NOT NULL AND (NEW.digest IS DISTINCT FROM OLD.digest OR NEW.mime IS DISTINCT FROM OLD.mime OR NEW.byte_size IS DISTINCT FROM OLD.byte_size)) THEN RAISE EXCEPTION 'Bank evidence identity immutable' USING ERRCODE='23514';END IF;RETURN NEW;
END $$;
CREATE TRIGGER bank_evidence_guard BEFORE UPDATE ON cinenest_payments.bank_receipt_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_evidence();

CREATE TRIGGER immutable_request_delete BEFORE DELETE ON cinenest_payments.bank_payment_request FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_evidence_delete BEFORE DELETE ON cinenest_payments.bank_receipt_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();

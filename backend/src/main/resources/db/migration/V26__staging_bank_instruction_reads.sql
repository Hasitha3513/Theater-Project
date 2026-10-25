-- Owner-authorized staging metadata only; no accounts, grants or financial records seeded.
CREATE TABLE cinenest_payments.bank_instruction_revision (
 instruction_revision_id UUID PRIMARY KEY, venue_id UUID NOT NULL,
 instruction_version TEXT NOT NULL CHECK(instruction_version ~ '^SYNTHETIC-[A-Za-z0-9_-]{1,80}$'),
 mode TEXT NOT NULL CHECK(mode='STAGING_ONLY'), reference_id UUID NOT NULL, reference_version UUID NOT NULL,
 content_sha256 TEXT NOT NULL CHECK(content_sha256 ~ '^[0-9a-f]{64}$'),
 effective_from TIMESTAMPTZ NOT NULL, effective_until TIMESTAMPTZ NOT NULL,
 proposer_id UUID NOT NULL, approver_id UUID NOT NULL, approval_reference UUID NOT NULL,
 approval_sha256 TEXT NOT NULL CHECK(approval_sha256 ~ '^[0-9a-f]{64}$'),
 approved_at TIMESTAMPTZ NOT NULL, registered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(proposer_id<>approver_id), CHECK(approved_at<=registered_at),
 CHECK(approved_at<=effective_from), CHECK(effective_from<effective_until),
 UNIQUE(venue_id,instruction_version), UNIQUE(instruction_revision_id,venue_id,instruction_version)
);
CREATE TABLE cinenest_payments.bank_instruction_request_binding (
 request_id UUID PRIMARY KEY REFERENCES cinenest_payments.bank_payment_request(request_id),
 venue_id UUID NOT NULL, instruction_revision_id UUID NOT NULL, instruction_version TEXT NOT NULL,
 bound_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(instruction_revision_id,venue_id,instruction_version)
 REFERENCES cinenest_payments.bank_instruction_revision(instruction_revision_id,venue_id,instruction_version)
);
CREATE FUNCTION cinenest_payments.guard_instruction_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.bound_at := clock_timestamp();
 PERFORM 1 FROM cinenest_payments.bank_payment_request r
 JOIN cinenest_payments.bank_instruction_revision i
 ON i.instruction_revision_id=NEW.instruction_revision_id AND i.venue_id=NEW.venue_id AND i.instruction_version=NEW.instruction_version
 WHERE r.request_id=NEW.request_id AND r.venue_id=NEW.venue_id AND r.instruction_version=NEW.instruction_version
 AND r.created_at>=i.registered_at AND i.effective_from<=NEW.bound_at AND NEW.bound_at<i.effective_until
 FOR KEY SHARE OF r;
 IF NOT FOUND THEN RAISE EXCEPTION 'Invalid staging instruction binding' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER validate_instruction_binding BEFORE INSERT ON cinenest_payments.bank_instruction_request_binding
 FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_instruction_binding();
CREATE TABLE cinenest_payments.bank_instruction_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, venue_id UUID, instruction_revision_id UUID, request_id UUID,
 action TEXT NOT NULL CHECK(action IN ('REVISION_REGISTERED','REQUEST_BOUND','DETAIL_READ','REQUEST_READ')),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('ADMITTED_FIXTURE','BOUND','SCOPED_READ','ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNBOUND_REQUEST','UNAVAILABLE')),
 observed_at TIMESTAMPTZ NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), correlation_id UUID NOT NULL,
 CHECK((outcome='ALLOW' AND actor_id IS NOT NULL AND venue_id IS NOT NULL AND instruction_revision_id IS NOT NULL AND (
 (action='REVISION_REGISTERED' AND reason_code='ADMITTED_FIXTURE' AND request_id IS NULL) OR
 (action='REQUEST_BOUND' AND reason_code='BOUND' AND request_id IS NOT NULL) OR
 (action='DETAIL_READ' AND reason_code='SCOPED_READ' AND request_id IS NULL) OR
 (action='REQUEST_READ' AND reason_code='SCOPED_READ' AND request_id IS NOT NULL))) OR
 (outcome='DENY' AND reason_code IN ('ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNBOUND_REQUEST','UNAVAILABLE')))
);
CREATE INDEX bank_instruction_binding_revision ON cinenest_payments.bank_instruction_request_binding(instruction_revision_id,request_id);
CREATE INDEX bank_instruction_audit_scope_time ON cinenest_payments.bank_instruction_audit(venue_id,recorded_at DESC,event_id DESC);
CREATE TRIGGER immutable_instruction_revision BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_revision
 FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_instruction_binding BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_request_binding
 FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_instruction_audit BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_audit
 FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();

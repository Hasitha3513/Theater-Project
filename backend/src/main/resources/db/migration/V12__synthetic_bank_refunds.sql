-- Additive, synthetic-only refunds. Never remove this balance guard on application rollback.
CREATE TABLE cinenest_payments.refund_obligation (
 refund_id UUID PRIMARY KEY, credit_id UUID NOT NULL, request_id UUID NOT NULL,
 FOREIGN KEY(credit_id,request_id) REFERENCES cinenest_payments.bank_credit_match(credit_id,request_id),
 owner_id UUID NOT NULL, venue_id UUID NOT NULL, amount_minor BIGINT NOT NULL CHECK(amount_minor>0),
 currency CHAR(3) NOT NULL CHECK(currency='LKR'), policy_hash CHAR(64) NOT NULL,
 policy_snapshot JSONB NOT NULL CHECK(policy_snapshot @> '{"synthetic":true,"realTransfers":false,"version":"SYNTHETIC-REFUND-V1"}'::jsonb AND policy_snapshot->>'originalPayerAccountId' IS NOT NULL AND policy_snapshot->>'originalPayerAccountId'=owner_id::TEXT AND policy_snapshot->>'maximumMinor' IS NOT NULL AND (policy_snapshot->>'maximumMinor')::NUMERIC>=amount_minor),
 state TEXT NOT NULL CHECK(state IN ('REQUESTED','UNDER_REVIEW','APPROVED','REJECTED','WITHDRAWN','ATTEMPT_PREPARED','AWAITING_EVIDENCE','SIMULATED_CONFIRMED','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), approver_id UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), synthetic BOOLEAN NOT NULL DEFAULT TRUE CHECK(synthetic)
);
CREATE TABLE cinenest_payments.refund_destination_version (
 refund_id UUID PRIMARY KEY REFERENCES cinenest_payments.refund_obligation, version INTEGER NOT NULL CHECK(version=1),
 destination_reference TEXT NOT NULL CHECK(destination_reference IN ('SYNTHETIC-PAYER-100','SYNTHETIC-PAYER-200','SYNTHETIC-PAYER-10000','SYNTHETIC-PAYER-50000')),
 proposer_id UUID NOT NULL, verifier_id UUID NOT NULL CHECK(verifier_id<>proposer_id), manifest_hash CHAR(64) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_payments.refund_decision (
 decision_id UUID PRIMARY KEY, refund_id UUID NOT NULL REFERENCES cinenest_payments.refund_obligation,
 actor_id UUID NOT NULL, action TEXT NOT NULL CHECK(action IN ('REVIEW','APPROVE','REJECT','WITHDRAW')), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 160),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_payments.refund_attempt (
 attempt_id UUID PRIMARY KEY, refund_id UUID NOT NULL REFERENCES cinenest_payments.refund_obligation,
 sequence INTEGER NOT NULL CHECK(sequence>0), destination_version INTEGER NOT NULL CHECK(destination_version=1),
 executor_id UUID NOT NULL, operation_key UUID NOT NULL UNIQUE,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), UNIQUE(refund_id,sequence)
);
CREATE TABLE cinenest_payments.refund_outgoing_evidence (
 evidence_id UUID PRIMARY KEY, attempt_id UUID NOT NULL REFERENCES cinenest_payments.refund_attempt,
 fixture_key TEXT NOT NULL CHECK(fixture_key IN ('SYNTHETIC-CONFIRMED','SYNTHETIC-FAILED','SYNTHETIC-UNKNOWN')),
 recorder_id UUID NOT NULL, phase TEXT NOT NULL CHECK(phase IN ('RECORDED','VERIFIED')), verifier_id UUID,
 CHECK((phase='RECORDED' AND verifier_id IS NULL) OR (phase='VERIFIED' AND verifier_id IS NOT NULL AND verifier_id<>recorder_id)), manifest_hash CHAR(64) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), UNIQUE(attempt_id,fixture_key,phase)
);
CREATE TABLE cinenest_payments.refund_command (
 actor_id UUID NOT NULL, command_key UUID NOT NULL, payload_hash CHAR(64) NOT NULL, result JSONB NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_payments.refund_work (
 refund_id UUID PRIMARY KEY REFERENCES cinenest_payments.refund_obligation,
 state TEXT NOT NULL CHECK(state IN ('REVIEW_REQUIRED','OUTGOING_EVIDENCE_REQUIRED','OUTGOING_VERIFICATION_REQUIRED','RECONCILIATION_REQUIRED','FAILED_REVIEW_REQUIRED','COMPLETE')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX refund_credit_commitments ON cinenest_payments.refund_obligation(credit_id,state);
CREATE INDEX refund_venue_queue ON cinenest_payments.refund_obligation(venue_id,created_at,refund_id);
CREATE FUNCTION cinenest_payments.refund_committed(credit UUID) RETURNS NUMERIC LANGUAGE sql STABLE AS $$
 SELECT COALESCE(sum(amount_minor),0) FROM cinenest_payments.refund_obligation WHERE credit_id=credit
 AND state IN ('APPROVED','ATTEMPT_PREPARED','AWAITING_EVIDENCE','SIMULATED_CONFIRMED','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED')
$$;
CREATE FUNCTION cinenest_payments.guard_refund() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE available NUMERIC; credit cinenest_payments.incoming_payment_credit; req cinenest_payments.bank_payment_request;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Immutable refund history'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-'state'-'revision'-'approver_id')<>(to_jsonb(OLD)-'state'-'revision'-'approver_id') OR NEW.revision<>OLD.revision+1
   OR (OLD.approver_id IS NOT NULL AND NEW.approver_id IS DISTINCT FROM OLD.approver_id) THEN RAISE EXCEPTION 'Immutable refund terms/revision'; END IF;
  IF NOT ((OLD.state='REQUESTED' AND NEW.state IN ('UNDER_REVIEW','REJECTED','WITHDRAWN'))
   OR (OLD.state='UNDER_REVIEW' AND NEW.state IN ('APPROVED','REJECTED','WITHDRAWN'))
   OR (OLD.state IN ('APPROVED','FAILED_CONFIRMED') AND NEW.state IN ('ATTEMPT_PREPARED','WITHDRAWN'))
   OR (OLD.state='ATTEMPT_PREPARED' AND NEW.state='AWAITING_EVIDENCE')
   OR (OLD.state='AWAITING_EVIDENCE' AND NEW.state IN ('AWAITING_EVIDENCE','SIMULATED_CONFIRMED','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED'))
   OR (OLD.state='UNKNOWN_RECONCILIATION_REQUIRED' AND NEW.state IN ('UNKNOWN_RECONCILIATION_REQUIRED','SIMULATED_CONFIRMED','FAILED_CONFIRMED'))) THEN RAISE EXCEPTION 'Unsafe refund transition'; END IF;
 ELSIF NEW.state<>'REQUESTED' OR NEW.revision<>0 OR NEW.approver_id IS NOT NULL THEN RAISE EXCEPTION 'Refund must start requested'; END IF;
 SELECT * INTO req FROM cinenest_payments.bank_payment_request WHERE request_id=NEW.request_id FOR UPDATE;
 SELECT * INTO credit FROM cinenest_payments.incoming_payment_credit WHERE credit_id=NEW.credit_id FOR UPDATE;
 IF req.owner_id<>NEW.owner_id OR req.venue_id<>NEW.venue_id OR credit.venue_id<>NEW.venue_id OR credit.currency<>NEW.currency
 OR credit.source_kind<>'SYNTHETIC_BANK' OR NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_verification WHERE credit_id=NEW.credit_id)
 THEN RAISE EXCEPTION 'Invalid synthetic refund source'; END IF;
 IF NEW.state IN ('REQUESTED','UNDER_REVIEW','APPROVED','ATTEMPT_PREPARED') AND EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_adjustment WHERE credit_id=NEW.credit_id) THEN RAISE EXCEPTION 'Disputed source'; END IF;
 IF NEW.state IN ('SIMULATED_CONFIRMED','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED') AND NOT EXISTS(
  SELECT 1 FROM cinenest_payments.refund_outgoing_evidence e JOIN cinenest_payments.refund_attempt a USING(attempt_id)
  WHERE e.phase='VERIFIED' AND a.refund_id=NEW.refund_id AND a.sequence=(SELECT max(sequence) FROM cinenest_payments.refund_attempt WHERE refund_id=NEW.refund_id)
  AND e.fixture_key=CASE NEW.state WHEN 'SIMULATED_CONFIRMED' THEN 'SYNTHETIC-CONFIRMED' WHEN 'FAILED_CONFIRMED' THEN 'SYNTHETIC-FAILED' ELSE 'SYNTHETIC-UNKNOWN' END
 ) THEN RAISE EXCEPTION 'Current independent outgoing evidence required'; END IF;
 IF NEW.state='APPROVED' AND (NEW.approver_id IS NULL OR NEW.approver_id=NEW.owner_id OR NOT EXISTS(SELECT 1 FROM cinenest_payments.refund_destination_version WHERE refund_id=NEW.refund_id)) THEN RAISE EXCEPTION 'Independent approval/destination required'; END IF;
 available:=credit.amount_minor-COALESCE((SELECT sum(amount_minor) FROM cinenest_payments.payment_allocation WHERE credit_id=NEW.credit_id),0)
 -COALESCE((SELECT sum(amount_minor) FROM cinenest_payments.refund_obligation WHERE credit_id=NEW.credit_id AND refund_id<>NEW.refund_id AND state IN ('APPROVED','ATTEMPT_PREPARED','AWAITING_EVIDENCE','SIMULATED_CONFIRMED','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED')),0);
 IF NEW.state NOT IN ('REJECTED','WITHDRAWN') AND NEW.amount_minor>available THEN RAISE EXCEPTION 'Refund exceeds uncommitted credit'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER refund_invariant BEFORE INSERT OR UPDATE OR DELETE ON cinenest_payments.refund_obligation FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_refund();
CREATE OR REPLACE FUNCTION cinenest_payments.guard_financial_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE available NUMERIC; due NUMERIC; verifier UUID; owner UUID;
BEGIN
 PERFORM 1 FROM cinenest_payments.bank_payment_request WHERE request_id=NEW.request_id FOR UPDATE;
 PERFORM 1 FROM cinenest_payments.incoming_payment_credit WHERE credit_id=NEW.credit_id FOR UPDATE;
 SELECT c.amount_minor-cinenest_payments.refund_committed(c.credit_id)-COALESCE((SELECT sum(a.amount_minor) FROM cinenest_payments.payment_allocation a WHERE a.credit_id=c.credit_id),0),v.verifier_id
 INTO available,verifier FROM cinenest_payments.incoming_payment_credit c JOIN cinenest_payments.bank_credit_verification v USING(credit_id)
 JOIN cinenest_payments.bank_credit_match m USING(credit_id) JOIN cinenest_payments.bank_payment_request r USING(request_id)
 WHERE c.credit_id=NEW.credit_id AND r.request_id=NEW.request_id AND c.venue_id=r.venue_id AND c.currency=NEW.currency;
 SELECT amount_minor-COALESCE((SELECT sum(amount_minor) FROM cinenest_payments.payment_allocation WHERE request_id=NEW.request_id),0),owner_id
 INTO due,owner FROM cinenest_payments.bank_payment_request WHERE request_id=NEW.request_id;
 IF available IS NULL OR NEW.amount_minor>available OR NEW.amount_minor>due OR NEW.actor_id=verifier OR NEW.actor_id=owner
 OR EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_adjustment WHERE credit_id=NEW.credit_id)
 THEN RAISE EXCEPTION 'Synthetic allocation invariant'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_refund_destination_version BEFORE UPDATE OR DELETE ON cinenest_payments.refund_destination_version FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_refund_decision BEFORE UPDATE OR DELETE ON cinenest_payments.refund_decision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_refund_attempt BEFORE UPDATE OR DELETE ON cinenest_payments.refund_attempt FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_refund_outgoing_evidence BEFORE UPDATE OR DELETE ON cinenest_payments.refund_outgoing_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_refund_command BEFORE UPDATE OR DELETE ON cinenest_payments.refund_command FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE FUNCTION cinenest_payments.guard_refund_fact() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r cinenest_payments.refund_obligation; a cinenest_payments.refund_attempt;
BEGIN
 IF TG_TABLE_NAME='refund_outgoing_evidence' THEN
  SELECT * INTO a FROM cinenest_payments.refund_attempt WHERE attempt_id=NEW.attempt_id;
  SELECT * INTO r FROM cinenest_payments.refund_obligation WHERE refund_id=a.refund_id FOR UPDATE;
  IF r.state NOT IN ('AWAITING_EVIDENCE','UNKNOWN_RECONCILIATION_REQUIRED') OR NEW.recorder_id<>a.executor_id OR NEW.verifier_id IN (r.owner_id,a.executor_id)
   OR (NEW.phase='VERIFIED' AND NOT EXISTS(SELECT 1 FROM cinenest_payments.refund_outgoing_evidence e WHERE e.attempt_id=NEW.attempt_id AND e.fixture_key=NEW.fixture_key AND e.phase='RECORDED' AND e.recorder_id=NEW.recorder_id AND e.manifest_hash=NEW.manifest_hash))
   OR a.sequence<>(SELECT max(sequence) FROM cinenest_payments.refund_attempt WHERE refund_id=r.refund_id) THEN RAISE EXCEPTION 'Independent current outgoing evidence required'; END IF;
 ELSE
  SELECT * INTO r FROM cinenest_payments.refund_obligation WHERE refund_id=NEW.refund_id FOR UPDATE;
  IF TG_TABLE_NAME='refund_attempt' THEN
   IF r.state NOT IN ('APPROVED','FAILED_CONFIRMED') OR NEW.executor_id IN (r.owner_id,r.approver_id) OR r.approver_id IS NULL
   OR NOT EXISTS(SELECT 1 FROM cinenest_payments.refund_destination_version WHERE refund_id=r.refund_id) THEN RAISE EXCEPTION 'Approved independent attempt required'; END IF;
  ELSIF TG_TABLE_NAME='refund_destination_version' THEN
   IF r.state<>'UNDER_REVIEW' OR NEW.proposer_id<>r.owner_id OR NEW.verifier_id=r.owner_id THEN RAISE EXCEPTION 'Independent current destination required'; END IF;
  ELSE
   IF NEW.actor_id=r.owner_id THEN RAISE EXCEPTION 'Independent decision required'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER refund_destination_invariant BEFORE INSERT ON cinenest_payments.refund_destination_version FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_refund_fact();
CREATE TRIGGER refund_attempt_invariant BEFORE INSERT ON cinenest_payments.refund_attempt FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_refund_fact();
CREATE TRIGGER refund_evidence_invariant BEFORE INSERT ON cinenest_payments.refund_outgoing_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_refund_fact();
CREATE TRIGGER refund_decision_invariant BEFORE INSERT ON cinenest_payments.refund_decision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_refund_fact();

-- Synthetic-only reconciliation. No bank connection or commercial activation.
CREATE TABLE cinenest_payments.bank_credit_source_revision (
 source_id UUID PRIMARY KEY, fixture_key TEXT NOT NULL UNIQUE CHECK(fixture_key LIKE 'SYNTHETIC-%'),
 manifest_hash CHAR(64) NOT NULL, synthetic BOOLEAN NOT NULL CHECK(synthetic), recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_payments.incoming_payment_credit (
 credit_id UUID PRIMARY KEY, source_id UUID NOT NULL REFERENCES cinenest_payments.bank_credit_source_revision,
 venue_id UUID NOT NULL, account_identity TEXT NOT NULL CHECK(account_identity LIKE 'SYNTHETIC-%'),
 movement_identity TEXT NOT NULL CHECK(movement_identity LIKE 'SYNTHETIC-%'),
 source_kind TEXT NOT NULL CHECK(source_kind IN ('SYNTHETIC_BANK','SYNTHETIC_GATEWAY')),
 amount_minor BIGINT NOT NULL CHECK(amount_minor>0), currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 credited_at TIMESTAMPTZ NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(account_identity,movement_identity)
);
CREATE TABLE cinenest_payments.bank_credit_verification (
 verification_id UUID PRIMARY KEY, credit_id UUID NOT NULL UNIQUE REFERENCES cinenest_payments.incoming_payment_credit,
 verifier_id UUID NOT NULL, status TEXT NOT NULL CHECK(status='SIMULATED_VERIFIED'), reason TEXT NOT NULL,
 verified_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_payments.bank_credit_match (
 credit_id UUID PRIMARY KEY REFERENCES cinenest_payments.incoming_payment_credit,
 request_id UUID NOT NULL REFERENCES cinenest_payments.bank_payment_request,
 actor_id UUID NOT NULL, reason TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), UNIQUE(credit_id,request_id)
);
CREATE TABLE cinenest_payments.payment_allocation (
 allocation_id UUID PRIMARY KEY, credit_id UUID NOT NULL, request_id UUID NOT NULL,
 FOREIGN KEY(credit_id,request_id) REFERENCES cinenest_payments.bank_credit_match(credit_id,request_id),
 amount_minor BIGINT NOT NULL CHECK(amount_minor>0), currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 actor_id UUID NOT NULL, command_key UUID NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(actor_id,command_key)
);
CREATE TABLE cinenest_payments.bank_credit_adjustment (
 adjustment_id UUID PRIMARY KEY, credit_id UUID NOT NULL REFERENCES cinenest_payments.incoming_payment_credit,
 source_key TEXT NOT NULL UNIQUE CHECK(source_key LIKE 'SYNTHETIC-REVERSAL-%'), proposer_id UUID NOT NULL,
 approver_id UUID, reason TEXT NOT NULL, proposed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 approved_at TIMESTAMPTZ, CHECK((approver_id IS NULL)=(approved_at IS NULL)), CHECK(approver_id IS NULL OR approver_id<>proposer_id)
);
CREATE TABLE cinenest_payments.bank_financial_exception (
 exception_id UUID PRIMARY KEY, venue_id UUID NOT NULL, request_id UUID REFERENCES cinenest_payments.bank_payment_request,
 credit_id UUID REFERENCES cinenest_payments.incoming_payment_credit, kind TEXT NOT NULL,
 assigned_to UUID, revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), due_at TIMESTAMPTZ NOT NULL,
 state TEXT NOT NULL CHECK(state IN ('OPEN','ESCALATED','RESOLVED')), recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX one_credit_exception ON cinenest_payments.bank_financial_exception(credit_id,kind) WHERE credit_id IS NOT NULL;
CREATE UNIQUE INDEX one_request_exception ON cinenest_payments.bank_financial_exception(request_id,kind) WHERE credit_id IS NULL;
CREATE TABLE cinenest_booking.booking (
 booking_id UUID PRIMARY KEY, request_id UUID NOT NULL UNIQUE, hold_id UUID NOT NULL UNIQUE REFERENCES cinenest_booking.reservation_hold,
 owner_id UUID NOT NULL, venue_id UUID NOT NULL, state TEXT NOT NULL CHECK(state='SIMULATED_CONFIRMED'),
 synthetic BOOLEAN NOT NULL CHECK(synthetic), confirmed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_booking.booking_revision (
 booking_id UUID NOT NULL REFERENCES cinenest_booking.booking, revision BIGINT NOT NULL CHECK(revision=1),
 quote_id UUID NOT NULL, quote_revision BIGINT NOT NULL CHECK(quote_revision>0), quote_hash CHAR(64) NOT NULL,
 amount_minor BIGINT NOT NULL CHECK(amount_minor>0), currency CHAR(3) NOT NULL CHECK(currency='LKR'), snapshot JSONB NOT NULL,
 PRIMARY KEY(booking_id,revision), CHECK(snapshot->>'synthetic'='true'), CHECK(snapshot->>'commercialIssuance'='false')
);
CREATE TABLE cinenest_booking.booking_command (
 request_id UUID PRIMARY KEY, command_key UUID NOT NULL UNIQUE, booking_id UUID NOT NULL REFERENCES cinenest_booking.booking,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE cinenest_booking.reservation_hold DROP CONSTRAINT reservation_hold_state_check;
ALTER TABLE cinenest_booking.reservation_hold ADD CHECK(state IN ('ACTIVE','RELEASED','EXPIRED','INVALIDATED','CONSUMED'));
ALTER TABLE cinenest_payments.bank_work DROP CONSTRAINT bank_work_kind_check;
ALTER TABLE cinenest_payments.bank_work ADD CHECK(kind IN ('SCAN','NOTIFY','EXPIRY','ESCALATION','CLEANUP','FINANCIAL'));
CREATE FUNCTION cinenest_payments.guard_financial_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE available NUMERIC; due NUMERIC; verifier UUID; owner UUID;
BEGIN
 PERFORM 1 FROM cinenest_payments.bank_payment_request WHERE request_id=NEW.request_id FOR UPDATE;
 PERFORM 1 FROM cinenest_payments.incoming_payment_credit WHERE credit_id=NEW.credit_id FOR UPDATE;
 SELECT c.amount_minor-COALESCE((SELECT sum(a.amount_minor) FROM cinenest_payments.payment_allocation a WHERE a.credit_id=c.credit_id),0),v.verifier_id
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
CREATE TRIGGER allocation_invariant BEFORE INSERT ON cinenest_payments.payment_allocation FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_financial_allocation();
CREATE FUNCTION cinenest_payments.guard_credit_adjustment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.approver_id IS NOT NULL OR (to_jsonb(OLD)-'approver_id'-'approved_at')<>(to_jsonb(NEW)-'approver_id'-'approved_at')
 OR NEW.approver_id IS NULL THEN RAISE EXCEPTION 'Immutable adjustment history'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER adjustment_history BEFORE UPDATE OR DELETE ON cinenest_payments.bank_credit_adjustment FOR EACH ROW EXECUTE FUNCTION cinenest_payments.guard_credit_adjustment();
CREATE TRIGGER immutable_bank_credit_source_revision BEFORE UPDATE OR DELETE ON cinenest_payments.bank_credit_source_revision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_incoming_payment_credit BEFORE UPDATE OR DELETE ON cinenest_payments.incoming_payment_credit FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_bank_credit_verification BEFORE UPDATE OR DELETE ON cinenest_payments.bank_credit_verification FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_bank_credit_match BEFORE UPDATE OR DELETE ON cinenest_payments.bank_credit_match FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_payment_allocation BEFORE UPDATE OR DELETE ON cinenest_payments.payment_allocation FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_booking BEFORE UPDATE OR DELETE ON cinenest_booking.booking FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_booking_revision BEFORE UPDATE OR DELETE ON cinenest_booking.booking_revision FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_booking_command BEFORE UPDATE OR DELETE ON cinenest_booking.booking_command FOR EACH ROW EXECUTE FUNCTION cinenest_payments.immutable_history();

ALTER TABLE cinenest_payments.bank_audit ADD COLUMN financial_resource_id UUID;
ALTER TABLE cinenest_payments.bank_audit ADD COLUMN venue_id UUID;
ALTER TABLE cinenest_payments.bank_audit ADD COLUMN reason TEXT;

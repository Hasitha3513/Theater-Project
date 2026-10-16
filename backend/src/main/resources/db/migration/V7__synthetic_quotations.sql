-- Synthetic-only quotation history. No commercial policy or production seed.
CREATE TABLE cinenest_pricing.quotation_policy_revision (
 policy_id UUID PRIMARY KEY, profile VARCHAR(64) NOT NULL CHECK(profile='SYNTHETIC_FIXED_MINOR_V1'),
 currency CHAR(3) NOT NULL CHECK(currency='LKR'), synthetic BOOLEAN NOT NULL CHECK(synthetic),
 tax_minor BIGINT NOT NULL CHECK(tax_minor>=0), bank_fee_minor BIGINT NOT NULL CHECK(bank_fee_minor=0),
 gateway_fee_minor BIGINT NOT NULL CHECK(gateway_fee_minor=0), approval_reference TEXT NOT NULL CHECK(length(trim(approval_reference))>0),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_pricing.quotation (
 quote_id UUID PRIMARY KEY, owner_id UUID NOT NULL, hold_id UUID NOT NULL UNIQUE,
 venue_id UUID NOT NULL, room_id UUID NOT NULL, package_id UUID NOT NULL REFERENCES cinenest_pricing.package,
 current_revision BIGINT NOT NULL CHECK(current_revision>0), created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_pricing.quotation_revision (
 quote_id UUID NOT NULL REFERENCES cinenest_pricing.quotation, revision BIGINT NOT NULL CHECK(revision>0),
 version_id UUID NOT NULL REFERENCES cinenest_pricing.package_version, policy_id UUID NOT NULL REFERENCES cinenest_pricing.quotation_policy_revision,
 hold_revision BIGINT NOT NULL CHECK(hold_revision>=0), expires_at TIMESTAMPTZ NOT NULL,
 payment_method VARCHAR(8) NOT NULL CHECK(payment_method IN ('BANK','GATEWAY')),
 snapshot JSONB NOT NULL, content_hash CHAR(64) NOT NULL CHECK(content_hash ~ '^[0-9a-f]{64}$'),
 total_minor BIGINT NOT NULL CHECK(total_minor>0), recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(quote_id,revision), CHECK(expires_at>recorded_at)
);
CREATE TABLE cinenest_pricing.quotation_line (
 quote_id UUID NOT NULL, revision BIGINT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
 kind VARCHAR(24) NOT NULL, label VARCHAR(200) NOT NULL, quantity BIGINT NOT NULL CHECK(quantity>=0),
 unit_minor BIGINT NOT NULL CHECK(unit_minor>=0), amount_minor BIGINT NOT NULL CHECK(amount_minor>=0),
 reference JSONB NOT NULL, PRIMARY KEY(quote_id,revision,position),
 FOREIGN KEY(quote_id,revision) REFERENCES cinenest_pricing.quotation_revision,
 CHECK(amount_minor=quantity::numeric*unit_minor)
);
CREATE TABLE cinenest_pricing.quotation_acceptance (
 quote_id UUID PRIMARY KEY, revision BIGINT NOT NULL, actor_id UUID NOT NULL, content_hash CHAR(64) NOT NULL,
 synthetic BOOLEAN NOT NULL CHECK(synthetic), accepted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(quote_id,revision) REFERENCES cinenest_pricing.quotation_revision
);
CREATE TABLE cinenest_pricing.quotation_command (
 actor_id UUID NOT NULL, command_key UUID NOT NULL, command_hash CHAR(64) NOT NULL,
 quote_id UUID NOT NULL REFERENCES cinenest_pricing.quotation, revision BIGINT NOT NULL,
 action VARCHAR(8) NOT NULL CHECK(action IN ('CREATE','REVISE','ACCEPT')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), PRIMARY KEY(actor_id,command_key),
 FOREIGN KEY(quote_id,revision) REFERENCES cinenest_pricing.quotation_revision
);
CREATE TABLE cinenest_pricing.quotation_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, quote_id UUID REFERENCES cinenest_pricing.quotation,
 revision BIGINT, action VARCHAR(32) NOT NULL, correlation_id UUID NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX quotation_owner ON cinenest_pricing.quotation(owner_id);
CREATE INDEX quotation_attempt ON cinenest_pricing.quotation_audit(actor_id,recorded_at);
CREATE TRIGGER immutable_quote_policy BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_policy_revision FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_quote_revision BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_revision FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_quote_line BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_line FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_quote_acceptance BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_acceptance FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_quote_command BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_command FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_quote_audit BEFORE UPDATE OR DELETE ON cinenest_pricing.quotation_audit FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE FUNCTION cinenest_pricing.guard_quote_header() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.quote_id<>OLD.quote_id OR NEW.owner_id<>OLD.owner_id OR NEW.hold_id<>OLD.hold_id OR NEW.venue_id<>OLD.venue_id OR NEW.room_id<>OLD.room_id OR NEW.package_id<>OLD.package_id OR NEW.created_at<>OLD.created_at OR NEW.current_revision<OLD.current_revision OR NEW.current_revision>OLD.current_revision+1 THEN
  RAISE EXCEPTION 'Quotation identity and original hold immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.current_revision<>OLD.current_revision AND EXISTS(SELECT 1 FROM cinenest_pricing.quotation_acceptance WHERE quote_id=OLD.quote_id) THEN
  RAISE EXCEPTION 'Accepted quotation immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER quote_header_guard BEFORE UPDATE ON cinenest_pricing.quotation FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_quote_header();
CREATE FUNCTION cinenest_pricing.guard_quote_acceptance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_pricing.quotation q JOIN cinenest_pricing.quotation_revision r ON r.quote_id=q.quote_id AND r.revision=q.current_revision WHERE q.quote_id=NEW.quote_id AND q.owner_id=NEW.actor_id AND r.revision=NEW.revision AND r.content_hash=NEW.content_hash AND r.expires_at>clock_timestamp()) THEN
  RAISE EXCEPTION 'Acceptance requires exact current owned unexpired revision' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER quote_acceptance_guard BEFORE INSERT ON cinenest_pricing.quotation_acceptance FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_quote_acceptance();

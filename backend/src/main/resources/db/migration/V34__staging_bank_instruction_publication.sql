-- PROPOSAL ONLY. Not a Flyway migration. NOT approved for execution.
-- Three Payments-owned append-only journals; no account data, seeds, grants or historical rewrites.
CREATE TABLE cinenest_payments.bank_instruction_proposal (
 proposal_id UUID PRIMARY KEY, venue_id UUID NOT NULL, proposer_id UUID NOT NULL,
 instruction_version TEXT NOT NULL CHECK(instruction_version ~ '^SYNTHETIC-[A-Za-z0-9_-]{1,80}$'),
 reference_id UUID NOT NULL, reference_version UUID NOT NULL,
 content_sha256 TEXT NOT NULL CHECK(content_sha256 ~ '^[0-9a-f]{64}$'),
 effective_from TIMESTAMPTZ NOT NULL, effective_until TIMESTAMPTZ NOT NULL,
 snapshot_sha256 TEXT NOT NULL CHECK(snapshot_sha256 ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(effective_from<effective_until), CHECK(created_at<=effective_from),
 CHECK(reference_id='38c19812-2358-3b67-8f44-61cb0e4a51f2'::uuid AND ((reference_version='37dcd14c-03dc-3cb8-89b7-c193f189453f'::uuid AND content_sha256='a999aefc7ca927c04b5e94c040910ca4f789751b03491e5d15ad6a421e27f8d6') OR (reference_version='518a353d-1cdc-340a-8f10-9677dfe592f4'::uuid AND content_sha256='e90aa98fa5782e1a39554760dddef34c2e9c0c680d3043bf40e2d3addf871952'))),
 UNIQUE(proposal_id,venue_id)
);
CREATE TABLE cinenest_payments.bank_instruction_publication_command (
 command_id UUID PRIMARY KEY, actor_id UUID NOT NULL, command_key UUID NOT NULL,
 venue_id UUID NOT NULL, proposal_id UUID NOT NULL, action TEXT NOT NULL CHECK(action IN ('PROPOSE','REVIEW','PUBLISH')),
 payload_sha256 TEXT NOT NULL CHECK(payload_sha256 ~ '^[0-9a-f]{64}$'),
 expected_revision BIGINT NOT NULL, revision BIGINT NOT NULL, state TEXT NOT NULL,
 reviewer_id UUID, published_revision_id UUID REFERENCES cinenest_payments.bank_instruction_revision(instruction_revision_id),
 occurred_at TIMESTAMPTZ NOT NULL, correlation_id UUID NOT NULL, audit_id UUID NOT NULL UNIQUE,
 UNIQUE(actor_id,command_key), UNIQUE(proposal_id,revision), UNIQUE(published_revision_id),
 UNIQUE(command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id,audit_id),
 FOREIGN KEY(proposal_id,venue_id) REFERENCES cinenest_payments.bank_instruction_proposal(proposal_id,venue_id),
 CHECK((action='PROPOSE' AND expected_revision=0 AND revision=1 AND state='PROPOSED' AND reviewer_id IS NULL AND published_revision_id IS NULL)
 OR (action='REVIEW' AND expected_revision=1 AND revision=2 AND state='REVIEWED' AND reviewer_id IS NOT NULL AND reviewer_id=actor_id AND published_revision_id IS NULL)
 OR (action='PUBLISH' AND expected_revision=2 AND revision=3 AND state='PUBLISHED' AND reviewer_id IS NOT NULL AND reviewer_id=actor_id AND published_revision_id IS NOT NULL))
);
CREATE TABLE cinenest_payments.bank_instruction_publication_audit (
 audit_id UUID PRIMARY KEY, command_id UUID UNIQUE, actor_id UUID, venue_id UUID, proposal_id UUID,
 action TEXT NOT NULL CHECK(action IN ('PROPOSE','REVIEW','PUBLISH','READ','INVALID')),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('STAGING_COMMAND','SCOPED_READ','ACCESS_DENIED','CONFLICT','INVALID_REQUEST','UNAVAILABLE')),
 occurred_at TIMESTAMPTZ NOT NULL, correlation_id UUID NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(audit_id,command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id),
 FOREIGN KEY(proposal_id,venue_id) REFERENCES cinenest_payments.bank_instruction_proposal(proposal_id,venue_id),
 CHECK((outcome='ALLOW' AND actor_id IS NOT NULL AND venue_id IS NOT NULL AND proposal_id IS NOT NULL AND
 ((action IN ('PROPOSE','REVIEW','PUBLISH') AND reason_code='STAGING_COMMAND' AND command_id IS NOT NULL)
 OR (action='READ' AND reason_code='SCOPED_READ' AND command_id IS NULL)))
 OR (outcome='DENY' AND command_id IS NULL AND reason_code IN ('ACCESS_DENIED','CONFLICT','INVALID_REQUEST','UNAVAILABLE')))
);
ALTER TABLE cinenest_payments.bank_instruction_publication_command ADD CONSTRAINT publication_command_audit_fk
 FOREIGN KEY(audit_id,command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id)
 REFERENCES cinenest_payments.bank_instruction_publication_audit(audit_id,command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id)
 DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE cinenest_payments.bank_instruction_publication_audit ADD CONSTRAINT publication_audit_command_fk
 FOREIGN KEY(command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id,audit_id)
 REFERENCES cinenest_payments.bank_instruction_publication_command(command_id,actor_id,venue_id,proposal_id,action,occurred_at,correlation_id,audit_id)
 DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX instruction_proposal_scope ON cinenest_payments.bank_instruction_proposal(venue_id,created_at DESC,proposal_id);
CREATE INDEX instruction_publication_audit_scope ON cinenest_payments.bank_instruction_publication_audit(venue_id,recorded_at DESC,audit_id);
CREATE FUNCTION cinenest_payments.validate_instruction_publication_command() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p cinenest_payments.bank_instruction_proposal%ROWTYPE; previous cinenest_payments.bank_instruction_publication_command%ROWTYPE;
BEGIN
 SELECT * INTO p FROM cinenest_payments.bank_instruction_proposal WHERE proposal_id=NEW.proposal_id AND venue_id=NEW.venue_id FOR UPDATE;
 IF NOT FOUND OR NEW.occurred_at>clock_timestamp() OR NEW.occurred_at>p.effective_from THEN RAISE EXCEPTION 'Invalid staging proposal context' USING ERRCODE='23514';END IF;
 IF NEW.action='PROPOSE' THEN
  IF NEW.actor_id<>p.proposer_id OR EXISTS(SELECT 1 FROM cinenest_payments.bank_instruction_publication_command WHERE proposal_id=p.proposal_id) THEN RAISE EXCEPTION 'Invalid staging proposal command' USING ERRCODE='23514';END IF;
 ELSE
  SELECT * INTO previous FROM cinenest_payments.bank_instruction_publication_command WHERE proposal_id=p.proposal_id ORDER BY revision DESC LIMIT 1;
  IF NOT FOUND OR NEW.actor_id=p.proposer_id OR previous.revision<>NEW.expected_revision THEN RAISE EXCEPTION 'Independent current staging decision required' USING ERRCODE='23514';END IF;
  IF NEW.action='REVIEW' AND previous.state<>'PROPOSED' THEN RAISE EXCEPTION 'Pending proposal required' USING ERRCODE='23514';END IF;
  IF NEW.action='PUBLISH' THEN
   IF previous.state<>'REVIEWED' OR previous.reviewer_id IS DISTINCT FROM NEW.actor_id THEN RAISE EXCEPTION 'Reviewed publisher required' USING ERRCODE='23514';END IF;
   PERFORM 1 FROM cinenest_payments.bank_instruction_revision i WHERE i.instruction_revision_id=NEW.published_revision_id AND i.venue_id=p.venue_id
    AND i.instruction_version=p.instruction_version AND i.mode='STAGING_ONLY' AND i.reference_id=p.reference_id AND i.reference_version=p.reference_version
    AND i.content_sha256=p.content_sha256 AND i.effective_from=p.effective_from AND i.effective_until=p.effective_until
    AND i.proposer_id=p.proposer_id AND i.approver_id=NEW.actor_id AND i.approval_reference=NEW.command_id AND i.approval_sha256=p.snapshot_sha256 AND i.approved_at=NEW.occurred_at;
   IF NOT FOUND THEN RAISE EXCEPTION 'Published snapshot mismatch' USING ERRCODE='23514';END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER validate_publication_command BEFORE INSERT ON cinenest_payments.bank_instruction_publication_command
 FOR EACH ROW EXECUTE FUNCTION cinenest_payments.validate_instruction_publication_command();
CREATE FUNCTION cinenest_payments.require_instruction_proposal_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_instruction_publication_command WHERE proposal_id=NEW.proposal_id AND venue_id=NEW.venue_id AND actor_id=NEW.proposer_id AND action='PROPOSE' AND revision=1)
 THEN RAISE EXCEPTION 'Mandatory proposal receipt required' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER instruction_proposal_receipt AFTER INSERT ON cinenest_payments.bank_instruction_proposal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_payments.require_instruction_proposal_receipt();
-- No stored history can be truncated. Empty reset is allowed only with pre-held locks on all three journals.
CREATE FUNCTION cinenest_payments.instruction_publication_empty_truncate_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE locked INTEGER;
BEGIN
 SELECT count(DISTINCT relation) INTO locked FROM pg_locks WHERE pid=pg_backend_pid() AND granted AND mode='AccessExclusiveLock'
 AND relation IN ('cinenest_payments.bank_instruction_proposal'::regclass,'cinenest_payments.bank_instruction_publication_command'::regclass,'cinenest_payments.bank_instruction_publication_audit'::regclass);
 IF TG_OP<>'TRUNCATE' OR TG_LEVEL<>'STATEMENT' OR current_setting('transaction_isolation')<>'read committed' OR locked<>3
 THEN RAISE EXCEPTION 'Empty journal reset requires all locks and read committed' USING ERRCODE='23514';END IF;
 IF EXISTS(SELECT 1 FROM cinenest_payments.bank_instruction_proposal) OR EXISTS(SELECT 1 FROM cinenest_payments.bank_instruction_publication_command) OR EXISTS(SELECT 1 FROM cinenest_payments.bank_instruction_publication_audit)
 THEN RAISE EXCEPTION 'Publication history immutable' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER immutable_instruction_proposal BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_proposal FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_instruction_publication_command BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_publication_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER immutable_instruction_publication_audit BEFORE UPDATE OR DELETE ON cinenest_payments.bank_instruction_publication_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.immutable_history();
CREATE TRIGGER instruction_proposal_no_history_truncate BEFORE TRUNCATE ON cinenest_payments.bank_instruction_proposal FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.instruction_publication_empty_truncate_guard();
CREATE TRIGGER instruction_command_no_history_truncate BEFORE TRUNCATE ON cinenest_payments.bank_instruction_publication_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.instruction_publication_empty_truncate_guard();
CREATE TRIGGER instruction_audit_no_history_truncate BEFORE TRUNCATE ON cinenest_payments.bank_instruction_publication_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_payments.instruction_publication_empty_truncate_guard();

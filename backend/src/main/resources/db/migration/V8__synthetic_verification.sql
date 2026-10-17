-- Synthetic manual review only. No production policy seed or cross-owner tables.
CREATE SCHEMA cinenest_verification;
CREATE TABLE cinenest_verification.verification_policy_revision (
 policy_id UUID PRIMARY KEY, version TEXT NOT NULL UNIQUE, content_hash CHAR(64) NOT NULL,
 policy JSONB NOT NULL, synthetic BOOLEAN NOT NULL CHECK(synthetic), recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_verification.verification_request (
 request_id UUID PRIMARY KEY, owner_id UUID NOT NULL, venue_id UUID NOT NULL, quote_id UUID NOT NULL,
 policy_id UUID NOT NULL REFERENCES cinenest_verification.verification_policy_revision,
 previous_id UUID UNIQUE REFERENCES cinenest_verification.verification_request,
 generation INTEGER NOT NULL CHECK(generation>0), revision BIGINT NOT NULL CHECK(revision>=0),
 state TEXT NOT NULL CHECK(state IN ('NOT_SUBMITTED','SUBMITTED','PROCESSING','REVIEW_REQUIRED','VERIFIED','REJECTED','EXPIRED','REVOKED')),
 superseded BOOLEAN NOT NULL DEFAULT FALSE, consent_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), valid_until TIMESTAMPTZ,
 UNIQUE(quote_id,generation)
);
CREATE TABLE cinenest_verification.identity_evidence (
 evidence_id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES cinenest_verification.verification_request,
 document_type TEXT NOT NULL CHECK(document_type IN ('NIC','PASSPORT')), position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 1),
 object_key UUID NOT NULL UNIQUE, preview_key UUID NOT NULL UNIQUE,
 state TEXT NOT NULL CHECK(state IN ('ALLOCATED','UPLOADING','QUARANTINED','READY','REJECTED','DELETION_PENDING','CLEANUP_FAILED','DELETED')),
 digest CHAR(64), mime TEXT CHECK(mime IN ('image/jpeg','image/png')), byte_size BIGINT CHECK(byte_size BETWEEN 1 AND 5242880),
 expires_at TIMESTAMPTZ NOT NULL, retain_until TIMESTAMPTZ NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 UNIQUE(request_id,position), UNIQUE(request_id,evidence_id)
);
CREATE TABLE cinenest_verification.verification_decision (
 decision_id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES cinenest_verification.verification_request,
 revision BIGINT NOT NULL, actor_id UUID NOT NULL, verdict TEXT NOT NULL CHECK(verdict IN ('VERIFIED','REJECTED','REVOKED')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('SYNTHETIC_MATCH_18_PLUS','INCONSISTENT','UNREADABLE','EXPIRED_EVIDENCE','CORRECTION_REQUIRED','WITHDRAWN')),
 policy_id UUID NOT NULL REFERENCES cinenest_verification.verification_policy_revision,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), valid_until TIMESTAMPTZ NOT NULL,
 UNIQUE(request_id,revision), UNIQUE(request_id,decision_id)
);
CREATE TABLE cinenest_verification.decision_evidence (
 request_id UUID NOT NULL, decision_id UUID NOT NULL, evidence_id UUID NOT NULL,
 PRIMARY KEY(decision_id,evidence_id), FOREIGN KEY(request_id,decision_id) REFERENCES cinenest_verification.verification_decision(request_id,decision_id),
 FOREIGN KEY(request_id,evidence_id) REFERENCES cinenest_verification.identity_evidence(request_id,evidence_id)
);
CREATE TABLE cinenest_verification.verification_command (
 actor_id UUID NOT NULL, command_key UUID NOT NULL, content_hash CHAR(64) NOT NULL,
 response JSONB NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_verification.verification_work (
 work_id UUID PRIMARY KEY, evidence_id UUID NOT NULL UNIQUE REFERENCES cinenest_verification.identity_evidence,
 state TEXT NOT NULL CHECK(state IN ('PENDING','RUNNING','DONE','FAILED')), lease_until TIMESTAMPTZ,
 attempts INTEGER NOT NULL DEFAULT 0, due_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_verification.verification_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, request_id UUID, action TEXT NOT NULL,
 outcome TEXT NOT NULL, correlation_id UUID NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX verification_queue ON cinenest_verification.verification_request(venue_id,state,created_at);
CREATE INDEX verification_cleanup ON cinenest_verification.identity_evidence(retain_until,state);
CREATE FUNCTION cinenest_verification.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Verification history is immutable' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_policy BEFORE UPDATE OR DELETE ON cinenest_verification.verification_policy_revision FOR EACH ROW EXECUTE FUNCTION cinenest_verification.immutable_history();
CREATE TRIGGER immutable_decision BEFORE UPDATE OR DELETE ON cinenest_verification.verification_decision FOR EACH ROW EXECUTE FUNCTION cinenest_verification.immutable_history();
CREATE TRIGGER immutable_decision_evidence BEFORE UPDATE OR DELETE ON cinenest_verification.decision_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_verification.immutable_history();
CREATE TRIGGER immutable_command BEFORE UPDATE OR DELETE ON cinenest_verification.verification_command FOR EACH ROW EXECUTE FUNCTION cinenest_verification.immutable_history();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON cinenest_verification.verification_audit FOR EACH ROW EXECUTE FUNCTION cinenest_verification.immutable_history();
CREATE FUNCTION cinenest_verification.guard_request_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.request_id<>OLD.request_id OR NEW.owner_id<>OLD.owner_id OR NEW.venue_id<>OLD.venue_id OR NEW.quote_id<>OLD.quote_id OR NEW.policy_id<>OLD.policy_id OR NEW.previous_id IS DISTINCT FROM OLD.previous_id OR NEW.generation<>OLD.generation OR NEW.consent_at<>OLD.consent_at OR NEW.created_at<>OLD.created_at OR NEW.revision<>OLD.revision+1 OR (OLD.superseded AND NOT NEW.superseded) THEN
  RAISE EXCEPTION 'Verification identity/consent/history cannot be replaced' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER request_identity_guard BEFORE UPDATE ON cinenest_verification.verification_request FOR EACH ROW EXECUTE FUNCTION cinenest_verification.guard_request_identity();
CREATE FUNCTION cinenest_verification.guard_evidence_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.evidence_id<>OLD.evidence_id OR NEW.request_id<>OLD.request_id OR NEW.object_key<>OLD.object_key OR NEW.preview_key<>OLD.preview_key OR NEW.document_type<>OLD.document_type OR NEW.position<>OLD.position OR NEW.retain_until>OLD.retain_until OR NEW.expires_at>OLD.expires_at THEN
  RAISE EXCEPTION 'Evidence identity and deadlines cannot be broadened' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_identity_guard BEFORE UPDATE ON cinenest_verification.identity_evidence FOR EACH ROW EXECUTE FUNCTION cinenest_verification.guard_evidence_identity();

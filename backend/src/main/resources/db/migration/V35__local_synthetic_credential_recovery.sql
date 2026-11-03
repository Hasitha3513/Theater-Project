-- Identity-owned persistence source only. No runtime adapter, reset endpoint, login reader,
-- session filter, permission assignment or activation is supplied by this migration.
-- A dedicated exact-venue issuer grant is necessary; approval text/inventory grants confer none.
-- Capabilities are valid at locked atomic-consumption admission, NOT necessarily at commit.
CREATE TABLE cinenest_identity.manual_recovery_capability (
 capability_id UUID PRIMARY KEY,
 issuance_command_id UUID NOT NULL UNIQUE,
 account_id UUID NOT NULL REFERENCES cinenest_identity.manual_customer(account_id),
 actor_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
 issuer_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
 issuer_grant_id UUID NOT NULL REFERENCES cinenest_identity.access_grant(grant_id),
 issuer_grant_revision BIGINT NOT NULL CHECK(issuer_grant_revision>=0),
 venue_id UUID NOT NULL,
 expected_account_revision BIGINT NOT NULL CHECK(expected_account_revision>=0),
 expected_credential_revision BIGINT NOT NULL CHECK(expected_credential_revision>=0),
 token_digest CHAR(64) NOT NULL UNIQUE CHECK(token_digest ~ '^[0-9a-f]{64}$'),
 issued_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 issuer_reference VARCHAR(200) NOT NULL CHECK(length(trim(issuer_reference))>0),
 approval_reference VARCHAR(200) NOT NULL CHECK(length(trim(approval_reference))>0),
 mode VARCHAR(24) NOT NULL DEFAULT 'LOCAL_SYNTHETIC_ONLY' CHECK(mode='LOCAL_SYNTHETIC_ONLY'),
 CHECK(expires_at>issued_at AND expires_at<=issued_at+INTERVAL '15 minutes')
);
CREATE TABLE cinenest_identity.manual_credential_version (
 account_id UUID NOT NULL REFERENCES cinenest_identity.manual_customer(account_id),
 credential_revision BIGINT NOT NULL CHECK(credential_revision>0),
 previous_revision BIGINT NOT NULL CHECK(previous_revision>=0),
 expected_account_revision BIGINT NOT NULL CHECK(expected_account_revision>=0),
 command_id UUID NOT NULL UNIQUE,
 capability_id UUID NOT NULL UNIQUE REFERENCES cinenest_identity.manual_recovery_capability(capability_id),
 actor_id UUID NOT NULL REFERENCES cinenest_identity.account(account_id),
 venue_id UUID NOT NULL,
 password_hash VARCHAR(256) NOT NULL CHECK(password_hash ~ '^\{pbkdf2-v1\}[0-9a-f]{96}$'),
 audit_id UUID NOT NULL UNIQUE,
 admitted_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(account_id,credential_revision),
 CHECK(credential_revision=previous_revision+1),
 UNIQUE(audit_id,command_id,capability_id,account_id,actor_id,venue_id,
        previous_revision,credential_revision,expected_account_revision,admitted_at)
);
CREATE TABLE cinenest_identity.manual_credential_recovery_audit (
 audit_id UUID PRIMARY KEY,
 command_id UUID NOT NULL UNIQUE,
 capability_id UUID NOT NULL UNIQUE,
 account_id UUID NOT NULL,
 actor_id UUID NOT NULL,
 venue_id UUID NOT NULL,
 previous_revision BIGINT NOT NULL,
 credential_revision BIGINT NOT NULL,
 expected_account_revision BIGINT NOT NULL,
 admitted_at TIMESTAMPTZ NOT NULL,
 action VARCHAR(32) NOT NULL CHECK(action='LOCAL_CREDENTIAL_ROTATED'),
 session_invalidation_epoch BIGINT NOT NULL,
 invalidate_all_previous_sessions BOOLEAN NOT NULL CHECK(invalidate_all_previous_sessions),
 CHECK(session_invalidation_epoch=credential_revision),
 UNIQUE(audit_id,command_id,capability_id,account_id,actor_id,venue_id,
        previous_revision,credential_revision,expected_account_revision,admitted_at),
 FOREIGN KEY(audit_id,command_id,capability_id,account_id,actor_id,venue_id,
             previous_revision,credential_revision,expected_account_revision,admitted_at)
 REFERENCES cinenest_identity.manual_credential_version
            (audit_id,command_id,capability_id,account_id,actor_id,venue_id,
             previous_revision,credential_revision,expected_account_revision,admitted_at)
 DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE cinenest_identity.manual_credential_version
 ADD CONSTRAINT manual_credential_version_audit_binding
 FOREIGN KEY(audit_id,command_id,capability_id,account_id,actor_id,venue_id,
             previous_revision,credential_revision,expected_account_revision,admitted_at)
 REFERENCES cinenest_identity.manual_credential_recovery_audit
            (audit_id,command_id,capability_id,account_id,actor_id,venue_id,
             previous_revision,credential_revision,expected_account_revision,admitted_at)
 DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION cinenest_identity.reject_local_recovery_history_mutation()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 RAISE EXCEPTION 'Immutable local recovery history' USING ERRCODE='23514';
END $$;

CREATE FUNCTION cinenest_identity.check_local_recovery_capability()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE wanted INTEGER; locked INTEGER; head BIGINT;
        grant_row cinenest_identity.access_grant%ROWTYPE;
BEGIN
 IF current_setting('transaction_isolation')<>'read committed'
    OR NEW.account_id IS NULL OR NEW.actor_id IS NULL OR NEW.issuer_id IS NULL
    OR NEW.venue_id IS NULL OR NEW.issuer_grant_id IS NULL
    OR NEW.mode IS DISTINCT FROM 'LOCAL_SYNTHETIC_ONLY'
 THEN RAISE EXCEPTION 'Local capability rejected' USING ERRCODE='23514';END IF;
 SELECT count(DISTINCT id) INTO wanted FROM unnest(ARRAY[NEW.account_id,NEW.actor_id,NEW.issuer_id]) ids(id);
 PERFORM account_id FROM cinenest_identity.account
  WHERE account_id IN(NEW.account_id,NEW.actor_id,NEW.issuer_id) ORDER BY account_id FOR NO KEY UPDATE;
 GET DIAGNOSTICS locked=ROW_COUNT;
 IF locked<>wanted OR EXISTS(SELECT 1 FROM cinenest_identity.account
      WHERE account_id IN(NEW.account_id,NEW.actor_id,NEW.issuer_id)
      AND (synthetic IS DISTINCT FROM TRUE OR eligibility IS DISTINCT FROM 'ELIGIBLE'))
 THEN RAISE EXCEPTION 'Synthetic identity required' USING ERRCODE='23514';END IF;
 PERFORM account_id FROM cinenest_identity.account_scope
  WHERE venue_id=NEW.venue_id AND account_id IN(NEW.account_id,NEW.actor_id,NEW.issuer_id)
  ORDER BY account_id FOR SHARE;
 GET DIAGNOSTICS locked=ROW_COUNT;
 IF locked<>wanted THEN RAISE EXCEPTION 'Exact venue required' USING ERRCODE='23514';END IF;
 PERFORM account_id FROM cinenest_identity.manual_customer
  WHERE account_id=NEW.account_id AND verified_at IS NOT NULL FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Verified manual identity required' USING ERRCODE='23514';END IF;
 SELECT * INTO grant_row FROM cinenest_identity.access_grant WHERE grant_id=NEW.issuer_grant_id FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Issuer authority required' USING ERRCODE='23514';END IF;
 NEW.issued_at=clock_timestamp();
 IF grant_row.account_id IS DISTINCT FROM NEW.issuer_id
    OR grant_row.permission IS DISTINCT FROM 'identity.credential.recovery.issue'
    OR grant_row.scope_kind IS DISTINCT FROM 'VENUE' OR grant_row.venue_id IS DISTINCT FROM NEW.venue_id
    OR grant_row.state IS DISTINCT FROM 'ACTIVE' OR grant_row.revision IS DISTINCT FROM NEW.issuer_grant_revision
    OR grant_row.valid_from>NEW.issued_at OR grant_row.valid_until<=NEW.issued_at
 THEN RAISE EXCEPTION 'Issuer authority required' USING ERRCODE='23514';END IF;
 SELECT coalesce(max(credential_revision),0) INTO head FROM cinenest_identity.manual_credential_version WHERE account_id=NEW.account_id;
 IF NEW.expected_account_revision IS DISTINCT FROM (SELECT revision FROM cinenest_identity.account WHERE account_id=NEW.account_id)
    OR NEW.expected_credential_revision IS DISTINCT FROM head
    OR NEW.expires_at IS NULL OR NEW.expires_at<=NEW.issued_at
    OR NEW.expires_at>NEW.issued_at+INTERVAL '15 minutes'
 THEN RAISE EXCEPTION 'Capability revision or expiry rejected' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION cinenest_identity.check_local_credential_append()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE cap cinenest_identity.manual_recovery_capability%ROWTYPE;
        grant_row cinenest_identity.access_grant%ROWTYPE;
        wanted INTEGER; locked INTEGER; head BIGINT; expected_audit BOOLEAN;
BEGIN
 IF NEW.capability_id IS NULL OR current_setting('transaction_isolation')<>'read committed'
 THEN RAISE EXCEPTION 'Credential append rejected' USING ERRCODE='23514';END IF;
 SELECT * INTO cap FROM cinenest_identity.manual_recovery_capability WHERE capability_id=NEW.capability_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Capability required' USING ERRCODE='23514';END IF;
 IF TG_WHEN='BEFORE' THEN
  SELECT count(DISTINCT id) INTO wanted FROM unnest(ARRAY[cap.account_id,cap.actor_id,cap.issuer_id]) ids(id);
  PERFORM account_id FROM cinenest_identity.account
   WHERE account_id IN(cap.account_id,cap.actor_id,cap.issuer_id) ORDER BY account_id FOR NO KEY UPDATE;
  GET DIAGNOSTICS locked=ROW_COUNT;
  IF locked<>wanted OR EXISTS(SELECT 1 FROM cinenest_identity.account
       WHERE account_id IN(cap.account_id,cap.actor_id,cap.issuer_id)
       AND (synthetic IS DISTINCT FROM TRUE OR eligibility IS DISTINCT FROM 'ELIGIBLE'))
  THEN RAISE EXCEPTION 'Synthetic identity required' USING ERRCODE='23514';END IF;
  PERFORM account_id FROM cinenest_identity.account_scope
   WHERE venue_id=cap.venue_id AND account_id IN(cap.account_id,cap.actor_id,cap.issuer_id)
   ORDER BY account_id FOR SHARE;
  GET DIAGNOSTICS locked=ROW_COUNT;
  IF locked<>wanted THEN RAISE EXCEPTION 'Exact venue required' USING ERRCODE='23514';END IF;
  PERFORM account_id FROM cinenest_identity.manual_customer
   WHERE account_id=cap.account_id AND verified_at IS NOT NULL FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Verified manual identity required' USING ERRCODE='23514';END IF;
  SELECT * INTO grant_row FROM cinenest_identity.access_grant WHERE grant_id=cap.issuer_grant_id FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Issuer authority required' USING ERRCODE='23514';END IF;
  -- Admission is sampled only after every required row/authority lock has been acquired.
  NEW.admitted_at=clock_timestamp();
  IF grant_row.account_id IS DISTINCT FROM cap.issuer_id
     OR grant_row.permission IS DISTINCT FROM 'identity.credential.recovery.issue'
     OR grant_row.scope_kind IS DISTINCT FROM 'VENUE' OR grant_row.venue_id IS DISTINCT FROM cap.venue_id
     OR grant_row.state IS DISTINCT FROM 'ACTIVE' OR grant_row.revision IS DISTINCT FROM cap.issuer_grant_revision
     OR grant_row.valid_from>NEW.admitted_at OR grant_row.valid_until<=NEW.admitted_at
  THEN RAISE EXCEPTION 'Issuer authority required' USING ERRCODE='23514';END IF;
  SELECT coalesce(max(credential_revision),0) INTO head FROM cinenest_identity.manual_credential_version WHERE account_id=cap.account_id;
  IF NEW.previous_revision IS DISTINCT FROM head
     OR EXISTS(SELECT 1 FROM cinenest_identity.manual_credential_version WHERE capability_id=NEW.capability_id)
  THEN RAISE EXCEPTION 'Stale or consumed capability' USING ERRCODE='23514';END IF;
 END IF;
 -- Repeated by the deferred trigger: static binding/admission proof, never expiry-at-commit.
 IF cap.mode IS DISTINCT FROM 'LOCAL_SYNTHETIC_ONLY'
    OR NEW.account_id IS DISTINCT FROM cap.account_id OR NEW.actor_id IS DISTINCT FROM cap.actor_id
    OR NEW.venue_id IS DISTINCT FROM cap.venue_id
    OR NEW.expected_account_revision IS DISTINCT FROM cap.expected_account_revision
    OR NEW.previous_revision IS DISTINCT FROM cap.expected_credential_revision
    OR NEW.expected_account_revision IS DISTINCT FROM (SELECT revision FROM cinenest_identity.account WHERE account_id=cap.account_id)
    OR NEW.admitted_at IS NULL OR NEW.admitted_at<cap.issued_at OR NEW.admitted_at>=cap.expires_at
 THEN RAISE EXCEPTION 'Credential binding rejected' USING ERRCODE='23514';END IF;
 IF TG_WHEN='BEFORE' THEN RETURN NEW;END IF;
 SELECT EXISTS(SELECT 1 FROM cinenest_identity.manual_credential_recovery_audit a
  WHERE a.audit_id=NEW.audit_id AND a.command_id=NEW.command_id AND a.capability_id=NEW.capability_id
   AND a.account_id=NEW.account_id AND a.actor_id=NEW.actor_id AND a.venue_id=NEW.venue_id
   AND a.previous_revision=NEW.previous_revision AND a.credential_revision=NEW.credential_revision
   AND a.expected_account_revision=NEW.expected_account_revision AND a.admitted_at=NEW.admitted_at
   AND a.session_invalidation_epoch=NEW.credential_revision AND a.invalidate_all_previous_sessions)
 INTO expected_audit;
 IF expected_audit IS DISTINCT FROM TRUE
 THEN RAISE EXCEPTION 'Recovery audit and invalidation epoch required' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;

CREATE FUNCTION cinenest_identity.local_recovery_empty_truncate_guard()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE locked INTEGER;
BEGIN
 SELECT count(DISTINCT relation) INTO locked FROM pg_locks
  WHERE pid=pg_backend_pid() AND granted AND mode='AccessExclusiveLock'
  AND relation IN('cinenest_identity.manual_recovery_capability'::regclass,
                 'cinenest_identity.manual_credential_version'::regclass,
                 'cinenest_identity.manual_credential_recovery_audit'::regclass);
 IF TG_OP<>'TRUNCATE' OR TG_LEVEL<>'STATEMENT' OR current_setting('transaction_isolation')<>'read committed' OR locked<>3
 THEN RAISE EXCEPTION 'Empty recovery reset requires all locks and read committed' USING ERRCODE='23514';END IF;
 IF EXISTS(SELECT 1 FROM cinenest_identity.manual_recovery_capability)
    OR EXISTS(SELECT 1 FROM cinenest_identity.manual_credential_version)
    OR EXISTS(SELECT 1 FROM cinenest_identity.manual_credential_recovery_audit)
 THEN RAISE EXCEPTION 'Recovery history immutable' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;

CREATE TRIGGER manual_recovery_capability_rows_immutable BEFORE UPDATE OR DELETE
 ON cinenest_identity.manual_recovery_capability FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.reject_local_recovery_history_mutation();
CREATE TRIGGER manual_credential_version_rows_immutable BEFORE UPDATE OR DELETE
 ON cinenest_identity.manual_credential_version FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.reject_local_recovery_history_mutation();
CREATE TRIGGER manual_credential_recovery_audit_rows_immutable BEFORE UPDATE OR DELETE
 ON cinenest_identity.manual_credential_recovery_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.reject_local_recovery_history_mutation();
CREATE TRIGGER manual_recovery_capability_truncate_immutable BEFORE TRUNCATE
 ON cinenest_identity.manual_recovery_capability FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.local_recovery_empty_truncate_guard();
CREATE TRIGGER manual_credential_version_truncate_immutable BEFORE TRUNCATE
 ON cinenest_identity.manual_credential_version FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.local_recovery_empty_truncate_guard();
CREATE TRIGGER manual_credential_recovery_audit_truncate_immutable BEFORE TRUNCATE
 ON cinenest_identity.manual_credential_recovery_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.local_recovery_empty_truncate_guard();
CREATE TRIGGER manual_recovery_capability_issue_guard BEFORE INSERT
 ON cinenest_identity.manual_recovery_capability FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_local_recovery_capability();
CREATE TRIGGER manual_credential_append_admission BEFORE INSERT
 ON cinenest_identity.manual_credential_version FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_local_credential_append();
CREATE CONSTRAINT TRIGGER manual_credential_append_integrity AFTER INSERT
 ON cinenest_identity.manual_credential_version DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_local_credential_append();

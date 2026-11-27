-- Identity-owned customer credentials. No accounts, policy approvals, keys, providers or grants are seeded.
-- Exact SQL execution admission is required before applying this new candidate in any environment.
CREATE TABLE cinenest_identity.customer_key_binding (
 singleton BOOLEAN PRIMARY KEY DEFAULT true CHECK(singleton),
 key_version VARCHAR(128) NOT NULL,
 lookup_fingerprint CHAR(64) NOT NULL CHECK(lookup_fingerprint ~ '^[0-9a-f]{64}$'),
 command_fingerprint CHAR(64) NOT NULL CHECK(command_fingerprint ~ '^[0-9a-f]{64}$'),
 encryption_fingerprint CHAR(64) NOT NULL CHECK(encryption_fingerprint ~ '^[0-9a-f]{64}$'),
 address_policy VARCHAR(64) NOT NULL CHECK(address_policy='ASCII_CASEFOLD_V1')
);
CREATE TABLE cinenest_identity.customer_credential (
 account_id UUID PRIMARY KEY REFERENCES cinenest_identity.account,
 last_command UUID NOT NULL,
 protected_address UUID NOT NULL UNIQUE,
 address_lookup CHAR(64) NOT NULL CHECK(address_lookup ~ '^[0-9a-f]{64}$'),
 address_ciphertext BYTEA NOT NULL CHECK(octet_length(address_ciphertext) BETWEEN 28 AND 4096),
 address_key_version VARCHAR(128) NOT NULL CHECK(length(address_key_version)>0),
 state VARCHAR(16) NOT NULL CHECK(state IN('PENDING','VERIFIED','SUSPENDED')),
 password_hash VARCHAR(256) NOT NULL CHECK(password_hash ~ '^\{pbkdf2-v1\}[0-9a-f]{96}$'),
 credential_epoch BIGINT NOT NULL CHECK(credential_epoch>=0),
 policy_revision VARCHAR(128) NOT NULL CHECK(length(policy_revision)>0),
 privacy_revision VARCHAR(128) NOT NULL CHECK(length(privacy_revision)>0),
 delivery_revision VARCHAR(128) NOT NULL CHECK(length(delivery_revision)>0),
 verified_at TIMESTAMPTZ,
 registration_expires_at TIMESTAMPTZ NOT NULL,
 abandoned_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(isfinite(created_at) AND isfinite(registration_expires_at) AND registration_expires_at>created_at AND registration_expires_at<=created_at+interval '7 days'),
 CHECK(abandoned_at IS NULL OR (state='PENDING' AND isfinite(abandoned_at) AND abandoned_at>=registration_expires_at)),
 CHECK((state='PENDING' AND verified_at IS NULL AND credential_epoch=0) OR
       (state IN('VERIFIED','SUSPENDED') AND verified_at IS NOT NULL AND isfinite(verified_at) AND verified_at>=created_at))
);
CREATE UNIQUE INDEX customer_active_address ON cinenest_identity.customer_credential(address_lookup) WHERE abandoned_at IS NULL;
CREATE TABLE cinenest_identity.customer_command (
 command_id UUID PRIMARY KEY,
 request_digest CHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 account_id UUID REFERENCES cinenest_identity.customer_credential,
 action VARCHAR(20) NOT NULL CHECK(action IN('REGISTER','VERIFY','LOGIN','RECOVERY_REQUEST','RECOVER')),
 status INTEGER NOT NULL CHECK(status IN(202,204,400,401)),
 credential_epoch BIGINT NOT NULL CHECK(credential_epoch>=0),
 account_revision BIGINT NOT NULL CHECK(account_revision>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE cinenest_identity.customer_credential ADD CONSTRAINT customer_head_command_reference FOREIGN KEY(last_command) REFERENCES cinenest_identity.customer_command DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE cinenest_identity.customer_registration_retirement (
 account_id UUID PRIMARY KEY REFERENCES cinenest_identity.customer_credential,
 replacement_command UUID NOT NULL UNIQUE REFERENCES cinenest_identity.customer_command DEFERRABLE INITIALLY DEFERRED,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(occurred_at))
);
CREATE TABLE cinenest_identity.customer_capability (
 capability_id UUID PRIMARY KEY,
 account_id UUID NOT NULL REFERENCES cinenest_identity.customer_credential,
 purpose VARCHAR(8) NOT NULL CHECK(purpose IN('VERIFY','RECOVER')),
 token_digest CHAR(64) NOT NULL UNIQUE CHECK(token_digest ~ '^[0-9a-f]{64}$'),
 account_revision BIGINT NOT NULL CHECK(account_revision>=0),
 credential_epoch BIGINT NOT NULL CHECK(credential_epoch>=0),
 policy_revision VARCHAR(128) NOT NULL,
 privacy_revision VARCHAR(128) NOT NULL,
 delivery_revision VARCHAR(128) NOT NULL,
 issued_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 consumed_command UUID UNIQUE REFERENCES cinenest_identity.customer_command DEFERRABLE INITIALLY DEFERRED,
 revoked_at TIMESTAMPTZ,
 CHECK(isfinite(issued_at) AND isfinite(expires_at) AND expires_at>issued_at AND expires_at<=issued_at+interval '1 hour'),
 CHECK((consumed_at IS NULL)=(consumed_command IS NULL)),
 CHECK(consumed_at IS NULL OR (isfinite(consumed_at) AND consumed_at>=issued_at AND consumed_at<expires_at)),
 CHECK(revoked_at IS NULL OR (isfinite(revoked_at) AND revoked_at>=issued_at)),
 CHECK(consumed_at IS NULL OR revoked_at IS NULL),
 UNIQUE(capability_id,account_id,purpose,expires_at,policy_revision)
);
CREATE TABLE cinenest_identity.customer_outbox (
 outbox_id UUID PRIMARY KEY,
 command_id UUID NOT NULL UNIQUE REFERENCES cinenest_identity.customer_command DEFERRABLE INITIALLY DEFERRED,
 account_id UUID NOT NULL REFERENCES cinenest_identity.customer_credential,
 capability_id UUID NOT NULL UNIQUE REFERENCES cinenest_identity.customer_capability,
 purpose VARCHAR(8) NOT NULL CHECK(purpose IN('VERIFY','RECOVER')),
 sealed_payload BYTEA NOT NULL CHECK(octet_length(sealed_payload) BETWEEN 28 AND 4096),
 key_version VARCHAR(128) NOT NULL CHECK(length(key_version)>0),
 policy_revision VARCHAR(128) NOT NULL CHECK(length(policy_revision)>0),
 expires_at TIMESTAMPTZ NOT NULL CHECK(isfinite(expires_at)),
 state VARCHAR(12) NOT NULL CHECK(state IN('PENDING','SENDING','ACCEPTED','UNKNOWN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(capability_id,account_id,purpose,expires_at,policy_revision) REFERENCES cinenest_identity.customer_capability(capability_id,account_id,purpose,expires_at,policy_revision)
);
CREATE TABLE cinenest_identity.customer_audit (
 event_id UUID PRIMARY KEY,
 command_id UUID NOT NULL UNIQUE REFERENCES cinenest_identity.customer_command,
 account_id UUID REFERENCES cinenest_identity.customer_credential,
 action VARCHAR(20) NOT NULL CHECK(action IN('REGISTER','VERIFY','LOGIN','RECOVERY_REQUEST','RECOVER')),
 status INTEGER NOT NULL CHECK(status IN(202,204,400,401)),
 credential_epoch BIGINT NOT NULL CHECK(credential_epoch>=0),
 account_revision BIGINT NOT NULL CHECK(account_revision>=0),
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_identity.customer_abuse (
 action VARCHAR(20) NOT NULL CHECK(action IN('REGISTER','VERIFY','LOGIN','RECOVERY_REQUEST','RECOVER')),
 subject_digest VARCHAR(64) NOT NULL CHECK(subject_digest='GLOBAL' OR subject_digest ~ '^[0-9a-f]{64}$'),
 bucket BIGINT NOT NULL CHECK(bucket>=0),
 attempts INTEGER NOT NULL CHECK(attempts>0),
 PRIMARY KEY(action,subject_digest,bucket)
);
CREATE TABLE cinenest_identity.customer_lease (
 lease_id UUID PRIMARY KEY,
 command_id UUID NOT NULL,
 account_id UUID,
 action VARCHAR(20) NOT NULL CHECK(action IN('REGISTER','VERIFY','LOGIN','RECOVERY_REQUEST','RECOVER')),
 hashes BOOLEAN NOT NULL,
 issued_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 CHECK(isfinite(issued_at) AND isfinite(expires_at) AND expires_at>issued_at AND expires_at<=issued_at+interval '30 seconds'),
 CHECK(consumed_at IS NULL OR (consumed_at>=issued_at AND consumed_at<expires_at))
);
CREATE INDEX customer_active_capability ON cinenest_identity.customer_capability(account_id,purpose);
CREATE INDEX customer_active_hash_lease ON cinenest_identity.customer_lease(expires_at) WHERE hashes AND consumed_at IS NULL;

CREATE FUNCTION cinenest_identity.guard_customer_head() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_identity.account WHERE account_id=NEW.account_id AND NOT synthetic) THEN
  RAISE EXCEPTION 'Customer identity requires its own nonsynthetic account' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.state<>'PENDING' OR NEW.credential_epoch<>0 OR NEW.verified_at IS NOT NULL THEN RAISE EXCEPTION 'Pending customer required' USING ERRCODE='23514'; END IF;
 ELSE
  IF ROW(NEW.account_id,NEW.protected_address,NEW.address_lookup,NEW.address_ciphertext,NEW.address_key_version,NEW.policy_revision,NEW.privacy_revision,NEW.delivery_revision,NEW.created_at,NEW.registration_expires_at)
   IS DISTINCT FROM ROW(OLD.account_id,OLD.protected_address,OLD.address_lookup,OLD.address_ciphertext,OLD.address_key_version,OLD.policy_revision,OLD.privacy_revision,OLD.delivery_revision,OLD.created_at,OLD.registration_expires_at)
   OR OLD.abandoned_at IS NOT NULL
   OR NOT ((OLD.state='PENDING' AND NEW.state='PENDING' AND NEW.abandoned_at IS NOT NULL AND NEW.abandoned_at>=OLD.registration_expires_at AND OLD.registration_expires_at<=clock_timestamp() AND NEW.abandoned_at<=clock_timestamp() AND NEW.password_hash=OLD.password_hash AND NEW.credential_epoch=OLD.credential_epoch AND NEW.verified_at IS NULL)
      OR (OLD.state='PENDING' AND NEW.abandoned_at IS NULL AND OLD.registration_expires_at>clock_timestamp() AND NEW.state='VERIFIED' AND NEW.credential_epoch=OLD.credential_epoch AND NEW.password_hash=OLD.password_hash AND NEW.verified_at IS NOT NULL)
      OR (OLD.state='VERIFIED' AND NEW.abandoned_at IS NULL AND NEW.state='VERIFIED' AND NEW.credential_epoch=OLD.credential_epoch+1 AND NEW.verified_at=OLD.verified_at))
  THEN RAISE EXCEPTION 'Invalid customer credential transition' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER customer_head_transition BEFORE INSERT OR UPDATE ON cinenest_identity.customer_credential FOR EACH ROW EXECUTE FUNCTION cinenest_identity.guard_customer_head();
CREATE FUNCTION cinenest_identity.guard_customer_capability() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF ROW(NEW.capability_id,NEW.account_id,NEW.purpose,NEW.token_digest,NEW.account_revision,NEW.credential_epoch,NEW.policy_revision,NEW.privacy_revision,NEW.delivery_revision,NEW.issued_at,NEW.expires_at)
  IS DISTINCT FROM ROW(OLD.capability_id,OLD.account_id,OLD.purpose,OLD.token_digest,OLD.account_revision,OLD.credential_epoch,OLD.policy_revision,OLD.privacy_revision,OLD.delivery_revision,OLD.issued_at,OLD.expires_at)
  OR OLD.consumed_at IS NOT NULL OR OLD.revoked_at IS NOT NULL
  OR NOT ((NEW.consumed_at IS NOT NULL AND NEW.revoked_at IS NULL) OR (NEW.revoked_at IS NOT NULL AND NEW.consumed_at IS NULL))
 THEN RAISE EXCEPTION 'Immutable single-use customer capability' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER customer_capability_consumption BEFORE UPDATE ON cinenest_identity.customer_capability FOR EACH ROW EXECUTE FUNCTION cinenest_identity.guard_customer_capability();
CREATE FUNCTION cinenest_identity.guard_customer_outbox() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF ROW(NEW.outbox_id,NEW.command_id,NEW.account_id,NEW.capability_id,NEW.purpose,NEW.sealed_payload,NEW.key_version,NEW.policy_revision,NEW.expires_at,NEW.created_at)
  IS DISTINCT FROM ROW(OLD.outbox_id,OLD.command_id,OLD.account_id,OLD.capability_id,OLD.purpose,OLD.sealed_payload,OLD.key_version,OLD.policy_revision,OLD.expires_at,OLD.created_at)
  OR NOT ((OLD.state='PENDING' AND NEW.state='SENDING') OR (OLD.state='SENDING' AND NEW.state IN('ACCEPTED','UNKNOWN')))
 THEN RAISE EXCEPTION 'Invalid customer delivery transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER customer_outbox_transition BEFORE UPDATE ON cinenest_identity.customer_outbox FOR EACH ROW EXECUTE FUNCTION cinenest_identity.guard_customer_outbox();
CREATE FUNCTION cinenest_identity.protect_customer_history() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE populated BOOLEAN;
BEGIN
 IF TG_OP='TRUNCATE' AND current_setting('transaction_isolation')='read committed' THEN
  -- TRUNCATE holds ACCESS EXCLUSIVE before this observation; concurrent INSERT cannot escape it.
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I.%I)',TG_TABLE_SCHEMA,TG_TABLE_NAME) INTO populated;
  IF NOT populated THEN RETURN NULL; END IF;
 END IF;
 RAISE EXCEPTION 'Customer credential history is protected' USING ERRCODE='23514';
END $$;
CREATE TRIGGER customer_credential_no_delete BEFORE DELETE ON cinenest_identity.customer_credential FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_capability_no_delete BEFORE DELETE ON cinenest_identity.customer_capability FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_outbox_no_delete BEFORE DELETE ON cinenest_identity.customer_outbox FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_command_immutable BEFORE UPDATE OR DELETE ON cinenest_identity.customer_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_audit_immutable BEFORE UPDATE OR DELETE ON cinenest_identity.customer_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_credential_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_credential FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_capability_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_capability FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_outbox_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_outbox FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_command_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_audit_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE FUNCTION cinenest_identity.check_customer_head_receipt() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF NEW.abandoned_at IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_registration_retirement r JOIN cinenest_identity.customer_command c ON c.command_id=r.replacement_command WHERE r.account_id=NEW.account_id AND c.action='REGISTER' AND c.status=202 AND c.account_id<>NEW.account_id) THEN RAISE EXCEPTION 'Retired registration requires its replacement receipt' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_command c WHERE c.command_id=NEW.last_command AND c.account_id=NEW.account_id AND c.credential_epoch=NEW.credential_epoch AND ((NEW.state='PENDING' AND c.action='REGISTER' AND c.status=202 AND c.account_revision=1) OR(NEW.state='VERIFIED' AND c.action IN('VERIFY','RECOVER') AND c.status=204 AND EXISTS(SELECT 1 FROM cinenest_identity.customer_capability cap WHERE cap.consumed_command=c.command_id AND cap.account_id=NEW.account_id AND cap.purpose=c.action)))) THEN RAISE EXCEPTION 'Customer credential head requires its exact receipt' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER customer_head_receipt_binding AFTER INSERT OR UPDATE ON cinenest_identity.customer_credential DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_customer_head_receipt();
CREATE FUNCTION cinenest_identity.check_customer_command_links() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF TG_TABLE_NAME='customer_command' THEN
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_audit a WHERE a.command_id=NEW.command_id AND a.account_id IS NOT DISTINCT FROM NEW.account_id AND a.action=NEW.action AND a.status=NEW.status AND a.credential_epoch=NEW.credential_epoch AND a.account_revision=NEW.account_revision) THEN
   RAISE EXCEPTION 'Customer command requires its exact audit' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='customer_audit' THEN
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_command c WHERE c.command_id=NEW.command_id AND c.account_id IS NOT DISTINCT FROM NEW.account_id AND c.action=NEW.action AND c.status=NEW.status AND c.credential_epoch=NEW.credential_epoch AND c.account_revision=NEW.account_revision) THEN
   RAISE EXCEPTION 'Customer audit binding mismatch' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='customer_outbox' THEN
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_command c JOIN cinenest_identity.customer_capability cap ON cap.capability_id=NEW.capability_id WHERE c.command_id=NEW.command_id AND c.account_id=NEW.account_id AND c.status=202 AND c.account_revision=cap.account_revision AND c.credential_epoch=cap.credential_epoch AND ((c.action='REGISTER' AND NEW.purpose='VERIFY') OR(c.action='RECOVERY_REQUEST' AND NEW.purpose='RECOVER'))) THEN
   RAISE EXCEPTION 'Customer outbox command binding mismatch' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='customer_capability' AND NEW.consumed_at IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM cinenest_identity.customer_command c WHERE c.command_id=NEW.consumed_command AND c.account_id=NEW.account_id AND c.action=NEW.purpose AND c.status=204 AND c.account_revision=NEW.account_revision+1 AND c.credential_epoch=NEW.credential_epoch+CASE WHEN NEW.purpose='RECOVER' THEN 1 ELSE 0 END) THEN
   RAISE EXCEPTION 'Capability consumption command mismatch' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER customer_command_audit_binding AFTER INSERT ON cinenest_identity.customer_command DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_customer_command_links();
CREATE CONSTRAINT TRIGGER customer_audit_command_binding AFTER INSERT ON cinenest_identity.customer_audit DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_customer_command_links();
CREATE CONSTRAINT TRIGGER customer_outbox_command_binding AFTER INSERT ON cinenest_identity.customer_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_customer_command_links();
CREATE CONSTRAINT TRIGGER customer_capability_command_binding AFTER UPDATE ON cinenest_identity.customer_capability DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.check_customer_command_links();
CREATE TRIGGER customer_key_binding_immutable BEFORE UPDATE OR DELETE ON cinenest_identity.customer_key_binding FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_key_binding_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_key_binding FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_retirement_immutable BEFORE UPDATE OR DELETE ON cinenest_identity.customer_registration_retirement FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
CREATE TRIGGER customer_retirement_no_truncate BEFORE TRUNCATE ON cinenest_identity.customer_registration_retirement FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_history();
REVOKE ALL ON cinenest_identity.customer_registration_retirement FROM PUBLIC;
REVOKE ALL ON cinenest_identity.customer_key_binding FROM PUBLIC;
REVOKE ALL ON cinenest_identity.customer_credential,cinenest_identity.customer_capability,cinenest_identity.customer_command,cinenest_identity.customer_outbox,cinenest_identity.customer_audit,cinenest_identity.customer_abuse,cinenest_identity.customer_lease FROM PUBLIC;

-- Local synthetic customer credentials only. No external identities, memberships or grants are created.
CREATE TABLE cinenest_identity.manual_customer (
 account_id UUID PRIMARY KEY REFERENCES cinenest_identity.account(account_id) ON DELETE CASCADE,
 email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email) AND email ~ '^[a-z0-9][a-z0-9._+-]{0,63}@example[.]test$'),
 password_hash VARCHAR(256) NOT NULL CHECK (password_hash ~ '^\{pbkdf2-v1\}[0-9a-f]{96}$'),
 verification_digest CHAR(64) NOT NULL UNIQUE CHECK (verification_digest ~ '^[0-9a-f]{64}$'),
 verification_expires_at TIMESTAMPTZ NOT NULL,
 verified_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK (verification_expires_at > created_at), CHECK (verified_at IS NULL OR verified_at >= created_at)
);
CREATE TABLE cinenest_identity.manual_throttle (
 action VARCHAR(12) NOT NULL CHECK (action IN ('REGISTER','LOGIN','VERIFY','GLOBAL')),
 subject_digest CHAR(64) NOT NULL,
 bucket BIGINT NOT NULL,
 attempts INTEGER NOT NULL CHECK (attempts > 0),
 PRIMARY KEY (action,subject_digest,bucket)
);
CREATE TABLE cinenest_identity.manual_audit (
 event_id UUID PRIMARY KEY, account_id UUID,
 action VARCHAR(12) NOT NULL CHECK (action IN ('REGISTER','LOGIN','VERIFY')),
 outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('ALLOW','DENY')),
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE FUNCTION cinenest_identity.protect_manual_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME = 'manual_audit' THEN RAISE EXCEPTION 'Immutable authentication audit'; END IF;
 IF NEW.account_id <> OLD.account_id OR NEW.email <> OLD.email OR NEW.password_hash <> OLD.password_hash
    OR NEW.verification_digest <> OLD.verification_digest OR NEW.verification_expires_at <> OLD.verification_expires_at
    OR NEW.created_at <> OLD.created_at OR OLD.verified_at IS NOT NULL OR NEW.verified_at IS NULL
 THEN RAISE EXCEPTION 'Immutable manual identity binding'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER manual_customer_binding BEFORE UPDATE ON cinenest_identity.manual_customer
 FOR EACH ROW EXECUTE FUNCTION cinenest_identity.protect_manual_history();
CREATE TRIGGER manual_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_identity.manual_audit
 FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_manual_history();

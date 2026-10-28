-- Local synthetic self-profile only; no credentials, eligibility or grants change.
CREATE TABLE cinenest_identity.customer_profile (
 account_id UUID PRIMARY KEY REFERENCES cinenest_identity.account(account_id),
 full_name VARCHAR(240) NOT NULL CHECK (char_length(full_name) BETWEEN 1 AND 120 AND full_name = btrim(full_name) AND full_name !~ '[[:cntrl:]<>]'),
 phone VARCHAR(32) CHECK (phone IS NULL OR (phone = btrim(phone) AND phone ~ '^\+?[0-9][0-9 ()-]{5,31}$' AND length(regexp_replace(phone,'[^0-9]','','g')) BETWEEN 6 AND 15)),
 revision BIGINT NOT NULL CHECK (revision > 0),
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE cinenest_identity.customer_profile_audit (
 event_id UUID PRIMARY KEY, account_id UUID NOT NULL,
 action VARCHAR(8) NOT NULL CHECK (action IN ('READ','UPDATE')),
 outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('ALLOW','DENY','CONFLICT','INVALID')),
 revision_before BIGINT NOT NULL CHECK (revision_before >= 0),
 revision_after BIGINT NOT NULL CHECK (revision_after >= 0),
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK ((action='UPDATE' AND outcome='ALLOW' AND revision_after=revision_before+1)
     OR ((action<>'UPDATE' OR outcome<>'ALLOW') AND revision_after=revision_before))
);
-- Existing Identity audit convention: actor references survive independently of mutable account fixtures.
CREATE FUNCTION cinenest_identity.protect_customer_profile() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='customer_profile_audit' THEN RAISE EXCEPTION 'Immutable profile audit'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.revision<>1 THEN RAISE EXCEPTION 'Initial profile revision must be one'; END IF; RETURN NEW;
 END IF;
 IF NEW.account_id<>OLD.account_id OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at
 THEN RAISE EXCEPTION 'Profile revision conflict'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER customer_profile_revision BEFORE INSERT OR UPDATE ON cinenest_identity.customer_profile
 FOR EACH ROW EXECUTE FUNCTION cinenest_identity.protect_customer_profile();
CREATE TRIGGER customer_profile_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_identity.customer_profile_audit
 FOR EACH STATEMENT EXECUTE FUNCTION cinenest_identity.protect_customer_profile();

CREATE UNIQUE INDEX customer_profile_change_receipt ON cinenest_identity.customer_profile_audit(account_id,revision_after)
 WHERE action='UPDATE' AND outcome='ALLOW';
CREATE FUNCTION cinenest_identity.require_customer_profile_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM cinenest_identity.customer_profile_audit a
  WHERE a.account_id=NEW.account_id AND a.action='UPDATE' AND a.outcome='ALLOW'
    AND a.revision_before=NEW.revision-1 AND a.revision_after=NEW.revision)
 THEN RAISE EXCEPTION 'Profile change requires atomic audit'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER customer_profile_atomic_audit AFTER INSERT OR UPDATE ON cinenest_identity.customer_profile
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_identity.require_customer_profile_audit();

CREATE TABLE cinenest_pricing.package_version (
 version_id UUID PRIMARY KEY, package_id UUID NOT NULL REFERENCES cinenest_pricing.package,
 version_no BIGINT NOT NULL CHECK(version_no>0), source_revision BIGINT NOT NULL,
 content_hash CHAR(64) NOT NULL, snapshot JSONB NOT NULL, policy_refs JSONB NOT NULL,
 author_id UUID NOT NULL, publisher_id UUID NOT NULL CHECK(publisher_id<>author_id),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0), recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(package_id,version_no), UNIQUE(version_id,package_id)
);
CREATE TABLE cinenest_pricing.package_version_room (
 version_id UUID NOT NULL REFERENCES cinenest_pricing.package_version, room_id UUID NOT NULL, PRIMARY KEY(version_id,room_id)
);
CREATE TABLE cinenest_pricing.package_version_option (
 version_id UUID NOT NULL REFERENCES cinenest_pricing.package_version, service_id UUID NOT NULL,
 rate_version_id UUID NOT NULL, snapshot JSONB NOT NULL, PRIMARY KEY(version_id,service_id),
 FOREIGN KEY(service_id,rate_version_id) REFERENCES cinenest_pricing.service_rate_version(service_id,rate_version_id)
);
CREATE TABLE cinenest_pricing.package_publication (
 publication_id UUID PRIMARY KEY, version_id UUID NOT NULL UNIQUE, package_id UUID NOT NULL,
 FOREIGN KEY(version_id,package_id) REFERENCES cinenest_pricing.package_version(version_id,package_id),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), room_ids UUID[] NOT NULL CHECK(cardinality(room_ids)>0),
 sales_from TIMESTAMPTZ NOT NULL, sales_to TIMESTAMPTZ NOT NULL CHECK(sales_to>sales_from),
 session_from TIMESTAMPTZ NOT NULL, session_to TIMESTAMPTZ NOT NULL CHECK(session_to>session_from),
 timezone TEXT NOT NULL, local_dates JSONB NOT NULL, state VARCHAR(12) NOT NULL CHECK(state IN ('SCHEDULED','ACTIVE','BLOCKED','INACTIVE')),
 last_command_key UUID, last_command_hash CHAR(64), last_command_actor UUID,
 failure_reason VARCHAR(500), idempotency_key UUID NOT NULL, request_hash CHAR(64) NOT NULL,
 UNIQUE(package_id,idempotency_key)
);
CREATE TABLE cinenest_pricing.package_publication_work (
 publication_id UUID PRIMARY KEY REFERENCES cinenest_pricing.package_publication,
 next_attempt TIMESTAMPTZ NOT NULL, attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
 state VARCHAR(8) NOT NULL CHECK(state IN ('PENDING','DONE','FAILED')), last_error VARCHAR(500), updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX publication_due_idx ON cinenest_pricing.package_publication_work(next_attempt) WHERE state='PENDING';
CREATE TRIGGER immutable_package_version BEFORE UPDATE OR DELETE ON cinenest_pricing.package_version FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_version_room BEFORE UPDATE OR DELETE ON cinenest_pricing.package_version_room FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_version_option BEFORE UPDATE OR DELETE ON cinenest_pricing.package_version_option FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
-- Database-level serialization shares the stable package lock used by commands.
CREATE FUNCTION cinenest_pricing.guard_publication() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM cinenest_pricing.package WHERE package_id=NEW.package_id FOR UPDATE;
 IF TG_OP='UPDATE' AND (NEW.version_id<>OLD.version_id OR NEW.package_id<>OLD.package_id OR NEW.room_ids<>OLD.room_ids OR NEW.sales_from<>OLD.sales_from OR NEW.sales_to<>OLD.sales_to OR NEW.session_from<>OLD.session_from OR NEW.session_to<>OLD.session_to OR NEW.timezone<>OLD.timezone OR NEW.local_dates<>OLD.local_dates OR NEW.idempotency_key<>OLD.idempotency_key OR NEW.request_hash<>OLD.request_hash) THEN
  RAISE EXCEPTION 'Publication terms immutable; replace explicitly' USING ERRCODE='23514';
 END IF;
 IF NEW.room_ids<>(SELECT array_agg(room_id ORDER BY room_id) FROM cinenest_pricing.package_version_room WHERE version_id=NEW.version_id) THEN RAISE EXCEPTION 'Frozen scope mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.state IN ('SCHEDULED','ACTIVE') AND EXISTS(SELECT 1 FROM cinenest_pricing.package_publication p WHERE p.package_id=NEW.package_id AND p.publication_id<>NEW.publication_id AND p.state IN ('SCHEDULED','ACTIVE') AND p.room_ids && NEW.room_ids AND tstzrange(p.sales_from,p.sales_to,'[)') && tstzrange(NEW.sales_from,NEW.sales_to,'[)')) THEN
  RAISE EXCEPTION 'Publication scope/time overlap' USING ERRCODE='23505';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER publication_guard BEFORE INSERT OR UPDATE ON cinenest_pricing.package_publication FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_publication();

ALTER TABLE cinenest_pricing.configuration_audit ADD COLUMN command_key UUID,
 ADD COLUMN command_hash CHAR(64), ADD COLUMN publication_id UUID REFERENCES cinenest_pricing.package_publication;
CREATE UNIQUE INDEX publication_command_key ON cinenest_pricing.configuration_audit(package_id,command_key) WHERE command_key IS NOT NULL;

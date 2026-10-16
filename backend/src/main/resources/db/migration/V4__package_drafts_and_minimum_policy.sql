CREATE TABLE cinenest_rooms.minimum_session_policy (
 policy_id UUID PRIMARY KEY, venue_id UUID NOT NULL REFERENCES cinenest_rooms.venue,
 revision BIGINT NOT NULL CHECK(revision>0), minimum_minutes INTEGER NOT NULL CHECK(minimum_minutes>0),
 effective_from TIMESTAMPTZ NOT NULL, approval_reference TEXT NOT NULL CHECK(length(trim(approval_reference))>0),
 approved_by UUID NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(venue_id,revision), UNIQUE(venue_id,effective_from)
);
CREATE SCHEMA cinenest_pricing;
CREATE TABLE cinenest_pricing.package (
 package_id UUID PRIMARY KEY, venue_id UUID NOT NULL, code VARCHAR(64) NOT NULL CHECK(code ~ '^[A-Z0-9_-]+$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(venue_id,code)
);
CREATE TABLE cinenest_pricing.package_draft (
 package_id UUID PRIMARY KEY REFERENCES cinenest_pricing.package,
 revision BIGINT NOT NULL CHECK(revision>=0), name VARCHAR(200) NOT NULL CHECK(length(trim(name))>0),
 description VARCHAR(2000) NOT NULL, currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 base_price_minor BIGINT NOT NULL CHECK(base_price_minor>0), included_minutes INTEGER NOT NULL CHECK(included_minutes>0),
 minimum_minutes INTEGER NOT NULL CHECK(minimum_minutes>0), maximum_minutes INTEGER NOT NULL,
 extensions_enabled BOOLEAN NOT NULL, extension_step_minutes INTEGER, extension_price_minor BIGINT,
 included_guests INTEGER NOT NULL CHECK(included_guests>0), maximum_guests INTEGER NOT NULL,
 extra_guest_price_minor BIGINT NOT NULL CHECK(extra_guest_price_minor>=0), venue_wide BOOLEAN NOT NULL,
 policy_id UUID NOT NULL, policy_revision BIGINT NOT NULL CHECK(policy_revision>0),
 tax_status VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED' CHECK(tax_status='UNRESOLVED'),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(minimum_minutes<=included_minutes AND included_minutes<=maximum_minutes),
 CHECK(included_guests<=maximum_guests),
 CHECK((extensions_enabled AND extension_step_minutes IS NOT NULL AND extension_step_minutes>0 AND extension_price_minor IS NOT NULL AND extension_price_minor>=0)
 OR (NOT extensions_enabled AND extension_step_minutes IS NULL AND extension_price_minor IS NULL AND maximum_minutes=included_minutes))
);
CREATE TABLE cinenest_pricing.package_draft_room (
 package_id UUID NOT NULL REFERENCES cinenest_pricing.package_draft, room_id UUID NOT NULL, PRIMARY KEY(package_id,room_id)
);
CREATE TABLE cinenest_pricing.service_definition (
 service_id UUID PRIMARY KEY, venue_id UUID NOT NULL, code VARCHAR(64) NOT NULL, label VARCHAR(300) NOT NULL CHECK(length(trim(label))>0),
 required_facility_code VARCHAR(64) NOT NULL CHECK(length(trim(required_facility_code))>0),
 approved BOOLEAN NOT NULL, approval_reference TEXT NOT NULL CHECK(length(trim(approval_reference))>0), UNIQUE(venue_id,code)
);
CREATE TABLE cinenest_pricing.service_rate_version (
 rate_version_id UUID PRIMARY KEY, service_id UUID NOT NULL REFERENCES cinenest_pricing.service_definition,
 revision BIGINT NOT NULL CHECK(revision>0), currency CHAR(3) NOT NULL CHECK(currency='LKR'),
 unit_price_minor BIGINT NOT NULL CHECK(unit_price_minor>=0), pricing_basis VARCHAR(16) NOT NULL CHECK(pricing_basis='PER_SESSION'),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(service_id,revision), UNIQUE(service_id,rate_version_id)
);
CREATE TABLE cinenest_pricing.package_draft_option (
 package_id UUID NOT NULL REFERENCES cinenest_pricing.package_draft, service_id UUID NOT NULL,
 rate_version_id UUID NOT NULL, inclusion_mode VARCHAR(8) NOT NULL CHECK(inclusion_mode IN ('INCLUDED','OPTIONAL')),
 PRIMARY KEY(package_id,service_id), FOREIGN KEY(service_id,rate_version_id) REFERENCES cinenest_pricing.service_rate_version(service_id,rate_version_id)
);
CREATE TABLE cinenest_pricing.package_draft_revision (
 package_id UUID NOT NULL REFERENCES cinenest_pricing.package, revision BIGINT NOT NULL CHECK(revision>=0),
 snapshot JSONB NOT NULL, actor_id UUID NOT NULL, reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(package_id,revision)
);
CREATE TABLE cinenest_pricing.configuration_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, venue_id UUID, package_id UUID, action VARCHAR(24) NOT NULL,
 outcome VARCHAR(8) NOT NULL CHECK(outcome IN ('ALLOW','DENY')), old_revision BIGINT, new_revision BIGINT,
 reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, correlation_id UUID NOT NULL
);
CREATE INDEX package_venue_idx ON cinenest_pricing.package(venue_id,code,package_id);
CREATE INDEX service_venue_idx ON cinenest_pricing.service_definition(venue_id,service_id);
CREATE INDEX minimum_policy_effective_idx ON cinenest_rooms.minimum_session_policy(venue_id,effective_from);
CREATE FUNCTION cinenest_pricing.reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Immutable configuration history'; END $$;
CREATE TRIGGER immutable_draft_history BEFORE UPDATE OR DELETE ON cinenest_pricing.package_draft_revision FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE TRIGGER immutable_service_rate BEFORE UPDATE OR DELETE ON cinenest_pricing.service_rate_version FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_history_mutation();
CREATE FUNCTION cinenest_rooms.reject_minimum_policy_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Immutable approved minimum policy'; END $$;
CREATE TRIGGER immutable_minimum_policy BEFORE UPDATE OR DELETE ON cinenest_rooms.minimum_session_policy FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.reject_minimum_policy_mutation();

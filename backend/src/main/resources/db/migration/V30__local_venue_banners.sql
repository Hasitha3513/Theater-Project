-- Default-off local synthetic advertising. No Identity/financial/device tables change.
CREATE TABLE cinenest_rooms.banner (
 banner_id UUID PRIMARY KEY, venue_id UUID NOT NULL REFERENCES cinenest_rooms.venue,
 resource_revision BIGINT NOT NULL CHECK(resource_revision>0), draft_revision BIGINT NOT NULL,
 published_revision BIGINT, UNIQUE(banner_id,venue_id)
);
CREATE TABLE cinenest_rooms.banner_revision (
 banner_id UUID NOT NULL, venue_id UUID NOT NULL, revision_number BIGINT NOT NULL CHECK(revision_number>0),
 title TEXT NOT NULL CHECK(char_length(title) BETWEEN 1 AND 120 AND title=btrim(title) AND title !~ '[[:cntrl:]<>]'),
 description TEXT NOT NULL CHECK(char_length(description)<=1000 AND description !~ '[[:cntrl:]<>]'),
 alt_text TEXT NOT NULL CHECK(char_length(alt_text) BETWEEN 1 AND 240 AND alt_text=btrim(alt_text) AND alt_text !~ '[[:cntrl:]<>]'),
 asset_id TEXT NOT NULL CHECK(asset_id IN ('hero','violet','blue','amber','recliners')),
 asset_version INTEGER NOT NULL CHECK(asset_version=1), asset_sha256 CHAR(64) NOT NULL CHECK(asset_sha256 ~ '^[a-f0-9]{64}$'),
 starts_at TIMESTAMPTZ NOT NULL CHECK(isfinite(starts_at)), ends_at TIMESTAMPTZ NOT NULL CHECK(isfinite(ends_at)),
 display_order INTEGER NOT NULL CHECK(display_order BETWEEN 0 AND 10000), link_target TEXT NOT NULL CHECK(link_target IN ('NONE','ROOMS')),
 author_id UUID NOT NULL, review_hash CHAR(64) NOT NULL CHECK(review_hash ~ '^[a-f0-9]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(banner_id,revision_number), UNIQUE(banner_id,venue_id,revision_number),
 FOREIGN KEY(banner_id,venue_id) REFERENCES cinenest_rooms.banner(banner_id,venue_id) DEFERRABLE INITIALLY DEFERRED,
 CHECK(ends_at>starts_at)
);
ALTER TABLE cinenest_rooms.banner ADD FOREIGN KEY(banner_id,venue_id,draft_revision) REFERENCES cinenest_rooms.banner_revision(banner_id,venue_id,revision_number) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE cinenest_rooms.banner ADD FOREIGN KEY(banner_id,venue_id,published_revision) REFERENCES cinenest_rooms.banner_revision(banner_id,venue_id,revision_number) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE cinenest_rooms.banner_audit (
 event_id UUID PRIMARY KEY, actor_id UUID NOT NULL, venue_id UUID NOT NULL,
 banner_id UUID, action TEXT NOT NULL CHECK(action IN ('READ','CREATE','EDIT','PUBLISH','UNPUBLISH')),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')), revision_before BIGINT NOT NULL CHECK(revision_before>=0), revision_after BIGINT NOT NULL CHECK(revision_after>=0),
 correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK((action<>'READ' AND outcome='ALLOW' AND revision_after=revision_before+1) OR ((action='READ' OR outcome='DENY') AND revision_after=revision_before))
);
CREATE UNIQUE INDEX banner_mutation_audit ON cinenest_rooms.banner_audit(banner_id,revision_after) WHERE action<>'READ' AND outcome='ALLOW';
CREATE TABLE cinenest_rooms.banner_command (
 actor_id UUID NOT NULL, command_id UUID NOT NULL, venue_id UUID NOT NULL, banner_id UUID NOT NULL,
 action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','PUBLISH','UNPUBLISH')), request_hash CHAR(64) NOT NULL CHECK(request_hash ~ '^[a-f0-9]{64}$'),
 result_revision BIGINT NOT NULL CHECK(result_revision>0), audit_id UUID NOT NULL UNIQUE REFERENCES cinenest_rooms.banner_audit(event_id) DEFERRABLE INITIALLY DEFERRED,
 PRIMARY KEY(actor_id,command_id), FOREIGN KEY(banner_id,venue_id) REFERENCES cinenest_rooms.banner(banner_id,venue_id)
);
-- Empty-table truncate permits existing disposable test resets; any stored history blocks it.
CREATE FUNCTION cinenest_rooms.banner_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE has_rows BOOLEAN;
BEGIN
 IF TG_OP='TRUNCATE' THEN
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I.%I)',TG_TABLE_SCHEMA,TG_TABLE_NAME) INTO has_rows;
  IF NOT has_rows THEN RETURN NULL; END IF;
 END IF;
 RAISE EXCEPTION 'Immutable banner history';
END $$;
CREATE TRIGGER banner_revision_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_rooms.banner_revision FOR EACH STATEMENT EXECUTE FUNCTION cinenest_rooms.banner_immutable();
CREATE TRIGGER banner_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_rooms.banner_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_rooms.banner_immutable();
CREATE TRIGGER banner_command_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON cinenest_rooms.banner_command FOR EACH STATEMENT EXECUTE FUNCTION cinenest_rooms.banner_immutable();
CREATE FUNCTION cinenest_rooms.banner_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN IF NEW.resource_revision<>1 OR NEW.draft_revision<>1 OR NEW.published_revision IS NOT NULL THEN RAISE EXCEPTION 'Invalid initial banner'; END IF;
 ELSE
  IF NEW.banner_id<>OLD.banner_id OR NEW.venue_id<>OLD.venue_id OR NEW.resource_revision<>OLD.resource_revision+1 THEN RAISE EXCEPTION 'Banner revision conflict'; END IF;
  IF NEW.draft_revision<>OLD.draft_revision AND (NEW.draft_revision<>OLD.draft_revision+1 OR NEW.published_revision IS DISTINCT FROM OLD.published_revision) THEN RAISE EXCEPTION 'Invalid draft transition'; END IF;
  IF NEW.published_revision IS DISTINCT FROM OLD.published_revision AND NEW.published_revision IS NOT NULL AND NEW.published_revision<>NEW.draft_revision THEN RAISE EXCEPTION 'Only reviewed draft may publish'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER banner_guard BEFORE INSERT OR UPDATE ON cinenest_rooms.banner FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.banner_guard();
CREATE TRIGGER banner_no_delete BEFORE DELETE OR TRUNCATE ON cinenest_rooms.banner FOR EACH STATEMENT EXECUTE FUNCTION cinenest_rooms.banner_immutable();
CREATE FUNCTION cinenest_rooms.banner_atomic() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a cinenest_rooms.banner_audit; c cinenest_rooms.banner_command; expected TEXT;
BEGIN
 expected:=CASE WHEN TG_OP='INSERT' THEN 'CREATE' WHEN NEW.draft_revision<>OLD.draft_revision THEN 'EDIT' WHEN NEW.published_revision IS NULL THEN 'UNPUBLISH' ELSE 'PUBLISH' END;
 SELECT * INTO a FROM cinenest_rooms.banner_audit WHERE banner_id=NEW.banner_id AND revision_after=NEW.resource_revision AND action=expected AND outcome='ALLOW';
 IF NOT FOUND OR a.venue_id<>NEW.venue_id OR a.revision_before<>NEW.resource_revision-1 THEN RAISE EXCEPTION 'Mandatory atomic banner audit'; END IF;
 SELECT * INTO c FROM cinenest_rooms.banner_command WHERE audit_id=a.event_id;
 IF NOT FOUND OR c.actor_id<>a.actor_id OR c.venue_id<>NEW.venue_id OR c.banner_id<>NEW.banner_id OR c.action<>expected OR c.result_revision<>NEW.resource_revision THEN RAISE EXCEPTION 'Mandatory command receipt'; END IF;
 IF expected IN ('CREATE','EDIT') AND NOT EXISTS(SELECT 1 FROM cinenest_rooms.banner_revision r WHERE r.banner_id=NEW.banner_id AND r.revision_number=NEW.draft_revision AND r.author_id=a.actor_id) THEN RAISE EXCEPTION 'Draft author mismatch'; END IF;
 IF expected='PUBLISH' AND EXISTS(SELECT 1 FROM cinenest_rooms.banner_revision r WHERE r.banner_id=NEW.banner_id AND r.revision_number=NEW.published_revision AND r.author_id=a.actor_id) THEN RAISE EXCEPTION 'Independent publisher required'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER banner_atomic AFTER INSERT OR UPDATE ON cinenest_rooms.banner DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.banner_atomic();
CREATE INDEX banner_venue ON cinenest_rooms.banner(venue_id,banner_id);
CREATE FUNCTION cinenest_rooms.banner_revision_atomic() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_rooms.banner b JOIN cinenest_rooms.banner_audit a ON a.banner_id=b.banner_id AND a.revision_after=b.resource_revision JOIN cinenest_rooms.banner_command c ON c.audit_id=a.event_id WHERE b.banner_id=NEW.banner_id AND b.venue_id=NEW.venue_id AND b.draft_revision=NEW.revision_number AND a.actor_id=NEW.author_id AND a.action IN ('CREATE','EDIT') AND a.outcome='ALLOW') THEN RAISE EXCEPTION 'Content revision requires atomic author audit'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER banner_revision_atomic AFTER INSERT ON cinenest_rooms.banner_revision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.banner_revision_atomic();
CREATE FUNCTION cinenest_rooms.banner_command_atomic() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM cinenest_rooms.banner_audit a JOIN cinenest_rooms.banner b ON b.banner_id=a.banner_id AND b.venue_id=a.venue_id WHERE a.event_id=NEW.audit_id AND a.actor_id=NEW.actor_id AND a.banner_id=NEW.banner_id AND a.venue_id=NEW.venue_id AND a.action=NEW.action AND a.outcome='ALLOW' AND a.revision_after=NEW.result_revision AND b.resource_revision>=NEW.result_revision) THEN RAISE EXCEPTION 'Command audit binding mismatch'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER banner_command_atomic AFTER INSERT ON cinenest_rooms.banner_command DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.banner_command_atomic();

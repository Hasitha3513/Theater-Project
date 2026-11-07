-- Synthetic qualification only. No operational intake, grants, seed or issuance.
CREATE TABLE cinenest_pricing.commercial_policy_binding (
    binding_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    command_key UUID NOT NULL,
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    version_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ NOT NULL,
    finance_review UUID NOT NULL REFERENCES cinenest_pricing.commercial_policy_review,
    legal_review UUID NOT NULL REFERENCES cinenest_pricing.commercial_policy_review,
    operations_review UUID NOT NULL REFERENCES cinenest_pricing.commercial_policy_review,
    provenance TEXT NOT NULL CHECK (provenance = 'SYNTHETIC_V36_REVIEW_ONLY'),
    recorded_at TIMESTAMPTZ NOT NULL,
    CHECK (isfinite(effective_from) AND isfinite(effective_until) AND effective_until > effective_from),
    FOREIGN KEY (policy_id, venue_id) REFERENCES cinenest_pricing.commercial_policy_draft (policy_id, venue_id),
    FOREIGN KEY (policy_id, revision, content_hash)
        REFERENCES cinenest_pricing.commercial_policy_revision (policy_id, revision, content_hash),
    FOREIGN KEY (version_id, room_id) REFERENCES cinenest_pricing.package_version_room,
    UNIQUE (actor_id, venue_id, command_key),
    UNIQUE (binding_id, actor_id, venue_id),
    EXCLUDE USING gist (venue_id WITH =, room_id WITH =, version_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&)
);

CREATE TABLE cinenest_pricing.commercial_policy_binding_audit (
    binding_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (binding_id, actor_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_binding (binding_id, actor_id, venue_id)
);
ALTER TABLE cinenest_pricing.commercial_policy_binding
    ADD FOREIGN KEY (binding_id) REFERENCES cinenest_pricing.commercial_policy_binding_audit
        DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION cinenest_pricing.commercial_binding_immutable()
RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SET search_path = pg_catalog AS $$
BEGIN
    -- The reciprocal foreign keys require both relations in any TRUNCATE.
    -- PostgreSQL locks the entire truncate set before invoking these triggers.
    -- The volatile query at READ COMMITTED sees inserts committed while waiting.
    IF TG_OP = 'TRUNCATE'
       AND current_setting('transaction_isolation') = 'read committed'
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_binding)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_binding_audit)
    THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Commercial binding history is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER binding_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.commercial_policy_binding
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.commercial_binding_immutable();
CREATE TRIGGER binding_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON cinenest_pricing.commercial_policy_binding_audit
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_pricing.commercial_binding_immutable();

CREATE FUNCTION cinenest_pricing.require_binding_authority(
    actor UUID, venue UUID, capability TEXT, evidence_grant UUID, evidence_revision BIGINT
) RETURNS VOID LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    PERFORM 1 FROM cinenest_identity.account a
    JOIN cinenest_identity.account_scope s ON s.account_id = a.account_id
    JOIN cinenest_identity.access_grant g ON g.account_id = a.account_id AND g.venue_id = s.venue_id
    WHERE a.account_id = actor AND a.synthetic AND a.eligibility = 'ELIGIBLE'
      AND s.venue_id = venue AND g.scope_kind = 'VENUE' AND g.permission = capability
      AND g.state = 'ACTIVE' AND g.valid_from <= clock_timestamp() AND g.valid_until > clock_timestamp()
      AND (evidence_grant IS NULL OR (g.grant_id = evidence_grant AND g.revision = evidence_revision))
    FOR SHARE OF a, s, g;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Current scoped synthetic authority required' USING ERRCODE = '42501';
    END IF;
END;
$$;

CREATE FUNCTION cinenest_pricing.guard_commercial_binding()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    evidence RECORD;
    found_reviews INTEGER := 0;
BEGIN
    PERFORM cinenest_pricing.require_binding_authority(
        NEW.actor_id, NEW.venue_id, 'commercial.policy.edit', NULL, NULL);
    PERFORM 1
    FROM cinenest_pricing.commercial_policy_draft d
    JOIN cinenest_pricing.commercial_policy_revision r
        ON r.policy_id = d.policy_id AND r.revision = d.current_revision
    JOIN cinenest_pricing.package_version v ON v.version_id = NEW.version_id
    JOIN cinenest_pricing.package p ON p.package_id = v.package_id
    JOIN cinenest_rooms.room room ON room.room_id = NEW.room_id
    WHERE d.policy_id = NEW.policy_id AND d.venue_id = NEW.venue_id
      AND r.revision = NEW.revision AND r.content_hash = NEW.content_hash
      AND p.venue_id = NEW.venue_id AND room.venue_id = NEW.venue_id
      AND (d.package_id IS NULL OR d.package_id = p.package_id)
      AND r.submitted_for_review AND cinenest_pricing.commercial_policy_is_complete(r)
      AND r.effective_from_proposal <= NEW.effective_from
      AND (r.effective_until_proposal IS NULL OR NEW.effective_until <= r.effective_until_proposal)
    FOR SHARE OF d, room, p;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid binding scope, revision, terms or period' USING ERRCODE = '23514';
    END IF;

    FOR evidence IN
        SELECT r.reviewer_id, r.domain, c.grant_id, c.grant_revision
        FROM cinenest_pricing.commercial_policy_review r
        JOIN cinenest_pricing.commercial_policy_command c ON c.command_id = r.command_id
        WHERE r.policy_id = NEW.policy_id AND r.venue_id = NEW.venue_id
          AND r.revision = NEW.revision AND r.content_hash = NEW.content_hash
          AND r.outcome = 'APPROVE'
          AND r.review_id = CASE r.domain WHEN 'FINANCE' THEN NEW.finance_review
              WHEN 'LEGAL' THEN NEW.legal_review WHEN 'OPERATIONS' THEN NEW.operations_review END
        ORDER BY r.domain
    LOOP
        PERFORM cinenest_pricing.require_binding_authority(evidence.reviewer_id, NEW.venue_id,
            'commercial.policy.review.' || lower(evidence.domain), evidence.grant_id, evidence.grant_revision);
        found_reviews := found_reviews + 1;
    END LOOP;
    IF found_reviews <> 3 THEN
        RAISE EXCEPTION 'Three matching synthetic reviews required' USING ERRCODE = '42501';
    END IF;
    NEW.recorded_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_commercial_binding BEFORE INSERT ON cinenest_pricing.commercial_policy_binding
    FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.guard_commercial_binding();

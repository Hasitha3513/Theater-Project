-- Candidate only. No historical migration changes, seeds, grants or activation.

CREATE TABLE cinenest_pricing.commercial_policy_draft (
    policy_id UUID PRIMARY KEY,
    venue_id UUID NOT NULL
        REFERENCES cinenest_rooms.venue(venue_id),
    package_id UUID
        REFERENCES cinenest_pricing.package(package_id),
    current_revision BIGINT NOT NULL CHECK (current_revision > 0),
    created_by UUID NOT NULL
        REFERENCES cinenest_identity.account(account_id),
    created_command_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    mode TEXT NOT NULL DEFAULT 'LOCAL_SYNTHETIC_DRAFT_ONLY'
        CHECK (mode = 'LOCAL_SYNTHETIC_DRAFT_ONLY'),
    commercial_issuance_enabled BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (commercial_issuance_enabled = FALSE),
    checkout_enabled BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (checkout_enabled = FALSE),
    customer_access_enabled BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (customer_access_enabled = FALSE),
    UNIQUE (policy_id, venue_id)
);

CREATE TABLE cinenest_pricing.commercial_policy_command (
    command_id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    actor_id UUID NOT NULL
        REFERENCES cinenest_identity.account(account_id),
    account_revision BIGINT NOT NULL CHECK (account_revision >= 0),
    expected_credential_epoch BIGINT NOT NULL
        CHECK (expected_credential_epoch >= 0),
    grant_id UUID NOT NULL
        REFERENCES cinenest_identity.access_grant(grant_id),
    grant_revision BIGINT NOT NULL CHECK (grant_revision >= 0),
    grant_permission TEXT NOT NULL,
    grant_valid_from TIMESTAMPTZ NOT NULL,
    grant_valid_until TIMESTAMPTZ NOT NULL,
    idempotency_key UUID NOT NULL,
    operation TEXT NOT NULL
        CHECK (operation IN ('CREATE', 'REVISE', 'REVIEW')),
    expected_revision BIGINT NOT NULL CHECK (expected_revision >= 0),
    result_revision BIGINT NOT NULL CHECK (result_revision > 0),
    expected_content_hash CHAR(64),
    review_domain TEXT
        CHECK (review_domain IN ('FINANCE', 'LEGAL', 'OPERATIONS')),
    result_review_id UUID,
    outcome TEXT NOT NULL CHECK (
        outcome IN (
            'CREATED', 'REVISED', 'APPROVE',
            'REJECT', 'REQUEST_CHANGES'
        )
    ),
    request_hash CHAR(64) NOT NULL
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    admitted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    UNIQUE (actor_id, venue_id, idempotency_key),
    FOREIGN KEY (policy_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_draft
            (policy_id, venue_id)
        DEFERRABLE INITIALLY DEFERRED,

    CHECK (grant_valid_until > grant_valid_from),
    CHECK ((
        (operation = 'CREATE'
         AND expected_revision = 0 AND result_revision = 1
         AND outcome = 'CREATED'
         AND review_domain IS NULL
         AND expected_content_hash IS NULL
         AND result_review_id IS NULL)
        OR
        (operation = 'REVISE'
         AND expected_revision > 0
         AND result_revision::NUMERIC = expected_revision::NUMERIC + 1
         AND outcome = 'REVISED'
         AND review_domain IS NULL
         AND expected_content_hash IS NULL
         AND result_review_id IS NULL)
        OR
        (operation = 'REVIEW'
         AND expected_revision > 0
         AND result_revision = expected_revision
         AND outcome IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES')
         AND review_domain IS NOT NULL
         AND expected_content_hash IS NOT NULL
         AND expected_content_hash ~ '^[0-9a-f]{64}$'
         AND result_review_id IS NOT NULL)
    ) IS TRUE)
);

CREATE TABLE cinenest_pricing.commercial_policy_revision (
    policy_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    command_id UUID NOT NULL UNIQUE,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'LKR'),
    venue_timezone TEXT NOT NULL CHECK (length(trim(venue_timezone)) > 0),
    effective_from_proposal TIMESTAMPTZ,
    effective_until_proposal TIMESTAMPTZ,

    tax_applicability TEXT NOT NULL CHECK (
        tax_applicability IN (
            'UNRESOLVED', 'APPLICABLE', 'NOT_APPLICABLE', 'EXEMPT'
        )
    ),
    tax_type TEXT NOT NULL CHECK (
        tax_type IN ('UNRESOLVED', 'NONE', 'PERCENTAGE', 'FIXED_PER_SESSION')
    ),
    tax_rate_basis_points INTEGER
        CHECK (tax_rate_basis_points BETWEEN 0 AND 10000),
    tax_amount_minor BIGINT CHECK (tax_amount_minor >= 0),
    tax_treatment TEXT NOT NULL CHECK (
        tax_treatment IN ('UNRESOLVED', 'NONE', 'INCLUSIVE', 'EXCLUSIVE')
    ),
    tax_basis TEXT NOT NULL CHECK (
        tax_basis IN (
            'UNRESOLVED', 'NONE',
            'PACKAGE_COMPONENTS', 'PACKAGE_COMPONENTS_AND_METHOD_FEE'
        )
    ),
    tax_rounding TEXT NOT NULL CHECK (
        tax_rounding IN ('UNRESOLVED', 'NONE', 'HALF_UP', 'HALF_EVEN')
    ),

    payment_methods TEXT[] NOT NULL,
    fee_channels TEXT[] NOT NULL,
    fees JSONB NOT NULL,

    cancellation_eligibility TEXT NOT NULL CHECK (
        cancellation_eligibility IN (
            'UNRESOLVED', 'NOT_ALLOWED', 'BEFORE_START', 'REVIEW_REQUIRED'
        )
    ),
    cancellation_cutoff_minutes INTEGER
        CHECK (cancellation_cutoff_minutes BETWEEN 0 AND 525600),
    cancellation_time_basis TEXT NOT NULL
        CHECK (cancellation_time_basis = 'VENUE_TIMEZONE'),
    no_show_treatment TEXT NOT NULL CHECK (
        no_show_treatment IN ('UNRESOLVED', 'NO_REFUND', 'REVIEW_REQUIRED')
    ),
    exception_handling TEXT NOT NULL CHECK (
        exception_handling IN (
            'UNRESOLVED', 'NONE', 'MANUAL_REVIEW_REQUIRED'
        )
    ),
    exception_notes VARCHAR(2000) NOT NULL,

    refund_eligibility TEXT NOT NULL CHECK (
        refund_eligibility IN (
            'UNRESOLVED', 'NONE', 'ELIGIBLE_CANCELLATION', 'MANUAL_REVIEW'
        )
    ),
    refund_calculation TEXT NOT NULL CHECK (
        refund_calculation IN (
            'UNRESOLVED', 'NONE', 'PERCENTAGE', 'FIXED_MINOR'
        )
    ),
    refund_basis TEXT NOT NULL CHECK (
        refund_basis IN ('UNRESOLVED', 'NONE', 'ELIGIBLE_PRINCIPAL')
    ),
    refund_rate_basis_points INTEGER
        CHECK (refund_rate_basis_points BETWEEN 0 AND 10000),
    refund_amount_minor BIGINT CHECK (refund_amount_minor >= 0),
    non_refundable_components TEXT[],
    refund_destination TEXT NOT NULL CHECK (
        refund_destination IN (
            'UNRESOLVED', 'NONE', 'ORIGINAL_VERIFIED_PAYER'
        )
    ),

    supplied_references TEXT[] NOT NULL,
    submitted_for_review BOOLEAN NOT NULL,
    authored_by UUID NOT NULL
        REFERENCES cinenest_identity.account(account_id),
    recorded_at TIMESTAMPTZ NOT NULL,
    content_hash CHAR(64) NOT NULL
        CHECK (content_hash ~ '^[0-9a-f]{64}$'),

    PRIMARY KEY (policy_id, revision),
    UNIQUE (policy_id, revision, content_hash),
    FOREIGN KEY (policy_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_draft
            (policy_id, venue_id),
    FOREIGN KEY (command_id)
        REFERENCES cinenest_pricing.commercial_policy_command(command_id)
        DEFERRABLE INITIALLY DEFERRED,

    CHECK (
        effective_from_proposal IS NULL
        OR isfinite(effective_from_proposal)
    ),
    CHECK (
        effective_until_proposal IS NULL
        OR (
            isfinite(effective_until_proposal)
            AND effective_from_proposal IS NOT NULL
            AND effective_until_proposal > effective_from_proposal
        )
    ),
    CHECK (payment_methods <@ ARRAY['BANK', 'GATEWAY']::TEXT[]),
    CHECK (cardinality(payment_methods) <= 2),
    CHECK (fee_channels <@ ARRAY['ONLINE', 'MANUAL']::TEXT[]),
    CHECK (cardinality(fee_channels) <= 2),
    CHECK (
        non_refundable_components IS NULL
        OR non_refundable_components <@ ARRAY[
            'BASE', 'EXTENSION', 'EXTRA_GUEST',
            'ADDON', 'METHOD_FEE', 'TAX'
        ]::TEXT[]
    ),
    CHECK (cardinality(supplied_references) <= 20),
    CHECK (jsonb_typeof(fees) = 'array'),
    CHECK (jsonb_array_length(fees) <= 4),
    CHECK ((
        (tax_type = 'PERCENTAGE' AND tax_amount_minor IS NULL)
        OR
        (tax_type = 'FIXED_PER_SESSION'
         AND tax_rate_basis_points IS NULL)
        OR
        (tax_type IN ('UNRESOLVED', 'NONE')
         AND tax_rate_basis_points IS NULL
         AND tax_amount_minor IS NULL)
    ) IS TRUE),
    CHECK ((
        (refund_calculation = 'PERCENTAGE'
         AND refund_amount_minor IS NULL)
        OR
        (refund_calculation = 'FIXED_MINOR'
         AND refund_rate_basis_points IS NULL)
        OR
        (refund_calculation IN ('UNRESOLVED', 'NONE')
         AND refund_rate_basis_points IS NULL
         AND refund_amount_minor IS NULL)
    ) IS TRUE)
);

CREATE TABLE cinenest_pricing.commercial_policy_review (
    review_id UUID PRIMARY KEY,
    command_id UUID NOT NULL UNIQUE,
    policy_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    domain TEXT NOT NULL CHECK (domain IN ('FINANCE', 'LEGAL', 'OPERATIONS')),
    reviewer_id UUID NOT NULL
        REFERENCES cinenest_identity.account(account_id),
    outcome TEXT NOT NULL
        CHECK (outcome IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES')),
    evidence_reference VARCHAR(500) NOT NULL
        CHECK (length(trim(evidence_reference)) > 0),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    reviewed_at TIMESTAMPTZ NOT NULL,

    UNIQUE (policy_id, revision, domain),
    FOREIGN KEY (policy_id, venue_id)
        REFERENCES cinenest_pricing.commercial_policy_draft
            (policy_id, venue_id),
    FOREIGN KEY (policy_id, revision, content_hash)
        REFERENCES cinenest_pricing.commercial_policy_revision
            (policy_id, revision, content_hash),
    FOREIGN KEY (command_id)
        REFERENCES cinenest_pricing.commercial_policy_command(command_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE cinenest_pricing.commercial_policy_audit (
    event_id UUID PRIMARY KEY,
    command_id UUID UNIQUE,
    policy_id UUID,
    venue_id UUID,
    revision BIGINT,
    actor_id UUID REFERENCES cinenest_identity.account(account_id),
    operation TEXT NOT NULL
        CHECK (operation IN ('CREATE', 'REVISE', 'REVIEW', 'DENY')),
    outcome TEXT NOT NULL CHECK (
        outcome IN (
            'CREATED', 'REVISED', 'APPROVE',
            'REJECT', 'REQUEST_CHANGES', 'DENY'
        )
    ),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    FOREIGN KEY (command_id)
        REFERENCES cinenest_pricing.commercial_policy_command(command_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (policy_id, revision)
        REFERENCES cinenest_pricing.commercial_policy_revision
            (policy_id, revision)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK ((
        (operation = 'DENY' AND outcome = 'DENY'
         AND command_id IS NULL AND policy_id IS NULL
         AND venue_id IS NULL AND revision IS NULL)
        OR
        (operation <> 'DENY' AND outcome <> 'DENY'
         AND command_id IS NOT NULL AND policy_id IS NOT NULL
         AND venue_id IS NOT NULL AND revision IS NOT NULL
         AND actor_id IS NOT NULL)
    ) IS TRUE)
);

ALTER TABLE cinenest_pricing.commercial_policy_draft
    ADD CONSTRAINT commercial_policy_head_fk
    FOREIGN KEY (policy_id, current_revision)
    REFERENCES cinenest_pricing.commercial_policy_revision
        (policy_id, revision)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE cinenest_pricing.commercial_policy_draft
    ADD CONSTRAINT commercial_policy_create_command_fk
    FOREIGN KEY (created_command_id)
    REFERENCES cinenest_pricing.commercial_policy_command(command_id)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE cinenest_pricing.commercial_policy_command
    ADD CONSTRAINT commercial_policy_result_revision_fk
    FOREIGN KEY (policy_id, result_revision)
    REFERENCES cinenest_pricing.commercial_policy_revision
        (policy_id, revision)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE cinenest_pricing.commercial_policy_command
    ADD CONSTRAINT commercial_policy_result_review_fk
    FOREIGN KEY (result_review_id)
    REFERENCES cinenest_pricing.commercial_policy_review(review_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX commercial_policy_venue_list_idx
    ON cinenest_pricing.commercial_policy_draft
        (venue_id, created_at DESC, policy_id);


-- Pure structural validation. Null business decisions remain unresolved.
CREATE FUNCTION cinenest_pricing.commercial_policy_validate_revision(
    r cinenest_pricing.commercial_policy_revision
) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    f JSONB;
    amount_text TEXT;
    pair_key TEXT;
    seen TEXT[] := ARRAY[]::TEXT[];
    reference_text TEXT;
BEGIN
    IF r.schema_version IS DISTINCT FROM 1
       OR r.currency IS DISTINCT FROM 'LKR'
       OR r.payment_methods IS NULL
       OR r.fee_channels IS NULL
       OR r.supplied_references IS NULL
       OR jsonb_typeof(r.fees) IS DISTINCT FROM 'array'
    THEN RETURN FALSE; END IF;

    IF cardinality(r.payment_methods) > 2
       OR cardinality(r.fee_channels) > 2
       OR cardinality(r.supplied_references) > 20
       OR jsonb_array_length(r.fees) > 4
       OR array_position(r.payment_methods, NULL) IS NOT NULL
       OR array_position(r.fee_channels, NULL) IS NOT NULL
       OR array_position(r.supplied_references, NULL) IS NOT NULL
    THEN RETURN FALSE; END IF;

    IF (SELECT count(DISTINCT v) FROM unnest(r.payment_methods) AS t(v))
         <> cardinality(r.payment_methods)
       OR
       (SELECT count(DISTINCT v) FROM unnest(r.fee_channels) AS t(v))
         <> cardinality(r.fee_channels)
    THEN RETURN FALSE; END IF;

    IF r.non_refundable_components IS NOT NULL THEN
        IF array_position(r.non_refundable_components, NULL) IS NOT NULL
           OR (SELECT count(DISTINCT v)
               FROM unnest(r.non_refundable_components) AS t(v))
                <> cardinality(r.non_refundable_components)
        THEN RETURN FALSE; END IF;
    END IF;

    FOREACH reference_text IN ARRAY r.supplied_references LOOP
        IF length(trim(reference_text)) NOT BETWEEN 1 AND 500
           OR reference_text ~ '[[:cntrl:]]'
        THEN RETURN FALSE; END IF;
    END LOOP;

    IF r.title ~ '[[:cntrl:]]'
       OR r.exception_notes ~ '[[:cntrl:]]'
       OR octet_length(convert_to(to_jsonb(r)::TEXT, 'UTF8')) > 65536
    THEN RETURN FALSE; END IF;

    IF r.tax_applicability = 'UNRESOLVED' THEN
        IF r.tax_type <> 'UNRESOLVED'
           OR r.tax_treatment <> 'UNRESOLVED'
           OR r.tax_basis <> 'UNRESOLVED'
           OR r.tax_rounding <> 'UNRESOLVED'
        THEN RETURN FALSE; END IF;
    ELSIF r.tax_applicability IN ('NOT_APPLICABLE', 'EXEMPT') THEN
        IF r.tax_type <> 'NONE'
           OR r.tax_treatment <> 'NONE'
           OR r.tax_basis <> 'NONE'
           OR r.tax_rounding <> 'NONE'
        THEN RETURN FALSE; END IF;
    ELSIF r.tax_applicability = 'APPLICABLE' THEN
        IF r.tax_type = 'NONE'
           OR r.tax_treatment = 'NONE'
           OR r.tax_basis = 'NONE'
           OR r.tax_rounding = 'NONE'
           OR (r.tax_type = 'FIXED_PER_SESSION'
               AND r.tax_treatment = 'INCLUSIVE')
        THEN RETURN FALSE; END IF;
    END IF;

    IF r.cancellation_eligibility <> 'BEFORE_START'
       AND r.cancellation_cutoff_minutes IS NOT NULL
    THEN RETURN FALSE; END IF;

    IF r.refund_eligibility = 'UNRESOLVED' THEN
        IF r.refund_calculation <> 'UNRESOLVED'
           OR r.refund_basis <> 'UNRESOLVED'
           OR r.refund_destination <> 'UNRESOLVED'
        THEN RETURN FALSE; END IF;
    ELSIF r.refund_eligibility = 'NONE' THEN
        IF r.refund_calculation <> 'NONE'
           OR r.refund_basis <> 'NONE'
           OR r.refund_destination <> 'NONE'
        THEN RETURN FALSE; END IF;
    ELSE
        IF r.refund_calculation = 'NONE'
           OR r.refund_basis = 'NONE'
           OR r.refund_destination = 'NONE'
        THEN RETURN FALSE; END IF;
    END IF;

    FOR f IN SELECT value FROM jsonb_array_elements(r.fees) LOOP
        IF jsonb_typeof(f) IS DISTINCT FROM 'object' THEN
            RETURN FALSE;
        END IF;

        IF (
            f ?& ARRAY[
                'method', 'channel', 'type',
                'amountMinor', 'currency', 'refundability'
            ]::TEXT[]
            AND
            (f - ARRAY[
                'method', 'channel', 'type',
                'amountMinor', 'currency', 'refundability'
            ]::TEXT[]) = '{}'::JSONB
            AND f->>'method' = ANY(r.payment_methods)
            AND f->>'channel' = ANY(r.fee_channels)
            AND f->>'type' IN ('UNRESOLVED', 'NONE', 'FIXED_MINOR')
            AND f->>'currency' = 'LKR'
            AND f->>'refundability' IN (
                'UNRESOLVED', 'REFUNDABLE', 'NON_REFUNDABLE'
            )
        ) IS NOT TRUE THEN RETURN FALSE; END IF;

        pair_key := (f->>'method') || ':' || (f->>'channel');
        IF pair_key = ANY(seen) THEN RETURN FALSE; END IF;
        seen := array_append(seen, pair_key);

        IF f->'amountMinor' <> 'null'::JSONB THEN
            IF jsonb_typeof(f->'amountMinor') IS DISTINCT FROM 'string'
            THEN RETURN FALSE; END IF;

            amount_text := f->>'amountMinor';
            IF amount_text !~ '^(0|[1-9][0-9]*)$'
               OR length(amount_text) > 19
            THEN RETURN FALSE; END IF;

            IF amount_text::NUMERIC > 9223372036854775807
            THEN RETURN FALSE; END IF;
        END IF;

        IF f->>'type' = 'UNRESOLVED'
           AND f->'amountMinor' <> 'null'::JSONB
        THEN RETURN FALSE; END IF;

        IF f->>'type' = 'NONE'
           AND f->'amountMinor' IS DISTINCT FROM '"0"'::JSONB
        THEN RETURN FALSE; END IF;
    END LOOP;

    RETURN TRUE;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_is_complete(
    r cinenest_pricing.commercial_policy_revision
) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    method_name TEXT;
    channel_name TEXT;
BEGIN
    IF cinenest_pricing.commercial_policy_validate_revision(r)
        IS NOT TRUE
    THEN RETURN FALSE; END IF;

    IF r.effective_from_proposal IS NULL
       OR cardinality(r.payment_methods) = 0
       OR cardinality(r.fee_channels) = 0
       OR r.tax_applicability = 'UNRESOLVED'
       OR r.tax_type = 'UNRESOLVED'
       OR r.tax_treatment = 'UNRESOLVED'
       OR r.tax_basis = 'UNRESOLVED'
       OR r.tax_rounding = 'UNRESOLVED'
       OR r.cancellation_eligibility = 'UNRESOLVED'
       OR r.no_show_treatment = 'UNRESOLVED'
       OR r.exception_handling = 'UNRESOLVED'
       OR r.refund_eligibility = 'UNRESOLVED'
       OR r.refund_calculation = 'UNRESOLVED'
       OR r.refund_basis = 'UNRESOLVED'
       OR r.refund_destination = 'UNRESOLVED'
       OR r.non_refundable_components IS NULL
    THEN RETURN FALSE; END IF;

    IF r.tax_type = 'PERCENTAGE'
       AND r.tax_rate_basis_points IS NULL
    THEN RETURN FALSE; END IF;

    IF r.tax_type = 'FIXED_PER_SESSION'
       AND r.tax_amount_minor IS NULL
    THEN RETURN FALSE; END IF;

    IF r.cancellation_eligibility = 'BEFORE_START'
       AND r.cancellation_cutoff_minutes IS NULL
    THEN RETURN FALSE; END IF;

    IF r.refund_calculation = 'PERCENTAGE'
       AND r.refund_rate_basis_points IS NULL
    THEN RETURN FALSE; END IF;

    IF r.refund_calculation = 'FIXED_MINOR'
       AND r.refund_amount_minor IS NULL
    THEN RETURN FALSE; END IF;

    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(r.fees) AS t(f)
        WHERE f->>'type' = 'UNRESOLVED'
           OR f->>'refundability' = 'UNRESOLVED'
           OR f->'amountMinor' = 'null'::JSONB
    ) THEN RETURN FALSE; END IF;

    FOREACH method_name IN ARRAY r.payment_methods LOOP
        FOREACH channel_name IN ARRAY r.fee_channels LOOP
            IF NOT EXISTS (
                SELECT 1 FROM jsonb_array_elements(r.fees) AS t(f)
                WHERE f->>'method' = method_name
                  AND f->>'channel' = channel_name
            ) THEN RETURN FALSE; END IF;
        END LOOP;
    END LOOP;

    RETURN TRUE;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_immutable_history()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
BEGIN
    -- Empty test-schema reset is permitted; any retained policy evidence still blocks TRUNCATE.
    IF TG_OP = 'TRUNCATE'
       AND current_setting('transaction_isolation') = 'read committed'
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_draft)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_revision)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_review)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_command)
       AND NOT EXISTS (SELECT 1 FROM cinenest_pricing.commercial_policy_audit)
    THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Commercial policy history is immutable'
        USING ERRCODE = '23514';
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_guard_head()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    package_venue UUID;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.current_revision <> 1 THEN
            RAISE EXCEPTION 'Initial policy revision must be one'
                USING ERRCODE = '23514';
        END IF;
        NEW.created_at := clock_timestamp();

        IF NEW.package_id IS NOT NULL THEN
            SELECT venue_id INTO package_venue
            FROM cinenest_pricing.package
            WHERE package_id = NEW.package_id
            FOR SHARE;

            IF NOT FOUND
               OR package_venue IS DISTINCT FROM NEW.venue_id
            THEN
                RAISE EXCEPTION 'Package venue mismatch'
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    ELSE
        IF NEW.current_revision::NUMERIC
               <> OLD.current_revision::NUMERIC + 1
           OR (to_jsonb(NEW) - 'current_revision')
               IS DISTINCT FROM
              (to_jsonb(OLD) - 'current_revision')
        THEN
            RAISE EXCEPTION 'Policy head mutation rejected'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_guard_command()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    a cinenest_identity.account%ROWTYPE;
    g cinenest_identity.access_grant%ROWTYPE;
    h cinenest_pricing.commercial_policy_draft%ROWTYPE;
    r cinenest_pricing.commercial_policy_revision%ROWTYPE;
    required_permission TEXT;
    current_epoch BIGINT;
    admitted TIMESTAMPTZ;
BEGIN
    SELECT * INTO a
    FROM cinenest_identity.account
    WHERE account_id = NEW.actor_id
    FOR UPDATE;

    IF NOT FOUND
       OR a.synthetic IS DISTINCT FROM TRUE
       OR a.eligibility IS DISTINCT FROM 'ELIGIBLE'
       OR a.revision IS DISTINCT FROM NEW.account_revision
    THEN
        RAISE EXCEPTION 'Current synthetic identity required'
            USING ERRCODE = '28000';
    END IF;

    SELECT coalesce(max(credential_revision), 0)
    INTO current_epoch
    FROM cinenest_identity.manual_credential_version
    WHERE account_id = NEW.actor_id;

    IF current_epoch IS DISTINCT FROM NEW.expected_credential_epoch
    THEN
        RAISE EXCEPTION 'Credential epoch changed'
            USING ERRCODE = '28000';
    END IF;

    PERFORM account_id
    FROM cinenest_identity.account_scope
    WHERE account_id = NEW.actor_id AND venue_id = NEW.venue_id
    FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Exact venue membership required'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO g
    FROM cinenest_identity.access_grant
    WHERE grant_id = NEW.grant_id
    FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Applicable grant required'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO h
    FROM cinenest_pricing.commercial_policy_draft
    WHERE policy_id = NEW.policy_id
    FOR UPDATE;

    IF NOT FOUND OR h.venue_id IS DISTINCT FROM NEW.venue_id THEN
        RAISE EXCEPTION 'Policy scope mismatch'
            USING ERRCODE = '42501';
    END IF;

    required_permission :=
        CASE WHEN NEW.operation = 'REVIEW'
             THEN 'commercial.policy.review.' || lower(NEW.review_domain)
             ELSE 'commercial.policy.edit'
        END;

    admitted := clock_timestamp();

    IF g.account_id IS DISTINCT FROM NEW.actor_id
       OR g.venue_id IS DISTINCT FROM NEW.venue_id
       OR g.scope_kind IS DISTINCT FROM 'VENUE'
       OR g.permission IS DISTINCT FROM required_permission
       OR g.state IS DISTINCT FROM 'ACTIVE'
       OR g.revision IS DISTINCT FROM NEW.grant_revision
       OR g.valid_from > admitted
       OR g.valid_until <= admitted
    THEN
        RAISE EXCEPTION 'Current matching grant required'
            USING ERRCODE = '42501';
    END IF;

    IF NEW.operation = 'CREATE' THEN
        IF h.created_command_id IS DISTINCT FROM NEW.command_id
           OR h.created_by IS DISTINCT FROM NEW.actor_id
           OR EXISTS (
               SELECT 1
               FROM cinenest_pricing.commercial_policy_revision
               WHERE policy_id = NEW.policy_id
           )
        THEN
            RAISE EXCEPTION 'Policy creation conflict'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        IF h.current_revision IS DISTINCT FROM NEW.expected_revision THEN
            RAISE EXCEPTION 'Policy revision changed'
                USING ERRCODE = '40001';
        END IF;

        IF NEW.operation = 'REVIEW' THEN
            SELECT * INTO r
            FROM cinenest_pricing.commercial_policy_revision
            WHERE policy_id = NEW.policy_id
              AND revision = h.current_revision;

            IF NOT FOUND
               OR r.content_hash IS DISTINCT FROM NEW.expected_content_hash
               OR r.submitted_for_review IS DISTINCT FROM TRUE
               OR cinenest_pricing.commercial_policy_is_complete(r)
                    IS NOT TRUE
            THEN
                RAISE EXCEPTION 'Current submitted revision required'
                    USING ERRCODE = '40001';
            END IF;
        END IF;
    END IF;

    NEW.grant_permission := g.permission;
    NEW.grant_valid_from := g.valid_from;
    NEW.grant_valid_until := g.valid_until;
    NEW.admitted_at := admitted;
    RETURN NEW;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_guard_revision()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    c cinenest_pricing.commercial_policy_command%ROWTYPE;
    current_zone TEXT;
    hash_document JSONB;
BEGIN
    SELECT * INTO c
    FROM cinenest_pricing.commercial_policy_command
    WHERE command_id = NEW.command_id;

    IF NOT FOUND
       OR c.operation NOT IN ('CREATE', 'REVISE')
       OR c.policy_id IS DISTINCT FROM NEW.policy_id
       OR c.venue_id IS DISTINCT FROM NEW.venue_id
       OR c.result_revision IS DISTINCT FROM NEW.revision
       OR c.actor_id IS DISTINCT FROM NEW.authored_by
    THEN
        RAISE EXCEPTION 'Revision command mismatch'
            USING ERRCODE = '23514';
    END IF;

    SELECT timezone INTO current_zone
    FROM cinenest_rooms.venue
    WHERE venue_id = NEW.venue_id
    FOR SHARE;

    IF NOT FOUND OR current_zone IS DISTINCT FROM NEW.venue_timezone THEN
        RAISE EXCEPTION 'Venue timezone mismatch'
            USING ERRCODE = '23514';
    END IF;

    NEW.recorded_at := c.admitted_at;
    NEW.content_hash := NULL;

    IF cinenest_pricing.commercial_policy_validate_revision(NEW)
        IS NOT TRUE
    THEN
        RAISE EXCEPTION 'Invalid policy revision'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.submitted_for_review
       AND cinenest_pricing.commercial_policy_is_complete(NEW)
            IS NOT TRUE
    THEN
        RAISE EXCEPTION 'Incomplete policy cannot enter review'
            USING ERRCODE = '23514';
    END IF;

    -- Remove timezone-dependent timestamp rendering.
    hash_document :=
        (to_jsonb(NEW) - ARRAY[
            'content_hash', 'recorded_at',
            'effective_from_proposal', 'effective_until_proposal'
        ]::TEXT[])
        ||
        jsonb_build_object(
            'hashFormat', 'PG_JSONB_UTF8_EPOCH_V1',
            'recordedAtEpoch', extract(epoch FROM NEW.recorded_at),
            'effectiveFromEpoch',
                extract(epoch FROM NEW.effective_from_proposal),
            'effectiveUntilEpoch',
                extract(epoch FROM NEW.effective_until_proposal)
        );

    NEW.content_hash :=
        encode(sha256(convert_to(hash_document::TEXT, 'UTF8')), 'hex');

    RETURN NEW;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_guard_review()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    c cinenest_pricing.commercial_policy_command%ROWTYPE;
    h cinenest_pricing.commercial_policy_draft%ROWTYPE;
    r cinenest_pricing.commercial_policy_revision%ROWTYPE;
BEGIN
    SELECT * INTO c
    FROM cinenest_pricing.commercial_policy_command
    WHERE command_id = NEW.command_id;

    IF NOT FOUND
       OR c.operation IS DISTINCT FROM 'REVIEW'
       OR c.result_review_id IS DISTINCT FROM NEW.review_id
       OR c.policy_id IS DISTINCT FROM NEW.policy_id
       OR c.venue_id IS DISTINCT FROM NEW.venue_id
       OR c.result_revision IS DISTINCT FROM NEW.revision
       OR c.review_domain IS DISTINCT FROM NEW.domain
       OR c.actor_id IS DISTINCT FROM NEW.reviewer_id
       OR c.outcome IS DISTINCT FROM NEW.outcome
       OR c.reason IS DISTINCT FROM NEW.reason
       OR c.expected_content_hash IS DISTINCT FROM NEW.content_hash
    THEN
        RAISE EXCEPTION 'Review command mismatch'
            USING ERRCODE = '23514';
    END IF;

    SELECT * INTO h
    FROM cinenest_pricing.commercial_policy_draft
    WHERE policy_id = NEW.policy_id
    FOR UPDATE;

    SELECT * INTO r
    FROM cinenest_pricing.commercial_policy_revision
    WHERE policy_id = NEW.policy_id AND revision = NEW.revision;

    IF h.current_revision IS DISTINCT FROM NEW.revision
       OR r.content_hash IS DISTINCT FROM NEW.content_hash
       OR r.submitted_for_review IS DISTINCT FROM TRUE
       OR cinenest_pricing.commercial_policy_is_complete(r) IS NOT TRUE
    THEN
        RAISE EXCEPTION 'Review revision is stale or incomplete'
            USING ERRCODE = '40001';
    END IF;

    IF h.created_by = NEW.reviewer_id OR EXISTS (
        SELECT 1
        FROM cinenest_pricing.commercial_policy_revision
        WHERE policy_id = NEW.policy_id
          AND revision <= NEW.revision
          AND authored_by = NEW.reviewer_id
    ) THEN
        RAISE EXCEPTION 'Independent reviewer required'
            USING ERRCODE = '42501';
    END IF;

    NEW.reviewed_at := c.admitted_at;
    RETURN NEW;
END;
$$;


CREATE FUNCTION cinenest_pricing.commercial_policy_require_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, cinenest_pricing
AS $$
DECLARE
    h cinenest_pricing.commercial_policy_draft%ROWTYPE;
    c cinenest_pricing.commercial_policy_command%ROWTYPE;
    r cinenest_pricing.commercial_policy_revision%ROWTYPE;
    v cinenest_pricing.commercial_policy_review%ROWTYPE;
    a cinenest_pricing.commercial_policy_audit%ROWTYPE;
    revision_count BIGINT;
    highest_revision BIGINT;
BEGIN
    IF NEW.policy_id IS NULL THEN
        RETURN NULL; -- Sanitized denial audit, no protected target.
    END IF;

    SELECT * INTO h
    FROM cinenest_pricing.commercial_policy_draft
    WHERE policy_id = NEW.policy_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Policy head missing' USING ERRCODE = '23514';
    END IF;

    SELECT count(*), max(revision)
    INTO revision_count, highest_revision
    FROM cinenest_pricing.commercial_policy_revision
    WHERE policy_id = h.policy_id;

    IF highest_revision IS DISTINCT FROM h.current_revision
       OR revision_count IS DISTINCT FROM h.current_revision
    THEN
        RAISE EXCEPTION 'Policy revision chain incomplete'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM cinenest_pricing.commercial_policy_command
        WHERE command_id = h.created_command_id
          AND policy_id = h.policy_id
          AND venue_id = h.venue_id
          AND actor_id = h.created_by
          AND operation = 'CREATE'
          AND result_revision = 1
    ) THEN
        RAISE EXCEPTION 'Creation receipt missing'
            USING ERRCODE = '23514';
    END IF;

    FOR c IN
        SELECT *
        FROM cinenest_pricing.commercial_policy_command
        WHERE policy_id = h.policy_id
    LOOP
        SELECT * INTO a
        FROM cinenest_pricing.commercial_policy_audit
        WHERE command_id = c.command_id;

        IF NOT FOUND
           OR a.policy_id IS DISTINCT FROM c.policy_id
           OR a.venue_id IS DISTINCT FROM c.venue_id
           OR a.revision IS DISTINCT FROM c.result_revision
           OR a.actor_id IS DISTINCT FROM c.actor_id
           OR a.operation IS DISTINCT FROM c.operation
           OR a.outcome IS DISTINCT FROM c.outcome
           OR a.reason IS DISTINCT FROM c.reason
           OR a.occurred_at IS DISTINCT FROM c.admitted_at
        THEN
            RAISE EXCEPTION 'Mandatory attributable audit missing'
                USING ERRCODE = '23514';
        END IF;

        IF c.operation IN ('CREATE', 'REVISE') THEN
            SELECT * INTO r
            FROM cinenest_pricing.commercial_policy_revision
            WHERE command_id = c.command_id;

            IF NOT FOUND
               OR r.policy_id IS DISTINCT FROM c.policy_id
               OR r.venue_id IS DISTINCT FROM c.venue_id
               OR r.revision IS DISTINCT FROM c.result_revision
               OR r.authored_by IS DISTINCT FROM c.actor_id
               OR r.recorded_at IS DISTINCT FROM c.admitted_at
            THEN
                RAISE EXCEPTION 'Revision receipt mismatch'
                    USING ERRCODE = '23514';
            END IF;
        ELSE
            SELECT * INTO v
            FROM cinenest_pricing.commercial_policy_review
            WHERE command_id = c.command_id;

            IF NOT FOUND
               OR v.review_id IS DISTINCT FROM c.result_review_id
               OR v.policy_id IS DISTINCT FROM c.policy_id
               OR v.venue_id IS DISTINCT FROM c.venue_id
               OR v.revision IS DISTINCT FROM c.result_revision
               OR v.content_hash IS DISTINCT FROM c.expected_content_hash
               OR v.domain IS DISTINCT FROM c.review_domain
               OR v.reviewer_id IS DISTINCT FROM c.actor_id
               OR v.outcome IS DISTINCT FROM c.outcome
               OR v.reason IS DISTINCT FROM c.reason
               OR v.reviewed_at IS DISTINCT FROM c.admitted_at
            THEN
                RAISE EXCEPTION 'Review receipt mismatch'
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    END LOOP;

    RETURN NULL;
END;
$$;


CREATE TRIGGER commercial_policy_head_guard
BEFORE INSERT OR UPDATE
ON cinenest_pricing.commercial_policy_draft
FOR EACH ROW
EXECUTE FUNCTION cinenest_pricing.commercial_policy_guard_head();

CREATE TRIGGER commercial_policy_command_guard
BEFORE INSERT
ON cinenest_pricing.commercial_policy_command
FOR EACH ROW
EXECUTE FUNCTION cinenest_pricing.commercial_policy_guard_command();

CREATE TRIGGER commercial_policy_revision_guard
BEFORE INSERT
ON cinenest_pricing.commercial_policy_revision
FOR EACH ROW
EXECUTE FUNCTION cinenest_pricing.commercial_policy_guard_revision();

CREATE TRIGGER commercial_policy_review_guard
BEFORE INSERT
ON cinenest_pricing.commercial_policy_review
FOR EACH ROW
EXECUTE FUNCTION cinenest_pricing.commercial_policy_guard_review();

CREATE TRIGGER commercial_policy_head_no_delete
BEFORE DELETE OR TRUNCATE
ON cinenest_pricing.commercial_policy_draft
FOR EACH STATEMENT
EXECUTE FUNCTION cinenest_pricing.commercial_policy_immutable_history();

DO $$
DECLARE
    table_name TEXT;
    events TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'commercial_policy_revision',
        'commercial_policy_review',
        'commercial_policy_command',
        'commercial_policy_audit'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I
             BEFORE UPDATE OR DELETE OR TRUNCATE
             ON cinenest_pricing.%I
             FOR EACH STATEMENT
             EXECUTE FUNCTION
               cinenest_pricing.commercial_policy_immutable_history()',
            table_name || '_immutable', table_name
        );
    END LOOP;

    FOREACH table_name IN ARRAY ARRAY[
        'commercial_policy_draft',
        'commercial_policy_revision',
        'commercial_policy_review',
        'commercial_policy_command',
        'commercial_policy_audit'
    ] LOOP
        events := CASE
            WHEN table_name = 'commercial_policy_draft'
            THEN 'INSERT OR UPDATE'
            ELSE 'INSERT'
        END;

        EXECUTE format(
            'CREATE CONSTRAINT TRIGGER %I
             AFTER %s ON cinenest_pricing.%I
             DEFERRABLE INITIALLY DEFERRED
             FOR EACH ROW
             EXECUTE FUNCTION
               cinenest_pricing.commercial_policy_require_integrity()',
            table_name || '_integrity', events, table_name
        );
    END LOOP;
END;
$$;

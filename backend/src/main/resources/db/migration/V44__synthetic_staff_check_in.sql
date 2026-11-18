-- Booking-owned receipt and immutable audit of a synthetic software decision.
CREATE TABLE cinenest_booking.synthetic_staff_check_in (
    actor_id UUID NOT NULL,
    command_key UUID NOT NULL,
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    booking_id UUID NOT NULL REFERENCES cinenest_booking.guest_roster(booking_id),
    booking_revision BIGINT NOT NULL CHECK (booking_revision > 0),
    roster_revision BIGINT NOT NULL CHECK (roster_revision >= 0),
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    policy_version TEXT NOT NULL CHECK (policy_version = 'SYNTHETIC-STAFF-ADMISSION-V1'),
    outcome TEXT NOT NULL CHECK (outcome = 'SYNTHETIC_CHECK_IN_RECORDED'),
    recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (actor_id, command_key),
    FOREIGN KEY (booking_id, booking_revision)
        REFERENCES cinenest_booking.booking_revision (booking_id, revision),
    UNIQUE (booking_id, booking_revision, roster_revision, policy_version)
);

CREATE FUNCTION cinenest_booking.guard_synthetic_staff_check_in()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    booking cinenest_booking.booking;
    purchase cinenest_booking.booking_revision;
    current_roster_revision BIGINT;
    session_start TIMESTAMPTZ;
    session_end TIMESTAMPTZ;
BEGIN
    -- Other Booking mutations serialize on this row before writing revisions.
    SELECT * INTO booking FROM cinenest_booking.booking
        WHERE booking_id = NEW.booking_id FOR UPDATE;
    IF NOT FOUND OR NOT booking.synthetic OR booking.state <> 'SIMULATED_CONFIRMED'
        OR booking.venue_id IS DISTINCT FROM NEW.venue_id THEN
        RAISE EXCEPTION 'Synthetic check-in booking scope invalid' USING ERRCODE = '23514';
    END IF;

    SELECT * INTO purchase FROM cinenest_booking.booking_revision
        WHERE booking_id = NEW.booking_id ORDER BY revision DESC LIMIT 1;
    IF NOT FOUND OR purchase.revision IS DISTINCT FROM NEW.booking_revision
        OR (purchase.snapshot->>'roomId')::UUID IS DISTINCT FROM NEW.room_id THEN
        RAISE EXCEPTION 'Synthetic check-in booking revision or room invalid' USING ERRCODE = '23514';
    END IF;

    SELECT revision INTO current_roster_revision FROM cinenest_booking.guest_roster
        WHERE booking_id = NEW.booking_id FOR UPDATE;
    IF NOT FOUND OR current_roster_revision IS DISTINCT FROM NEW.roster_revision THEN
        RAISE EXCEPTION 'Synthetic check-in roster revision invalid' USING ERRCODE = '23514';
    END IF;

    session_start := (purchase.snapshot->>'sessionStart')::TIMESTAMPTZ;
    session_end := (purchase.snapshot->>'sessionEnd')::TIMESTAMPTZ;
    NEW.recorded_at := clock_timestamp();
    IF session_start IS NULL OR session_end IS NULL OR session_start >= session_end
        OR NEW.recorded_at < session_start OR NEW.recorded_at >= session_end THEN
        RAISE EXCEPTION 'Synthetic check-in outside purchased session' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER synthetic_staff_check_in_scope
    BEFORE INSERT ON cinenest_booking.synthetic_staff_check_in
    FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_synthetic_staff_check_in();

CREATE TRIGGER synthetic_staff_check_in_immutable
    BEFORE UPDATE OR DELETE ON cinenest_booking.synthetic_staff_check_in
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

CREATE FUNCTION cinenest_booking.guard_synthetic_staff_check_in_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- PostgreSQL holds an ACCESS EXCLUSIVE lock while executing this trigger.
    -- A repeatable-read or serializable transaction may retain a stale snapshot.
    IF current_setting('transaction_isolation') <> 'read committed'
        OR EXISTS (SELECT 1 FROM cinenest_booking.synthetic_staff_check_in) THEN
        RAISE EXCEPTION 'Synthetic check-in history is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER synthetic_staff_check_in_no_history_truncate
    BEFORE TRUNCATE ON cinenest_booking.synthetic_staff_check_in
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.guard_synthetic_staff_check_in_truncate();

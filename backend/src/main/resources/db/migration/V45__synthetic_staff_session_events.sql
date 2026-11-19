-- PROPOSED only. Requires migration and bounded policy admission before application.
-- Booking owns immutable synthetic events. Application authorization consults
-- Identity through its published contract, with grant provenance captured here.
CREATE TABLE cinenest_booking.synthetic_staff_session_event (
    event_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    booking_revision BIGINT NOT NULL CHECK (booking_revision > 0),
    roster_revision BIGINT NOT NULL CHECK (roster_revision >= 0),
    venue_id UUID NOT NULL,
    room_id UUID NOT NULL,
    check_in_actor_id UUID NOT NULL,
    check_in_command_key UUID NOT NULL,
    actor_id UUID NOT NULL,
    command_key UUID NOT NULL,
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    policy_version TEXT NOT NULL CHECK (length(btrim(policy_version)) BETWEEN 1 AND 64),
    authorization_grant_id UUID NOT NULL,
    authorization_grant_revision BIGINT NOT NULL CHECK (authorization_grant_revision >= 0),
    event_type TEXT NOT NULL CHECK (event_type IN ('START', 'CHECKOUT')),
    start_event_id UUID REFERENCES cinenest_booking.synthetic_staff_session_event(event_id),
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (actor_id, command_key),
    UNIQUE (booking_id, booking_revision, event_type),
    FOREIGN KEY (booking_id, booking_revision)
        REFERENCES cinenest_booking.booking_revision(booking_id, revision),
    FOREIGN KEY (check_in_actor_id, check_in_command_key)
        REFERENCES cinenest_booking.synthetic_staff_check_in(actor_id, command_key),
    CHECK ((event_type = 'CHECKOUT') = (start_event_id IS NOT NULL))
);

CREATE FUNCTION cinenest_booking.guard_synthetic_staff_session_event()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    current_booking cinenest_booking.booking%ROWTYPE;
    purchase cinenest_booking.booking_revision%ROWTYPE;
    check_in cinenest_booking.synthetic_staff_check_in%ROWTYPE;
    started cinenest_booking.synthetic_staff_session_event%ROWTYPE;
    current_roster_revision BIGINT;
    session_start TIMESTAMPTZ;
    session_end TIMESTAMPTZ;
BEGIN
    -- Fresh statement snapshots are required after waiting on Booking's lock.
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Synthetic staff session events require READ COMMITTED'
            USING ERRCODE = '23514';
    END IF;

    SELECT * INTO current_booking FROM cinenest_booking.booking
        WHERE booking_id = NEW.booking_id FOR UPDATE;
    IF NOT FOUND OR NOT current_booking.synthetic
        OR current_booking.state <> 'SIMULATED_CONFIRMED'
        OR current_booking.venue_id IS DISTINCT FROM NEW.venue_id THEN
        RAISE EXCEPTION 'Synthetic staff session booking scope invalid'
            USING ERRCODE = '23514';
    END IF;

    SELECT * INTO purchase FROM cinenest_booking.booking_revision
        WHERE booking_id = NEW.booking_id ORDER BY revision DESC LIMIT 1;
    IF NOT FOUND OR purchase.revision IS DISTINCT FROM NEW.booking_revision
        OR (purchase.snapshot->>'roomId')::UUID IS DISTINCT FROM NEW.room_id THEN
        RAISE EXCEPTION 'Synthetic staff session booking revision or room invalid'
            USING ERRCODE = '23514';
    END IF;

    SELECT revision INTO current_roster_revision FROM cinenest_booking.guest_roster
        WHERE booking_id = NEW.booking_id FOR UPDATE;
    IF NOT FOUND OR current_roster_revision IS DISTINCT FROM NEW.roster_revision THEN
        RAISE EXCEPTION 'Synthetic staff session roster revision invalid'
            USING ERRCODE = '23514';
    END IF;

    SELECT * INTO check_in FROM cinenest_booking.synthetic_staff_check_in
        WHERE actor_id = NEW.check_in_actor_id AND command_key = NEW.check_in_command_key;
    IF NOT FOUND OR ROW(check_in.booking_id, check_in.booking_revision, check_in.roster_revision,
                         check_in.venue_id, check_in.room_id)
        IS DISTINCT FROM ROW(NEW.booking_id, NEW.booking_revision, NEW.roster_revision,
                             NEW.venue_id, NEW.room_id) THEN
        RAISE EXCEPTION 'Synthetic staff session check-in scope invalid'
            USING ERRCODE = '23514';
    END IF;

    session_start := (purchase.snapshot->>'sessionStart')::TIMESTAMPTZ;
    session_end := (purchase.snapshot->>'sessionEnd')::TIMESTAMPTZ;
    NEW.recorded_at := clock_timestamp();
    IF session_start IS NULL OR session_end IS NULL OR session_start >= session_end
        OR NEW.recorded_at < check_in.recorded_at THEN
        RAISE EXCEPTION 'Synthetic staff session has invalid time context'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.event_type = 'START' THEN
        IF NEW.recorded_at < session_start OR NEW.recorded_at >= session_end THEN
            RAISE EXCEPTION 'Synthetic staff session start outside purchased window'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.event_type = 'CHECKOUT' THEN
        SELECT * INTO started FROM cinenest_booking.synthetic_staff_session_event
            WHERE booking_id = NEW.booking_id AND booking_revision = NEW.booking_revision
              AND event_type = 'START';
        IF NOT FOUND OR ROW(started.roster_revision, started.venue_id, started.room_id,
                            started.check_in_actor_id, started.check_in_command_key)
            IS DISTINCT FROM ROW(NEW.roster_revision, NEW.venue_id, NEW.room_id,
                                 NEW.check_in_actor_id, NEW.check_in_command_key)
            OR NEW.recorded_at < started.recorded_at THEN
            RAISE EXCEPTION 'Synthetic staff session requires a matching start'
                USING ERRCODE = '23514';
        END IF;
        NEW.start_event_id := started.event_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER synthetic_staff_session_event_scope
    BEFORE INSERT ON cinenest_booking.synthetic_staff_session_event
    FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_synthetic_staff_session_event();

CREATE TRIGGER synthetic_staff_session_event_immutable
    BEFORE UPDATE OR DELETE ON cinenest_booking.synthetic_staff_session_event
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

CREATE FUNCTION cinenest_booking.guard_synthetic_staff_session_event_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- A TRUNCATE CASCADE takes ACCESS EXCLUSIVE before entering this trigger.
    IF current_setting('transaction_isolation') <> 'read committed'
        OR EXISTS (SELECT 1 FROM cinenest_booking.synthetic_staff_session_event) THEN
        RAISE EXCEPTION 'Synthetic staff session event history is immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER synthetic_staff_session_event_no_history_truncate
    BEFORE TRUNCATE ON cinenest_booking.synthetic_staff_session_event
    FOR EACH STATEMENT EXECUTE FUNCTION cinenest_booking.guard_synthetic_staff_session_event_truncate();

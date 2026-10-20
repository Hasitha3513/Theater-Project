-- Booking owns these append-only synthetic records. No admission/identity authority.
CREATE TABLE cinenest_booking.guest_roster (
 booking_id UUID PRIMARY KEY REFERENCES cinenest_booking.booking,
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE cinenest_booking.guest_entry (
 entry_id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES cinenest_booking.guest_roster,
 kind TEXT NOT NULL CHECK(kind IN('BOOKER','COMPANION')), label TEXT NOT NULL,
 booker_account_id UUID,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(booking_id,label),
 CHECK((kind='BOOKER' AND label='BOOKER' AND booker_account_id IS NOT NULL) OR
       (kind='COMPANION' AND label ~ '^SYNTHETIC-[1-9][0-9]{0,5}$' AND booker_account_id IS NULL))
);
CREATE UNIQUE INDEX one_roster_booker ON cinenest_booking.guest_entry(booking_id) WHERE kind='BOOKER';
CREATE TABLE cinenest_booking.guest_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,booking_id UUID NOT NULL REFERENCES cinenest_booking.guest_roster,
 payload_hash CHAR(64) NOT NULL,PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_booking.guest_audit (
 event_id UUID PRIMARY KEY,actor_id UUID NOT NULL,booking_id UUID NOT NULL REFERENCES cinenest_booking.booking,
 action TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER guest_entry_immutable BEFORE UPDATE OR DELETE ON cinenest_booking.guest_entry FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER guest_command_immutable BEFORE UPDATE OR DELETE ON cinenest_booking.guest_command FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER guest_audit_immutable BEFORE UPDATE OR DELETE ON cinenest_booking.guest_audit FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE FUNCTION cinenest_booking.guard_guest_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b cinenest_booking.booking; purchased INTEGER;
BEGIN
 SELECT * INTO b FROM cinenest_booking.booking WHERE booking_id=NEW.booking_id FOR UPDATE;
 SELECT (snapshot->>'guests')::INTEGER INTO purchased FROM cinenest_booking.booking_revision WHERE booking_id=NEW.booking_id ORDER BY revision DESC LIMIT 1;
 IF NOT b.synthetic OR b.state<>'SIMULATED_CONFIRMED' OR purchased IS NULL OR
 (NEW.kind='BOOKER' AND NEW.booker_account_id<>b.owner_id) OR
 (NEW.kind='COMPANION' AND NOT EXISTS(SELECT 1 FROM cinenest_booking.guest_entry WHERE booking_id=NEW.booking_id AND kind='BOOKER')) OR
 (SELECT count(*) FROM cinenest_booking.guest_entry WHERE booking_id=NEW.booking_id)>=purchased
 THEN RAISE EXCEPTION 'Invalid synthetic roster binding or purchased capacity'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guest_entry_guard BEFORE INSERT ON cinenest_booking.guest_entry FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_guest_entry();
CREATE FUNCTION cinenest_booking.guard_guest_roster() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.booking_id<>OLD.booking_id OR NEW.created_at<>OLD.created_at OR NEW.revision<>OLD.revision+1
 THEN RAISE EXCEPTION 'Immutable roster binding'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER guest_roster_guard BEFORE UPDATE ON cinenest_booking.guest_roster FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_guest_roster();
CREATE TRIGGER guest_roster_no_delete BEFORE DELETE ON cinenest_booking.guest_roster FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE FUNCTION cinenest_booking.check_guest_booker() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (SELECT count(*) FROM cinenest_booking.guest_entry WHERE booking_id=NEW.booking_id AND kind='BOOKER')<>1
 THEN RAISE EXCEPTION 'Exactly one server-bound booker required'; END IF; RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER guest_booker_complete AFTER INSERT OR UPDATE ON cinenest_booking.guest_roster DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_booking.check_guest_booker();

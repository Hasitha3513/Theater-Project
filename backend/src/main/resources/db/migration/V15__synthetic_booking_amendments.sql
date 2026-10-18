-- Booking owns the amendment lifecycle. Original purchases and financial history remain immutable.
CREATE TABLE cinenest_booking.booking_amendment (
 amendment_id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES cinenest_booking.booking,
 owner_id UUID NOT NULL,venue_id UUID NOT NULL,original_revision BIGINT NOT NULL CHECK(original_revision=1),
 target_hold UUID NOT NULL UNIQUE REFERENCES cinenest_booking.reservation_hold,
 quote_id UUID NOT NULL,quote_revision BIGINT NOT NULL CHECK(quote_revision>0),quote_hash CHAR(64) NOT NULL,
 proposal_hash CHAR(64) NOT NULL,proposed_snapshot JSONB NOT NULL,expires_at TIMESTAMPTZ NOT NULL,
 state TEXT NOT NULL CHECK(state IN('PROPOSED','CONFIRMED','WITHDRAWN','EXPIRED')),
 revision BIGINT NOT NULL CHECK(revision>=0),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 accepted_at TIMESTAMPTZ,accepted_hash CHAR(64),committed_revision BIGINT,
 CHECK((state='CONFIRMED')=(accepted_at IS NOT NULL AND accepted_hash IS NOT NULL AND committed_revision IS NOT NULL)),
 CHECK(state='CONFIRMED' OR (accepted_at IS NULL AND accepted_hash IS NULL AND committed_revision IS NULL)),
 CHECK(committed_revision IS NULL OR committed_revision=2),CHECK(accepted_hash IS NULL OR accepted_hash=proposal_hash),
 CHECK(expires_at>created_at),CHECK(accepted_at IS NULL OR accepted_at<expires_at)
);
CREATE UNIQUE INDEX one_live_amendment ON cinenest_booking.booking_amendment(booking_id) WHERE state='PROPOSED';
CREATE UNIQUE INDEX one_confirmed_amendment ON cinenest_booking.booking_amendment(booking_id) WHERE state='CONFIRMED';
CREATE TABLE cinenest_booking.booking_amendment_command (
 actor_id UUID NOT NULL,command_key UUID NOT NULL,amendment_id UUID NOT NULL REFERENCES cinenest_booking.booking_amendment,
 action TEXT NOT NULL CHECK(action IN('CREATE','CONFIRM','WITHDRAW')),payload_hash CHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(actor_id,command_key)
);
CREATE TABLE cinenest_booking.booking_amendment_audit (
 event_id UUID PRIMARY KEY,actor_id UUID,booking_id UUID NOT NULL REFERENCES cinenest_booking.booking,
 amendment_id UUID REFERENCES cinenest_booking.booking_amendment,action TEXT NOT NULL,
 outcome TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_amendment_command BEFORE UPDATE OR DELETE ON cinenest_booking.booking_amendment_command FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_amendment_audit BEFORE UPDATE OR DELETE ON cinenest_booking.booking_amendment_audit FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_amendment_delete BEFORE DELETE ON cinenest_booking.booking_amendment FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE FUNCTION cinenest_booking.guard_amendment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.state<>'PROPOSED' OR NEW.state='PROPOSED' OR NEW.revision<>OLD.revision+1 OR
 (to_jsonb(NEW)-'state'-'revision'-'accepted_at'-'accepted_hash'-'committed_revision')<>(to_jsonb(OLD)-'state'-'revision'-'accepted_at'-'accepted_hash'-'committed_revision')
 THEN RAISE EXCEPTION 'Immutable amendment proposal or terminal history'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER amendment_guard BEFORE UPDATE ON cinenest_booking.booking_amendment FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_amendment();
ALTER TABLE cinenest_booking.booking_revision DROP CONSTRAINT booking_revision_revision_check;
ALTER TABLE cinenest_booking.booking_revision ADD CHECK(revision IN(1,2));
CREATE FUNCTION cinenest_booking.guard_second_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original cinenest_booking.booking_revision; proposal cinenest_booking.booking_amendment;
BEGIN
 IF NEW.revision=1 THEN RETURN NEW; END IF;
 SELECT * INTO original FROM cinenest_booking.booking_revision WHERE booking_id=NEW.booking_id AND revision=1;
 SELECT * INTO proposal FROM cinenest_booking.booking_amendment WHERE booking_id=NEW.booking_id AND state='CONFIRMED';
 IF original.booking_id IS NULL OR proposal.amendment_id IS NULL OR proposal.committed_revision<>2 OR
 NEW.quote_id<>original.quote_id OR NEW.quote_revision<>original.quote_revision OR NEW.quote_hash<>original.quote_hash OR
 NEW.amount_minor<>original.amount_minor OR NEW.currency<>original.currency OR NEW.snapshot<>proposal.proposed_snapshot OR
 (NEW.snapshot->'package') IS DISTINCT FROM (original.snapshot->'package') OR
 (NEW.snapshot->'packageVersionId') IS DISTINCT FROM (original.snapshot->'packageVersionId') OR
 (NEW.snapshot->'lines') IS DISTINCT FROM (original.snapshot->'lines') OR
 (NEW.snapshot->'totalMinor') IS DISTINCT FROM (original.snapshot->'totalMinor') OR
 (NEW.snapshot->'currency') IS DISTINCT FROM (original.snapshot->'currency') OR
 (NEW.snapshot->'guests') IS DISTINCT FROM (original.snapshot->'guests') OR
 (NEW.snapshot->'optionalServiceIds') IS DISTINCT FROM (original.snapshot->'optionalServiceIds')
 THEN RAISE EXCEPTION 'Unsafe purchased revision'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER second_revision_guard BEFORE INSERT ON cinenest_booking.booking_revision FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_second_revision();
ALTER TABLE cinenest_notifications.notification_event DROP CONSTRAINT notification_event_source_check;
ALTER TABLE cinenest_notifications.notification_event ADD CHECK(source IN('PAYMENTS','ACCESS','BOOKING'));
ALTER TABLE cinenest_notifications.notification_event DROP CONSTRAINT notification_event_kind_check;
ALTER TABLE cinenest_notifications.notification_event ADD CHECK(kind IN('BANK_CLAIM_RECEIVED','BANK_REVIEW_COMPLETED','BANK_REVIEW_NEEDS_INFORMATION','BANK_REVIEW_REJECTED','SYNTHETIC_ACCESS_READY','SYNTHETIC_ACCESS_EXPIRED','SYNTHETIC_ACCESS_REVOKED','SYNTHETIC_OPERATION_EXCEPTION','AMENDMENT_CONFIRMED'));
ALTER TABLE cinenest_notifications.notification_event ADD CHECK((source='BOOKING')=(kind='AMENDMENT_CONFIRMED'));
ALTER TABLE cinenest_notifications.notification_intent DROP CONSTRAINT notification_intent_destination_check;
ALTER TABLE cinenest_notifications.notification_intent ADD CHECK(destination IN('/bank','/access','/bookings'));

-- Deferred check observes the complete transaction, never a partially replaced purchase.
CREATE FUNCTION cinenest_booking.check_committed_amendment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='CONFIRMED' AND NOT EXISTS(
  SELECT 1 FROM cinenest_booking.booking_revision r
  JOIN cinenest_booking.booking b USING(booking_id)
  JOIN cinenest_booking.reservation_hold target ON target.hold_id=NEW.target_hold
  JOIN cinenest_booking.inventory_block replacement ON replacement.block_id=target.block_id
  JOIN cinenest_booking.reservation_hold original ON original.hold_id=b.hold_id
  JOIN cinenest_booking.inventory_block retired ON retired.block_id=original.block_id
  WHERE r.booking_id=NEW.booking_id AND r.revision=2 AND r.snapshot=NEW.proposed_snapshot
   AND b.owner_id=NEW.owner_id AND b.venue_id=NEW.venue_id AND target.owner_id=b.owner_id
   AND replacement.venue_id=b.venue_id AND replacement.room_id=retired.room_id
   AND replacement.session_end-replacement.session_start=retired.session_end-retired.session_start
   AND NOT(tstzrange(replacement.session_start,replacement.blocked_until,'[)') && tstzrange(retired.session_start,retired.blocked_until,'[)'))
   AND target.state='CONSUMED' AND replacement.blocking AND replacement.kind='CONFIRMED' AND NOT retired.blocking
 ) THEN RAISE EXCEPTION 'Incomplete atomic amendment'; END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER committed_amendment AFTER INSERT OR UPDATE ON cinenest_booking.booking_amendment DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_booking.check_committed_amendment();

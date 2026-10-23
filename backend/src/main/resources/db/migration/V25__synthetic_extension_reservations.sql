-- Owner-approved synthetic extension coverage only. No purchase or financial writes.
ALTER TABLE cinenest_booking.inventory_block DROP CONSTRAINT inventory_block_kind_check;
ALTER TABLE cinenest_booking.inventory_block ADD CONSTRAINT inventory_block_kind_check CHECK(kind IN('HOLD','CONFIRMED','MAINTENANCE','EXTENSION_HOLD'));
CREATE TABLE cinenest_booking.booking_extension_reservation (
 reservation_id UUID PRIMARY KEY,booking_id UUID NOT NULL,owner_id UUID NOT NULL,venue_id UUID NOT NULL,room_id UUID NOT NULL,
 purchased_revision BIGINT NOT NULL CHECK(purchased_revision=1),lifecycle_version BIGINT NOT NULL CHECK(lifecycle_version=0),
 original_block UUID NOT NULL REFERENCES cinenest_booking.inventory_block,tail_block UUID NOT NULL UNIQUE REFERENCES cinenest_booking.inventory_block,
 purchase_snapshot JSONB NOT NULL,quotation JSONB NOT NULL,configuration TEXT NOT NULL,timezone TEXT NOT NULL,
 guests INTEGER NOT NULL CHECK(guests>0),buffer_minutes INTEGER NOT NULL CHECK(buffer_minutes>=0),
 original_start TIMESTAMPTZ NOT NULL,original_end TIMESTAMPTZ NOT NULL,original_until TIMESTAMPTZ NOT NULL,
 proposed_end TIMESTAMPTZ NOT NULL,proposed_until TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,
 policy_version TEXT NOT NULL CHECK(policy_version='SYNTHETIC_EXTENSION_RESERVATION_V1'),approval_reference TEXT NOT NULL CHECK(length(trim(approval_reference))>0),
 state TEXT NOT NULL CHECK(state IN('ACTIVE','WITHDRAWN','EXPIRED','INVALIDATED')),revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 FOREIGN KEY(booking_id,purchased_revision) REFERENCES cinenest_booking.booking_revision(booking_id,revision),
 CHECK(original_start<original_end AND proposed_end>original_end),
 CHECK(original_until=original_end+buffer_minutes*INTERVAL '1 minute' AND proposed_until=proposed_end+buffer_minutes*INTERVAL '1 minute'),
 CHECK(expires_at>created_at AND expires_at<=created_at+INTERVAL '600 seconds' AND expires_at<=original_start),
 CHECK(quotation ?& ARRAY['currency','pricingBasis','additionalMinor','additionalMinutes','packageVersionId','termsVersion','acknowledged'] AND quotation->>'currency'='LKR' AND quotation->>'pricingBasis'='FROZEN_PURCHASED_TERMS' AND (quotation->>'additionalMinor')::bigint>0 AND (quotation->>'additionalMinutes')::integer>0 AND quotation->>'termsVersion'=policy_version AND quotation->'acknowledged'='true'::jsonb)
);
CREATE UNIQUE INDEX one_active_extension_booking ON cinenest_booking.booking_extension_reservation(booking_id) WHERE state='ACTIVE';
CREATE UNIQUE INDEX one_active_extension_owner ON cinenest_booking.booking_extension_reservation(owner_id) WHERE state='ACTIVE';
CREATE INDEX extension_expiry ON cinenest_booking.booking_extension_reservation(expires_at,reservation_id) WHERE state='ACTIVE';
CREATE TABLE cinenest_booking.booking_extension_command (
 owner_id UUID NOT NULL,command_key UUID NOT NULL,payload_hash CHAR(64) NOT NULL,
 reservation_id UUID NOT NULL REFERENCES cinenest_booking.booking_extension_reservation,
 action TEXT NOT NULL CHECK(action IN('CREATE','WITHDRAW')),result JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(owner_id,command_key)
);
CREATE TABLE cinenest_booking.booking_extension_audit (
 event_id UUID PRIMARY KEY,owner_id UUID,booking_id UUID,reservation_id UUID REFERENCES cinenest_booking.booking_extension_reservation,
 action TEXT NOT NULL CHECK(action IN('ADMISSION','CREATE','READ','REPLAY','WITHDRAWN','EXPIRED','INVALIDATED','DENY')),
 outcome TEXT NOT NULL CHECK(outcome IN('ALLOW','DENY')),reservation_revision BIGINT,
 correlation_id UUID NOT NULL,occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX extension_admission_budget ON cinenest_booking.booking_extension_audit(owner_id,occurred_at) WHERE action='ADMISSION';
CREATE TRIGGER immutable_extension_command BEFORE UPDATE OR DELETE ON cinenest_booking.booking_extension_command FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE TRIGGER immutable_extension_audit BEFORE UPDATE OR DELETE ON cinenest_booking.booking_extension_audit FOR EACH ROW EXECUTE FUNCTION cinenest_booking.reject_history_mutation();
CREATE FUNCTION cinenest_booking.guard_extension_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Extension history immutable'; END IF;
 IF OLD.state<>'ACTIVE' OR NEW.state='ACTIVE' OR NEW.revision<>OLD.revision+1 OR
 (to_jsonb(NEW)-'state'-'revision')<>(to_jsonb(OLD)-'state'-'revision') THEN RAISE EXCEPTION 'Extension transition invalid';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER extension_history_guard BEFORE UPDATE OR DELETE ON cinenest_booking.booking_extension_reservation FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_extension_reservation();
CREATE FUNCTION cinenest_booking.check_extension_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e cinenest_booking.booking_extension_reservation;b cinenest_booking.inventory_block;o cinenest_booking.inventory_block; purchase cinenest_booking.booking_revision;booking cinenest_booking.booking;
BEGIN
 SELECT * INTO e FROM cinenest_booking.booking_extension_reservation WHERE reservation_id=NEW.reservation_id;
 SELECT * INTO b FROM cinenest_booking.inventory_block WHERE block_id=e.tail_block;
 SELECT * INTO o FROM cinenest_booking.inventory_block WHERE block_id=e.original_block;
 SELECT * INTO purchase FROM cinenest_booking.booking_revision WHERE booking_id=e.booking_id AND revision=e.purchased_revision;
 SELECT * INTO booking FROM cinenest_booking.booking WHERE booking_id=e.booking_id;
 IF purchase.snapshot IS DISTINCT FROM e.purchase_snapshot OR booking.owner_id<>e.owner_id OR booking.venue_id<>e.venue_id OR
 e.original_start<>(purchase.snapshot->>'sessionStart')::timestamptz OR e.original_end<>(purchase.snapshot->>'sessionEnd')::timestamptz OR e.original_until<>(purchase.snapshot->>'blockedUntil')::timestamptz OR
 e.guests<>(purchase.snapshot->>'guests')::integer OR e.timezone<>purchase.snapshot->>'timezone' OR e.quotation->>'packageVersionId'<>purchase.snapshot->>'packageVersionId' OR
 e.proposed_end<>e.original_end+(e.quotation->>'additionalMinutes')::integer*INTERVAL '1 minute' OR
 NOT EXISTS(SELECT 1 FROM cinenest_booking.reservation_hold h WHERE h.block_id=e.original_block AND h.hold_id=(purchase.snapshot->>'holdId')::uuid AND h.owner_id=e.owner_id AND h.state='CONSUMED') OR
 b.kind<>'EXTENSION_HOLD' OR b.room_id<>e.room_id OR b.venue_id<>e.venue_id OR b.session_start<>e.original_until OR b.session_end<>e.proposed_until OR b.blocked_until<>e.proposed_until OR b.buffer_minutes<>0 OR
 b.blocking<>(e.state='ACTIVE') OR
 o.kind<>'CONFIRMED' OR o.room_id<>e.room_id OR o.venue_id<>e.venue_id OR o.session_start<>e.original_start OR o.session_end<>e.original_end OR o.blocked_until<>e.original_until OR
 NOT EXISTS(SELECT 1 FROM cinenest_booking.booking_extension_audit a WHERE a.reservation_id=e.reservation_id AND a.reservation_revision=e.revision AND a.action=CASE WHEN e.state='ACTIVE' THEN 'CREATE' ELSE e.state END AND a.outcome='ALLOW') OR
 NOT EXISTS(SELECT 1 FROM cinenest_booking.booking_extension_command c WHERE c.reservation_id=e.reservation_id AND c.action='CREATE' AND c.owner_id=e.owner_id) OR
 (e.state='WITHDRAWN' AND NOT EXISTS(SELECT 1 FROM cinenest_booking.booking_extension_command c WHERE c.reservation_id=e.reservation_id AND c.action='WITHDRAW' AND c.owner_id=e.owner_id))
 THEN RAISE EXCEPTION 'Incomplete extension reservation';END IF;
 IF TG_OP='INSERT' AND (booking.state<>'SIMULATED_CONFIRMED' OR booking.lifecycle_version<>0 OR NOT o.blocking OR clock_timestamp()>=e.expires_at) THEN RAISE EXCEPTION 'Extension eligibility expired';END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER extension_complete AFTER INSERT OR UPDATE ON cinenest_booking.booking_extension_reservation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_booking.check_extension_complete();
CREATE FUNCTION cinenest_booking.guard_extension_block() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN IF OLD.kind='EXTENSION_HOLD' THEN RAISE EXCEPTION 'Extension block history immutable';END IF;RETURN OLD;END IF;
 IF OLD.kind='EXTENSION_HOLD' AND (NEW.kind<>OLD.kind OR NEW.blocking OR NEW.revision<>OLD.revision+1 OR
 (to_jsonb(NEW)-'blocking'-'revision')<>(to_jsonb(OLD)-'blocking'-'revision')) THEN RAISE EXCEPTION 'Invalid extension coverage mutation';END IF;
 IF NEW.kind='EXTENSION_HOLD' AND OLD.kind<>'EXTENSION_HOLD' THEN RAISE EXCEPTION 'No inventory kind conversion';END IF;RETURN NEW;
END $$;
CREATE TRIGGER extension_block_history BEFORE UPDATE OR DELETE ON cinenest_booking.inventory_block FOR EACH ROW EXECUTE FUNCTION cinenest_booking.guard_extension_block();
CREATE FUNCTION cinenest_booking.check_extension_block() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e cinenest_booking.booking_extension_reservation;
BEGIN
 IF NEW.kind<>'EXTENSION_HOLD' THEN RETURN NEW;END IF;
 SELECT * INTO e FROM cinenest_booking.booking_extension_reservation WHERE tail_block=NEW.block_id;
 IF e.reservation_id IS NULL OR NEW.blocking<>(e.state='ACTIVE') THEN RAISE EXCEPTION 'Orphan or inconsistent extension block';END IF;RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER extension_block_complete AFTER INSERT OR UPDATE ON cinenest_booking.inventory_block DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cinenest_booking.check_extension_block();

-- Existing commands must not retire/change the original block while a tail remains ACTIVE.
CREATE FUNCTION cinenest_booking.protect_extension_original() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM cinenest_booking.booking_extension_reservation e WHERE e.original_block=OLD.block_id AND e.state='ACTIVE')
 AND to_jsonb(NEW) IS DISTINCT FROM to_jsonb(OLD) THEN RAISE EXCEPTION 'Original inventory has pending extension';END IF;RETURN NEW;
END $$;
CREATE TRIGGER protect_extension_original BEFORE UPDATE ON cinenest_booking.inventory_block FOR EACH ROW EXECUTE FUNCTION cinenest_booking.protect_extension_original();

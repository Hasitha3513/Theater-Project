-- Booking-owned security read evidence; no business mutation or grant seed.
CREATE TABLE cinenest_booking.staff_booking_read_audit (
 event_id UUID PRIMARY KEY,
 actor_id UUID,
 venue_id UUID,
 requested_booking_id UUID,
 action TEXT NOT NULL CHECK(action IN ('LIST','DETAIL')),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('SCOPED_READ','EMPTY','ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','NOT_CONFIGURED','UNAVAILABLE','SESSION_REQUIRED')),
 correlation_id UUID NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(outcome<>'ALLOW' OR (actor_id IS NOT NULL AND venue_id IS NOT NULL AND reason_code IN ('SCOPED_READ','EMPTY'))),
 CHECK(outcome<>'ALLOW' OR action<>'DETAIL' OR requested_booking_id IS NOT NULL),
 CHECK(reason_code<>'EMPTY' OR (action='LIST' AND outcome='ALLOW')),
 CHECK(outcome<>'DENY' OR reason_code IN ('ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','NOT_CONFIGURED','UNAVAILABLE','SESSION_REQUIRED'))
);
-- Requested identifiers intentionally have no FK: failed/missing reads must remain auditable.
CREATE TRIGGER immutable_staff_booking_read_audit BEFORE UPDATE OR DELETE
 ON cinenest_booking.staff_booking_read_audit FOR EACH ROW
 EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

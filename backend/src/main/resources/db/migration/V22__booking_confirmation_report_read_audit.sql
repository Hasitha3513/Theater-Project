-- Owner-authorized protected synthetic confirmation reporting audit only.
CREATE TABLE cinenest_booking.booking_confirmation_report_read_audit (
 event_id UUID PRIMARY KEY, actor_id UUID NOT NULL, venue_id UUID,
 room_id UUID, requested_from DATE, requested_to DATE, timezone TEXT,
 observed_at TIMESTAMPTZ NOT NULL, metric_version TEXT NOT NULL CHECK(metric_version='SYNTHETIC_BOOKING_CONFIRMATIONS_V1'),
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('SCOPED_READ','EMPTY','ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNAVAILABLE')),
 correlation_id UUID NOT NULL,
 CHECK(outcome<>'ALLOW' OR (venue_id IS NOT NULL AND requested_from IS NOT NULL AND requested_to IS NOT NULL AND requested_to>requested_from AND requested_to-requested_from<=31 AND timezone IS NOT NULL AND reason_code IN ('SCOPED_READ','EMPTY'))),
 CHECK(outcome<>'DENY' OR reason_code IN ('ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNAVAILABLE'))
);
CREATE INDEX booking_confirmation_report_audit_venue_time ON cinenest_booking.booking_confirmation_report_read_audit(venue_id,observed_at DESC,event_id);
CREATE TRIGGER immutable_booking_confirmation_report_read_audit BEFORE UPDATE OR DELETE
 ON cinenest_booking.booking_confirmation_report_read_audit FOR EACH ROW
 EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

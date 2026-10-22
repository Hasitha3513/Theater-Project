-- Booking-owned maintenance preview audit only; no inventory or authority changes.
CREATE TABLE cinenest_booking.maintenance_impact_read_audit (
 event_id UUID PRIMARY KEY,
 actor_id UUID,
 venue_id UUID,
 room_id UUID,
 requested_start TIMESTAMPTZ,
 requested_end TIMESTAMPTZ,
 observed_at TIMESTAMPTZ NOT NULL,
 outcome TEXT NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 reason_code TEXT NOT NULL CHECK(reason_code IN ('SCOPED_READ','INCOMPLETE','ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNAVAILABLE')),
 correlation_id UUID NOT NULL,
 CHECK(outcome<>'ALLOW' OR (actor_id IS NOT NULL AND venue_id IS NOT NULL AND room_id IS NOT NULL AND requested_start IS NOT NULL AND requested_end IS NOT NULL AND requested_start<requested_end AND reason_code IN ('SCOPED_READ','INCOMPLETE'))),
 CHECK(outcome<>'DENY' OR reason_code IN ('ACCESS_DENIED','INVALID_REQUEST','NOT_FOUND','UNAVAILABLE'))
);
-- Nullable denied context is deliberately not asserted to be an authorized resource.
CREATE INDEX maintenance_impact_audit_scope_time ON cinenest_booking.maintenance_impact_read_audit(venue_id,room_id,observed_at DESC,event_id);
CREATE TRIGGER immutable_maintenance_impact_read_audit BEFORE UPDATE OR DELETE
 ON cinenest_booking.maintenance_impact_read_audit FOR EACH ROW
 EXECUTE FUNCTION cinenest_booking.reject_history_mutation();

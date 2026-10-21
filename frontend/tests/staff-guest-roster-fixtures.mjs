// Synthetic presentation data only; not admission or backend evidence.
import { venue, id, other } from "./staff-bookings-fixtures.mjs";
export const rosterFixture = () => ({
	bookingId: id,
	venueId: venue,
	bookingRevision: 1,
	revision: 1,
	sessionStart: "2026-10-21T04:30:00Z",
	serverNow: "2026-10-20T10:00:00Z",
	initialized: true,
	purchasedLimit: 4,
	roomCapacity: 6,
	count: 2,
	remaining: 2,
	synthetic: true,
	status: "UNVERIFIED",
	physicalAdmission: false,
	ageVerified: false,
	accessIssued: false,
	permittedActions: [],
	entries: [
		{ id, kind: "BOOKER", label: "BOOKER", status: "UNVERIFIED" },
		{
			id: other,
			kind: "COMPANION",
			label: "SYNTHETIC-1",
			status: "UNVERIFIED",
		},
	],
});

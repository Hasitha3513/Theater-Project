export const venue = "00000000-0000-4000-8000-000000000001",
	account = "00000000-0000-4000-8000-000000000002",
	other = "00000000-0000-4000-8000-000000000003";
export const context = { venue, account, limit: 20 };
// Syntactically valid opaque test reference, not a real backend-issued cursor.
export const cursor = `fixture.${"a".repeat(43)}`;
export const fixture = () => ({
	kind: "MANAGED_GRANT_SUBSET",
	venueId: venue,
	accountId: account,
	targetEligibility: "ELIGIBLE",
	observedAt: "2026-10-22T08:00:00Z",
	limit: 20,
	hasNext: false,
	nextCursor: null,
	snapshotPolicy: "VERSION_BOUND_CURRENT_REQUEST",
	overlapBasis: "ACTIVE_STORED_INTERVALS_HALF_OPEN",
	items: [
		{
			grantId: other,
			venueId: venue,
			permission: "booking.synthetic.read",
			storedState: "ACTIVE",
			validFrom: "2026-10-22T07:00:00Z",
			validUntil: "2026-10-22T15:00:00Z",
			revision: 0,
			effectiveAtObservation: true,
			overlapsAnotherActiveGrant: true,
			anotherEffectiveGrant: true,
		},
	],
});

export const context = {
	venue: "11111111-1111-1111-1111-111111111111",
	room: "22222222-2222-2222-2222-222222222222",
	startLocal: "2026-10-22T10:00",
	endLocal: "2026-10-22T12:00",
};
export const account = "33333333-3333-3333-3333-333333333333";
export function fixture() {
	return {
		venueId: context.venue,
		roomId: context.room,
		timezone: "Asia/Colombo",
		configuration: "config-v1",
		requestedStart: "2026-10-22T04:30:00Z",
		requestedEnd: "2026-10-22T06:30:00Z",
		observedAt: "2026-10-22T03:00:00.123456Z",
		complete: true,
		recordLimit: 1000,
		counts: {
			activeHolds: 2,
			expiredPendingHolds: 1,
			invalidatablePendingHolds: 1,
			confirmedBlocks: 3,
			maintenanceBlocks: 1,
		},
		incompleteReason: null,
		synthetic: true,
		evidenceLevel: "INVENTORY_ONLY",
		occupancy: "UNKNOWN",
		readiness: "UNVERIFIED",
	};
}

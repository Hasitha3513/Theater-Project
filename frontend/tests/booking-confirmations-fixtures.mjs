export const context = {
	venue: "00000000-0000-4000-8000-000000000001",
	room: "",
	from: "2026-10-01",
	to: "2026-10-03",
};
export const account = "00000000-0000-4000-8000-000000000003";
export const fixture = () => ({
	venueId: context.venue,
	roomId: null,
	from: context.from,
	to: context.to,
	timezone: "Asia/Colombo",
	observedAt: "2026-10-22T00:00:00Z",
	metricVersion: "SYNTHETIC_BOOKING_CONFIRMATIONS_V1",
	cohortBasis: "confirmed_at",
	state: "SIMULATED_CONFIRMED",
	synthetic: true,
	complete: true,
	cancellationStatistics: "NOT_AVAILABLE",
	total: "3",
	daily: [
		{ date: "2026-10-01", count: "2" },
		{ date: "2026-10-02", count: "1" },
	],
	rooms: [{ roomId: "00000000-0000-4000-8000-000000000002", count: "3" }],
});

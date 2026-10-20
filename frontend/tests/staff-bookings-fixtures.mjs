// Synthetic presentation fixtures only; not operational bookings or evidence.
export const venue = "00000000-0000-4000-8000-000000000001",
	id = "00000000-0000-4000-8000-000000000002",
	other = "00000000-0000-4000-8000-000000000003";
export const filters = () => ({
	venue,
	reference: "",
	from: "",
	to: "",
	page: 0,
	size: 10,
});
export const item = () => ({
	bookingId: id,
	venueId: venue,
	roomId: other,
	revision: 1,
	state: "SIMULATED_CONFIRMED",
	scheduledStart: "2026-10-21T04:30:00Z",
	scheduledEnd: "2026-10-21T07:30:00Z",
	timezone: "Asia/Colombo",
	packageName: "SYNTHETIC purchased cinema",
	purchasedGuestCount: 4,
	synthetic: true,
	realBooking: false,
});
export const pageRecord = (f = filters()) => ({
	venueId: venue,
	items: [item()],
	page: f.page,
	size: f.size,
	hasNext: false,
	observedAt: "2026-10-20T10:00:00Z",
	synthetic: true,
	realBooking: false,
});
export const detailRecord = () => ({
	booking: item(),
	observedAt: "2026-10-20T10:00:00Z",
	synthetic: true,
	realBooking: false,
});

import { roomRequest } from "./rooms";
import { reviewReference } from "./staff-review";
export type BookingFilters = {
	venue: string;
	reference: string;
	from: string;
	to: string;
	page: number;
	size: number;
};
export type StaffBooking = {
	bookingId: string;
	venueId: string;
	roomId: string;
	revision: number;
	state: "SIMULATED_CONFIRMED" | "SIMULATED_CANCELLED";
	scheduledStart: string;
	scheduledEnd: string;
	timezone: string;
	packageName: string;
	purchasedGuestCount: number;
	synthetic: true;
	realBooking: false;
};
export type StaffBookingPage = {
	venueId: string;
	items: StaffBooking[];
	page: number;
	size: number;
	hasNext: boolean;
	observedAt: string;
	synthetic: true;
	realBooking: false;
};
export const emptyBookingFilters: BookingFilters = {
	venue: "",
	reference: "",
	from: "",
	to: "",
	page: 0,
	size: 10,
};
// Exact elapsed arithmetic for offset timestamps, including backend nanosecond precision.
export function offsetNanos(value: string): bigint {
	const m =
		/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:\d{2}(?::\d{2})?)$/.exec(
			value,
		);
	if (!m)
		throw new Error(
			"Use offset-aware timestamps, for example 2026-10-20T09:00:00+05:30.",
		);
	const [year, month, day, hour, minute, second] = m
		.slice(1, 7)
		.map((v) => Number(v ?? 0));
	const date = new Date(0);
	date.setUTCFullYear(year, month - 1, day);
	date.setUTCHours(hour, minute, second, 0);
	if (
		year < 1 ||
		date.getUTCFullYear() !== year ||
		date.getUTCMonth() !== month - 1 ||
		date.getUTCDate() !== day ||
		date.getUTCHours() !== hour ||
		date.getUTCMinutes() !== minute ||
		date.getUTCSeconds() !== second
	)
		throw new Error("Invalid timestamp.");
	let offset = 0;
	if (m[8] !== "Z") {
		const parts = m[8].slice(1).split(":").map(Number),
			[h, min, sec = 0] = parts;
		if (
			h > 18 ||
			min > 59 ||
			sec > 59 ||
			(h === 18 && (min !== 0 || sec !== 0))
		)
			throw new Error("Invalid timezone offset.");
		offset = (h * 3600 + min * 60 + sec) * (m[8][0] === "-" ? -1 : 1);
	}
	return (
		BigInt(date.getTime()) * 1000000n +
		BigInt((m[7] ?? "").padEnd(9, "0")) -
		BigInt(offset) * 1000000000n
	);
}
export function bookingFilters(input: BookingFilters): BookingFilters {
	const venue = reviewReference(input.venue),
		reference = input.reference ? reviewReference(input.reference) : "";
	if (
		!Number.isSafeInteger(input.page) ||
		input.page < 0 ||
		input.page > 10000 ||
		!Number.isSafeInteger(input.size) ||
		input.size < 1 ||
		input.size > 25
	)
		throw new Error("Page must be 0–10000 and page size 1–25.");
	if (Boolean(input.from) !== Boolean(input.to))
		throw new Error("Supply both date boundaries or leave both empty.");
	if (input.from) {
		const elapsed = offsetNanos(input.to) - offsetNanos(input.from);
		if (elapsed <= 0n || elapsed > 31n * 86400n * 1000000000n)
			throw new Error("Use an increasing range of at most 31 elapsed days.");
	}
	return { ...input, venue, reference };
}
export function bookingFilterParams(
	input: BookingFilters,
	includeVenue = false,
): URLSearchParams {
	const f = bookingFilters(input),
		q = new URLSearchParams();
	if (includeVenue) q.set("venue", f.venue);
	if (f.reference) q.set("reference", f.reference);
	if (f.from) {
		q.set("from", f.from);
		q.set("to", f.to);
	}
	q.set("page", String(f.page));
	q.set("size", String(f.size));
	return q;
}
export function filtersFromUrl(q: URLSearchParams): BookingFilters {
	for (const key of q.keys())
		if (
			!["venue", "reference", "from", "to", "page", "size"].includes(key) ||
			q.getAll(key).length !== 1
		)
			throw new Error("Invalid or repeated filter.");
	const numeric = (key: string, fallback: number) => {
		const v = q.get(key);
		if (v === null) return fallback;
		if (!/^(0|[1-9][0-9]{0,4})$/.test(v)) throw new Error("Invalid page.");
		return Number(v);
	};
	return bookingFilters({
		venue: q.get("venue") ?? "",
		reference: q.get("reference") ?? "",
		from: q.get("from") ?? "",
		to: q.get("to") ?? "",
		page: numeric("page", 0),
		size: numeric("size", 10),
	});
}
export function staffBookingListHref(f: BookingFilters) {
	return `/admin/bookings?${bookingFilterParams(f, true)}`;
}
export function staffBookingDetailHref(
	venue: string,
	booking: string,
	f: BookingFilters,
) {
	if (reviewReference(venue) !== bookingFilters(f).venue)
		throw new Error("Venue context mismatch.");
	return `/admin/bookings/venues/${reviewReference(venue)}/${reviewReference(booking)}?${bookingFilterParams(f)}`;
}
export function staffBookingRecord(
	raw: unknown,
	venue: string,
	booking?: string,
): StaffBooking {
	const v = raw as StaffBooking;
	if (
		v?.synthetic !== true ||
		v.realBooking !== false ||
		!["SIMULATED_CONFIRMED", "SIMULATED_CANCELLED"].includes(v.state) ||
		reviewReference(v.venueId) !== reviewReference(venue) ||
		(booking && reviewReference(v.bookingId) !== reviewReference(booking)) ||
		!Number.isSafeInteger(v.revision) ||
		v.revision < 1 ||
		!Number.isSafeInteger(v.purchasedGuestCount) ||
		v.purchasedGuestCount < 1 ||
		typeof v.timezone !== "string" ||
		!v.timezone ||
		typeof v.packageName !== "string" ||
		!v.packageName ||
		v.packageName.length > 1024 ||
		offsetNanos(v.scheduledEnd) <= offsetNanos(v.scheduledStart)
	)
		throw new Error("Invalid protected booking response.");
	new Intl.DateTimeFormat("en", { timeZone: v.timezone });
	return {
		bookingId: reviewReference(v.bookingId),
		venueId: reviewReference(v.venueId),
		roomId: reviewReference(v.roomId),
		revision: v.revision,
		state: v.state,
		scheduledStart: v.scheduledStart,
		scheduledEnd: v.scheduledEnd,
		timezone: v.timezone,
		packageName: v.packageName,
		purchasedGuestCount: v.purchasedGuestCount,
		synthetic: true,
		realBooking: false,
	};
}
export function staffBookingPage(
	raw: unknown,
	filters: BookingFilters,
): StaffBookingPage {
	const v = raw as StaffBookingPage,
		f = bookingFilters(filters);
	if (
		v?.synthetic !== true ||
		v.realBooking !== false ||
		reviewReference(v.venueId) !== f.venue ||
		v.page !== f.page ||
		v.size !== f.size ||
		typeof v.hasNext !== "boolean" ||
		!Array.isArray(v.items) ||
		v.items.length > f.size ||
		(v.hasNext && v.items.length !== f.size)
	)
		throw new Error("Invalid protected booking page.");
	offsetNanos(v.observedAt);
	const items = v.items.map((item) => staffBookingRecord(item, f.venue));
	if (
		new Set(items.map((item) => item.bookingId)).size !== items.length ||
		(f.reference && items.some((item) => item.bookingId !== f.reference))
	)
		throw new Error("Booking response mismatch.");
	return {
		venueId: f.venue,
		items,
		page: v.page,
		size: v.size,
		hasNext: v.hasNext,
		observedAt: v.observedAt,
		synthetic: true,
		realBooking: false,
	};
}
export async function loadStaffBookings(
	f: BookingFilters,
	signal?: AbortSignal,
) {
	const filters = bookingFilters(f);
	return staffBookingPage(
		await roomRequest(
			`/api/admin/venues/${filters.venue}/bookings?${bookingFilterParams(filters)}`,
			{ signal },
		),
		filters,
	);
}
export async function loadStaffBooking(
	venue: string,
	booking: string,
	signal?: AbortSignal,
) {
	const raw = await roomRequest<{
		booking: unknown;
		observedAt: string;
		synthetic: boolean;
		realBooking: boolean;
	}>(
		`/api/admin/venues/${reviewReference(venue)}/bookings/${reviewReference(booking)}`,
		{ signal },
	);
	if (raw?.synthetic !== true || raw.realBooking !== false)
		throw new Error("Invalid protected detail.");
	offsetNanos(raw.observedAt);
	return {
		booking: staffBookingRecord(raw.booking, venue, booking),
		observedAt: raw.observedAt,
	};
}

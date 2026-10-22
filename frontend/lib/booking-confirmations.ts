import { readSession } from "./session";
import { RoomRequestError, roomRequest } from "./rooms";
export const confirmationUuid =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type ConfirmationContext = {
	venue: string;
	from: string;
	to: string;
	room: string;
};
export type ConfirmationReport = {
	venueId: string;
	roomId: string | null;
	from: string;
	to: string;
	timezone: string;
	observedAt: string;
	metricVersion: string;
	cohortBasis: string;
	state: string;
	synthetic: true;
	complete: true;
	cancellationStatistics: string;
	total: string;
	daily: { date: string; count: string }[];
	rooms: { roomId: string; count: string }[];
};
function date(value: string) {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith("0000"))
		throw Error("Choose valid report dates.");
	const parsed = new Date(`${value}T00:00:00Z`);
	if (
		!Number.isFinite(parsed.getTime()) ||
		parsed.toISOString().slice(0, 10) !== value
	)
		throw Error("Choose valid report dates.");
	return parsed.getTime();
}
export function confirmationPath(context: ConfirmationContext) {
	if (
		!confirmationUuid.test(context.venue) ||
		(context.room && !confirmationUuid.test(context.room))
	)
		throw Error("Enter valid venue and optional room references.");
	const days = (date(context.to) - date(context.from)) / 86400000;
	if (days < 1 || days > 31)
		throw Error("Choose an increasing range of at most 31 venue-local days.");
	const query = new URLSearchParams({ from: context.from, to: context.to });
	if (context.room) query.set("roomId", context.room);
	return `/api/admin/venues/${context.venue}/reports/synthetic-booking-confirmations?${query}`;
}
function object(value: unknown): Record<string, unknown> {
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Report unavailable.");
	return value as Record<string, unknown>;
}
function count(value: unknown): string {
	if (typeof value !== "string" || !/^(0|[1-9][0-9]{0,18})$/.test(value))
		throw Error("Report unavailable.");
	return value;
}
export function parseConfirmations(
	value: unknown,
	context: ConfirmationContext,
): ConfirmationReport {
	confirmationPath(context);
	const v = object(value);
	if (
		v.venueId !== context.venue ||
		v.roomId !== (context.room || null) ||
		v.from !== context.from ||
		v.to !== context.to ||
		v.metricVersion !== "SYNTHETIC_BOOKING_CONFIRMATIONS_V1" ||
		v.cohortBasis !== "confirmed_at" ||
		v.state !== "SIMULATED_CONFIRMED" ||
		v.synthetic !== true ||
		v.complete !== true ||
		v.cancellationStatistics !== "NOT_AVAILABLE" ||
		typeof v.timezone !== "string" ||
		typeof v.observedAt !== "string" ||
		!/^\d{4}-\d{2}-\d{2}T/.test(v.observedAt) ||
		!Number.isFinite(Date.parse(v.observedAt))
	)
		throw Error("Report unavailable.");
	new Intl.DateTimeFormat("en", { timeZone: v.timezone });
	if (
		!Array.isArray(v.daily) ||
		!Array.isArray(v.rooms) ||
		v.rooms.length > 1000
	)
		throw Error("Report unavailable.");
	const expected = (date(context.to) - date(context.from)) / 86400000;
	if (v.daily.length !== expected) throw Error("Incomplete report.");
	const daily = v.daily.map((raw, index) => {
		const row = object(raw);
		const day = new Date(date(context.from) + index * 86400000)
			.toISOString()
			.slice(0, 10);
		if (row.date !== day) throw Error("Incomplete report.");
		return { date: day, count: count(row.count) };
	});
	const ids = new Set<string>();
	const rooms = v.rooms.map((raw) => {
		const row = object(raw);
		if (
			typeof row.roomId !== "string" ||
			!confirmationUuid.test(row.roomId) ||
			ids.has(row.roomId) ||
			(context.room && row.roomId !== context.room)
		)
			throw Error("Report unavailable.");
		ids.add(row.roomId);
		return { roomId: row.roomId, count: count(row.count) };
	});
	const total = count(v.total);
	if (
		daily.reduce((n, row) => n + BigInt(row.count), 0n) !== BigInt(total) ||
		rooms.reduce((n, row) => n + BigInt(row.count), 0n) !== BigInt(total)
	)
		throw Error("Inconsistent report.");
	return {
		venueId: context.venue,
		roomId: context.room || null,
		from: context.from,
		to: context.to,
		timezone: v.timezone,
		observedAt: v.observedAt,
		metricVersion: "SYNTHETIC_BOOKING_CONFIRMATIONS_V1",
		cohortBasis: "confirmed_at",
		state: "SIMULATED_CONFIRMED",
		synthetic: true,
		complete: true,
		cancellationStatistics: "NOT_AVAILABLE",
		total,
		daily,
		rooms,
	};
}
export async function loadConfirmations(
	context: ConfirmationContext,
	signal?: AbortSignal,
) {
	const path = confirmationPath(context);
	const session = () =>
		readSession((input, init) => fetch(input, { ...init, signal }));
	const before = await session();
	if (before.kind !== "authenticated")
		throw new RoomRequestError(
			before.kind === "denied"
				? 403
				: before.kind === "unauthenticated"
					? 401
					: 503,
			"Session unavailable.",
		);
	const raw = await roomRequest(path, { signal });
	const after = await session();
	if (
		after.kind !== "authenticated" ||
		after.account.accountId !== before.account.accountId
	)
		throw Error("Session changed.");
	return parseConfirmations(raw, context);
}

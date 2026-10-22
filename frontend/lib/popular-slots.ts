import {
	confirmationPath,
	type ConfirmationContext,
} from "./booking-confirmations";
import { readSession } from "./session";
import { roomRequest, RoomRequestError } from "./rooms";
export type PopularContext = ConfirmationContext;
export type PopularReport = {
	metricId: string;
	metricVersion: string;
	bandDefinitionVersion: string;
	venueId: string;
	roomId: string | null;
	from: string;
	to: string;
	timezone: string;
	observedAt: string;
	revisionBasis: string;
	cohortBasis: string;
	synthetic: true;
	complete: true;
	total: string;
	buckets: {
		bandId: string;
		startHour: number;
		endHour: number;
		count: string;
	}[];
};
export function popularPath(context: PopularContext) {
	return confirmationPath(context).replace(
		"synthetic-booking-confirmations",
		"synthetic-popular-session-starts",
	);
}
function object(value: unknown): Record<string, unknown> {
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Report unavailable.");
	return value as Record<string, unknown>;
}
function count(value: unknown): string {
	if (
		typeof value !== "string" ||
		!/^(0|[1-9][0-9]{0,18})$/.test(value) ||
		BigInt(value) > 9223372036854775807n
	)
		throw Error("Report unavailable.");
	return value;
}
export function parsePopular(
	value: unknown,
	context: PopularContext,
): PopularReport {
	popularPath(context);
	const v = object(value);
	if (
		v.metricId !== "CN-M-POPULAR-TIME-SLOTS" ||
		v.metricVersion !== "SYNTHETIC_POPULAR_SESSION_STARTS_V1" ||
		v.bandDefinitionVersion !== "LOCAL_HOURLY_24_V1" ||
		v.venueId !== context.venue ||
		v.roomId !== (context.room || null) ||
		v.from !== context.from ||
		v.to !== context.to ||
		v.revisionBasis !== "LATEST_COMMITTED_PURCHASED_REVISION" ||
		v.cohortBasis !== "sessionStart" ||
		v.synthetic !== true ||
		v.complete !== true ||
		typeof v.timezone !== "string" ||
		typeof v.observedAt !== "string" ||
		!/^\d{4}-\d{2}-\d{2}T/.test(v.observedAt) ||
		!Number.isFinite(Date.parse(v.observedAt)) ||
		!Array.isArray(v.buckets) ||
		v.buckets.length !== 24
	)
		throw Error("Report unavailable.");
	new Intl.DateTimeFormat("en", { timeZone: v.timezone });
	const buckets = v.buckets.map((raw, index) => {
		const b = object(raw);
		const bandId = `H${String(index).padStart(2, "0")}`;
		if (b.bandId !== bandId || b.startHour !== index || b.endHour !== index + 1)
			throw Error("Incomplete report.");
		return {
			bandId,
			startHour: index,
			endHour: index + 1,
			count: count(b.count),
		};
	});
	const total = count(v.total);
	if (buckets.reduce((sum, b) => sum + BigInt(b.count), 0n) !== BigInt(total))
		throw Error("Inconsistent report.");
	return {
		metricId: "CN-M-POPULAR-TIME-SLOTS",
		metricVersion: "SYNTHETIC_POPULAR_SESSION_STARTS_V1",
		bandDefinitionVersion: "LOCAL_HOURLY_24_V1",
		venueId: context.venue,
		roomId: context.room || null,
		from: context.from,
		to: context.to,
		timezone: v.timezone,
		observedAt: v.observedAt,
		revisionBasis: "LATEST_COMMITTED_PURCHASED_REVISION",
		cohortBasis: "sessionStart",
		synthetic: true,
		complete: true,
		total,
		buckets,
	};
}
export function bandLabel(start: number, end: number) {
	return `${String(start).padStart(2, "0")}:00–${String(end).padStart(2, "0")}:00`;
}
export function barPercent(value: string, maximum: string) {
	return maximum === "0"
		? 0
		: Number((BigInt(value) * 10000n) / BigInt(maximum)) / 100;
}
export async function loadPopular(
	context: PopularContext,
	signal?: AbortSignal,
) {
	const path = popularPath(context);
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
	const safe = parsePopular(raw, context);
	if (signal?.aborted) throw Error("Request cancelled.");
	return safe;
}

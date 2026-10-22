import { readSession } from "./session";
import { RoomRequestError } from "./rooms";
export const impactUuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/;
export type ImpactContext = {
	venue: string;
	room: string;
	startLocal: string;
	endLocal: string;
};
export const impactCategories = {
	activeHolds: "Active reservation holds",
	expiredPendingHolds: "Expired-pending holds",
	invalidatablePendingHolds: "Invalidatable-pending holds",
	confirmedBlocks: "Confirmed inventory blocks",
	maintenanceBlocks: "Maintenance inventory blocks",
} as const;
type Counts = Record<keyof typeof impactCategories, number>;
export type Impact = {
	venueId: string;
	roomId: string;
	timezone: string;
	configuration: string;
	requestedStart: string;
	requestedEnd: string;
	observedAt: string;
	complete: boolean;
	recordLimit: 1000;
	counts: Counts | null;
	incompleteReason: "RECORD_LIMIT" | null;
	synthetic: true;
	evidenceLevel: "INVENTORY_ONLY";
	occupancy: "UNKNOWN";
	readiness: "UNVERIFIED";
};
export function validLocal(value: string) {
	if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}$/.test(value))
		return false;
	const date = new Date(`${value}:00Z`);
	return (
		Number.isFinite(date.getTime()) && date.toISOString().slice(0, 16) === value
	);
}
export function impactPath(c: ImpactContext) {
	if (
		!impactUuid.test(c.venue) ||
		!impactUuid.test(c.room) ||
		!validLocal(c.startLocal) ||
		!validLocal(c.endLocal)
	)
		throw Error("Invalid preview context");
	const next = new Date(`${c.startLocal.slice(0, 10)}T00:00:00Z`);
	next.setUTCDate(next.getUTCDate() + 1);
	if (
		c.startLocal >= c.endLocal ||
		c.endLocal > next.toISOString().slice(0, 16)
	)
		throw Error("Use an increasing interval within one venue-local day");
	return `/api/admin/venues/${c.venue}/rooms/${c.room}/maintenance-impact`;
}
function instant(value: unknown): value is string {
	return (
		typeof value === "string" &&
		/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value) &&
		Number.isFinite(Date.parse(value)) &&
		validLocal(value.slice(0, 16))
	);
}
function localAt(value: string, zone: string) {
	const parts = new Intl.DateTimeFormat("en-GB", {
		timeZone: zone,
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		hourCycle: "h23",
	}).formatToParts(new Date(value));
	const get = (type: string) => parts.find((p) => p.type === type)?.value;
	return `${get("year")}-${get("month")}-${get("day")}T${get("hour")}:${get("minute")}`;
}
export function parseImpact(value: unknown, c: ImpactContext): Impact {
	impactPath(c);
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Invalid preview");
	const p = value as Record<string, unknown>;
	if (
		p.venueId !== c.venue ||
		p.roomId !== c.room ||
		typeof p.timezone !== "string" ||
		p.timezone.length > 128 ||
		typeof p.configuration !== "string" ||
		!p.configuration ||
		p.configuration.length > 1024 ||
		!instant(p.requestedStart) ||
		!instant(p.requestedEnd) ||
		!instant(p.observedAt) ||
		p.requestedStart.slice(17, 19) !== "00" ||
		p.requestedEnd.slice(17, 19) !== "00" ||
		/\.\d*[1-9]/.test(p.requestedStart) ||
		/\.\d*[1-9]/.test(p.requestedEnd) ||
		Date.parse(p.requestedStart) >= Date.parse(p.requestedEnd) ||
		p.synthetic !== true ||
		p.evidenceLevel !== "INVENTORY_ONLY" ||
		p.occupancy !== "UNKNOWN" ||
		p.readiness !== "UNVERIFIED" ||
		p.recordLimit !== 1000 ||
		typeof p.complete !== "boolean"
	)
		throw Error("Invalid preview contract");
	if (
		localAt(p.requestedStart, p.timezone) !== c.startLocal ||
		localAt(p.requestedEnd, p.timezone) !== c.endLocal
	)
		throw Error("Mismatched interval");
	let counts: Counts | null = null;
	if (p.complete) {
		if (
			p.incompleteReason !== null ||
			!p.counts ||
			typeof p.counts !== "object" ||
			Array.isArray(p.counts)
		)
			throw Error("Invalid complete preview");
		const raw = p.counts as Record<string, unknown>;
		const result = {} as Counts;
		let sum = 0;
		for (const key of Object.keys(impactCategories) as (keyof Counts)[]) {
			const n = raw[key];
			if (
				typeof n !== "number" ||
				!Number.isSafeInteger(n) ||
				n < 0 ||
				n > 1000
			)
				throw Error("Invalid count");
			result[key] = n;
			sum += n;
		}
		if (sum > 1000) throw Error("Invalid completeness");
		counts = result;
	} else if (p.counts !== null || p.incompleteReason !== "RECORD_LIMIT")
		throw Error("Invalid incomplete preview");
	return {
		venueId: c.venue,
		roomId: c.room,
		timezone: p.timezone,
		configuration: p.configuration,
		requestedStart: p.requestedStart,
		requestedEnd: p.requestedEnd,
		observedAt: p.observedAt,
		complete: p.complete,
		recordLimit: 1000,
		counts,
		incompleteReason: p.complete ? null : "RECORD_LIMIT",
		synthetic: true,
		evidenceLevel: "INVENTORY_ONLY",
		occupancy: "UNKNOWN",
		readiness: "UNVERIFIED",
	};
}
export async function loadImpact(c: ImpactContext, signal: AbortSignal) {
	const path = impactPath(c);
	const session = () =>
		readSession((input, init) => fetch(input, { ...init, signal }));
	const before = await session();
	signal.throwIfAborted();
	if (before.kind !== "authenticated")
		throw new RoomRequestError(
			before.kind === "unauthenticated"
				? 401
				: before.kind === "denied"
					? 403
					: 503,
			"Session unavailable",
		);
	const response = await fetch(
		`${path}?${new URLSearchParams({ startLocal: c.startLocal, endLocal: c.endLocal })}`,
		{ credentials: "same-origin", cache: "no-store", signal },
	);
	if (!response.ok)
		throw new RoomRequestError(response.status, "Preview unavailable");
	const raw: unknown = await response.json();
	signal.throwIfAborted();
	const after = await session();
	signal.throwIfAborted();
	if (
		after.kind !== "authenticated" ||
		before.account.accountId !== after.account.accountId
	)
		throw new RoomRequestError(
			after.kind === "unauthenticated"
				? 401
				: after.kind === "denied"
					? 403
					: 409,
			"Session changed",
		);
	return parseImpact(raw, c);
}

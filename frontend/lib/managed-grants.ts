import { RoomRequestError } from "./rooms";
import { readSession } from "./session";

export const grantUuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/;
export const grantCursor = (value: unknown): value is string =>
	typeof value === "string" &&
	value.length <= 1024 &&
	/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]{43}$/.test(value);
export type GrantContext = { venue: string; account: string; limit: number };
export type ManagedGrant = {
	grantId: string;
	venueId: string;
	permission: "booking.synthetic.read" | "guest.synthetic.read";
	storedState: "ACTIVE" | "REVOKED";
	validFrom: string;
	validUntil: string;
	revision: number;
	effectiveAtObservation: boolean;
	overlapsAnotherActiveGrant: boolean;
	anotherEffectiveGrant: boolean;
};
export type ManagedGrantPage = {
	kind: "MANAGED_GRANT_SUBSET";
	venueId: string;
	accountId: string;
	targetEligibility: "ELIGIBLE" | "SUSPENDED" | "INELIGIBLE";
	observedAt: string;
	limit: number;
	hasNext: boolean;
	nextCursor: string | null;
	snapshotPolicy: "VERSION_BOUND_CURRENT_REQUEST";
	overlapBasis: "ACTIVE_STORED_INTERVALS_HALF_OPEN";
	items: ManagedGrant[];
};
const record = (value: unknown): Record<string, unknown> => {
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Invalid grant response.");
	return value as Record<string, unknown>;
};
const instant = (value: unknown): value is string =>
	typeof value === "string" &&
	/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value) &&
	Number.isFinite(Date.parse(value)) &&
	new Date(value).toISOString().slice(0, 19) === value.slice(0, 19);
const instantKey = (value: string) =>
	value.slice(0, 19) +
	"." +
	(value.slice(19, -1).replace(".", "") || "").padEnd(9, "0");
export function grantPath(context: GrantContext) {
	if (
		!grantUuid.test(context.venue) ||
		!grantUuid.test(context.account) ||
		!Number.isInteger(context.limit) ||
		context.limit < 1 ||
		context.limit > 100
	)
		throw Error("Invalid inspection context.");
	return `/api/admin/venues/${context.venue}/accounts/${context.account}/grants`;
}
export function parseManagedGrants(
	value: unknown,
	context: GrantContext,
): ManagedGrantPage {
	grantPath(context);
	const p = record(value);
	if (
		p.kind !== "MANAGED_GRANT_SUBSET" ||
		p.venueId !== context.venue ||
		p.accountId !== context.account ||
		typeof p.targetEligibility !== "string" ||
		!["ELIGIBLE", "SUSPENDED", "INELIGIBLE"].includes(
			String(p.targetEligibility),
		) ||
		!instant(p.observedAt) ||
		p.limit !== context.limit ||
		typeof p.hasNext !== "boolean" ||
		p.snapshotPolicy !== "VERSION_BOUND_CURRENT_REQUEST" ||
		p.overlapBasis !== "ACTIVE_STORED_INTERVALS_HALF_OPEN" ||
		!Array.isArray(p.items) ||
		p.items.length > context.limit ||
		(p.hasNext
			? !grantCursor(p.nextCursor) || p.items.length !== context.limit
			: p.nextCursor !== null)
	)
		throw Error("Invalid grant response.");
	let previous = "";
	const items = p.items.map((value) => {
		const g = record(value);
		if (
			typeof g.grantId !== "string" ||
			!grantUuid.test(g.grantId) ||
			g.grantId <= previous ||
			g.venueId !== context.venue ||
			typeof g.permission !== "string" ||
			!["booking.synthetic.read", "guest.synthetic.read"].includes(
				String(g.permission),
			) ||
			typeof g.storedState !== "string" ||
			!["ACTIVE", "REVOKED"].includes(String(g.storedState)) ||
			!instant(g.validFrom) ||
			!instant(g.validUntil) ||
			instantKey(g.validFrom) >= instantKey(g.validUntil) ||
			!Number.isSafeInteger(g.revision) ||
			(g.revision as number) < 0 ||
			[
				g.effectiveAtObservation,
				g.overlapsAnotherActiveGrant,
				g.anotherEffectiveGrant,
			].some((v) => typeof v !== "boolean") ||
			(g.effectiveAtObservation &&
				(p.targetEligibility !== "ELIGIBLE" || g.storedState !== "ACTIVE")) ||
			(g.anotherEffectiveGrant && p.targetEligibility !== "ELIGIBLE") ||
			(g.overlapsAnotherActiveGrant && g.storedState !== "ACTIVE")
		)
			throw Error("Invalid grant row.");
		previous = g.grantId;
		return {
			grantId: g.grantId,
			venueId: context.venue,
			permission: g.permission as ManagedGrant["permission"],
			storedState: g.storedState as ManagedGrant["storedState"],
			validFrom: g.validFrom,
			validUntil: g.validUntil,
			revision: g.revision as number,
			effectiveAtObservation: g.effectiveAtObservation as boolean,
			overlapsAnotherActiveGrant: g.overlapsAnotherActiveGrant as boolean,
			anotherEffectiveGrant: g.anotherEffectiveGrant as boolean,
		};
	});
	return {
		kind: "MANAGED_GRANT_SUBSET",
		venueId: context.venue,
		accountId: context.account,
		targetEligibility:
			p.targetEligibility as ManagedGrantPage["targetEligibility"],
		observedAt: p.observedAt,
		limit: context.limit,
		hasNext: p.hasNext,
		nextCursor: p.nextCursor as string | null,
		snapshotPolicy: "VERSION_BOUND_CURRENT_REQUEST",
		overlapBasis: "ACTIVE_STORED_INTERVALS_HALF_OPEN",
		items,
	};
}
export async function loadManagedGrants(
	context: GrantContext,
	cursor: string | null,
	signal: AbortSignal,
) {
	const path = grantPath(context);
	if (cursor !== null && !grantCursor(cursor))
		throw Error("Invalid page reference.");
	const session = () =>
		readSession((input, init) => fetch(input, { ...init, signal }));
	const before = await session();
	if (before.kind !== "authenticated")
		throw new RoomRequestError(
			before.kind === "unauthenticated"
				? 401
				: before.kind === "denied"
					? 403
					: 503,
			"Session unavailable.",
		);
	const q = new URLSearchParams({ limit: String(context.limit) });
	if (cursor !== null) q.set("cursor", cursor);
	const response = await fetch(`${path}?${q}`, {
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	if (!response.ok)
		throw new RoomRequestError(response.status, "Inspection unavailable.");
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
			"Session changed.",
		);
	return parseManagedGrants(raw, context);
}

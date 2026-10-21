import { roomRequest } from "./rooms";
import { offsetNanos } from "./staff-bookings";
import { reviewReference } from "./staff-review";
export type BookingAccessContext = { venue: string; booking: string };
export function bookingAccessContext(
	query: Record<string, string | string[] | undefined>,
): BookingAccessContext | undefined {
	if (!Object.keys(query).length) return undefined;
	if (
		Object.keys(query).some((k) => !["venue", "booking"].includes(k)) ||
		typeof query.venue !== "string" ||
		typeof query.booking !== "string"
	)
		throw new Error("Invalid access context");
	return {
		venue: reviewReference(query.venue),
		booking: reviewReference(query.booking),
	};
}
export function bookingAccessHref(venue: string, booking: string) {
	return `/admin/access?venue=${reviewReference(venue)}&booking=${reviewReference(booking)}`;
}
function object(v: unknown): Record<string, unknown> {
	if (!v || typeof v !== "object" || Array.isArray(v))
		throw new Error("Invalid access context");
	return v as Record<string, unknown>;
}
function number(v: unknown, min: number) {
	if (typeof v !== "number" || !Number.isSafeInteger(v) || v < min)
		throw new Error("Invalid access revision");
	return v;
}
function reference(v: unknown) {
	if (typeof v !== "string") throw new Error("Invalid access reference");
	return reviewReference(v);
}
function timestamp(v: unknown) {
	if (typeof v !== "string") throw new Error("Invalid observation");
	offsetNanos(v);
	return v;
}
const states = [
		"SCHEDULED",
		"ACTIVE",
		"CONSUMED",
		"EXPIRED",
		"REVOKED",
		"SUPERSEDED",
	],
	provisioning = [
		"PENDING_SIMULATION",
		"SIMULATOR_INSTALLED",
		"FAILED",
		"UNKNOWN",
		"RECONCILIATION_REQUIRED",
		"REVOKED",
	];
export function staffBookingAccessRecord(
	raw: unknown,
	context: BookingAccessContext,
) {
	const r = object(raw),
		bookingId = reference(r.bookingId),
		venueId = reference(r.venueId),
		roomId = reference(r.roomId),
		bookingRevision = number(r.bookingRevision, 1);
	const sessionStart = timestamp(r.sessionStart),
		sessionEnd = timestamp(r.sessionEnd),
		observedAt = timestamp(r.observedAt);
	if (
		bookingId !== reviewReference(context.booking) ||
		venueId !== reviewReference(context.venue) ||
		r.synthetic !== true ||
		r.doorControlled !== false ||
		typeof r.timezone !== "string" ||
		offsetNanos(sessionEnd) <= offsetNanos(sessionStart) ||
		typeof r.credentialExists !== "boolean" ||
		!Array.isArray(r.permittedActions) ||
		r.permittedActions.some((a) => a !== "REVOKE" && a !== "SIMULATE") ||
		new Set(r.permittedActions).size !== r.permittedActions.length
	)
		throw new Error("Access context mismatch");
	new Intl.DateTimeFormat("en", { timeZone: r.timezone });
	let credential: null | {
		id: string;
		version: number;
		revision: number;
		bookingRevision: number;
		provisioning: string;
		operationId: string;
	} = null;
	if (r.credentialExists) {
		const c = object(r.credential);
		if (
			typeof r.state !== "string" ||
			!states.includes(r.state) ||
			typeof c.provisioning !== "string" ||
			!provisioning.includes(c.provisioning) ||
			c.bookingRevision !== bookingRevision
		)
			throw new Error("Invalid credential state");
		credential = {
			id: reference(c.id),
			version: number(c.version, 1),
			revision: number(c.revision, 0),
			bookingRevision,
			provisioning: c.provisioning,
			operationId: reference(c.operationId),
		};
	} else if (
		r.state !== "NOT_ISSUED" ||
		r.credential !== null ||
		r.permittedActions.length
	)
		throw new Error("Invalid unissued state");
	return {
		bookingId,
		venueId,
		roomId,
		bookingRevision,
		sessionStart,
		sessionEnd,
		timezone: r.timezone,
		observedAt,
		credentialExists: r.credentialExists,
		state: r.state as string,
		credential,
		permittedActions: [...r.permittedActions] as string[],
		synthetic: true as const,
		doorControlled: false as const,
	};
}
export type StaffBookingAccess = ReturnType<typeof staffBookingAccessRecord>;
export async function loadStaffBookingAccess(
	context: BookingAccessContext,
	signal?: AbortSignal,
) {
	const venue = reviewReference(context.venue),
		booking = reviewReference(context.booking);
	return staffBookingAccessRecord(
		await roomRequest(`/api/admin/venues/${venue}/bookings/${booking}/access`, {
			signal,
		}),
		{ venue, booking },
	);
}

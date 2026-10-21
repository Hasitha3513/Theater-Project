import { roomRequest, RoomRequestError } from "./rooms";
import { offsetNanos, type StaffBooking } from "./staff-bookings";
import { reviewReference } from "./staff-review";
function object(v: unknown): Record<string, unknown> {
	if (!v || typeof v !== "object" || Array.isArray(v))
		throw new Error("Invalid refund context");
	return v as Record<string, unknown>;
}
function reference(v: unknown) {
	if (typeof v !== "string") throw new Error("Invalid refund reference");
	return reviewReference(v);
}
function integer(v: unknown, min: number, max = Number.MAX_SAFE_INTEGER) {
	if (typeof v !== "number" || !Number.isSafeInteger(v) || v < min || v > max)
		throw new Error("Invalid refund revision or page");
	return v;
}
const states = [
	"REQUESTED",
	"UNDER_REVIEW",
	"APPROVED",
	"REJECTED",
	"WITHDRAWN",
	"ATTEMPT_PREPARED",
	"AWAITING_EVIDENCE",
	"SIMULATED_CONFIRMED",
	"FAILED_CONFIRMED",
	"UNKNOWN_RECONCILIATION_REQUIRED",
];
const resolutions = [
	"REVIEW_REQUIRED",
	"OUTGOING_EVIDENCE_REQUIRED",
	"OUTGOING_VERIFICATION_REQUIRED",
	"RECONCILIATION_REQUIRED",
	"FAILED_REVIEW_REQUIRED",
	"COMPLETE",
];
function state(v: unknown, allowed: string[]) {
	if (typeof v !== "string" || !allowed.includes(v))
		throw new Error("Unsupported refund state");
	return v;
}
export function staffBookingRefundRecord(
	raw: unknown,
	booking: StaffBooking,
	page: number,
	size: number,
) {
	integer(page, 0, 10000);
	integer(size, 1, 25);
	const r = object(raw),
		bookingId = reference(r.bookingId),
		venueId = reference(r.venueId),
		bookingRevision = integer(r.bookingRevision, 1);
	if (
		bookingId !== reviewReference(booking.bookingId) ||
		venueId !== reviewReference(booking.venueId) ||
		bookingRevision !== booking.revision
	)
		throw new RoomRequestError(
			409,
			"Reload current booking before reading refunds",
		);
	if (
		r.synthetic !== true ||
		r.realRepayment !== false ||
		r.page !== page ||
		r.size !== size ||
		typeof r.hasNext !== "boolean" ||
		!Array.isArray(r.items) ||
		r.items.length > size ||
		(r.hasNext && r.items.length !== size) ||
		typeof r.observedAt !== "string"
	)
		throw new Error("Invalid refund page");
	offsetNanos(r.observedAt);
	const items = r.items.map((value) => {
		const row = object(value),
			id = reference(row.id);
		if (
			typeof row.amountMinor !== "string" ||
			!/^[1-9]\d{0,18}$/.test(row.amountMinor) ||
			BigInt(row.amountMinor) > 9223372036854775807n ||
			row.currency !== "LKR" ||
			typeof row.destinationVerified !== "boolean" ||
			!Array.isArray(row.attempts)
		)
			throw new Error("Invalid refund amount or metadata");
		const attempts = row.attempts.map((value) => {
			const a = object(value);
			if (
				!Array.isArray(a.evidencePhases) ||
				a.evidencePhases.some((p) => p !== "RECORDED" && p !== "VERIFIED") ||
				new Set(a.evidencePhases).size !== a.evidencePhases.length
			)
				throw new Error("Invalid evidence phase");
			return {
				id: reference(a.id),
				sequence: integer(a.sequence, 1),
				evidencePhases: [...a.evidencePhases] as string[],
			};
		});
		if (
			new Set(attempts.map((a) => a.id)).size !== attempts.length ||
			new Set(attempts.map((a) => a.sequence)).size !== attempts.length
		)
			throw new Error("Conflicting refund attempts");
		return {
			id,
			revision: integer(row.revision, 0),
			state: state(row.state, states),
			resolution: state(row.resolution, resolutions),
			amountMinor: row.amountMinor,
			currency: row.currency,
			destinationVerified: row.destinationVerified,
			attempts,
		};
	});
	if (new Set(items.map((r) => r.id)).size !== items.length)
		throw new Error("Conflicting refund references");
	return {
		bookingId,
		venueId,
		bookingRevision,
		originatingRequestId: reference(r.originatingRequestId),
		observedAt: r.observedAt,
		items,
		page,
		size,
		hasNext: r.hasNext,
		synthetic: true as const,
		realRepayment: false as const,
	};
}
export type StaffBookingRefunds = ReturnType<typeof staffBookingRefundRecord>;
export async function loadStaffBookingRefunds(
	booking: StaffBooking,
	page = 0,
	size = 10,
	signal?: AbortSignal,
) {
	integer(page, 0, 10000);
	integer(size, 1, 25);
	const venue = reviewReference(booking.venueId),
		id = reviewReference(booking.bookingId);
	return staffBookingRefundRecord(
		await roomRequest(
			`/api/admin/venues/${venue}/bookings/${id}/refunds?page=${page}&size=${size}`,
			{ signal },
		),
		booking,
		page,
		size,
	);
}

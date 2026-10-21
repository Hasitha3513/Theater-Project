import { roomRequest, RoomRequestError } from "./rooms";
import { offsetNanos, type StaffBooking } from "./staff-bookings";
import { reviewReference } from "./staff-review";
function object(v: unknown): Record<string, unknown> {
	if (!v || typeof v !== "object" || Array.isArray(v))
		throw new Error("Invalid operations context");
	return v as Record<string, unknown>;
}
function reference(v: unknown) {
	if (typeof v !== "string") throw new Error("Invalid operations reference");
	return reviewReference(v);
}
function integer(v: unknown, min: number, max = Number.MAX_SAFE_INTEGER) {
	if (typeof v !== "number" || !Number.isSafeInteger(v) || v < min || v > max)
		throw new Error("Invalid operations revision or page");
	return v;
}
function instant(v: unknown) {
	if (typeof v !== "string") throw new Error("Invalid operations timestamp");
	offsetNanos(v);
	return v;
}
function oneOf(v: unknown, allowed: string[]) {
	if (typeof v !== "string" || !allowed.includes(v))
		throw new Error("Unsupported operations state");
	return v;
}
export function staffBookingOperationsRecord(
	raw: unknown,
	booking: StaffBooking,
	page: number,
) {
	integer(page, 0, 100000);
	const r = object(raw),
		bookingId = reference(r.bookingId),
		venueId = reference(r.venueId),
		currentRoomId = reference(r.currentRoomId),
		currentBookingRevision = integer(r.currentBookingRevision, 1),
		currentSessionStart = instant(r.currentSessionStart),
		currentSessionEnd = instant(r.currentSessionEnd);
	if (
		bookingId !== reviewReference(booking.bookingId) ||
		venueId !== reviewReference(booking.venueId) ||
		currentRoomId !== reviewReference(booking.roomId) ||
		currentBookingRevision !== booking.revision ||
		offsetNanos(currentSessionStart) !== offsetNanos(booking.scheduledStart) ||
		offsetNanos(currentSessionEnd) !== offsetNanos(booking.scheduledEnd) ||
		r.timezone !== booking.timezone
	)
		throw new RoomRequestError(
			409,
			"Reload current booking before reading operations",
		);
	if (
		r.synthetic !== true ||
		r.occupancy !== "UNKNOWN" ||
		r.readiness !== "UNVERIFIED" ||
		r.inventoryChanged !== false ||
		r.page !== page ||
		r.pageSize !== 25 ||
		typeof r.hasNext !== "boolean" ||
		!Array.isArray(r.items) ||
		r.items.length > 25 ||
		(r.hasNext && r.items.length !== 25)
	)
		throw new Error("Invalid operations page");
	const observedAt = instant(r.observedAt);
	const items = r.items.map((value) => {
		const row = object(value),
			caseId = reference(row.caseId),
			roomId = reference(row.roomId),
			capturedBookingRevision = integer(row.capturedBookingRevision, 1),
			capturedSessionStart = instant(row.capturedSessionStart),
			capturedSessionEnd = instant(row.capturedSessionEnd),
			createdAt = instant(row.createdAt),
			updatedAt = instant(row.updatedAt),
			existingCaseState = oneOf(row.existingCaseState, [
				"OPEN",
				"ACKNOWLEDGED",
				"ASSERTED",
				"RESOLVED",
			]),
			assignmentStatus = oneOf(row.assignmentStatus, [
				"ASSIGNED",
				"UNASSIGNED",
			]);
		if (
			capturedBookingRevision > currentBookingRevision ||
			offsetNanos(capturedSessionEnd) <= offsetNanos(capturedSessionStart) ||
			offsetNanos(updatedAt) < offsetNanos(createdAt) ||
			(existingCaseState !== "OPEN" && assignmentStatus !== "ASSIGNED") ||
			!Array.isArray(row.recordedExceptionCategories) ||
			row.recordedExceptionCategories.some(
				(c) =>
					typeof c !== "string" ||
					!["CLEANING", "MAINTENANCE", "UNKNOWN_DEVICE"].includes(c),
			) ||
			new Set(row.recordedExceptionCategories).size !==
				row.recordedExceptionCategories.length
		)
			throw new Error("Invalid linked operations case");
		if (
			capturedBookingRevision === currentBookingRevision &&
			(roomId !== currentRoomId ||
				offsetNanos(capturedSessionStart) !==
					offsetNanos(currentSessionStart) ||
				offsetNanos(capturedSessionEnd) !== offsetNanos(currentSessionEnd))
		)
			throw new Error("Inconsistent current purchased context");
		return {
			caseId,
			roomId,
			capturedBookingRevision,
			capturedSessionStart,
			capturedSessionEnd,
			caseRevision: integer(row.caseRevision, 0),
			existingCaseState,
			assignmentStatus,
			createdAt,
			updatedAt,
			recordedExceptionCategories: [
				...row.recordedExceptionCategories,
			] as string[],
		};
	});
	if (new Set(items.map((r) => r.caseId)).size !== items.length)
		throw new Error("Conflicting case references");
	return {
		bookingId,
		venueId,
		currentRoomId,
		currentBookingRevision,
		currentSessionStart,
		currentSessionEnd,
		timezone: booking.timezone,
		observedAt,
		page,
		pageSize: 25,
		hasNext: r.hasNext,
		items,
		synthetic: true as const,
		occupancy: "UNKNOWN" as const,
		readiness: "UNVERIFIED" as const,
		inventoryChanged: false as const,
	};
}
export type StaffBookingOperations = ReturnType<
	typeof staffBookingOperationsRecord
>;
export async function loadStaffBookingOperations(
	booking: StaffBooking,
	page = 0,
	signal?: AbortSignal,
) {
	integer(page, 0, 100000);
	return staffBookingOperationsRecord(
		await roomRequest(
			`/api/admin/venues/${reviewReference(booking.venueId)}/bookings/${reviewReference(booking.bookingId)}/operations/cases?page=${page}`,
			{ signal },
		),
		booking,
		page,
	);
}

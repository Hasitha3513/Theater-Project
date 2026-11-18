import { useCallback } from "react";
import { roomRequest } from "../lib/rooms";
import { offsetNanos, type StaffBooking } from "../lib/staff-bookings";
import { Button, Card, StateMessage } from "./primitives";
import { useProtectedRead } from "./staff-bookings";
import { StaffSyntheticCheckIn } from "./staff-synthetic-check-in";

const outcomes = {
	NOT_CONFIRMED: "Booking is not currently confirmed.",
	PENDING_VERIFICATION:
		"Current guest eligibility decisions are missing or unresolved.",
	INELIGIBLE:
		"The current booking or guest checks do not satisfy the review criteria.",
	ELIGIBLE_FOR_STAFF_REVIEW:
		"All required synthetic checks support staff review.",
	UNAVAILABLE:
		"Admission assessment is unavailable. Reload current booking details.",
};

export function admissionRecord(input: unknown, booking: StaffBooking) {
	if (!input || typeof input !== "object")
		throw new Error("Invalid admission assessment");
	const value = input as Record<string, unknown>;
	const status = value.status;
	const count = value.checkedGuestCount;
	if (
		value.bookingId !== booking.bookingId ||
		value.venueId !== booking.venueId ||
		value.roomId !== booking.roomId ||
		value.bookingRevision !== booking.revision ||
		value.bookingState !== booking.state ||
		value.permittedGuestCount !== booking.purchasedGuestCount ||
		value.synthetic !== true ||
		value.physicalEntryAuthorized !== false ||
		typeof value.scheduledStart !== "string" ||
		typeof value.scheduledEnd !== "string" ||
		offsetNanos(value.scheduledStart) !== offsetNanos(booking.scheduledStart) ||
		offsetNanos(value.scheduledEnd) !== offsetNanos(booking.scheduledEnd) ||
		typeof count !== "number" ||
		!Number.isSafeInteger(count) ||
		count < 0 ||
		count > booking.purchasedGuestCount ||
		typeof status !== "string" ||
		!Object.hasOwn(outcomes, status) ||
		(status === "ELIGIBLE_FOR_STAFF_REVIEW" &&
			(booking.state !== "SIMULATED_CONFIRMED" ||
				count !== booking.purchasedGuestCount))
	)
		throw new Error("Admission assessment does not match booking");
	return { status: status as keyof typeof outcomes, checkedGuestCount: count };
}

export function StaffGuestAdmission({ booking }: { booking: StaffBooking }) {
	const read = useCallback(
		async (signal: AbortSignal) =>
			admissionRecord(
				await roomRequest(
					`/api/admin/venues/${booking.venueId}/bookings/${booking.bookingId}/guest-admission`,
					{ signal },
				),
				booking,
			),
		[booking],
	);
	const state = useProtectedRead(read, true);
	return (
		<section aria-label="Guest admission assessment">
			<Card title="Guest admission assessment">
				<p>
					Synthetic read-only assessment. It does not authorize physical entry.
				</p>
				<Button
					aria-disabled={state.loading}
					onClick={() => {
						if (!state.loading) void state.reload();
					}}
				>
					Load admission assessment
				</Button>
				{state.loading && (
					<StateMessage kind="loading">
						<p>Loading assessment.</p>
					</StateMessage>
				)}
				{state.error && (
					<StateMessage kind={state.error}>
						<p>
							{state.error === "denied"
								? "Admission assessment requires current venue authority."
								: "Admission assessment unavailable. Reload the booking and try again."}
						</p>
					</StateMessage>
				)}
				{state.value && (
					<output>
						<p>{state.value.status}</p>
						<p>{outcomes[state.value.status]}</p>
						<p>
							Eligible synthetic guest checks: {state.value.checkedGuestCount} /{" "}
							{booking.purchasedGuestCount}
						</p>
					</output>
				)}
			</Card>
			<StaffSyntheticCheckIn booking={booking} />
		</section>
	);
}

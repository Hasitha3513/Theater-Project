import { useCallback, useRef, useState } from "react";
import { RoomRequestError, roomRequest } from "../lib/rooms";
import { offsetNanos, type StaffBooking } from "../lib/staff-bookings";
import { Button, Card } from "./primitives";
import { useProtectedRead } from "./staff-bookings";

export function checkInReceipt(input: unknown, booking: StaffBooking) {
	if (!input || typeof input !== "object")
		throw new Error("Invalid check-in receipt");
	const value = input as Record<string, unknown>;
	if (
		value.bookingId !== booking.bookingId ||
		value.venueId !== booking.venueId ||
		value.roomId !== booking.roomId ||
		value.bookingRevision !== booking.revision ||
		value.physicalEntryAuthorized !== false ||
		value.outcome !== "SYNTHETIC_CHECK_IN_RECORDED" ||
		typeof value.actorId !== "string" ||
		!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.actorId) ||
		typeof value.recordedAt !== "string"
	)
		throw new Error("Check-in receipt does not match booking");
	offsetNanos(value.recordedAt);
	return { actorId: value.actorId, recordedAt: value.recordedAt };
}

function checkInView(input: unknown, booking: StaffBooking) {
	if (!input || typeof input !== "object")
		throw new Error("Invalid check-in status");
	const value = input as Record<string, unknown>;
	const revision = value.rosterRevision;
	if (
		typeof value.available !== "boolean" ||
		(revision !== null &&
			(typeof revision !== "number" ||
				!Number.isSafeInteger(revision) ||
				revision < 0)) ||
		(value.available &&
			(revision === null ||
				value.decision !== null ||
				booking.state !== "SIMULATED_CONFIRMED"))
	)
		throw new Error("Invalid check-in scope");
	return {
		available: value.available,
		rosterRevision: revision as number | null,
		decision:
			value.decision === null ? null : checkInReceipt(value.decision, booking),
	};
}

export function StaffSyntheticCheckIn({ booking }: { booking: StaffBooking }) {
	const endpoint = `/api/admin/venues/${booking.venueId}/bookings/${booking.bookingId}/guest-check-in`;
	const read = useCallback(
		async (signal: AbortSignal) =>
			checkInView(await roomRequest(endpoint, { signal }), booking),
		[booking, endpoint],
	);
	const state = useProtectedRead(read, true);
	const retry = useRef<{ key: string; roster: number } | null>(null);
	const sending = useRef(false);
	const [feedback, setFeedback] = useState("");
	const [busy, setBusy] = useState(false);

	async function record() {
		const roster = state.value?.rosterRevision;
		if (sending.current || !state.value?.available || roster == null) return;
		sending.current = true;
		setBusy(true);
		setFeedback("");
		const prior = retry.current;
		const key = prior?.roster === roster ? prior.key : crypto.randomUUID();
		retry.current = { key, roster };
		try {
			checkInReceipt(
				await roomRequest(endpoint, {
					method: "POST",
					signal: AbortSignal.timeout(10000),
					body: JSON.stringify({
						commandKey: key,
						roomId: booking.roomId,
						bookingRevision: booking.revision,
						rosterRevision: roster,
					}),
				}),
				booking,
			);
			await state.reload();
		} catch (error) {
			setFeedback(
				error instanceof RoomRequestError && error.status === 409
					? "The current scope cannot be recorded. Reload check-in status."
					: "Check-in was not confirmed. Retry the same request or reload status.",
			);
		} finally {
			sending.current = false;
			setBusy(false);
		}
	}

	return (
		<section aria-label="Synthetic staff check-in">
			<Card title="Synthetic staff check-in">
				<p>The recorded decision grants no physical entry.</p>
				<Button
					onClick={() => {
						if (!sending.current && !state.loading) void state.reload();
					}}
					aria-disabled={busy || state.loading}
				>
					Reload check-in status
				</Button>
				{state.error && (
					<p role="status">
						Check-in status unavailable for this session or venue.
					</p>
				)}
				{state.loading && <p role="status">Checking availability.</p>}
				{state.value?.available && (
					<Button onClick={() => void record()} aria-disabled={busy}>
						Record synthetic check-in
					</Button>
				)}
				{state.value && !state.value.available && !state.value.decision && (
					<p>Current guest checks or the session window prevent recording.</p>
				)}
				{state.value?.decision && (
					<p role="status">
						Synthetic decision recorded by {state.value.decision.actorId} at{" "}
						<time dateTime={state.value.decision.recordedAt}>
							{state.value.decision.recordedAt}
						</time>
						.
					</p>
				)}
				{feedback && <p role="alert">{feedback}</p>}
			</Card>
		</section>
	);
}

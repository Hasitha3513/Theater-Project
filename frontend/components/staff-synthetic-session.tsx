import { useCallback, useRef, useState } from "react";
import { roomRequest } from "../lib/rooms";
import { offsetNanos, type StaffBooking } from "../lib/staff-bookings";
import { useProtectedRead } from "./staff-bookings";
import { Button, Card } from "./primitives";

type Event = {
	eventId: string;
	actorId: string;
	eventType: "START" | "CHECKOUT";
	recordedAt: string;
	lateCheckout: boolean;
};
type Session = {
	startAvailable: boolean;
	checkoutAvailable: boolean;
	rosterRevision: number | null;
	checkIn: { actorId: string; commandKey: string } | null;
	events: Event[];
};
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
export function sessionView(input: unknown, booking: StaffBooking): Session {
	if (!input || typeof input !== "object") throw Error("Invalid session");
	const v = input as Record<string, unknown>;
	if (
		v.bookingId !== booking.bookingId ||
		v.venueId !== booking.venueId ||
		v.roomId !== booking.roomId ||
		v.bookingRevision !== booking.revision ||
		typeof v.startAvailable !== "boolean" ||
		typeof v.checkoutAvailable !== "boolean" ||
		v.synthetic !== true ||
		v.inventoryChanged !== false ||
		v.physicalOccupancy !== "UNKNOWN" ||
		v.physicalReadiness !== "UNVERIFIED" ||
		!(
			v.rosterRevision === null ||
			(Number.isSafeInteger(v.rosterRevision) && Number(v.rosterRevision) >= 0)
		) ||
		!Array.isArray(v.events) ||
		v.events.length > 2
	)
		throw Error("Session context changed");
	const events: Event[] = v.events.map((e) => {
		if (
			!e ||
			e.bookingId !== booking.bookingId ||
			e.venueId !== booking.venueId ||
			e.roomId !== booking.roomId ||
			e.bookingRevision !== booking.revision ||
			!uuid.test(e.eventId) ||
			!uuid.test(e.actorId) ||
			!["START", "CHECKOUT"].includes(e.eventType) ||
			typeof e.recordedAt !== "string" ||
			typeof e.lateCheckout !== "boolean"
		)
			throw Error("Invalid event");
		offsetNanos(e.recordedAt);
		return {
			eventId: e.eventId,
			actorId: e.actorId,
			eventType: e.eventType,
			recordedAt: e.recordedAt,
			lateCheckout: e.lateCheckout,
		};
	});
	if (
		new Set(events.map((e) => e.eventType)).size !== events.length ||
		(events.some((e) => e.eventType === "CHECKOUT") &&
			events[0]?.eventType !== "START")
	)
		throw Error("Invalid event order");
	let checkIn: Session["checkIn"] = null;
	if (v.checkIn !== null) {
		const c = v.checkIn as Record<string, unknown>;
		if (
			!c ||
			typeof c.actorId !== "string" ||
			typeof c.commandKey !== "string" ||
			!uuid.test(c.actorId) ||
			!uuid.test(c.commandKey)
		)
			throw Error("Invalid check-in");
		checkIn = { actorId: c.actorId, commandKey: c.commandKey };
	}
	return {
		startAvailable: v.startAvailable as boolean,
		checkoutAvailable: v.checkoutAvailable as boolean,
		rosterRevision: v.rosterRevision as number | null,
		checkIn,
		events,
	};
}
export function StaffSyntheticSession({ booking }: { booking: StaffBooking }) {
	const base = `/api/admin/venues/${booking.venueId}/bookings/${booking.bookingId}/session`;
	const read = useCallback(
		async (signal: AbortSignal) =>
			sessionView(await roomRequest(base, { signal }), booking),
		[base, booking],
	);
	const state = useProtectedRead(read, true);
	const pending = useRef<{ signature: string; key: string } | null>(null);
	const sending = useRef(false);
	const [busy, setBusy] = useState(false);
	const [message, setMessage] = useState("");
	const start = state.value?.events.find((e) => e.eventType === "START");
	const checkout = state.value?.events.find((e) => e.eventType === "CHECKOUT");
	const checkoutId = checkout?.eventId;
	const readHandoff = useCallback(
		async (signal: AbortSignal) => {
			const response = await roomRequest<{
				status?: unknown;
				case?: { state?: unknown };
			}>(`${base}/cleaning-handoff/${checkoutId}`, { signal });
			if (response.status !== "PENDING" && response.status !== "RECORDED")
				throw Error("Invalid handoff");
			const state = response.case?.state;
			if (
				state !== undefined &&
				!["OPEN", "ACKNOWLEDGED", "ASSERTED", "RESOLVED"].includes(
					String(state),
				)
			)
				throw Error("Invalid turnover");
			return {
				status: response.status,
				state: typeof state === "string" ? state : undefined,
			};
		},
		[base, checkoutId],
	);
	const handoff = useProtectedRead(readHandoff, !!checkoutId);
	async function command(action: "starts" | "checkouts" | "cleaning-handoff") {
		const current = state.value;
		if (
			sending.current ||
			!current ||
			current.rosterRevision === null ||
			!current.checkIn
		)
			return;
		const body: Record<string, unknown> =
			action === "cleaning-handoff"
				? { checkoutEventId: checkout?.eventId }
				: {
						bookingRevision: booking.revision,
						rosterRevision: current.rosterRevision,
						checkInActorId: current.checkIn.actorId,
						checkInCommandKey: current.checkIn.commandKey,
						...(action === "checkouts" ? { startEventId: start?.eventId } : {}),
					};
		const signature = JSON.stringify([action, body]);
		if (pending.current?.signature !== signature)
			pending.current = { signature, key: crypto.randomUUID() };
		sending.current = true;
		setBusy(true);
		setMessage("");
		try {
			const result = await roomRequest<{ status?: string }>(
				`${base}/${action}`,
				{
					method: "POST",
					signal: AbortSignal.timeout(10000),
					body: JSON.stringify(
						action === "cleaning-handoff"
							? body
							: { ...body, commandKey: pending.current.key },
					),
				},
			);
			if (action === "cleaning-handoff") {
				if (result.status !== "RECORDED") throw Error("Handoff pending");
				setMessage(
					"Cleaning handoff recorded. Review the linked operations case.",
				);
			} else {
				setMessage("Session event recorded. Reloaded current history.");
			}
			pending.current = null;
			await handoff.reload();
			await state.reload();
		} catch {
			state.clear();
			setMessage(
				"Result unavailable. Reload session history before retrying. The original command key is retained.",
			);
		} finally {
			sending.current = false;
			setBusy(false);
		}
	}
	return (
		<section aria-label="Synthetic session operations">
			<Card title="Synthetic session operations">
				<p>
					Staff assertions preserve physical occupancy UNKNOWN and readiness
					UNVERIFIED.
				</p>
				<Button
					aria-disabled={busy || state.loading}
					onClick={() => {
						if (!sending.current && !state.loading) void state.reload();
					}}
				>
					Reload session history
				</Button>
				{state.loading && <p role="status">Loading session history.</p>}
				{state.error && (
					<p role="status">
						Session unavailable for the current staff or venue.
					</p>
				)}
				{state.value && (
					<>
						<ol>
							{state.value.events.map((e) => (
								<li key={e.eventId}>
									{e.eventType} —{" "}
									<time dateTime={e.recordedAt}>{e.recordedAt}</time> — staff{" "}
									{e.actorId}
									{e.lateCheckout && " — Late checkout recorded"}
								</li>
							))}
						</ol>
						{!start && currentCanStart(state.value) && (
							<Button
								aria-disabled={busy}
								onClick={() => void command("starts")}
							>
								Record synthetic START
							</Button>
						)}
						{start && !checkout && state.value.checkoutAvailable && (
							<Button
								aria-disabled={busy}
								onClick={() => void command("checkouts")}
							>
								Record synthetic CHECKOUT
							</Button>
						)}
						{checkout && (
							<Button
								aria-disabled={busy}
								onClick={() => void command("cleaning-handoff")}
							>
								Create or recover cleaning handoff
							</Button>
						)}
						{!state.value.checkIn && (
							<p>A current synthetic check-in is required.</p>
						)}
					</>
				)}
				{checkout && (
					<p>
						Cleaning handoff: {handoff.value?.status ?? "UNAVAILABLE"}
						{handoff.value?.state ? ` — turnover ${handoff.value.state}` : ""}
					</p>
				)}
				{message && <p role="status">{message}</p>}
			</Card>
		</section>
	);
}
function currentCanStart(value: Session) {
	return (
		value.startAvailable &&
		value.checkIn !== null &&
		value.rosterRevision !== null
	);
}

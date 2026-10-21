"use client";
import { useCallback, useState } from "react";
import type { StaffBooking } from "../lib/staff-bookings";
import {
	loadStaffBookingOperations,
	type StaffBookingOperations,
} from "../lib/staff-booking-operations";
import { Badge, Button, Card, StateMessage } from "./primitives";
import { useProtectedRead } from "./staff-bookings";
const categoryLabels: Record<string, string> = {
	CLEANING: "Cleaning",
	MAINTENANCE: "Maintenance",
	UNKNOWN_DEVICE: "Unknown device outcome",
};
export function StaffOperationsSummary({
	value: v,
}: {
	value: StaffBookingOperations;
}) {
	return (
		<section
			aria-label="Protected booking operations results"
			style={{ overflowWrap: "anywhere" }}
		>
			<dl className="account-summary">
				<div>
					<dt>Exact booking</dt>
					<dd>{v.bookingId}</dd>
				</div>
				<div>
					<dt>Venue</dt>
					<dd>{v.venueId}</dd>
				</div>
				<div>
					<dt>Current room</dt>
					<dd>{v.currentRoomId}</dd>
				</div>
				<div>
					<dt>Current purchased revision</dt>
					<dd>{v.currentBookingRevision}</dd>
				</div>
				<div>
					<dt>Purchased session start</dt>
					<dd>
						<time dateTime={v.currentSessionStart}>
							{v.currentSessionStart}
						</time>
					</dd>
				</div>
				<div>
					<dt>Purchased session end</dt>
					<dd>
						<time dateTime={v.currentSessionEnd}>{v.currentSessionEnd}</time>
					</dd>
				</div>
				<div>
					<dt>Venue timezone</dt>
					<dd>{v.timezone}</dd>
				</div>
				<div>
					<dt>Server observation</dt>
					<dd>
						<time dateTime={v.observedAt}>{v.observedAt}</time>
					</dd>
				</div>
			</dl>
			<p>
				Page {v.page + 1}. Records may change between reads; pages are not a
				single snapshot.
			</p>
			{!v.items.length ? (
				<StateMessage kind="empty">
					<p>
						No explicitly booking-linked operations cases recorded. This does
						not mean the room is ready; room-wide maintenance and other unlinked
						cases are not included.
					</p>
				</StateMessage>
			) : (
				<div className="refund-observation-list">
					{v.items.map((c) => (
						<article
							key={c.caseId}
							className="refund-observation"
							aria-label={`Operations case ${c.caseId}`}
						>
							<header className="toolbar">
								<h3>Linked operations case</h3>
								<Badge tone="info">{c.existingCaseState}</Badge>
								<Badge tone="warning">{c.assignmentStatus}</Badge>
							</header>
							<dl className="account-summary">
								<div>
									<dt>Case reference</dt>
									<dd>{c.caseId}</dd>
								</div>
								<div>
									<dt>Case revision</dt>
									<dd>{c.caseRevision}</dd>
								</div>
								<div>
									<dt>Captured room</dt>
									<dd>{c.roomId}</dd>
								</div>
								<div>
									<dt>Captured purchased revision</dt>
									<dd>
										{c.capturedBookingRevision}
										{c.capturedBookingRevision !== v.currentBookingRevision
											? " · Historical revision"
											: " · Current revision"}
									</dd>
								</div>
								<div>
									<dt>Captured session start</dt>
									<dd>
										<time dateTime={c.capturedSessionStart}>
											{c.capturedSessionStart}
										</time>
									</dd>
								</div>
								<div>
									<dt>Captured session end</dt>
									<dd>
										<time dateTime={c.capturedSessionEnd}>
											{c.capturedSessionEnd}
										</time>
									</dd>
								</div>
								<div>
									<dt>Created</dt>
									<dd>
										<time dateTime={c.createdAt}>{c.createdAt}</time>
									</dd>
								</div>
								<div>
									<dt>Updated</dt>
									<dd>
										<time dateTime={c.updatedAt}>{c.updatedAt}</time>
									</dd>
								</div>
							</dl>
							<p>
								Recorded exception categories:{" "}
								{c.recordedExceptionCategories.length
									? c.recordedExceptionCategories
											.map((c) => categoryLabels[c])
											.join(", ")
									: "None recorded"}
								. These are historical records, not current unresolved
								exceptions or device health.
							</p>
						</article>
					))}
				</div>
			)}
		</section>
	);
}
function OperationsRead({ booking }: { booking: StaffBooking }) {
	const [page, setPage] = useState(0),
		read = useCallback(
			(signal: AbortSignal) =>
				loadStaffBookingOperations(booking, page, signal),
			[booking, page],
		),
		state = useProtectedRead(read, true);
	const move = (next: number) => {
		state.clear();
		setPage(next);
	};
	return (
		<>
			<Button
				aria-disabled={state.loading}
				onClick={() => {
					if (!state.loading) void state.reload();
				}}
			>
				Reload linked operations cases
			</Button>
			{state.loading && (
				<StateMessage kind="loading">
					<p>Loading current authorized operations context.</p>
				</StateMessage>
			)}
			{state.error && (
				<StateMessage kind={state.error}>
					<p>
						{state.error === "denied"
							? "Operations access is denied. Independent current operations.synthetic.read authority at this venue is required; booking, access or refund permissions cannot substitute."
							: state.error === "unauthenticated"
								? "Your session is unavailable. Sign in again and reload the booking."
								: state.error === "conflict"
									? "Booking or operations context changed. Reload booking detail before reading operations again."
									: "Operations context is missing, limited or unavailable. Protected results were cleared. Check booking and venue; explicitly retry this read when the service is available."}
					</p>
				</StateMessage>
			)}
			{state.value &&
				state.value.bookingId === booking.bookingId &&
				state.value.venueId === booking.venueId &&
				state.value.currentBookingRevision === booking.revision &&
				state.value.page === page && (
					<>
						<StaffOperationsSummary value={state.value} />
						<nav aria-label="Booking operations pagination" className="toolbar">
							<Button
								variant="secondary"
								disabled={page === 0}
								onClick={() => move(page - 1)}
							>
								Previous operations page
							</Button>
							<Button
								variant="secondary"
								disabled={!state.value.hasNext || page === 100000}
								onClick={() => move(page + 1)}
							>
								Next operations page
							</Button>
						</nav>
					</>
				)}
		</>
	);
}
export function StaffBookingOperationsPanel({
	booking,
}: {
	booking: StaffBooking;
}) {
	const [opened, setOpened] = useState(false);
	return (
		<Card
			title="Booking Operations"
			className="staff-review-status booking-operations"
		>
			<p>
				Only explicitly linked cases for this exact booking. Captured historical
				revisions retain their original purchased room and session.
			</p>
			<p role="note">
				<strong>SYNTHETIC OPERATIONS — NOT PHYSICAL CLEARANCE.</strong>{" "}
				Occupancy: UNKNOWN. Readiness: UNVERIFIED. Inventory changed: false. No
				cleanliness, vacancy, safety or admission is established, even when a
				case is resolved or no cases are recorded.
			</p>
			<p>
				Independent venue-scoped operations-read permission is required. No
				operational actions are available here.
			</p>
			<Button
				aria-expanded={opened}
				aria-controls="booking-operations-results"
				onClick={() => setOpened((v) => !v)}
			>
				{opened
					? "Hide linked operations cases"
					: "View linked operations cases"}
			</Button>
			<div id="booking-operations-results">
				{opened && <OperationsRead booking={booking} />}
			</div>
		</Card>
	);
}

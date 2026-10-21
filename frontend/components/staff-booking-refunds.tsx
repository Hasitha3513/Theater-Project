"use client";
import { useCallback, useState } from "react";
import { displayMoney } from "../lib/packages";
import type { StaffBooking } from "../lib/staff-bookings";
import {
	loadStaffBookingRefunds,
	type StaffBookingRefunds,
} from "../lib/staff-booking-refunds";
import { Badge, Button, Card, StateMessage } from "./primitives";
import { useProtectedRead } from "./staff-bookings";
export function StaffRefundSummary({
	value: v,
}: {
	value: StaffBookingRefunds;
}) {
	return (
		<section
			aria-label="Protected booking refund results"
			style={{ overflowWrap: "anywhere" }}
		>
			<dl className="account-summary">
				<div>
					<dt>Booking revision</dt>
					<dd>{v.bookingRevision}</dd>
				</div>
				<div>
					<dt>Originating bank request</dt>
					<dd>{v.originatingRequestId}</dd>
				</div>
				<div>
					<dt>Server observation</dt>
					<dd>
						<time dateTime={v.observedAt}>{v.observedAt}</time>
					</dd>
				</div>
			</dl>
			<p>Page {v.page + 1}. Current records may change between reads.</p>
			{!v.items.length ? (
				<StateMessage kind="empty">
					<p>
						No recorded refund obligations associated with this booking&apos;s
						originating bank request. This does not establish eligibility, a
						zero refundable amount, cancellation approval or completed
						repayment.
					</p>
				</StateMessage>
			) : (
				<div className="refund-observation-list">
					{v.items.map((r) => (
						<article
							key={r.id}
							className="refund-observation"
							aria-label={`Refund ${r.id}`}
						>
							<header className="toolbar">
								<h3>Refund obligation</h3>
								<Badge tone="warning">{r.state}</Badge>
							</header>
							<p>
								<strong>
									{r.currency} {displayMoney(r.amountMinor)}
								</strong>{" "}
								· Synthetic only
							</p>
							<dl className="account-summary">
								<div>
									<dt>Refund reference</dt>
									<dd>{r.id}</dd>
								</div>
								<div>
									<dt>Revision</dt>
									<dd>{r.revision}</dd>
								</div>
								<div>
									<dt>Resolution</dt>
									<dd>{r.resolution}</dd>
								</div>
								<div>
									<dt>Destination verification</dt>
									<dd>
										{r.destinationVerified
											? "Verified in synthetic workflow"
											: "Not verified"}
									</dd>
								</div>
							</dl>
							{r.state === "APPROVED" && (
								<p>
									Approved does not mean repaid. No repayment is executed here.
								</p>
							)}
							{r.state === "UNKNOWN_RECONCILIATION_REQUIRED" && (
								<StateMessage kind="conflict">
									<p>
										Repayment outcome is uncertain. This read does not permit a
										retry or resend. Independent reconciliation remains
										required.
									</p>
								</StateMessage>
							)}
							{r.state === "SIMULATED_CONFIRMED" && (
								<p>
									Simulated completion only. No real repayment is established.
								</p>
							)}
							{r.attempts.length ? (
								<details>
									<summary>Recorded attempts ({r.attempts.length})</summary>
									<ul>
										{r.attempts.map((a) => (
											<li key={a.id}>
												Attempt {a.sequence} · {a.id}
												<br />
												Evidence phases:{" "}
												{a.evidencePhases.length
													? a.evidencePhases.join(", ")
													: "No recorded phases"}
											</li>
										))}
									</ul>
								</details>
							) : (
								<p>No recorded attempts.</p>
							)}
						</article>
					))}
				</div>
			)}
		</section>
	);
}
function RefundRead({ booking }: { booking: StaffBooking }) {
	const [page, setPage] = useState(0);
	const read = useCallback(
		(signal: AbortSignal) => loadStaffBookingRefunds(booking, page, 10, signal),
		[booking, page],
	);
	const state = useProtectedRead(read, true);
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
				Reload refund obligations
			</Button>
			{state.loading && (
				<StateMessage kind="loading">
					<p>Loading current authorized refund obligations.</p>
				</StateMessage>
			)}
			{state.error && (
				<StateMessage kind={state.error}>
					<p>
						{state.error === "denied"
							? "Refund access is denied. Independent current refund.synthetic.read authority at this venue is required; booking-read authority alone is insufficient."
							: state.error === "unauthenticated"
								? "Your session is unavailable. Sign in again and reload the booking."
								: state.error === "conflict"
									? "Booking or refund context changed. Reload booking detail before reading refunds again."
									: "Refund context is missing, limited or unavailable. Protected results were cleared. Check the booking and venue; retry this read explicitly when the service is available."}
					</p>
				</StateMessage>
			)}
			{state.value &&
				state.value.bookingId === booking.bookingId &&
				state.value.venueId === booking.venueId &&
				state.value.bookingRevision === booking.revision &&
				state.value.page === page && (
					<>
						<StaffRefundSummary value={state.value} />
						<nav aria-label="Refund observation pagination" className="toolbar">
							<Button
								variant="secondary"
								disabled={page === 0}
								onClick={() => move(page - 1)}
							>
								Previous refund page
							</Button>
							<Button
								variant="secondary"
								disabled={!state.value.hasNext || page === 10000}
								onClick={() => move(page + 1)}
							>
								Next refund page
							</Button>
						</nav>
					</>
				)}
		</>
	);
}
export function StaffBookingRefundPanel({
	booking,
}: {
	booking: StaffBooking;
}) {
	const [opened, setOpened] = useState(false);
	return (
		<Card title="Booking refund context" className="staff-review-status">
			<p>
				Refund obligations associated with this booking&apos;s originating bank
				request.
			</p>
			<p role="note">
				<strong>SYNTHETIC REFUND OBSERVATION — NO REAL REPAYMENT.</strong>{" "}
				Read-only context for unallocated synthetic bank credit. No refund
				eligibility, cancellation approval or authority to act is established.
			</p>
			<p>
				Separate venue-scoped refund-read permission is required. No financial
				action is available here.
			</p>
			<Button
				aria-expanded={opened}
				aria-controls="booking-refund-results"
				onClick={() => setOpened((v) => !v)}
			>
				{opened ? "Hide refund obligations" : "View refund obligations"}
			</Button>
			<div id="booking-refund-results">
				{opened && <RefundRead booking={booking} />}
			</div>
		</Card>
	);
}

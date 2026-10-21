"use client";
import Link from "next/link";
import { useCallback } from "react";
import {
	type BookingAccessContext,
	loadStaffBookingAccess,
	type StaffBookingAccess,
} from "../lib/staff-booking-access";
import { Badge, Button, Card, StateMessage } from "./primitives";
import { useProtectedRead } from "./staff-bookings";
export function StaffBookingAccessSummary({
	value: v,
}: {
	value: StaffBookingAccess;
}) {
	return (
		<section
			aria-label="Protected booking access status"
			style={{ overflowWrap: "anywhere" }}
		>
			<Badge tone="warning">{v.state} · SYNTHETIC ONLY</Badge>
			<dl className="account-summary">
				<div>
					<dt>Booking reference</dt>
					<dd>{v.bookingId}</dd>
				</div>
				<div>
					<dt>Venue</dt>
					<dd>{v.venueId}</dd>
				</div>
				<div>
					<dt>Room reference</dt>
					<dd>{v.roomId}</dd>
				</div>
				<div>
					<dt>Purchased session</dt>
					<dd>
						<time dateTime={v.sessionStart}>
							{new Intl.DateTimeFormat("en-GB", {
								timeZone: v.timezone,
								dateStyle: "medium",
								timeStyle: "short",
							}).format(new Date(v.sessionStart))}
						</time>{" "}
						–{" "}
						<time dateTime={v.sessionEnd}>
							{new Intl.DateTimeFormat("en-GB", {
								timeZone: v.timezone,
								dateStyle: "medium",
								timeStyle: "short",
							}).format(new Date(v.sessionEnd))}
						</time>{" "}
						({v.timezone})
					</dd>
				</div>
				<div>
					<dt>Booking revision</dt>
					<dd>{v.bookingRevision}</dd>
				</div>
				<div>
					<dt>Server observation</dt>
					<dd>
						<time dateTime={v.observedAt}>{v.observedAt}</time>
					</dd>
				</div>
			</dl>
			{!v.credential ? (
				<p>
					No synthetic credential has been issued for this booking. This status
					comes from the exact protected booking read.
				</p>
			) : (
				<>
					<h3>Credential observation</h3>
					<dl className="account-summary">
						<div>
							<dt>Credential reference</dt>
							<dd>{v.credential.id}</dd>
						</div>
						<div>
							<dt>Version / revision</dt>
							<dd>
								{v.credential.version} / {v.credential.revision}
							</dd>
						</div>
						<div>
							<dt>Provisioning</dt>
							<dd>{v.credential.provisioning}</dd>
						</div>
						<div>
							<dt>Simulation operation reference</dt>
							<dd>{v.credential.operationId}</dd>
						</div>
					</dl>
					{["UNKNOWN", "RECONCILIATION_REQUIRED"].includes(
						v.credential.provisioning,
					) && (
						<StateMessage kind="conflict">
							<p>
								Provisioning is uncertain. No installation, readiness or
								physical entry is confirmed.
							</p>
						</StateMessage>
					)}
				</>
			)}
			<p>
				Current policy reports:{" "}
				{v.permittedActions.length
					? v.permittedActions.join(", ")
					: "no credential actions"}
				. This page is read-only. Commands require separate explicit actions and
				fresh backend authorization.
			</p>
		</section>
	);
}
export function StaffBookingAccessPanel({
	context,
}: {
	context: BookingAccessContext;
}) {
	const read = useCallback(
		(signal: AbortSignal) => loadStaffBookingAccess(context, signal),
		[context],
	);
	const state = useProtectedRead(read, true);
	return (
		<div className="page-stack">
			<h1>Booking access management</h1>
			<p role="note">
				<strong>SYNTHETIC ACCESS TEST — NO DOOR CONTROL.</strong> This
				observation does not approve admission, confirm room vacancy or
				establish physical readiness. No credential is issued or revealed here.
			</p>
			<nav aria-label="Access context navigation" className="toolbar">
				<Link
					prefetch={false}
					href={`/admin/bookings/venues/${context.venue}/${context.booking}`}
				>
					Return to this booking
				</Link>
				<Link prefetch={false} href="/admin/access">
					Open venue access workspace
				</Link>
			</nav>
			<Card title="Current booking access" className="staff-review-status">
				<Button
					aria-disabled={state.loading}
					onClick={() => {
						if (!state.loading) void state.reload();
					}}
				>
					Reload booking access
				</Button>
				{state.loading && (
					<StateMessage kind="loading">
						<p>Loading current authorized booking access.</p>
					</StateMessage>
				)}
				{state.error && (
					<StateMessage kind={state.error}>
						<p>
							{state.error === "denied"
								? "Current access.synthetic.review authority at this venue is required. Booking-read permission does not grant access review."
								: state.error === "unauthenticated"
									? "Your session is unavailable. Sign in again, then explicitly reload."
									: "Access context is missing, changed, limited or unavailable. Protected results were cleared. Check the selected booking and venue before retrying."}
						</p>
					</StateMessage>
				)}
				{state.value &&
					state.value.venueId === context.venue &&
					state.value.bookingId === context.booking && (
						<StaffBookingAccessSummary value={state.value} />
					)}
			</Card>
		</div>
	);
}

import { rosterRecord } from "./guest-roster";
import { reviewReference } from "../lib/staff-review";
import { roomRequest } from "../lib/rooms";
import { offsetNanos, type StaffBooking } from "../lib/staff-bookings";
import { Badge } from "./primitives";
export function staffRosterRecord(input: unknown, booking: StaffBooking) {
	const value = rosterRecord(input, booking.bookingId);
	const context = input as {
		venueId?: unknown;
		serverNow: string;
		sessionStart: string;
	};
	if (
		context.venueId !== booking.venueId ||
		value.bookingRevision !== booking.revision ||
		value.purchasedLimit !== booking.purchasedGuestCount ||
		value.permittedActions.length !== 0 ||
		offsetNanos(context.sessionStart) !== offsetNanos(booking.scheduledStart)
	)
		throw new Error("Roster does not match current protected booking");
	offsetNanos(context.serverNow);
	// Project only supported fields; unknown identity/evidence fields never enter UI state.
	return {
		bookingId: value.bookingId,
		venueId: booking.venueId,
		bookingRevision: value.bookingRevision,
		purchasedLimit: value.purchasedLimit,
		roomCapacity: value.roomCapacity,
		count: value.count,
		initialized: value.initialized,
		status: value.status,
		serverNow: context.serverNow,
		entries: value.entries.map(({ id, kind, label, status }) => ({
			id,
			kind,
			label,
			status,
		})),
	};
}
export type StaffRoster = ReturnType<typeof staffRosterRecord>;
export async function loadStaffRoster(
	booking: StaffBooking,
	signal?: AbortSignal,
) {
	const venue = reviewReference(booking.venueId),
		id = reviewReference(booking.bookingId);
	return staffRosterRecord(
		await roomRequest(
			`/api/admin/venues/${venue}/bookings/${id}/guest-roster`,
			{ signal },
		),
		booking,
	);
}
export function StaffRosterSummary({ value }: { value: StaffRoster }) {
	return (
		<>
			<Badge tone="warning">{value.status} · SYNTHETIC ONLY</Badge>
			<dl className="account-summary">
				<div>
					<dt>Purchased guest limit</dt>
					<dd>{value.purchasedLimit}</dd>
				</div>
				<div>
					<dt>Registered synthetic guests</dt>
					<dd>{value.count}</dd>
				</div>
				<div>
					<dt>Current room capacity</dt>
					<dd>{value.roomCapacity}</dd>
				</div>
			</dl>
			{!value.initialized ? (
				<p>No synthetic guest roster has been initialized.</p>
			) : (
				<section
					className="table-scroll"
					aria-label="Scrollable synthetic guest entries"
				>
					<table aria-label="Registered synthetic guests">
						<thead>
							<tr>
								<th scope="col">Guest</th>
								<th scope="col">Registration</th>
							</tr>
						</thead>
						<tbody>
							{value.entries.map((entry) => (
								<tr key={entry.id}>
									<th scope="row">
										{entry.kind === "BOOKER" ? "Booker" : entry.label}
									</th>
									<td>{entry.status}</td>
								</tr>
							))}
						</tbody>
					</table>
				</section>
			)}
			<p>
				Server observation:{" "}
				<time dateTime={value.serverNow}>{value.serverNow}</time>
			</p>
		</>
	);
}

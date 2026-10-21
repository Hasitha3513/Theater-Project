"use client";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { stateFor } from "../lib/rooms";
import { bookingAccessHref } from "../lib/staff-booking-access";
import {
	type BookingFilters,
	bookingFilters,
	emptyBookingFilters,
	filtersFromUrl,
	loadStaffBooking,
	loadStaffBookings,
	type StaffBooking,
	type StaffBookingPage,
	staffBookingDetailHref,
	staffBookingListHref,
} from "../lib/staff-bookings";
import {
	Badge,
	Button,
	Card,
	Field,
	type StateKind,
	StateMessage,
} from "./primitives";
import { loadStaffRoster, StaffRosterSummary } from "./staff-guest-roster";
import { StaffVenueContext } from "./staff-venue-context";
export function useProtectedRead<T>(
	read: (signal: AbortSignal) => Promise<T>,
	enabled: boolean,
) {
	const [value, setValue] = useState<T>(),
		[loading, setLoading] = useState(false),
		[error, setError] = useState<StateKind>();
	const epoch = useRef(0),
		controller = useRef<AbortController | undefined>(undefined);
	const clear = useCallback(() => {
		epoch.current++;
		controller.current?.abort();
		setValue(undefined);
		setLoading(false);
	}, []);
	const reload = useCallback(async () => {
		clear();
		setError(undefined);
		if (!enabled) return;
		const token = epoch.current,
			c = new AbortController();
		controller.current = c;
		setLoading(true);
		try {
			const next = await read(
				AbortSignal.any([c.signal, AbortSignal.timeout(10000)]),
			);
			if (epoch.current === token) setValue(next);
		} catch (e) {
			if (epoch.current === token) setError(stateFor(e));
		} finally {
			if (epoch.current === token) setLoading(false);
		}
	}, [clear, enabled, read]);
	useEffect(() => {
		void reload();
		const refresh = () => {
			void reload();
		};
		const hide = () => {
			clear();
			setError("unauthenticated");
		};
		const visibility = () =>
			document.visibilityState === "hidden" ? hide() : refresh();
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", hide);
		window.addEventListener("cinenest-session-change", hide);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			clear();
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", hide);
			window.removeEventListener("cinenest-session-change", hide);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [clear, reload]);
	return { value, loading, error, reload, clear };
}
function Notice() {
	return (
		<p>
			<strong>
				SYNTHETIC STAFF BOOKING READS — NOT REAL COMMERCIAL CONFIRMATION.
			</strong>{" "}
			No booking, payment, refund, admission or access actions are available
			here. Backend read auditing is intentional.
		</p>
	);
}
function ReadState({
	loading,
	error,
}: {
	loading: boolean;
	error?: StateKind;
}) {
	return (
		<>
			{loading && (
				<StateMessage kind="loading">
					<p>Loading current authorized booking context.</p>
				</StateMessage>
			)}
			{error && (
				<StateMessage kind={error}>
					<p>
						{error === "unauthenticated"
							? "Session unavailable. Sign in again and reload."
							: error === "denied"
								? "Current booking.synthetic.read authority at this venue is required. Protected results were cleared."
								: "Booking unavailable, missing, changed or refused; check references and filters, then retry. No protected details or operational outcome is inferred."}
					</p>
				</StateMessage>
			)}
		</>
	);
}
export function StaffBookingSummary({ booking: b }: { booking: StaffBooking }) {
	return (
		<div style={{ overflowWrap: "anywhere" }}>
			<Badge tone="info">{b.state} / SYNTHETIC ONLY</Badge>
			<dl className="account-summary">
				<div>
					<dt>Booking reference</dt>
					<dd>{b.bookingId}</dd>
				</div>
				<div>
					<dt>Venue reference</dt>
					<dd>{b.venueId}</dd>
				</div>
				<div>
					<dt>Room reference</dt>
					<dd>{b.roomId}</dd>
				</div>
				<div>
					<dt>Purchased package</dt>
					<dd>{b.packageName}</dd>
				</div>
				<div>
					<dt>Purchased guest count</dt>
					<dd>{b.purchasedGuestCount}</dd>
				</div>
				<div>
					<dt>Scheduled start (inclusive filter)</dt>
					<dd>
						<time dateTime={b.scheduledStart}>{b.scheduledStart}</time>
					</dd>
				</div>
				<div>
					<dt>Scheduled end</dt>
					<dd>
						<time dateTime={b.scheduledEnd}>{b.scheduledEnd}</time>
					</dd>
				</div>
				<div>
					<dt>Captured venue timezone</dt>
					<dd>{b.timezone}</dd>
				</div>
				<div>
					<dt>Server revision</dt>
					<dd>{b.revision}</dd>
				</div>
			</dl>
		</div>
	);
}
export function StaffBookingList({ query = "" }: { query?: string }) {
	const { initial, invalid } = useMemo(() => {
		try {
			return {
				initial: query
					? filtersFromUrl(new URLSearchParams(query))
					: emptyBookingFilters,
				invalid: false,
			};
		} catch {
			return { initial: emptyBookingFilters, invalid: true };
		}
	}, [query]);
	const [fields, setFields] = useState(initial),
		[dirty, setDirty] = useState(false),
		[formError, setFormError] = useState(
			invalid
				? "Invalid or repeated booking filters. Reset and apply valid references."
				: "",
		);
	const read = useCallback(
		(signal: AbortSignal) => loadStaffBookings(initial, signal),
		[initial],
	);
	const state = useProtectedRead<StaffBookingPage>(
		read,
		Boolean(initial.venue) && !invalid && !dirty,
	);
	function change(key: keyof BookingFilters, value: string | number) {
		state.clear();
		setDirty(true);
		setFormError("");
		setFields((old) => ({ ...old, [key]: value, page: 0 }));
	}
	return (
		<>
			<Notice />
			<StaffVenueContext venue={fields.venue} />
			<ReadState loading={state.loading} error={state.error} />
			<Card title="Venue-scoped booking search">
				<p>
					Enter your assigned venue reference. Selection is not authorization;
					the protected backend checks your current booking-read grant. No room
					or finance permission is required.
				</p>
				<form
					onSubmit={(e) => {
						e.preventDefault();
						state.clear();
						try {
							const f = bookingFilters(fields);
							window.location.assign(staffBookingListHref(f));
						} catch (error) {
							setFormError(
								error instanceof Error ? error.message : "Invalid filters.",
							);
						}
					}}
				>
					<div className="filter-grid">
						<Field
							id="staff-booking-venue"
							label="Assigned venue reference"
							value={fields.venue}
							onChange={(e) => change("venue", e.target.value)}
							required
						/>
						<Field
							id="staff-booking-reference"
							label="Exact booking reference (optional)"
							value={fields.reference}
							onChange={(e) => change("reference", e.target.value)}
						/>
						<Field
							id="staff-booking-from"
							label="Scheduled start from (inclusive, offset required)"
							value={fields.from}
							placeholder="2026-10-20T09:00:00+05:30"
							onChange={(e) => change("from", e.target.value)}
						/>
						<Field
							id="staff-booking-to"
							label="Scheduled start to (exclusive, offset required)"
							value={fields.to}
							placeholder="2026-10-21T09:00:00+05:30"
							onChange={(e) => change("to", e.target.value)}
						/>
						<Field
							id="staff-booking-size"
							label="Bookings per page (1–25)"
							type="number"
							min={1}
							max={25}
							value={fields.size}
							onChange={(e) => change("size", Number(e.target.value))}
						/>
					</div>
					<p>
						Supply both offset-aware boundaries or neither; maximum 31 elapsed
						days. No date window is selected by default.
					</p>
					<Button type="submit">Apply booking filters</Button>{" "}
					<Link href="/admin/bookings" prefetch={false}>
						Reset booking search
					</Link>
				</form>
				{formError && <p role="alert">{formError}</p>}
				{dirty && (
					<p role="status">
						Filters changed. Apply filters to reload authorized results.
					</p>
				)}
			</Card>
			{!initial.venue && !formError && (
				<StateMessage kind="empty">
					<p>Select an assigned venue to load current bookings.</p>
				</StateMessage>
			)}
			<Button
				disabled={!initial.venue || invalid || dirty || state.loading}
				onClick={() => void state.reload()}
			>
				Reload authorized bookings
			</Button>
			{state.value && !dirty && (
				<>
					<p>
						Server observation: {state.value.observedAt}. Page{" "}
						{state.value.page + 1}; live results may change between requests.
					</p>
					{state.value.items.length === 0 ? (
						<StateMessage kind="empty">
							<p>No matching synthetic bookings in this authorized venue.</p>
						</StateMessage>
					) : (
						state.value.items.map((b) => (
							<Card key={b.bookingId} title={b.packageName}>
								<StaffBookingSummary booking={b} />
								<Link
									prefetch={false}
									aria-label={`View exact booking details for ${b.bookingId}`}
									href={staffBookingDetailHref(
										initial.venue,
										b.bookingId,
										initial,
									)}
								>
									View exact booking details
								</Link>
							</Card>
						))
					)}
					<nav aria-label="Booking pagination" className="toolbar">
						{initial.page > 0 && (
							<Link
								prefetch={false}
								href={staffBookingListHref({
									...initial,
									page: initial.page - 1,
								})}
							>
								Previous booking page
							</Link>
						)}
						{state.value.hasNext && initial.page < 10000 && (
							<Link
								prefetch={false}
								href={staffBookingListHref({
									...initial,
									page: initial.page + 1,
								})}
							>
								Next booking page
							</Link>
						)}
					</nav>
				</>
			)}
		</>
	);
}
function StaffGuestRoster({ booking }: { booking: StaffBooking }) {
	const read = useCallback(
		(signal: AbortSignal) => loadStaffRoster(booking, signal),
		[booking],
	);
	const state = useProtectedRead(read, true);
	return (
		<section aria-label="Staff guest roster">
			<Card title="Registered guest roster" className="staff-review-status">
				<p>
					<strong>
						SYNTHETIC GUEST REGISTRATION — NOT PHYSICAL ADMISSION.
					</strong>{" "}
					Entries are unverified declarations. Registration is not identity
					verification, age approval or permission to enter. No access
					credential is shown or issued.
				</p>
				<p>
					Guest-roster access requires separate current venue-scoped authority;
					booking-read permission alone is insufficient.
				</p>
				<Button
					aria-disabled={state.loading}
					onClick={() => {
						if (!state.loading) void state.reload();
					}}
				>
					Reload guest roster
				</Button>
				{state.loading && (
					<StateMessage kind="loading">
						<p>Loading current authorized guest roster.</p>
					</StateMessage>
				)}
				{state.error && (
					<StateMessage kind={state.error}>
						<p>
							{state.error === "denied"
								? "Guest-roster access is denied. Separate guest.synthetic.read authority at this venue is required."
								: state.error === "unauthenticated"
									? "Session unavailable. Sign in again and reload the booking."
									: "Guest roster unavailable, missing, changed or refused. Reload current booking context before retrying."}
						</p>
					</StateMessage>
				)}
				{state.value && <StaffRosterSummary value={state.value} />}
			</Card>
		</section>
	);
}
export function StaffBookingDetail({
	venue,
	booking,
	query = "",
}: {
	venue: string;
	booking: string;
	query?: string;
}) {
	let back = "/admin/bookings";
	try {
		const p = new URLSearchParams(query);
		p.set("venue", venue);
		back = staffBookingListHref(filtersFromUrl(p));
	} catch {
		/* Never reflect unknown or unsafe navigation context. */
	}
	const read = useCallback(
		(signal: AbortSignal) => loadStaffBooking(venue, booking, signal),
		[venue, booking],
	);
	const state = useProtectedRead(read, true);
	return (
		<>
			<Notice />
			<Link prefetch={false} href={back}>
				Return to booking results
			</Link>
			<Card title="Protected staff booking detail">
				<Button disabled={state.loading} onClick={() => void state.reload()}>
					Reload booking detail
				</Button>
				<ReadState loading={state.loading} error={state.error} />
				{state.value &&
					state.value.booking.venueId === venue.toLowerCase() &&
					state.value.booking.bookingId === booking.toLowerCase() && (
						<section aria-label="Authorized staff booking detail">
							<StaffBookingSummary booking={state.value.booking} />
							<Link
								className="button button-primary booking-access-link"
								prefetch={false}
								href={bookingAccessHref(
									state.value.booking.venueId,
									state.value.booking.bookingId,
								)}
							>
								Manage Cinema Access
							</Link>
							<p>Server observation: {state.value.observedAt}</p>
						</section>
					)}
			</Card>
			{state.value &&
				state.value.booking.venueId === venue.toLowerCase() &&
				state.value.booking.bookingId === booking.toLowerCase() && (
					<StaffGuestRoster
						key={`${venue}/${booking}/${state.value.booking.revision}`}
						booking={state.value.booking}
					/>
				)}
		</>
	);
}

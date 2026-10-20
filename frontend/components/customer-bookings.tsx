"use client";
import { bankRequestHref, confirmedBookingHref } from "../lib/bank-request";
import { useCallback, useEffect, useRef, useState } from "react";
import { roomRequest } from "../lib/rooms";
import { readSession } from "../lib/session";
import { GuestRoster } from "./guest-roster";
import { BookingAmendment } from "./booking-amendment";
import { Badge, Button, Card } from "./primitives";
export const CUSTOMER_LABEL = "SYNTHETIC BANK PAYMENT — NO REAL MONEY";
const UUID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/;
type Purchase = {
	name: string;
	description: string;
	packageVersionId: string;
	sessionStart: string;
	sessionEnd: string;
	timezone: string;
	roomId: string;
	amountMinor: string;
	currency: string;
	lines: {
		lineId: string;
		kind: string;
		label: string;
		quantity: string;
		amountMinor: string;
	}[];
};
type Booking = {
	amendmentPlanning?: {
		packageId: string;
		guests: number;
		optionalServiceIds: string[];
		paymentMethod: string;
	};
	latestAmendmentId?: string;
	originalSnapshot?: Record<string, unknown>;
	id: string;
	requestId: string;
	venueId: string;
	state?: string;
	syntheticBookingStatus?: string;
	revision: number;
	purchase: Purchase;
	synthetic: true;
	purchasedAt?: string;
	confirmedAt?: string;
	observedAt?: string;
};
type Funding = {
	verifiedMatchedMinor: string;
	allocatedMinor: string;
	outstandingMinor: string;
	fundingShortfallMinor: string;
	status: string;
	bookingStatus: string;
	bookingId: string | null;
	realPayment: false;
};
type RequestSummary = {
	id: string;
	revision: number;
	state: string;
	reviewState: string;
	claimSubmitted: boolean;
	amountMinor: string;
	currency: string;
	expiresAt: string;
	submitBy: string;
	reviewBy: string;
	funding: Funding;
	resolutions: { kind: string; state: string }[];
	synthetic: true;
};
type Refund = {
	id: string;
	revision: number;
	state: string;
	amountMinor: string;
	currency: string;
	resolution: string;
	synthetic: true;
	realRepayment: false;
};
type Page<T> = {
	items: T[];
	page: number;
	size: number;
	hasNext: boolean;
	observedAt: string;
	synthetic: true;
};
type RequestDetail = {
	id: string;
	revision: number;
	requestState: string;
	reviewState: string;
	financial: {
		verifiedMatchedMinor: string;
		syntheticFundingStatus: string;
		allocatedMinor: number;
		outstandingMinor: number;
		fundingShortfallMinor: number;
		syntheticBookingStatus: string;
		bookingId: string | null;
	};
	history: { action: string; at?: string; reason?: string }[];
};
export function bookingRecord(value: unknown): Booking {
	const b = value as Booking,
		p = b?.purchase;
	if (
		b?.synthetic !== true ||
		!UUID.test(b.id) ||
		!UUID.test(b.requestId) ||
		!UUID.test(b.venueId) ||
		![1, 2].includes(b.revision) ||
		(b.state ?? b.syntheticBookingStatus) !== "SIMULATED_CONFIRMED" ||
		(b.state !== undefined && b.state !== "SIMULATED_CONFIRMED") ||
		(b.syntheticBookingStatus !== undefined &&
			b.syntheticBookingStatus !== "SIMULATED_CONFIRMED") ||
		!p ||
		!UUID.test(p.roomId) ||
		!/^\d+$/.test(p.amountMinor) ||
		p.currency !== "LKR" ||
		!Array.isArray(p.lines) ||
		!Number.isFinite(Date.parse(p.sessionStart)) ||
		!Number.isFinite(Date.parse(p.sessionEnd))
	)
		throw Error("Invalid purchased snapshot");
	new Intl.DateTimeFormat("en", { timeZone: p.timezone }).format();
	return b;
}
export function bookingGroup(b: Booking, observedAt: string) {
	const now = Date.parse(observedAt);
	if (!Number.isFinite(now))
		throw Error("Missing authoritative observation time");
	return now < Date.parse(b.purchase.sessionStart)
		? "Upcoming"
		: now < Date.parse(b.purchase.sessionEnd)
			? "Current"
			: "Past";
}
export function money(amount: string, currency: string) {
	if (!/^\d+$/.test(amount) || currency !== "LKR")
		throw Error("Invalid original amount");
	const n = BigInt(amount);
	return `${currency} ${n / 100n}.${(n % 100n).toString().padStart(2, "0")}`;
}
function pageRecord<T>(p: Page<T>): Page<T> {
	if (
		p?.synthetic !== true ||
		!Array.isArray(p.items) ||
		p.items.length > 25 ||
		!Number.isSafeInteger(p.page) ||
		p.page < 0 ||
		!Number.isSafeInteger(p.size) ||
		p.size < 1 ||
		p.size > 25 ||
		typeof p.hasNext !== "boolean" ||
		!Number.isFinite(Date.parse(p.observedAt))
	)
		throw Error("Invalid page");
	return p;
}
export function refundRecord(r: Refund) {
	if (
		r?.synthetic !== true ||
		r.realRepayment !== false ||
		!UUID.test(r.id) ||
		!Number.isSafeInteger(r.revision) ||
		![
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
		].includes(r.state)
	)
		throw Error("Invalid synthetic refund");
	money(r.amountMinor, r.currency);
	return r;
}
function requestRecord(r: RequestSummary) {
	if (
		r?.synthetic !== true ||
		!UUID.test(r.id) ||
		!Number.isSafeInteger(r.revision) ||
		r.funding?.realPayment !== false
	)
		throw Error("Invalid request");
	money(r.amountMinor, r.currency);
	return r;
}
function Purchased({ b }: { b: Booking }) {
	const p = b.purchase;
	const date = (v: string) =>
		new Intl.DateTimeFormat("en-GB", {
			dateStyle: "medium",
			timeStyle: "short",
			timeZone: p.timezone,
		}).format(new Date(v));
	return (
		<>
			<h3>{p.name}</h3>
			<p>{p.description}</p>
			<p>
				Venue: {b.venueId} · Room: {p.roomId}
			</p>
			<p>
				{date(p.sessionStart)} – {date(p.sessionEnd)} ({p.timezone})
			</p>
			<p>
				Original purchase: {money(p.amountMinor, p.currency)} · Revision{" "}
				{b.revision}
			</p>
			<p>Package version: {p.packageVersionId}</p>
			<ul>
				{p.lines.map((l) => (
					<li key={l.lineId}>
						{l.label} × {l.quantity}: {money(l.amountMinor, p.currency)}
					</li>
				))}
			</ul>
			<p>SIMULATED BOOKING CONFIRMED. Elapsed time does not mean completed.</p>
		</>
	);
}
function Pager({
	page,
	hasNext,
	busy,
	load,
}: {
	page: number;
	hasNext: boolean;
	busy: boolean;
	load: (n: number) => void;
}) {
	return (
		<div>
			<p>Page {page + 1}</p>
			<Button
				variant="secondary"
				disabled={busy || page === 0}
				onClick={() => load(page - 1)}
			>
				Previous
			</Button>
			<Button
				variant="secondary"
				disabled={busy || !hasNext}
				onClick={() => load(page + 1)}
			>
				Next
			</Button>
		</div>
	);
}
export function CustomerBookings({ bookingId }: { bookingId?: string }) {
	const [bookings, setBookings] = useState<Page<Booking> | null>(null),
		[requests, setRequests] = useState<Page<RequestSummary> | null>(null),
		[detail, setDetail] = useState<Booking | null>(null),
		[payment, setPayment] = useState<RequestDetail | null>(null),
		[refunds, setRefunds] = useState<Page<Refund> | null>(null),
		[error, setError] = useState(""),
		[busy, setBusy] = useState(false);
	const generation = useRef(0);
	const load = useCallback(
		async (bookingPage = 0, requestPage = 0, refundPage = 0) => {
			const epoch = ++generation.current;
			setBookings(null);
			setRequests(null);
			setDetail(null);
			setPayment(null);
			setRefunds(null);
			setError("");
			setBusy(true);
			try {
				const session = await readSession();
				if (session.kind !== "authenticated") throw Error();
				if (bookingId && !UUID.test(bookingId)) throw Error();
				if (bookingId) {
					const b = bookingRecord(
						await roomRequest<Booking>(`/api/bookings/${bookingId}`),
					);
					if (b.id !== bookingId) throw Error();
					const [p, f] = await Promise.all([
						roomRequest<RequestDetail>(`/api/bank/requests/${b.requestId}`),
						roomRequest<Page<Refund>>(
							`/api/bank/requests/${b.requestId}/refunds?page=${refundPage}&size=10`,
						),
					]);
					if (
						p.id !== b.requestId ||
						p.financial.bookingId !== b.id ||
						p.financial.syntheticBookingStatus !== "SIMULATED_CONFIRMED" ||
						!Number.isSafeInteger(p.revision)
					)
						throw Error();
					const safe = pageRecord(f);
					safe.items = safe.items.map(refundRecord);
					if (epoch !== generation.current) return;
					setDetail(b);
					setPayment(p);
					setRefunds(safe);
				} else {
					const [b, r] = await Promise.all([
						roomRequest<Page<Booking>>(
							`/api/bookings?page=${bookingPage}&size=10`,
						),
						roomRequest<Page<RequestSummary>>(
							`/api/bank/requests?page=${requestPage}&size=10`,
						),
					]);
					const bs = pageRecord(b),
						rs = pageRecord(r);
					bs.items = bs.items.map(bookingRecord);
					rs.items = rs.items.map(requestRecord);
					if (epoch !== generation.current) return;
					setBookings(bs);
					setRequests(rs);
				}
			} catch {
				if (epoch === generation.current) {
					setError(
						"Unavailable, signed out, denied or changed. Refresh your current account context; no success is inferred.",
					);
				}
			} finally {
				if (epoch === generation.current) setBusy(false);
			}
		},
		[bookingId],
	);
	useEffect(() => {
		function clear() {
			generation.current++;
			setBookings(null);
			setRequests(null);
			setDetail(null);
			setPayment(null);
			setRefunds(null);
			setError("");
			setBusy(false);
		}
		function refresh() {
			if (bookingId) void load();
		}
		const visibility = () =>
			document.visibilityState === "hidden" ? clear() : refresh();
		clear();
		if (bookingId) {
			if (UUID.test(bookingId)) refresh();
			else setError("Invalid booking reference.");
		}
		window.addEventListener("cinenest-session-change", clear);
		window.addEventListener("pagehide", clear);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("focus", refresh);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			generation.current++;
			window.removeEventListener("cinenest-session-change", clear);
			window.removeEventListener("pagehide", clear);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("focus", refresh);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [bookingId, load]);
	return (
		<div className="customer-bookings">
			<h1>{bookingId ? "Booking detail" : "My bookings"}</h1>
			{bookingId && (
				<p>
					<a href="/bookings">Return to customer history</a> ·{" "}
					<a href="/account">Account and session</a>
				</p>
			)}
			<p>
				<Badge tone="warning">{CUSTOMER_LABEL}</Badge>
			</p>
			<p>
				Synthetic purchased history and bounded rescheduling. No real payment,
				refund, admission or support ticket is created.
			</p>
			<Button busy={busy} onClick={() => void load()}>
				Refresh booking history
			</Button>
			<div role="status" aria-live="polite">
				{busy ? "Loading owned history…" : error}
			</div>
			{bookings && (
				<Card title="Your purchased bookings">
					<p>
						Observed: {bookings.observedAt}. Groups apply to this page only.
					</p>
					{bookings.items.length === 0 ? (
						<p>No owned bookings.</p>
					) : (
						["Upcoming", "Current", "Past"].map((group) => (
							<section key={group}>
								<h2>{group}</h2>
								<ul>
									{bookings.items
										.filter(
											(b) => bookingGroup(b, bookings.observedAt) === group,
										)
										.map((b) => (
											<li key={b.id}>
												<Purchased b={b} />
												<a href={`/bookings/${b.id}`}>View booking {b.id}</a>
											</li>
										))}
								</ul>
							</section>
						))
					)}
					<Pager
						page={bookings.page}
						hasNext={bookings.hasNext}
						busy={busy}
						load={(n) => void load(n, requests?.page ?? 0)}
					/>
				</Card>
			)}
			{requests && (
				<Card title="Bank-payment requests — separate from bookings">
					<p>Observed: {requests.observedAt}</p>
					{requests.items.length === 0 ? (
						<p>No owned bank-payment requests.</p>
					) : (
						<ul>
							{requests.items.map((r) => (
								<li key={r.id}>
									<h3>Request {r.id}</h3>
									<p>
										{r.funding.bookingStatus === "NOT_CONFIRMED"
											? "Pending/unconfirmed request — not a booking"
											: "Linked synthetic booking"}
									</p>
									<p>
										Claim:{" "}
										{r.claimSubmitted
											? "BANK CLAIM SUBMITTED — not credit proof"
											: "No submitted claim"}
										. Review: {r.reviewState}. Request: {r.state}, revision{" "}
										{r.revision}.
									</p>
									<p>
										Funding: {r.funding.status}. Allocated:{" "}
										{money(r.funding.allocatedMinor, r.currency)}; outstanding:{" "}
										{money(r.funding.outstandingMinor, r.currency)}.
									</p>
									<p>
										Submit by {r.submitBy}; review by {r.reviewBy}; expires{" "}
										{r.expiresAt}.
									</p>
									<p>
										Support status: {r.reviewState}; {r.funding.status}. No
										ticket or response time promised.
									</p>
									<ul aria-label="Recorded resolutions">
										{r.resolutions.map((x) => (
											<li key={`${x.kind}-${x.state}`}>
												{x.kind}: {x.state}
											</li>
										))}
									</ul>
									<p>
										Independently verified matched synthetic credit:{" "}
										{money(r.funding.verifiedMatchedMinor, r.currency)}. This is
										not an available balance or real payment.
									</p>
									<a href={bankRequestHref(r.id)}>View Bank Request</a>
									{confirmedBookingHref(
										r.funding.bookingStatus,
										r.funding.bookingId,
									) && (
										<p>
											<a
												href={
													confirmedBookingHref(
														r.funding.bookingStatus,
														r.funding.bookingId,
													) ?? "/bookings"
												}
											>
												Open linked booking
											</a>
										</p>
									)}
								</li>
							))}
						</ul>
					)}
					<Pager
						page={requests.page}
						hasNext={requests.hasNext}
						busy={busy}
						load={(n) => void load(bookings?.page ?? 0, n)}
					/>
				</Card>
			)}
			{detail && detail.id === bookingId && payment && refunds && (
				<>
					<Card title="Purchased snapshot">
						<Purchased b={detail} />
						<p>
							Purchased at {detail.confirmedAt}; observed {detail.observedAt}.
						</p>
						<p>
							History: retained immutable revision {detail.revision},
							SIMULATED_CONFIRMED. Original purchased history is retained.
						</p>
					</Card>
					<GuestRoster key={detail.id} bookingId={detail.id} />
					<BookingAmendment
						booking={detail}
						onCommitted={() => void load(0, 0)}
					/>
					<Card title="Payment and support status">
						<p>
							Independently verified matched synthetic credit:{" "}
							{money(
								payment.financial.verifiedMatchedMinor,
								detail.purchase.currency,
							)}{" "}
							— not an available balance or real payment.
						</p>
						<ul aria-label="Recorded review reasons">
							{payment.history.map((h) => (
								<li key={`${h.action}-${h.at}`}>
									{h.action}: {h.reason} {h.at}
								</li>
							))}
						</ul>
						<p>
							Receipt review: {payment.reviewState}; request{" "}
							{payment.requestState}, revision {payment.revision}.
						</p>
						<p>
							Synthetic funding: {payment.financial.syntheticFundingStatus}.
							allocated{" "}
							{money(
								String(payment.financial.allocatedMinor),
								detail.purchase.currency,
							)}
							; outstanding{" "}
							{money(
								String(payment.financial.outstandingMinor),
								detail.purchase.currency,
							)}
							.
						</p>
						<p>
							Support status is derived from the recorded review and funding
							states above. Unresolved states remain unresolved; use the
							protected workspaces to inspect permitted next actions. No support
							ticket, contact, allocation or repayment is implied.
						</p>
						<a href={bankRequestHref(payment.id)}>View Bank Request</a>
					</Card>
					<Card title="Synthetic refund status">
						<p>
							APPROVED is not repayment. SIMULATED_CONFIRMED means no real money
							was transferred.
						</p>
						{refunds.items.length === 0 ? (
							<p>No recorded refund obligations.</p>
						) : (
							<ul>
								{refunds.items.map((f) => (
									<li key={f.id}>
										{f.state} — {money(f.amountMinor, f.currency)} · revision{" "}
										{f.revision}. Resolution: {f.resolution}. SYNTHETIC REFUND —
										NO REAL MONEY RETURNED.
									</li>
								))}
							</ul>
						)}
						<Pager
							page={refunds.page}
							hasNext={refunds.hasNext}
							busy={busy}
							load={(n) => void load(0, 0, n)}
						/>
						<a href="/refunds">Open protected refund workspace</a>
					</Card>
					<Card title="Access and notifications">
						<p>
							SYNTHETIC ACCESS — NO PHYSICAL DOOR CONTROL. Opening the workspace
							does not issue or reveal a credential.
						</p>
						<a href="/access">Open protected access status</a>
						<p>
							Notifications remain separate from funds and access. Navigation
							does not mark messages read or establish delivery.
						</p>
						<a href="/notifications">Open protected notifications</a>
					</Card>
					<a href="/bookings">Back to My bookings</a>
				</>
			)}
		</div>
	);
}

"use client";
import { CustomerAvailabilityCalendar } from "./customer-calendar";
import { BookingStepGuide, AwaitingBookingQuote } from "./booking-steps";
import { SyntheticQuotation } from "./quotes";
import type { FormEvent } from "react";
import { useEffect, useRef, useState } from "react";
import { operationalQuoteHold } from "../lib/commercial-quotes";
import { catalogItems, type PublicPackage } from "../lib/publications";
import type { Candidate, Hold } from "../lib/inventory";
import { remainingSeconds, validateHold, venueTime } from "../lib/inventory";
import { RoomRequestError, roomRequest, stateFor } from "../lib/rooms";
import { Button, Card, Field, SelectField, StateMessage } from "./primitives";

type HoldCandidate = Candidate & { mode?: "OPERATIONAL" };
const packageReference = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;

export function operationalHoldPackage(
	candidate: HoldCandidate,
	items: PublicPackage[],
	selectedVersion: string,
) {
	if (candidate.mode !== "OPERATIONAL") return {};
	const matches = items.filter((item) => item.versionId === selectedVersion);
	const selected = matches[0];
	if (
		matches.length !== 1 ||
		!packageReference.test(selected.packageId) ||
		!packageReference.test(selected.versionId)
	)
		throw new Error("Choose a published package for this interval.");
	return {
		packageId: selected.packageId,
		packageVersionId: selected.versionId,
	};
}

export function HoldStatus({
	hold,
	elapsedMs,
}: {
	hold: Hold;
	elapsedMs: number;
}) {
	const left = remainingSeconds(hold, elapsedMs);
	return (
		<section className="booking-hold-status" aria-label="Inventory hold status">
			<p role="status">
				{hold.state === "ACTIVE"
					? left > 0
						? "Temporary inventory hold"
						: "Deadline reached; refresh authoritative state"
					: `Hold ${hold.state.toLowerCase()}`}
			</p>
			<p>
				Customer session: {venueTime(hold.sessionStart, hold.timezone)} –{" "}
				{venueTime(hold.sessionEnd, hold.timezone)} ({hold.timezone})
			</p>
			<p>
				Cleaning buffer: {hold.bufferMinutes} minutes. Inventory blocked until{" "}
				{venueTime(hold.blockedUntil, hold.timezone)}.
			</p>
			<p>Server expiry: {venueTime(hold.expiresAt, hold.timezone)}</p>
			<p aria-live="off">Time remaining: {left} seconds</p>
			<p>
				This is inventory only. No booking, price, payment, physical readiness
				or access is confirmed.
			</p>
		</section>
	);
}
export function InventoryHold({
	venueId,
	roomId,
	timezone,
}: {
	venueId: string;
	roomId: string;
	timezone: string;
}) {
	const [local, setLocal] = useState("");
	const [duration, setDuration] = useState("");
	const [guests, setGuests] = useState("");
	const [candidate, setCandidate] = useState<HoldCandidate>();
	const [packages, setPackages] = useState<PublicPackage[]>([]);
	const [packageVersion, setPackageVersion] = useState("");
	const [hold, setHold] = useState<Hold>();
	const [error, setError] = useState<unknown>();
	const [busy, setBusy] = useState(false);
	const [elapsed, setElapsed] = useState(0);
	const received = useRef(0);
	// Retain exact command after an unknown outcome; edits cannot silently create another hold.
	const pending = useRef<{ path: string; body: string } | undefined>(undefined);
	useEffect(() => {
		if (!hold) return;
		const id = setInterval(
			() => setElapsed(performance.now() - received.current),
			1000,
		);
		return () => clearInterval(id);
	}, [hold]);
	function accept(
		value: unknown,
		expected?: { packageId?: string; packageVersionId?: string },
	) {
		const h = validateHold(value);
		const operational = operationalQuoteHold(h, venueId);
		if (
			h.roomId !== roomId ||
			("mode" in h && !operational) ||
			(expected?.packageId &&
				(operational?.packageId !== expected.packageId ||
					operational?.packageVersionId !== expected.packageVersionId))
		)
			throw new Error(
				"The server returned a hold outside the requested scope.",
			);
		received.current = performance.now();
		setElapsed(0);
		setHold(h);
	}
	async function inquire(event: FormEvent) {
		event.preventDefault();
		setBusy(true);
		setError(undefined);
		setCandidate(undefined);
		setPackages([]);
		setPackageVersion("");
		try {
			const query = new URLSearchParams({
				sessionStartLocal: local,
				durationMinutes: duration,
				guests,
			});
			const c = await roomRequest<HoldCandidate>(
				`/api/rooms/${roomId}/availability?${query}`,
			);
			if (
				c.evidenceLevel !== "INVENTORY_ONLY" ||
				c.roomId !== roomId ||
				(c.mode !== undefined && c.mode !== "OPERATIONAL") ||
				typeof c.available !== "boolean" ||
				typeof c.configuration !== "string"
			)
				throw new Error("Invalid inventory response");
			if (c.available && c.mode === "OPERATIONAL") {
				const scope = new URLSearchParams({
					roomId,
					sessionStart: c.sessionStart,
					sessionEnd: c.sessionEnd,
				});
				setPackages(
					catalogItems(
						await roomRequest(
							`/api/venues/${encodeURIComponent(venueId)}/packages?${scope}`,
						),
					),
				);
			}
			setCandidate(c);
		} catch (e) {
			if (e instanceof RoomRequestError && [401, 403].includes(e.status))
				setHold(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	async function command(path: string, body: object) {
		if (!pending.current)
			pending.current = { path, body: JSON.stringify(body) };
		setBusy(true);
		setError(undefined);
		try {
			accept(
				await roomRequest(pending.current.path, {
					method: "POST",
					body: pending.current.body,
				}),
				pending.current.path === "/api/holds"
					? JSON.parse(pending.current.body)
					: undefined,
			);
			pending.current = undefined;
			setCandidate(undefined);
		} catch (e) {
			if (
				e instanceof RoomRequestError &&
				[400, 401, 403, 404, 409, 429].includes(e.status)
			)
				pending.current = undefined;
			if (e instanceof RoomRequestError && [401, 403].includes(e.status))
				setHold(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	function createHold() {
		if (!candidate?.available || pending.current) return;
		try {
			const selection = operationalHoldPackage(
				candidate,
				packages,
				packageVersion,
			);
			void command("/api/holds", {
				roomId,
				sessionStartLocal: local,
				durationMinutes: Number(duration),
				guests: Number(guests),
				expectedConfiguration: candidate.configuration,
				idempotencyKey: crypto.randomUUID(),
				...selection,
			});
		} catch (failure) {
			setError(failure);
		}
	}
	async function refresh() {
		if (!hold) return;
		setBusy(true);
		setError(undefined);
		try {
			accept(await roomRequest(`/api/holds/${hold.holdId}`));
		} catch (e) {
			if (e instanceof RoomRequestError && [401, 403].includes(e.status))
				setHold(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<section className="booking-workflow">
			<BookingStepGuide />
			<Card title="Plan your cinema session">
				<p>
					Eligible signed-in customers only. Venue timezone: {timezone}.
					Inventory is separate from booking and physical readiness.
				</p>
				<CustomerAvailabilityCalendar
					key={`${roomId}:${venueId}:${timezone}`}
					roomId={roomId}
					venueId={venueId}
					timezone={timezone}
					disabled={busy || !!pending.current}
					onChoose={(value) => {
						setCandidate(undefined);
						setLocal(value?.local ?? "");
						if (value) {
							setDuration(value.duration);
							setGuests(value.guests);
						}
					}}
				/>
				<div className="booking-planning-grid">
					<section id="booking-date" aria-labelledby="booking-date-title">
						<h3 id="booking-date-title">Choose date & time</h3>
						<form onSubmit={inquire} onChange={() => setCandidate(undefined)}>
							<div className="filter-grid">
								<Field
									id={`inventory-start-${roomId}`}
									label="Customer start in venue local time"
									type="datetime-local"
									required
									value={local}
									disabled={busy || !!pending.current}
									onChange={(e) => setLocal(e.target.value)}
								/>
								<Field
									id={`inventory-duration-${roomId}`}
									label="Customer duration (minutes)"
									type="number"
									min="1"
									required
									value={duration}
									disabled={busy || !!pending.current}
									onChange={(e) => setDuration(e.target.value)}
								/>
								<Field
									id={`inventory-guests-${roomId}`}
									label="Guests"
									type="number"
									min="1"
									required
									value={guests}
									disabled={busy || !!pending.current}
									onChange={(e) => setGuests(e.target.value)}
								/>
							</div>
							<Button type="submit" disabled={busy || !!pending.current}>
								Check inventory (no hold)
							</Button>
						</form>
					</section>
					<section id="booking-hold" aria-labelledby="booking-hold-title">
						<h3 id="booking-hold-title">Availability & reservation hold</h3>
						{!candidate && !hold && !busy && !error && (
							<p>
								Choose your date, duration and guest count, then check the
								interval. An availability inquiry does not reserve it.
							</p>
						)}
						{busy && <StateMessage kind="loading" />}
						{error !== undefined && (
							<StateMessage kind={stateFor(error)}>
								<p>
									{error instanceof Error
										? error.message
										: "Inventory request failed"}
								</p>
							</StateMessage>
						)}
						{pending.current && !busy && (
							<>
								<p>
									Outcome unresolved. Retry the same command; do not create
									another request.
								</p>
								<Button onClick={() => command("", {})}>
									Retry original command
								</Button>
							</>
						)}
						{candidate && (
							<>
								<p role="status">
									{candidate.available
										? "Candidate inventory is currently free; inquiry reserves nothing."
										: "Inventory conflict; choose another interval."}
								</p>
								<p>
									Customer end:{" "}
									{venueTime(candidate.sessionEnd, candidate.timezone)} ·
									cleaning {candidate.bufferMinutes} minutes; blocked until{" "}
									{venueTime(candidate.blockedUntil, candidate.timezone)}.
								</p>
								<p>
									{candidate.mode === "OPERATIONAL"
										? "Operational availability"
										: "Synthetic availability"}
								</p>
								{candidate.available && candidate.mode === "OPERATIONAL" && (
									<SelectField
										id={`inventory-package-${roomId}`}
										label="Published package"
										value={packageVersion}
										disabled={
											busy || !!pending.current || packages.length === 0
										}
										onChange={(event) => setPackageVersion(event.target.value)}
									>
										<option value="">
											{packages.length
												? "Choose a package"
												: "No packages for this interval"}
										</option>
										{packages.map((item) => (
											<option key={item.versionId} value={item.versionId}>
												{item.name}
											</option>
										))}
									</SelectField>
								)}
								{candidate.available && (
									<Button
										disabled={
											busy ||
											!!pending.current ||
											(candidate.mode === "OPERATIONAL" && !packageVersion)
										}
										onClick={createHold}
									>
										Create temporary inventory hold
									</Button>
								)}
							</>
						)}
						{hold && (
							<>
								<HoldStatus hold={hold} elapsedMs={elapsed} />

								<div className="toolbar">
									<Button disabled={busy} onClick={refresh}>
										Refresh hold state
									</Button>
									{hold.state === "ACTIVE" && (
										<Button
											disabled={busy || !!pending.current}
											variant="secondary"
											onClick={() =>
												command(`/api/holds/${hold.holdId}/release`, {
													expectedRevision: hold.revision,
													idempotencyKey: crypto.randomUUID(),
												})
											}
										>
											Release hold
										</Button>
									)}
								</div>
							</>
						)}
					</section>
				</div>
				{hold?.state === "ACTIVE" ? (
					<SyntheticQuotation key={hold.holdId} hold={hold} venueId={venueId} />
				) : (
					<AwaitingBookingQuote />
				)}
			</Card>
		</section>
	);
}

export function InventoryInspection() {
	const [venue, setVenue] = useState("");
	const [page, setPage] = useState(0);
	const [items, setItems] = useState<Record<string, string | number>[]>();
	const [error, setError] = useState<unknown>();
	const [busy, setBusy] = useState(false);
	async function load(next: number) {
		setBusy(true);
		setError(undefined);
		setItems(undefined);
		try {
			const data = await roomRequest<{
				items: Record<string, string | number>[];
			}>(
				`/api/admin/venues/${encodeURIComponent(venue)}/inventory?page=${next}&size=24`,
			);
			if (!Array.isArray(data.items))
				throw new Error("Invalid inventory response");
			setItems(data.items);
			setPage(next);
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<Card title="Scoped inventory inspection">
			<p>
				Requires inventory.read at this venue. Inventory blocks do not certify
				occupancy or physical readiness. Owner identities are not shown.
			</p>
			<form
				onSubmit={(e) => {
					e.preventDefault();
					void load(0);
				}}
			>
				<Field
					id="inventory-venue"
					label="Assigned venue ID"
					required
					value={venue}
					onChange={(e) => {
						setVenue(e.target.value);
						setItems(undefined);
					}}
				/>
				<Button type="submit" disabled={busy}>
					Inspect inventory
				</Button>
			</form>
			{busy && <StateMessage kind="loading" />}
			{error !== undefined && <StateMessage kind={stateFor(error)} />}
			{items &&
				(items.length === 0 ? (
					<StateMessage kind="empty" />
				) : (
					<div className="table-scroll">
						<table>
							<caption>Active inventory blocks (not bookings)</caption>
							<thead>
								<tr>
									<th>Room</th>
									<th>Kind</th>
									<th>Customer start</th>
									<th>Customer end</th>
									<th>Cleaning minutes</th>
									<th>Blocked until</th>
								</tr>
							</thead>
							<tbody>
								{items.map((item) => (
									<tr key={String(item.blockId)}>
										<td>{item.roomId}</td>
										<td>{item.kind}</td>
										<td>{item.sessionStart}</td>
										<td>{item.sessionEnd}</td>
										<td>{item.bufferMinutes}</td>
										<td>{item.blockedUntil}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				))}
			{items && (
				<div className="toolbar">
					<Button disabled={busy || page === 0} onClick={() => load(page - 1)}>
						Previous inventory page
					</Button>
					<span>Page {page + 1}</span>
					<Button
						disabled={busy || items.length < 24}
						onClick={() => load(page + 1)}
					>
						Next inventory page
					</Button>
				</div>
			)}
		</Card>
	);
}

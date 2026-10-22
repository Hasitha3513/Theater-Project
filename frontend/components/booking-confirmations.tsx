"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type ConfirmationContext,
	type ConfirmationReport,
	loadConfirmations,
} from "../lib/booking-confirmations";
import { RoomRequestError } from "../lib/rooms";
import { Badge, Button, Card, Field } from "./primitives";
export function BookingConfirmationsSection() {
	const [context, setContext] = useState<ConfirmationContext>({
		venue: "",
		room: "",
		from: "",
		to: "",
	});
	const [report, setReport] = useState<ConfirmationReport>();
	const [busy, setBusy] = useState(false);
	const [message, setMessage] = useState(
		"Choose an authorized venue and date range, then load statistics explicitly.",
	);
	const epoch = useRef(0),
		controller = useRef<AbortController | undefined>(undefined);
	const clear = useCallback(() => {
		epoch.current++;
		controller.current?.abort();
		setReport(undefined);
		setBusy(false);
	}, []);
	useEffect(() => {
		const hide = () => {
			clear();
			setMessage(
				"Protected statistics cleared. Reload to check current access.",
			);
		};
		const events = [
			"blur",
			"focus",
			"pagehide",
			"pageshow",
			"cinenest-session-change",
		];
		for (const event of events) window.addEventListener(event, hide);
		document.addEventListener("visibilitychange", hide);
		return () => {
			clear();
			for (const event of events) window.removeEventListener(event, hide);
			document.removeEventListener("visibilitychange", hide);
		};
	}, [clear]);
	function change(key: keyof ConfirmationContext, value: string) {
		clear();
		setContext((previous) => ({ ...previous, [key]: value }));
		setMessage("Scope changed. Load a fresh report explicitly.");
	}
	async function load() {
		if (busy) return;
		clear();
		const generation = epoch.current;
		const abort = new AbortController();
		controller.current = abort;
		setBusy(true);
		setMessage("Loading protected statistics…");
		try {
			const value = await loadConfirmations(context, abort.signal);
			if (generation !== epoch.current) return;
			setReport(value);
			setMessage(
				value.total === "0"
					? "No synthetic confirmations in this complete reporting interval."
					: "Statistics loaded from the authoritative booking snapshot.",
			);
		} catch (error) {
			if (generation !== epoch.current) return;
			setReport(undefined);
			const status = error instanceof RoomRequestError ? error.status : 0;
			setMessage(
				status === 401
					? "Your session expired. Sign in again, then retry."
					: status === 403
						? "Access denied. Independent reporting permission for this venue is required."
						: status === 503
							? "Statistics unavailable. No values are shown; retry explicitly."
							: "Unable to load statistics. Check the scope and dates, then retry explicitly.",
			);
		} finally {
			if (generation === epoch.current) setBusy(false);
		}
	}
	return (
		<section aria-label="Booking Statistics">
			<Card title="Synthetic Booking Confirmations">
				<Badge tone="info">Synthetic statistics only</Badge>
				<p className="notice">
					Synthetic confirmations only. Not real commercial booking or revenue
					statistics.
				</p>
				<p>
					Confirmation-time cohort. Cancellation statistics: NOT AVAILABLE.
					Current room attribution may reflect later booking revisions.
				</p>
				<form
					className="confirmation-filters"
					onSubmit={(event) => {
						event.preventDefault();
						void load();
					}}
				>
					<Field
						id="confirmations-venue"
						label="Statistics venue reference"
						value={context.venue}
						onChange={(e) => change("venue", e.target.value)}
						required
					/>
					<Field
						id="confirmations-room"
						label="Statistics room reference (optional)"
						value={context.room}
						onChange={(e) => change("room", e.target.value)}
					/>
					<Field
						id="confirmations-from"
						label="From date (inclusive)"
						type="date"
						value={context.from}
						onChange={(e) => change("from", e.target.value)}
						required
					/>
					<Field
						id="confirmations-to"
						label="To date (exclusive)"
						type="date"
						value={context.to}
						onChange={(e) => change("to", e.target.value)}
						required
					/>
					<Button type="submit" aria-disabled={busy}>
						{busy ? "Loading statistics…" : "Load statistics"}
					</Button>
				</form>
				<p role="status" aria-live="polite">
					{message}
				</p>
				{report && (
					<section
						className="confirmation-report"
						aria-label="Confirmation report"
						aria-busy={busy}
					>
						<dl className="confirmation-summary">
							<div>
								<dt>Total synthetic confirmations</dt>
								<dd className="confirmation-total">{report.total}</dd>
							</div>
							<div>
								<dt>Venue</dt>
								<dd>{report.venueId}</dd>
							</div>
							<div>
								<dt>Venue timezone</dt>
								<dd>{report.timezone}</dd>
							</div>
							<div>
								<dt>Reporting interval</dt>
								<dd>
									{report.from} to {report.to} (exclusive)
								</dd>
							</div>
							<div>
								<dt>Observed at</dt>
								<dd>{report.observedAt}</dd>
							</div>
						</dl>
						<h3>Daily synthetic confirmations</h3>
						<section className="table-wrap" aria-label="Daily confirmations">
							<table className="confirmation-table">
								<thead>
									<tr>
										<th scope="col">Venue-local date</th>
										<th scope="col">Synthetic confirmations</th>
									</tr>
								</thead>
								<tbody>
									{report.daily.map((day) => (
										<tr key={day.date}>
											<th scope="row">{day.date}</th>
											<td>{day.count}</td>
										</tr>
									))}
								</tbody>
							</table>
						</section>
						<h3>Current room distribution</h3>
						{report.rooms.length ? (
							<section
								className="table-wrap"
								aria-label="Current room confirmations"
							>
								<table className="confirmation-table">
									<thead>
										<tr>
											<th scope="col">Room reference</th>
											<th scope="col">Synthetic confirmations</th>
										</tr>
									</thead>
									<tbody>
										{report.rooms.map((room) => (
											<tr key={room.roomId}>
												<th scope="row">{room.roomId}</th>
												<td>{room.count}</td>
											</tr>
										))}
									</tbody>
								</table>
							</section>
						) : (
							<p>No room counts in this empty cohort.</p>
						)}
						<p>
							Complete current snapshot · {report.metricVersion}. Advisory
							reporting; no historical reconstruction or physical readiness
							inference.
						</p>
					</section>
				)}
			</Card>
		</section>
	);
}

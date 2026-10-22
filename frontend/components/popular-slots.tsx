"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type PopularContext,
	type PopularReport,
	loadPopular,
	bandLabel,
	barPercent,
} from "../lib/popular-slots";
import { RoomRequestError } from "../lib/rooms";
import { Badge, Button, Card, Field } from "./primitives";
export function PopularSlotsSection() {
	const [context, setContext] = useState<PopularContext>({
		venue: "",
		room: "",
		from: "",
		to: "",
	});
	const [report, setReport] = useState<PopularReport>();
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
			"popstate",
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
	function change(key: keyof PopularContext, value: string) {
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
			const value = await loadPopular(context, abort.signal);
			if (generation !== epoch.current) return;
			setReport(value);
			setMessage(
				value.total === "0"
					? "No synthetic reserved session starts in this complete reporting interval."
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
	const maximum =
		report?.buckets.reduce(
			(max, b) => (BigInt(b.count) > BigInt(max) ? b.count : max),
			"0",
		) ?? "0";
	return (
		<section aria-label="Popular Reserved Session Start Times">
			<Card title="Popular Reserved Session Start Times">
				<Badge tone="info">Synthetic reporting only</Badge>
				<p className="notice">
					Synthetic reserved session starts only. Not actual attendance,
					occupancy, revenue or physical readiness.
				</p>
				<p>
					Current purchased revisions and current venue timezone. Results may
					change after confirmed amendments or timezone changes. Not an
					immutable historical demand snapshot.
				</p>
				<form
					className="confirmation-filters"
					onSubmit={(e) => {
						e.preventDefault();
						void load();
					}}
				>
					<Field
						id="popular-venue"
						label="Popular slots venue reference"
						value={context.venue}
						onChange={(e) => change("venue", e.target.value)}
						required
					/>
					<Field
						id="popular-room"
						label="Popular slots room reference (optional)"
						value={context.room}
						onChange={(e) => change("room", e.target.value)}
					/>
					<Field
						id="popular-from"
						label="Popular slots from date (inclusive)"
						type="date"
						value={context.from}
						onChange={(e) => change("from", e.target.value)}
						required
					/>
					<Field
						id="popular-to"
						label="Popular slots to date (exclusive)"
						type="date"
						value={context.to}
						onChange={(e) => change("to", e.target.value)}
						required
					/>
					<div className="toolbar">
						<Button type="submit" disabled={busy}>
							{busy ? "Loading report…" : "Load Report"}
						</Button>
						<Button
							type="button"
							variant="secondary"
							disabled={busy}
							onClick={() => void load()}
						>
							Retry
						</Button>
						<Button
							type="button"
							variant="secondary"
							onClick={() => {
								clear();
								setMessage(
									"Report cleared. Load explicitly to check current access.",
								);
							}}
						>
							Clear
						</Button>
					</div>
				</form>
				<p role="status" aria-live="polite">
					{message}
				</p>
				{report && (
					<section
						className="confirmation-report"
						aria-label="Popular slots report"
					>
						<dl className="confirmation-summary">
							<div>
								<dt>Total synthetic reservations</dt>
								<dd className="confirmation-total">{report.total}</dd>
							</div>
							<div>
								<dt>Venue</dt>
								<dd>{report.venueId}</dd>
							</div>
							<div>
								<dt>Room filter</dt>
								<dd>{report.roomId ?? "All purchased rooms in this venue"}</dd>
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
						<figure aria-label="Hourly reserved session starts chart">
							<figcaption>
								Local starting hours · synthetic reservation counts · bars
								scaled from zero
							</figcaption>
							<ol className="popular-bars">
								{report.buckets.map((b) => (
									<li
										className="popular-row"
										key={b.bandId}
										aria-label={`${bandLabel(b.startHour, b.endHour)}: ${b.count} synthetic reservations`}
									>
										<span className="popular-hour">
											{bandLabel(b.startHour, b.endHour)}
										</span>
										<span className="popular-track" aria-hidden="true">
											<span
												className="popular-fill"
												style={{ width: `${barPercent(b.count, maximum)}%` }}
											/>
										</span>
										<strong>{b.count}</strong>
									</li>
								))}
							</ol>
						</figure>
						<section
							className="table-wrap"
							aria-label="Hourly reserved session starts data"
						>
							<table className="confirmation-table">
								<caption>Equivalent hourly counts · {report.timezone}</caption>
								<thead>
									<tr>
										<th scope="col">Local-hour band</th>
										<th scope="col">Synthetic reservations</th>
									</tr>
								</thead>
								<tbody>
									{report.buckets.map((b) => (
										<tr key={b.bandId}>
											<th scope="row">{bandLabel(b.startHour, b.endHour)}</th>
											<td>{b.count}</td>
										</tr>
									))}
								</tbody>
							</table>
						</section>
						<p>
							Complete current-revision report · {report.metricVersion} ·{" "}
							{report.bandDefinitionVersion}. Fixed hourly bands only;
							configured business bands remain outstanding.
						</p>
					</section>
				)}
			</Card>
		</section>
	);
}

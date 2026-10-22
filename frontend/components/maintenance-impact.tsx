"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	impactCategories,
	impactPath,
	loadImpact,
	type Impact,
	type ImpactContext,
} from "../lib/maintenance-impact";
import { RoomRequestError } from "../lib/rooms";
import { Button, Card, Field } from "./primitives";
export function MaintenanceImpactPreview() {
	const [context, setContext] = useState<ImpactContext>({
		venue: "",
		room: "",
		startLocal: "",
		endLocal: "",
	});
	const [result, setResult] = useState<Impact>();
	const [busy, setBusy] = useState(false);
	const [failed, setFailed] = useState(false);
	const [message, setMessage] = useState(
		"No preview loaded. Enter exact references and venue-local times, then load explicitly.",
	);
	const generation = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		status = useRef<HTMLParagraphElement>(null);
	const clear = useCallback(() => {
		generation.current++;
		controller.current?.abort();
		setResult(undefined);
		setBusy(false);
	}, []);
	useEffect(() => {
		const hide = () => {
			clear();
			setFailed(false);
			setMessage(
				"Protected preview cleared. Load explicitly to check current access and inventory.",
			);
		};
		const events = [
			"blur",
			"focus",
			"pagehide",
			"pageshow",
			"popstate",
			"cinenest-session-change",
		];
		for (const e of events) window.addEventListener(e, hide);
		document.addEventListener("visibilitychange", hide);
		return () => {
			generation.current++;
			controller.current?.abort();
			for (const e of events) window.removeEventListener(e, hide);
			document.removeEventListener("visibilitychange", hide);
		};
	}, [clear]);
	function change(key: keyof ImpactContext, value: string) {
		clear();
		setFailed(false);
		setContext((c) => ({ ...c, [key]: value }));
		setMessage("Context changed. Load a new preview explicitly.");
	}
	async function load() {
		clear();
		const token = generation.current,
			c = { ...context };
		try {
			impactPath(c);
		} catch {
			setFailed(true);
			setMessage(
				"Check the venue and room references and minute-precision local times. Use an increasing interval ending no later than the start day's next midnight.",
			);
			status.current?.focus();
			return;
		}
		const pending = new AbortController();
		controller.current = pending;
		setBusy(true);
		setFailed(false);
		setMessage("Loading inventory observation. No room action is performed…");
		try {
			const data = await loadImpact(
				c,
				AbortSignal.any([pending.signal, AbortSignal.timeout(10000)]),
			);
			if (token !== generation.current) return;
			setResult(data);
			setMessage(
				data.complete
					? "Complete inventory observation loaded. This preview reserves nothing."
					: "Inventory impact preview is incomplete. Conflict counts are unavailable.",
			);
		} catch (error) {
			if (token !== generation.current) return;
			setResult(undefined);
			setFailed(true);
			const code = error instanceof RoomRequestError ? error.status : 502;
			setMessage(
				code === 400
					? "Invalid venue-local interval or request. Check the details; missing or ambiguous local times are not supported."
					: code === 401
						? "Session expired or unavailable. Sign in, then load again."
						: code === 403
							? "Access denied. Current membership and inventory.read for this exact venue are required."
							: code === 404
								? "Room unavailable in the selected venue. Check the references."
								: code === 409
									? "Room configuration or session context is unavailable or changed. Check the context and reload explicitly."
									: "Preview unavailable or response invalid. No conflict counts are shown. Retry explicitly.",
			);
			queueMicrotask(() => {
				if (token === generation.current) status.current?.focus();
			});
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}
	return (
		<Card title="Maintenance Impact Preview" className="maintenance-impact">
			<p className="fixture-notice">
				SYNTHETIC INVENTORY OBSERVATION · INVENTORY ONLY
			</p>
			<p>
				Requires independent inventory.read at the selected venue. Occupancy
				UNKNOWN; readiness UNVERIFIED. This observation does not authorize
				closure, reopening or physical admission.
			</p>
			<div className="filter-grid">
				<Field
					id="impact-venue"
					label="Venue reference"
					value={context.venue}
					onChange={(e) => change("venue", e.target.value)}
				/>
				<Field
					id="impact-room"
					label="Room reference"
					value={context.room}
					onChange={(e) => change("room", e.target.value)}
				/>
				<Field
					id="impact-start"
					label="Start date/time (venue local)"
					type="datetime-local"
					step={60}
					value={context.startLocal}
					onChange={(e) => change("startLocal", e.target.value)}
				/>
				<Field
					id="impact-end"
					label="End date/time (venue local)"
					type="datetime-local"
					step={60}
					value={context.endLocal}
					onChange={(e) => change("endLocal", e.target.value)}
				/>
			</div>
			<p>
				Times are interpreted in the room’s stored venue timezone, never your
				browser timezone. The server validates the interval and returns the
				authoritative timezone.
			</p>
			<div className="impact-actions">
				<Button busy={busy} onClick={() => load()}>
					Load Preview
				</Button>
				<Button
					variant="secondary"
					disabled={busy || !failed}
					onClick={() => load()}
				>
					Retry
				</Button>
				<Button
					variant="secondary"
					onClick={() => {
						clear();
						setContext({ venue: "", room: "", startLocal: "", endLocal: "" });
						setFailed(false);
						setMessage("Preview and form cleared.");
					}}
				>
					Clear
				</Button>
			</div>
			<p
				ref={status}
				className="workspace-status"
				role="status"
				aria-live="polite"
				aria-atomic="true"
				aria-busy={busy || undefined}
				tabIndex={-1}
			>
				{message}
			</p>
			{result && (
				<div className="impact-result">
					<h3>Observed interval</h3>
					<dl className="impact-context">
						<dt>Venue</dt>
						<dd>{result.venueId}</dd>
						<dt>Room</dt>
						<dd>{result.roomId}</dd>
						<dt>Venue timezone</dt>
						<dd>{result.timezone}</dd>
						<dt>Requested local interval</dt>
						<dd>
							{context.startLocal} — {context.endLocal}
						</dd>
						<dt>Server interval (UTC)</dt>
						<dd>
							{result.requestedStart} — {result.requestedEnd}
						</dd>
						<dt>Observed at (UTC)</dt>
						<dd>{result.observedAt}</dd>
						<dt>Completeness</dt>
						<dd>
							{result.complete
								? "Complete within the 1,000-record limit"
								: "Incomplete — RECORD_LIMIT"}
						</dd>
					</dl>
					{!result.complete ? (
						<p className="fixture-notice" role="status">
							Inventory impact preview is incomplete. Conflict counts are
							unavailable.
						</p>
					) : (
						result.counts && (
							<>
								<dl className="impact-counts">
									{(
										Object.keys(
											impactCategories,
										) as (keyof typeof impactCategories)[]
									).map((k) => (
										<div key={k}>
											<dt>{impactCategories[k]}</dt>
											<dd>{result.counts?.[k]}</dd>
										</div>
									))}
								</dl>
								{Object.values(result.counts).every((n) => n === 0) && (
									<p>
										No overlapping blocking inventory was observed for this
										interval.
									</p>
								)}
							</>
						)
					)}
					<p>
						Expired-pending and invalidatable-pending holds remain persisted
						blocking observations. Counts describe inventory records, not
						people, physical conditions or maintenance approval. Results can
						change immediately after observation.
					</p>
				</div>
			)}
		</Card>
	);
}

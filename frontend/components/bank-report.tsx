"use client";
import { useEffect, useRef, useState } from "react";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, PageHeading } from "./primitives";
export const REPORT_LABEL = "SYNTHETIC FINANCIAL REPORT — NO REAL MONEY";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
type Amounts = {
	c: string;
	a: string;
	t: string;
	r: string;
	u: string;
	f: string;
	available: string | null;
	unavailable: string;
};
type Row = {
	creditId: string;
	creditedAt: string;
	verifiedAt: string;
	amounts: Amounts;
	eligibility: string;
	exceptions: { kind: string; state: string }[];
	creditDrilldown: boolean;
};
export type BankReport = {
	synthetic: true;
	realFinancialReporting: false;
	venueId: string;
	currency: string;
	timezone: string;
	page: number;
	size: number;
	snapshotPolicy: "CURRENT_REQUEST_ONLY";
	snapshot: {
		snapshotAt: string;
		metricVersion: string;
		total: number;
		summary: Amounts;
		rows: Row[];
	};
};
function exact(a: Amounts) {
	for (const k of ["c", "a", "t", "r", "u", "f", "unavailable"] as const)
		if (typeof a?.[k] !== "string" || !/^(0|[1-9]\d*)$/.test(a[k]))
			throw Error("Invalid exact minor units");
	if (
		a.available !== null &&
		(typeof a.available !== "string" || !/^(0|[1-9]\d*)$/.test(a.available))
	)
		throw Error("Invalid availability");
}
export function bankReport(
	value: unknown,
	venue: string,
	currency: string,
): BankReport {
	const r = value as BankReport;
	if (
		r?.synthetic !== true ||
		r.realFinancialReporting !== false ||
		r.venueId !== venue ||
		r.currency !== currency ||
		r.snapshotPolicy !== "CURRENT_REQUEST_ONLY" ||
		!r.snapshot ||
		!Number.isFinite(Date.parse(r.snapshot.snapshotAt)) ||
		r.snapshot.metricVersion !== "SYNTHETIC-BANK-BALANCES-V1" ||
		!Number.isSafeInteger(r.snapshot.total) ||
		r.snapshot.total < 0 ||
		!Array.isArray(r.snapshot.rows)
	)
		throw Error("Invalid synthetic report");
	exact(r.snapshot.summary);
	for (const row of r.snapshot.rows) {
		exact(row.amounts);
		if (
			!uuid.test(row.creditId) ||
			!["ELIGIBLE", "DISPUTED", "SOURCE_REVERSED"].includes(row.eligibility) ||
			!Array.isArray(row.exceptions) ||
			typeof row.creditDrilldown !== "boolean"
		)
			throw Error("Invalid redacted credit row");
	}
	return r;
}
export function bankReportPath(venue: string) {
	if (!uuid.test(venue)) throw Error("Invalid venue");
	return `/api/admin/venues/${venue}/reports/synthetic-bank-balances`;
}
export function BankReportWorkspace() {
	const [venue, setVenue] = useState(""),
		[currency, setCurrency] = useState("LKR"),
		[from, setFrom] = useState(""),
		[to, setTo] = useState(""),
		[report, setReport] = useState<BankReport | null>(null),
		[busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Enter an authorized venue. Reporting is synthetic only.",
		),
		[priorSnapshot, setPriorSnapshot] = useState("");
	const generation = useRef(0),
		status = useRef<HTMLParagraphElement>(null);
	useEffect(
		() => () => {
			generation.current++;
		},
		[],
	);
	function change(set: (v: string) => void, v: string) {
		generation.current++;
		set(v);
		setReport(null);
		setBusy(false);
		setPriorSnapshot("");
		setMessage("Filters changed. Load a fresh current snapshot.");
	}
	async function load(page = 0) {
		const g = ++generation.current;
		const previous = report?.snapshot.snapshotAt;
		setBusy(true);
		setReport(null);
		try {
			const params = new URLSearchParams({
				currency,
				page: String(page),
				size: "25",
			});
			if (from || to) {
				params.set("from", from);
				params.set("to", to);
			}
			const result = bankReport(
				await roomRequest(`${bankReportPath(venue)}?${params}`),
				venue,
				currency,
			);
			if (g !== generation.current) return;
			setReport(result);
			setPriorSnapshot(previous ?? "");
			setMessage(
				"Current-request snapshot loaded. Later pages and refreshes may use a different snapshot.",
			);
		} catch (e) {
			if (g !== generation.current) return;
			setReport(null);
			setMessage(
				e instanceof RoomRequestError
					? e.status === 403 || e.status === 401
						? "Denied or session expired. No financial data shown."
						: e.status === 422
							? "Financial invariant error. No totals shown; reconcile source evidence."
							: e.status === 400
								? "Invalid filters. Check currency and venue-local date boundaries."
								: "Synthetic report unavailable. No missing values replaced with zero."
					: "Invalid or unavailable report. No totals shown.",
			);
			queueMicrotask(() => status.current?.focus());
		} finally {
			if (g === generation.current) setBusy(false);
		}
	}
	const labels = {
		c: "C · Verified synthetic credits",
		a: "A · Allocated",
		t: "T · Confirmed synthetic returns",
		r: "R · Active refund commitments",
		u: "U · Unallocated residual",
		f: "F · Uncommitted residual",
		available: "Credit-eligible free balance",
		unavailable: "Restricted free balance",
	};
	return (
		<div className="staff-workspace bank-report-workspace">
			<PageHeading
				eyebrow="Synthetic staff workspace"
				title="Bank reconciliation"
			>
				Inspect authorized synthetic bank-credit balances and their current
				source records. No real banking or financial action is enabled.
			</PageHeading>
			<p className="fixture-notice">{REPORT_LABEL}</p>
			<p>
				Receipt review is not credit. Allocation is not new incoming money.
				UNKNOWN outgoing results remain reserved.
			</p>
			<p
				className="workspace-status"
				ref={status}
				tabIndex={-1}
				role="status"
				aria-atomic="true"
				aria-busy={busy || undefined}
			>
				{busy ? "Loading current snapshot…" : message}
			</p>
			<Card title="Report scope" className="report-filters">
				<Field
					id="report-venue"
					label="Venue ID"
					hint="Enter a venue covered by your current server-authorized grant."
					value={venue}
					onChange={(e) => change(setVenue, e.target.value)}
				/>
				<Field
					id="report-currency"
					label="Currency"
					value={currency}
					maxLength={3}
					onChange={(e) => change(setCurrency, e.target.value)}
				/>
				<Field
					id="report-from"
					label="Credited from (venue-local date, inclusive)"
					type="date"
					value={from}
					onChange={(e) => change(setFrom, e.target.value)}
				/>
				<Field
					id="report-to"
					label="Credited to (venue-local date, exclusive)"
					type="date"
					value={to}
					onChange={(e) => change(setTo, e.target.value)}
				/>
				<Button
					busy={busy}
					disabled={!uuid.test(venue) || !/^[A-Z]{3}$/.test(currency)}
					onClick={() => load()}
				>
					Load current report
				</Button>
			</Card>
			{report && (
				<>
					<Card title="Complete-population summary" className="report-summary">
						<p>
							Venue {report.venueId}; currency {report.currency}; timezone{" "}
							{report.timezone}
						</p>
						<p>
							Snapshot {report.snapshot.snapshotAt}; definition{" "}
							{report.snapshot.metricVersion}; {report.snapshot.total} credits
						</p>
						{priorSnapshot && priorSnapshot !== report.snapshot.snapshotAt && (
							<p role="status">
								Snapshot changed from {priorSnapshot}. Pages are independent
								current snapshots.
							</p>
						)}
						<dl className="report-amounts">
							{Object.entries(labels).map(([key, label]) => (
								<div key={key}>
									<dt>{label}</dt>
									<dd>
										{report.snapshot.summary[key as keyof Amounts] ??
											"Unavailable"}{" "}
										{report.currency} minor units
									</dd>
								</div>
							))}
						</dl>
						<p>
							Source eligibility is not permission to allocate. Existing
							financial commands enforce their own rules. Totals cover the
							complete filtered population, not this page.
						</p>
					</Card>
					<Card title="Redacted credit details" className="report-details">
						{!report.snapshot.rows.length && (
							<p>No synthetic credits in this scope.</p>
						)}
						<ul>
							{report.snapshot.rows.map((row) => (
								<li key={row.creditId}>
									<h3>Credit {row.creditId}</h3>
									<p>
										{row.eligibility}; credited {row.creditedAt}; verified{" "}
										{row.verifiedAt}
									</p>
									<dl className="report-amounts">
										{Object.entries(labels).map(([key, label]) => (
											<div key={key}>
												<dt>{label}</dt>
												<dd>
													{row.amounts[key as keyof Amounts] ?? "Unavailable"}{" "}
													{report.currency} minor units
												</dd>
											</div>
										))}
									</dl>
									{row.eligibility !== "ELIGIBLE" && (
										<p>
											Unavailable for allocation: {row.eligibility}. Source
											correction does not create an extra money movement.
										</p>
									)}
									<ul>
										{row.exceptions.map((e) => (
											<li key={`${e.kind}-${e.state}`}>
												{e.kind}: {e.state}
											</li>
										))}
									</ul>
									{row.creditDrilldown && (
										<a
											href={`/api/admin/venues/${report.venueId}/bank/credits/${row.creditId}`}
										>
											Open protected credit source
										</a>
									)}
								</li>
							))}
						</ul>
						<Button
							disabled={busy || report.page === 0}
							onClick={() => load(report.page - 1)}
						>
							Previous page
						</Button>
						<Button
							disabled={
								busy || (report.page + 1) * report.size >= report.snapshot.total
							}
							onClick={() => load(report.page + 1)}
						>
							Next page
						</Button>
						<p>No export or financial mutation is provided.</p>
					</Card>
				</>
			)}
		</div>
	);
}

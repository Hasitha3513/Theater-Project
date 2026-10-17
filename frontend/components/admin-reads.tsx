"use client";
import { useEffect, useRef, useState } from "react";
import { Button, Card, Field, SelectField } from "./primitives";
type Row = Record<string, string | number | boolean | null>;
export function safeAdminRows(value: unknown): Row[] {
	if (!Array.isArray(value)) throw new Error();
	const fields = new Set([
		"accountId",
		"synthetic",
		"eligibility",
		"revision",
		"venueId",
		"eventId",
		"source",
		"occurredAt",
		"action",
		"outcome",
		"reasonCode",
		"correlationId",
		"actorId",
		"resourceId",
	]);
	return value.map((row) => {
		if (!row || typeof row !== "object" || Array.isArray(row))
			throw new Error();
		return Object.fromEntries(
			Object.entries(row).filter(
				([key, v]) =>
					fields.has(key) && ["string", "number", "boolean"].includes(typeof v),
			),
		) as Row;
	});
}
export function AdminReads() {
	const [venue, setVenue] = useState("");
	const [kind, setKind] = useState("accounts");
	const [source, setSource] = useState("IDENTITY");
	const [eligibility, setEligibility] = useState("");
	const [account, setAccount] = useState("");
	const [action, setAction] = useState("");
	const [outcome, setOutcome] = useState("");
	const [from, setFrom] = useState("");
	const [to, setTo] = useState("");
	const [rows, setRows] = useState<Row[]>([]);
	const [detail, setDetail] = useState<Row[]>([]);
	const [cursor, setCursor] = useState<string | null>(null);
	const [status, setStatus] = useState(
		"Choose a venue and filters, then load synthetic records.",
	);
	const [busy, setBusy] = useState(false);
	const epoch = useRef(0);
	function clear() {
		epoch.current++;
		setRows([]);
		setDetail([]);
		setCursor(null);
		setBusy(false);
		setStatus("Filters changed. Load current records.");
	}
	useEffect(() => {
		const discard = () => {
			epoch.current++;
			setRows([]);
			setDetail([]);
			setCursor(null);
			setBusy(false);
			setStatus(
				"Restricted results cleared. Reload to check current session and permissions.",
			);
		};
		window.addEventListener("blur", discard);
		return () => {
			window.removeEventListener("blur", discard);
			epoch.current++;
		};
	}, []);
	async function load(next = false, id?: string) {
		const run = ++epoch.current;
		setRows([]);
		setDetail([]);
		setCursor(null);
		setBusy(true);
		setStatus("Loading restricted synthetic records…");
		try {
			if (!/^[0-9a-f-]{36}$/.test(venue)) throw new Error();
			const q = new URLSearchParams({ limit: "20" });
			if (kind === "audit") {
				q.set("source", source);
				q.set("from", new Date(from).toISOString());
				q.set("to", new Date(to).toISOString());
				if (action) q.set("action", action);
				if (outcome) q.set("outcome", outcome);
			} else {
				if (eligibility) q.set("eligibility", eligibility);
				if (account && !id) q.set("accountId", account);
			}
			if (next && cursor) q.set("cursor", cursor);
			const response = await fetch(
				`/api/admin/venues/${venue}/${kind}${id ? `/${id}` : ""}?${q}`,
				{ cache: "no-store" },
			);
			if (run !== epoch.current) return;
			if (!response.ok) {
				setStatus(
					[401, 403, 404].includes(response.status)
						? "Access denied or resource unavailable. Restricted results cleared."
						: "Unable to load. Check filters and retry.",
				);
				return;
			}
			const data = await response.json();
			if (run !== epoch.current) return;
			const safe = safeAdminRows(id ? [data] : data.items);
			if (id) setDetail(safe);
			else setRows(safe);
			setCursor(
				!id && typeof data.nextCursor === "string" ? data.nextCursor : null,
			);
			setStatus(
				safe.length
					? "Synthetic records loaded. Read-only; no production data."
					: "No visible records match these filters.",
			);
		} catch {
			if (run === epoch.current)
				setStatus("Unable to load. Check venue, UTC time filters and retry.");
		} finally {
			if (run === epoch.current) setBusy(false);
		}
	}
	return (
		<Card title="Scoped accounts and audit">
			<p>
				SYNTHETIC DATA ONLY — no account editing, evidence, financial records or
				exports.
			</p>
			<div className="controls">
				<Field
					id="admin-venue-id"
					label="Venue ID"
					value={venue}
					onChange={(e) => {
						clear();
						setVenue(e.target.value);
					}}
				/>
				<SelectField
					id="admin-view"
					label="View"
					value={kind}
					onChange={(e) => {
						clear();
						setKind(e.target.value);
						setAction("");
					}}
				>
					<option value="accounts">Account status</option>
					<option value="audit">Audit events</option>
				</SelectField>
				{kind === "accounts" ? (
					<>
						<Field
							id="admin-exact-account-id"
							label="Exact account ID"
							value={account}
							onChange={(e) => {
								clear();
								setAccount(e.target.value);
							}}
						/>
						<SelectField
							id="admin-eligibility"
							label="Eligibility"
							value={eligibility}
							onChange={(e) => {
								clear();
								setEligibility(e.target.value);
							}}
						>
							<option value="">All visible</option>
							{["ELIGIBLE", "SUSPENDED", "INELIGIBLE"].map((v) => (
								<option key={v}>{v}</option>
							))}
						</SelectField>
					</>
				) : (
					<>
						<SelectField
							id="admin-audit-source"
							label="Audit source"
							value={source}
							onChange={(e) => {
								clear();
								setSource(e.target.value);
								setAction("");
							}}
						>
							<option>IDENTITY</option>
							<option>VERIFICATION</option>
						</SelectField>
						<Field
							id="admin-from-utc"
							label="From UTC"
							placeholder="2026-10-17T00:00:00Z"
							value={from}
							onChange={(e) => {
								clear();
								setFrom(e.target.value);
							}}
						/>
						<Field
							id="admin-to-utc-exclusive"
							label="To UTC (exclusive)"
							placeholder="2026-10-18T00:00:00Z"
							value={to}
							onChange={(e) => {
								clear();
								setTo(e.target.value);
							}}
						/>
						<SelectField
							id="admin-action"
							label="Action"
							value={action}
							onChange={(e) => {
								clear();
								setAction(e.target.value);
							}}
						>
							<option value="">All allowed</option>
							{(source === "IDENTITY"
								? [
										"ACCOUNTS_LIST",
										"ACCOUNT_DETAIL",
										"AUDIT_LIST",
										"AUDIT_DETAIL",
									]
								: ["VERIFICATION_EVENT"]
							).map((v) => (
								<option key={v}>{v}</option>
							))}
						</SelectField>
						<SelectField
							id="admin-outcome"
							label="Outcome"
							value={outcome}
							onChange={(e) => {
								clear();
								setOutcome(e.target.value);
							}}
						>
							<option value="">All allowed</option>
							<option>ALLOW</option>
							<option>DENY</option>
						</SelectField>
					</>
				)}
			</div>
			<div className="controls">
				<Button busy={busy} onClick={() => load()}>
					Load records
				</Button>
				<Button disabled={!cursor || busy} onClick={() => load(true)}>
					Next page
				</Button>
			</div>
			<p role="status">{status}</p>
			<div className="table-scroll">
				<table>
					<caption>Restricted {kind}</caption>
					<thead>
						<tr>
							<th scope="col">Resource</th>
							<th scope="col">State / action</th>
							<th scope="col">Details</th>
						</tr>
					</thead>
					<tbody>
						{rows.map((row) => (
							<tr key={String(row.accountId ?? row.eventId)}>
								<td>{String(row.accountId ?? row.eventId)}</td>
								<td>{String(row.eligibility ?? row.action)}</td>
								<td>
									<Button
										onClick={() =>
											load(false, String(row.accountId ?? row.eventId))
										}
									>
										View detail
									</Button>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
			{detail.length > 0 && (
				<section aria-label="Restricted detail">
					<h2>Restricted detail</h2>
					<dl className="account-summary">
						{Object.entries(detail[0]).map(([key, value]) => (
							<div key={key}>
								<dt>{key}</dt>
								<dd>{String(value)}</dd>
							</div>
						))}
					</dl>
				</section>
			)}
		</Card>
	);
}

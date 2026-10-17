"use client";
import { useRef, useState } from "react";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type Refund = {
	id: string;
	requestId: string;
	creditId: string;
	venueId: string;
	revision: number;
	state: string;
	amountMinor: string;
	approvedMinor: string;
	currency: string;
	availableMinor: string;
	destinationVerified: boolean;
	destinationReference?: string;
	resolution: string;
	synthetic: true;
	realRepayment: false;
	decisions: { action: string; at: string }[];
	attempts: {
		id: string;
		sequence: number;
		evidence: { fixture: string; phase: string; at: string }[];
	}[];
	audit?: { action: string; at: string }[];
};
export function refundRows(value: unknown): Refund[] {
	const rows = Array.isArray(value) ? value : [value];
	for (const v of rows) {
		if (
			v?.synthetic !== true ||
			v.realRepayment !== false ||
			!uuid.test(v.id) ||
			!Number.isSafeInteger(v.revision) ||
			!/^\d+$/.test(v.amountMinor) ||
			!Array.isArray(v.attempts) ||
			!Array.isArray(v.decisions)
		)
			throw new Error("Invalid synthetic refund response");
	}
	return rows;
}
export function refundCommand(
	op: string,
	venue: string,
	target: string,
	revision: string,
	key: string,
	reason: string,
	fields: Record<string, unknown> = {},
) {
	if (
		!uuid.test(target) ||
		!uuid.test(key) ||
		!/^\d+$/.test(revision) ||
		!Number.isSafeInteger(Number(revision)) ||
		!reason.trim()
	)
		throw new Error(
			"Current identifiers, revision and bounded reason required",
		);
	const body = {
		...fields,
		expectedRevision: Number(revision),
		commandKey: key,
		reason,
	};
	if (op === "REQUEST") {
		if (
			!uuid.test(String(fields.creditId)) ||
			typeof fields.amountMinor !== "string" ||
			!/^([1-9][0-9]*)$/.test(fields.amountMinor) ||
			!/^\d{1,19}$/.test(fields.amountMinor) ||
			BigInt(fields.amountMinor) > 9223372036854775807n ||
			fields.currency !== "LKR"
		)
			throw new Error("Exact positive LKR minor units and credit required");
		return {
			path: `/api/bank/requests/${target}/refunds`,
			init: { method: "POST", body: JSON.stringify(body) },
		};
	}
	if (!uuid.test(venue)) throw new Error("Applicable finance venue required");
	const suffix =
		op === "DESTINATION"
			? "destination-verifications"
			: op === "ATTEMPT"
				? "attempts"
				: op === "RECONCILE"
					? "reconciliations"
					: op.startsWith("EVIDENCE:")
						? `attempts/${op.slice(9)}/evidence`
						: "decisions";
	if (op.startsWith("EVIDENCE:") && !uuid.test(op.slice(9)))
		throw new Error("Current attempt required");
	return {
		path: `/api/admin/venues/${venue}/refunds/${target}/${suffix}`,
		init: { method: "POST", body: JSON.stringify(body) },
	};
}
export function RefundWorkspace({ finance = false }: { finance?: boolean }) {
	const [venue, setVenue] = useState(""),
		[target, setTarget] = useState(""),
		[credit, setCredit] = useState(""),
		[amount, setAmount] = useState(""),
		[revision, setRevision] = useState("0"),
		[reason, setReason] = useState(""),
		[rows, setRows] = useState<Refund[]>([]),
		[busy, setBusy] = useState(false),
		[error, setError] = useState(""),
		[filter, setFilter] = useState("ALL");
	const epoch = useRef(0),
		pending = useRef<{ fingerprint: string; key: string } | null>(null);
	function clear() {
		epoch.current++;
		setRows([]);
		setError("");
		setReason("");
		setTarget("");
		setCredit("");
		setAmount("");
		setRevision("0");
		pending.current = null;
		setBusy(false);
	}
	async function run(
		op: string,
		row?: Refund,
		fields: Record<string, unknown> = {},
	) {
		const current = epoch.current;
		setBusy(true);
		setError("");
		try {
			let value: unknown;
			if (op === "READ") {
				if (!uuid.test(target)) throw new Error("Refund ID required");
				value = await roomRequest(`/api/refunds/${target}`);
			} else if (op === "QUEUE") {
				if (!uuid.test(venue)) throw new Error("Finance venue required");
				value = await roomRequest(`/api/admin/venues/${venue}/refunds`);
			} else {
				const fingerprint = JSON.stringify([
					op,
					venue,
					target,
					credit,
					amount,
					revision,
					reason,
					row?.id,
					row?.revision,
					fields,
				]);
				if (pending.current?.fingerprint !== fingerprint)
					pending.current = { fingerprint, key: crypto.randomUUID() };
				const command = refundCommand(
					op,
					venue,
					row?.id ?? target,
					String(row?.revision ?? revision),
					pending.current.key,
					reason,
					op === "REQUEST"
						? { creditId: credit, amountMinor: amount, currency: "LKR" }
						: fields,
				);
				value = await roomRequest(command.path, command.init);
			}
			const received = refundRows(value);
			if (epoch.current === current) {
				setRows(received);
				if (op !== "READ" && op !== "QUEUE") pending.current = null;
			}
		} catch (e) {
			if (epoch.current === current) {
				setRows([]);
				if (
					e instanceof RoomRequestError &&
					[401, 403, 404].includes(e.status)
				) {
					clear();
					setVenue("");
				}
				setError(e instanceof Error ? e.message : "Refund unavailable");
			}
		} finally {
			if (epoch.current === current) setBusy(false);
		}
	}
	return (
		<Card
			title={
				finance
					? "Synthetic refund review queue"
					: "Synthetic bank-only refunds"
			}
		>
			<p>
				<strong>SYNTHETIC REFUND TEST · NO REAL MONEY TRANSFERRED</strong>
			</p>
			<p>
				APPROVED DOES NOT MEAN REPAID. Only uncommitted synthetic bank credit is
				eligible. No booking cancellation or real transfers.
			</p>
			{finance ? (
				<>
					<Field
						id="refund-venue"
						label="Finance venue ID"
						value={venue}
						onChange={(e) => {
							clear();
							setVenue(e.target.value);
						}}
					/>
					<Button busy={busy} onClick={() => run("QUEUE")}>
						Load refund queue
					</Button>
					<SelectField
						id="refund-filter"
						label="Refund state filter"
						value={filter}
						onChange={(e) => setFilter(e.target.value)}
					>
						{[
							"ALL",
							"REQUESTED",
							"UNDER_REVIEW",
							"APPROVED",
							"AWAITING_EVIDENCE",
							"UNKNOWN_RECONCILIATION_REQUIRED",
							"SIMULATED_CONFIRMED",
							"FAILED_CONFIRMED",
							"REJECTED",
							"WITHDRAWN",
						].map((v) => (
							<option key={v}>{v}</option>
						))}
					</SelectField>
				</>
			) : null}
			<Field
				id="refund-target"
				label="Refund ID to inspect or owned bank request ID to request a refund"
				value={target}
				onChange={(e) => {
					clear();
					setTarget(e.target.value);
				}}
			/>
			<Button busy={busy} onClick={() => run("READ")}>
				Load refund status
			</Button>
			{!finance ? (
				<>
					<Field
						id="refund-credit"
						label="Controlled-matched synthetic credit ID"
						value={credit}
						onChange={(e) => setCredit(e.target.value)}
					/>
					<Field
						id="refund-amount"
						label="Requested LKR minor units"
						inputMode="numeric"
						value={amount}
						onChange={(e) => setAmount(e.target.value)}
					/>
					<Field
						id="refund-revision"
						label="Current bank request revision"
						inputMode="numeric"
						value={revision}
						onChange={(e) => setRevision(e.target.value)}
					/>
				</>
			) : null}
			<Field
				id="refund-reason"
				label="Reason (synthetic information only)"
				maxLength={160}
				value={reason}
				onChange={(e) => setReason(e.target.value)}
			/>
			{!finance ? (
				<Button busy={busy} onClick={() => run("REQUEST")}>
					Request synthetic return
				</Button>
			) : null}
			<Button variant="secondary" onClick={clear}>
				Clear private refund context
			</Button>
			{error ? (
				<p role="alert">{error} Refresh before retrying a changed command.</p>
			) : null}
			<div aria-live="polite">
				{busy ? (
					<p role="status">Loading refund state…</p>
				) : rows.length === 0 ? (
					<p>
						No refund records loaded. Sign in with an eligible synthetic
						account; current backend permissions apply.
					</p>
				) : (
					rows
						.filter((r) => filter === "ALL" || r.state === filter)
						.map((r) => (
							<section key={r.id} aria-label={`Refund ${r.id}`}>
								<h3>{r.state.replaceAll("_", " ")}</h3>
								{r.state === "UNKNOWN_RECONCILIATION_REQUIRED" ? (
									<p role="alert">UNKNOWN OUTGOING RESULT — DO NOT RESEND</p>
								) : null}
								<dl className="account-summary">
									<div>
										<dt>Refund</dt>
										<dd>{r.id}</dd>
									</div>
									<div>
										<dt>Requested / approved minor units</dt>
										<dd>
											{r.amountMinor} / {r.approvedMinor} {r.currency}
										</dd>
									</div>
									<div>
										<dt>Available uncommitted credit</dt>
										<dd>
											{r.availableMinor} {r.currency}
										</dd>
									</div>
									<div>
										<dt>Resolution</dt>
										<dd>{r.resolution}</dd>
									</div>
									<div>
										<dt>Revision</dt>
										<dd>{r.revision}</dd>
									</div>
									<div>
										<dt>Destination independently verified</dt>
										<dd>{r.destinationVerified ? "Yes" : "Pending"}</dd>
									</div>
								</dl>
								{finance ? (
									<>
										<p>
											Each action requires its own current venue grant.
											Requesters cannot review; approvers cannot execute;
											outgoing recorders cannot verify completion.
										</p>
										{r.state === "REQUESTED" ? (
											<Button
												busy={busy}
												onClick={() => run("DECISION", r, { action: "REVIEW" })}
											>
												Begin independent review
											</Button>
										) : null}
										{r.state === "UNDER_REVIEW" && !r.destinationVerified ? (
											<Button
												busy={busy}
												onClick={() =>
													run("DESTINATION", r, {
														destinationReference: r.destinationReference,
													})
												}
											>
												Verify controlled original-payer destination
											</Button>
										) : null}
										{r.state === "UNDER_REVIEW" && r.destinationVerified ? (
											<Button
												busy={busy}
												onClick={() =>
													run("DECISION", r, { action: "APPROVE" })
												}
											>
												Approve and reserve synthetic funds
											</Button>
										) : null}
										{["REQUESTED", "UNDER_REVIEW"].includes(r.state) ? (
											<Button
												variant="secondary"
												busy={busy}
												onClick={() => run("DECISION", r, { action: "REJECT" })}
											>
												Reject request
											</Button>
										) : null}
										{[
											"REQUESTED",
											"UNDER_REVIEW",
											"APPROVED",
											"FAILED_CONFIRMED",
										].includes(r.state) ? (
											<Button
												variant="secondary"
												busy={busy}
												onClick={() =>
													run("DECISION", r, { action: "WITHDRAW" })
												}
											>
												Withdraw before unresolved outgoing effects
											</Button>
										) : null}
										{["APPROVED", "FAILED_CONFIRMED"].includes(r.state) ? (
											<Button busy={busy} onClick={() => run("ATTEMPT", r)}>
												Prepare independent simulated attempt
											</Button>
										) : null}
										{[
											"AWAITING_EVIDENCE",
											"UNKNOWN_RECONCILIATION_REQUIRED",
										].includes(r.state) ? (
											<fieldset>
												<legend>
													Independent controlled outgoing evidence
												</legend>
												{[
													"SYNTHETIC-CONFIRMED",
													"SYNTHETIC-FAILED",
													...(r.state === "AWAITING_EVIDENCE"
														? ["SYNTHETIC-UNKNOWN"]
														: []),
												].map((f) => (
													<Button
														key={f}
														busy={busy}
														onClick={() =>
															run(
																r.state === "UNKNOWN_RECONCILIATION_REQUIRED"
																	? "RECONCILE"
																	: `EVIDENCE:${r.attempts.at(-1)?.id}`,
																r,
																{ fixtureKey: f },
															)
														}
													>
														{f.replace(
															"SYNTHETIC-",
															r.attempts
																.at(-1)
																?.evidence.some(
																	(e) =>
																		e.fixture === f && e.phase === "RECORDED",
																)
																? "Independently verify simulated "
																: "Record simulated ",
														)}
													</Button>
												))}
											</fieldset>
										) : null}
									</>
								) : null}
								<h4>Decision and attempt history</h4>
								<ul>
									{r.decisions.map((d) => (
										<li key={`${d.at}-${d.action}`}>
											{d.action} — {d.at}
										</li>
									))}
									{r.attempts.map((a) => (
										<li key={a.id}>
											Attempt {a.sequence} · {a.id}
											<ul>
												{a.evidence.map((e) => (
													<li key={`${e.fixture}-${e.phase}`}>
														{e.fixture} · {e.phase} — {e.at}
													</li>
												))}
											</ul>
										</li>
									))}
								</ul>
								{finance ? (
									<>
										<h4>Financial audit</h4>
										<ul>
											{r.audit?.map((a) => (
												<li key={`${a.at}-${a.action}`}>
													{a.action} — {a.at}
												</li>
											))}
										</ul>
									</>
								) : null}
							</section>
						))
				)}
			</div>
		</Card>
	);
}

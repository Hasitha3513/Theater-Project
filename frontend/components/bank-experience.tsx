import type { BankRequest } from "../lib/bank";
import { confirmedBookingHref } from "../lib/bank-request";
import { displayMoney } from "../lib/packages";

// Display only: no rounding, inferred money, browser-clock eligibility or mutations.
export function bankMoney(value: string | number | undefined) {
	if (typeof value === "number" && (!Number.isSafeInteger(value) || value < 0))
		return "Amount unavailable";
	if (value === undefined || !/^\d+$/.test(String(value)))
		return "Amount unavailable";
	return `LKR ${displayMoney(String(value))}`;
}
export function bankDate(value?: string) {
	if (!value) return "Not supplied";
	const parsed = Date.parse(value);
	if (!Number.isFinite(parsed)) return value;
	// Bank request DTO has no venue timezone: label UTC rather than invent one.
	return (
		new Intl.DateTimeFormat("en-GB", {
			dateStyle: "medium",
			timeStyle: "short",
			timeZone: "UTC",
		}).format(new Date(parsed)) + " UTC"
	);
}
export function BankSections() {
	return (
		<nav className="bank-ux-sections" aria-label="Bank request sections">
			<ol>
				{[
					"Request context",
					"Your claim",
					"Receipt evidence",
					"Review & recovery",
				].map((label, i) => (
					<li key={label}>
						<span aria-hidden="true">{i + 1}</span>
						{label}
					</li>
				))}
			</ol>
			<p>
				These sections describe the workflow, not completed payment steps. Each
				submission requires your explicit action.
			</p>
		</nav>
	);
}
export function CustomerBankSummary({ value: v }: { value: BankRequest }) {
	const href = confirmedBookingHref(
		v.financial?.syntheticBookingStatus,
		v.financial?.bookingId,
	);
	return (
		<section
			className="bank-ux-summary"
			aria-label="Current bank request summary"
		>
			<section className="bank-ux-panel" aria-label="Request and review status">
				<h3>Request overview</h3>
				<dl className="account-summary">
					<div>
						<dt>Expected amount · synthetic</dt>
						<dd className="bank-ux-total">{bankMoney(v.amountMinor)}</dd>
					</div>
					<div>
						<dt>Request</dt>
						<dd>{v.requestState}</dd>
					</div>
					<div>
						<dt>Receipt review</dt>
						<dd>{v.reviewState}</dd>
					</div>
					<div>
						<dt>Inventory</dt>
						<dd>{v.inventoryState}</dd>
					</div>

					<div>
						<dt>Submit by (UTC)</dt>
						<dd>{bankDate(v.submitBy)}</dd>
					</div>
					<div>
						<dt>Inventory expires (UTC)</dt>
						<dd>{bankDate(v.inventoryExpiresAt)}</dd>
					</div>
				</dl>
				<p>
					A claim or reviewed receipt is not independently verified bank credit.
				</p>
			</section>
			<section
				className="bank-ux-panel bank-ux-instructions"
				aria-label="Existing payment instructions"
			>
				<h3>Payment instructions · synthetic only</h3>
				<p>{v.instructions}</p>
				<p>Instruction version {v.instructionVersion ?? "Not supplied"}</p>
				<p>
					Do not transfer real money. No receiving account is configured for
					commercial use.
				</p>
			</section>
			<details className="bank-ux-panel">
				<summary>Timing and technical references</summary>
				<dl className="account-summary">
					<div>
						<dt>Request reference</dt>
						<dd>{v.id}</dd>
					</div>
					<div>
						<dt>Quotation reference</dt>
						<dd>{v.quoteId}</dd>
					</div>
					<div>
						<dt>Revision</dt>
						<dd>{v.revision}</dd>
					</div>
					{[
						["Inventory expires", v.inventoryExpiresAt],
						["Initiate by", v.initiateBy],
						["Submit by", v.submitBy],
						["Request expiry", v.expiresAt],
						["Review target", v.reviewBy],
					].map(([label, date]) => (
						<div key={label}>
							<dt>{label}</dt>
							<dd>{bankDate(date)}</dd>
						</div>
					))}
					<div>
						<dt>Configured review window</dt>
						<dd>{v.reviewWindow ?? "Not supplied"}</dd>
					</div>
					<div>
						<dt>Review overdue</dt>
						<dd>{v.overdue ? "OVERDUE" : "Not flagged by server"}</dd>
					</div>
				</dl>
				<p>
					Dates are shown in UTC because this request contract supplies no venue
					timezone. These observations do not extend inventory or payment
					deadlines.
				</p>
			</details>
			<section
				className="bank-ux-panel"
				aria-label="Synthetic financial status"
			>
				<h3>Funds and booking status · synthetic</h3>
				{v.financial ? (
					<>
						<p>
							{v.financial.syntheticFundingStatus} ·{" "}
							{v.financial.syntheticBookingStatus}
						</p>
						<dl className="account-summary">
							<div>
								<dt>Allocated from verified synthetic credit</dt>
								<dd>{bankMoney(v.financial.allocatedMinor)}</dd>
							</div>
							<div>
								<dt>Outstanding</dt>
								<dd>{bankMoney(v.financial.outstandingMinor)}</dd>
							</div>
						</dl>
					</>
				) : (
					<p>
						Financial outcome is unavailable or unresolved. Do not infer payment
						or booking success.
					</p>
				)}
				<p>
					Allocation is not a new incoming payment. SIMULATED_CONFIRMED is not a
					real commercial confirmation. Refund approval is not an outgoing
					repayment. Commercial payment and confirmation remain disabled. Access
					is not provisioned.
				</p>
				{href && (
					<p>
						<a href={href}>View simulated booking details</a>
					</p>
				)}
			</section>
			<section className="bank-ux-panel">
				<h3>Receipt evidence</h3>
				{v.evidence.length ? (
					v.evidence.map((e) => (
						<p key={e.id}>
							{e.state} · {e.expiresAt}
						</p>
					))
				) : (
					<p>No receipt evidence submitted.</p>
				)}
				<p>
					Upload or scan completion does not verify funds. Only approved
					synthetic fixtures may be used.
				</p>
			</section>
			<details className="bank-ux-panel">
				<summary>Claims, review history and background work</summary>
				<h3>Submission generations (declared, not verified money)</h3>
				{v.claims?.map((c) => (
					<p key={c.id}>
						Generation {c.generation} · declared{" "}
						{bankMoney(c.declaredAmountMinor)} · received{" "}
						{bankDate(c.receivedAt)}
					</p>
				))}
				<h3>Review history</h3>
				{v.history.length ? (
					v.history.map((h) => (
						<p key={h.id}>
							{h.action} · {h.reason} · {bankDate(h.at)}
						</p>
					))
				) : (
					<p>No review decision recorded.</p>
				)}
				<h3>Background work — live delivery disabled</h3>
				{v.work?.map((w) => (
					<p key={w.id}>
						{w.kind} · {w.state} · Attempts {w.attempts}
					</p>
				))}
			</details>
		</section>
	);
}

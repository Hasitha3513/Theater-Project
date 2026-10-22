"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type AdminBankSummary,
	loadBankSummary,
	type SummaryContext,
	summaryLabels,
	summaryQuery,
} from "../lib/admin-bank-summary";
import { RoomRequestError } from "../lib/rooms";
import { Badge, Button, Card, Field } from "./primitives";

export function AdminBankSummarySection() {
	const [context, setContext] = useState<SummaryContext>({
		venue: "",
		currency: "",
		from: "",
		to: "",
	});
	const [summary, setSummary] = useState<AdminBankSummary>();
	const [busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"No financial data loaded. Select your report scope, then load explicitly.",
		);
	const epoch = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		status = useRef<HTMLParagraphElement>(null);
	const clear = useCallback(() => {
		epoch.current++;
		controller.current?.abort();
		setSummary(undefined);
		setBusy(false);
	}, []);
	useEffect(() => {
		const hide = () => {
			clear();
			setMessage(
				"Protected summary cleared. Explicitly reload to check current access.",
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
	function change(key: keyof SummaryContext, value: string) {
		clear();
		setContext((previous) => ({ ...previous, [key]: value }));
		setMessage("Scope changed. Load a fresh summary explicitly.");
	}
	async function load() {
		clear();
		const token = epoch.current;
		try {
			summaryQuery(context);
		} catch {
			setMessage(
				"Enter a valid venue ID, uppercase three-letter currency and increasing date range.",
			);
			status.current?.focus();
			return;
		}
		const c = new AbortController();
		controller.current = c;
		setBusy(true);
		setMessage("Loading authorized synthetic bank summary…");
		try {
			const next = await loadBankSummary(
				context,
				AbortSignal.any([c.signal, AbortSignal.timeout(10000)]),
			);
			if (epoch.current !== token) return;
			setSummary(next);
			setMessage(
				"Current synthetic snapshot loaded. Reload explicitly for a new observation.",
			);
		} catch (error) {
			if (epoch.current !== token) return;
			setSummary(undefined);
			setMessage(
				error instanceof RoomRequestError
					? error.status === 401
						? "Session expired. Sign in again, then explicitly reload."
						: error.status === 403
							? "Access denied. Current report.bank.synthetic.read permission at this venue is required."
							: error.status === 400
								? "Invalid or excessive date range or currency. Check venue-local dates and the server-configured range limit."
								: error.status === 429
									? "Too many requests. Wait, then explicitly retry."
									: "Bank summary unavailable. No financial values are shown; retry explicitly."
					: "Session changed or report unavailable or invalid. No financial values are shown; sign in if needed and retry explicitly.",
			);
			queueMicrotask(() => status.current?.focus());
		} finally {
			if (epoch.current === token) setBusy(false);
		}
	}
	return (
		<section
			className="staff-workspace admin-bank-summary"
			aria-label="Synthetic Bank Summary"
		>
			<Card title="Synthetic Bank Summary">
				<Badge tone="info">
					Synthetic banking only · No real money movement
				</Badge>
				<p>
					No independently verified real bank balance, commercial revenue
					qualification or production finance acceptance.
				</p>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						if (!busy) void load();
					}}
				>
					<div className="summary-filter-grid">
						<Field
							id="summary-venue"
							label="Venue ID"
							hint="Use a venue covered by your independent report permission."
							required
							value={context.venue}
							onChange={(event) => change("venue", event.target.value)}
						/>
						<Field
							id="summary-currency"
							label="Currency code"
							hint="Explicit uppercase three-letter code; currency support is checked by the server."
							required
							maxLength={3}
							pattern="[A-Z]{3}"
							value={context.currency}
							onChange={(event) => change("currency", event.target.value)}
						/>
						<Field
							id="summary-from"
							label="Credited from (venue-local date, inclusive)"
							type="date"
							required
							value={context.from}
							onChange={(event) => change("from", event.target.value)}
						/>
						<Field
							id="summary-to"
							label="Credited to (venue-local date, exclusive)"
							type="date"
							required
							value={context.to}
							onChange={(event) => change("to", event.target.value)}
						/>
					</div>
					<div className="button-row">
						<Button type="submit" busy={busy}>
							Load Bank Summary
						</Button>
						<Button
							variant="secondary"
							disabled={busy}
							onClick={() => void load()}
						>
							Reload Bank Summary
						</Button>
						<Button
							variant="secondary"
							onClick={() => {
								clear();
								setContext({ venue: "", currency: "", from: "", to: "" });
								setMessage("Report scope reset. No financial data loaded.");
							}}
						>
							Reset summary
						</Button>
					</div>
				</form>
				<p
					role="status"
					aria-live="polite"
					aria-atomic="true"
					aria-busy={busy || undefined}
					ref={status}
					tabIndex={-1}
				>
					{message}
				</p>
				{summary && (
					<div className="bank-summary-result">
						<h3>Complete-population summary</h3>
						<p>
							Venue {summary.venueId} · {summary.currency} · {summary.timezone}
						</p>
						<p>
							Credited {summary.from} inclusive to {summary.to} exclusive, in
							the venue timezone.
						</p>
						<p>
							Observed{" "}
							<time dateTime={summary.snapshotAt}>{summary.snapshotAt}</time> ·
							Definition {summary.metricVersion}
						</p>
						<dl className="report-amounts">
							{(
								Object.keys(summaryLabels) as (keyof typeof summaryLabels)[]
							).map((key) => (
								<div key={key}>
									<dt>{summaryLabels[key]}</dt>
									<dd>
										{summary.amounts[key] === null
											? "Unavailable"
											: `${summary.amounts[key]} ${summary.currency} minor units`}
									</dd>
								</div>
							))}
						</dl>
						<p>
							Totals cover the complete filtered population, not the returned
							page. Credit eligibility is not permission to allocate.
						</p>
					</div>
				)}
				<p>
					Payment claims and receipt evidence are not independently verified
					synthetic credit. Allocation, refund obligations, approval and
					simulated repayment remain separate. APPROVED does not mean REPAID.
					SIMULATED_CONFIRMED does not establish real payment or repayment.
				</p>
				<Link href="/admin/reports/bank" prefetch={false}>
					Open detailed synthetic bank report →
				</Link>
			</Card>
		</section>
	);
}

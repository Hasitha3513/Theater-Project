"use client";
import { useRef, useState } from "react";
import type { Hold } from "../lib/inventory";
import { venueTime } from "../lib/inventory";
import { displayMoney } from "../lib/packages";
import { catalogItems } from "../lib/publications";
import type { Quote } from "../lib/quotes";
import { quoteDisclosures, validateQuote } from "../lib/quotes";
import { RoomRequestError, roomRequest, stateFor } from "../lib/rooms";
import { Button, Card, Field, SelectField, StateMessage } from "./primitives";
import { QuoteContinuation } from "./quote-handoff";

type Choice = {
	packageId: string;
	versionId: string;
	name: string;
	options: { serviceId: string; label: string; mode: string }[];
};
export function QuoteSummary({ quote }: { quote: Quote }) {
	return (
		<section aria-label="Synthetic quotation summary">
			{quoteDisclosures.map((label) => (
				<p key={label}>
					<strong>{label}</strong>
				</p>
			))}
			<p>
				{quote.package.name} · Revision {quote.revision} · {quote.state} ·
				Method {quote.paymentMethod}
			</p>
			<h4>Server-calculated synthetic LKR line items</h4>
			<dl className="account-summary">
				{quote.lines.map((l) => (
					<div key={l.lineId}>
						<dt>{l.label}</dt>
						<dd>
							Quantity {l.quantity} · Unit LKR {displayMoney(l.unitMinor)} ·
							Amount LKR {displayMoney(l.amountMinor)}
						</dd>
					</div>
				))}
			</dl>
			<p>Total: LKR {displayMoney(quote.totalMinor)}</p>
			<p>
				Tax is an explicit synthetic fixture, not approved Sri Lankan tax
				policy. Zero CineNest-added method surcharge does not represent external
				bank/provider costs.
			</p>
			<p>
				Quote validity: {venueTime(quote.expiresAt, quote.timezone)}. Original
				hold expiry: {venueTime(quote.holdExpiresAt, quote.timezone)} (
				{quote.timezone}). No hold renewal.
			</p>
		</section>
	);
}
export function SyntheticQuotation({
	hold,
	venueId,
}: {
	hold: Hold;
	venueId: string;
}) {
	const [choices, setChoices] = useState<Choice[]>();
	const [selection, setSelection] = useState("");
	const [optional, setOptional] = useState<string[]>([]);
	const [method, setMethod] = useState("BANK");
	const [quote, setQuote] = useState<Quote>();
	const [reviewed, setReviewed] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<unknown>();
	const pending = useRef<{ path: string; body: string } | undefined>(undefined);
	const selected = choices?.find((c) => c.versionId === selection);
	const changed =
		!!quote &&
		(quote.paymentMethod !== method ||
			JSON.stringify([...quote.optionalServiceIds].sort()) !==
				JSON.stringify([...optional].sort()));
	async function load() {
		setBusy(true);
		setError(undefined);
		try {
			const query = new URLSearchParams({
				roomId: hold.roomId,
				sessionStart: hold.sessionStart,
				sessionEnd: hold.sessionEnd,
			});
			const items = catalogItems(
				await roomRequest(`/api/venues/${venueId}/packages?${query}`),
			);
			// Service IDs are transport identifiers only; the backend validates every selection and price.
			if (
				items.some((p) =>
					p.options.some(
						(o) => !("serviceId" in o) || typeof o.serviceId !== "string",
					),
				)
			)
				throw new Error("Invalid package options");
			setChoices(items as unknown as Choice[]);
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	async function command(action: "create" | "revise" | "accept" | "retry") {
		if (!pending.current) {
			if (action === "create" && !selected) return;
			const path =
				action === "create"
					? "/api/quotes"
					: `/api/quotes/${quote?.quoteId}/${action === "revise" ? "revisions" : "accept"}`;
			const common = { idempotencyKey: crypto.randomUUID() };
			const body =
				action === "accept"
					? {
							...common,
							expectedRevision: quote?.revision,
							contentHash: quote?.contentHash,
							disclosures: quoteDisclosures,
						}
					: action === "revise"
						? {
								...common,
								expectedRevision: quote?.revision,
								optionalServiceIds: optional,
								paymentMethod: method,
							}
						: {
								...common,
								holdId: hold.holdId,
								expectedHoldRevision: hold.revision,
								packageId: selected?.packageId,
								packageVersionId: selection,
								optionalServiceIds: optional,
								paymentMethod: method,
							};
			pending.current = { path, body: JSON.stringify(body) };
		}
		setBusy(true);
		setError(undefined);
		setReviewed(false);
		try {
			const result = validateQuote(
				await roomRequest(pending.current.path, {
					method: "POST",
					body: pending.current.body,
				}),
			);
			setQuote(result);
			pending.current = undefined;
		} catch (e) {
			if (
				e instanceof RoomRequestError &&
				[400, 401, 403, 404, 409, 429].includes(e.status)
			)
				pending.current = undefined;
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status))
				setQuote(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	async function refresh() {
		if (!quote) return;
		setBusy(true);
		setError(undefined);
		setReviewed(false);
		try {
			setQuote(
				validateQuote(await roomRequest(`/api/quotes/${quote.quoteId}`)),
			);
		} catch (e) {
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status))
				setQuote(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<Card title="Synthetic quotation laboratory">
			{quoteDisclosures.map((label) => (
				<p key={label}>
					<strong>{label}</strong>
				</p>
			))}
			<p>
				Explicitly configured nonproduction test environments and associated
				synthetic accounts only. Commercial checkout is disabled.
			</p>
			{!quote && (
				<Button disabled={busy || !!pending.current} onClick={load}>
					Load applicable synthetic package choices
				</Button>
			)}
			{choices?.length === 0 && (
				<StateMessage kind="empty">
					<p>No applicable published package. No quotation issued.</p>
				</StateMessage>
			)}
			{choices && choices.length > 0 && (
				<div className="filter-grid">
					<SelectField
						id={`quote-package-${hold.holdId}`}
						label="Published package"
						value={selection}
						disabled={busy || !!pending.current || !!quote}
						onChange={(e) => {
							setSelection(e.target.value);
							setOptional([]);
							setReviewed(false);
						}}
					>
						<option value="">Select package</option>
						{choices.map((c) => (
							<option key={c.versionId} value={c.versionId}>
								{c.name}
							</option>
						))}
					</SelectField>
					<SelectField
						id={`quote-method-${hold.holdId}`}
						label="Intended method (no payment)"
						value={method}
						disabled={busy || !!pending.current || quote?.state === "ACCEPTED"}
						onChange={(e) => {
							setMethod(e.target.value);
							setReviewed(false);
						}}
					>
						<option value="BANK">BANK</option>
						<option value="GATEWAY">GATEWAY</option>
					</SelectField>
					{selected?.options
						.filter((o) => o.mode === "OPTIONAL")
						.map((o) => (
							<label key={o.serviceId}>
								<input
									type="checkbox"
									checked={optional.includes(o.serviceId)}
									disabled={
										busy || !!pending.current || quote?.state === "ACCEPTED"
									}
									onChange={(e) => {
										setOptional(
											e.target.checked
												? [...optional, o.serviceId]
												: optional.filter((id) => id !== o.serviceId),
										);
										setReviewed(false);
									}}
								/>
								{o.label}
							</label>
						))}
				</div>
			)}
			{!quote && selected && (
				<Button
					disabled={busy || !!pending.current}
					onClick={() => command("create")}
				>
					Calculate synthetic quotation
				</Button>
			)}
			{busy && <StateMessage kind="loading" />}
			{error !== undefined && (
				<StateMessage kind={stateFor(error)}>
					<p>
						{error instanceof Error ? error.message : "Quotation unavailable"}.
						Refresh and review; no payment or booking confirmation.
					</p>
				</StateMessage>
			)}
			{!!pending.current && !busy && (
				<>
					<p>Outcome unknown. Retry the identical command before editing.</p>
					<Button onClick={() => command("retry")}>
						Retry original quotation command
					</Button>
				</>
			)}
			{quote && (
				<>
					<QuoteSummary quote={quote} />
					{quote.state === "ACCEPTED" && !busy && !pending.current && (
						<QuoteContinuation
							reference={quote.quoteId}
							onDenied={() => setQuote(undefined)}
						/>
					)}
					<Button disabled={busy || !!pending.current} onClick={refresh}>
						Refresh quotation state
					</Button>
					{quote.state === "ISSUED" && (
						<>
							<Button
								disabled={busy || !!pending.current}
								onClick={() => command("revise")}
							>
								Review new revision for changed selections/method
							</Button>
							<Field
								id={`quote-reviewed-${hold.holdId}`}
								label="I reviewed this exact synthetic revision and all three disclosures"
								type="checkbox"
								checked={reviewed}
								disabled={busy || !!pending.current || changed}
								onChange={(e) => setReviewed(e.target.checked)}
							/>
							<Button
								disabled={!reviewed || busy || !!pending.current || changed}
								onClick={() => command("accept")}
							>
								Accept synthetic revision only
							</Button>
						</>
					)}
					{["EXPIRED", "INVALIDATED"].includes(quote.state) && (
						<StateMessage kind="error">
							<p>
								Quotation cannot be accepted. Return to inventory and obtain a
								new applicable hold.
							</p>
						</StateMessage>
					)}
				</>
			)}
		</Card>
	);
}

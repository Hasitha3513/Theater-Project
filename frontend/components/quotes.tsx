"use client";
import { useRef, useState } from "react";
import {
	commercialIssuanceUnavailable,
	issueOperationalQuote,
	operationalQuoteBody,
	operationalQuoteHold,
	operationalQuoteMessage,
} from "../lib/commercial-quotes";
import type { Hold } from "../lib/inventory";
import { venueTime } from "../lib/inventory";
import { displayMoney } from "../lib/packages";
import { catalogItems } from "../lib/publications";
import type { CommercialQuotePreview, Quote } from "../lib/quotes";
import {
	commercialPreviewQuery,
	quoteDisclosures,
	validateCommercialPreview,
	validateQuote,
} from "../lib/quotes";
import { RoomRequestError, roomRequest, stateFor } from "../lib/rooms";
import { Button, Card, Field, SelectField, StateMessage } from "./primitives";
import { QuoteContinuation } from "./quote-handoff";

type Choice = {
	packageId: string;
	versionId: string;
	name: string;
	options: { serviceId: string; label: string; mode: string }[];
};
export function CommercialIssuanceNotice() {
	return <p>{commercialIssuanceUnavailable}</p>;
}

export function CommercialPreviewSummary({
	preview,
}: {
	preview: CommercialQuotePreview;
}) {
	return (
		<section
			aria-label="Commercial policy preview — not an offer"
			style={{ minWidth: 0, overflowWrap: "anywhere" }}
		>
			<h3>Commercial policy preview — not an offer</h3>
			<CommercialIssuanceNotice />
			<p>
				<strong>{preview.classification}</strong>
			</p>
			<p>
				Read-only synthetic explanation. No quotation, hold, booking, acceptance
				or payment is created by this preview.
			</p>
			<p>
				{preview.durationMinutes} minutes · {preview.guests} guests ·{" "}
				{preview.paymentMethod} · {preview.timezone}
			</p>
			<p>
				Venue {preview.venueId} · Room {preview.roomId} · Package{" "}
				{preview.packageId} · Version {preview.packageVersionId}
			</p>
			<p>
				{venueTime(preview.sessionStart, preview.timezone)} –{" "}
				{venueTime(preview.sessionEnd, preview.timezone)}
			</p>
			<p>
				Selected add-ons:{" "}
				{preview.optionalServiceIds.length
					? preview.optionalServiceIds.join(", ")
					: "None"}
			</p>
			<h4>Known configured components</h4>
			<dl className="account-summary">
				{preview.lines.map((line) => (
					<div key={line.lineId}>
						<dt>{line.label}</dt>
						<dd>
							Quantity {line.quantity} · LKR {displayMoney(line.amountMinor)}
						</dd>
					</div>
				))}
			</dl>
			<p>
				<strong>
					Known components only: LKR{" "}
					{displayMoney(preview.knownComponentsMinor)} — not payable.
				</strong>
			</p>
			<p>
				Missing tax and fees are unresolved, not zero. Reviewed draft values do
				not establish commercial authority.
			</p>
			{preview.policyCandidate ? (
				<p>
					Policy candidate {preview.policyCandidate.policyId} · Revision{" "}
					{preview.policyCandidate.revision} · Draft review status{" "}
					{preview.policyCandidate.draftReviewStatus} · Hash{" "}
					{preview.policyCandidate.contentHash}. This draft is not active.
				</p>
			) : (
				<p>No unique applicable policy candidate is available.</p>
			)}
			<h4>Commercial readiness blockers</h4>
			<ul>
				{preview.blockers.map((blocker) => (
					<li key={blocker}>{blocker.replaceAll("_", " ")}</li>
				))}
			</ul>
			<p>Commercial issuance, checkout and customer access remain disabled.</p>
		</section>
	);
}
export function QuoteSummary({ quote }: { quote: Quote }) {
	return (
		<section
			className="booking-quote-summary"
			aria-label="Synthetic quotation summary"
		>
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
			<p className="booking-quote-total">
				Total: LKR {displayMoney(quote.totalMinor)}
			</p>
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
	if ("mode" in hold && hold.mode === "OPERATIONAL") {
		const operational = operationalQuoteHold(hold, venueId);
		return operational ? (
			<OperationalQuotation
				key={`${operational.holdId}:${operational.revision}`}
				hold={operational}
			/>
		) : (
			<CommercialIssuanceNotice />
		);
	}
	return <SyntheticQuotationWorkflow hold={hold} venueId={venueId} />;
}

function OperationalQuotation({
	hold,
}: {
	hold: NonNullable<ReturnType<typeof operationalQuoteHold>>;
}) {
	const [method, setMethod] = useState<"BANK" | "GATEWAY">("BANK");
	const [receipt, setReceipt] = useState<string>();
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);
	const [outcomeUnknown, setOutcomeUnknown] = useState(false);
	const pending = useRef<string | undefined>(undefined);
	const inFlight = useRef(false);

	async function issue() {
		if (
			inFlight.current ||
			receipt ||
			!hold.commercialQuotationPermitted ||
			hold.state !== "ACTIVE"
		)
			return;
		inFlight.current = true;
		setBusy(true);
		try {
			pending.current ??= operationalQuoteBody(
				hold,
				crypto.randomUUID(),
				method,
			);
			const result = await issueOperationalQuote(pending.current);
			setReceipt(result.quoteId);
			setOutcomeUnknown(false);
			setMessage("");
		} catch (error) {
			if (
				error instanceof RoomRequestError &&
				error.status < 500 &&
				!outcomeUnknown
			)
				pending.current = undefined;
			else setOutcomeUnknown(true);
			setMessage(operationalQuoteMessage(error));
		} finally {
			inFlight.current = false;
			setBusy(false);
		}
	}

	return (
		<Card title="Commercial quotation">
			{receipt ? (
				<p role="status">
					Quotation reference {receipt}. No payment or booking confirmation was
					recorded.
				</p>
			) : hold.state === "ACTIVE" && hold.commercialQuotationPermitted ? (
				<>
					<SelectField
						id={`commercial-method-${hold.holdId}`}
						label="Method for fee calculation"
						value={method}
						disabled={busy || outcomeUnknown}
						onChange={(event) =>
							setMethod(event.target.value === "GATEWAY" ? "GATEWAY" : "BANK")
						}
					>
						<option value="BANK">Bank transfer</option>
						<option value="GATEWAY">Online payment</option>
					</SelectField>
					<Button type="button" disabled={busy} onClick={() => void issue()}>
						{outcomeUnknown
							? "Retry same quotation request"
							: "Request commercial quotation"}
					</Button>
				</>
			) : (
				<CommercialIssuanceNotice />
			)}
			<p role="status">{message}</p>
		</Card>
	);
}

function SyntheticQuotationWorkflow({
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
	const [preview, setPreview] = useState<{
		context: string;
		value: CommercialQuotePreview;
	}>();
	const pending = useRef<{ path: string; body: string } | undefined>(undefined);
	const selected = choices?.find((c) => c.versionId === selection);
	const previewContext = JSON.stringify([
		venueId,
		hold.holdId,
		hold.revision,
		hold.roomId,
		hold.sessionStart,
		hold.sessionEnd,
		selection,
		method,
		[...optional].sort(),
	]);
	const currentPreviewContext = useRef(previewContext);
	currentPreviewContext.current = previewContext;
	const currentPreview =
		preview?.context === previewContext ? preview.value : undefined;
	const changed =
		!!quote &&
		(quote.paymentMethod !== method ||
			JSON.stringify([...quote.optionalServiceIds].sort()) !==
				JSON.stringify([...optional].sort()));
	async function load() {
		setPreview(undefined);
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
		setPreview(undefined);
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
	async function inspectCommercialPreview() {
		if (!selected || pending.current || busy) return;
		const context = previewContext;
		const pin = currentPreview?.policyCandidate;
		setBusy(true);
		setError(undefined);
		setPreview(undefined);
		try {
			const query = commercialPreviewQuery(
				{
					holdId: hold.holdId,
					expectedHoldRevision: hold.revision,
					packageId: selected.packageId,
					packageVersionId: selection,
					paymentMethod: method,
					optionalServiceIds: optional,
				},
				pin,
			);
			const value = validateCommercialPreview(
				await roomRequest(`/api/quotes/preview?${query}`),
			);
			if (currentPreviewContext.current !== context) return;
			if (
				value.holdId !== hold.holdId ||
				value.holdRevision !== hold.revision ||
				value.venueId !== venueId ||
				value.roomId !== hold.roomId ||
				value.packageId !== selected.packageId ||
				value.packageVersionId !== selection ||
				value.paymentMethod !== method ||
				JSON.stringify([...value.optionalServiceIds].sort()) !==
					JSON.stringify([...optional].sort()) ||
				Date.parse(value.sessionStart) !== Date.parse(hold.sessionStart) ||
				Date.parse(value.sessionEnd) !== Date.parse(hold.sessionEnd)
			)
				throw new Error("Preview does not match the current selection");
			setPreview({ context, value });
		} catch (e) {
			if (currentPreviewContext.current !== context) return;
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status))
				setQuote(undefined);
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<Card title="Package choices & quotation" className="booking-quotation">
			<section id="booking-packages">
				<h3>Choose a package & supported add-ons</h3>
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
								setPreview(undefined);
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
							disabled={
								busy || !!pending.current || quote?.state === "ACCEPTED"
							}
							onChange={(e) => {
								setMethod(e.target.value);
								setPreview(undefined);
								setReviewed(false);
							}}
						>
							<option value="BANK">Bank transfer/deposit (synthetic)</option>
							<option value="GATEWAY">
								Gateway (synthetic; integration deferred)
							</option>
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
											setPreview(undefined);
										}}
									/>
									{o.label}
								</label>
							))}
					</div>
				)}
				{selected &&
					!selected.options.some((option) => option.mode === "OPTIONAL") && (
						<p>No optional add-ons are listed for this package.</p>
					)}
			</section>
			{!quote && selected && (
				<Button
					disabled={busy || !!pending.current}
					onClick={() => void inspectCommercialPreview()}
				>
					Preview commercial policy readiness (read-only)
				</Button>
			)}
			{!quote && currentPreview && (
				<CommercialPreviewSummary preview={currentPreview} />
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
			{!quote && (
				<div className="booking-review-grid">
					<section id="booking-review">
						<h3>Review your quotation</h3>
						<p>
							Select an applicable package and calculate a quotation to see its
							exact server-priced terms.
						</p>
					</section>
					<section id="booking-accept">
						<h3>Accept this revision</h3>
						<p>
							Acceptance requires an issued revision and your explicit review.
							No payment or booking is confirmed here.
						</p>
					</section>
				</div>
			)}
			{quote && (
				<div className="booking-review-grid">
					<section id="booking-review">
						<h3>Review your quotation</h3>
						<QuoteSummary quote={quote} />

						<Button disabled={busy || !!pending.current} onClick={refresh}>
							Refresh quotation state
						</Button>
					</section>
					<section id="booking-accept">
						<h3>Accept this revision</h3>
						{changed && (
							<StateMessage kind="conflict">
								<p>
									Your selections differ from this quotation. Request a new
									revision, then review its terms again.
								</p>
							</StateMessage>
						)}
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
						{quote.state === "ACCEPTED" && !busy && !pending.current && (
							<QuoteContinuation
								reference={quote.quoteId}
								onDenied={() => setQuote(undefined)}
							/>
						)}
					</section>
				</div>
			)}
		</Card>
	);
}

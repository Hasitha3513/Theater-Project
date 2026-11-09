"use client";
import { type FormEvent, useEffect, useRef, useState } from "react";
import {
	approvalMessage,
	emptyDocument,
	maxApprovalEnvelopeBytes,
	type Fee,
	type Policy,
	type PolicyDocument,
	policyMessage,
	policyNotice,
	policyRequest,
	policyVenues,
	RoomRequestError,
	sections,
	validApprovalEnvelope,
	validateDocument,
} from "../lib/commercial-policies";
import { roomRequest } from "../lib/rooms";
import { Button, Card, Field, PageHeading } from "./primitives";

type Summary = { policyId: string; title: string; revision: string };
export function CommercialPolicies() {
	const [venues, setVenues] = useState<
			import("../lib/commercial-policies").PolicyVenue[]
		>([]),
		[venue, setVenue] = useState("");
	const [items, setItems] = useState<Summary[]>([]),
		[policy, setPolicy] = useState<Policy>();
	const [document, setDocument] = useState<PolicyDocument>(emptyDocument),
		[packageId, setPackage] = useState("");
	const [reason, setReason] = useState(""),
		[message, setMessage] = useState(""),
		[errors, setErrors] = useState<Record<string, string>>({});
	const [busy, setBusy] = useState(false),
		[uncertain, setUncertain] = useState(false),
		[history, setHistory] = useState<unknown>();
	const [domain, setDomain] = useState("FINANCE"),
		[outcome, setOutcome] = useState("APPROVE"),
		[reference, setReference] = useState("");
	const [approvalEnvelope, setApprovalEnvelope] = useState("");
	const key = useRef<string>("");
	const base = `/api/admin/venues/${encodeURIComponent(venue)}/commercial-policies`;
	useEffect(() => {
		let live = true;
		roomRequest<unknown>("/api/admin/venues")
			.then((v) => {
				if (live) setVenues(policyVenues(v));
			})
			.catch(() => {
				if (live)
					setMessage(
						"Venue discovery unavailable. Enter an exact authorized venue reference below.",
					);
			});
		return () => {
			live = false;
		};
	}, []);
	useEffect(() => {
		const clearEnvelope = () => setApprovalEnvelope("");
		const events = ["cinenest-session-change", "pagehide", "popstate"];
		for (const event of events) window.addEventListener(event, clearEnvelope);
		window.document.addEventListener("visibilitychange", clearEnvelope);
		return () => {
			for (const event of events)
				window.removeEventListener(event, clearEnvelope);
			window.document.removeEventListener("visibilitychange", clearEnvelope);
		};
	}, []);
	function change(field: string, value: PolicyDocument[string]) {
		setDocument((d) => ({ ...d, [field]: value }));
		setErrors({});
		key.current = "";
	}
	function selectVenue(value: string) {
		setApprovalEnvelope("");
		setVenue(value);
		setPolicy(undefined);
		setItems([]);
		setHistory(undefined);
		setDocument(emptyDocument());
		setPackage("");
		setUncertain(false);
		key.current = "";
	}
	async function load() {
		setBusy(true);
		try {
			const page = await policyRequest<{ items: Summary[] }>(
				`${base}?page=0&size=100`,
			);
			setItems(page.items);
			setMessage(
				"Draft list loaded. These policies are not commercially active.",
			);
		} catch (e) {
			setMessage(policyMessage(e));
		} finally {
			setBusy(false);
		}
	}
	async function inspect(id: string) {
		setApprovalEnvelope("");
		setBusy(true);
		try {
			const next = await policyRequest<Policy>(`${base}/${id}`);
			setPolicy(next);
			setDocument(next.document);
			setPackage(next.packageId ?? "");
			setHistory(undefined);
			setErrors({});
			setMessage(`Revision ${next.revision} loaded — ${next.status}.`);
		} catch (e) {
			setMessage(policyMessage(e));
		} finally {
			setBusy(false);
		}
	}
	function startDraft() {
		setApprovalEnvelope("");
		setPolicy(undefined);
		setDocument(emptyDocument());
		setPackage("");
		setHistory(undefined);
		setUncertain(false);
		setReason("");
		setErrors({});
		key.current = "";
		setMessage("New draft. Unresolved selections are not approvals.");
	}
	async function submitApproval(event: FormEvent) {
		event.preventDefault();
		if (
			busy ||
			uncertain ||
			!policy ||
			policy.venueId !== venue ||
			!validApprovalEnvelope(approvalEnvelope)
		)
			return;
		setBusy(true);
		setApprovalEnvelope("");
		try {
			await policyRequest(`${base}/${policy.policyId}/trusted-approvals`, {
				method: "POST",
				body: JSON.stringify({ envelope: approvalEnvelope }),
			});
			setMessage(
				"Signed synthetic decision accepted. Commercial issuance remains disabled.",
			);
		} catch (error) {
			setMessage(approvalMessage(error));
			if (!(error instanceof RoomRequestError) || error.status >= 500)
				setUncertain(true);
		} finally {
			setBusy(false);
		}
	}
	async function write(
		path: string,
		method: string,
		payload: Record<string, unknown>,
	) {
		if (uncertain) return;
		if (!reason.trim()) {
			setErrors({ reason: "Record an attributable reason." });
			return;
		}
		setBusy(true);
		if (!key.current) key.current = crypto.randomUUID();
		try {
			const receipt = await policyRequest<{
				policyId: string;
				revision: string;
			}>(path, {
				method,
				body: JSON.stringify({
					...payload,
					idempotencyKey: key.current,
					reason,
				}),
			});
			key.current = "";
			setMessage(
				`Command saved at revision ${receipt.revision}. Reload to inspect the authoritative result.`,
			);
			await inspect(receipt.policyId);
		} catch (e) {
			setMessage(policyMessage(e));
			if (!(e instanceof RoomRequestError) || e.status >= 500)
				setUncertain(true);
		} finally {
			setBusy(false);
		}
	}
	function save(event: FormEvent) {
		event.preventDefault();
		const invalid = validateDocument(document);
		if (Object.keys(invalid).length) {
			setErrors(invalid);
			return;
		}
		if (policy)
			void write(`${base}/${policy.policyId}`, "PUT", {
				expectedRevision: policy.revision,
				document,
			});
		else
			void write(base, "POST", {
				scopeKind: packageId ? "PACKAGE" : "VENUE",
				packageId: packageId || null,
				document,
			});
	}
	function fees(methods: string[], channels: string[]) {
		const existing = document.fees as Fee[];
		return methods.flatMap((method) =>
			channels.map(
				(channel) =>
					existing.find(
						(f) => f.method === method && f.channel === channel,
					) ?? {
						method,
						channel,
						type: "UNRESOLVED",
						amountMinor: null,
						currency: "LKR",
						refundability: "UNRESOLVED",
					},
			),
		);
	}
	function toggle(keyName: "paymentMethods" | "feeChannels", value: string) {
		const selected = document[keyName] as string[];
		const next = selected.includes(value)
			? selected.filter((v) => v !== value)
			: [...selected, value];
		const methods =
				keyName === "paymentMethods"
					? next
					: (document.paymentMethods as string[]),
			channels =
				keyName === "feeChannels" ? next : (document.feeChannels as string[]);
		setDocument((d) => ({
			...d,
			[keyName]: next,
			fees: fees(methods, channels),
		}));
		key.current = "";
	}
	const locked = busy || uncertain;
	const canEdit =
		!policy || policy.permittedActions.includes("commercial.policy.edit");
	return (
		<>
			<PageHeading
				eyebrow="Synthetic administration"
				title="Commercial policy drafts"
			>
				Synthetic-only draft configuration and attributable review. No
				commercial issuance, checkout or customer access.
			</PageHeading>
			<p role="status" aria-live="polite">
				{message}
			</p>
			{uncertain && (
				<p role="alert">
					Write outcome uncertain. Do not resubmit. Reload the list and inspect
					history before beginning another command.
				</p>
			)}
			<Card title="Authorized venue">
				{venues.length > 0 && (
					<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} htmlFor="policy-venue-select">
						Venue
						<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
							id="policy-venue-select"
							value={venue}
							onChange={(e) => selectVenue(e.target.value)}
						>
							<option value="">Select venue</option>
							{venues.map((v) => (
								<option key={v.venueId} value={v.venueId}>
									{v.displayName} · {v.timezone}
								</option>
							))}
						</select>
					</label>
				)}
				<Field
					id="policy-venue"
					label="Exact authorized venue reference"
					value={venue}
					onChange={(e) => selectVenue(e.target.value)}
					hint="Venue selection does not grant permission."
				/>
				<Button disabled={busy || !venue} onClick={() => void load()}>
					Load policy drafts
				</Button>
				<Button disabled={busy || !venue} onClick={startDraft}>
					New draft
				</Button>
			</Card>
			<Card title="Saved drafts">
				<ul>
					{items.map((item) => (
						<li key={item.policyId}>
							<Button
								disabled={busy}
								onClick={() => void inspect(item.policyId)}
							>
								{item.title} · Revision {item.revision}
							</Button>
						</li>
					))}
				</ul>
				{items.length === 0 && <p>No drafts loaded.</p>}
			</Card>
			{venue && (
				<Card
					title={
						policy
							? `Policy revision ${policy.revision} — ${policy.status}`
							: "Create policy draft"
					}
				>
					<p>{policyNotice}</p>
					{policy && (
						<p style={{ overflowWrap: "anywhere" }}>
							Policy {policy.policyId} · Venue timezone {policy.venueTimezone} ·
							Content hash {policy.contentHash}
						</p>
					)}
					<form onSubmit={save}>
						<fieldset style={{ minWidth: 0, maxWidth: "100%", overflowWrap: "anywhere" }} disabled={locked || !canEdit}>
							<legend>Structured policy document</legend>
							<Field
								id="policy-title"
								label="Draft title"
								value={document.title as string}
								maxLength={200}
								onChange={(e) => change("title", e.target.value)}
								error={errors.title}
							/>
							<Field
								id="policy-package"
								label="Package reference (optional)"
								value={packageId}
								disabled={!!policy}
								onChange={(e) => setPackage(e.target.value)}
								hint="Leave empty for venue scope; package must belong to this venue."
							/>
							{(
								["effectiveFromProposal", "effectiveUntilProposal"] as const
							).map((field) => (
								<Field
									key={field}
									id={field}
									label={
										field === "effectiveFromProposal"
											? "Proposed effective start (UTC)"
											: "Proposed effective end (UTC)"
									}
									type="datetime-local"
									value={
										typeof document[field] === "string"
											? (document[field] as string).slice(0, 16)
											: ""
									}
									onChange={(e) =>
										change(
											field,
											e.target.value ? `${e.target.value}:00Z` : null,
										)
									}
									error={errors[field]}
									hint="UTC proposal only; this does not activate the policy."
								/>
							))}
							{sections.map((section) => (
								<fieldset style={{ minWidth: 0, maxWidth: "100%", overflowWrap: "anywhere" }} key={section.title}>
									<legend>{section.title}</legend>
									{section.fields.map(([name, label, options]) =>
										options ? (
											<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} key={name} htmlFor={name}>
												{label}
												<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
													id={name}
													value={String(document[name])}
													onChange={(e) => change(name, e.target.value)}
												>
													{options.map((value) => (
														<option key={value} value={value}>
															{value.replaceAll("_", " ")}
														</option>
													))}
												</select>
											</label>
										) : (
											<Field
												key={name}
												id={name}
												label={label}
												value={(document[name] as string) ?? ""}
												onChange={(e) =>
													change(
														name,
														e.target.value ||
															(name === "exceptionNotes" ? "" : null),
													)
												}
												error={errors[name]}
												hint={
													name.includes("Minor")
														? "Whole minor units: 100 = LKR 1.00. No floating-point amounts."
														: undefined
												}
											/>
										),
									)}
								</fieldset>
							))}
							<fieldset style={{ minWidth: 0, maxWidth: "100%", overflowWrap: "anywhere" }}>
								<legend>Payment methods and fee channels</legend>
								{["BANK", "GATEWAY"].map((v) => (
									<Field
										key={v}
										id={`method-${v}`}
										label={v}
										type="checkbox"
										checked={(document.paymentMethods as string[]).includes(v)}
										onChange={() => toggle("paymentMethods", v)}
									/>
								))}
								{["ONLINE", "MANUAL"].map((v) => (
									<Field
										key={v}
										id={`channel-${v}`}
										label={v}
										type="checkbox"
										checked={(document.feeChannels as string[]).includes(v)}
										onChange={() => toggle("feeChannels", v)}
									/>
								))}
								{(document.fees as Fee[]).map((fee, index) => (
									<fieldset style={{ minWidth: 0, maxWidth: "100%", overflowWrap: "anywhere" }} key={`${fee.method}-${fee.channel}`}>
										<legend>
											{fee.method} / {fee.channel} fee
										</legend>
										{["type", "refundability"].map((field) => (
											<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} key={field} htmlFor={`fee-${index}-${field}`}>
												{field}
												<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
													id={`fee-${index}-${field}`}
													value={fee[field as keyof Fee] ?? ""}
													onChange={(e) => {
														const list = [...(document.fees as Fee[])];
														list[index] = {
															...fee,
															[field]: e.target.value,
															...(field === "type"
																? {
																		amountMinor:
																			e.target.value === "NONE" ? "0" : null,
																	}
																: {}),
														};
														change("fees", list);
													}}
												>
													{(field === "type"
														? ["UNRESOLVED", "NONE", "FIXED_MINOR"]
														: ["UNRESOLVED", "REFUNDABLE", "NON_REFUNDABLE"]
													).map((v) => (
														<option key={v} value={v}>
															{v.replaceAll("_", " ")}
														</option>
													))}
												</select>
											</label>
										))}
										<Field
											id={`fee-${index}-amount`}
											label="Fee amount (LKR minor units)"
											value={fee.amountMinor ?? ""}
											disabled={fee.type !== "FIXED_MINOR"}
											onChange={(e) => {
												const list = [...(document.fees as Fee[])];
												list[index] = {
													...fee,
													amountMinor: e.target.value || null,
												};
												change("fees", list);
											}}
										/>
									</fieldset>
								))}
							</fieldset>
							<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} htmlFor="non-refundable">
								Non-refundable components
								<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
									multiple
									id="non-refundable"
									value={(document.nonRefundableComponents as string[]) ?? []}
									onChange={(e) =>
										change(
											"nonRefundableComponents",
											Array.from(e.target.selectedOptions, (v) => v.value),
										)
									}
								>
									{[
										"BASE",
										"EXTENSION",
										"EXTRA_GUEST",
										"ADDON",
										"METHOD_FEE",
										"TAX",
									].map((v) => (
										<option key={v} value={v}>
											{v}
										</option>
									))}
								</select>
							</label>
							<Button onClick={() => change("nonRefundableComponents", [])}>
								Explicitly select no non-refundable components
							</Button>
							<Field
								id="policy-references"
								label="Supplied references (comma separated; not proof of authority)"
								value={(document.suppliedReferences as string[]).join(",")}
								onChange={(e) =>
									change(
										"suppliedReferences",
										e.target.value
											? e.target.value.split(",").map((v) => v.trim())
											: [],
									)
								}
							/>
							<Field
								id="policy-submit"
								label="Submit this complete revision for review"
								type="checkbox"
								checked={document.submittedForReview as boolean}
								onChange={(e) => change("submittedForReview", e.target.checked)}
							/>
						</fieldset>
						<Field
							id="policy-reason"
							label="Command / review reason"
							value={reason}
							maxLength={500}
							onChange={(e) => {
								setReason(e.target.value);
								key.current = "";
							}}
							error={errors.reason}
						/>
						<Button type="submit" disabled={locked || !canEdit}>
							Save draft
						</Button>
					</form>
					{policy && (
						<>
							<Button
								disabled={busy}
								onClick={() => void inspect(policy.policyId)}
							>
								Reload current revision
							</Button>
							<Button
								disabled={busy}
								onClick={() => {
									void policyRequest(
										`${base}/${policy.policyId}/history?page=0&size=100`,
									)
										.then(setHistory)
										.catch((e) => setMessage(policyMessage(e)));
								}}
							>
								View immutable history
							</Button>
							<fieldset style={{ minWidth: 0, maxWidth: "100%", overflowWrap: "anywhere" }} disabled={locked}>
								<legend>
									Record authorized review of revision {policy.revision}
								</legend>
								<p>
									An evidence reference alone does not establish reviewer
									authority. Authors cannot review their own policy.
								</p>
								<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} htmlFor="policy-domain">
									Review domain
									<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
										id="policy-domain"
										value={domain}
										onChange={(e) => setDomain(e.target.value)}
									>
										{["FINANCE", "LEGAL", "OPERATIONS"].map((v) => (
											<option key={v} value={v}>
												{v}
											</option>
										))}
									</select>
								</label>
								<label style={{ display: "block", minWidth: 0, maxWidth: "100%" }} htmlFor="policy-outcome">
									Review decision
									<select style={{ minWidth: 0, maxWidth: "100%", width: "100%" }}
										id="policy-outcome"
										value={outcome}
										onChange={(e) => setOutcome(e.target.value)}
									>
										{["APPROVE", "REJECT", "REQUEST_CHANGES"].map((v) => (
											<option key={v} value={v}>
												{v}
											</option>
										))}
									</select>
								</label>
								<Field
									id="policy-review-reference"
									label="Review evidence reference"
									value={reference}
									onChange={(e) => setReference(e.target.value)}
									maxLength={500}
								/>
								<Button
									disabled={
										!reference.trim() ||
										!policy.permittedActions.includes(
											`commercial.policy.review.${domain.toLowerCase()}`,
										)
									}
									onClick={() =>
										void write(`${base}/${policy.policyId}/reviews`, "POST", {
											expectedRevision: policy.revision,
											expectedContentHash: policy.contentHash,
											domain,
											outcome,
											evidenceReference: reference,
										})
									}
								>
									Record review
								</Button>
							</fieldset>
							<Card title="Signed approval intake (synthetic only)">
								<p>
									Intake is disabled by default. Use only a pre-signed synthetic
									envelope. Never enter a signing key or other secret.
								</p>
								<p style={{ overflowWrap: "anywhere" }}>
									Venue {policy.venueId} · Policy {policy.policyId} · Revision{" "}
									{policy.revision} · Draft package {policy.packageId ?? "none"}
								</p>
								<p id="policy-envelope-hint">
									The server checks the signed room and package version, revision,
									and content hash. This action cannot enable commercial issuance.
								</p>
								<form onSubmit={submitApproval}>
									<label htmlFor="policy-envelope">Signed synthetic envelope</label>
									<textarea
										id="policy-envelope"
										aria-describedby="policy-envelope-hint policy-envelope-error"
										aria-invalid={
											approvalEnvelope !== "" &&
											!validApprovalEnvelope(approvalEnvelope)
										}
										autoComplete="off"
										spellCheck={false}
										maxLength={maxApprovalEnvelopeBytes}
										value={approvalEnvelope}
										disabled={locked}
										onChange={(event) => setApprovalEnvelope(event.target.value)}
										style={{ display: "block", maxWidth: "100%", width: "100%" }}
									/>
									<p id="policy-envelope-error">
										{approvalEnvelope !== "" &&
											!validApprovalEnvelope(approvalEnvelope) &&
											"Enter a non-empty signed envelope of at most 4 KiB."}
									</p>
									<Button
										type="submit"
										disabled={
											locked ||
											policy.venueId !== venue ||
											!validApprovalEnvelope(approvalEnvelope)
										}
									>
										Submit signed synthetic decision
									</Button>
								</form>
							</Card>
							<Card title="Current revision review history">
								{policy.reviews.length === 0 ? (
									<p>No reviews recorded for this revision.</p>
								) : (
									policy.reviews.map((v) => (
										<p key={v.reviewId}>
											{v.domain} · {v.outcome} · Reviewer {v.reviewerId} ·{" "}
											{v.reviewedAt}
										</p>
									))
								)}
							</Card>
						</>
					)}
					{history !== undefined && (
						<details open>
							<summary>Immutable policy history</summary>
							<pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
								{JSON.stringify(history, null, 2)}
							</pre>
						</details>
					)}
				</Card>
			)}
		</>
	);
}

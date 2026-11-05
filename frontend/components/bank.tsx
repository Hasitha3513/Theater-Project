"use client";
import { StaffVenueContext } from "./staff-venue-context";
import Link from "next/link";
import { reviewHref } from "../lib/staff-review";
import { useCallback, useEffect, useRef, useState } from "react";
import { bankLabels, type BankRequest, validateBank } from "../lib/bank";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";
import {
	bankRequestHref,
	loadBankRequest,
	confirmedBookingHref,
} from "../lib/bank-request";
import { quoteReference } from "../lib/bank-handoff";
import { BankRequestCreation } from "./bank-handoff";
import { BankSections, CustomerBankSummary } from "./bank-experience";
export function BankNotice() {
	return (
		<aside aria-label="Synthetic bank safety">
			{bankLabels.map((l) => (
				<p key={l}>
					<strong>{l}</strong>
				</p>
			))}
		</aside>
	);
}
export function BankSummary({
	value: v,
	customer = false,
}: {
	value: BankRequest;
	customer?: boolean;
}) {
	const bookingHref = confirmedBookingHref(
		v.financial?.syntheticBookingStatus,
		v.financial?.bookingId,
	);
	if (customer) return <CustomerBankSummary value={v} />;
	return (
		<>
			<dl className="account-summary">
				<div>
					<dt>Request / review / inventory</dt>
					<dd>
						{v.requestState} / {v.reviewState} / {v.inventoryState}
					</dd>
				</div>
				<div>
					<dt>Synthetic expected amount</dt>
					<dd>
						{v.currency} {v.amountMinor} minor units
					</dd>
				</div>
				<div>
					<dt>Inventory expires</dt>
					<dd>{v.inventoryExpiresAt}</dd>
				</div>
				<div>
					<dt>Initiate by / review window</dt>
					<dd>
						{v.initiateBy} / {v.reviewWindow}
					</dd>
				</div>
				<div>
					<dt>Submit by / request expiry</dt>
					<dd>
						{v.submitBy} / {v.expiresAt}
					</dd>
				</div>
				<div>
					<dt>Review target</dt>
					<dd>
						{v.reviewBy}
						{v.overdue ? " · OVERDUE" : ""}
					</dd>
				</div>
			</dl>
			<p>
				{v.instructions} · Instruction version {v.instructionVersion}
			</p>
			<h3>Submission generations (declared, not verified money)</h3>
			{v.claims?.map((c) => (
				<p key={c.id}>
					Generation {c.generation} · declared {c.declaredAmountMinor} minor
					units · received {c.receivedAt}
				</p>
			))}
			{v.financial ? (
				<section aria-label="Synthetic financial status">
					<h3>SYNTHETIC TEST — NO REAL MONEY</h3>
					<p>
						{v.financial.syntheticFundingStatus} ·{" "}
						{v.financial.syntheticBookingStatus}
					</p>
					<p>
						Allocated LKR {v.financial.allocatedMinor} minor units · Outstanding
						LKR {v.financial.outstandingMinor} minor units
					</p>
					<p>
						Commercial payment and confirmation remain disabled. Access is not
						provisioned.
					</p>
				</section>
			) : null}
			{customer && bookingHref && (
				<p>
					<a href={bookingHref}>View simulated booking details</a>
				</p>
			)}
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
			<h3>Background work — live delivery disabled</h3>
			{v.work?.map((w) => (
				<p key={w.id}>
					{w.kind} · {w.state} · Attempts {w.attempts}
				</p>
			))}
			<h3>Review history</h3>
			{v.history.length ? (
				v.history.map((h) => (
					<p key={h.id}>
						{h.action} · {h.reason} · {h.at}
					</p>
				))
			) : (
				<p>No review decision recorded.</p>
			)}
		</>
	);
}
export function BankWorkspace({
	reviewer = false,
	initialQuoteId = "",
	initialVenueId = "",
	evidenceRequest,
}: {
	reviewer?: boolean;
	initialQuoteId?: string;
	initialVenueId?: string;
	evidenceRequest?: string;
}) {
	const contextual = !reviewer && evidenceRequest !== undefined;
	const uploadFlow = useRef<
		{ id: string; revision: number; claim: string } | undefined
	>(undefined);
	const contextRead = useRef<AbortController | undefined>(undefined);
	const [handoverTo, setHandoverTo] = useState("");
	const [venue, setVenue] = useState(initialVenueId),
		[requestId, setRequestId] = useState(""),
		[amount, setAmount] = useState(""),
		[paid, setPaid] = useState(""),
		[reference, setReference] = useState(""),
		[action, setAction] = useState("REVIEWED"),
		[reason, setReason] = useState("SYNTHETIC_REVIEW");
	const [value, setValue] = useState<BankRequest>(),
		[items, setItems] = useState<BankRequest[]>(),
		[file, setFile] = useState<File>(),
		[preview, setPreview] = useState<string>(),
		[busy, setBusy] = useState(false),
		[error, setError] = useState("");
	const epoch = useRef(0),
		input = useRef<HTMLInputElement>(null),
		pending = useRef<{ path: string; body: string; input: string } | undefined>(
			undefined,
		);
	useEffect(
		() => () => {
			if (preview) URL.revokeObjectURL(preview);
		},
		[preview],
	);
	const clear = useCallback(() => {
		epoch.current++;
		contextRead.current?.abort();
		uploadFlow.current = undefined;
		setValue(undefined);
		setItems(undefined);
		setFile(undefined);
		setPreview(undefined);
		setAmount("");
		setPaid("");
		setReference("");
		setBusy(false);
		pending.current = undefined;
		if (input.current) input.current.value = "";
	}, []);

	const reloadContext = useCallback(async () => {
		if (evidenceRequest === undefined || reviewer) return;
		clear();
		setAmount("");
		setPaid("");
		setReference("");
		setError("");
		setBusy(true);
		const token = epoch.current;
		const controller = new AbortController();
		contextRead.current = controller;
		try {
			const next = await loadBankRequest(evidenceRequest, controller.signal);
			if (token === epoch.current) {
				setValue(next);
				setRequestId(next.id);
			}
		} catch {
			if (token === epoch.current)
				setError(
					"Request unavailable, signed out, denied or changed. Return to history or reload; no submission is confirmed.",
				);
		} finally {
			if (token === epoch.current) setBusy(false);
		}
	}, [evidenceRequest, reviewer, clear]);
	useEffect(() => {
		if (!contextual) return;
		void reloadContext();
		const refresh = () => {
			void reloadContext();
		};
		const hide = () => {
			clear();
			setAmount("");
			setPaid("");
			setReference("");
			setBusy(false);
		};
		const visibility = () =>
			document.visibilityState === "hidden" ? hide() : refresh();
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", hide);
		window.addEventListener("cinenest-session-change", hide);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			epoch.current++;
			contextRead.current?.abort();
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", hide);
			window.removeEventListener("cinenest-session-change", hide);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [contextual, clear, reloadContext]);
	const created = useCallback(
		(result: BankRequest) => {
			clear();
			setValue(result);
			setRequestId(result.id);
		},
		[clear],
	);
	const prefix = reviewer
		? `/api/admin/venues/${venue}/bank/requests`
		: "/api/bank/requests";
	async function run(f: () => Promise<void>) {
		const token = epoch.current;
		setBusy(true);
		setError("");
		try {
			await f();
		} catch (e) {
			if (contextual && token !== epoch.current) return;
			setError(
				contextual
					? e instanceof RoomRequestError && [401, 403, 404].includes(e.status)
						? "Current session or owned request is unavailable. Return to account or history; no submission is confirmed."
						: e instanceof RoomRequestError &&
								[400, 409, 413].includes(e.status)
							? "Request or evidence changed, or input was refused. Reload current state and review before another submission."
							: "Submission outcome is not confirmed. Retry only the unchanged pending operation, or reload authoritative state. An unresolved upload must not be resent blindly."
					: e instanceof Error
						? e.message
						: "Request failed",
			);
			if (
				contextual &&
				e instanceof RoomRequestError &&
				[400, 409, 413].includes(e.status)
			)
				clear();
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status)) {
				clear();
				if (contextual) {
					setAmount("");
					setPaid("");
					setReference("");
				}
			}
		} finally {
			if (!contextual || token === epoch.current) setBusy(false);
		}
	}
	async function command(path: string, body: object) {
		const token = epoch.current;
		const encoded = JSON.stringify(body);
		if (contextual && !pending.current) {
			const current = await loadBankRequest(evidenceRequest ?? "");
			if (token !== epoch.current) throw new Error("Context invalidated");
			if (
				!value ||
				current.revision !== Number((body as { revision?: number }).revision) ||
				current.claimId !== (uploadFlow.current?.claim ?? value.claimId)
			)
				throw new RoomRequestError(409, "Current request changed");
		}
		if (!pending.current)
			pending.current = {
				path,
				input: encoded,
				body: JSON.stringify({
					...JSON.parse(encoded),
					commandKey: crypto.randomUUID(),
				}),
			};
		else if (pending.current.path !== path || pending.current.input !== encoded)
			throw new Error(
				"Retry the pending command or refresh before another action",
			);
		const v = validateBank(
			await roomRequest(pending.current.path, {
				method: "POST",
				body: pending.current.body,
			}),
		);
		if (contextual && v.id !== evidenceRequest)
			throw new Error("Request response mismatch");
		if (epoch.current === token) {
			setValue(v);
			setRequestId(v.id);
			pending.current = undefined;
			if (contextual && /\/(claims|resubmissions)$/.test(path)) {
				uploadFlow.current = undefined;
				setFile(undefined);
				if (input.current) input.current.value = "";
			}
		}
		return v;
	}
	async function load() {
		const token = epoch.current;
		const v = validateBank(await roomRequest(`${prefix}/${requestId}`));
		if (token === epoch.current) {
			setValue(v);
			pending.current = undefined;
			setFile(undefined);
			setPreview(undefined);
			if (input.current) input.current.value = "";
		}
	}

	async function uploadContext() {
		const token = epoch.current;
		if (!value?.claimId) throw new Error("Submit a claim first");
		let flow = uploadFlow.current;
		// An ambiguous completion is replayed with its original command identity/body.
		if (
			flow &&
			pending.current?.path === `/api/bank/uploads/${flow.id}/complete`
		) {
			await command(pending.current.path, { revision: flow.revision });
			if (token !== epoch.current) return;
			const current = await loadBankRequest(evidenceRequest ?? "");
			if (token === epoch.current) {
				setValue(current);
				uploadFlow.current = undefined;
				setFile(undefined);
				if (input.current) input.current.value = "";
			}
			return;
		}
		if (
			!flow &&
			pending.current?.path === `/api/bank/claims/${value.claimId}/uploads`
		) {
			const replay = await command(pending.current.path, {
				revision: JSON.parse(pending.current.body).revision,
			});
			if (token !== epoch.current) return;
			if (!replay.uploadId || !quoteReference.test(replay.uploadId))
				throw new Error("Upload allocation unavailable");
			flow = {
				id: replay.uploadId,
				revision: replay.revision,
				claim: value.claimId,
			};
			uploadFlow.current = flow;
		}
		const current = await loadBankRequest(evidenceRequest ?? "");
		if (token !== epoch.current) return;
		if (
			current.claimId !== value.claimId ||
			(!pending.current &&
				current.revision !== (flow?.revision ?? value.revision))
		)
			throw new RoomRequestError(409, "Claim changed");
		const incomplete = current.evidence.find((e) =>
			["ALLOCATED", "UPLOADING", "QUARANTINED"].includes(e.state),
		);
		if (!flow && incomplete)
			flow = {
				id: incomplete.id,
				revision: current.revision,
				claim: current.claimId ?? "",
			};
		if (
			flow &&
			(!quoteReference.test(flow.id) || flow.claim !== current.claimId)
		)
			throw new RoomRequestError(409, "Claim changed");
		let evidence = flow
			? current.evidence.find((e) => e.id === flow?.id)
			: undefined;
		if (flow && !evidence)
			throw new RoomRequestError(409, "Evidence no longer current");
		if (evidence?.state === "UPLOADING")
			throw new Error("Upload unresolved; no byte retry permitted");
		if (evidence?.state === "READY") {
			setValue(current);
			uploadFlow.current = undefined;
			setFile(undefined);
			return;
		}
		if (evidence && !["ALLOCATED", "QUARANTINED"].includes(evidence.state))
			throw new RoomRequestError(409, "Evidence changed");
		if (
			evidence?.state !== "QUARANTINED" &&
			(!file ||
				!["image/png", "image/jpeg"].includes(file.type) ||
				file.size === 0 ||
				file.size > 5242880)
		)
			throw new RoomRequestError(400, "Synthetic JPEG/PNG up to 5 MiB only");
		if (!flow) {
			const allocated = await command(
				`/api/bank/claims/${value.claimId}/uploads`,
				{ revision: value.revision },
			);
			if (token !== epoch.current) return;
			if (!allocated.uploadId || !quoteReference.test(allocated.uploadId))
				throw new Error("Upload allocation unavailable");
			flow = {
				id: allocated.uploadId,
				revision: allocated.revision,
				claim: value.claimId,
			};
			evidence = allocated.evidence.find((e) => e.id === flow?.id);
			if (evidence?.state !== "ALLOCATED")
				throw new Error("Allocation state unavailable");
		}
		uploadFlow.current = flow;
		if (evidence?.state !== "QUARANTINED") {
			const csrf = await roomRequest<{ token: string }>("/api/session/csrf");
			if (token !== epoch.current) return;
			const response = await fetch(`/api/bank/uploads/${flow.id}/content`, {
				method: "PUT",
				cache: "no-store",
				headers: {
					"Content-Type": file?.type ?? "",
					"X-CSRF-TOKEN": csrf.token,
				},
				body: file,
			});
			if (!response.ok)
				throw new Error("Upload outcome unresolved; reload current state");
			if (token !== epoch.current) return;
		}
		await command(`/api/bank/uploads/${flow.id}/complete`, {
			revision: flow.revision,
		});
		if (token === epoch.current) {
			uploadFlow.current = undefined;
			setFile(undefined);
			if (input.current) input.current.value = "";
		}
	}
	async function upload() {
		if (contextual) return uploadContext();
		if (!value?.claimId || !file)
			throw new Error("Create a claim and choose a synthetic fixture first");
		if (
			!["image/png", "image/jpeg"].includes(file.type) ||
			file.size === 0 ||
			file.size > 5242880
		)
			throw new Error("Synthetic JPEG/PNG up to 5 MiB only");
		const token = epoch.current;
		const allocated = await command(
			`/api/bank/claims/${value.claimId}/uploads`,
			{ revision: value.revision },
		);
		if (token !== epoch.current) return;
		if (!allocated.uploadId) throw new Error("Upload allocation unavailable");
		const csrf = await roomRequest<{ token: string }>("/api/session/csrf");
		const response = await fetch(
			`/api/bank/uploads/${allocated.uploadId}/content`,
			{
				method: "PUT",
				headers: { "Content-Type": file.type, "X-CSRF-TOKEN": csrf.token },
				body: file,
			},
		);
		if (!response.ok)
			throw new RoomRequestError(
				response.status,
				"Upload failed; refresh authoritative state before retrying",
			);
		if (token !== epoch.current) return;
		await command(`/api/bank/uploads/${allocated.uploadId}/complete`, {
			revision: allocated.revision,
		});
		setFile(undefined);
		if (input.current) input.current.value = "";
	}
	return (
		<div
			data-cn-theme={reviewer ? undefined : "booking-panel"}
			className={reviewer ? undefined : "bank-experience"}
		>
			<BankNotice />
			{reviewer && <StaffVenueContext venue={venue} />}
			<Card
				title={
					reviewer ? "Scoped bank review queue" : "Your bank request & receipt"
				}
			>
				{!reviewer && <BankSections />}
				<p>
					Manual review only. Upload does not establish money received. Scanning
					is simulated. No real bank account is configured.
				</p>
				{reviewer ? (
					<>
						<Field
							id="bank-venue"
							label="Venue ID"
							value={venue}
							onChange={(e) => {
								clear();
								setVenue(e.target.value);
							}}
						/>
						<Button
							busy={busy}
							onClick={() =>
								run(async () => {
									const token = epoch.current;
									const data = await roomRequest<unknown[]>(prefix);
									if (token === epoch.current) setItems(data.map(validateBank));
								})
							}
						>
							Load scoped queue
						</Button>
						{items?.length === 0 && <p>No requests in this permitted queue.</p>}
						{items?.map((v) => (
							<p key={v.id}>
								<Link
									prefetch={false}
									className="staff-detail-link"
									href={reviewHref("bank", venue, v.id)}
								>
									Open exact bank review details
								</Link>

								<Button
									variant="secondary"
									onClick={() => {
										clear();
										setRequestId(v.id);
										setValue(v);
									}}
								>
									{v.id} · {v.reviewState}
								</Button>
							</p>
						))}
					</>
				) : !contextual ? (
					<BankRequestCreation
						key={initialQuoteId}
						reference={initialQuoteId}
						onCreated={created}
						onDenied={clear}
					/>
				) : (
					<p>
						Exact owned request context. Claims remain unverified declarations;
						rejected or late requests retain their recorded status and do not
						renew inventory. Opening this view submits nothing.
					</p>
				)}
				{!contextual && (
					<Field
						id="bank-request"
						label="Request ID"
						value={requestId}
						onChange={(e) => {
							clear();
							setRequestId(e.target.value);
						}}
					/>
				)}
				<Button
					busy={busy}
					variant="secondary"
					onClick={() => (contextual ? void reloadContext() : run(load))}
				>
					Refresh request and recover current state
				</Button>
				{error && <p role="alert">{error}</p>}
				{busy && <p role="status">Working; success is not yet confirmed.</p>}
				{!value && !busy && (
					<p>
						{contextual
							? "Reload the owned request or return to customer history."
							: "Select or create a synthetic request."}
					</p>
				)}
				{contextual && (
					<p>
						<a href="/bookings">Return to customer history</a> ·{" "}
						<a href="/account">Account and session</a>
						{value && (
							<>
								{" "}
								·{" "}
								<a href={bankRequestHref(value.id)}>
									Return to request details
								</a>
							</>
						)}
					</p>
				)}
				{value && (!contextual || value.id === evidenceRequest) && (
					<>
						{contextual && (
							<p className="bank-context-summary">
								Request {value.id} · Revision {value.revision}
							</p>
						)}
						<BankSummary value={value} customer={!reviewer} />
						{reviewer ? (
							<>
								<Field
									id="bank-handover"
									label="Handover recipient account ID (HANDOVER only)"
									value={handoverTo}
									onChange={(e) => setHandoverTo(e.target.value)}
								/>
								<SelectField
									id="bank-action"
									label="Review action"
									value={action}
									onChange={(e) => setAction(e.target.value)}
								>
									{[
										"REVIEWED",
										"NEEDS_INFORMATION",
										"REJECTED",
										"ESCALATE",
										"HANDOVER",
										"DISPUTE",
									].map((a) => (
										<option key={a}>{a}</option>
									))}
								</SelectField>
								<SelectField
									id="bank-reason"
									label="Attributed reason"
									value={reason}
									onChange={(e) => setReason(e.target.value)}
								>
									{[
										"SYNTHETIC_REVIEW",
										"CORRECTION_REQUIRED",
										"UNREADABLE",
										"OVERDUE",
										"DISPUTED",
										"STAFF_HANDOVER",
									].map((a) => (
										<option key={a}>{a}</option>
									))}
								</SelectField>
								<Button
									busy={busy}
									onClick={() =>
										run(async () => {
											await command(
												`${prefix}/${value.id}/${["ESCALATE", "HANDOVER", "DISPUTE"].includes(action) ? "escalations" : "decisions"}`,
												{
													revision: value.revision,
													action,
													reason,
													...(action === "HANDOVER" ? { handoverTo } : {}),
												},
											);
										})
									}
								>
									Record review action
								</Button>
								{value.evidence
									.filter((e) => e.state === "READY")
									.map((e) => (
										<Button
											key={e.id}
											busy={busy}
											variant="secondary"
											onClick={() =>
												run(async () => {
													const token = epoch.current;
													const response = await fetch(
														`${prefix}/${value.id}/evidence/${e.id}`,
														{ cache: "no-store" },
													);
													if (!response.ok)
														throw new RoomRequestError(
															response.status,
															"Private preview denied or unavailable",
														);
													const blob = await response.blob();
													if (token === epoch.current)
														setPreview(URL.createObjectURL(blob));
												})
											}
										>
											Open audited synthetic preview
										</Button>
									))}
								{preview && (
									<>
										{/* biome-ignore lint/performance/noImgElement: Private ephemeral blob must bypass shared image optimization. */}
										<img
											src={preview}
											className="private-evidence-preview"
											alt="Private synthetic receipt preview"
											width="320"
											height="120"
										/>
										<Button
											variant="secondary"
											onClick={() => setPreview(undefined)}
										>
											Close private preview
										</Button>
									</>
								)}
							</>
						) : (
							<div className="bank-ux-actions">
								<section
									className="bank-ux-panel"
									aria-labelledby="bank-claim-heading"
								>
									<h3 id="bank-claim-heading">
										Declare your synthetic payment claim
									</h3>
									<p>
										This is your declaration, not proof that funds arrived.
										Submit only when you explicitly intend to create or correct
										a claim.
									</p>
									<Field
										id="bank-declared-amount"
										hint="Enter the exact amount in LKR minor units: 100 minor units = LKR 1.00. This field does not verify bank credit."
										label="Declared amount in minor units (not proof of funds)"
										disabled={contextual && busy}
										value={amount}
										onChange={(e) => setAmount(e.target.value)}
									/>
									<Field
										id="bank-declared-time"
										hint="Use the existing ISO UTC format, including Z. This declaration does not change the server deadlines."
										label="Declared payment time (ISO UTC, unverified)"
										disabled={contextual && busy}
										value={paid}
										onChange={(e) => setPaid(e.target.value)}
									/>
									<Field
										id="bank-reference"
										label="Synthetic declared reference"
										disabled={contextual && busy}
										value={reference}
										onChange={(e) => setReference(e.target.value)}
									/>
									<Button
										busy={busy}
										onClick={() =>
											run(async () => {
												await command(
													`${prefix}/${value.id}/${value.claimId ? "resubmissions" : "claims"}`,
													{
														revision: value.revision,
														declaredAmountMinor: amount,
														declaredPaidAt: paid,
														declaredReference: reference,
													},
												);
											})
										}
									>
										{value.claimId
											? "Append corrected claim"
											: "Submit synthetic claim"}
									</Button>
								</section>
								<section
									className="bank-ux-panel"
									aria-labelledby="bank-receipt-heading"
								>
									<h3 id="bank-receipt-heading">
										Add private synthetic receipt evidence
									</h3>
									<p>
										Selecting a file does not upload it. Use the separate upload
										action when ready.
									</p>
									{contextual && (
										<p>
											Use only approved watermarked synthetic JPEG/PNG fixtures,
											up to 5 MiB each and two per claim. No real receipts or
											PDF. Upload/scan status is not proof of funds. If a prior
											upload is UPLOADING, its outcome remains unresolved; do
											not allocate a replacement blindly. A quarantined upload
											may be explicitly finalized without resending bytes.
										</p>
									)}
									<div className="field">
										<label htmlFor="bank-file">
											Approved synthetic JPEG/PNG fixture
										</label>
										<input
											ref={input}
											id="bank-file"
											aria-describedby="bank-file-help"
											type="file"
											accept="image/png,image/jpeg"
											disabled={busy}
											onChange={(e) => setFile(e.target.files?.[0])}
										/>
									</div>
									<Button busy={busy} onClick={() => run(upload)}>
										Upload private synthetic receipt
									</Button>
									<p id="bank-file-help">
										Select a file, then use the separate upload action. Unknown
										outcomes require current-state recovery before repeating.
									</p>
								</section>
							</div>
						)}
					</>
				)}
			</Card>
		</div>
	);
}

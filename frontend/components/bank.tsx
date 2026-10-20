"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { bankLabels, type BankRequest, validateBank } from "../lib/bank";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";
import { BankRequestCreation } from "./bank-handoff";
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
export function BankSummary({ value: v }: { value: BankRequest }) {
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
}: {
	reviewer?: boolean;
	initialQuoteId?: string;
}) {
	const [handoverTo, setHandoverTo] = useState("");
	const [venue, setVenue] = useState(""),
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
		setValue(undefined);
		setItems(undefined);
		setFile(undefined);
		setPreview(undefined);
		pending.current = undefined;
		if (input.current) input.current.value = "";
	}, []);
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
		setBusy(true);
		setError("");
		try {
			await f();
		} catch (e) {
			setError(e instanceof Error ? e.message : "Request failed");
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status))
				clear();
		} finally {
			setBusy(false);
		}
	}
	async function command(path: string, body: object) {
		const token = epoch.current;
		const encoded = JSON.stringify(body);
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
		if (epoch.current === token) {
			setValue(v);
			setRequestId(v.id);
			pending.current = undefined;
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
	async function upload() {
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
		<>
			<BankNotice />
			<Card
				title={
					reviewer
						? "Scoped bank review queue"
						: "Synthetic request and receipt"
				}
			>
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
				) : (
					<BankRequestCreation
						key={initialQuoteId}
						reference={initialQuoteId}
						onCreated={created}
						onDenied={clear}
					/>
				)}
				<Field
					id="bank-request"
					label="Request ID"
					value={requestId}
					onChange={(e) => {
						clear();
						setRequestId(e.target.value);
					}}
				/>
				<Button busy={busy} variant="secondary" onClick={() => run(load)}>
					Refresh request and recover current state
				</Button>
				{error && <p role="alert">{error}</p>}
				{busy && <p role="status">Working; success is not yet confirmed.</p>}
				{!value && !busy && <p>Select or create a synthetic request.</p>}
				{value && (
					<>
						<BankSummary value={value} />
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
							<>
								<Field
									id="bank-declared-amount"
									label="Declared amount in minor units (not proof of funds)"
									value={amount}
									onChange={(e) => setAmount(e.target.value)}
								/>
								<Field
									id="bank-declared-time"
									label="Declared payment time (ISO UTC, unverified)"
									value={paid}
									onChange={(e) => setPaid(e.target.value)}
								/>
								<Field
									id="bank-reference"
									label="Synthetic declared reference"
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
								<div className="field">
									<label htmlFor="bank-file">
										Approved synthetic JPEG/PNG fixture
									</label>
									<input
										ref={input}
										id="bank-file"
										type="file"
										accept="image/png,image/jpeg"
										onChange={(e) => setFile(e.target.files?.[0])}
									/>
								</div>
								<Button busy={busy} onClick={() => run(upload)}>
									Upload private synthetic receipt
								</Button>
							</>
						)}
					</>
				)}
			</Card>
		</>
	);
}

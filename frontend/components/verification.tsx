"use client";
import { useEffect, useRef, useState } from "react";
import { roomRequest, RoomRequestError, stateFor } from "../lib/rooms";
import {
	type Verification,
	validateVerification,
	verificationLabels,
	uploadSynthetic,
} from "../lib/verification";
import { Button, Card, Field, SelectField, StateMessage } from "./primitives";
export function VerificationNotice() {
	return (
		<>
			{verificationLabels.map((label) => (
				<p key={label}>
					<strong>{label}</strong>
				</p>
			))}
			<p>
				Manual review of server-approved synthetic fixtures only. Simulated
				scanning is not production malware protection. No OCR, booking
				confirmation, hold extension or physical admission.
			</p>
		</>
	);
}
export function VerificationSummary({ value }: { value: Verification }) {
	return (
		<>
			<dl className="account-summary">
				<div>
					<dt>Digital test status</dt>
					<dd>
						{value.state} · Revision {value.revision} · Generation{" "}
						{value.generation}
						{value.superseded ? " · SUPERSEDED" : ""}
					</dd>
				</div>
				<div>
					<dt>Request</dt>
					<dd>{value.id}</dd>
				</div>
				<div>
					<dt>Validity</dt>
					<dd>{value.validUntil ?? "No approved decision"}</dd>
				</div>
			</dl>
			<h3>Evidence status</h3>
			{value.evidence.length === 0 ? (
				<p>No synthetic images submitted.</p>
			) : (
				value.evidence.map((e) => (
					<p key={e.id}>
						{e.type} · {e.state} · Upload expiry {e.expiresAt}
					</p>
				))
			)}
			<h3>Immutable decision history</h3>
			{value.history.length === 0 ? (
				<p>No human decision recorded.</p>
			) : (
				value.history.map((d) => (
					<p key={d.id}>
						{d.verdict} · {d.reason} · {d.at}
					</p>
				))
			)}
		</>
	);
}
export function VerificationWorkspace({
	reviewer = false,
}: {
	reviewer?: boolean;
}) {
	const [quote, setQuote] = useState("");
	const [policy, setPolicy] = useState("");
	const [consent, setConsent] = useState(false);
	const [venue, setVenue] = useState("");
	const [requestId, setRequestId] = useState("");
	const [type, setType] = useState("NIC");
	const [file, setFile] = useState<File>();
	const [value, setValue] = useState<Verification>();
	const [items, setItems] = useState<Verification[]>();
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<unknown>();
	const [progress, setProgress] = useState<number>();
	const [preview, setPreview] = useState<string>();
	const [verdict, setVerdict] = useState("VERIFIED");
	const [reason, setReason] = useState("SYNTHETIC_MATCH_18_PLUS");
	const [reviewed, setReviewed] = useState(false);
	const pending = useRef<{ path: string; body: string } | undefined>(undefined);
	const generation = useRef(0);
	useEffect(
		() => () => {
			if (preview) URL.revokeObjectURL(preview);
		},
		[preview],
	);
	function clear() {
		setFile(undefined);
		setProgress(undefined);
		setConsent(false);
		setValue(undefined);
		setItems(undefined);
		setPreview(undefined);
		setReviewed(false);
		generation.current++;
	}
	const prefix = reviewer
		? `/api/admin/venues/${venue}/verification/requests`
		: "/api/verification/requests";
	async function run(operation: () => Promise<void>) {
		setBusy(true);
		setError(undefined);
		try {
			await operation();
		} catch (e) {
			setError(e);
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status))
				clear();
		} finally {
			setBusy(false);
		}
	}
	async function refresh(id = requestId) {
		const loaded = validateVerification(await roomRequest(`${prefix}/${id}`));
		clear();
		setValue(loaded);
		setRequestId(loaded.id);
	}
	async function command(path: string, body: object) {
		if (!pending.current)
			pending.current = {
				path,
				body: JSON.stringify({ ...body, commandKey: crypto.randomUUID() }),
			};
		try {
			const loaded = validateVerification(
				await roomRequest(pending.current.path, {
					method: "POST",
					body: pending.current.body,
				}),
			);
			if (value?.id !== loaded.id) {
				setFile(undefined);
				setProgress(undefined);
			}
			setValue(loaded);
			setRequestId(loaded.id);
			setReviewed(false);
			pending.current = undefined;
			return loaded;
		} catch (e) {
			if (
				e instanceof RoomRequestError &&
				[400, 401, 403, 404, 409, 413, 415, 429].includes(e.status)
			)
				pending.current = undefined;
			throw e;
		}
	}
	async function submit() {
		if (!file || !value) return;
		const allocated = await command(
			`/api/verification/requests/${value.id}/uploads`,
			{ revision: value.revision, documentType: type },
		);
		if (!allocated.uploadId)
			throw new Error("Upload authorization unavailable");
		setProgress(0);
		await uploadSynthetic(allocated.uploadId, file, setProgress);
		await command(`/api/verification/uploads/${allocated.uploadId}/complete`, {
			revision: allocated.revision,
		});
		setFile(undefined);
		setProgress(undefined);
	}
	async function showPreview(evidence: string) {
		if (!value) return;
		const current = ++generation.current;
		setPreview(undefined);
		const response = await fetch(`${prefix}/${value.id}/evidence/${evidence}`, {
			cache: "no-store",
		});
		if (!response.ok)
			throw new RoomRequestError(
				response.status,
				"Private preview unavailable or denied",
			);
		const blob = await response.blob();
		if (blob.type !== "image/png" || blob.size > 5 * 1024 * 1024)
			throw new Error("Invalid private preview");
		if (current === generation.current) setPreview(URL.createObjectURL(blob));
	}
	return (
		<Card
			title={
				reviewer
					? "Synthetic manual review queue"
					: "Synthetic booker verification"
			}
		>
			<VerificationNotice />
			{reviewer ? (
				<>
					<Field
						id="verification-venue"
						label="Authorized venue ID"
						value={venue}
						disabled={busy || !!pending.current}
						onChange={(e) => {
							setVenue(e.target.value);
							clear();
						}}
					/>
					<Button
						disabled={busy || !venue || !!pending.current}
						onClick={() =>
							run(async () => {
								clear();
								const response = await roomRequest<unknown[]>(prefix);
								setItems(response.map(validateVerification));
							})
						}
					>
						Load venue review queue
					</Button>
					{items?.length === 0 && (
						<StateMessage kind="empty">
							<p>No review cases returned for this venue.</p>
						</StateMessage>
					)}
					{items?.map((item) => (
						<p key={item.id}>
							<Button
								disabled={busy || !!pending.current}
								onClick={() => run(() => refresh(item.id))}
							>
								{item.state} · {item.id}
							</Button>
						</p>
					))}
				</>
			) : (
				<>
					<Field
						id="verification-quote"
						label="Owned synthetic quotation ID"
						value={quote}
						disabled={busy || !!pending.current}
						onChange={(e) => setQuote(e.target.value)}
					/>
					<Field
						id="verification-policy"
						label="Operator-provided synthetic policy version"
						value={policy}
						disabled={busy || !!pending.current}
						onChange={(e) => {
							setPolicy(e.target.value);
							setConsent(false);
						}}
					/>
					<Field
						id="verification-consent"
						type="checkbox"
						label="I consent to this versioned synthetic test processing; no real documents"
						checked={consent}
						disabled={busy || !!pending.current}
						onChange={(e) => setConsent(e.target.checked)}
					/>
					<Button
						disabled={
							!quote || !policy || !consent || busy || !!pending.current
						}
						onClick={() =>
							run(async () => {
								await command("/api/verification/requests", {
									quoteId: quote,
									policyVersion: policy,
									consent: true,
								});
							})
						}
					>
						Start synthetic request
					</Button>
				</>
			)}
			<Field
				id="verification-request"
				label="Existing request ID"
				value={requestId}
				disabled={busy || !!pending.current}
				onChange={(e) => {
					clear();
					setRequestId(e.target.value);
				}}
			/>
			<Button
				disabled={!requestId || busy || !!pending.current}
				onClick={() => run(() => refresh())}
			>
				Refresh current request and history
			</Button>
			{busy && <StateMessage kind="loading" />}
			{error !== undefined && (
				<StateMessage kind={stateFor(error)}>
					<p>
						{error instanceof Error
							? error.message
							: "Synthetic verification unavailable"}
						. Refresh after conflicts. An interrupted upload expires and can be
						replaced by a new submission generation.
					</p>
				</StateMessage>
			)}
			{!!pending.current && !busy && (
				<>
					<p>Outcome unknown. Retry the identical command before editing.</p>
					<Button
						onClick={() =>
							run(async () => {
								if (pending.current) await command(pending.current.path, {});
							})
						}
					>
						Retry original verification command
					</Button>
				</>
			)}
			{value && (
				<>
					<VerificationSummary value={value} />
					{!reviewer && !value.superseded && (
						<>
							<SelectField
								id="verification-type"
								label="Synthetic document type"
								value={type}
								disabled={busy || !!pending.current}
								onChange={(e) => setType(e.target.value)}
							>
								<option>NIC</option>
								<option>PASSPORT</option>
							</SelectField>
							<Field
								id="verification-file"
								label="Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)"
								type="file"
								accept="image/jpeg,image/png"
								disabled={busy || !!pending.current}
								onChange={(e) => setFile(e.target.files?.[0])}
							/>
							<Button
								disabled={
									!file ||
									busy ||
									!!pending.current ||
									!["NOT_SUBMITTED", "SUBMITTED", "REVIEW_REQUIRED"].includes(
										value.state,
									) ||
									value.evidence.length >= 2
								}
								onClick={() => run(submit)}
							>
								Upload and request simulated scan
							</Button>
							{progress !== undefined && (
								<p role="status">
									Upload progress {progress}% — transfer is not identity
									approval.
								</p>
							)}
							<Button
								disabled={!consent || !policy || busy || !!pending.current}
								onClick={() =>
									run(async () => {
										await command(
											`/api/verification/requests/${value.id}/resubmissions`,
											{
												quoteId: value.quoteId,
												revision: value.revision,
												policyVersion: policy,
												consent: true,
											},
										);
									})
								}
							>
								Create correction generation with current consent
							</Button>
						</>
					)}
					{reviewer && (
						<>
							{value.evidence
								.filter((e) => e.state === "READY")
								.map((e) => (
									<Button
										key={e.id}
										disabled={busy || !!pending.current}
										onClick={() => run(() => showPreview(e.id))}
									>
										Open private {e.type} preview
									</Button>
								))}
							{preview && (
								<>
									{/* biome-ignore lint/performance/noImgElement: Private ephemeral blob must never enter shared image optimization/cache. */}
									<img
										src={preview}
										alt="Private explicitly synthetic fixture for current manual review"
										width="280"
									/>
									<Button onClick={() => setPreview(undefined)}>
										Close private preview
									</Button>
								</>
							)}
							<SelectField
								id="verification-verdict"
								label="Manual digital test decision"
								value={verdict}
								disabled={busy || !!pending.current}
								onChange={(e) => {
									setVerdict(e.target.value);
									setReason(
										e.target.value === "VERIFIED"
											? "SYNTHETIC_MATCH_18_PLUS"
											: e.target.value === "REVOKED"
												? "WITHDRAWN"
												: "CORRECTION_REQUIRED",
									);
									setReviewed(false);
								}}
							>
								<option>VERIFIED</option>
								<option>REJECTED</option>
								<option>REVOKED</option>
							</SelectField>
							<SelectField
								id="verification-reason"
								label="Attributable decision reason"
								value={reason}
								disabled={busy || !!pending.current}
								onChange={(e) => {
									setReason(e.target.value);
									setReviewed(false);
								}}
							>
								{(verdict === "VERIFIED"
									? ["SYNTHETIC_MATCH_18_PLUS"]
									: [
											"INCONSISTENT",
											"UNREADABLE",
											"EXPIRED_EVIDENCE",
											"CORRECTION_REQUIRED",
											"WITHDRAWN",
										]
								).map((r) => (
									<option key={r}>{r}</option>
								))}
							</SelectField>
							<Field
								id="verification-reviewed"
								type="checkbox"
								label="I manually reviewed the current synthetic evidence, booker match and 18+ policy; this does not approve admission"
								checked={reviewed}
								disabled={busy || !!pending.current}
								onChange={(e) => setReviewed(e.target.checked)}
							/>
							<Button
								disabled={
									!reviewed || busy || !!pending.current || value.superseded
								}
								onClick={() =>
									run(async () => {
										setPreview(undefined);
										await command(`${prefix}/${value.id}/decisions`, {
											revision: value.revision,
											verdict,
											reason,
										});
									})
								}
							>
								Record exact-revision manual decision
							</Button>
						</>
					)}
				</>
			)}
		</Card>
	);
}

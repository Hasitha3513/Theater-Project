"use client";
import { useEffect, useRef, useState } from "react";
import {
	type CancellationCommand,
	type CancellationContext,
	type CancellationResult,
	CancellationError,
	requestCancellation,
	validCancellationReason,
} from "../lib/booking-cancellation";
import { Badge, Button, Card, Field, StateMessage } from "./primitives";
const reasons: Record<string, string> = {
	ELIGIBLE: "The server currently permits this bounded synthetic cancellation.",
	SESSION_STARTED:
		"The purchased session has started. Pre-start cancellation is unavailable.",
	CREDENTIAL_EVER_ISSUED:
		"A credential was previously issued. Even expired or revoked credentials exclude this workflow.",
	AMENDMENT_PENDING:
		"An amendment is pending. Resolve it through the existing amendment workflow first.",
	FINANCIAL_REVIEW_REQUIRED:
		"Funding needs independent financial review. Cancellation cannot proceed automatically.",
	BOOKING_UNAVAILABLE: "Current purchased context could not be established.",
	ALREADY_CANCELLED: "The server records this synthetic booking as cancelled.",
};
export function CancellationSummary({
	value: v,
}: {
	value: CancellationResult;
}) {
	return (
		<div style={{ overflowWrap: "anywhere" }}>
			<Badge tone={v.state === "SIMULATED_CANCELLED" ? "warning" : "info"}>
				{v.state}
			</Badge>
			<p>{reasons[v.reason]}</p>
			{v.sessionStart && (
				<p>
					Purchased session start:{" "}
					<time dateTime={v.sessionStart}>{v.sessionStart}</time>. The server
					rechecks its clock before committing.
				</p>
			)}
			{v.eligible && v.refundPrincipalMinor && (
				<p>
					Eligible allocated principal: LKR{" "}
					{(BigInt(v.refundPrincipalMinor) / 100n).toString()}.
					{(BigInt(v.refundPrincipalMinor) % 100n).toString().padStart(2, "0")}.
				</p>
			)}
			{v.state === "SIMULATED_CANCELLED" && (
				<>
					<h3>Refund obligation — separate from repayment</h3>
					<p>
						<strong>Reserved for financial review.</strong> No refund approval,
						payment or settlement is established. Allocations remain preserved
						and unavailable for spending.
					</p>
					<dl className="account-summary">
						<div>
							<dt>Obligation state</dt>
							<dd>{v.refundStatus}</dd>
						</div>
						<div>
							<dt>Refund obligation reference</dt>
							<dd>{v.refundObligationId}</dd>
						</div>
						<div>
							<dt>Inventory</dt>
							<dd>
								Current purchased block released; physical readiness remains
								UNVERIFIED.
							</dd>
						</div>
					</dl>
				</>
			)}
			<details>
				<summary>Cancellation status references</summary>
				<dl className="account-summary">
					<div>
						<dt>Booking</dt>
						<dd>{v.bookingId}</dd>
					</div>
					<div>
						<dt>Venue</dt>
						<dd>{v.venueId}</dd>
					</div>
					<div>
						<dt>Purchased revision</dt>
						<dd>{v.purchasedRevision ?? "Unavailable"}</dd>
					</div>
					<div>
						<dt>Lifecycle version</dt>
						<dd>{v.lifecycleVersion}</dd>
					</div>
					<div>
						<dt>Policy version</dt>
						<dd>{v.policyVersion}</dd>
					</div>
					{v.cancellationId && (
						<div>
							<dt>Cancellation reference</dt>
							<dd>{v.cancellationId}</dd>
						</div>
					)}
				</dl>
				<p>
					Committed cancellations retain backend audit and command records. This
					panel does not expose actor identities or private evidence.
				</p>
			</details>
		</div>
	);
}
export function BookingCancellationPanel({
	context,
	onCommitted,
}: {
	context: CancellationContext;
	onCommitted?: () => void;
}) {
	const [value, setValue] = useState<CancellationResult>(),
		[error, setError] = useState<number>(),
		[busy, setBusy] = useState(false),
		[reason, setReason] = useState(""),
		[confirmed, setConfirmed] = useState(false),
		[uncertain, setUncertain] = useState(false),
		[retry, setRetry] = useState<CancellationCommand>();
	const generation = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		running = useRef(false),
		pending = useRef<CancellationCommand | undefined>(undefined),
		owner = useRef<string | undefined>(undefined),
		callback = useRef(onCommitted);
	callback.current = onCommitted;
	const identity = `${context.venueId ?? "customer"}/${context.bookingId}`;
	const currentIdentity = useRef(identity);
	currentIdentity.current = identity;
	useEffect(() => {
		const clear = () => {
			if (currentIdentity.current !== identity) return;
			generation.current++;
			controller.current?.abort();
			running.current = false;
			pending.current = undefined;
			owner.current = undefined;
			setValue(undefined);
			setError(undefined);
			setReason("");
			setConfirmed(false);
			setBusy(false);
			setUncertain(false);
			setRetry(undefined);
		};
		const visibility = () => {
			if (document.visibilityState === "hidden") clear();
		};
		clear();
		for (const event of [
			"cinenest-session-change",
			"pagehide",
			"pageshow",
			"focus",
		])
			window.addEventListener(event, clear);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			clear();
			for (const event of [
				"cinenest-session-change",
				"pagehide",
				"pageshow",
				"focus",
			])
				window.removeEventListener(event, clear);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [identity]);
	async function run(command?: CancellationCommand) {
		if (running.current) return;
		running.current = true;
		const epoch = ++generation.current,
			c = new AbortController();
		controller.current?.abort();
		controller.current = c;
		setBusy(true);
		setValue(undefined);
		setError(undefined);
		setConfirmed(false);
		setReason("");
		setRetry(undefined);
		try {
			const response = await requestCancellation(
				context,
				AbortSignal.any([c.signal, AbortSignal.timeout(15000)]),
				command,
				owner.current,
			);
			if (
				epoch !== generation.current ||
				c.signal.aborted ||
				currentIdentity.current !== identity
			)
				return;
			if (
				pending.current &&
				response.value.state !== "SIMULATED_CANCELLED" &&
				response.value.purchasedRevision !==
					pending.current.expectedPurchasedRevision
			) {
				pending.current = undefined;
				throw new CancellationError(409);
			}
			owner.current = response.account;
			setValue(response.value);
			setUncertain(false);
			if (response.value.state === "SIMULATED_CANCELLED") {
				pending.current = undefined;
				callback.current?.();
			} else if (pending.current) {
				setRetry(pending.current);
				setReason(pending.current.reason);
			}
		} catch (e) {
			if (
				epoch !== generation.current ||
				c.signal.aborted ||
				currentIdentity.current !== identity
			)
				return;
			const status = e instanceof CancellationError ? e.status : 503;
			setValue(undefined);
			setError(status);
			setConfirmed(false);
			if (status === 503 || status === 502) {
				setUncertain(!!pending.current);
			} else {
				pending.current = undefined;
				owner.current = undefined;
				setUncertain(false);
			}
		} finally {
			if (epoch === generation.current) {
				running.current = false;
				setBusy(false);
			}
		}
	}
	function submit(e: React.FormEvent) {
		e.preventDefault();
		if (
			running.current ||
			!value?.eligible ||
			!confirmed ||
			!reason.trim() ||
			!value.purchasedRevision
		)
			return;
		const command = retry ?? {
			reason: reason.trim(),
			commandKey: crypto.randomUUID(),
			expectedPurchasedRevision: value.purchasedRevision,
			expectedLifecycleVersion: value.lifecycleVersion,
		};
		pending.current = command;
		void run(command);
	}
	return (
		<Card
			title={
				context.venueId ? "Staff booking cancellation" : "Booking cancellation"
			}
			className="cancellation-panel staff-review-status"
		>
			<p role="note">
				<strong>SYNTHETIC CANCELLATION ONLY.</strong> Pre-start, never-issued
				credentials and reconciled funding are required. No real commercial
				cancellation, credential revocation or physical safety is established.
			</p>
			{context.venueId && (
				<p>
					Independent current <strong>booking.synthetic.cancel</strong>{" "}
					authority at this exact venue is required. Booking-read permission and
					administrator labels do not grant cancellation authority.
				</p>
			)}
			<Button disabled={busy} onClick={() => void run()}>
				Check Cancellation Eligibility
			</Button>
			{busy && (
				<StateMessage kind="loading">
					<p>Checking the protected booking context. No outcome is assumed.</p>
				</StateMessage>
			)}
			{error && (
				<StateMessage
					kind={
						error === 400
							? "validation"
							: error === 401
								? "unauthenticated"
								: error === 403
									? "denied"
									: error === 404
										? "not-found"
										: error === 409
											? "conflict"
											: "unavailable"
					}
				>
					<p>
						{error === 401
							? "Session unavailable or changed. Sign in and reload this booking."
							: error === 403
								? "Cancellation denied. Current ownership or independent exact-venue cancellation authority is required."
								: error === 404
									? "Booking unavailable in this authorized context."
									: error === 409
										? "Booking, policy or command context changed. Check current cancellation status before any new action."
										: error === 400
											? "The cancellation request was invalid. Reload context and review the required reason."
											: "Cancellation service unavailable. Protected details were cleared."}
					</p>
				</StateMessage>
			)}
			{uncertain && (
				<p role="alert">
					Cancellation outcome is uncertain. No success is assumed and no
					automatic retry will occur. Check Cancellation Eligibility to
					reconcile; an explicit retry retains the original command identity.
				</p>
			)}
			{value &&
				value.bookingId === context.bookingId &&
				(!context.venueId || value.venueId === context.venueId) && (
					<>
						<CancellationSummary value={value} />
						{value.eligible && (
							<form onSubmit={submit}>
								<h3>Review the consequences</h3>
								<p>
									Cancel this purchased synthetic booking and release its
									current reserved inventory. Reserve 100% of eligible allocated
									principal with zero deductions for separate financial review.
									This does not approve or pay a refund.
								</p>
								<Field
									id={`cancellation-reason-${identity.replaceAll("/", "-")}`}
									label="Cancellation reason"
									value={reason}
									maxLength={240}
									required
									readOnly={!!retry}
									onChange={(e) => {
										setReason(e.target.value);
										setConfirmed(false);
									}}
									hint={
										retry
											? "Original uncertain command: reason and versions remain unchanged."
											: "Required, up to 240 characters. Do not include identity, bank or access details."
									}
								/>
								<Field
									id={`cancellation-confirm-${identity.replaceAll("/", "-")}`}
									type="checkbox"
									label="I understand the synthetic cancellation and separate financial-review obligation."
									checked={confirmed}
									onChange={(e) => setConfirmed(e.target.checked)}
								/>
								<Button
									type="submit"
									style={{ transition: "none" }}
									disabled={
										busy ||
										!confirmed ||
										!reason.trim() ||
										!validCancellationReason(reason)
									}
								>
									{retry
										? "Retry same cancellation command"
										: "Confirm synthetic cancellation"}
								</Button>
							</form>
						)}
					</>
				)}
		</Card>
	);
}

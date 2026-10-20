"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type BankContext,
	bankReadinessReasons,
	bankHref,
	loadBankContext,
	bankCreationBody,
	bankCreationResult,
} from "../lib/bank-handoff";
import type { BankRequest } from "../lib/bank";
import { displayMoney } from "../lib/packages";
import { venueTime } from "../lib/inventory";
import { roomRequest, RoomRequestError, stateFor } from "../lib/rooms";
import { Button, Field, StateMessage } from "./primitives";

export function useBankContext(
	reference: string,
	detail = false,
	onInvalidate?: () => void,
) {
	const [value, setValue] = useState<BankContext>();
	const [error, setError] = useState<unknown>();
	const [loading, setLoading] = useState(true);
	const [epoch, setEpoch] = useState(0);
	const sequence = useRef(0),
		live = useRef<BankContext | undefined>(undefined);
	const controller = useRef<AbortController | undefined>(undefined);
	const invalidate = useCallback(() => {
		onInvalidate?.();
		controller.current?.abort();
		sequence.current++;
		live.current = undefined;
		setValue(undefined);
		setError(undefined);
		setLoading(true);
		setEpoch((n) => n + 1);
	}, [onInvalidate]);
	const reload = useCallback(async () => {
		invalidate();
		const current = sequence.current;
		const abort = new AbortController();
		controller.current = abort;
		try {
			const context = await loadBankContext(reference, detail, abort.signal);
			if (current !== sequence.current) return;
			live.current = context;
			setValue(context);
			return context;
		} catch (failure) {
			if (current === sequence.current) setError(failure);
		} finally {
			if (current === sequence.current) setLoading(false);
		}
	}, [reference, detail, invalidate]);
	useEffect(() => {
		void reload();
		const refresh = () => void reload();
		const visibility = () =>
			document.visibilityState === "visible" ? refresh() : invalidate();
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", invalidate);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			controller.current?.abort();
			sequence.current++;
			live.current = undefined;
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", invalidate);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [reload, invalidate]);
	return {
		value:
			value?.quoteId.toLowerCase() === reference.toLowerCase()
				? value
				: undefined,
		error,
		loading,
		epoch,
		reload,
		invalidate,
		isCurrent: (context: BankContext) => live.current === context,
	};
}
export function BankContextStatus({
	value,
	error,
	loading,
}: {
	value?: BankContext;
	error?: unknown;
	loading: boolean;
}) {
	if (loading)
		return (
			<StateMessage kind="loading">
				<p>Reloading protected bank-request context.</p>
			</StateMessage>
		);
	if (error !== undefined)
		return (
			<StateMessage kind={stateFor(error)}>
				<p>
					{error instanceof RoomRequestError && error.status === 400
						? "A quotation reference is missing or malformed. Continue from your accepted quotation and verification workspace."
						: "Bank context changed, is inaccessible or unavailable. Sign in if needed, then reload. No bank request was confirmed."}
				</p>
			</StateMessage>
		);
	return (
		<p role="status">
			{value
				? bankReadinessReasons[value.reason]
				: "Return to verification to select your quotation."}
		</p>
	);
}
export function BankContinuation({ reference }: { reference: string }) {
	const context = useBankContext(reference);
	return (
		<section aria-label="Bank request handoff">
			<BankContextStatus {...context} />
			{context.value?.eligible && !context.loading && (
				<Link
					className="button button-primary verification-continue"
					prefetch={false}
					href={bankHref(context.value.quoteId)}
					onClick={async (event) => {
						event.preventDefault();
						const current = await context.reload();
						if (current?.eligible)
							window.location.assign(bankHref(current.quoteId));
					}}
				>
					Continue to Bank Request
				</Link>
			)}
			<Button
				variant="secondary"
				disabled={context.loading}
				onClick={() => void context.reload()}
			>
				Reload bank-request eligibility
			</Button>
		</section>
	);
}
export function BankContextSummary({ value }: { value: BankContext }) {
	const s = value.quoteSummary;
	return (
		<section
			className="bank-context-summary"
			aria-label="Selected bank-request quotation"
		>
			<h3>{s.packageName}</h3>
			<dl>
				<dt>Quotation</dt>
				<dd>
					{value.quoteId} · revision {value.quoteRevision}
				</dd>
				<dt>Venue reference</dt>
				<dd>{value.venueId}</dd>
				<dt>Room reference</dt>
				<dd>{value.roomId}</dd>
				<dt>Session</dt>
				<dd>
					{venueTime(s.sessionStart, s.timezone)} –{" "}
					{venueTime(s.sessionEnd, s.timezone)} ({s.timezone})
				</dd>
				<dt>Accepted total</dt>
				<dd>
					{s.currency} {displayMoney(s.totalMinor)}
				</dd>
				<dt>Method</dt>
				<dd>{s.paymentMethod}</dd>
				<dt>Verification readiness</dt>
				<dd>{value.verificationReadiness}</dd>
				<dt>Observed at</dt>
				<dd>{venueTime(value.serverNow, s.timezone)}</dd>
			</dl>
			<p>
				This is a time-sensitive synthetic observation. Creation rechecks
				eligibility; no payment or booking is confirmed.
			</p>
		</section>
	);
}
export function BankRequestCreation({
	reference,
	onCreated,
	onDenied,
}: {
	reference: string;
	onCreated: (value: BankRequest) => void;
	onDenied: () => void;
}) {
	const [confirmedEpoch, setConfirmedEpoch] = useState<number>();
	const [busy, setBusy] = useState(false),
		[message, setMessage] = useState("");
	const [created, setCreated] = useState(false);
	const [unknown, setUnknown] = useState(false);
	const reset = useCallback(() => {
		onDenied();
		setCreated(false);
	}, [onDenied]);
	const context = useBankContext(reference, true, reset);
	const pending = useRef<{ body: string; quoteId: string } | undefined>(
		undefined,
	);
	const confirmed = confirmedEpoch === context.epoch;
	useEffect(() => {
		if (
			context.error instanceof RoomRequestError &&
			[401, 403, 404].includes(context.error.status)
		) {
			pending.current = undefined;
			setUnknown(false);
			setCreated(false);
			onDenied();
		}
	}, [context.error, onDenied]);
	async function send(checked: BankContext) {
		if (!pending.current)
			pending.current = {
				body: JSON.stringify(bankCreationBody(checked, crypto.randomUUID())),
				quoteId: checked.quoteId,
			};
		const command = pending.current;
		try {
			const result = bankCreationResult(
				await roomRequest("/api/bank/requests", {
					method: "POST",
					body: command.body,
				}),
				command.quoteId,
			);
			if (!context.isCurrent(checked)) {
				setUnknown(true);
				return;
			}
			pending.current = undefined;
			setUnknown(false);
			setCreated(true);
			onCreated(result);
		} catch (failure) {
			if (
				failure instanceof RoomRequestError &&
				[400, 401, 403, 404, 409, 413, 415, 429].includes(failure.status)
			) {
				pending.current = undefined;
				setUnknown(false);
				onDenied();
				await context.reload();
				setMessage(
					"The server did not create a confirmed request result. The quotation, verification, hold, session or existing request may have changed. Review current context or open your existing request history.",
				);
			} else {
				setUnknown(true);
				setMessage(
					"The request outcome is unknown. Retry the identical command explicitly or inspect your existing request history. Do not submit another payment.",
				);
			}
		}
	}
	async function submit(retry = false) {
		if (
			busy ||
			(!retry && (!confirmed || !context.value?.eligible || pending.current))
		)
			return;
		const displayed = context.value;
		setBusy(true);
		setMessage("");
		setConfirmedEpoch(undefined);
		try {
			const fresh = await context.reload();
			if (!fresh) return;
			if (retry) {
				if (pending.current && fresh.quoteId === pending.current.quoteId)
					await send(fresh);
				return;
			}
			if (
				!fresh.eligible ||
				!displayed ||
				fresh.quoteRevision !== displayed.quoteRevision ||
				fresh.contentHash !== displayed.contentHash
			) {
				setMessage(
					"Readiness or quotation terms changed. Review the reloaded context and confirm again if eligible.",
				);
				return;
			}
			await send(fresh);
		} finally {
			setBusy(false);
		}
	}
	return (
		<section
			className="bank-ux-panel bank-ux-creation"
			aria-label="Create bank request from verification"
		>
			<h3>Review quotation & request readiness</h3>
			<p>
				Check the current server context before explicitly creating a synthetic
				bank request. Readiness is not a payment result.
			</p>
			<BankContextStatus {...context} />
			{context.value && !context.loading && (
				<BankContextSummary value={context.value} />
			)}
			{!created && !unknown && context.value?.eligible && !context.loading && (
				<>
					<Field
						id="bank-context-confirm"
						label="I reviewed this synthetic quotation and explicitly request creation. No money transfer or booking confirmation is authorized."
						type="checkbox"
						checked={confirmed}
						disabled={busy}
						onChange={(event) =>
							setConfirmedEpoch(
								event.target.checked ? context.epoch : undefined,
							)
						}
					/>
					<Button
						busy={busy}
						disabled={!confirmed || busy}
						onClick={() => void submit()}
					>
						Create synthetic request
					</Button>
				</>
			)}
			{created && (
				<p role="status">
					Synthetic request created. Its authoritative result and existing
					receipt workflow are shown below.
				</p>
			)}
			{message && <p role="alert">{message}</p>}
			{unknown && context.value && (
				<Button
					disabled={busy || context.loading}
					onClick={() => void submit(true)}
				>
					Retry original bank request
				</Button>
			)}
			<Button
				variant="secondary"
				disabled={busy || context.loading}
				onClick={() => {
					setCreated(false);
					setMessage("");
					void context.reload();
				}}
			>
				Reload bank context
			</Button>
			<p>
				<Link href="/verification">Return to verification</Link> ·{" "}
				<Link href="/rooms">Return to room selection</Link> ·{" "}
				<Link href="/bookings">Open your request history</Link>
			</p>
		</section>
	);
}

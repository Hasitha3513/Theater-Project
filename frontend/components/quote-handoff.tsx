"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type HandoffQuote,
	handoffReasons,
	loadHandoffQuote,
	verificationHref,
} from "../lib/quote-handoff";
import { RoomRequestError, stateFor } from "../lib/rooms";
import { Button, StateMessage } from "./primitives";

// Every reload clears protected context first; late responses cannot restore it.
// No client clock or cached quote status determines current eligibility.
export function useQuoteHandoff(reference: string, enabled = true) {
	const [quote, setQuote] = useState<HandoffQuote>();
	const [error, setError] = useState<unknown>();
	const [loading, setLoading] = useState(enabled);
	const [epoch, setEpoch] = useState(0);
	const sequence = useRef(0);
	const controller = useRef<AbortController | undefined>(undefined);
	const invalidate = useCallback(() => {
		controller.current?.abort();
		sequence.current++;
		setQuote(undefined);
		setError(undefined);
		setLoading(true);
		setEpoch((value) => value + 1);
	}, []);
	const reload = useCallback(async () => {
		if (!enabled) return;
		invalidate();
		const current = sequence.current;
		const abort = new AbortController();
		controller.current = abort;
		try {
			const loaded = await loadHandoffQuote(reference, abort.signal);
			if (current !== sequence.current) return;
			setQuote(loaded);
			return loaded;
		} catch (failure) {
			if (current === sequence.current) setError(failure);
		} finally {
			if (current === sequence.current) setLoading(false);
		}
	}, [enabled, reference, invalidate]);
	useEffect(() => {
		if (!enabled) return;
		void reload();
		const refresh = () => void reload();
		const visibility = () => {
			if (document.visibilityState === "visible") refresh();
			else invalidate();
		};
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", invalidate);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			controller.current?.abort();
			sequence.current++;
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", invalidate);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [enabled, reload, invalidate]);
	// Reference binding also protects the render before a changed-reference effect runs.
	return {
		quote:
			quote?.quoteId.toLowerCase() === reference.toLowerCase()
				? quote
				: undefined,
		error,
		loading,
		epoch,
		reload,
	};
}
export function HandoffStatus({
	quote,
	error,
	loading,
}: {
	quote?: HandoffQuote;
	error?: unknown;
	loading: boolean;
}) {
	if (loading)
		return (
			<StateMessage kind="loading">
				<p>Reloading protected quotation context.</p>
			</StateMessage>
		);
	if (error !== undefined) {
		const missing = error instanceof RoomRequestError && error.status === 400;
		return (
			<StateMessage kind={missing ? "empty" : stateFor(error)}>
				<p>
					{missing
						? "Quotation reference is missing or malformed. Select an accepted quotation, or enter its owned reference below."
						: "Quotation unavailable or inaccessible. Sign in if needed, then reload. No verification or payment was submitted."}
				</p>
			</StateMessage>
		);
	}
	if (!quote)
		return (
			<StateMessage kind="empty">
				<p>Select an accepted quotation to continue.</p>
			</StateMessage>
		);
	return (
		<p role="status">{handoffReasons[quote.verificationHandoff.reason]}</p>
	);
}
export function QuoteContinuation({
	reference,
	onDenied,
}: {
	reference: string;
	onDenied?: () => void;
}) {
	const context = useQuoteHandoff(reference);
	useEffect(() => {
		if (
			context.error instanceof RoomRequestError &&
			[401, 403, 404].includes(context.error.status)
		)
			onDenied?.();
	}, [context.error, onDenied]);
	return (
		<section aria-label="Verification handoff">
			<HandoffStatus {...context} />
			{context.quote?.verificationHandoff.eligible && !context.loading && (
				<Link
					className="button button-primary verification-continue"
					prefetch={false}
					href={verificationHref(context.quote.quoteId)}
					onClick={async (event) => {
						event.preventDefault();
						const current = await context.reload();
						if (current?.verificationHandoff.eligible)
							window.location.assign(verificationHref(current.quoteId));
					}}
				>
					Continue to Verification
				</Link>
			)}
			<Button disabled={context.loading} onClick={() => void context.reload()}>
				Reload verification eligibility
			</Button>
		</section>
	);
}

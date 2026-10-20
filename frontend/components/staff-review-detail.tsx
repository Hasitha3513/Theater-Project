"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import type { BankRequest } from "../lib/bank";
import type { Verification } from "../lib/verification";
import { stateFor } from "../lib/rooms";
import {
	loadStaffReview,
	reviewQueueHref,
	type ReviewKind,
} from "../lib/staff-review";
import { BankNotice, BankSummary } from "./bank";
import { VerificationNotice, VerificationSummary } from "./verification";
import { Button, Card, StateMessage, type StateKind } from "./primitives";
export function StaffReviewDetail({
	kind,
	venue,
	request,
}: {
	kind: ReviewKind;
	venue: string;
	request: string;
}) {
	const [value, setValue] = useState<Verification | BankRequest>();
	const [busy, setBusy] = useState(true);
	const [error, setError] = useState<StateKind>();
	const generation = useRef(0);
	const controller = useRef<AbortController | undefined>(undefined);
	const clear = useCallback(() => {
		generation.current++;
		controller.current?.abort();
		setValue(undefined);
	}, []);
	const reload = useCallback(async () => {
		clear();
		setBusy(true);
		setError(undefined);
		const epoch = generation.current;
		const read = new AbortController();
		controller.current = read;
		try {
			const current = await loadStaffReview(kind, venue, request, read.signal);
			if (epoch === generation.current) setValue(current);
		} catch (e) {
			if (epoch === generation.current) setError(stateFor(e));
		} finally {
			if (epoch === generation.current) setBusy(false);
		}
	}, [kind, venue, request, clear]);
	useEffect(() => {
		void reload();
		const refresh = () => {
			void reload();
		};
		const hide = () => {
			clear();
			setBusy(false);
			setError("unauthenticated");
		};
		const visibility = () =>
			document.visibilityState === "hidden" ? hide() : refresh();
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", hide);
		window.addEventListener("cinenest-session-change", hide);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			clear();
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", hide);
			window.removeEventListener("cinenest-session-change", hide);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [reload, clear]);
	let queue = `/admin/${kind}`;
	try {
		queue = reviewQueueHref(kind, venue);
	} catch {
		/* Recovery without reflecting malformed references. */
	}
	return (
		<Card
			title={
				kind === "verification"
					? "Exact verification review details"
					: "Exact bank review details"
			}
		>
			{kind === "verification" ? <VerificationNotice /> : <BankNotice />}
			<p>
				Read-only synthetic review context. Opening this page records no review
				decision, verifies no funds and confirms no booking. Use the existing
				queue workspace for explicit authorized review actions.
			</p>
			<div className="toolbar staff-detail-actions">
				<Link href={queue} prefetch={false}>
					Return to review queue
				</Link>
				<Button disabled={busy} onClick={() => void reload()}>
					Reload protected review details
				</Button>
			</div>
			{busy && (
				<StateMessage kind="loading">
					<p>Loading current protected review context.</p>
				</StateMessage>
			)}
			{error && (
				<StateMessage kind={error}>
					<p>
						{error === "unauthenticated"
							? "Session unavailable. Sign in again, then reload."
							: error === "denied"
								? "Current venue review permission is required. No protected details are shown."
								: "Review unavailable, missing, withdrawn or changed; check the queue or reload. No decision or financial outcome is inferred."}
					</p>
				</StateMessage>
			)}
			{value && !busy && !error && (
				<section
					aria-label="Server-authoritative review context"
					style={{ overflowWrap: "anywhere" }}
				>
					<section
						className="staff-review-status"
						aria-label="Current server review status"
					>
						<h3>Current review status</h3>
						{kind === "verification" ? (
							<p>
								Digital test status: {(value as Verification).state} ·{" "}
								{(value as Verification).superseded
									? "SUPERSEDED"
									: "Current request generation"}
							</p>
						) : (
							<dl className="account-summary">
								<div>
									<dt>Request</dt>
									<dd>{(value as BankRequest).requestState}</dd>
								</div>
								<div>
									<dt>Receipt review</dt>
									<dd>{(value as BankRequest).reviewState}</dd>
								</div>
								<div>
									<dt>Inventory</dt>
									<dd>{(value as BankRequest).inventoryState}</dd>
								</div>
							</dl>
						)}
						<p>
							Server venue: {value.venueId}. A status observation is not
							permission to decide, verify funds or confirm a booking.
						</p>
					</section>
					<p>Request reference: {value.id}</p>
					<p>Venue reference: {value.venueId}</p>
					<p>Current server revision: {value.revision}</p>
					{kind === "bank" ? (
						<BankSummary value={value as BankRequest} />
					) : (
						<VerificationSummary value={value as Verification} />
					)}
					<p>
						Processed, rejected, expired or superseded status does not authorize
						another decision. Reload before returning to the existing review
						controls.
					</p>
				</section>
			)}
		</Card>
	);
}

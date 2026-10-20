"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import type { BankRequest } from "../lib/bank";
import { bankEvidenceHref, loadBankRequest } from "../lib/bank-request";
import { quoteReference } from "../lib/bank-handoff";
import { RoomRequestError } from "../lib/rooms";
import { BankNotice, BankSummary } from "./bank";
import { Button, Card } from "./primitives";

export function BankRequestDetail({ reference }: { reference: string }) {
	const [value, setValue] = useState<BankRequest>();
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const generation = useRef(0);
	const pending = useRef<AbortController | undefined>(undefined);
	const invalidate = useCallback(() => {
		generation.current++;
		pending.current?.abort();
		setValue(undefined);
	}, []);
	const load = useCallback(async () => {
		invalidate();
		const token = generation.current;
		setError("");
		if (!quoteReference.test(reference)) {
			setBusy(false);
			setError("Choose a valid bank request from your customer history.");
			return;
		}
		const controller = new AbortController();
		pending.current = controller;
		setBusy(true);
		try {
			const next = await loadBankRequest(reference, controller.signal);
			if (token === generation.current) setValue(next);
		} catch (failure) {
			if (token !== generation.current) return;
			setError(
				failure instanceof RoomRequestError && failure.status === 401
					? "Your session is unavailable. Return to your account, then reopen your history."
					: failure instanceof RoomRequestError &&
							[403, 404].includes(failure.status)
						? "This bank request is unavailable to your current account. Return to your history."
						: "Current bank request could not be loaded. No financial outcome has been established. Retry or return to history.",
			);
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}, [reference, invalidate]);
	useEffect(() => {
		void load();
		const refresh = () => {
			void load();
		};
		const hide = () => {
			invalidate();
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
			generation.current++;
			pending.current?.abort();
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", hide);
			window.removeEventListener("cinenest-session-change", hide);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [load, invalidate]);
	return (
		<section
			aria-label="Owned bank request detail"
			aria-live="polite"
			className="bank-context-summary"
		>
			<BankNotice />
			<Card title="Bank request details">
				<p>
					Read-only synthetic context. A receipt claim or completed review is
					not verified funds. No real transfer or commercial booking
					confirmation.
				</p>
				{busy && <p role="status">Loading current protected request…</p>}
				{error && <p role="alert">{error}</p>}
				{value && (
					<>
						<h3>Request {value.id}</h3>
						<p>Current server revision: {value.revision}</p>
						<p>
							{value.reviewState === "NEEDS_INFORMATION" ||
							value.reviewState === "REJECTED"
								? "Review requires attention. This read-only view does not submit a correction."
								: value.reviewState === "REVIEWED"
									? "Receipt review recorded; independent credit verification and allocation are separate."
									: "Consult the current request, review and financial states below; no financial success is inferred."}
						</p>
						<BankSummary value={value} customer />
						<p>
							<a href={bankEvidenceHref(value.id)}>
								Open synthetic claim and receipt workflow
							</a>
						</p>
						<p>
							Submission remains a separate explicit action. Server checks
							determine whether current claims or corrections are permitted.
						</p>
						{!value.financial && (
							<p>
								Financial outcome is unavailable or unresolved. Do not infer
								payment or booking success.
							</p>
						)}
					</>
				)}
				<Button busy={busy} variant="secondary" onClick={() => void load()}>
					Reload current request
				</Button>
				<p>
					<a href="/bookings">Return to customer history</a> ·{" "}
					<a href="/account">Account and session</a>
				</p>
			</Card>
		</section>
	);
}

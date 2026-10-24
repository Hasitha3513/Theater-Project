"use client";
import { useEffect, useRef, useState } from "react";
import {
	extensionMoney,
	extensionNotice,
	extensionReasons,
	ExtensionError,
	requestExtension,
	type ExtensionResult,
	type ExtensionCommand,
} from "../lib/extension-preview";
import { Badge, Button, Card, Field, StateMessage } from "./primitives";
export type ExtensionBooking = {
	id: string;
	venueId: string;
	revision: number;
	state?: string;
	syntheticBookingStatus?: string;
	purchase: {
		name: string;
		roomId: string;
		packageVersionId: string;
		sessionStart: string;
		sessionEnd: string;
		timezone: string;
		amountMinor: string;
		currency: string;
	};
};
function local(value: string, zone: string) {
	return new Intl.DateTimeFormat("en-GB", {
		dateStyle: "medium",
		timeStyle: "short",
		timeZone: zone,
	}).format(new Date(value));
}
export function ExtensionPriceSummary({
	value: v,
	booking: b,
}: {
	value: ExtensionResult;
	booking: ExtensionBooking;
}) {
	if (v.additionalMinor === undefined || v.additionalMinutes === undefined) return null;
 const p = b.purchase,
		end = new Date(
			Date.parse(p.sessionEnd) + (v.additionalMinutes ?? 0) * 60000,
		).toISOString();
	return (
		<div className="extension-price-summary">
			<h3>Additional time preview</h3>
			<dl className="account-summary extension-summary-grid">
				<div>
					<dt>Purchased duration</dt>
					<dd>{v.currentDurationMinutes} minutes</dd>
				</div>
				<div>
					<dt>Additional duration</dt>
					<dd>{v.additionalMinutes} minutes</dd>
				</div>
				<div>
					<dt>Proposed duration</dt>
					<dd>{v.proposedDurationMinutes} minutes</dd>
				</div>
				<div>
					<dt>Current session end</dt>
					<dd>
						<time dateTime={p.sessionEnd}>
							{local(p.sessionEnd, p.timezone)}
						</time>
					</dd>
				</div>
				<div>
					<dt>Proposed session end — calculated display</dt>
					<dd>
						<time dateTime={end}>{local(end, p.timezone)}</time>
					</dd>
				</div>
				<div>
					<dt>Existing purchased price</dt>
					<dd>{extensionMoney(p.amountMinor)}</dd>
				</div>
				<div>
					<dt>Additional extension price</dt>
					<dd>
						<strong>{extensionMoney(v.additionalMinor)}</strong>
					</dd>
				</div>
			</dl>
			<p>
				The proposed end adds the returned elapsed minutes to your purchased
				end, in {p.timezone}. It is not a committed session change. No total
				price or preview expiry is provided by this API.
			</p>
		</div>
	);
}
export function BookingExtensionPreview({
	booking: b,
}: {
	booking: ExtensionBooking;
}) {
	const [loadedIdentity, setLoadedIdentity] = useState(""),
		[value, setValue] = useState<ExtensionResult>(),
		[amount, setAmount] = useState(""),
		[busy, setBusy] = useState(false),
		[error, setError] = useState<number>();
	const epoch = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		running = useRef(false),
		owner = useRef<string | undefined>(undefined),
		scope = useRef("");
	const identity = JSON.stringify([
		b.id,
		b.venueId,
		b.revision,
		b.state,
		b.syntheticBookingStatus,
		b.purchase,
	]);
	scope.current = identity;
	useEffect(() => {
		const clear = () => {
			if (scope.current !== identity) return;
			epoch.current++;
			controller.current?.abort();
			running.current = false;
			owner.current = undefined;
			setValue(undefined);
			setAmount("");
			setError(undefined);
			setBusy(false);
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
	const confirmed =
		(b.state ?? b.syntheticBookingStatus) === "SIMULATED_CONFIRMED";
	const units = value?.durationUnits,
		n = Number(amount),
		valid =
			!!units &&
			/^[1-9][0-9]*$/.test(amount) &&
			Number.isSafeInteger(n) &&
			n >= units.minimumAdditionalMinutes &&
			n <= units.maximumAdditionalMinutes &&
			n % units.incrementMinutes === 0;
	async function run(command?: ExtensionCommand) {
		if (running.current || !confirmed) return;
		running.current = true;
		const generation = ++epoch.current,
			c = new AbortController();
		controller.current?.abort();
		controller.current = c;
		setBusy(true);
		setError(undefined);
		setValue(undefined);
		try {
			const response = await requestExtension(
				b.id,
				AbortSignal.any([c.signal, AbortSignal.timeout(15000)]),
				command,
				owner.current,
			);
			if (
				c.signal.aborted ||
				generation !== epoch.current ||
				scope.current !== identity
			)
				return;
			const v = response.value;
			if (
				v.purchasedRevision !== b.revision ||
				(command &&
					(v.packageVersionId !== b.purchase.packageVersionId ||
						v.currentDurationMinutes !==
							(Date.parse(b.purchase.sessionEnd) -
								Date.parse(b.purchase.sessionStart)) /
								60000))
			)
				throw new ExtensionError(409);
			owner.current = response.account;
			setLoadedIdentity(identity);
			setValue(v);
			setAmount(
				command
					? String(command.additionalMinutes)
					: String(v.durationUnits?.minimumAdditionalMinutes ?? ""),
			);
		} catch (e) {
			if (
				c.signal.aborted ||
				generation !== epoch.current ||
				scope.current !== identity
			)
				return;
			owner.current = undefined;
			setValue(undefined);
			setError(e instanceof ExtensionError ? e.status : 503);
		} finally {
			if (generation === epoch.current) {
				running.current = false;
				setBusy(false);
			}
		}
	}
	return (
		<Card title="Explore extra cinema time" className="extension-preview-panel">
			<Badge tone="info">Synthetic preview only</Badge>
			<p role="note">{extensionNotice}</p>
			<p>
				Review your purchased terms before checking additional time. Overlapping
				inventory is not checked by this preview; no time is held or guaranteed.
				Occupancy remains UNKNOWN and physical readiness UNVERIFIED.
			</p>
			<details>
				<summary>Booking context for this preview</summary>
				<dl className="account-summary extension-summary-grid">
					<div>
						<dt>Booking reference</dt>
						<dd>{b.id}</dd>
					</div>
					<div>
						<dt>Venue / room references</dt>
						<dd>
							{b.venueId} / {b.purchase.roomId}
						</dd>
					</div>
					<div>
						<dt>Purchased package</dt>
						<dd>{b.purchase.name}</dd>
					</div>
					<div>
						<dt>Booking status</dt>
						<dd>{b.state ?? b.syntheticBookingStatus}</dd>
					</div>
					<div>
						<dt>Purchased session start</dt>
						<dd>{local(b.purchase.sessionStart, b.purchase.timezone)}</dd>
					</div>
					<div>
						<dt>Current session end</dt>
						<dd>{local(b.purchase.sessionEnd, b.purchase.timezone)}</dd>
					</div>
					<div>
						<dt>Venue timezone</dt>
						<dd>{b.purchase.timezone}</dd>
					</div>
				</dl>
			</details>
			<Button disabled={busy || !confirmed} onClick={() => void run()}>
				Check Extension Options
			</Button>
			{!confirmed && (
				<p>Extension preview is unavailable for this booking status.</p>
			)}
			{busy && (
				<StateMessage kind="loading">
					<p>
						Checking current purchased terms. No extension is being reserved.
					</p>
				</StateMessage>
			)}
			{error && (
				<StateMessage
					kind={
						error === 401
							? "unauthenticated"
							: error === 403
								? "denied"
								: error === 404
									? "not-found"
									: error === 409
										? "conflict"
										: error === 400
											? "validation"
											: "unavailable"
					}
				>
					<p>
						{error === 401
							? "Your session changed or expired. Sign in and reload this booking."
							: error === 403
								? "Extension preview is not authorized for this account."
								: error === 404
									? "This booking is unavailable in your current account."
									: error === 409
										? "The booking or room context changed, or the requested time is no longer eligible. Reload the booking and check again."
										: error === 400
											? "The requested additional duration is invalid. Check options again."
											: "Extension preview could not be retrieved or timed out. Protected preview details have been cleared. Use Check Extension Options to retry explicitly."}
					</p>
				</StateMessage>
			)}
			{value && loadedIdentity === identity && (
				<div role="status" aria-live="polite">
					<p>{extensionReasons[value.reason]}</p>
					<p>
						Observed{" "}
						<time dateTime={value.observedAt}>
							{local(value.observedAt, b.purchase.timezone)}
						</time>{" "}
						({b.purchase.timezone}). Eligibility is rechecked for each preview.
					</p>
					{value.eligible && units && (
						<form
							onSubmit={(e) => {
								e.preventDefault();
								if (valid && !busy)
									void run({
										additionalMinutes: n,
										expectedPurchasedRevision: value.purchasedRevision,
										expectedLifecycleVersion: value.lifecycleVersion,
									});
							}}
						>
							<Field
								id={`extension-minutes-${b.id}`}
								label="Additional duration (minutes)"
								type="number"
								min={units.minimumAdditionalMinutes}
								max={units.maximumAdditionalMinutes}
								step={units.incrementMinutes}
								value={amount}
								required
								disabled={busy}
								hint={`Purchased terms support ${units.incrementMinutes}-minute increments, from ${units.minimumAdditionalMinutes} to ${units.maximumAdditionalMinutes} minutes. These options do not guarantee inventory availability.`}
								onChange={(e) => {
									setAmount(e.target.value);
									if (value.additionalMinutes !== undefined)
										setValue({
											...value,
											additionalMinutes: undefined,
											additionalMinor: undefined,
											currentDurationMinutes: undefined,
											proposedDurationMinutes: undefined,
										});
								}}
							/>
							<Button type="submit" disabled={busy || !valid}>
								Preview Additional Price
							</Button>
						</form>
					)}
					{value.additionalMinor !== undefined && (
						<ExtensionPriceSummary value={value} booking={b} />
					)}
				</div>
			)}
		</Card>
	);
}

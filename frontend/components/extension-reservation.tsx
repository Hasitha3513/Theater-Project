"use client";
import { useEffect, useRef, useState } from "react";
import { Button, Field, StateMessage, Badge } from "./primitives";
import type { ExtensionBooking } from "./booking-extension-preview";
import {
	ExtensionError,
	extensionMoney,
	type ExtensionResult,
} from "../lib/extension-preview";
import { venueTime } from "../lib/inventory";
import { calendarPath, validateCalendar } from "../lib/customer-calendar";
import { cancellationUuid as uuid } from "../lib/booking-cancellation";
import {
	createFromPreview,
	reservationAccount,
	reservationFetch,
	reservationPath,
	reservationResult,
	recoveryResult,
	type Reservation,
	type WithdrawReservation,
} from "../lib/extension-reservation";
type Reference = {
	commandKey: string;
	reservationId?: string;
	withdrawal?: WithdrawReservation;
};
const storageKey = (account: string, booking: string) =>
	`cinenest.extension.reference.v1:${account}:${booking}`;
export function reservationReference(
	raw: string | null,
): Reference | undefined {
	if (!raw) return;
	try {
		const r = JSON.parse(raw);
		if (
			!r ||
			Object.keys(r).some(
				(k) => !["commandKey", "reservationId", "withdrawal"].includes(k),
			) ||
			!uuid.test(r.commandKey) ||
			(r.reservationId && !uuid.test(r.reservationId))
		)
			return;
		if (
			r.withdrawal &&
			(!uuid.test(r.withdrawal.commandKey) ||
				!Number.isSafeInteger(r.withdrawal.expectedRevision) ||
				r.withdrawal.expectedRevision < 0 ||
				Object.keys(r.withdrawal).sort().join() !==
					"commandKey,expectedRevision")
		)
			return;
		return {
			commandKey: r.commandKey,
			...(r.reservationId ? { reservationId: r.reservationId } : {}),
			...(r.withdrawal ? { withdrawal: r.withdrawal } : {}),
		};
	} catch {
		return;
	}
}
export function ExtensionReservationPanel({
	booking: b,
	preview,
}: {
	booking: ExtensionBooking;
	preview?: ExtensionResult;
}) {
	const [value, setValue] = useState<Reservation>(),
		[message, setMessage] = useState(""),
		[error, setError] = useState<number>(),
		[busy, setBusy] = useState(false),
		[consent, setConsent] = useState(false),
		[withdrawConsent, setWithdrawConsent] = useState(false),
		[configuration, setConfiguration] = useState<string>(),
		[configurationFor, setConfigurationFor] = useState<string>(),
		[ready, setReady] = useState(false),
		[hasReference, setHasReference] = useState(false),
		[loaded, setLoaded] = useState("");
	const epoch = useRef(0),
		control = useRef<AbortController | undefined>(undefined),
		running = useRef(false),
		scope = useRef(""),
		owner = useRef<string | undefined>(undefined),
		reference = useRef<Reference | undefined>(undefined);
	const identity = JSON.stringify([
		b.id,
		b.venueId,
		b.revision,
		b.state,
		b.syntheticBookingStatus,
		b.purchase,
		b.amendmentPlanning,
	]);
	scope.current = identity;
	const previewIdentity = JSON.stringify(preview);
	const prepared =
		configurationFor === previewIdentity && loaded === identity
			? configuration
			: undefined;
	const lastPreview = useRef(previewIdentity),
		renderedPreview = useRef(previewIdentity),
		actions = useRef({ clear: () => {}, load: () => {} });
	renderedPreview.current = previewIdentity;
	useEffect(() => {
		if (lastPreview.current === previewIdentity) return;
		lastPreview.current = previewIdentity;
		epoch.current++;
		control.current?.abort();
		running.current = false;
		setBusy(false);
		setConfiguration(undefined);
		setConsent(false);
	}, [previewIdentity]);
	function clear() {
		epoch.current++;
		control.current?.abort();
		running.current = false;
		owner.current = undefined;
		reference.current = undefined;
		setValue(undefined);
		setConfiguration(undefined);
		setConsent(false);
		setWithdrawConsent(false);
		setBusy(false);
		setReady(false);
		setHasReference(false);
		setLoaded("");
		setMessage("");
		setError(undefined);
	}
	function save(r: Reference) {
		if (!owner.current) throw new ExtensionError(401);
		sessionStorage.setItem(storageKey(owner.current, b.id), JSON.stringify(r));
		reference.current = r;
		setHasReference(true);
	}
	function forget() {
		if (owner.current)
			sessionStorage.removeItem(storageKey(owner.current, b.id));
		reference.current = undefined;
		setHasReference(false);
	}
	async function run(action: "load" | "prepare" | "create" | "withdraw") {
		if (running.current) return;
		running.current = true;
		const generation = ++epoch.current,
			c = new AbortController();
		control.current?.abort();
		control.current = c;
		const signal = AbortSignal.any([c.signal, AbortSignal.timeout(15000)]);
		const current = () => {
			signal.throwIfAborted();
			if (
				generation !== epoch.current ||
				scope.current !== identity ||
				((action === "prepare" || action === "create") &&
					renderedPreview.current !== previewIdentity)
			)
				throw new DOMException("Context changed", "AbortError");
		};
		setBusy(true);
		setError(undefined);
		setMessage("");
		setValue(undefined);
		setWithdrawConsent(false);
		let posted = false;
		try {
			const account = await reservationAccount(signal);
			current();
			if (owner.current && account !== owner.current)
				throw new ExtensionError(401);
			owner.current = account;
			const stored = reservationReference(
				sessionStorage.getItem(storageKey(account, b.id)),
			);
			reference.current = stored;
			setHasReference(!!stored);
			setLoaded(identity);
			const readStatus = async (id: string) => {
				const raw = await reservationFetch(
					reservationPath(b.id, id),
					signal,
					account,
				);
				current();
				const result = reservationResult(raw, b.id, id);
				current();
				if (
					result.packageVersionId !== b.purchase.packageVersionId ||
					result.originalSession.start !== b.purchase.sessionStart ||
					result.originalSession.end !== b.purchase.sessionEnd
				)
					throw new ExtensionError(409);
				setValue(result);
				return result;
			};
			const recover = async () => {
				const r = reference.current;
				if (!r) return;
				if (!r.reservationId) {
					const raw = await reservationFetch(
						reservationPath(b.id, r.commandKey, "recover"),
						signal,
						account,
					);
					current();
					const recovered = recoveryResult(raw, b.id, r.commandKey);
					save({ ...r, reservationId: recovered.reservationId });
				}
				const result = await readStatus(
					reference.current?.reservationId as string,
				);
				if (reference.current?.withdrawal) {
					if (result.state !== "ACTIVE") {
						save({
							commandKey: reference.current.commandKey,
							reservationId: result.reservationId,
						});
						setMessage(
							"Current terminal reservation state loaded. This does not prove which earlier request caused it.",
						);
					} else
						setMessage(
							"Withdrawal outcome is unresolved. Current state remains ACTIVE; reload or explicitly retry the original withdrawal command.",
						);
				}
			};
			if (action === "load") {
				await recover();
				current();
				setReady(true);
				if (!reference.current)
					setMessage(
						"No saved reservation reference in this browser session. No reservation lookup by other customers or automatic creation occurs.",
					);
			} else if (action === "prepare") {
				if (stored) throw new ExtensionError(409);
				const guests = b.amendmentPlanning?.guests;
				if (
					!preview?.eligible ||
					preview.additionalMinor === undefined ||
					!preview.proposedDurationMinutes ||
					!Number.isSafeInteger(guests) ||
					!guests ||
					guests < 1 ||
					guests > 2147483647
				)
					throw new ExtensionError(409);
				const parts = new Intl.DateTimeFormat("en-CA", {
					timeZone: b.purchase.timezone,
					year: "numeric",
					month: "2-digit",
					day: "2-digit",
				}).formatToParts(new Date(b.purchase.sessionStart));
				const part = (t: string) => parts.find((p) => p.type === t)?.value;
				const context = {
					roomId: b.purchase.roomId,
					venueId: b.venueId,
					date: `${part("year")}-${part("month")}-${part("day")}`,
					durationMinutes: preview.proposedDurationMinutes,
					guests,
				};
				const raw = await reservationFetch(
					calendarPath(context),
					signal,
					account,
				);
				current();
				const calendar = validateCalendar(raw, context);
				if (calendar.timezone !== b.purchase.timezone)
					throw new ExtensionError(409);
				current();
				setConfiguration(calendar.configuration);
				setConfigurationFor(previewIdentity);
				setReady(true);
				setConsent(false);
				setMessage(
					"Current room configuration checked. Calendar cells are not extension eligibility; the server checks additional inventory on explicit reservation.",
				);
			} else if (action === "create") {
				if (stored || !preview || !prepared || !consent || !ready)
					throw new ExtensionError(409);
				const command = createFromPreview(
					preview,
					prepared,
					crypto.randomUUID(),
				);
				save({ commandKey: command.commandKey });
				setConfiguration(undefined);
				setConsent(false);
				posted = true;
				let committed = false;
				try {
					const raw = await reservationFetch(
						reservationPath(b.id),
						signal,
						account,
						command,
					);
					current();
					const result = reservationResult(raw, b.id);
					committed = true;
					save({
						commandKey: command.commandKey,
						reservationId: result.reservationId,
					});
					await readStatus(result.reservationId);
					setMessage(
						"Temporary reservation loaded from the server. Your purchased booking is unchanged.",
					);
				} catch (e) {
					current();
					if (
						!committed &&
						e instanceof ExtensionError &&
						[400, 409, 429].includes(e.status)
					) {
						forget();
						throw e;
					}
					setMessage(
						"Creation response is uncertain. Checking the original command reference; no replacement request will be sent.",
					);
					await recover();
				}
			} else {
				const r = stored;
				if (!r?.reservationId || !withdrawConsent || !value)
					throw new ExtensionError(409);
				const command = r.withdrawal ?? {
					expectedRevision: value.proposalRevision,
					commandKey: crypto.randomUUID(),
				};
				save({ ...r, withdrawal: command });
				posted = true;
				try {
					const raw = await reservationFetch(
						reservationPath(b.id, r.reservationId, "withdraw"),
						signal,
						account,
						command,
					);
					current();
					reservationResult(raw, b.id, r.reservationId);
				} catch (e) {
					current();
					if (
						e instanceof ExtensionError &&
						[400, 409, 429].includes(e.status)
					) {
						save({ commandKey: r.commandKey, reservationId: r.reservationId });
						throw e;
					}
					setMessage(
						"Withdrawal response is uncertain. Reloading server state; no success is assumed.",
					);
				}
				await recover();
			}
			current();
		} catch (e) {
			if (
				c.signal.aborted ||
				generation !== epoch.current ||
				scope.current !== identity
			)
				return;
			setValue(undefined);
			setConfiguration(undefined);
			setConsent(false);
			setWithdrawConsent(false);
			setError(e instanceof ExtensionError ? e.status : 503);
			if (reference.current)
				setMessage(
					posted || reference.current.withdrawal
						? "Outcome unknown. Keep the original reference and reload status; never create another reservation to resolve uncertainty."
						: "Saved reference could not be resolved. A missing receipt does not prove creation failed. Reload explicitly; no new CREATE is sent.",
				);
			if (e instanceof ExtensionError && [401, 403].includes(e.status)) {
				setReady(false);
				owner.current = undefined;
				reference.current = undefined;
				setHasReference(false);
			}
		} finally {
			if (generation === epoch.current && scope.current === identity) {
				running.current = false;
				setBusy(false);
			}
		}
	}
	actions.current = { clear, load: () => void run("load") };
	useEffect(() => {
		if (scope.current !== identity) return;
		actions.current.clear();
		actions.current.load();
		const reset = () => actions.current.clear();
		for (const e of ["cinenest-session-change", "pagehide"])
			window.addEventListener(e, reset);
		return () => {
			actions.current.clear();
			for (const e of ["cinenest-session-change", "pagehide"])
				window.removeEventListener(e, reset);
		};
	}, [identity]);

	const shown = loaded === identity ? value : undefined;
	return (
		<section
			aria-label="Extension reservation"
			className="extension-reservation-panel"
		>
			<h3>Reserve additional cinema time</h3>
			<Badge tone="info">Temporary synthetic reservation only</Badge>
			<p>
				Your original purchased booking stays unchanged. No payment is
				collected, no extension is purchased and no access credential is
				extended. Occupancy UNKNOWN; physical readiness UNVERIFIED.
			</p>
			<p>
				A reservation lasts at most 10 minutes and never beyond your original
				session start. The server assigns the exact deadline; no automatic
				renewal occurs.
			</p>
			<Button disabled={busy} onClick={() => void run("load")}>
				Reload Extension Reservation
			</Button>
			{busy && (
				<StateMessage kind="loading">
					<p>Checking the current protected reservation context.</p>
				</StateMessage>
			)}
			{message && <p role="status">{message}</p>}
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
							? "Your session expired or changed. Sign in and reload this booking."
							: error === 403
								? "This account is not authorized for the reservation."
								: error === 404
									? "The protected booking, reservation or committed reference is unavailable. A missing recovery receipt leaves creation unresolved."
									: error === 409
										? "The booking, price, room inventory or revision changed, or an active reservation already exists. Reload before another explicit action."
										: error === 429
											? "The server request limit was reached. Wait before retrying explicitly."
											: "The reservation service is unavailable or the response could not be verified. Reload to recover safely."}
					</p>
				</StateMessage>
			)}
			{!hasReference && preview?.additionalMinor !== undefined && (
				<div>
					<p>
						Selected additional time: {preview.additionalMinutes} minutes ·{" "}
						{extensionMoney(preview.additionalMinor)}. Review the proposed end
						in the price preview above.
					</p>
					<Button disabled={busy || !ready} onClick={() => void run("prepare")}>
						Review Extension Reservation
					</Button>
					{prepared && (
						<>
							<Field
								id={`extension-reserve-consent-${b.id}`}
								type="checkbox"
								label="I understand this reserves temporary time only; my purchased booking and payments remain unchanged."
								checked={consent}
								onChange={(e) => setConsent(e.target.checked)}
							/>
							<Button
								disabled={busy || !consent}
								onClick={() => void run("create")}
							>
								Reserve Extension Time
							</Button>
						</>
					)}
				</div>
			)}
			{shown && (
				<div>
					<h4>Current reservation</h4>
					<p>
						Times shown in {b.purchase.timezone}; deadlines and state are server
						observations.
					</p>
					<Badge tone="info">
						{shown.state}
						{shown.pendingReconciliation ? " · PENDING_RECONCILIATION" : ""}
					</Badge>
					<dl className="account-summary extension-summary-grid">
						{[
							["Additional duration", `${shown.additionalMinutes} minutes`],
							["Additional price", extensionMoney(shown.additionalMinor)],
							["Proposed end", shown.proposedSession.end],
							["Created", shown.createdAt],
							["Expires", shown.expiresAt],
							["Observed", shown.observedAt],
							["Reservation revision", String(shown.proposalRevision)],
						].map(([k, v]) => (
							<div key={k}>
								<dt>{k}</dt>
								<dd>
									{["Proposed end", "Created", "Expires", "Observed"].includes(
										k,
									) ? (
										<time dateTime={v}>
											{venueTime(v, b.purchase.timezone)}
										</time>
									) : (
										v
									)}
								</dd>
							</div>
						))}
					</dl>
					<p>
						{shown.effective
							? "The server observed effective temporary coverage. This is not purchased extension time."
							: "No effective temporary coverage is established by this observation. Pending reconciliation is not availability."}
					</p>
					<details>
						<summary>Reservation references</summary>
						<p>Booking: {shown.bookingId}</p>
						<p>Reservation: {shown.reservationId}</p>
					</details>
					{shown.state === "ACTIVE" && (
						<>
							<Field
								id={`extension-withdraw-consent-${b.id}`}
								type="checkbox"
								label="I explicitly request withdrawal of this temporary extension reservation."
								checked={withdrawConsent}
								onChange={(e) => setWithdrawConsent(e.target.checked)}
							/>
							<Button
								disabled={busy || !withdrawConsent}
								onClick={() => void run("withdraw")}
							>
								Withdraw Extension Reservation
							</Button>
						</>
					)}
				</div>
			)}
		</section>
	);
}

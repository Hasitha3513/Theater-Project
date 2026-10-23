export const cancellationUuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/;
export const cancellationPolicy = "SYNTHETIC_PRESTART_CANCEL_V1";
export type CancellationContext = { bookingId: string; venueId?: string };
export type CancellationCommand = {
	reason: string;
	commandKey: string;
	expectedPurchasedRevision: number;
	expectedLifecycleVersion: number;
};
export type CancellationResult = {
	bookingId: string;
	venueId: string;
	state: "SIMULATED_CONFIRMED" | "SIMULATED_CANCELLED";
	lifecycleVersion: number;
	policyVersion: string;
	synthetic: true;
	realRepayment: false;
	physicalReadiness: "UNVERIFIED";
	eligible: boolean;
	reason: string;
	purchasedRevision?: number;
	sessionStart?: string;
	refundPrincipalMinor?: string;
	currency?: string;
	refundStatus: "NO_CANCELLATION_OBLIGATION" | "RESERVED_REVIEW_REQUIRED";
	cancellationId?: string;
	refundObligationId?: string;
	inventoryReleased?: true;
	accessState?: "NOT_ISSUED";
	resolution?: "FINANCIAL_REVIEW_REQUIRED";
};
export class CancellationError extends Error {
	constructor(public status: number) {
		super("Cancellation unavailable;check current status before retrying.");
	}
}
export function cancellationPath(c: CancellationContext, command = false) {
	if (
		!cancellationUuid.test(c.bookingId) ||
		(c.venueId !== undefined && !cancellationUuid.test(c.venueId))
	)
		throw new CancellationError(400);
	return `${c.venueId ? `/api/admin/venues/${c.venueId}` : "/api"}/bookings/${c.bookingId}/${command ? "cancellations" : "cancellation"}`;
}
export function validCancellationReason(value: unknown): value is string {
	return (
		typeof value === "string" &&
		value.trim().length > 0 &&
		value.length <= 240 &&
		!Array.from(value).some((character) => {
			const code = character.codePointAt(0) ?? 0;
			return code < 32 || (code >= 127 && code <= 159);
		})
	);
}
export function cancellationCommand(raw: unknown): CancellationCommand {
	const v = raw as CancellationCommand;
	if (
		!v ||
		Object.keys(v).sort().join() !==
			[
				"commandKey",
				"expectedLifecycleVersion",
				"expectedPurchasedRevision",
				"reason",
			]
				.sort()
				.join() ||
		!validCancellationReason(v.reason) ||
		!cancellationUuid.test(v.commandKey) ||
		!Number.isSafeInteger(v.expectedPurchasedRevision) ||
		v.expectedPurchasedRevision < 1 ||
		v.expectedLifecycleVersion !== 0
	)
		throw new CancellationError(400);
	return {
		reason: v.reason,
		commandKey: v.commandKey,
		expectedPurchasedRevision: v.expectedPurchasedRevision,
		expectedLifecycleVersion: 0,
	};
}
function validTime(value: unknown): value is string {
	return (
		typeof value === "string" &&
		/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value) &&
		Number.isFinite(Date.parse(value)) &&
		new Date(value).toISOString().slice(0, 19) === value.slice(0, 19)
	);
}
export function cancellationResult(
	raw: unknown,
	c: CancellationContext,
): CancellationResult {
	const v = raw as CancellationResult;
	if (
		!v ||
		v.bookingId !== c.bookingId ||
		!cancellationUuid.test(v.venueId) ||
		(c.venueId && v.venueId !== c.venueId) ||
		v.policyVersion !== cancellationPolicy ||
		v.synthetic !== true ||
		v.realRepayment !== false ||
		v.physicalReadiness !== "UNVERIFIED" ||
		typeof v.eligible !== "boolean"
	)
		throw new CancellationError(503);
	const safe: CancellationResult = {
		bookingId: v.bookingId,
		venueId: v.venueId,
		state: v.state,
		lifecycleVersion: v.lifecycleVersion,
		policyVersion: v.policyVersion,
		synthetic: true,
		realRepayment: false,
		physicalReadiness: "UNVERIFIED",
		eligible: v.eligible,
		reason: v.reason,
		refundStatus: v.refundStatus,
	};
	if (v.purchasedRevision !== undefined) {
		if (!Number.isSafeInteger(v.purchasedRevision) || v.purchasedRevision < 1)
			throw new CancellationError(503);
		safe.purchasedRevision = v.purchasedRevision;
	}
	if (v.state === "SIMULATED_CANCELLED") {
		if (
			v.lifecycleVersion !== 1 ||
			v.eligible ||
			v.reason !== "ALREADY_CANCELLED" ||
			!safe.purchasedRevision ||
			v.refundStatus !== "RESERVED_REVIEW_REQUIRED" ||
			v.resolution !== "FINANCIAL_REVIEW_REQUIRED" ||
			v.inventoryReleased !== true ||
			v.accessState !== "NOT_ISSUED" ||
			!cancellationUuid.test(v.cancellationId ?? "") ||
			!cancellationUuid.test(v.refundObligationId ?? "")
		)
			throw new CancellationError(503);
		return {
			...safe,
			cancellationId: v.cancellationId,
			refundObligationId: v.refundObligationId,
			resolution: "FINANCIAL_REVIEW_REQUIRED",
			inventoryReleased: true,
			accessState: "NOT_ISSUED",
		};
	}
	if (
		v.state !== "SIMULATED_CONFIRMED" ||
		v.lifecycleVersion !== 0 ||
		v.refundStatus !== "NO_CANCELLATION_OBLIGATION" ||
		![
			"ELIGIBLE",
			"SESSION_STARTED",
			"CREDENTIAL_EVER_ISSUED",
			"AMENDMENT_PENDING",
			"FINANCIAL_REVIEW_REQUIRED",
			"BOOKING_UNAVAILABLE",
		].includes(v.reason) ||
		v.eligible !== (v.reason === "ELIGIBLE") ||
		(v.eligible &&
			(!safe.purchasedRevision ||
				typeof v.sessionStart !== "string" ||
				!validTime(v.sessionStart)))
	)
		throw new CancellationError(503);
	if (v.sessionStart !== undefined) {
		if (!validTime(v.sessionStart)) throw new CancellationError(503);
		safe.sessionStart = v.sessionStart;
	}
	if (v.refundPrincipalMinor !== undefined || v.currency !== undefined) {
		if (
			typeof v.refundPrincipalMinor !== "string" ||
			!/^[1-9][0-9]{0,18}$/.test(v.refundPrincipalMinor) ||
			v.currency !== "LKR" ||
			BigInt(v.refundPrincipalMinor) > 9223372036854775807n
		)
			throw new CancellationError(503);
		if (!c.venueId) {
			safe.refundPrincipalMinor = v.refundPrincipalMinor;
			safe.currency = "LKR";
		}
	}
	return safe;
}
function active(signal: AbortSignal) {
	signal.throwIfAborted();
}
async function account(signal: AbortSignal) {
	active(signal);
	const r = await fetch("/api/accounts/me", {
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	active(signal);
	if (!r.ok) throw new CancellationError(r.status);
	const v = await r.json();
	active(signal);
	if (!cancellationUuid.test(v?.accountId) || v.eligibility !== "ELIGIBLE")
		throw new CancellationError(403);
	return v.accountId as string;
}
export async function requestCancellation(
	c: CancellationContext,
	signal: AbortSignal,
	command?: CancellationCommand,
	expectedAccount?: string,
) {
	const owner = await account(signal);
	if (expectedAccount && owner !== expectedAccount)
		throw new CancellationError(401);
	const headers = new Headers({ Accept: "application/json" });
	if (command) {
		cancellationCommand(command);
		const csrf = await fetch("/api/session/csrf", {
			credentials: "same-origin",
			cache: "no-store",
			signal,
		});
		active(signal);
		if (!csrf.ok) throw new CancellationError(csrf.status);
		const token = await csrf.json();
		active(signal);
		if (
			token?.headerName !== "X-CSRF-TOKEN" ||
			typeof token.token !== "string" ||
			!token.token ||
			token.token.length > 4096
		)
			throw new CancellationError(403);
		if ((await account(signal)) !== owner) throw new CancellationError(401);
		active(signal);
		headers.set("X-CSRF-TOKEN", token.token);
		headers.set("Content-Type", "application/json");
	}
	active(signal);
	const r = await fetch(cancellationPath(c, !!command), {
		method: command ? "POST" : "GET",
		headers,
		body: command ? JSON.stringify(command) : undefined,
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	active(signal);
	if (!r.ok) throw new CancellationError(r.status);
	const raw = await r.json();
	active(signal);
	const value = cancellationResult(raw, c);
	if (command && value.state !== "SIMULATED_CANCELLED")
		throw new CancellationError(503);
	if ((await account(signal)) !== owner) throw new CancellationError(401);
	active(signal);
	return { value, account: owner };
}

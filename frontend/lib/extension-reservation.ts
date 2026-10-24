import { cancellationUuid as uuid } from "./booking-cancellation";
import { ExtensionError, type ExtensionResult } from "./extension-preview";
export const reservationTerms = "SYNTHETIC_EXTENSION_RESERVATION_V1";
export const reservationWarning =
	"Temporary synthetic extension coverage only. Original booking unchanged. No payment requested or extension confirmed.";
export type Reservation = {
	bookingId: string;
	reservationId: string;
	purchasedRevision: number;
	proposalRevision: number;
	state: "ACTIVE" | "WITHDRAWN" | "EXPIRED" | "INVALIDATED";
	originalSession: { start: string; end: string; blockedUntil: string };
	proposedSession: { start: string; end: string; blockedUntil: string };
	additionalCoverage: { start: string; end: string };
	additionalMinor: string;
	currency: "LKR";
	packageVersionId: string;
	pricingBasis: "FROZEN_PURCHASED_TERMS";
	additionalMinutes: number;
	termsVersion: string;
	createdAt: string;
	expiresAt: string;
	observedAt: string;
	effective: boolean;
	pendingReconciliation: boolean;
	reasons: string[];
	bookingChanged: false;
	paymentRequested: false;
	occupancy: "UNKNOWN";
	readiness: "UNVERIFIED";
	synthetic: true;
	warning: string;
};
export type CreateReservation = {
	additionalMinutes: number;
	expectedPurchasedRevision: number;
	expectedLifecycleVersion: number;
	expectedPackageVersionId: string;
	expectedAdditionalMinor: string;
	expectedCurrency: "LKR";
	expectedConfiguration: string;
	commandKey: string;
	termsVersion: string;
	acknowledged: true;
};
export type WithdrawReservation = {
	expectedRevision: number;
	commandKey: string;
};
const integer = (v: unknown): v is number =>
	typeof v === "number" && Number.isSafeInteger(v) && v >= 0 && v <= 2147483647;
const timestamp = (v: unknown): v is string =>
	typeof v === "string" &&
	/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(v) &&
	Number.isFinite(Date.parse(v));
const fail = (): never => {
	throw new ExtensionError(503);
};
export function reservationCommand(
	raw: unknown,
	withdraw = false,
): CreateReservation | WithdrawReservation {
	const v = raw as CreateReservation & WithdrawReservation;
	if (
		!v ||
		typeof v !== "object" ||
		Array.isArray(v) ||
		!uuid.test(v.commandKey)
	)
		throw new ExtensionError(400);
	const keys = withdraw
		? ["expectedRevision", "commandKey"]
		: [
				"additionalMinutes",
				"expectedPurchasedRevision",
				"expectedLifecycleVersion",
				"expectedPackageVersionId",
				"expectedAdditionalMinor",
				"expectedCurrency",
				"expectedConfiguration",
				"commandKey",
				"termsVersion",
				"acknowledged",
			];
	if (Object.keys(v).sort().join() !== keys.sort().join())
		throw new ExtensionError(400);
	if (withdraw) {
		if (!integer(v.expectedRevision)) throw new ExtensionError(400);
		return { expectedRevision: v.expectedRevision, commandKey: v.commandKey };
	}
	if (
		!integer(v.additionalMinutes) ||
		v.additionalMinutes < 1 ||
		v.expectedPurchasedRevision !== 1 ||
		v.expectedLifecycleVersion !== 0 ||
		!uuid.test(v.expectedPackageVersionId) ||
		typeof v.expectedAdditionalMinor !== "string" ||
		!/^\d{1,19}$/.test(v.expectedAdditionalMinor) ||
		BigInt(v.expectedAdditionalMinor) > 9223372036854775807n ||
		v.expectedCurrency !== "LKR" ||
		typeof v.expectedConfiguration !== "string" ||
		!v.expectedConfiguration ||
		v.expectedConfiguration.length > 512 ||
		v.termsVersion !== reservationTerms ||
		v.acknowledged !== true
	)
		throw new ExtensionError(400);
	return Object.fromEntries(
		keys.map((k) => [k, v[k as keyof CreateReservation]]),
	) as CreateReservation;
}
export function reservationResult(
	raw: unknown,
	booking: string,
	reservation?: string,
): Reservation {
	const v = raw as Reservation;
	if (
		!v ||
		typeof v !== "object" ||
		v.bookingId !== booking ||
		!uuid.test(booking) ||
		!uuid.test(v.reservationId) ||
		(reservation && v.reservationId !== reservation) ||
		v.purchasedRevision !== 1 ||
		!integer(v.proposalRevision) ||
		!["ACTIVE", "WITHDRAWN", "EXPIRED", "INVALIDATED"].includes(v.state) ||
		!integer(v.additionalMinutes) ||
		v.additionalMinutes < 1 ||
		typeof v.additionalMinor !== "string" ||
		!/^\d{1,19}$/.test(v.additionalMinor) ||
		BigInt(v.additionalMinor) > 9223372036854775807n ||
		v.currency !== "LKR" ||
		!uuid.test(v.packageVersionId) ||
		v.pricingBasis !== "FROZEN_PURCHASED_TERMS" ||
		v.termsVersion !== reservationTerms ||
		![v.createdAt, v.expiresAt, v.observedAt].every(timestamp) ||
		typeof v.effective !== "boolean" ||
		typeof v.pendingReconciliation !== "boolean" ||
		!Array.isArray(v.reasons) ||
		v.reasons.some((x) => x !== "PENDING_RECONCILIATION") ||
		v.reasons.length > 1 ||
		v.bookingChanged !== false ||
		v.paymentRequested !== false ||
		v.occupancy !== "UNKNOWN" ||
		v.readiness !== "UNVERIFIED" ||
		v.synthetic !== true ||
		v.warning !== reservationWarning
	)
		return fail();
	for (const w of [v.originalSession, v.proposedSession])
		if (
			!w ||
			![w.start, w.end, w.blockedUntil].every(timestamp) ||
			Date.parse(w.start) >= Date.parse(w.end) ||
			Date.parse(w.end) > Date.parse(w.blockedUntil)
		)
			return fail();
	if (
		Date.parse(v.observedAt) < Date.parse(v.createdAt) ||
		Date.parse(v.proposedSession.blockedUntil) -
			Date.parse(v.originalSession.blockedUntil) !==
			v.additionalMinutes * 60000 ||
		!v.additionalCoverage ||
		![v.additionalCoverage.start, v.additionalCoverage.end].every(timestamp) ||
		v.originalSession.start !== v.proposedSession.start ||
		v.additionalCoverage.start !== v.originalSession.blockedUntil ||
		v.additionalCoverage.end !== v.proposedSession.blockedUntil ||
		Date.parse(v.additionalCoverage.start) >=
			Date.parse(v.additionalCoverage.end) ||
		Date.parse(v.proposedSession.end) - Date.parse(v.originalSession.end) !==
			v.additionalMinutes * 60000 ||
		Date.parse(v.createdAt) >= Date.parse(v.expiresAt) ||
		Date.parse(v.expiresAt) > Date.parse(v.originalSession.start) ||
		Date.parse(v.expiresAt) - Date.parse(v.createdAt) > 600000 ||
		v.pendingReconciliation !== (v.reasons.length === 1) ||
		(v.state !== "ACTIVE" && (v.effective || v.pendingReconciliation)) ||
		(v.effective && v.pendingReconciliation) ||
		(v.state === "ACTIVE" && !v.effective && !v.pendingReconciliation) ||
		(v.effective && Date.parse(v.observedAt) >= Date.parse(v.expiresAt))
	)
		return fail();
	const {
		bookingId,
		reservationId,
		purchasedRevision,
		proposalRevision,
		state,
		additionalMinor,
		currency,
		packageVersionId,
		pricingBasis,
		additionalMinutes,
		termsVersion,
		createdAt,
		expiresAt,
		observedAt,
		effective,
		pendingReconciliation,
	} = v;
	const window = (w: Reservation["originalSession"]) => ({
		start: w.start,
		end: w.end,
		blockedUntil: w.blockedUntil,
	});
	return {
		bookingId,
		reservationId,
		purchasedRevision,
		proposalRevision,
		state,
		originalSession: window(v.originalSession),
		proposedSession: window(v.proposedSession),
		additionalCoverage: {
			start: v.additionalCoverage.start,
			end: v.additionalCoverage.end,
		},
		additionalMinor,
		currency,
		packageVersionId,
		pricingBasis,
		additionalMinutes,
		termsVersion,
		createdAt,
		expiresAt,
		observedAt,
		effective,
		pendingReconciliation,
		reasons: [...v.reasons],
		bookingChanged: false,
		paymentRequested: false,
		occupancy: "UNKNOWN",
		readiness: "UNVERIFIED",
		synthetic: true,
		warning: reservationWarning,
	};
}
export function recoveryResult(raw: unknown, booking: string, key: string) {
	const v = raw as Record<string, unknown>;
	if (
		!v ||
		v.bookingId !== booking ||
		v.commandKey !== key ||
		!uuid.test(String(v.reservationId)) ||
		v.commandType !== "CREATE" ||
		v.recoveryStatus !== "COMMITTED_REFERENCE" ||
		v.statusReadRequired !== true
	)
		return fail();
	return {
		bookingId: booking,
		reservationId: String(v.reservationId),
		commandKey: key,
		commandType: "CREATE",
		recoveryStatus: "COMMITTED_REFERENCE",
		statusReadRequired: true,
	};
}
export function reservationPath(
	booking: string,
	ref?: string,
	operation?: "recover" | "withdraw",
) {
	if (
		!uuid.test(booking) ||
		(ref !== undefined && !uuid.test(ref)) ||
		(operation && !ref)
	)
		throw new ExtensionError(400);
	return `/api/bookings/${booking}/extension-reservations${operation === "recover" ? `/commands/${ref}` : ref ? `/${ref}${operation === "withdraw" ? "/withdrawals" : ""}` : ""}`;
}
export async function reservationAccount(signal: AbortSignal) {
	signal.throwIfAborted();
	const r = await fetch("/api/accounts/me", {
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	signal.throwIfAborted();
	if (!r.ok) throw new ExtensionError(r.status);
	const v = await r.json();
	signal.throwIfAborted();
	if (!uuid.test(v?.accountId) || v.eligibility !== "ELIGIBLE")
		throw new ExtensionError(403);
	return v.accountId as string;
}
export async function reservationFetch(
	path: string,
	signal: AbortSignal,
	owner: string,
	body?: CreateReservation | WithdrawReservation,
) {
	if ((await reservationAccount(signal)) !== owner)
		throw new ExtensionError(401);
	const headers = new Headers({ Accept: "application/json" });
	if (body) {
		reservationCommand(body, "expectedRevision" in body);
		const r = await fetch("/api/session/csrf", {
			credentials: "same-origin",
			cache: "no-store",
			signal,
		});
		signal.throwIfAborted();
		if (!r.ok) throw new ExtensionError(r.status);
		const c = await r.json();
		signal.throwIfAborted();
		if (
			c?.headerName !== "X-CSRF-TOKEN" ||
			typeof c.token !== "string" ||
			!c.token ||
			c.token.length > 4096
		)
			throw new ExtensionError(403);
		headers.set("X-CSRF-TOKEN", c.token);
		headers.set("Content-Type", "application/json");
		if ((await reservationAccount(signal)) !== owner)
			throw new ExtensionError(401);
	}
	const r = await fetch(path, {
		method: body ? "POST" : "GET",
		body: body ? JSON.stringify(body) : undefined,
		headers,
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	signal.throwIfAborted();
	if (!r.ok) throw new ExtensionError(r.status);
	const value = await r.json();
	signal.throwIfAborted();
	if ((await reservationAccount(signal)) !== owner)
		throw new ExtensionError(401);
	signal.throwIfAborted();
	return value;
}
export function createFromPreview(
	v: ExtensionResult,
	configuration: string,
	key: string,
): CreateReservation {
	return reservationCommand({
		additionalMinutes: v.additionalMinutes,
		expectedPurchasedRevision: v.purchasedRevision,
		expectedLifecycleVersion: v.lifecycleVersion,
		expectedPackageVersionId: v.packageVersionId,
		expectedAdditionalMinor: v.additionalMinor,
		expectedCurrency: v.currency,
		expectedConfiguration: configuration,
		commandKey: key,
		termsVersion: reservationTerms,
		acknowledged: true,
	}) as CreateReservation;
}

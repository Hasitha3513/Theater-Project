import { cancellationUuid } from "./booking-cancellation";
export const extensionWarning =
	"Indicative synthetic extension preview. No additional time is reserved. No payment has been requested. Your booking has not changed.";
export const extensionNotice =
	"This is a price and availability preview only. Your booking has not been extended, and no payment has been requested.";
export const extensionReasons: Record<string, string> = {
	ELIGIBLE:
		"You can request a synthetic price preview using your purchased terms.",
	BOOKING_NOT_CONFIRMED: "This booking is not synthetically confirmed.",
	PURCHASE_REVISION_UNSUPPORTED:
		"This purchased revision does not support extension preview.",
	EXTENSION_PENDING:
		"An extension reservation is already pending. This preview cannot replace it.",
	SESSION_STARTED:
		"The session has started. Pre-start extension preview is unavailable.",
	CREDENTIAL_EVER_ISSUED:
		"An access credential has previously been issued. Extension preview is unavailable.",
	AMENDMENT_PENDING:
		"An amendment is pending. Resolve it through the existing workflow first.",
	ROOM_CONTEXT_CHANGED:
		"The room configuration changed. Reload the booking before checking again.",
	FROZEN_PRICING_UNSUPPORTED:
		"Extension pricing is unavailable for these purchased terms.",
	ROOM_CONSTRAINTS_UNSATISFIED:
		"The requested time does not meet current room constraints.",
};
export class ExtensionError extends Error {
	constructor(public status: number) {
		super("Extension preview unavailable");
	}
}
export type ExtensionCommand = {
	additionalMinutes: number;
	expectedPurchasedRevision: number;
	expectedLifecycleVersion: number;
};
export type ExtensionResult = {
	bookingId: string;
	purchasedRevision: number;
	lifecycleVersion: number;
	eligible: boolean;
	reason: string;
	observedAt: string;
	synthetic: true;
	warning: string;
	timeReserved: false;
	paymentRequested: false;
	bookingChanged: false;
	occupancy: "UNKNOWN";
	readiness: "UNVERIFIED";
	durationUnits?: {
		incrementMinutes: number;
		minimumAdditionalMinutes: number;
		maximumAdditionalMinutes: number;
	};
	currentDurationMinutes?: number;
	additionalMinutes?: number;
	proposedDurationMinutes?: number;
	additionalMinor?: string;
	currency?: "LKR";
	packageVersionId?: string;
	pricingBasis?: "FROZEN_PURCHASED_TERMS";
};
const integer = (v: unknown, min = 0): v is number =>
	typeof v === "number" && Number.isInteger(v) && v >= min && v <= 2147483647;
export function extensionCommand(raw: unknown): ExtensionCommand {
	const v = raw as ExtensionCommand;
	if (
		!v ||
		Object.keys(v).sort().join() !==
			"additionalMinutes,expectedLifecycleVersion,expectedPurchasedRevision" ||
		!integer(v.additionalMinutes, 1) ||
		!integer(v.expectedPurchasedRevision, 1) ||
		!integer(v.expectedLifecycleVersion)
	)
		throw new ExtensionError(400);
	return {
		additionalMinutes: v.additionalMinutes,
		expectedPurchasedRevision: v.expectedPurchasedRevision,
		expectedLifecycleVersion: v.expectedLifecycleVersion,
	};
}
export function extensionResult(
	raw: unknown,
	bookingId: string,
	quote = false,
): ExtensionResult {
	const v = raw as ExtensionResult;
	if (
		!v ||
		!cancellationUuid.test(bookingId) ||
		v.bookingId !== bookingId ||
		!integer(v.purchasedRevision) ||
		!integer(v.lifecycleVersion) ||
		typeof v.eligible !== "boolean" ||
		!Object.hasOwn(extensionReasons, v.reason) ||
		v.eligible !== (v.reason === "ELIGIBLE") ||
		typeof v.observedAt !== "string" ||
		!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(
			v.observedAt,
		) ||
		!Number.isFinite(Date.parse(v.observedAt)) ||
		v.synthetic !== true ||
		v.warning !== extensionWarning ||
		v.timeReserved !== false ||
		v.paymentRequested !== false ||
		v.bookingChanged !== false ||
		v.occupancy !== "UNKNOWN" ||
		v.readiness !== "UNVERIFIED"
	)
		throw new ExtensionError(503);
	const safe: ExtensionResult = {
		bookingId: v.bookingId,
		purchasedRevision: v.purchasedRevision,
		lifecycleVersion: v.lifecycleVersion,
		eligible: v.eligible,
		reason: v.reason,
		observedAt: v.observedAt,
		synthetic: true,
		warning: extensionWarning,
		timeReserved: false,
		paymentRequested: false,
		bookingChanged: false,
		occupancy: "UNKNOWN",
		readiness: "UNVERIFIED",
	};
	if (v.eligible) {
		const d = v.durationUnits;
		if (
			v.purchasedRevision !== 1 ||
			v.lifecycleVersion !== 0 ||
			!d ||
			!integer(d.incrementMinutes, 1) ||
			d.minimumAdditionalMinutes !== d.incrementMinutes ||
			!integer(d.maximumAdditionalMinutes, d.minimumAdditionalMinutes)
		)
			throw new ExtensionError(503);
		safe.durationUnits = {
			incrementMinutes: d.incrementMinutes,
			minimumAdditionalMinutes: d.minimumAdditionalMinutes,
			maximumAdditionalMinutes: d.maximumAdditionalMinutes,
		};
	}
	if (quote) {
		const d = safe.durationUnits;
		if (
			!d ||
			!v.eligible ||
			!integer(v.currentDurationMinutes, 1) ||
			!integer(v.additionalMinutes, d.minimumAdditionalMinutes) ||
			v.additionalMinutes > d.maximumAdditionalMinutes ||
			v.additionalMinutes % d.incrementMinutes !== 0 ||
			!integer(v.proposedDurationMinutes, 1) ||
			v.proposedDurationMinutes !==
				v.currentDurationMinutes + v.additionalMinutes ||
			typeof v.additionalMinor !== "string" ||
			!/^[0-9]{1,19}$/.test(v.additionalMinor) ||
			BigInt(v.additionalMinor) > 9223372036854775807n ||
			v.currency !== "LKR" ||
			!cancellationUuid.test(v.packageVersionId ?? "") ||
			v.pricingBasis !== "FROZEN_PURCHASED_TERMS"
		)
			throw new ExtensionError(503);
		Object.assign(safe, {
			currentDurationMinutes: v.currentDurationMinutes,
			additionalMinutes: v.additionalMinutes,
			proposedDurationMinutes: v.proposedDurationMinutes,
			additionalMinor: v.additionalMinor,
			currency: "LKR",
			packageVersionId: v.packageVersionId,
			pricingBasis: "FROZEN_PURCHASED_TERMS",
		});
	}
	return safe;
}
export function extensionPath(id: string, quote = false) {
	if (!cancellationUuid.test(id)) throw new ExtensionError(400);
	return `/api/bookings/${id}/${quote ? "extension-quotes" : "extension-eligibility"}`;
}
async function account(signal: AbortSignal) {
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
	if (!cancellationUuid.test(v?.accountId) || v.eligibility !== "ELIGIBLE")
		throw new ExtensionError(403);
	return v.accountId as string;
}
export async function requestExtension(
	id: string,
	signal: AbortSignal,
	command?: ExtensionCommand,
	expectedAccount?: string,
) {
	const path = extensionPath(id, !!command),
		owner = await account(signal);
	if (expectedAccount && owner !== expectedAccount)
		throw new ExtensionError(401);
	const headers = new Headers({ Accept: "application/json" });
	if (command) {
		extensionCommand(command);
		const r = await fetch("/api/session/csrf", {
			credentials: "same-origin",
			cache: "no-store",
			signal,
		});
		signal.throwIfAborted();
		if (!r.ok) throw new ExtensionError(r.status);
		const token = await r.json();
		signal.throwIfAborted();
		if (
			token?.headerName !== "X-CSRF-TOKEN" ||
			typeof token.token !== "string" ||
			!token.token ||
			token.token.length > 4096
		)
			throw new ExtensionError(403);
		if ((await account(signal)) !== owner) throw new ExtensionError(401);
		headers.set("X-CSRF-TOKEN", token.token);
		headers.set("Content-Type", "application/json");
	}
	signal.throwIfAborted();
	const r = await fetch(path, {
		method: command ? "POST" : "GET",
		headers,
		body: command ? JSON.stringify(command) : undefined,
		credentials: "same-origin",
		cache: "no-store",
		signal,
	});
	signal.throwIfAborted();
	if (!r.ok) throw new ExtensionError(r.status);
	const raw = await r.json();
	signal.throwIfAborted();
	const value = extensionResult(raw, id, !!command);
	if (
		command &&
		(value.additionalMinutes !== command.additionalMinutes ||
			value.purchasedRevision !== command.expectedPurchasedRevision ||
			value.lifecycleVersion !== command.expectedLifecycleVersion)
	)
		throw new ExtensionError(409);
	if ((await account(signal)) !== owner) throw new ExtensionError(401);
	signal.throwIfAborted();
	return { value, account: owner };
}
export function extensionMoney(value: string) {
	const n = BigInt(value);
	return `LKR ${(n / 100n).toString()}.${(n % 100n).toString().padStart(2, "0")}`;
}

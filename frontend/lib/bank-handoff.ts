import { bankLabels, validateBank } from "./bank";
import { validateQuote } from "./quotes";
import { RoomRequestError, roomRequest } from "./rooms";

export const quoteReference =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const bankReadinessReasons = {
	ELIGIBLE:
		"Ready to review a synthetic bank request. No money or booking is confirmed.",
	QUOTE_NOT_ACCEPTED:
		"Return to quotation review and explicitly accept the current terms.",
	BANK_METHOD_REQUIRED:
		"This handoff requires an accepted BANK quotation. Gateway payments are unavailable.",
	QUOTE_EXPIRED:
		"The quotation expired. Return to room selection for a new hold and quotation.",
	VERIFICATION_REQUIRED:
		"Verification is missing, pending, rejected, expired or no longer eligible. Refresh your verification request; a staff decision may be required.",
	SYNTHETIC_TERMS_REQUIRED:
		"These terms cannot create a synthetic bank request. Return to quotation review.",
	LEAD_TIME_REQUIRED:
		"The session is too close for the configured bank-request lead time. Choose another session.",
	DEADLINES_EXCEED_SESSION_START:
		"The configured bank-request deadlines do not fit before this session. Choose another session.",
	REVIEW_WINDOW_CLOSED:
		"The configured review window is closed. Reload when review is available; the hold has not been renewed.",
	HOLD_UNAVAILABLE:
		"The inventory hold is unavailable. Return to room selection.",
	HOLD_ALREADY_CONVERTED:
		"A bank request already uses this hold. Open your existing request history; do not create another request.",
	HOLD_EXPIRED: "The inventory hold expired. Return to room selection.",
	HOLD_INELIGIBLE:
		"The room or hold is no longer eligible. Return to room selection.",
	HOLD_CHANGED:
		"The hold changed. Return to quotation review before continuing.",
	CONVERSION_DEADLINE_INVALID:
		"The hold cannot support the current request deadline. Choose another session.",
} as const;
export type BankContext = {
	quoteId: string;
	quoteRevision: number;
	contentHash: string;
	quoteExpiresAt: string;
	quoteSummary: {
		currency: "LKR";
		totalMinor: string;
		paymentMethod: "BANK" | "GATEWAY";
		sessionStart: string;
		sessionEnd: string;
		timezone: string;
		packageName: string;
	};
	verificationReadiness: "NOT_EVALUATED" | "ELIGIBLE" | "REQUIRED";
	eligible: boolean;
	reason: keyof typeof bankReadinessReasons;
	serverNow: string;
	synthetic: true;
	paymentVerified: false;
	bookingConfirmed: false;
	labels: string[];
	venueId?: string;
	roomId?: string;
};
export function bankHref(id: string) {
	if (!quoteReference.test(id))
		throw new Error("Valid quotation reference required");
	return `/bank?${new URLSearchParams({ quote: id })}`;
}
export function validateBankContext(
	value: unknown,
	reference: string,
): BankContext {
	const v = value as BankContext;
	const s = v?.quoteSummary;
	if (
		!quoteReference.test(reference) ||
		typeof v?.quoteId !== "string" ||
		v.quoteId.toLowerCase() !== reference.toLowerCase() ||
		!Number.isSafeInteger(v.quoteRevision) ||
		v.quoteRevision < 1 ||
		typeof v.contentHash !== "string" ||
		!/^[a-f0-9]{64}$/.test(v.contentHash) ||
		v.synthetic !== true ||
		v.paymentVerified !== false ||
		v.bookingConfirmed !== false ||
		!bankLabels.every((l) => v.labels?.includes(l)) ||
		typeof v.eligible !== "boolean" ||
		!Object.hasOwn(bankReadinessReasons, v.reason) ||
		v.eligible !== (v.reason === "ELIGIBLE") ||
		!["NOT_EVALUATED", "ELIGIBLE", "REQUIRED"].includes(
			v.verificationReadiness,
		) ||
		(v.eligible && v.verificationReadiness !== "ELIGIBLE") ||
		!s ||
		s.currency !== "LKR" ||
		!["BANK", "GATEWAY"].includes(s.paymentMethod) ||
		(v.eligible && s.paymentMethod !== "BANK") ||
		typeof s.totalMinor !== "string" ||
		!/^\d{1,19}$/.test(s.totalMinor) ||
		BigInt(s.totalMinor) > 9223372036854775807n ||
		typeof s.packageName !== "string" ||
		typeof s.timezone !== "string" ||
		!s.timezone ||
		![v.quoteExpiresAt, v.serverNow, s.sessionStart, s.sessionEnd].every(
			(t) => typeof t === "string" && Number.isFinite(Date.parse(t)),
		)
	)
		throw new Error(
			"Bank context unavailable. Reload protected quotation context.",
		);
	new Intl.DateTimeFormat("en-GB", { timeZone: s.timezone }).format(
		new Date(s.sessionStart),
	);
	return v;
}
export async function loadBankContext(
	id: string,
	detail = true,
	signal?: AbortSignal,
): Promise<BankContext> {
	if (!quoteReference.test(id))
		throw new RoomRequestError(
			400,
			"Select a valid accepted quotation from verification to continue.",
		);
	// The protected quote read supplies resource references only, never banking eligibility.
	const raw = detail
		? await roomRequest<Record<string, unknown>>(`/api/quotes/${id}`, {
				signal,
			})
		: undefined;
	const q = raw ? validateQuote(raw) : undefined;
	const context = validateBankContext(
		await roomRequest(
			`/api/bank/requests/readiness?${new URLSearchParams({ quote: id })}`,
			{ signal },
		),
		id,
	);
	if (
		q &&
		(q.quoteId.toLowerCase() !== id.toLowerCase() ||
			q.revision !== context.quoteRevision ||
			q.contentHash !== context.contentHash ||
			!quoteReference.test(String(raw?.venueId)) ||
			!quoteReference.test(String(raw?.roomId)))
	)
		throw new RoomRequestError(
			409,
			"Quotation changed during reload. Review the current quotation before confirming again.",
		);
	return q
		? { ...context, venueId: String(raw?.venueId), roomId: String(raw?.roomId) }
		: context;
}
export function bankCreationBody(context: BankContext, commandKey: string) {
	if (!context.eligible || !quoteReference.test(commandKey))
		throw new Error("Current eligible context and command key required");
	return {
		quoteId: context.quoteId,
		quoteRevision: context.quoteRevision,
		contentHash: context.contentHash,
		commandKey,
	};
}
export function bankCreationResult(value: unknown, quoteId: string) {
	const result = validateBank(value);
	if (result.quoteId.toLowerCase() !== quoteId.toLowerCase())
		throw new Error("Bank result does not match the selected quotation");
	return result;
}

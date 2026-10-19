import { type Quote, validateQuote } from "./quotes";
import { RoomRequestError, roomRequest } from "./rooms";

const reference =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const handoffReasons = {
	ELIGIBLE:
		"This synthetic quotation can continue to verification. No payment or booking is confirmed.",
	QUOTE_NOT_ACCEPTED:
		"Review and explicitly accept the quotation before continuing.",
	QUOTE_REVISION_CHANGED:
		"The quotation changed. Return to quotation review and reload its current revision.",
	BANK_METHOD_REQUIRED:
		"This verification handoff supports the BANK method only. No gateway payment is available.",
	HOLD_UNAVAILABLE:
		"The inventory hold is unavailable. Return to room selection.",
	QUOTE_EXPIRED:
		"The quotation expired. Return to room selection for a new hold and quotation review.",
	HOLD_EXPIRED:
		"The inventory hold expired. Return to room selection; it has not been renewed.",
	HOLD_INELIGIBLE:
		"The inventory hold is no longer eligible. Return to room selection.",
	HOLD_CHANGED:
		"The inventory hold changed. Return to quotation review before continuing.",
	PACKAGE_INCOMPATIBLE:
		"The room or package configuration is no longer applicable. Return to room selection.",
} as const;
export type HandoffQuote = Quote & {
	verificationHandoff: {
		eligible: boolean;
		reason: keyof typeof handoffReasons;
	};
};
export function verificationHref(id: string) {
	if (!reference.test(id))
		throw new Error("Valid quotation reference required");
	return `/verification?${new URLSearchParams({ quote: id })}`;
}
export function validateHandoffQuote(value: unknown, id: string): HandoffQuote {
	const quote = validateQuote(value) as HandoffQuote;
	const handoff = quote.verificationHandoff;
	if (
		!reference.test(id) ||
		quote.quoteId.toLowerCase() !== id.toLowerCase() ||
		!handoff ||
		typeof handoff.eligible !== "boolean" ||
		!Object.hasOwn(handoffReasons, handoff.reason) ||
		handoff.eligible !== (handoff.reason === "ELIGIBLE") ||
		(handoff.eligible &&
			(quote.state !== "ACCEPTED" || quote.paymentMethod !== "BANK"))
	)
		throw new Error(
			"Quotation handoff context is unavailable. Reload quotation review.",
		);
	return quote;
}
export async function loadHandoffQuote(id: string, signal?: AbortSignal) {
	if (!id)
		throw new RoomRequestError(
			400,
			"Select an accepted quotation to load verification context.",
		);
	if (!reference.test(id))
		throw new RoomRequestError(
			400,
			"The quotation reference is malformed. Return to room selection.",
		);
	return validateHandoffQuote(
		await roomRequest(`/api/quotes/${id}`, { signal }),
		id,
	);
}

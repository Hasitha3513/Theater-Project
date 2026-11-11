import type { Hold } from "./inventory";
import { RoomRequestError, roomRequest } from "./rooms";

export const commercialIssuanceUnavailable =
	"Commercial quotation issuance is unavailable. The synthetic preview does not create an offer, checkout, or payment.";

const reference =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

type OperationalHold = Hold & {
	mode: "OPERATIONAL";
	venueId: string;
	packageId: string;
	packageVersionId: string;
	commercialQuotationPermitted: boolean;
};

export function operationalQuoteHold(
	hold: Hold,
	venueId: string,
): OperationalHold | undefined {
	const operational = hold as Partial<OperationalHold>;
	if (
		operational.mode !== "OPERATIONAL" ||
		operational.venueId !== venueId ||
		![
			hold.holdId,
			hold.roomId,
			venueId,
			operational.packageId,
			operational.packageVersionId,
		].every((value) => typeof value === "string" && reference.test(value))
	)
		return undefined;
	return {
		...hold,
		...operational,
		commercialQuotationPermitted:
			operational.commercialQuotationPermitted === true,
	} as OperationalHold;
}

export function operationalQuoteReceipt(value: unknown) {
	if (
		!value ||
		typeof value !== "object" ||
		!("quoteId" in value) ||
		typeof value.quoteId !== "string" ||
		!reference.test(value.quoteId)
	)
		throw new Error("Invalid commercial quotation receipt");
	return { quoteId: value.quoteId };
}

export function operationalQuoteBody(
	hold: OperationalHold,
	commandKey: string,
	method: "BANK" | "GATEWAY",
	serviceIds: string[] = [],
) {
	if (
		!hold.commercialQuotationPermitted ||
		hold.state !== "ACTIVE" ||
		!Number.isSafeInteger(hold.revision) ||
		hold.revision < 0 ||
		!reference.test(commandKey) ||
		serviceIds.some((id) => !reference.test(id)) ||
		new Set(serviceIds).size !== serviceIds.length
	)
		throw new Error("Operational quotation scope is unavailable");
	return JSON.stringify({
		holdId: hold.holdId,
		holdRevision: hold.revision,
		venueId: hold.venueId,
		roomId: hold.roomId,
		packageId: hold.packageId,
		packageVersionId: hold.packageVersionId,
		commandKey,
		method,
		channel: "ONLINE",
		serviceIds,
	});
}

export async function issueOperationalQuote(body: string) {
	return operationalQuoteReceipt(
		await roomRequest<unknown>("/api/commercial-quotes", {
			method: "POST",
			body,
		}),
	);
}

export function operationalQuoteMessage(error: unknown) {
	if (error instanceof RoomRequestError) {
		if (error.status === 401)
			return "Sign in again before requesting a quotation.";
		if (error.status === 403)
			return "Quotation issuance is unavailable for this account and venue.";
		if (error.status === 409)
			return "The hold or commercial policy changed. Reload the hold before requesting a quotation.";
		if (error.status < 500)
			return "The quotation request was rejected. Check the hold and selections.";
	}
	return "Quotation issuance is unavailable. Retry only the same request if the outcome is unknown.";
}

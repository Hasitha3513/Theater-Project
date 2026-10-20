import { validateBank } from "./bank";
import { quoteReference } from "./bank-handoff";
import { roomRequest, RoomRequestError } from "./rooms";

export function bankRequestHref(reference: string) {
	if (!quoteReference.test(reference))
		throw new Error("Invalid request reference");
	return `/bank?request=${reference}`;
}

export async function loadBankRequest(reference: string, signal?: AbortSignal) {
	if (!quoteReference.test(reference))
		throw new RoomRequestError(400, "Valid request reference required");
	const value = validateBank(
		await roomRequest(`/api/bank/requests/${reference}`, { signal }),
	);
	if (value.id.toLowerCase() !== reference.toLowerCase())
		throw new Error("Request response mismatch");
	return value;
}

// Navigation is a projection of a protected server read, never authorization.
export function confirmedBookingHref(status: unknown, reference: unknown) {
	if (
		status !== "SIMULATED_CONFIRMED" ||
		typeof reference !== "string" ||
		!quoteReference.test(reference)
	)
		return null;
	return `/bookings/${reference}`;
}

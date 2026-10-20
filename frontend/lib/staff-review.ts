import { validateBank, type BankRequest } from "./bank";
import { validateVerification, type Verification } from "./verification";
import { roomRequest } from "./rooms";
export type ReviewKind = "verification" | "bank";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function reviewReference(value: string): string {
	if (typeof value !== "string" || value.length !== 36 || !uuid.test(value))
		throw new Error("Valid review reference required");
	return value.toLowerCase();
}
export function reviewHref(
	kind: ReviewKind,
	venue: string,
	id: string,
): string {
	return `/admin/${kind}/venues/${reviewReference(venue)}/requests/${reviewReference(id)}`;
}
export function reviewQueueHref(kind: ReviewKind, venue: string): string {
	return `/admin/${kind}?venue=${reviewReference(venue)}`;
}
export function reviewApiPath(
	kind: ReviewKind,
	venue: string,
	id: string,
): string {
	return `/api/admin/venues/${reviewReference(venue)}/${kind}/requests/${reviewReference(id)}`;
}
export function reviewRecord(
	kind: ReviewKind,
	raw: unknown,
	venue: string,
	id: string,
): Verification | BankRequest {
	const value =
		kind === "verification" ? validateVerification(raw) : validateBank(raw);
	if (
		reviewReference(value.id) !== reviewReference(id) ||
		reviewReference(value.venueId) !== reviewReference(venue)
	)
		throw new Error("Review context mismatch");
	return value;
}
export async function loadStaffReview(
	kind: ReviewKind,
	venue: string,
	id: string,
	signal?: AbortSignal,
) {
	const raw = await roomRequest(reviewApiPath(kind, venue, id), { signal });
	return reviewRecord(kind, raw, venue, id);
}

export const bankLabels = [
	"SYNTHETIC BANK-PAYMENT TEST",
	"DO NOT TRANSFER REAL MONEY",
	"PAYMENT NOT VERIFIED",
	"BOOKING NOT CONFIRMED",
];
export type BankRequest = {
	id: string;
	venueId: string;
	quoteId: string;
	revision: number;
	synthetic: true;
	paymentVerified: false;
	bookingConfirmed: false;
	labels: string[];
	requestState: string;
	reviewState: string;
	inventoryState: string;
	inventoryExpiresAt: string;
	initiateBy?: string;
	reviewWindow?: string;
	instructionVersion?: string;
	claims?: {
		id: string;
		generation: number;
		declaredAmountMinor: string;
		declaredPaidAt: string;
		receivedAt: string;
	}[];
	expiresAt: string;
	submitBy: string;
	reviewBy: string;
	overdue: boolean;
	amountMinor: string;
	currency: string;
	instructions: string;
	claimId: string | null;
	evidence: { id: string; state: string; expiresAt: string }[];
	history: { id: string; action: string; reason: string; at: string }[];
	work?: {
		id: string;
		kind: string;
		state: string;
		attempts: number;
		dueAt: string;
	}[];
	uploadId?: string;
};
export function validateBank(value: unknown): BankRequest {
	const v = value as BankRequest;
	if (
		v?.synthetic !== true ||
		v.paymentVerified !== false ||
		v.bookingConfirmed !== false ||
		typeof v.id !== "string" ||
		!Number.isSafeInteger(v.revision) ||
		v.revision < 0 ||
		!bankLabels.every((l) => v.labels?.includes(l)) ||
		!Array.isArray(v.evidence) ||
		v.evidence.length > 2 ||
		!Array.isArray(v.history) ||
		!/^\d+$/.test(v.amountMinor) ||
		v.currency !== "LKR" ||
		!["OPEN", "REVIEW_PENDING", "REVIEWED", "EXPIRED", "CANCELLED"].includes(
			v.requestState,
		)
	)
		throw new Error("Invalid synthetic bank response");
	return v;
}

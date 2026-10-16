export const quoteDisclosures = [
	"TEST / SYNTHETIC QUOTATION",
	"NOT A COMMERCIAL OFFER",
	"NO PAYMENT OR BOOKING CONFIRMATION",
] as const;
export type Quote = {
	quoteId: string;
	revision: number;
	contentHash: string;
	state: "ISSUED" | "ACCEPTED" | "EXPIRED" | "INVALIDATED";
	currency: "LKR";
	totalMinor: string;
	paymentMethod: "BANK" | "GATEWAY";
	synthetic: true;
	commercialIssuance: false;
	checkoutEnabled: false;
	bookingConfirmed: false;
	expiresAt: string;
	holdExpiresAt: string;
	serverNow: string;
	timezone: string;
	disclosures: string[];
	optionalServiceIds: string[];
	package: { name: string };
	lines: {
		lineId: string;
		kind: string;
		label: string;
		quantity: number;
		unitMinor: string;
		amountMinor: string;
	}[];
};
const minor = (value: unknown) =>
	typeof value === "string" &&
	/^\d{1,19}$/.test(value) &&
	BigInt(value) <= 9223372036854775807n;
export function validateQuote(value: unknown): Quote {
	if (!value || typeof value !== "object")
		throw new Error("Invalid synthetic quotation");
	const q = value as Quote;
	if (
		typeof q.quoteId !== "string" ||
		!Number.isSafeInteger(q.revision) ||
		q.revision < 1 ||
		typeof q.contentHash !== "string" ||
		!/^[a-f0-9]{64}$/.test(q.contentHash) ||
		!["ISSUED", "ACCEPTED", "EXPIRED", "INVALIDATED"].includes(q.state) ||
		q.currency !== "LKR" ||
		q.synthetic !== true ||
		q.commercialIssuance !== false ||
		q.checkoutEnabled !== false ||
		q.bookingConfirmed !== false ||
		!["BANK", "GATEWAY"].includes(q.paymentMethod) ||
		!minor(q.totalMinor) ||
		!q.package ||
		typeof q.package.name !== "string" ||
		!Array.isArray(q.optionalServiceIds) ||
		q.optionalServiceIds.some((id) => typeof id !== "string") ||
		!Array.isArray(q.disclosures) ||
		JSON.stringify(q.disclosures) !== JSON.stringify(quoteDisclosures) ||
		![q.expiresAt, q.holdExpiresAt, q.serverNow].every(
			(v) => typeof v === "string" && Number.isFinite(Date.parse(v)),
		) ||
		Date.parse(q.expiresAt) > Date.parse(q.holdExpiresAt) ||
		!Array.isArray(q.lines) ||
		q.lines.length === 0 ||
		q.lines.some(
			(l) =>
				!l ||
				typeof l.lineId !== "string" ||
				typeof l.label !== "string" ||
				!Number.isSafeInteger(l.quantity) ||
				l.quantity < 0 ||
				!minor(l.unitMinor) ||
				!minor(l.amountMinor),
		)
	)
		throw new Error("Invalid synthetic quotation");
	try {
		new Intl.DateTimeFormat("en-GB", { timeZone: q.timezone }).format(
			new Date(q.expiresAt),
		);
	} catch {
		throw new Error("Invalid synthetic quotation timezone");
	}
	return q;
}

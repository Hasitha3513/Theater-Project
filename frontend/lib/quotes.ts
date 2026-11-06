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

/** A read-only explanation of known inputs; never an issued or payable quotation. */
export type CommercialQuotePreview = {
	classification: "SYNTHETIC_PREVIEW_NOT_OFFER";
	synthetic: true;
	commercialIssuanceEnabled: false;
	checkoutEnabled: false;
	customerAccessEnabled: false;
	commerciallyEligible: false;
	currency: "LKR";
	holdId: string;
	holdRevision: number;
	venueId: string;
	roomId: string;
	packageId: string;
	packageVersionId: string;
	paymentMethod: "BANK" | "GATEWAY";
	optionalServiceIds: string[];
	durationMinutes: number;
	guests: number;
	sessionStart: string;
	sessionEnd: string;
	timezone: string;
	holdExpiresAt: string;
	serverNow: string;
	knownComponentsMinor: string;
	lines: Quote["lines"];
	policyCandidate: {
		policyId: string;
		revision: string;
		contentHash: string;
		draftReviewStatus: string;
		scopeKind: "VENUE" | "PACKAGE";
		packageId: string | null;
		effectiveFromProposal: string | null;
		effectiveUntilProposal: string | null;
	} | null;
	blockers: string[];
};

export type PreviewSelection = {
	holdId: string;
	expectedHoldRevision: number;
	packageId: string;
	packageVersionId: string;
	paymentMethod: string;
	optionalServiceIds: string[];
};
const reference = (v: unknown): v is string =>
	typeof v === "string" &&
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const timestamp = (v: unknown): v is string =>
	typeof v === "string" && Number.isFinite(Date.parse(v));

export function commercialPreviewQuery(
	selection: PreviewSelection,
	pin?: CommercialQuotePreview["policyCandidate"],
) {
	if (
		![selection.holdId, selection.packageId, selection.packageVersionId].every(
			reference,
		) ||
		!Number.isSafeInteger(selection.expectedHoldRevision) ||
		selection.expectedHoldRevision < 0 ||
		!["BANK", "GATEWAY"].includes(selection.paymentMethod) ||
		selection.optionalServiceIds.length > 32 ||
		!selection.optionalServiceIds.every(reference) ||
		new Set(selection.optionalServiceIds).size !==
			selection.optionalServiceIds.length
	)
		throw new Error("Invalid synthetic preview selection");
	const query = new URLSearchParams({
		holdId: selection.holdId,
		expectedHoldRevision: String(selection.expectedHoldRevision),
		packageId: selection.packageId,
		packageVersionId: selection.packageVersionId,
		paymentMethod: selection.paymentMethod,
		optionalServiceIds: [...selection.optionalServiceIds].sort().join(","),
	});
	if (pin) {
		if (
			!reference(pin.policyId) ||
			!/^([1-9][0-9]{0,18})$/.test(pin.revision) ||
			!/^[a-f0-9]{64}$/.test(pin.contentHash)
		)
			throw new Error("Invalid preview policy pin");
		query.set("policyId", pin.policyId);
		query.set("policyRevision", pin.revision);
		query.set("policyContentHash", pin.contentHash);
	}
	return query;
}

export function validateCommercialPreview(
	value: unknown,
): CommercialQuotePreview {
	const fail = () => {
		throw new Error("Invalid non-offer commercial preview");
	};
	if (!value || typeof value !== "object" || Array.isArray(value))
		return fail();
	const p = value as CommercialQuotePreview;
	if (
		["quoteId", "totalMinor", "payableTotal", "state", "accepted"].some(
			(key) => key in p,
		) ||
		p.classification !== "SYNTHETIC_PREVIEW_NOT_OFFER" ||
		p.synthetic !== true ||
		p.commercialIssuanceEnabled !== false ||
		p.checkoutEnabled !== false ||
		p.customerAccessEnabled !== false ||
		p.commerciallyEligible !== false ||
		p.currency !== "LKR" ||
		![p.holdId, p.venueId, p.roomId, p.packageId, p.packageVersionId].every(
			reference,
		) ||
		!Number.isSafeInteger(p.holdRevision) ||
		p.holdRevision < 0 ||
		!Number.isSafeInteger(p.durationMinutes) ||
		p.durationMinutes <= 0 ||
		!Number.isSafeInteger(p.guests) ||
		p.guests <= 0 ||
		!["BANK", "GATEWAY"].includes(p.paymentMethod) ||
		!Array.isArray(p.optionalServiceIds) ||
		!p.optionalServiceIds.every(reference) ||
		new Set(p.optionalServiceIds).size !== p.optionalServiceIds.length ||
		![p.sessionStart, p.sessionEnd, p.holdExpiresAt, p.serverNow].every(
			timestamp,
		) ||
		Date.parse(p.sessionEnd) - Date.parse(p.sessionStart) !==
			p.durationMinutes * 60000 ||
		!minor(p.knownComponentsMinor) ||
		!Array.isArray(p.lines) ||
		p.lines.length === 0 ||
		!Array.isArray(p.blockers) ||
		p.blockers.length === 0 ||
		p.blockers.some(
			(b) => typeof b !== "string" || !/^[A-Z][A-Z0-9_]{0,99}$/.test(b),
		)
	)
		return fail();
	let sum = 0n;
	for (const line of p.lines) {
		if (
			!line ||
			typeof line.lineId !== "string" ||
			!line.lineId ||
			typeof line.kind !== "string" ||
			typeof line.label !== "string" ||
			!line.label ||
			!Number.isSafeInteger(line.quantity) ||
			line.quantity < 0 ||
			!minor(line.unitMinor) ||
			!minor(line.amountMinor) ||
			BigInt(line.quantity) * BigInt(line.unitMinor) !==
				BigInt(line.amountMinor)
		)
			return fail();
		sum += BigInt(line.amountMinor);
	}
	if (sum !== BigInt(p.knownComponentsMinor)) return fail();
	if (p.policyCandidate !== null) {
		const c = p.policyCandidate;
		if (
			!c ||
			!reference(c.policyId) ||
			!/^[1-9][0-9]{0,18}$/.test(c.revision) ||
			!/^[a-f0-9]{64}$/.test(c.contentHash) ||
			![
				"DRAFT",
				"PENDING_REVIEW",
				"APPROVED",
				"REJECTED",
				"INCOMPLETE",
			].includes(c.draftReviewStatus) ||
			!["VENUE", "PACKAGE"].includes(c.scopeKind) ||
			(c.scopeKind === "VENUE"
				? c.packageId !== null
				: c.packageId !== p.packageId) ||
			(c.effectiveFromProposal !== null &&
				!timestamp(c.effectiveFromProposal)) ||
			(c.effectiveUntilProposal !== null &&
				!timestamp(c.effectiveUntilProposal)) ||
			["reviewerId", "evidenceReference", "grantId", "actorId"].some(
				(key) => key in c,
			)
		)
			return fail();
	}
	try {
		new Intl.DateTimeFormat("en-GB", { timeZone: p.timezone }).format(
			new Date(p.serverNow),
		);
	} catch {
		return fail();
	}
	return p;
}

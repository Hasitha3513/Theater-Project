import { RoomRequestError, roomRequest } from "./rooms";

export { RoomRequestError };
export const policyNotice =
	"Reviewed draft — not active; commercial issuance disabled.";
export const maxApprovalEnvelopeBytes = 4 * 1024;
const policyIdPattern =
	"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const activationPath = new RegExp(
	`^/api/admin/venues/${policyIdPattern}/commercial-policies/${policyIdPattern}/activation$`,
);
const exactPolicyId = new RegExp(`^${policyIdPattern}$`);

export function isPolicyActivationPath(path: string) {
	return activationPath.test(path);
}

export type ActivationScope = {
	venueId: string;
	policyId: string;
	policyRevision: number;
	policyHash: string;
	effectiveFrom: string;
	effectiveUntil: string | null;
};

export function policyActivationPath(venueId: string, policyId: string) {
	if (!exactPolicyId.test(venueId) || !exactPolicyId.test(policyId))
		return undefined;
	return `/api/admin/venues/${venueId}/commercial-policies/${policyId}/activation`;
}

export function activationScope(
	value: unknown,
	venueId: string,
	policyId: string,
): ActivationScope | undefined {
	if (
		!value ||
		typeof value !== "object" ||
		!("permitted" in value) ||
		value.permitted !== true
	)
		return undefined;
	const projected = value as Partial<ActivationScope>;
	if (
		!policyActivationPath(venueId, policyId) ||
		projected.venueId !== venueId ||
		projected.policyId !== policyId ||
		typeof projected.policyRevision !== "number" ||
		!Number.isSafeInteger(projected.policyRevision) ||
		projected.policyRevision < 0 ||
		typeof projected.policyHash !== "string" ||
		!/^[a-f0-9]{64}$/.test(projected.policyHash) ||
		typeof projected.effectiveFrom !== "string" ||
		!Number.isFinite(Date.parse(projected.effectiveFrom)) ||
		(projected.effectiveUntil !== null &&
			(typeof projected.effectiveUntil !== "string" ||
				Date.parse(projected.effectiveUntil) <=
					Date.parse(projected.effectiveFrom) ||
				!Number.isFinite(Date.parse(projected.effectiveUntil))))
	)
		return undefined;
	return {
		venueId,
		policyId,
		policyRevision: projected.policyRevision,
		policyHash: projected.policyHash,
		effectiveFrom: projected.effectiveFrom,
		effectiveUntil: projected.effectiveUntil,
	};
}

export function validActivationSelection(
	roomId: string,
	packageVersionId: string,
	effectiveFrom: string,
	effectiveUntil: string,
) {
	const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
	return (
		exactPolicyId.test(roomId) &&
		exactPolicyId.test(packageVersionId) &&
		utc.test(effectiveFrom) &&
		utc.test(effectiveUntil) &&
		Date.parse(effectiveFrom) < Date.parse(effectiveUntil)
	);
}

export function activationReceipt(value: unknown) {
	if (
		!value ||
		typeof value !== "object" ||
		!("activationId" in value) ||
		typeof value.activationId !== "string" ||
		!exactPolicyId.test(value.activationId)
	)
		throw new Error("Invalid activation receipt");
	return { activationId: value.activationId };
}

const approvalPath = new RegExp(
	`^(/api/admin/venues/${policyIdPattern}/commercial-policies)/(${policyIdPattern})/trusted-approvals$`,
);
const approvalUpstreamPath = new RegExp(
	`^/api/admin/venues/${policyIdPattern}/commercial-policies/trusted-approvals$`,
);

export function approvalTarget(path: string) {
	const match = approvalPath.exec(path);
	return match
		? { upstream: `${match[1]}/trusted-approvals`, policyId: match[2] }
		: null;
}

export function validApprovalEnvelope(value: unknown): value is string {
	return (
		typeof value === "string" &&
		value.trim().length > 0 &&
		new TextEncoder().encode(value).length <= maxApprovalEnvelopeBytes
	);
}

export function approvalMessage(error: unknown): string {
	if (error instanceof RoomRequestError) {
		if (error.status === 404)
			return "Synthetic signed approval intake is disabled or unavailable.";
		if (error.status === 503)
			return "Synthetic signed approval intake is unavailable. Verify the outcome before submitting again.";
		if (error.status === 400 || error.status === 413)
			return "The signed envelope was rejected. Check the format, size and selected revision.";
	}
	return policyMessage(error);
}
export type PolicyDocument = Record<
	string,
	string | null | boolean | string[] | Fee[]
>;
export type Fee = {
	method: string;
	channel: string;
	type: string;
	amountMinor: string | null;
	currency: string;
	refundability: string;
};
export type Policy = {
	policyId: string;
	venueId: string;
	packageId: string | null;
	revision: string;
	contentHash: string;
	status: string;
	venueTimezone: string;
	document: PolicyDocument;
	reviews: Record<string, string>[];
	permittedActions: string[];
	commercialIssuanceEnabled: false;
	checkoutEnabled: false;
	customerAccessEnabled: false;
};
export const sections = [
	{
		title: "Tax",
		fields: [
			[
				"taxApplicability",
				"Tax applicability",
				["UNRESOLVED", "APPLICABLE", "NOT_APPLICABLE", "EXEMPT"],
			],
			[
				"taxType",
				"Tax calculation",
				["UNRESOLVED", "NONE", "PERCENTAGE", "FIXED_PER_SESSION"],
			],
			["taxRateBasisPoints", "Tax rate in basis points", null],
			["taxAmountMinor", "Tax amount in LKR minor units", null],
			[
				"taxTreatment",
				"Tax treatment",
				["UNRESOLVED", "NONE", "INCLUSIVE", "EXCLUSIVE"],
			],
			[
				"taxBasis",
				"Tax basis",
				[
					"UNRESOLVED",
					"NONE",
					"PACKAGE_COMPONENTS",
					"PACKAGE_COMPONENTS_AND_METHOD_FEE",
				],
			],
			[
				"taxRounding",
				"Tax rounding",
				["UNRESOLVED", "NONE", "HALF_UP", "HALF_EVEN"],
			],
		],
	},
	{
		title: "Cancellation",
		fields: [
			[
				"cancellationEligibility",
				"Cancellation eligibility",
				["UNRESOLVED", "NOT_ALLOWED", "BEFORE_START", "REVIEW_REQUIRED"],
			],
			["cancellationCutoffMinutes", "Cutoff before start (minutes)", null],
			[
				"noShowTreatment",
				"No-show treatment",
				["UNRESOLVED", "NO_REFUND", "REVIEW_REQUIRED"],
			],
			[
				"exceptionHandling",
				"Exceptions",
				["UNRESOLVED", "NONE", "MANUAL_REVIEW_REQUIRED"],
			],
			["exceptionNotes", "Exception notes", null],
		],
	},
	{
		title: "Refunds",
		fields: [
			[
				"refundEligibility",
				"Refund eligibility",
				["UNRESOLVED", "NONE", "ELIGIBLE_CANCELLATION", "MANUAL_REVIEW"],
			],
			[
				"refundCalculation",
				"Refund calculation",
				["UNRESOLVED", "NONE", "PERCENTAGE", "FIXED_MINOR"],
			],
			[
				"refundBasis",
				"Refund basis",
				["UNRESOLVED", "NONE", "ELIGIBLE_PRINCIPAL"],
			],
			["refundRateBasisPoints", "Refund percentage in basis points", null],
			["refundAmountMinor", "Refund amount in LKR minor units", null],
			[
				"refundDestination",
				"Refund destination",
				["UNRESOLVED", "NONE", "ORIGINAL_VERIFIED_PAYER"],
			],
		],
	},
] as const;
export function emptyDocument(): PolicyDocument {
	const d: PolicyDocument = {
		title: "",
		effectiveFromProposal: null,
		effectiveUntilProposal: null,
		paymentMethods: [],
		feeChannels: [],
		fees: [],
		cancellationTimeBasis: "VENUE_TIMEZONE",
		nonRefundableComponents: null,
		suppliedReferences: [],
		submittedForReview: false,
	};
	for (const section of sections)
		for (const [key, , choices] of section.fields)
			d[key] = choices ? "UNRESOLVED" : key === "exceptionNotes" ? "" : null;
	return d;
}
export function validateDocument(d: PolicyDocument): Record<string, string> {
	const errors: Record<string, string> = {};
	if (typeof d.title !== "string" || !d.title.trim() || d.title.length > 200)
		errors.title = "Enter a title of 1–200 characters.";
	for (const key of [
		"taxAmountMinor",
		"refundAmountMinor",
		"taxRateBasisPoints",
		"refundRateBasisPoints",
		"cancellationCutoffMinutes",
	]) {
		const v = d[key];
		if (v === null) continue;
		if (typeof v !== "string" || !/^(0|[1-9][0-9]{0,18})$/.test(v))
			errors[key] = "Use a whole non-negative decimal value.";
		else if (
			BigInt(v) >
			(key.includes("BasisPoints")
				? 10000n
				: key === "cancellationCutoffMinutes"
					? 525600n
					: 9223372036854775807n)
		)
			errors[key] = "Value exceeds the supported range.";
	}
	const from = d.effectiveFromProposal,
		to = d.effectiveUntilProposal;
	if (
		from !== null &&
		(typeof from !== "string" || !Number.isFinite(Date.parse(from)))
	)
		errors.effectiveFromProposal = "Choose a valid UTC start.";
	if (
		to !== null &&
		(typeof to !== "string" ||
			typeof from !== "string" ||
			!Number.isFinite(Date.parse(to)) ||
			Date.parse(to) <= Date.parse(from))
	)
		errors.effectiveUntilProposal = "End must follow the proposed start.";
	return errors;
}
export function policyRoute(path: string, method: string): boolean {
	if (isPolicyActivationPath(path))
		return method === "GET" || method === "POST";
	if (approvalPath.test(path) || approvalUpstreamPath.test(path))
		return method === "POST";
	const id = policyIdPattern;
	const match = new RegExp(
		`^/api/admin/venues/${id}/commercial-policies(?:/(${id})(?:/(history|reviews))?)?$`,
	).exec(path);
	if (!match) return false;
	if (!match[1]) return ["GET", "POST"].includes(method);
	if (match[2] === "reviews") return method === "POST";
	if (match[2] === "history") return method === "GET";
	return ["GET", "PUT"].includes(method);
}
export async function policyRequest<T>(
	path: string,
	init?: RequestInit,
): Promise<T> {
	if (
		!policyRoute(
			new URL(path, "http://localhost").pathname,
			init?.method ?? "GET",
		)
	)
		throw new Error("Unsupported policy operation");
	return roomRequest<T>(path, init);
}
export function policyMessage(error: unknown): string {
	if (error instanceof RoomRequestError)
		return error.status === 401
			? "Your session expired. Sign in again; your inputs are retained."
			: error.status === 403
				? "You do not have the required permission for this venue or review."
				: error.status === 409
					? "The revision or review changed. Reload and compare before submitting a new command."
					: error.status === 400
						? "Check the policy fields and dependent selections. The server rejected this draft."
						: error.status === 413
							? "The draft exceeds the 64 KiB request limit."
							: "The write outcome may be uncertain. Inspect the saved history before issuing another command.";
	return "Request unavailable. Your inputs are retained; no write is automatically retried.";
}

export type PolicyVenue = {
	venueId: string;
	displayName: string;
	timezone: string;
};
export function policyVenues(value: unknown): PolicyVenue[] {
	if (!Array.isArray(value)) throw new Error("Invalid authorized venue list");
	return value.map((v) => {
		if (
			!v ||
			typeof v.venueId !== "string" ||
			!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
				v.venueId,
			) ||
			typeof v.displayName !== "string" ||
			!v.displayName.trim() ||
			typeof v.timezone !== "string"
		)
			throw new Error("Invalid authorized venue");
		new Intl.DateTimeFormat("en", { timeZone: v.timezone });
		return {
			venueId: v.venueId,
			displayName: v.displayName,
			timezone: v.timezone,
		};
	});
}

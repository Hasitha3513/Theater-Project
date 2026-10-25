import { RoomRequestError } from "./rooms";
export type InstructionContext = {
	venue: string;
	target: string;
	kind: "revision" | "request";
};
export type BankInstruction = {
	venueId: string;
	instructionRevisionId: string;
	instructionVersion: string;
	mode: "STAGING_ONLY";
	effectiveFrom: string;
	effectiveUntil: string;
	approvedAt: string;
	observedAt: string;
	approvalEvidence: "STAGING_FIXTURE";
	independentApprovalRecorded: true;
	instruction: {
		label: "NON-PAYABLE STAGING FIXTURE";
		accountDisplay: "NO REAL RECEIVING ACCOUNT";
		warning: "DO NOT TRANSFER MONEY";
	};
	synthetic: true;
	collectionEnabled: false;
	requestId: string | null;
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export function instructionPath(c: InstructionContext) {
	if (
		!uuid.test(c.venue) ||
		!uuid.test(c.target) ||
		!["request", "revision"].includes(c.kind)
	)
		throw new Error("Invalid instruction context");
	return c.kind === "revision"
		? `/api/admin/venues/${c.venue}/bank/instructions/${c.target}`
		: `/api/admin/venues/${c.venue}/bank/requests/${c.target}/instructions`;
}
export function parseInstruction(
	value: unknown,
	c: InstructionContext,
): BankInstruction {
	instructionPath(c);
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw new Error("Invalid instruction response");
	const v = value as Record<string, unknown>;
	const instant = (x: unknown): x is string =>
		typeof x === "string" &&
		/^\d{4}-\d{2}-\d{2}T.*Z$/.test(x) &&
		Number.isFinite(Date.parse(x));
	if (
		v.venueId !== c.venue ||
		typeof v.instructionRevisionId !== "string" ||
		!uuid.test(v.instructionRevisionId) ||
		(c.kind === "revision" && v.instructionRevisionId !== c.target) ||
		v.requestId !== (c.kind === "request" ? c.target : null) ||
		typeof v.instructionVersion !== "string" ||
		!/^SYNTHETIC-[A-Za-z0-9_-]{1,80}$/.test(v.instructionVersion) ||
		v.mode !== "STAGING_ONLY" ||
		v.synthetic !== true ||
		v.collectionEnabled !== false ||
		v.approvalEvidence !== "STAGING_FIXTURE" ||
		v.independentApprovalRecorded !== true ||
		!instant(v.effectiveFrom) ||
		!instant(v.effectiveUntil) ||
		!instant(v.approvedAt) ||
		!instant(v.observedAt) ||
		Date.parse(v.approvedAt) > Date.parse(v.effectiveFrom) ||
		Date.parse(v.effectiveFrom) >= Date.parse(v.effectiveUntil)
	)
		throw new Error("Invalid instruction response");
	const i = v.instruction as Record<string, unknown> | undefined;
	if (
		i?.label !== "NON-PAYABLE STAGING FIXTURE" ||
		i.accountDisplay !== "NO REAL RECEIVING ACCOUNT" ||
		i.warning !== "DO NOT TRANSFER MONEY"
	)
		throw new Error("Invalid non-payable fixture");
	return {
		venueId: c.venue,
		instructionRevisionId: v.instructionRevisionId,
		instructionVersion: v.instructionVersion,
		mode: "STAGING_ONLY",
		effectiveFrom: v.effectiveFrom,
		effectiveUntil: v.effectiveUntil,
		approvedAt: v.approvedAt,
		observedAt: v.observedAt,
		approvalEvidence: "STAGING_FIXTURE",
		independentApprovalRecorded: true,
		instruction: {
			label: "NON-PAYABLE STAGING FIXTURE",
			accountDisplay: "NO REAL RECEIVING ACCOUNT",
			warning: "DO NOT TRANSFER MONEY",
		},
		synthetic: true,
		collectionEnabled: false,
		requestId: c.kind === "request" ? c.target : null,
	};
}
export async function loadInstruction(
	c: InstructionContext,
	signal: AbortSignal,
) {
	const response = await fetch(instructionPath(c), {
		cache: "no-store",
		signal,
	});
	if (!response.ok)
		throw new RoomRequestError(
			response.status,
			"Staging instruction read failed",
		);
	const value: unknown = await response.json();
	signal.throwIfAborted();
	return parseInstruction(value, c);
}

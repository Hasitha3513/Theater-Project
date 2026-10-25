export const venue = "00000000-0000-4000-8000-000000000001",
	revision = "00000000-0000-4000-8000-000000000002",
	requestId = "00000000-0000-4000-8000-000000000003";
export const context = { venue, target: revision, kind: "revision" };
export const fixture = (c = context) => ({
	venueId: c.venue,
	instructionRevisionId: revision,
	instructionVersion: "SYNTHETIC-BI-V1",
	mode: "STAGING_ONLY",
	effectiveFrom: "2026-10-25T00:00:00Z",
	effectiveUntil: "2026-10-26T00:00:00Z",
	approvedAt: "2026-10-24T00:00:00Z",
	observedAt: "2026-10-25T05:00:00Z",
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
});

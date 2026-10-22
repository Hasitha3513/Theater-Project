import { bankReport, bankReportPath } from "../components/bank-report";
import { RoomRequestError, roomRequest } from "./rooms";
import { readSession } from "./session";

export const summaryLabels = {
	c: "Verified synthetic credits",
	a: "Allocated",
	t: "Confirmed synthetic returns",
	r: "Active refund commitments",
	u: "Unallocated residual",
	f: "Uncommitted residual",
	available: "Credit-eligible free balance",
	unavailable: "Restricted free balance",
} as const;
export type SummaryContext = {
	venue: string;
	currency: string;
	from: string;
	to: string;
};
export type AdminBankSummary = {
	venueId: string;
	currency: string;
	timezone: string;
	from: string;
	to: string;
	snapshotAt: string;
	metricVersion: string;
	amounts: Record<keyof typeof summaryLabels, string | null>;
};
function date(value: string) {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith("0000"))
		return false;
	const parsed = new Date(`${value}T00:00:00Z`);
	return (
		Number.isFinite(parsed.getTime()) &&
		parsed.toISOString().slice(0, 10) === value
	);
}
export function summaryQuery(context: SummaryContext) {
	bankReportPath(context.venue);
	if (
		!/^[A-Z]{3}$/.test(context.currency) ||
		!date(context.from) ||
		!date(context.to) ||
		context.from >= context.to
	)
		throw Error("Select a venue, currency and increasing date range.");
	// The configured maximum is enforced by the existing backend; no invented client limit.
	return new URLSearchParams({
		currency: context.currency,
		from: context.from,
		to: context.to,
		page: "0",
		size: "1",
	});
}
export function projectBankSummary(
	value: unknown,
	context: SummaryContext,
): AdminBankSummary {
	const query = summaryQuery(context),
		report = bankReport(value, context.venue, context.currency);
	const filters = (value as { filters?: Record<string, unknown> }).filters;
	if (
		!filters ||
		[...query].some(([key, expected]) => filters[key] !== expected) ||
		report.page !== 0 ||
		report.size !== 1 ||
		typeof report.timezone !== "string"
	)
		throw Error("Report context changed.");
	new Intl.DateTimeFormat("en", { timeZone: report.timezone });
	const amounts = {} as AdminBankSummary["amounts"];
	for (const key of Object.keys(
		summaryLabels,
	) as (keyof typeof summaryLabels)[])
		amounts[key] = report.snapshot.summary[key];
	// No row-level credit references, evidence or unknown response fields are retained.
	return {
		venueId: report.venueId,
		currency: report.currency,
		timezone: report.timezone,
		from: context.from,
		to: context.to,
		snapshotAt: report.snapshot.snapshotAt,
		metricVersion: report.snapshot.metricVersion,
		amounts,
	};
}
export async function loadBankSummary(
	context: SummaryContext,
	signal: AbortSignal,
) {
	const query = summaryQuery(context);
	const session = () =>
		readSession((input, init) => fetch(input, { ...init, signal }));
	const before = await session();
	if (before.kind !== "authenticated")
		throw new RoomRequestError(
			before.kind === "denied"
				? 403
				: before.kind === "unauthenticated"
					? 401
					: 503,
			"Session unavailable.",
		);
	const raw = await roomRequest(`${bankReportPath(context.venue)}?${query}`, {
		signal,
	});
	const after = await session();
	if (
		after.kind !== "authenticated" ||
		before.account.accountId !== after.account.accountId
	)
		throw Error("Session changed.");
	return projectBankSummary(raw, context);
}

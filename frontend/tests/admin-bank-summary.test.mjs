import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	summaryQuery,
	projectBankSummary,
	loadBankSummary,
	summaryLabels,
} from "../lib/admin-bank-summary.ts";
import { AdminBankSummarySection } from "../components/admin-bank-summary.tsx";
const venue = "00000000-0000-4000-8000-000000000001",
	context = { venue, currency: "LKR", from: "2026-10-01", to: "2026-10-02" };
const amount = "900719925474099312345678901234567890";
const fixture = () => ({
	synthetic: true,
	realFinancialReporting: false,
	venueId: venue,
	currency: "LKR",
	timezone: "Asia/Colombo",
	page: 0,
	size: 1,
	filters: Object.fromEntries(summaryQuery(context)),
	snapshotPolicy: "CURRENT_REQUEST_ONLY",
	snapshot: {
		snapshotAt: "2026-10-22T00:00:00Z",
		metricVersion: "SYNTHETIC-BANK-BALANCES-V1",
		total: 101,
		summary: {
			c: amount,
			a: "1",
			t: "2",
			r: "3",
			u: "4",
			f: "5",
			available: null,
			unavailable: "6",
		},
		rows: [],
	},
});
test("exact fixed-page query includes explicit venue-local inclusive/exclusive date boundaries", () => {
	assert.equal(
		summaryQuery(context).toString(),
		"currency=LKR&from=2026-10-01&to=2026-10-02&page=0&size=1",
	);
});
test("reject malformed scopes, currencies and non-increasing or nonexistent dates", () => {
	for (const patch of [
		{ venue: "" },
		{ venue: "../x" },
		{ currency: "" },
		{ currency: "lkr" },
		{ currency: "LKR&secret=x" },
		{ from: "" },
		{ to: "" },
		{ from: "2026-02-30" },
		{ from: "0000-01-01" },
		{ from: context.to },
		{ from: "2026-10-03" },
		{ to: "2026-1-2" },
	])
		assert.throws(() => summaryQuery({ ...context, ...patch }));
});
test("range maximum remains backend-controlled without an invented client limit", () => {
	assert(summaryQuery({ ...context, from: "2020-01-01", to: "2026-01-01" }));
});
test("exact huge minor units and null survive without row-derived totals", () => {
	const raw = fixture();
	raw.snapshot.rows = [
		{
			creditId: venue,
			creditedAt: "2026-10-01T00:00:00Z",
			verifiedAt: "2026-10-01T00:00:00Z",
			amounts: { ...raw.snapshot.summary, c: "1" },
			eligibility: "ELIGIBLE",
			exceptions: [],
			creditDrilldown: false,
		},
	];
	const result = projectBankSummary(raw, context);
	assert.equal(result.amounts.c, amount);
	assert.equal(result.amounts.available, null);
	assert.deepEqual(Object.keys(result.amounts), Object.keys(summaryLabels));
	assert(!JSON.stringify(result).includes("creditId"));
	assert(!("rows" in result));
});
test("unknown/private fields never enter summary state", () => {
	const raw = fixture();
	raw.secret = "SECRET";
	raw.snapshot.summary.secret = "SECRET";
	assert(!JSON.stringify(projectBankSummary(raw, context)).includes("SECRET"));
});
for (const [label, change] of Object.entries({
	venue: (r) => (r.venueId = "wrong"),
	currency: (r) => (r.currency = "USD"),
	real: (r) => (r.realFinancialReporting = true),
	filter: (r) => (r.filters.from = "2026-09-01"),
	page: (r) => (r.page = 2),
	size: (r) => (r.size = 25),
	timezone: (r) => (r.timezone = "invalid"),
	version: (r) => (r.snapshot.metricVersion = "wrong"),
	timestamp: (r) => (r.snapshot.snapshotAt = "invalid"),
	money: (r) => (r.snapshot.summary.c = 9007199254740992),
	missing: (r) => delete r.snapshot.summary.f,
	negative: (r) => (r.snapshot.summary.r = "-1"),
}))
	test(`reject malformed or mismatched ${label}`, () => {
		const raw = fixture();
		change(raw);
		assert.throws(() => projectBankSummary(raw, context));
	});
test("session checked before and after GET; return no account identity", async () => {
	const original = globalThis.fetch,
		calls = [];
	globalThis.fetch = async (path, init) => {
		calls.push([path, init]);
		return Response.json(
			path === "/api/accounts/me"
				? { accountId: venue, eligibility: "ELIGIBLE" }
				: fixture(),
		);
	};
	try {
		const result = await loadBankSummary(context, new AbortController().signal);
		assert.equal(result.amounts.c, amount);
		assert.equal(calls.length, 3);
		assert(calls.every(([, init]) => !init.method || init.method === "GET"));
		assert(calls.every(([, init]) => init.cache === "no-store"));
		assert(!JSON.stringify(result).includes("accountId"));
	} finally {
		globalThis.fetch = original;
	}
});
test("changed session prevents late financial result", async () => {
	const original = globalThis.fetch;
	let reads = 0;
	globalThis.fetch = async (path) =>
		Response.json(
			path === "/api/accounts/me"
				? {
						accountId:
							++reads === 1 ? venue : "00000000-0000-4000-8000-000000000002",
						eligibility: "ELIGIBLE",
					}
				: fixture(),
		);
	try {
		await assert.rejects(
			loadBankSummary(context, new AbortController().signal),
		);
	} finally {
		globalThis.fetch = original;
	}
});
test("initial section is explicit, accessible and contains no financial values", () => {
	const html = renderToStaticMarkup(
		React.createElement(AdminBankSummarySection),
	);
	assert(html.includes("Synthetic Bank Summary"));
	assert(html.includes("Load Bank Summary"));
	assert(html.includes('role="status"'));
	assert(html.includes('aria-live="polite"'));
	assert(!html.includes("Complete-population summary"));
	assert(!html.includes("<main"));
	assert(html.includes("APPROVED does not mean REPAID"));
	assert(html.includes("No independently verified real bank balance"));
	assert(!html.includes(amount));
});
for (const [upstream, expected] of [
	[401, 401],
	[403, 403],
	[503, 503],
	[502, 503],
])
	test(`session HTTP ${upstream} fails closed without misreporting provider failure as expiry`, async () => {
		const original = globalThis.fetch;
		let calls = 0;
		globalThis.fetch = async () => {
			calls++;
			return new Response(null, { status: upstream });
		};
		try {
			await assert.rejects(
				loadBankSummary(context, new AbortController().signal),
				(error) => error.status === expected,
			);
			assert.equal(calls, 1);
		} finally {
			globalThis.fetch = original;
		}
	});

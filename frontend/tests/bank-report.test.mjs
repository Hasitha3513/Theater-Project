import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	bankReport,
	bankReportPath,
	BankReportWorkspace,
	REPORT_LABEL,
} from "../components/bank-report.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001",
	amounts = {
		c: "9007199254740993",
		a: "0",
		t: "0",
		r: "0",
		u: "9007199254740993",
		f: "9007199254740993",
		available: "9007199254740993",
		unavailable: "0",
	};
const report = {
	synthetic: true,
	realFinancialReporting: false,
	venueId: id,
	currency: "LKR",
	timezone: "Asia/Colombo",
	page: 0,
	size: 25,
	snapshotPolicy: "CURRENT_REQUEST_ONLY",
	snapshot: {
		snapshotAt: "2026-10-19T00:00:00Z",
		metricVersion: "SYNTHETIC-BANK-BALANCES-V1",
		total: 1,
		summary: amounts,
		rows: [
			{
				creditId: id,
				creditedAt: "2026-10-19T00:00:00Z",
				verifiedAt: "2026-10-19T00:00:00Z",
				amounts,
				eligibility: "ELIGIBLE",
				exceptions: [],
				creditDrilldown: false,
			},
		],
	},
};
test("exact large minor-unit strings and nullable eligibility survive without client totals", () => {
	assert.equal(
		bankReport(report, id, "LKR").snapshot.summary.c,
		"9007199254740993",
	);
	const unavailable = structuredClone(report);
	unavailable.snapshot.rows[0].amounts.available = null;
	unavailable.snapshot.rows[0].eligibility = "DISPUTED";
	assert.equal(
		bankReport(unavailable, id, "LKR").snapshot.rows[0].amounts.available,
		null,
	);
});
test("reject wrong scope, real-data claims, malformed or lossy money and snapshot versions", () => {
	for (const patch of [
		{ venueId: "wrong" },
		{ realFinancialReporting: true },
		{ currency: "USD" },
		{ synthetic: false },
	])
		assert.throws(() => bankReport({ ...report, ...patch }, id, "LKR"));
	for (const c of ["-1", "1.2", "NaN", 9007199254740992]) {
		const bad = structuredClone(report);
		bad.snapshot.summary.c = c;
		assert.throws(() => bankReport(bad, id, "LKR"));
	}
});
test("report transport permits only its scoped GET; no exports or mutations", () => {
	const path = bankReportPath(id);
	assert(bankRoute(path, "GET"));
	assert(!bankRoute(path, "POST"));
	assert(!bankRoute(`${path}/export`, "GET"));
	assert.throws(() => bankReportPath("../"));
});
test("initial report is labelled synthetic and does not render fabricated metrics", () => {
	const html = renderToStaticMarkup(React.createElement(BankReportWorkspace));
	assert(html.includes(REPORT_LABEL));
	assert(!html.includes("Complete-population summary"));
	assert(html.includes('role="status"'));
});

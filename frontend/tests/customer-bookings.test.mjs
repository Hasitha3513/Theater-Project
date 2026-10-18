import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	bookingGroup,
	bookingRecord,
	CUSTOMER_LABEL,
	CustomerBookings,
	money,
	refundRecord,
} from "../components/customer-bookings.tsx";
import { bankRoute, forwardBank } from "../lib/bank-backend.ts";

const id = "00000000-0000-4000-8000-000000000001";
const row = {
	id,
	requestId: id,
	venueId: id,
	state: "SIMULATED_CONFIRMED",
	revision: 1,
	synthetic: true,
	purchase: {
		name: "Historical package",
		description: "Purchased terms",
		packageVersionId: id,
		sessionStart: "2026-10-18T10:00:00Z",
		sessionEnd: "2026-10-18T13:00:00Z",
		timezone: "Asia/Colombo",
		roomId: id,
		amountMinor: "9007199254740993",
		currency: "LKR",
		lines: [],
	},
};
test("three approved read routes retain existing mutations without adding new ones", () => {
	for (const path of [
		"/api/bookings",
		"/api/bank/requests",
		`/api/bank/requests/${id}/refunds`,
	]) {
		assert.equal(bankRoute(path, "GET"), true);
		assert.equal(bankRoute(path, "DELETE"), false);
	}
	assert.equal(bankRoute("/api/bookings", "POST"), false);
	assert.equal(bankRoute(`/api/bookings/${id}/cancel`, "POST"), false);
});
test("historical data and half-open display groups never create a completed state", () => {
	assert.equal(bookingRecord(row), row);
	assert.equal(bookingGroup(row, "2026-10-18T09:59:59Z"), "Upcoming");
	assert.equal(bookingGroup(row, row.purchase.sessionStart), "Current");
	assert.equal(bookingGroup(row, row.purchase.sessionEnd), "Past");
	assert.equal(row.state, "SIMULATED_CONFIRMED");
	assert.equal(money(row.purchase.amountMinor, "LKR"), "LKR 90071992547409.93");
});
test("reject non-synthetic purchase and invented revision/state/timezone", () => {
	for (const change of [
		{ synthetic: false },
		{ revision: 2 },
		{ state: "COMPLETED" },
		{ id: "x" },
		{ purchase: { ...row.purchase, timezone: "Invalid/Zone" } },
	])
		assert.throws(() => bookingRecord({ ...row, ...change }));
});
test("refund approval, unknown and synthetic completion remain distinct from real repayment", () => {
	for (const state of [
		"APPROVED",
		"UNKNOWN_RECONCILIATION_REQUIRED",
		"SIMULATED_CONFIRMED",
	]) {
		const r = {
			id,
			revision: 1,
			state,
			amountMinor: "100",
			currency: "LKR",
			synthetic: true,
			realRepayment: false,
		};
		assert.equal(refundRecord(r), r);
		assert.throws(() => refundRecord({ ...r, realRepayment: true }));
	}
	assert.throws(() =>
		refundRecord({
			id,
			revision: 1,
			state: "PAID",
			synthetic: true,
			realRepayment: false,
		}),
	);
});
test("customer pages have shared accessible read-only synthetic states", () => {
	for (const bookingId of [undefined, id]) {
		const html = renderToStaticMarkup(
			React.createElement(CustomerBookings, { bookingId }),
		);
		assert.ok(html.includes(CUSTOMER_LABEL));
		assert.ok(html.includes('aria-live="polite"'));
		assert.ok(html.includes("No real payment"));
		assert.ok(!html.includes("<form"));
	}
});
test("read transport rejects injected owner and secret query context", async () => {
	for (const query of [
		`ownerId=${id}`,
		`venueId=${id}`,
		"token=secret",
		"page=-1",
		"size=x",
	]) {
		const r = await forwardBank(
			new Request(`https://example.test/api/bookings?${query}`),
		);
		assert.equal(r.status, 404);
		assert.equal(r.headers.get("cache-control"), "no-store");
	}
});

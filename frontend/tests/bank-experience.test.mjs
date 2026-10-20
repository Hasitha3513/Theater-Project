import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	BankSections,
	CustomerBankSummary,
	bankMoney,
	bankDate,
} from "../components/bank-experience.tsx";
const render = (C, p = {}) => renderToStaticMarkup(React.createElement(C, p));
const value = {
	id: "00000000-0000-4000-8000-000000000001",
	quoteId: "00000000-0000-4000-8000-000000000001",
	revision: 1,
	amountMinor: "12352",
	requestState: "OPEN",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	evidence: [],
	history: [],
	claims: [],
};
test("bank display preserves exact minor-unit precision and rejects unsafe numeric claims", () => {
	assert.equal(bankMoney("9007199254740993"), "LKR 90071992547409.93");
	assert.equal(bankMoney("7"), "LKR 0.07");
	assert.equal(bankMoney(0), "LKR 0.00");
	for (const n of [undefined, -1, NaN, 9007199254740992])
		assert.equal(bankMoney(n), "Amount unavailable");
});
test("bank dates label UTC without inventing venue timezone or missing dates", () => {
	assert.match(bankDate("2026-10-20T10:00Z"), /20 Oct 2026, 10:00 UTC/);
	assert.equal(bankDate(), "Not supplied");
	assert.equal(bankDate("invalid"), "invalid");
});
test("customer summary preserves verbatim instructions and distinguishes claim review funds and booking", () => {
	const s = render(CustomerBankSummary, { value });
	assert.match(s, /SYNTHETIC ONLY — NO RECEIVING ACCOUNT/);
	assert.match(s, /LKR 123.52/);
	assert.match(s, /not independently verified bank credit/);
	assert.match(s, /Financial outcome is unavailable or unresolved/);
	assert.doesNotMatch(s, /href="\/bookings\//);
});
test("only simulated confirmed server booking may expose existing detail navigation", () => {
	const financial = {
		allocatedMinor: 12352,
		outstandingMinor: 0,
		syntheticFundingStatus: "FUNDED",
		syntheticBookingStatus: "PENDING",
		bookingId: value.id,
	};
	assert.doesNotMatch(
		render(CustomerBankSummary, { value: { ...value, financial } }),
		/href="\/bookings\//,
	);
	financial.syntheticBookingStatus = "SIMULATED_CONFIRMED";
	const s = render(CustomerBankSummary, { value: { ...value, financial } });
	assert.match(s, /href="\/bookings\/00000000/);
	assert.match(s, /not a real commercial confirmation/);
	assert.match(s, /Refund approval is not an outgoing repayment/);
});
test("bank section guide never claims payment completion or supplies implicit operations", () => {
	const s = render(BankSections);
	assert.match(s, /not completed payment steps/);
	assert.doesNotMatch(s, /<button|<a |checked=|aria-current/);
});

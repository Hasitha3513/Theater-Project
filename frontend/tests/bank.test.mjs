import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { bankRoute, forwardBank } from "../lib/bank-backend.ts";
import { bankLabels, validateBank } from "../lib/bank.ts";
import { BankWorkspace, BankSummary } from "../components/bank.tsx";
const id = "00000000-0000-4000-8000-000000000001";
const value = {
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels: bankLabels,
	requestState: "OPEN",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-20T10:00Z",
	expiresAt: "2026-10-20T10:01Z",
	submitBy: "2026-10-20T09:00Z",
	reviewBy: "2026-10-20T09:30Z",
	overdue: false,
	amountMinor: "12352",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
};
test("exact twelve bank operations exclude credit, allocation, refund and arbitrary proxy paths", () => {
	const paths = [
		["POST", "/api/bank/requests"],
		["GET", `/api/bank/requests/${id}`],
		["POST", `/api/bank/requests/${id}/claims`],
		["POST", `/api/bank/requests/${id}/resubmissions`],
		["POST", `/api/bank/claims/${id}/uploads`],
		["PUT", `/api/bank/uploads/${id}/content`],
		["POST", `/api/bank/uploads/${id}/complete`],
		["GET", `/api/admin/venues/${id}/bank/requests`],
		["GET", `/api/admin/venues/${id}/bank/requests/${id}`],
		["GET", `/api/admin/venues/${id}/bank/requests/${id}/evidence/${id}`],
		["POST", `/api/admin/venues/${id}/bank/requests/${id}/decisions`],
		["POST", `/api/admin/venues/${id}/bank/requests/${id}/escalations`],
	];
	for (const [method, path] of paths) {
		assert.equal(bankRoute(path, method), true);
		assert.equal(bankRoute(path, "DELETE"), false);
	}
	for (const action of ["allocate", "refund", "confirm", "verify-credit"])
		assert.equal(
			bankRoute(`/api/bank/requests/${id}/${action}`, "POST"),
			false,
		);
	assert.equal(bankRoute("/api/bank/../secrets", "GET"), false);
});
test("customer and reviewer shells always show four safety labels", () => {
	for (const reviewer of [false, true]) {
		const html = render(h(BankWorkspace, { reviewer }));
		for (const label of bankLabels) assert.ok(html.includes(label));
		assert.doesNotMatch(html, /Pay now|Confirm booking|Verify funds/);
	}
});
test("server response cannot imply real payment or confirmed booking", () => {
	assert.equal(validateBank(value), value);
	for (const change of [
		{ synthetic: false },
		{ paymentVerified: true },
		{ bookingConfirmed: true },
		{ labels: [] },
		{ revision: -1 },
		{ amountMinor: "1.5" },
		{ currency: "USD" },
		{ requestState: "PAYMENT_SUCCESS" },
	])
		assert.throws(() => validateBank({ ...value, ...change }));
});
test("review outcome, inventory expiry and correction history remain distinct", () => {
	const html = render(
		h(BankSummary, {
			value: {
				...value,
				requestState: "EXPIRED",
				reviewState: "REJECTED",
				inventoryState: "EXPIRED",
				history: [
					{
						id,
						action: "NEEDS_INFORMATION",
						reason: "CORRECTION_REQUIRED",
						at: "2026-10-17",
					},
				],
			},
		}),
	);
	assert.match(html, /EXPIRED \/ REJECTED \/ EXPIRED/);
	assert.match(html, /CORRECTION_REQUIRED/);
	assert.match(html, /minor units/);
	assert.doesNotMatch(html, /object_key|encryption-key/);
});
test("bank transport fails closed when unconfigured and refuses query/path widening", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN;
	delete process.env.CINENEST_AUTH_ORIGIN;
	try {
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}`),
				)
			).status,
			503,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}?download=true`),
				)
			).status,
			404,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}/allocate`, {
						method: "POST",
					}),
				)
			).status,
			404,
		);
	} finally {
		if (prior) process.env.CINENEST_AUTH_ORIGIN = prior;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("same-origin CSRF and accepted image MIME are required before forwarding", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:1";
	try {
		assert.equal(
			(
				await forwardBank(
					new Request("http://localhost/api/bank/requests", {
						method: "POST",
						headers: { "content-type": "application/json" },
						body: "{}",
					}),
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/uploads/${id}/content`, {
						method: "PUT",
						headers: {
							origin: "http://localhost",
							"x-csrf-token": "SYNTHETIC",
							"content-type": "application/pdf",
						},
						body: "%PDF",
					}),
				)
			).status,
			415,
		);
	} finally {
		if (prior) process.env.CINENEST_AUTH_ORIGIN = prior;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});

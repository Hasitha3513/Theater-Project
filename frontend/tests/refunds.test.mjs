import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	refundCommand,
	refundRows,
	RefundWorkspace,
} from "../components/refunds.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
test("refund transport allows only approved bounded methods and paths", () => {
	for (const [p, m] of [
		[`/api/bank/requests/${id}/refunds`, "POST"],
		[`/api/refunds/${id}`, "GET"],
		[`/api/admin/venues/${id}/refunds`, "GET"],
		[`/api/admin/venues/${id}/refunds/${id}/destination-verifications`, "POST"],
		[`/api/admin/venues/${id}/refunds/${id}/decisions`, "POST"],
		[`/api/admin/venues/${id}/refunds/${id}/attempts`, "POST"],
		[`/api/admin/venues/${id}/refunds/${id}/attempts/${id}/evidence`, "POST"],
		[`/api/admin/venues/${id}/refunds/${id}/reconciliations`, "POST"],
	]) {
		assert.equal(bankRoute(p, m), true);
		assert.equal(bankRoute(p, "DELETE"), false);
	}
	assert.equal(bankRoute(`/api/refunds/${id}/payhere`, "POST"), false);
});
test("refund requests preserve exact integer minor strings and immutable command identity", () => {
	const c = refundCommand("REQUEST", "", id, "2", id, "Synthetic reason", {
		creditId: id,
		amountMinor: "9007199254740992",
		currency: "LKR",
	});
	assert.equal(JSON.parse(c.init.body).amountMinor, "9007199254740992");
	assert.equal(JSON.parse(c.init.body).commandKey, id);
});
test("unsafe refund amount or destination route input rejected", () => {
	for (const amount of ["0", "-1", "1.2", "9223372036854775808", "x100"]) {
		assert.throws(() =>
			refundCommand("REQUEST", "", id, "0", id, "SYNTHETIC", {
				creditId: id,
				amountMinor: amount,
				currency: "LKR",
			}),
		);
	}
	assert.throws(() =>
		refundCommand("EVIDENCE:../../send", id, id, "0", id, "SYNTHETIC"),
	);
});
test("refund response cannot present real money or non-synthetic success", () => {
	assert.throws(() =>
		refundRows({ id, revision: 1, synthetic: false, realRepayment: true }),
	);
	assert.deepEqual(refundRows([]), []);
});
test("customer and finance views label synthetic status and use existing labelled primitives", () => {
	for (const finance of [true, false]) {
		const html = renderToStaticMarkup(
			React.createElement(RefundWorkspace, { finance }),
		);
		assert.match(html, /SYNTHETIC REFUND TEST/);
		assert.match(html, /NO REAL MONEY TRANSFERRED/);
		assert.match(html, /APPROVED DOES NOT MEAN REPAID/);
		assert.match(html, /Clear private refund context/);
		assert.match(html, /aria-live="polite"/);
	}
});

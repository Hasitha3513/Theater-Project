import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	amendmentRecord,
	BookingAmendment,
} from "../components/booking-amendment.tsx";
import { bankRoute } from "../lib/bank-backend.ts";

const id = "00000000-0000-4000-8000-000000000001";
const snapshot = {
	package: { name: "Purchased synthetic" },
	packageVersionId: id,
	guests: 4,
	optionalServiceIds: [],
	lines: [{ amountMinor: "100" }],
	totalMinor: "100",
	currency: "LKR",
	paymentMethod: "BANK",
	sessionStart: "2026-10-20T04:30:00Z",
	sessionEnd: "2026-10-20T07:30:00Z",
	timezone: "Asia/Colombo",
};
const proposal = {
	id,
	bookingId: id,
	revision: 0,
	state: "PROPOSED",
	proposalHash: "a".repeat(64),
	expiresAt: "2026-10-18T16:00:00Z",
	original: snapshot,
	proposed: {
		...snapshot,
		sessionStart: "2026-10-20T08:30:00Z",
		sessionEnd: "2026-10-20T11:30:00Z",
	},
	differenceMinor: "0",
	synthetic: true,
	realOperations: false,
	serverNow: "2026-10-18T15:55:00Z",
};
test("only the four bounded amendment routes are transported", () => {
	const root = `/api/bookings/${id}/amendments`;
	assert.equal(bankRoute(root, "POST"), true);
	assert.equal(bankRoute(`${root}/${id}`, "GET"), true);
	for (const verb of ["confirm", "withdrawals"])
		assert.equal(bankRoute(`${root}/${id}/${verb}`, "POST"), true);
	for (const path of [
		`${root}/${id}/extend`,
		`${root}/${id}/refund`,
		`${root}/${id}/confirm/extra`,
		`${root}?owner=other`,
	])
		assert.equal(bankRoute(path, "POST"), false);
	assert.equal(bankRoute(root, "DELETE"), false);
});
test("proposal and committed revision remain explicit synthetic facts", () => {
	assert.equal(amendmentRecord(proposal, id), proposal);
	assert.equal(
		amendmentRecord({ ...proposal, state: "CONFIRMED", revision: 1 }, id).state,
		"CONFIRMED",
	);
});
for (const [label, change] of [
	["money difference", { differenceMinor: "1" }],
	["live operation", { realOperations: true }],
	[
		"wrong owner resource",
		{ bookingId: "00000000-0000-4000-8000-000000000002" },
	],
	["invalid expiry", { expiresAt: "invalid" }],
	["missing acceptance hash", { proposalHash: "" }],
	["unknown state", { state: "PAID" }],
])
	test(`unsafe proposal rejects ${label}`, () =>
		assert.throws(() => amendmentRecord({ ...proposal, ...change }, id)));
test("changed financial lines and purchased version cannot be confirmed", () => {
	for (const change of [
		{ lines: [{ amountMinor: "101" }] },
		{ totalMinor: "101" },
		{ packageVersionId: "other" },
		{ guests: 5 },
		{ optionalServiceIds: [id] },
	])
		assert.throws(() =>
			amendmentRecord(
				{ ...proposal, proposed: { ...proposal.proposed, ...change } },
				id,
			),
		);
});
test("shell uses explicit preparation and protected navigation without promising success", () => {
	const html = renderToStaticMarkup(
		React.createElement(BookingAmendment, {
			booking: { id, revision: 1, purchase: { ...snapshot, roomId: id } },
			onCommitted: () => {},
		}),
	);
	assert.match(html, /NO REAL OPERATIONS/);
	assert.match(html, /Prepare synthetic proposal/);
	assert.match(html, /Original inventory remains reserved/);
	assert.doesNotMatch(html, /Backend confirmed revision 2/);
	assert.match(html, /href="\/access"/);
	assert.match(html, /href="\/notifications"/);
});

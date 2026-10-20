import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	Purchased,
	requestBookingLabel,
} from "../components/customer-bookings.tsx";
const id = "00000000-0000-4000-8000-000000000001";
const booking = {
	id,
	requestId: id,
	venueId: id,
	state: "SIMULATED_CONFIRMED",
	revision: 1,
	synthetic: true,
	purchase: {
		name: "SYNTHETIC retained package",
		description: "Immutable original terms",
		packageVersionId: id,
		roomId: id,
		sessionStart: "2026-10-20T04:30Z",
		sessionEnd: "2026-10-20T07:30Z",
		timezone: "Asia/Colombo",
		amountMinor: "30007",
		currency: "LKR",
		lines: [
			{
				lineId: "BASE",
				label: "SYNTHETIC purchased session",
				quantity: "1",
				amountMinor: "30007",
			},
		],
	},
};
const render = (b) =>
	renderToStaticMarkup(React.createElement(Purchased, { b }));
test("purchased summary keeps retained package time currency and exact original amount", () => {
	const s = render(booking);
	assert.match(s, /SYNTHETIC retained package/);
	assert.match(s, /Original purchase: LKR 300.07/);
	assert.match(s, /20 Oct 2026, 10:00/);
	assert.match(s, /Asia\/Colombo/);
	assert.match(s, /SIMULATED_CONFIRMED/);
});
test("technical references and immutable line items follow the main summary in a disclosure", () => {
	const s = render(booking);
	assert.ok(s.indexOf("Scheduled session") < s.indexOf("<details"));
	assert.match(s, /<summary>Purchased references and line items<\/summary>/);
	assert.match(s, /Package version/);
	assert.match(s, /SYNTHETIC purchased session/);
});
test("room imagery remains explicitly illustrative and not a historical room photograph", () => {
	const s = render(booking);
	assert.match(s, /\/cinema\/blue-960.webp/);
	assert.match(s, /AI-generated illustration/);
	assert.match(s, /Not a photograph of this venue or room/);
});
test("purchased guests only appear when server context supplies a positive safe count", () => {
	assert.match(
		render({ ...booking, amendmentPlanning: { guests: 4 } }),
		/Purchased guest count<\/dt><dd>4/,
	);
	for (const guests of [undefined, 0, -1, NaN])
		assert.match(
			render({ ...booking, amendmentPlanning: { guests } }),
			/Not included in this summary/,
		);
});
test("unknown and pending request outcomes cannot be labelled linked confirmed bookings", () => {
	for (const s of ["UNKNOWN", "NOT_CONFIRMED", "PENDING", ""])
		assert.equal(
			requestBookingLabel(s),
			"Pending/unconfirmed request — not a booking",
		);
	assert.equal(
		requestBookingLabel("SIMULATED_CONFIRMED"),
		"Linked synthetic booking",
	);
});
test("summary cannot submit amendments guests payments access or refunds", () => {
	const s = render(booking);
	assert.doesNotMatch(
		s,
		/<button|<form|<input|href="\/api|contentHash|commandKey|token/,
	);
	assert.match(s, /Elapsed time does not mean completed/);
});

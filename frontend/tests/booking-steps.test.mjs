import { test } from "node:test";
import assert from "node:assert/strict";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	BookingStepGuide,
	AwaitingBookingQuote,
	bookingSections,
} from "../components/booking-steps.tsx";
import { HoldStatus } from "../components/inventory.tsx";
test("section guide follows actual interval/hold/package/quotation/acceptance order", () => {
	assert.deepEqual(
		bookingSections.map((x) => x[0]),
		[
			"room-overview",
			"booking-date",
			"booking-hold",
			"booking-packages",
			"booking-review",
			"booking-accept",
		],
	);
	const html = render(h(BookingStepGuide));
	assert.match(html, /aria-label="Booking section guide"/);
	assert.equal((html.match(/<a /g) || []).length, 6);
	assert.doesNotMatch(html, /aria-current|Completed|Confirmed|<button|<form/);
});
test("guide links are local navigation and never carry protected identifiers or commands", () => {
	const html = render(h(BookingStepGuide));
	for (const [id] of bookingSections) assert.ok(html.includes(`href="#${id}"`));
	assert.doesNotMatch(
		html,
		/\/api\/|contentHash|token|quoteId|payment-success/,
	);
});
test("future sections retain unique reachable anchors without fabricated terms or acceptance", () => {
	const html = render(h(AwaitingBookingQuote));
	for (const id of ["booking-packages", "booking-review", "booking-accept"])
		assert.equal((html.match(new RegExp(`id="${id}"`, "g")) || []).length, 1);
	assert.match(html, /active inventory hold first/);
	assert.match(html, /does not confirm payment or a booking/);
	assert.doesNotMatch(html, /<button|<input|LKR|value="180"/);
});
const hold = {
	holdId: "SYNTHETIC",
	roomId: "SYNTHETIC",
	timezone: "Asia/Colombo",
	sessionStart: "2026-10-20T04:30:00Z",
	sessionEnd: "2026-10-20T07:30:00Z",
	bufferMinutes: 7,
	blockedUntil: "2026-10-20T07:37:00Z",
	state: "EXPIRED",
	revision: 1,
	createdAt: "2026-10-18T00:00:00Z",
	serverNow: "2026-10-18T00:10:00Z",
	expiresAt: "2026-10-18T00:10:00Z",
	evidenceLevel: "INVENTORY_ONLY",
};
test("expired hold presentation remains expired and never becomes confirmation", () => {
	const html = render(h(HoldStatus, { hold, elapsedMs: 0 }));
	assert.match(html, /Hold expired/);
	assert.match(html, /Time remaining: 0 seconds/);
	assert.match(
		html,
		/No booking, price, payment, physical readiness or access is confirmed/,
	);
	assert.doesNotMatch(html, /Temporary inventory hold/);
});
test("client countdown at deadline directs authoritative refresh without renewing hold", () => {
	const html = render(
		h(HoldStatus, { hold: { ...hold, state: "ACTIVE" }, elapsedMs: 0 }),
	);
	assert.match(html, /Deadline reached; refresh authoritative state/);
	assert.doesNotMatch(html, /<button|<form/);
});

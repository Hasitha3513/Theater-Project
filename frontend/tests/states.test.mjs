import { test } from "node:test";
import assert from "node:assert/strict";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { StateMessage, Button } from "../components/primitives.tsx";
import { stateFor, RoomRequestError } from "../lib/rooms.ts";
const states = [
	["loading", "Loading", "status"],
	["empty", "No results", "status"],
	["error", "Request not completed", "alert"],
	["unauthenticated", "Sign-in required", "alert"],
	["denied", "Access denied", "alert"],
	["unconfigured", "Connection unavailable", "alert"],
	["validation", "Check your details", "alert"],
	["not-found", "Item unavailable", "alert"],
	["conflict", "Information changed", "alert"],
	["unavailable", "Service unavailable", "alert"],
];
for (const [kind, title, role] of states)
	test(`${kind} has named announcements without fake success or automatic retry`, () => {
		const html = render(h(StateMessage, { kind }));
		assert.ok(html.includes(`role="${role}"`));
		assert.ok(html.includes(`aria-label="${title}"`));
		assert.match(html, /aria-atomic="true"/);
		assert.equal(html.includes('aria-busy="true"'), kind === "loading");
		assert.match(html, /aria-hidden="true"/);
		assert.doesNotMatch(
			html,
			/matching examples|No protected account data|<button|<form|Payment successful|Booking confirmed/,
		);
	});
for (const [statuses, kind] of [
	[[400, 422], "validation"],
	[[401], "unauthenticated"],
	[[403], "denied"],
	[[404], "not-found"],
	[[409, 412], "conflict"],
	[[408, 429, 500, 502, 503, 504, 599], "unavailable"],
])
	test(`HTTP ${statuses.join("/")} keeps authoritative failure classification and hides raw errors`, () => {
		for (const status of statuses) {
			const e = new RoomRequestError(status, "PRIVATE BACKEND DETAIL");
			assert.equal(stateFor(e), kind);
			assert.doesNotMatch(
				render(h(StateMessage, { kind: stateFor(e) })),
				/PRIVATE BACKEND DETAIL/,
			);
		}
	});
test("unrecognized failure never becomes success,empty,denied or inferred session expiration", () => {
	for (const e of [
		new Error("PRIVATE"),
		undefined,
		null,
		new RoomRequestError(418, "PRIVATE"),
	])
		assert.equal(stateFor(e), "error");
	assert.equal(stateFor(new TypeError("fetch failed")), "unavailable");
	assert.equal(
		stateFor(new DOMException("timeout", "TimeoutError")),
		"unavailable",
	);
	assert.equal(stateFor(new DOMException("navigation", "AbortError")), "error");
});
test("recovery is optional explicit UI, never invoked during render or supplied automatically", () => {
	let actions = 0;
	const html = render(
		h(StateMessage, {
			kind: "unavailable",
			recovery: h(
				Button,
				{ onClick: () => actions++ },
				"Reload current status",
			),
		}),
	);
	assert.equal(actions, 0);
	assert.match(html, /class="state-actions"/);
	assert.match(html, /type="button"/);
	assert.match(html, /Reload current status/);
	assert.doesNotMatch(
		render(h(StateMessage, { kind: "denied" })),
		/<button|href=/,
	);
});
test("unknown results and conflicts direct status review instead of repeating transactions", () => {
	assert.match(
		render(h(StateMessage, { kind: "error" })),
		/before repeating a submission/,
	);
	assert.match(
		render(h(StateMessage, { kind: "unavailable" })),
		/Do not repeat a submission whose outcome is unknown/,
	);
	assert.match(
		render(h(StateMessage, { kind: "conflict" })),
		/Reload the latest information and review/,
	);
	assert.match(
		render(h(StateMessage, { kind: "denied" })),
		/reloading does not grant access/,
	);
});

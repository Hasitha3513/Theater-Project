import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	staffRosterRecord,
	loadStaffRoster,
	StaffRosterSummary,
} from "../components/staff-guest-roster.tsx";
import { item, id, venue, other } from "./staff-bookings-fixtures.mjs";
import { rosterFixture } from "./staff-guest-roster-fixtures.mjs";
test("staff roster binds the exact venue, booking, revision, purchase and scheduled session", () => {
	const value = staffRosterRecord(rosterFixture(), item());
	assert.equal(value.count, 2);
	for (const patch of [
		{ venueId: other },
		{ bookingId: other },
		{ bookingRevision: 2 },
		{ purchasedLimit: 5 },
		{ sessionStart: "2026-10-22T04:30:00Z" },
	])
		assert.throws(() =>
			staffRosterRecord({ ...rosterFixture(), ...patch }, item()),
		);
});
test("malformed, admission or mutating roster contracts are refused", () => {
	for (const patch of [
		{ count: 9 },
		{ entries: [] },
		{ synthetic: false },
		{ status: "VERIFIED" },
		{ ageVerified: true },
		{ physicalAdmission: true },
		{ accessIssued: true },
		{ permittedActions: ["ADD"] },
		{ serverNow: "invalid" },
		{ initialized: false },
	])
		assert.throws(() =>
			staffRosterRecord({ ...rosterFixture(), ...patch }, item()),
		);
});
test("unknown private fields are projected away including nested entry fields", () => {
	const raw = rosterFixture();
	raw.document = "PRIVATE_UNSUPPORTED";
	raw.entries[1].evidence = "PRIVATE_UNSUPPORTED";
	assert.doesNotMatch(
		JSON.stringify(staffRosterRecord(raw, item())),
		/PRIVATE_UNSUPPORTED|document|evidence/,
	);
});
test("empty roster is explicit, unverified and never offers registration controls", () => {
	const value = staffRosterRecord(
		{
			...rosterFixture(),
			initialized: false,
			revision: 0,
			count: 0,
			entries: [],
			remaining: 4,
		},
		item(),
	);
	const html = renderToStaticMarkup(
		React.createElement(StaffRosterSummary, { value }),
	);
	assert.match(html, /No synthetic guest roster has been initialized/);
	assert.match(html, /UNVERIFIED/);
	assert.doesNotMatch(html, /<button|<input|<form/);
});
test("read-only semantic roster contains authoritative counts and synthetic labels", () => {
	const html = renderToStaticMarkup(
		React.createElement(StaffRosterSummary, {
			value: staffRosterRecord(rosterFixture(), item()),
		}),
	);
	for (const label of [
		"Purchased guest limit",
		"Registered synthetic guests",
		"Current room capacity",
		"SYNTHETIC-1",
		"UNVERIFIED",
	])
		assert.ok(html.includes(label));
	assert.match(html, /scope="col"/);
	assert.match(html, /scope="row"/);
	assert.doesNotMatch(html, /<main|<button|<input|<form/);
});
test("protected roster reload is a no-store GET with only opaque scoped path and abort signal", async () => {
	const original = globalThis.fetch,
		calls = [],
		controller = new AbortController();
	globalThis.fetch = async (path, init) => {
		calls.push({ path, init });
		return Response.json(rosterFixture());
	};
	try {
		await loadStaffRoster(item(), controller.signal);
		assert.equal(calls.length, 1);
		assert.equal(
			calls[0].path,
			`/api/admin/venues/${venue}/bookings/${id}/guest-roster`,
		);
		assert.equal(calls[0].init.method, undefined);
		assert.equal(calls[0].init.cache, "no-store");
		assert.equal(calls[0].init.signal, controller.signal);
		await assert.rejects(loadStaffRoster({ ...item(), venueId: "../unsafe" }));
		assert.equal(calls.length, 1);
	} finally {
		globalThis.fetch = original;
	}
});
test("permission, session, missing and unavailable failures propagate without fallback success", async () => {
	const original = globalThis.fetch;
	try {
		for (const status of [401, 403, 404, 409, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "denied" }, { status });
			await assert.rejects(
				loadStaffRoster(item()),
				(error) => error.status === status,
			);
		}
		globalThis.fetch = async () => {
			throw new TypeError("network failure");
		};
		await assert.rejects(loadStaffRoster(item()));
	} finally {
		globalThis.fetch = original;
	}
});

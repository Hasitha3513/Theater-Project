import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { GuestRoster, rosterRecord } from "../components/guest-roster.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
const entry = { id, kind: "BOOKER", label: "BOOKER", status: "UNVERIFIED" };
const roster = () => ({
	bookingId: id,
	bookingRevision: 1,
	revision: 0,
	initialized: true,
	synthetic: true,
	status: "UNVERIFIED",
	physicalAdmission: false,
	ageVerified: false,
	accessIssued: false,
	purchasedLimit: 4,
	roomCapacity: 5,
	count: 1,
	remaining: 3,
	entries: [entry],
	permittedActions: ["ADD"],
});
test("server-bound synthetic booker and current capacity", () =>
	assert.equal(rosterRecord(roster(), id).count, 1));
test("reject wrong identity and unsafe flags", () => {
	for (const key of ["physicalAdmission", "ageVerified", "accessIssued"])
		assert.throws(() => rosterRecord({ ...roster(), [key]: true }, id));
	assert.throws(() =>
		rosterRecord(roster(), "00000000-0000-4000-8000-000000000002"),
	);
});
test("reject client-shaped counts and limits", () => {
	for (const override of [
		{ remaining: 4 },
		{ count: 9 },
		{ purchasedLimit: NaN },
		{ roomCapacity: 0 },
		{ entries: [] },
		{ bookingRevision: 0 },
	])
		assert.throws(() => rosterRecord({ ...roster(), ...override }, id));
});
test("reject missing or duplicated booker and real-name labels", () => {
	assert.throws(() =>
		rosterRecord(
			{
				...roster(),
				entries: [{ ...entry, kind: "COMPANION", label: "Jane Doe" }],
			},
			id,
		),
	);
	assert.throws(() =>
		rosterRecord(
			{ ...roster(), count: 2, remaining: 2, entries: [entry, entry] },
			id,
		),
	);
});
test("uninitialized read has no mutation implied", () =>
	assert.equal(
		rosterRecord(
			{
				...roster(),
				initialized: false,
				count: 0,
				remaining: 4,
				entries: [],
				permittedActions: ["INITIALIZE"],
			},
			id,
		).count,
		0,
	));
test("room capacity reduction never invents space", () =>
	assert.equal(
		rosterRecord(
			{ ...roster(), roomCapacity: 1, remaining: 0, permittedActions: [] },
			id,
		).remaining,
		0,
	));
test("read/write proxy routes narrowly allow actual methods", () => {
	const p = `/api/bookings/${id}/guest-roster`;
	assert.ok(bankRoute(p, "GET"));
	assert.ok(bankRoute(p, "POST"));
	assert.equal(bankRoute(p, "DELETE"), false);
	assert.equal(bankRoute(`${p}/companions`, "GET"), false);
	const s = `/api/admin/venues/${id}/bookings/${id}/guest-roster`;
	assert.ok(bankRoute(s, "GET"));
	assert.equal(bankRoute(s, "POST"), false);
});
test("initial render labels exclusions without forms or automatic admission", () => {
	const html = renderToStaticMarkup(
		React.createElement(GuestRoster, { bookingId: id }),
	);
	assert.match(html, /UNVERIFIED/);
	assert.match(html, /No real names/);
	assert.doesNotMatch(html, /type="file"|type="email"|type="date"/);
});

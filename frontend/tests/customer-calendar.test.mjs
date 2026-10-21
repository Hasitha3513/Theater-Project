import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { CustomerAvailabilityCalendar } from "../components/customer-calendar.tsx";
import {
	calendarDate,
	calendarPath,
	shiftCalendarDate,
	validateCalendar,
	loadCalendar,
} from "../lib/customer-calendar.ts";
const c = {
	roomId: "00000000-0000-4000-8000-000000000001",
	venueId: "00000000-0000-4000-8000-000000000002",
	date: "2027-02-01",
	durationMinutes: 180,
	guests: 4,
};
function dto() {
	return {
		...c,
		timezone: "Asia/Colombo",
		configuration: "SYNTHETIC",
		observedAt: "2027-01-01T00:00:00Z",
		evidenceLevel: "INVENTORY_ONLY",
		complete: true,
		items: Array.from({ length: 1440 }, (_, n) => ({
			startLocal: `${c.date}T${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`,
			sessionStart: null,
			sessionEnd: null,
			blockedUntil: null,
			available: null,
			reasons: ["PENDING_RECONCILIATION"],
		})),
	};
}
test("calendar query is exact; native date arithmetic never uses browser timezone", () => {
	assert.equal(
		calendarPath(c),
		`/api/rooms/${c.roomId}/availability/calendar?date=2027-02-01&durationMinutes=180&guests=4`,
	);
	assert.equal(shiftCalendarDate("2028-02-28", 1), "2028-02-29");
	assert.equal(shiftCalendarDate("2027-12-31", 1), "2028-01-01");
	for (const date of ["2027-02-29", "2027-13-01", "bad"]) {
		assert.equal(calendarDate(date), false);
		assert.throws(() => calendarPath({ ...c, date }));
	}
	for (const durationMinutes of [0, 1441, 1.5])
		assert.throws(() => calendarPath({ ...c, durationMinutes }));
	for (const guests of [0, -1, 1.5])
		assert.throws(() => calendarPath({ ...c, guests }));
});
test("calendar validates authoritative identity, completeness and tri-state reasons", () => {
	assert.equal(validateCalendar(dto(), c).items[0].available, null);
	assert.deepEqual(validateCalendar({ ...dto(), items: [] }, c).items, []);
	for (const patch of [
		{ roomId: c.venueId },
		{ venueId: c.roomId },
		{ date: "2027-02-02" },
		{ guests: 2 },
		{ durationMinutes: 60 },
		{ complete: false },
		{ timezone: "invalid" },
		{ evidenceLevel: "READY" },
		{ items: dto().items.slice(1) },
	])
		assert.throws(() => validateCalendar({ ...dto(), ...patch }, c));
	for (const patch of [
		{ available: true },
		{ available: "true" },
		{ reasons: ["UNAPPROVED"] },
		{ startLocal: "2027-02-01T01:00" },
	]) {
		const d = dto();
		d.items[0] = { ...d.items[0], ...patch };
		assert.throws(() => validateCalendar(d, c));
	}
});
test("available items require all safe timestamp fields and no uncertainty", () => {
	const d = dto();
	d.items[0] = {
		...d.items[0],
		available: true,
		reasons: [],
		sessionStart: "2027-01-31T18:30:00Z",
		sessionEnd: "2027-01-31T21:30:00Z",
		blockedUntil: "2027-01-31T21:45:00Z",
	};
	assert.equal(validateCalendar(d, c).items[0].available, true);
	d.items[0].reasons = ["PENDING_RECONCILIATION"];
	assert.throws(() => validateCalendar(d, c));
});
test("read transport uses no-store GET and abort; errors never yield available data", async () => {
	const original = global.fetch;
	const control = new AbortController();
	try {
		let seen;
		global.fetch = async (path, init) => {
			seen = { path, init };
			return Response.json(dto());
		};
		await loadCalendar(c, control.signal);
		assert.equal(seen.path, calendarPath(c));
		assert.equal(seen.init.cache, "no-store");
		assert.equal(seen.init.signal, control.signal);
		assert.equal(seen.init.method, undefined);
		for (const status of [401, 403, 404, 409, 429, 503]) {
			global.fetch = async () =>
				Response.json({ message: "Sanitized" }, { status });
			await assert.rejects(
				loadCalendar(c, control.signal),
				(e) => e.status === status,
			);
		}
	} finally {
		global.fetch = original;
	}
});
test("initial calendar has accessible labels, no invented selection and advisory notice", () => {
	const html = render(
		h(CustomerAvailabilityCalendar, {
			...c,
			timezone: "Asia/Colombo",
			disabled: false,
			onChoose: () => {
				throw Error("automatic action");
			},
		}),
	);
	for (const text of [
		"Calendar date (venue local)",
		"Calendar duration (minutes)",
		"Calendar guests",
		"Previous calendar day",
		"Next calendar day",
		"Available times are advisory",
		"Uncertain",
	])
		assert.ok(html.includes(text));
	assert.doesNotMatch(html, /value="180"|Pay now|Confirm booking|role="grid"/);
});

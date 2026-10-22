import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	confirmationPath,
	parseConfirmations,
	loadConfirmations,
} from "../lib/booking-confirmations.ts";
import { forwardBookingConfirmations } from "../lib/booking-confirmations-backend.ts";
import { BookingConfirmationsSection } from "../components/booking-confirmations.tsx";
import {
	context,
	fixture,
	account,
} from "./booking-confirmations-fixtures.mjs";
const path = confirmationPath(context);
test("explicit validated one-venue half-open range,31-day boundary and optional opaque room", () => {
	assert.equal(
		path,
		`/api/admin/venues/${context.venue}/reports/synthetic-booking-confirmations?from=2026-10-01&to=2026-10-03`,
	);
	assert(confirmationPath({ ...context, to: "2026-11-01" }));
	assert(
		confirmationPath({ ...context, room: fixture().rooms[0].roomId }).includes(
			"roomId=",
		),
	);
	for (const c of [
		{ ...context, venue: "bad" },
		{ ...context, to: "2026-11-02" },
		{ ...context, from: "2026-02-30" },
		{ ...context, to: context.from },
	])
		assert.throws(() => confirmationPath(c));
});
test("allowlisted complete count projection excludes private fields without numeric rounding", () => {
	const f = fixture();
	f.actor = "PRIVATE";
	f.daily[0].secret = "PRIVATE";
	assert.deepEqual(parseConfirmations(f, context), fixture());
	const large = "9007199254740993";
	f.total = large;
	f.daily[0].count = large;
	f.daily[1].count = "0";
	f.rooms[0].count = large;
	assert.equal(parseConfirmations(f, context).total, large);
});
test("malformed,stale,incomplete,misclassified and inconsistent reports fail closed", () => {
	for (const change of [
		(f) => (f.venueId = account),
		(f) => (f.from = "2026-09-01"),
		(f) => (f.cohortBasis = "createdAt"),
		(f) => (f.synthetic = false),
		(f) => (f.state = "CONFIRMED"),
		(f) => (f.cancellationStatistics = "0%"),
		(f) => (f.complete = false),
		(f) => (f.total = "4"),
		(f) => f.daily.pop(),
		(f) => f.rooms.push(f.rooms[0]),
		(f) => (f.timezone = "invalid/zone"),
		(f) => (f.daily[0].count = -1),
	]) {
		const f = fixture();
		change(f);
		assert.throws(() => parseConfirmations(f, context));
	}
});
test("explicit empty complete population retains zero and no fabricated room values", () => {
	const f = fixture();
	f.total = "0";
	f.daily.forEach((d) => {
		d.count = "0";
	});
	f.rooms = [];
	assert.equal(parseConfirmations(f, context).total, "0");
});
function request(
	query = path,
	headers = { Cookie: "CINENEST_SESSION=test-session;PRIVATE=secret" },
) {
	return new Request(`http://localhost${query}`, { headers });
}
test("proxy rejects duplicate,unsupported,invalid range and spoofed authorization without upstream", async () => {
	const old = global.fetch;
	global.fetch = async () => {
		throw Error("must not forward");
	};
	try {
		for (const p of [
			path + "&from=2026-10-01",
			path + "&asOf=x",
			path.replace("2026-10-03", "2026-12-01"),
		])
			assert.equal((await forwardBookingConfirmations(request(p))).status, 400);
		assert.equal(
			(await forwardBookingConfirmations(request(path, {}))).status,
			401,
		);
		assert.equal(
			(
				await forwardBookingConfirmations(
					request(path, {
						Cookie: "CINENEST_SESSION=test",
						Authorization: "bad",
					}),
				)
			).status,
			400,
		);
	} finally {
		global.fetch = old;
	}
});
test("strict authenticated GET forwards only session cookie and strips private upstream fields", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	let called = 0;
	global.fetch = async (url, init) => {
		called++;
		assert.equal(init.method, "GET");
		assert.equal(init.redirect, "manual");
		assert.deepEqual(init.headers, {
			Accept: "application/json",
			Cookie: "CINENEST_SESSION=test-session",
		});
		assert.equal(
			new URL(url).pathname,
			new URL(`http://localhost${path}`).pathname,
		);
		return Response.json({ ...fixture(), secret: "PRIVATE" });
	};
	try {
		const response = await forwardBookingConfirmations(request());
		assert.equal(response.status, 200);
		assert.equal(called, 1);
		assert.deepEqual(await response.json(), fixture());
		assert.match(response.headers.get("Cache-Control"), /no-store/);
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});
test("sanitized denied,expired and audit unavailable backend errors reveal no payload", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	try {
		for (const status of [401, 403, 404, 409, 503]) {
			global.fetch = async () => new Response("PRIVATE", { status });
			const r = await forwardBookingConfirmations(request());
			assert.equal(r.status, status);
			assert.equal(await r.text(), "");
		}
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});
test("customer-facing loading and truthful initial state has no values or automatic fetch", () => {
	const html = renderToStaticMarkup(
		React.createElement(BookingConfirmationsSection),
	);
	assert.match(html, /Synthetic Booking Confirmations/);
	assert.match(html, /Not real commercial booking or revenue statistics/);
	assert.match(html, /Cancellation statistics: NOT AVAILABLE/);
	assert.match(html, /Load statistics/);
	assert.match(html, /role="status"/);
	assert.doesNotMatch(html, /<main|<table|Bookings Created|cancellationRate/);
});
test("protected load checks session before and after,GET only,no automatic mutation", async () => {
	const old = global.fetch;
	const calls = [];
	let mode = "OK",
		sessions = 0;
	global.fetch = async (url, init) => {
		calls.push([url, init]);
		assert(!init?.method || init.method === "GET");
		if (url === "/api/accounts/me") {
			sessions++;
			return mode === "EXPIRED"
				? new Response(null, { status: 401 })
				: Response.json({
						accountId:
							mode === "CHANGED" && sessions % 2 === 0
								? context.venue
								: account,
						eligibility: "ELIGIBLE",
					});
		}
		return Response.json(fixture());
	};
	try {
		assert.deepEqual(await loadConfirmations(context), fixture());
		assert.equal(calls.length, 3);
		mode = "CHANGED";
		sessions = 0;
		await assert.rejects(() => loadConfirmations(context));
		mode = "EXPIRED";
		await assert.rejects(() => loadConfirmations(context));
	} finally {
		global.fetch = old;
	}
});

import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	popularPath,
	parsePopular,
	loadPopular,
} from "../lib/popular-slots.ts";
import { forwardPopularSlots } from "../lib/popular-slots-backend.ts";
import { PopularSlotsSection } from "../components/popular-slots.tsx";
import { context, fixture, account } from "./popular-slots-fixtures.mjs";
const path = popularPath(context);
test("explicit validated one-venue half-open range,31-day boundary and optional opaque room", () => {
	assert.equal(
		path,
		`/api/admin/venues/${context.venue}/reports/synthetic-popular-session-starts?from=2026-10-01&to=2026-10-03`,
	);
	assert(popularPath({ ...context, to: "2026-11-01" }));
	assert(
		popularPath({
			...context,
			room: "00000000-0000-4000-8000-000000000002",
		}).includes("roomId="),
	);
	for (const c of [
		{ ...context, venue: "bad" },
		{ ...context, to: "2026-11-02" },
		{ ...context, from: "2026-02-30" },
		{ ...context, to: context.from },
	])
		assert.throws(() => popularPath(c));
});
test("allowlisted complete count projection excludes private fields without numeric rounding", () => {
	const f = fixture();
	f.actor = "PRIVATE";
	f.buckets[0].secret = "PRIVATE";
	assert.deepEqual(parsePopular(f, context), fixture());
	const large = "9007199254740993";
	f.total = large;
	f.buckets.forEach((b) => {
		b.count = "0";
	});
	f.buckets[0].count = large;

	assert.equal(parsePopular(f, context).total, large);
});
test("malformed,stale,incomplete,misclassified and inconsistent reports fail closed", () => {
	for (const change of [
		(f) => (f.venueId = account),
		(f) => (f.from = "2026-09-01"),
		(f) => (f.cohortBasis = "createdAt"),
		(f) => (f.synthetic = false),
		(f) => (f.bandDefinitionVersion = "OTHER"),
		(f) => (f.revisionBasis = "HISTORICAL"),
		(f) => (f.complete = false),
		(f) => (f.total = "4"),
		(f) => f.buckets.pop(),
		(f) => (f.buckets[0].bandId = "H01"),
		(f) => (f.timezone = "invalid/zone"),
		(f) => (f.buckets[0].count = -1),
	]) {
		const f = fixture();
		change(f);
		assert.throws(() => parsePopular(f, context));
	}
});
test("explicit empty complete population retains zero and no fabricated room values", () => {
	const f = fixture();
	f.total = "0";
	f.buckets.forEach((d) => {
		d.count = "0";
	});

	assert.equal(parsePopular(f, context).total, "0");
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
			path + "&roomId=",
			path.replace("2026-10-03", "2026-12-01"),
		])
			assert.equal((await forwardPopularSlots(request(p))).status, 400);
		assert.equal((await forwardPopularSlots(request(path, {}))).status, 401);
		assert.equal(
			(
				await forwardPopularSlots(
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
		const response = await forwardPopularSlots(request());
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
			const r = await forwardPopularSlots(request());
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
	const html = renderToStaticMarkup(React.createElement(PopularSlotsSection));
	assert.match(html, /Popular Reserved Session Start Times/);
	assert.match(
		html,
		/Not actual attendance, occupancy, revenue or physical readiness/,
	);
	assert.match(html, /Current purchased revisions/);
	assert.match(html, /Load Report/);
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
		assert.deepEqual(await loadPopular(context), fixture());
		assert.equal(calls.length, 3);
		mode = "CHANGED";
		sessions = 0;
		await assert.rejects(() => loadPopular(context));
		mode = "EXPIRED";
		await assert.rejects(() => loadPopular(context));
	} finally {
		global.fetch = old;
	}
});

test("chart ratios retain exact large-count arithmetic,all24 hourly labels", async () => {
	const { barPercent, bandLabel } = await import("../lib/popular-slots.ts");
	assert.equal(barPercent("9007199254740993", "9007199254740993"), 100);
	assert.equal(barPercent("1", "0"), 0);
	assert.equal(bandLabel(23, 24), "23:00–24:00");
});
test("response size cap,redirects and malformed JSON fail closed", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	try {
		for (const response of [
			new Response("x".repeat(65537)),
			new Response("{bad"),
			new Response(null, {
				status: 302,
				headers: { Location: "https://invalid.example" },
			}),
		]) {
			global.fetch = async () => response;
			const r = await forwardPopularSlots(request());
			assert.equal(r.status, 502);
			assert.equal(await r.text(), "");
		}
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});

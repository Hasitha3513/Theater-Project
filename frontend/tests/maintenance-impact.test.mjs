import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	impactPath,
	parseImpact,
	validLocal,
	loadImpact,
} from "../lib/maintenance-impact.ts";
import { forwardMaintenanceImpact } from "../lib/maintenance-impact-backend.ts";
import { MaintenanceImpactPreview } from "../components/maintenance-impact.tsx";
import { context, fixture, account } from "./maintenance-impact-fixtures.mjs";
const path = impactPath(context),
	query = new URLSearchParams({
		startLocal: context.startLocal,
		endLocal: context.endLocal,
	});
function request(q = query.toString(), init = {}) {
	return new Request(`http://localhost${path}?${q}`, {
		headers: {
			Cookie: "CINENEST_SESSION=test-session; PRIVATE=secret",
		},
		...init,
	});
}
async function upstream(fn) {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	const calls = [];
	let reply = () => Response.json(fixture());
	global.fetch = async (...args) => {
		calls.push(args);
		return reply();
	};
	try {
		await fn(calls, (r) => {
			reply = r;
		});
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
}
test("allowlisted complete projection strips private top-level and count fields", () => {
	const raw = fixture();
	raw.secret = "PRIVATE";
	raw.counts.evidence = "PRIVATE";
	assert.deepEqual(parseImpact(raw, context), fixture());
});
test("complete zero and incomplete null remain distinct", () => {
	const zero = fixture();
	for (const k of Object.keys(zero.counts)) zero.counts[k] = 0;
	assert.equal(parseImpact(zero, context).counts.activeHolds, 0);
	const raw = {
		...fixture(),
		complete: false,
		counts: null,
		incompleteReason: "RECORD_LIMIT",
	};
	assert.equal(parseImpact(raw, context).counts, null);
});
for (const [label, change] of Object.entries({
	venue: (p) => (p.venueId = account),
	room: (p) => (p.roomId = account),
	timezone: (p) => (p.timezone = "Invalid/Zone"),
	interval: (p) => (p.requestedEnd = "2026-10-22T06:31:00Z"),
	seconds: (p) => (p.requestedEnd = "2026-10-22T06:30:01Z"),
	date: (p) => (p.observedAt = "2026-02-30T00:00:00Z"),
	synthetic: (p) => (p.synthetic = false),
	occupancy: (p) => (p.occupancy = "EMPTY"),
	readiness: (p) => (p.readiness = "READY"),
	evidence: (p) => (p.evidenceLevel = "PHYSICAL"),
	limit: (p) => (p.recordLimit = 1001),
	negative: (p) => (p.counts.activeHolds = -1),
	fraction: (p) => (p.counts.activeHolds = 1.5),
	overflow: (p) => (p.counts.activeHolds = 1000),
	nullcounts: (p) => (p.counts = null),
	incompletecounts: (p) => {
		p.complete = false;
		p.incompleteReason = "RECORD_LIMIT";
	},
	reason: (p) => (p.incompleteReason = "RECORD_LIMIT"),
})) {
	test(`rejects malformed ${label}`, () => {
		const raw = fixture();
		change(raw);
		assert.throws(() => parseImpact(raw, context));
	});
}
test("local timestamps validate real dates, minutes and bounded intervals without browser conversion", () => {
	assert.equal(validLocal("2026-02-30T10:00"), false);
	assert.equal(validLocal("2026-10-22T24:00"), false);
	assert.throws(() => impactPath({ ...context, endLocal: "2026-10-23T00:01" }));
	assert.throws(() => impactPath({ ...context, endLocal: context.startLocal }));
	assert.ok(impactPath({ ...context, endLocal: "2026-10-23T00:00" }));
});
test("venue timezone DST offsets use actual returned instants", () => {
	for (const [date, start, end] of [
		["2026-03-08", "2026-03-08T05:00:00Z", "2026-03-09T04:00:00Z"],
		["2026-11-01", "2026-11-01T04:00:00Z", "2026-11-02T05:00:00Z"],
	]) {
		const next = new Date(date + "T00:00Z");
		next.setUTCDate(next.getUTCDate() + 1);
		const c = {
			...context,
			startLocal: date + "T00:00",
			endLocal: next.toISOString().slice(0, 10) + "T00:00",
		};
		assert.equal(
			parseImpact(
				{
					...fixture(),
					timezone: "America/New_York",
					requestedStart: start,
					requestedEnd: end,
				},
				c,
			).requestedEnd,
			end,
		);
	}
});
test("exact proxy GET forwards only session cookie and exact query, no authority headers", async () =>
	upstream(async (calls) => {
		const r = await forwardMaintenanceImpact(request());
		assert.equal(r.status, 200);
		assert.match(r.headers.get("cache-control"), /no-store/);
		assert.deepEqual(await r.json(), fixture());
		assert.equal(calls.length, 1);
		const [url, init] = calls[0];
		assert.equal(url.pathname, path);
		assert.equal(url.searchParams.get("startLocal"), context.startLocal);
		assert.deepEqual(init.headers, {
			Accept: "application/json",
			Cookie: "CINENEST_SESSION=test-session",
		});
		assert.equal(init.method, "GET");
		assert.equal(init.redirect, "manual");
	}));
for (const q of [
	"",
	`startLocal=${context.startLocal}`,
	`${query}&startLocal=${context.startLocal}`,
	`${query}&other=x`,
	`${query}&endLocal=x`,
	"startLocal=2026-02-30T10:00&endLocal=2026-02-30T12:00",
]) {
	test(`proxy rejects invalid query ${q}`, async () =>
		upstream(async (calls) => {
			assert.equal((await forwardMaintenanceImpact(request(q))).status, 400);
			assert.equal(calls.length, 0);
		}));
}
test("proxy rejects methods, invalid IDs, missing and duplicate sessions", async () =>
	upstream(async (calls) => {
		assert.equal(
			(
				await forwardMaintenanceImpact(
					request(query.toString(), { method: "POST" }),
				)
			).status,
			404,
		);
		assert.equal(
			(
				await forwardMaintenanceImpact(
					new Request(
						`http://localhost${path.replace(context.room, "bad")}?${query}`,
					),
				)
			).status,
			400,
		);
		for (const cookie of ["", "CINENEST_SESSION=a; CINENEST_SESSION=b"]) {
			assert.equal(
				(
					await forwardMaintenanceImpact(
						request(query.toString(), { headers: { Cookie: cookie } }),
					)
				).status,
				401,
			);
		}
		assert.equal(calls.length, 0);
	}));
for (const code of [400, 401, 403, 404, 409, 503])
	test(`sanitized backend ${code} preserved`, async () =>
		upstream(async (_, set) => {
			set(() => new Response("PRIVATE", { status: code }));
			const r = await forwardMaintenanceImpact(request());
			assert.equal(r.status, code);
			assert.equal(await r.text(), "");
		}));
test("proxy fails closed on oversize, mismatched DTO and redirect", async () =>
	upstream(async (_, set) => {
		for (const reply of [
			() => new Response("x".repeat(256 * 1024 + 1)),
			() => Response.json({ ...fixture(), venueId: account }),
			() =>
				new Response(null, {
					status: 302,
					headers: { Location: "https://example.com/secret" },
				}),
		]) {
			set(reply);
			const r = await forwardMaintenanceImpact(request());
			assert.equal(r.status, 502);
			assert.equal(await r.text(), "");
		}
	}));
async function transport(fn) {
	const old = global.fetch;
	let mode = "OK",
		after = false,
		resolveJson;
	const calls = [];
	global.fetch = async (input, init) => {
		calls.push([input, init]);
		if (input === "/api/accounts/me")
			return Response.json({
				accountId: after && mode === "CHANGED" ? context.room : account,
				eligibility: "ELIGIBLE",
			});
		if (mode === "DENIED") return new Response(null, { status: 403 });
		after = true;
		return {
			ok: true,
			json: async () =>
				mode === "DELAY"
					? await new Promise((r) => {
							resolveJson = r;
						})
					: fixture(),
		};
	};
	try {
		await fn(
			calls,
			(m) => {
				mode = m;
			},
			() => resolveJson,
		);
	} finally {
		global.fetch = old;
	}
}
test("protected load checks session before and after; no mutations", async () =>
	transport(async (calls) => {
		assert.deepEqual(
			await loadImpact(context, new AbortController().signal),
			fixture(),
		);
		assert.equal(calls.length, 3);
		assert.ok(calls.every(([, init]) => !init.method));
		assert.equal(
			new URL(calls[1][0], "http://localhost").searchParams.get("startLocal"),
			context.startLocal,
		);
	}));
test("session replacement and permission denial fail closed", async () => {
	for (const mode of ["CHANGED", "DENIED"])
		await transport(async (_, set) => {
			set(mode);
			await assert.rejects(loadImpact(context, new AbortController().signal));
		});
});
test("abort during delayed JSON cannot disclose stale result", async () =>
	transport(async (_, set, get) => {
		set("DELAY");
		const c = new AbortController(),
			pending = loadImpact(context, c.signal);
		while (!get()) await new Promise((r) => setImmediate(r));
		c.abort();
		get()(fixture());
		await assert.rejects(pending);
	}));
test("initial panel has labelled explicit controls, warnings and no fabricated counts", () => {
	const html = renderToStaticMarkup(
		React.createElement(MaintenanceImpactPreview),
	);
	for (const text of [
		"Maintenance Impact Preview",
		"Load Preview",
		"Retry",
		"Clear",
		"inventory.read",
		"UNKNOWN",
		"UNVERIFIED",
		"venue local",
	])
		assert.ok(html.includes(text));
	assert.doesNotMatch(html, /<main|impact-counts|canClose|safeToEnter/);
	assert.match(html, /role="status"/);
});

test("client authority headers are rejected, not forwarded", async () =>
	upstream(async (calls) => {
		for (const name of [
			"Authorization",
			"X-Permission",
			"X-Venue-ID",
			"X-Account-ID",
		]) {
			const r = request(query.toString(), {
				headers: {
					Cookie: "CINENEST_SESSION=test-session",
					[name]: "invented",
				},
			});
			assert.equal((await forwardMaintenanceImpact(r)).status, 400);
		}
		assert.equal(calls.length, 0);
	}));

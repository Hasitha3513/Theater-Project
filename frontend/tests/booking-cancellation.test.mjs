import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { once } from "node:events";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	cancellationPath,
	cancellationCommand,
	cancellationResult,
	requestCancellation,
} from "../lib/booking-cancellation.ts";
import { forwardCancellation } from "../lib/booking-cancellation-backend.ts";
import {
	BookingCancellationPanel,
	CancellationSummary,
} from "../components/booking-cancellation.tsx";
import {
	bookingId,
	venueId,
	accountId,
	requestId,
	preview,
	committed,
	command,
} from "./booking-cancellation-fixtures.mjs";
const context = { bookingId },
	staff = { bookingId, venueId };
test("only exact customer and exact-venue staff opaque routes are constructed", () => {
	assert.equal(
		cancellationPath(context),
		`/api/bookings/${bookingId}/cancellation`,
	);
	assert.equal(
		cancellationPath(staff, true),
		`/api/admin/venues/${venueId}/bookings/${bookingId}/cancellations`,
	);
	for (const id of ["bad", `${bookingId}?token=x`, `${bookingId}/access`])
		assert.throws(() => cancellationPath({ bookingId: id }));
});
test("command schema rejects unsupported actions,malformed keys,versions and unsafe reasons", () => {
	assert.deepEqual(cancellationCommand(command()), command());
	for (const change of [
		{ approve: true },
		{ reason: "" },
		{ reason: "x".repeat(241) },
		{ reason: "secret\ntext" },
		{ reason: "control\u0080" },
		{ expectedLifecycleVersion: 1 },
		{ expectedPurchasedRevision: 0 },
		{ expectedPurchasedRevision: 1.5 },
		{ commandKey: "unsafe" },
	])
		assert.throws(() => cancellationCommand({ ...command(), ...change }));
});
test("server eligibility is strict and never derived from parent booking status", () => {
	assert.equal(cancellationResult(preview(), context).eligible, true);
	for (const reason of [
		"SESSION_STARTED",
		"CREDENTIAL_EVER_ISSUED",
		"AMENDMENT_PENDING",
		"FINANCIAL_REVIEW_REQUIRED",
		"BOOKING_UNAVAILABLE",
	]) {
		const v = { ...preview(), reason, eligible: false };
		assert.equal(cancellationResult(v, context).eligible, false);
		assert(
			!renderToStaticMarkup(
				React.createElement(CancellationSummary, {
					value: cancellationResult(v, context),
				}),
			).includes("Eligible allocated principal"),
		);
		assert.throws(() => cancellationResult({ ...v, eligible: true }, context));
	}
	for (const change of [
		{ bookingId: venueId },
		{ venueId: accountId },
		{ synthetic: false },
		{ realRepayment: true },
		{ physicalReadiness: "SAFE" },
		{ state: "CONFIRMED" },
		{ purchasedRevision: 0 },
		{ sessionStart: "2026-02-30T04:30:00Z" },
		{ refundPrincipalMinor: "9999999999999999999" },
		{ lifecycleVersion: 1 },
		{ policyVersion: "UNAPPROVED" },
	])
		assert.throws(() => cancellationResult({ ...preview(), ...change }, staff));
});
test("committed cancellation requires distinct reserved obligation and never claims repayment", () => {
	const v = cancellationResult(committed(), context);
	assert.equal(v.refundStatus, "RESERVED_REVIEW_REQUIRED");
	for (const change of [
		{ refundStatus: "PAID" },
		{ accessState: "REVOKED" },
		{ inventoryReleased: false },
		{ eligible: true },
		{ refundObligationId: "" },
		{ resolution: "APPROVED" },
	])
		assert.throws(() =>
			cancellationResult({ ...committed(), ...change }, context),
		);
	const html = renderToStaticMarkup(
		React.createElement(CancellationSummary, { value: v }),
	);
	assert(html.includes("separate from repayment"));
	assert(html.includes("No refund approval"));
	assert(html.includes("physical readiness remains UNVERIFIED"));
});
test("DTO projection drops unknown private fields and omits staff principal", () => {
	const raw = {
		...preview(),
		token: "PRIVATE",
		evidence: { secret: "PRIVATE" },
		actorId: accountId,
		consequence: "UNTRUSTED",
	};
	assert(!JSON.stringify(cancellationResult(raw, staff)).includes("PRIVATE"));
	assert.equal(cancellationResult(raw, staff).refundPrincipalMinor, undefined);
	assert.equal(cancellationResult(raw, context).refundPrincipalMinor, "30007");
});
test("initial panels have explicit check,no automatically rendered command form or consent", () => {
	for (const c of [context, staff]) {
		const html = renderToStaticMarkup(
			React.createElement(BookingCancellationPanel, { context: c }),
		);
		assert(html.includes("Check Cancellation Eligibility"));
		assert(!html.includes("<form"));
		assert(!html.includes('type="checkbox"'));
		assert(html.includes("SYNTHETIC CANCELLATION ONLY"));
	}
});
test("client binds account,loads fresh CSRF and sends only one explicit POST", async () => {
	const original = globalThis.fetch,
		calls = [];
	globalThis.fetch = async (path, init) => {
		calls.push({ path, init });
		return Response.json(
			path === "/api/accounts/me"
				? { accountId, eligibility: "ELIGIBLE" }
				: path === "/api/session/csrf"
					? { headerName: "X-CSRF-TOKEN", token: "SYNTHETIC" }
					: init.method === "POST"
						? committed()
						: preview(),
		);
	};
	try {
		const read = await requestCancellation(
			context,
			new AbortController().signal,
		);
		assert.equal(read.account, accountId);
		assert.equal(calls.filter((c) => c.init.method === "POST").length, 0);
		const result = await requestCancellation(
			context,
			new AbortController().signal,
			command(),
			accountId,
		);
		assert.equal(result.value.state, "SIMULATED_CANCELLED");
		const posts = calls.filter((c) => c.init.method === "POST");
		assert.equal(posts.length, 1);
		assert.equal(posts[0].init.headers.get("X-CSRF-TOKEN"), "SYNTHETIC");
		assert.deepEqual(JSON.parse(posts[0].init.body), command());
		assert(!posts[0].path.includes(requestId));
	} finally {
		globalThis.fetch = original;
	}
});
test("abort after JSON parsing discards protected data and never sends a mutation", async () => {
	const original = globalThis.fetch,
		c = new AbortController();
	let release, entered;
	const gate = new Promise((r) => (entered = r));
	globalThis.fetch = async (path) =>
		path === "/api/accounts/me"
			? Response.json({ accountId, eligibility: "ELIGIBLE" })
			: {
					ok: true,
					json: async () => {
						entered();
						return new Promise((r) => (release = r));
					},
				};
	try {
		const p = requestCancellation(context, c.signal);
		await gate;
		c.abort();
		release(preview());
		await assert.rejects(p);
	} finally {
		globalThis.fetch = original;
	}
});
test("account change while loading CSRF prevents a cancellation POST", async () => {
	const original = globalThis.fetch;
	let reads = 0,
		posts = 0;
	globalThis.fetch = async (path, init) => {
		if (init?.method === "POST") posts++;
		return Response.json(
			path === "/api/accounts/me"
				? {
						accountId: ++reads === 1 ? accountId : venueId,
						eligibility: "ELIGIBLE",
					}
				: { headerName: "X-CSRF-TOKEN", token: "SYNTHETIC" },
		);
	};
	try {
		await assert.rejects(
			requestCancellation(
				context,
				new AbortController().signal,
				command(),
				accountId,
			),
			(e) => e.status === 401,
		);
		assert.equal(posts, 0);
	} finally {
		globalThis.fetch = original;
	}
});
test("invalid CSRF and uncommitted 200 responses cannot become cancellation success", async () => {
	const original = globalThis.fetch;
	for (const invalidCsrf of [true, false]) {
		let posts = 0;
		globalThis.fetch = async (path, init) => {
			if (init?.method === "POST") posts++;
			return Response.json(
				path === "/api/accounts/me"
					? { accountId, eligibility: "ELIGIBLE" }
					: path === "/api/session/csrf"
						? {
								headerName: invalidCsrf ? "OTHER" : "X-CSRF-TOKEN",
								token: "SYNTHETIC",
							}
						: preview(),
			);
		};
		try {
			await assert.rejects(
				requestCancellation(
					context,
					new AbortController().signal,
					command(),
					accountId,
				),
			);
			assert.equal(posts, invalidCsrf ? 0 : 1);
		} finally {
			globalThis.fetch = original;
		}
	}
});
test("strict proxy validates methods,queries,session and CSRF before forwarding", async () => {
	const base = `https://customer.test${cancellationPath(context)}`,
		headers = { Cookie: "CINENEST_SESSION=SYNTHETIC_ONLY" };
	for (const suffix of ["?page=0", "?x=1&x=2"]) {
		assert.equal(
			(await forwardCancellation(new Request(base + suffix, { headers })))
				.status,
			400,
		);
	}
	assert.equal((await forwardCancellation(new Request(base))).status, 401);
	assert.equal(
		(await forwardCancellation(new Request(base, { method: "POST", headers })))
			.status,
		404,
	);
	for (const h of [
		{ ...headers },
		{ ...headers, Origin: "https://evil.test", "X-CSRF-TOKEN": "SYNTHETIC" },
	])
		assert.equal(
			(
				await forwardCancellation(
					new Request(`${base}s`, {
						method: "POST",
						headers: h,
						body: JSON.stringify(command()),
					}),
				)
			).status,
			403,
		);
	assert.equal(
		(
			await forwardCancellation(
				new Request(base, {
					headers: { Cookie: "CINENEST_SESSION=A; CINENEST_SESSION=B" },
				}),
			)
		).status,
		401,
	);
});
test("real HTTP proxy preserves independent backend denial,sanitizes errors and projects DTOs", async () => {
	let status = 200,
		raw = preview(),
		captured;
	const server = createServer(async (req, res) => {
		let body = "";
		for await (const chunk of req) body += chunk;
		captured = {
			method: req.method,
			path: req.url,
			headers: req.headers,
			body,
		};
		res.writeHead(status, { "Content-Type": "application/json" });
		res.end(
			JSON.stringify(
				status === 200 ? raw : { message: "PRIVATE BACKEND EVIDENCE" },
			),
		);
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const old = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	const path = `https://customer.test${cancellationPath(staff)}`,
		headers = {
			Cookie: "OTHER=PRIVATE; CINENEST_SESSION=SYNTHETIC_ONLY",
			Authorization: "SECRET",
		};
	try {
		let r = await forwardCancellation(new Request(path, { headers }));
		assert.equal(r.status, 200);
		assert.equal((await r.json()).refundPrincipalMinor, undefined);
		assert.equal(captured.headers.cookie, "CINENEST_SESSION=SYNTHETIC_ONLY");
		assert.equal(captured.headers.authorization, undefined);
		assert.equal(r.headers.get("cache-control"), "private, no-store");
		for (status of [400, 401, 403, 404, 409, 429, 503]) {
			r = await forwardCancellation(new Request(path, { headers }));
			assert.equal(r.status, status);
			assert(!(await r.text()).includes("PRIVATE"));
		}
		status = 200;
		raw = committed();
		r = await forwardCancellation(
			new Request(`${path}s`, {
				method: "POST",
				headers: {
					...headers,
					Origin: "https://customer.test",
					"X-CSRF-TOKEN": "SYNTHETIC",
					"Content-Type": "application/json",
				},
				body: JSON.stringify(command()),
			}),
		);
		assert.equal(r.status, 200);
		assert.deepEqual(JSON.parse(captured.body), command());
		assert.equal(captured.headers["x-csrf-token"], "SYNTHETIC");
		raw = { ...preview(), venueId: accountId };
		assert.equal(
			(await forwardCancellation(new Request(path, { headers }))).status,
			503,
		);
	} finally {
		if (old === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = old;
		await new Promise((r) => server.close(r));
	}
});

test("cancellation POST uses the incoming authority and rejects foreign origins before forwarding", async () => {
	const original = globalThis.fetch;
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	const requests = [];
	globalThis.fetch = async (url, options) => {
		requests.push({ url, options });
		return new Response('{"detail":"private stale-command evidence"}', {
			status: 409,
		});
	};
	const path = `${cancellationPath(context)}s`;
	const submit = (base, origin, extra = {}) =>
		forwardCancellation(
			new Request(base + path, {
				method: "POST",
				headers: {
					Cookie: "CINENEST_SESSION=SYNTHETIC_ONLY",
					"Content-Type": "application/json",
					"X-CSRF-TOKEN": "SYNTHETIC",
					...(origin === undefined ? {} : { Origin: origin }),
					...extra,
				},
				body: JSON.stringify(command()),
			}),
		);
	const normalized = "http://localhost:43227";
	const loopback = "http://127.0.0.1:43227";
	const host = { Host: "127.0.0.1:43227" };
	try {
		for (const response of [
			await submit("https://customer.test", "https://customer.test"),
			await submit(normalized, loopback, host),
			await submit(normalized, normalized),
		]) {
			assert.equal(response.status, 409);
			assert.equal(await response.text(), "");
		}
		assert.equal(requests.length, 3);
		for (const { url, options } of requests) {
			assert.equal(url.href, `http://127.0.0.1:8080${path}`);
			assert.equal(
				options.headers.get("cookie"),
				"CINENEST_SESSION=SYNTHETIC_ONLY",
			);
			assert.equal(options.headers.get("x-csrf-token"), "SYNTHETIC");
			assert.deepEqual(JSON.parse(options.body), command());
		}

		for (const origin of [
			undefined,
			"null",
			`${loopback}/`,
			"https://127.0.0.1:43227",
			normalized,
			"http://127.0.0.1:43228",
			"http://evil.test:43227",
		]) {
			assert.equal((await submit(normalized, origin, host)).status, 403);
		}
		assert.equal(
			(
				await submit(normalized, "https://evil.test", {
					...host,
					Forwarded: "host=evil.test;proto=https",
					"X-Forwarded-Host": "evil.test",
					"X-Forwarded-Proto": "https",
				})
			).status,
			403,
		);
		assert.equal(
			(await submit(normalized, loopback, { ...host, "X-CSRF-TOKEN": "" }))
				.status,
			403,
		);
		assert.equal(requests.length, 3);
	} finally {
		globalThis.fetch = original;
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
	}
});

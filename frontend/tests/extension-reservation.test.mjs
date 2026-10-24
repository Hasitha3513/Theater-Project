// Synthetic DTO/client/proxy fixtures; no real-backend browser E2E claim.
import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	reservationCommand,
	reservationResult,
	recoveryResult,
	reservationPath,
	reservationFetch,
	reservationWarning,
} from "../lib/extension-reservation.ts";
import { forwardReservation } from "../lib/extension-reservation-backend.ts";
import {
	reservationReference,
	ExtensionReservationPanel,
} from "../components/extension-reservation.tsx";
import {
	bookingId,
	accountId,
	requestId,
	venueId,
} from "./booking-cancellation-fixtures.mjs";
import {
	create,
	reservation,
	recovery,
	reserveId,
	booking,
} from "./extension-reservation-fixtures.mjs";
test("exact four reservation paths and invalid references", () => {
	assert.equal(
		reservationPath(bookingId),
		`/api/bookings/${bookingId}/extension-reservations`,
	);
	assert.equal(
		reservationPath(bookingId, requestId, "recover"),
		`/api/bookings/${bookingId}/extension-reservations/commands/${requestId}`,
	);
	assert.equal(
		reservationPath(bookingId, reserveId, "withdraw"),
		`/api/bookings/${bookingId}/extension-reservations/${reserveId}/withdrawals`,
	);
	assert.throws(() => reservationPath("private?token=secret"));
	assert.throws(() => reservationPath(bookingId, "bad"));
});
for (const [label, patch] of [
	["unchecked", { acknowledged: false }],
	["changed terms", { termsVersion: "NEW" }],
	["revision", { expectedPurchasedRevision: 2 }],
	["lifecycle", { expectedLifecycleVersion: 1 }],
	["negative minutes", { additionalMinutes: -1 }],
	["currency", { expectedCurrency: "USD" }],
	["money number", { expectedAdditionalMinor: 12 }],
	["secret extra", { secret: "no" }],
	["no config", { expectedConfiguration: "" }],
])
	test(`strict CREATE rejects ${label}`, () =>
		assert.throws(() => reservationCommand({ ...create(), ...patch })));
test("strict CREATE and withdrawal preserve command payload", () => {
	assert.deepEqual(reservationCommand(create()), create());
	assert.deepEqual(
		reservationCommand({ expectedRevision: 0, commandKey: requestId }, true),
		{ expectedRevision: 0, commandKey: requestId },
	);
	assert.throws(() =>
		reservationCommand({ expectedRevision: -1, commandKey: requestId }, true),
	);
});
for (const [label, patch] of [
	["wrong booking", { bookingId: venueId }],
	["paid", { paymentRequested: true }],
	["purchased mutation", { bookingChanged: true }],
	["real status", { state: "CONFIRMED" }],
	[
		"no uncertainty reason",
		{ effective: false, pendingReconciliation: true, reasons: [] },
	],
	["invented reason", { reasons: ["APPROVED"] }],
	["future observation", { observedAt: "2026-10-23T00:00:00Z" }],
	["late expiry", { expiresAt: "2026-10-24T05:00:00Z" }],
	[
		"wrong coverage",
		{
			additionalCoverage: {
				start: "2026-11-01T07:30:00Z",
				end: "2026-11-01T08:15:00Z",
			},
		},
	],
	[
		"buffer parity",
		{
			proposedSession: {
				...reservation().proposedSession,
				blockedUntil: "2026-11-01T08:16:00Z",
			},
			additionalCoverage: {
				...reservation().additionalCoverage,
				end: "2026-11-01T08:16:00Z",
			},
		},
	],
])
	test(`reservation privacy/invariant decoder rejects ${label}`, () =>
		assert.throws(() =>
			reservationResult({ ...reservation(), ...patch }, bookingId),
		));
test("allowlisted response strips secrets and nested evidence", () => {
	const v = reservationResult(
		{
			...reservation(),
			secret: "PRIVATE",
			originalSession: { ...reservation().originalSession, private: "PRIVATE" },
		},
		bookingId,
	);
	assert.equal(JSON.stringify(v).includes("PRIVATE"), false);
	assert.equal(v.warning, reservationWarning);
});
for (const state of ["WITHDRAWN", "EXPIRED", "INVALIDATED"])
	test(`authoritative terminal ${state}`, () =>
		assert.equal(
			reservationResult(
				{ ...reservation(), state, effective: false },
				bookingId,
			).state,
			state,
		));
test("pending reconciliation distinct from effective and terminal", () =>
	assert.equal(
		reservationResult(
			{
				...reservation(),
				effective: false,
				pendingReconciliation: true,
				reasons: ["PENDING_RECONCILIATION"],
			},
			bookingId,
		).effective,
		false,
	));
test("recovery validates exact command/booking then requires fresh status", () => {
	assert.equal(
		recoveryResult(
			{ ...recovery(requestId), private: "SECRET" },
			bookingId,
			requestId,
		).statusReadRequired,
		true,
	);
	assert.throws(() => recoveryResult(recovery(venueId), bookingId, requestId));
	assert.throws(() =>
		recoveryResult(
			{ ...recovery(requestId), statusReadRequired: false },
			bookingId,
			requestId,
		),
	);
});
test("browser reference contains opaque identities and withdrawal revision only", () => {
	assert.deepEqual(
		reservationReference(
			JSON.stringify({ commandKey: requestId, reservationId: reserveId }),
		),
		{ commandKey: requestId, reservationId: reserveId },
	);
	for (const v of [
		{ commandKey: "bad" },
		{ commandKey: requestId, amount: "100" },
		{ commandKey: requestId, token: "private" },
		{
			commandKey: requestId,
			withdrawal: { expectedRevision: -1, commandKey: venueId },
		},
	])
		assert.equal(reservationReference(JSON.stringify(v)), undefined);
});
test("semantic initial UI is explicit and truthful", () => {
	const html = renderToStaticMarkup(
		React.createElement(ExtensionReservationPanel, { booking: booking() }),
	);
	assert.match(html, /Temporary synthetic reservation only/);
	assert.match(html, /physical readiness UNVERIFIED/);
	assert.match(html, /Reload Extension Reservation/);
	assert.doesNotMatch(html, /checked=""/);
	assert.doesNotMatch(html, /Confirm payment|Pay now/);
});
test("client brackets parsing and verifies current account; no unauthorized POST", async () => {
	const old = globalThis.fetch;
	let posts = 0;
	try {
		globalThis.fetch = async (_path, options) => {
			if (options?.method === "POST") posts++;
			return Response.json({ accountId: venueId, eligibility: "ELIGIBLE" });
		};
		await assert.rejects(() =>
			reservationFetch(
				reservationPath(bookingId),
				new AbortController().signal,
				accountId,
				create(),
			),
		);
		assert.equal(posts, 0);
		const c = new AbortController();
		globalThis.fetch = async () => ({
			ok: true,
			json: async () => {
				c.abort();
				return { accountId, eligibility: "ELIGIBLE" };
			},
		});
		await assert.rejects(() =>
			reservationFetch(
				reservationPath(bookingId, reserveId),
				c.signal,
				accountId,
			),
		);
	} finally {
		globalThis.fetch = old;
	}
});
test("actual HTTP proxy exact forwarding, CSRF, sanitized denials and strict query/method allowlist", async () => {
	let status = 200,
		body = reservation(),
		calls = [];
	const server = createServer(async (req, res) => {
		let input = "";
		for await (const x of req) input += x;
		calls.push({
			url: req.url,
			method: req.method,
			csrf: req.headers["x-csrf-token"],
			body: input,
			cookie: req.headers.cookie,
		});
		res.writeHead(status, { "content-type": "application/json" });
		res.end(JSON.stringify(body));
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const saved = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	const req = (path, method = "GET", payload, headers = {}) =>
		new Request("https://cinenest.test" + path, {
			method,
			headers: {
				cookie: "CINENEST_SESSION=synthetic-only",
				origin: "https://cinenest.test",
				"content-type": "application/json",
				"x-csrf-token": "synthetic-csrf",
				...headers,
			},
			body: payload ? JSON.stringify(payload) : undefined,
		});
	try {
		const path = reservationPath(bookingId);
		let r = await forwardReservation(req(path, "POST", create()));
		assert.equal(r.status, 200);
		assert.equal(calls.at(-1).csrf, "synthetic-csrf");
		assert.deepEqual(JSON.parse(calls.at(-1).body), create());
		assert.equal(r.headers.get("Cache-Control"), "private, no-store");
		r = await forwardReservation(req(reservationPath(bookingId, reserveId)));
		assert.equal(r.status, 200);
		body = recovery(requestId);
		assert.equal(
			(
				await forwardReservation(
					req(reservationPath(bookingId, requestId, "recover")),
				)
			).status,
			200,
		);
		body = {
			...reservation(),
			state: "WITHDRAWN",
			effective: false,
			proposalRevision: 1,
		};
		assert.equal(
			(
				await forwardReservation(
					req(reservationPath(bookingId, reserveId, "withdraw"), "POST", {
						expectedRevision: 0,
						commandKey: requestId,
					}),
				)
			).status,
			200,
		);
		for (const code of [400, 401, 403, 404, 409, 429, 503, 500, 302]) {
			status = code;
			body = { message: "PRIVATE EVIDENCE" };
			const r = await forwardReservation(
				req(reservationPath(bookingId, reserveId)),
			);
			assert.equal(r.status, [500, 302].includes(code) ? 503 : code);
			assert.equal((await r.text()).includes("PRIVATE"), false);
		}
		const count = calls.length;
		for (const query of ["?page=0", "?x=1&x=2", "?token=secret"])
			assert.equal(
				(
					await forwardReservation(
						req(reservationPath(bookingId, reserveId) + query),
					)
				).status,
				400,
			);
		assert.equal((await forwardReservation(req(path, "GET"))).status, 404);
		assert.equal(
			(
				await forwardReservation(
					req(path, "POST", create(), { origin: "https://attacker.test" }),
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardReservation(
					req(path, "POST", create(), { "x-csrf-token": "" }),
				)
			).status,
			403,
		);
		assert.equal(
			(await forwardReservation(req(path, "POST", create(), { cookie: "" })))
				.status,
			401,
		);
		assert.equal(calls.length, count);
	} finally {
		if (saved === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = saved;
		server.closeAllConnections();
		await new Promise((r) => server.close(r));
	}
});

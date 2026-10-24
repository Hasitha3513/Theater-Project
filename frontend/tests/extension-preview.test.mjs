import { NextRequest } from "next/server.js";
// Isolated client/proxy fixtures; not real-backend E2E.
import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	extensionCommand,
	extensionResult,
	extensionWarning,
	extensionNotice,
	extensionMoney,
	requestExtension,
	extensionPath,
} from "../lib/extension-preview.ts";
import { forwardExtension } from "../lib/extension-preview-backend.ts";
import {
	BookingExtensionPreview,
	ExtensionPriceSummary,
} from "../components/booking-extension-preview.tsx";
import {
	bookingId,
	accountId,
	venueId,
	customerBooking,
} from "./booking-cancellation-fixtures.mjs";
export const eligibility = () => ({
	bookingId,
	purchasedRevision: 1,
	lifecycleVersion: 0,
	eligible: true,
	reason: "ELIGIBLE",
	observedAt: "2026-10-24T04:00:00Z",
	synthetic: true,
	warning: extensionWarning,
	timeReserved: false,
	paymentRequested: false,
	bookingChanged: false,
	occupancy: "UNKNOWN",
	readiness: "UNVERIFIED",
	durationUnits: {
		incrementMinutes: 30,
		minimumAdditionalMinutes: 30,
		maximumAdditionalMinutes: 120,
	},
});
export const quote = () => ({
	...eligibility(),
	currentDurationMinutes: 180,
	additionalMinutes: 30,
	proposedDurationMinutes: 210,
	additionalMinor: "12345",
	currency: "LKR",
	packageVersionId: venueId,
	pricingBasis: "FROZEN_PURCHASED_TERMS",
});
const command = {
	additionalMinutes: 30,
	expectedPurchasedRevision: 1,
	expectedLifecycleVersion: 0,
};
test("exact protected paths and strict preview command", () => {
	assert.equal(
		extensionPath(bookingId),
		`/api/bookings/${bookingId}/extension-eligibility`,
	);
	assert.throws(() => extensionPath("secret?x"));
	assert.deepEqual(extensionCommand(command), command);
	for (const x of [
		{ ...command, additionalMinutes: 0 },
		{ ...command, additionalMinutes: 1.5 },
		{ ...command, additionalMinutes: "30" },
		{ ...command, extra: true },
		{ ...command, expectedLifecycleVersion: -1 },
	])
		assert.throws(() => extensionCommand(x));
});
test("allowlisted eligibility strips private metadata", () => {
	const v = extensionResult(
		{
			...eligibility(),
			secret: "DO NOT FORWARD",
			durationUnits: { ...eligibility().durationUnits, holdId: "private" },
		},
		bookingId,
	);
	assert.equal(JSON.stringify(v).includes("private"), false);
	assert.equal(v.secret, undefined);
});
for (const reason of [
	"SESSION_STARTED",
	"CREDENTIAL_EVER_ISSUED",
	"AMENDMENT_PENDING",
	"EXTENSION_PENDING",
	"ROOM_CONTEXT_CHANGED",
	"FROZEN_PRICING_UNSUPPORTED",
	"ROOM_CONSTRAINTS_UNSATISFIED",
	"BOOKING_NOT_CONFIRMED",
	"PURCHASE_REVISION_UNSUPPORTED",
])
	test(`ineligible reason ${reason}`, () => {
		assert.equal(
			extensionResult(
				{ ...eligibility(), eligible: false, reason, durationUnits: undefined },
				bookingId,
			).eligible,
			false,
		);
	});
test("malformed or falsely successful DTO fails closed", () => {
	for (const patch of [
		{ bookingId: venueId },
		{ timeReserved: true },
		{ paymentRequested: true },
		{ bookingChanged: true },
		{ occupancy: "EMPTY" },
		{ reason: "APPROVED" },
		{ observedAt: "garbage" },
		{ durationUnits: { incrementMinutes: 0 } },
	])
		assert.throws(() =>
			extensionResult({ ...eligibility(), ...patch }, bookingId),
		);
	for (const patch of [
		{ additionalMinor: "1.23" },
		{ currency: "USD" },
		{ additionalMinutes: 31 },
		{ proposedDurationMinutes: 999 },
		{ packageVersionId: "secret" },
	])
		assert.throws(() =>
			extensionResult({ ...quote(), ...patch }, bookingId, true),
		);
});
test("exact minor-unit prices and venue-local derived end", () => {
	assert.equal(extensionMoney("9007199254740993"), "LKR 90071992547409.93");
	const html = renderToStaticMarkup(
		React.createElement(ExtensionPriceSummary, {
			value: quote(),
			booking: customerBooking(),
		}),
	);
	for (const text of [
		"LKR 123.45",
		"LKR 300.07",
		"13:30",
		"210",
		"calculated display",
		"Asia/Colombo",
		"No total price",
	])
		assert.ok(html.includes(text), text);
});
test("initial UI explicit preview only, no automatic API operation", () => {
	const html = renderToStaticMarkup(
		React.createElement(BookingExtensionPreview, {
			booking: customerBooking(),
		}),
	);
	assert.ok(html.includes("Check Extension Options"));
	assert.ok(html.includes(extensionNotice));
	assert.ok(html.includes("Overlapping inventory is not checked"));
	assert.equal(html.includes("Preview Additional Price"), false);
	assert.equal(html.includes("<main"), false);
});
test("cancelled booking cannot initiate preview", () => {
	const html = renderToStaticMarkup(
		React.createElement(BookingExtensionPreview, {
			booking: { ...customerBooking(), state: "SIMULATED_CANCELLED" },
		}),
	);
	assert.ok(html.includes("disabled"));
	assert.ok(html.includes("unavailable for this booking status"));
});
test("proxy exact methods, no query, session and independent CSRF", async () => {
	const url = `http://localhost${extensionPath(bookingId)}`;
	assert.equal((await forwardExtension(new Request(url))).status, 401);
	assert.equal(
		(await forwardExtension(new Request(url + "?page=0"))).status,
		400,
	);
	assert.equal(
		(await forwardExtension(new Request(url, { method: "POST" }))).status,
		404,
	);
	assert.equal(
		(
			await forwardExtension(
				new Request(url.replace("extension-eligibility", "extension-quotes"), {
					method: "POST",
					headers: { cookie: "CINENEST_SESSION=synthetic" },
					body: "{}",
				}),
			)
		).status,
		403,
	);
});
test("real loopback HTTP proxy sanitization and backend authorization authority", async () => {
	const original = process.env.CINENEST_AUTH_ORIGIN;
	let status = 200;
	const calls = [];
	const server = createServer(async (req, res) => {
		let body = "";
		for await (const p of req) body += p;
		calls.push({
			method: req.method,
			url: req.url,
			headers: req.headers,
			body,
		});
		res.writeHead(status, { "content-type": "application/json" });
		res.end(
			JSON.stringify(
				status === 200
					? {
							...(req.method === "POST" ? quote() : eligibility()),
							secret: "PRIVATE",
						}
					: { message: "PRIVATE" },
			),
		);
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	const req = (post = false) =>
		new Request(`http://localhost${extensionPath(bookingId, post)}`, {
			method: post ? "POST" : "GET",
			headers: {
				cookie: "other=secret; CINENEST_SESSION=synthetic",
				...(post
					? {
							origin: "http://localhost",
							"x-csrf-token": "synthetic-csrf",
							"content-type": "application/json",
						}
					: {}),
			},
			body: post ? JSON.stringify(command) : undefined,
		});
	try {
		for (const post of [false, true]) {
			const r = await forwardExtension(req(post));
			assert.equal(r.status, 200);
			assert.equal(r.headers.get("cache-control"), "private, no-store");
			assert.equal((await r.json()).secret, undefined);
			assert.equal(calls.at(-1).headers.cookie, "CINENEST_SESSION=synthetic");
		}
		assert.deepEqual(JSON.parse(calls[1].body), command);
		// Next.js may supply an internal request URL; browser Host/Origin remain public.
		const publicRequest = () =>
			new NextRequest(
				"http://127.0.0.1:43210" + extensionPath(bookingId, true),
				{
					method: "POST",
					headers: {
						host: "127.0.0.1:43210",
						origin: "http://127.0.0.1:43210",
						cookie: "CINENEST_SESSION=synthetic",
						"x-csrf-token": "synthetic-csrf",
						"content-type": "application/json",
					},
					body: JSON.stringify(command),
				},
			);
		assert.equal((await forwardExtension(publicRequest())).status, 200);
		for (const field of ["origin", "x-csrf-token"]) {
			const bad = publicRequest();
			bad.headers.delete(field);
			const count = calls.length;
			assert.equal((await forwardExtension(bad)).status, 403);
			assert.equal(calls.length, count);
		}
		const cross = publicRequest();
		cross.headers.set("origin", "http://attacker.test");
		assert.equal((await forwardExtension(cross)).status, 403);
		const forwarded = publicRequest();
		forwarded.headers.set("origin", "http://attacker.test");
		forwarded.headers.set("x-forwarded-host", "attacker.test");
		assert.equal((await forwardExtension(forwarded)).status, 403);
		for (const code of [400, 401, 403, 404, 409, 429, 503]) {
			status = code;
			const r = await forwardExtension(req());
			assert.equal(r.status, code);
			assert.equal(await r.text(), "");
		}
	} finally {
		server.close();
		await once(server, "close");
		if (original === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = original;
	}
});
test("explicit client POST uses CSRF with no mutation routes", async () => {
	const original = global.fetch,
		calls = [];
	global.fetch = async (url, opts) => {
		calls.push([url, opts]);
		return Response.json(
			url === "/api/accounts/me"
				? { accountId, eligibility: "ELIGIBLE" }
				: url === "/api/session/csrf"
					? { headerName: "X-CSRF-TOKEN", token: "synthetic" }
					: quote(),
		);
	};
	try {
		const r = await requestExtension(
			bookingId,
			new AbortController().signal,
			command,
		);
		assert.equal(r.value.additionalMinor, "12345");
		const post = calls.find((x) => x[1].method === "POST");
		assert.equal(post[1].headers.get("x-csrf-token"), "synthetic");
		assert.equal(post[0], extensionPath(bookingId, true));
		assert.equal(calls.filter((x) => x[1].method === "POST").length, 1);
	} finally {
		global.fetch = original;
	}
});
test("JSON finishing after abort cannot publish", async () => {
	const original = global.fetch,
		c = new AbortController();
	global.fetch = async (url) =>
		url === "/api/accounts/me"
			? Response.json({ accountId, eligibility: "ELIGIBLE" })
			: {
					ok: true,
					json: async () => {
						c.abort();
						return eligibility();
					},
				};
	try {
		await assert.rejects(requestExtension(bookingId, c.signal));
	} finally {
		global.fetch = original;
	}
});
test("session change after JSON denies result", async () => {
	const original = global.fetch;
	let count = 0;
	global.fetch = async (url) =>
		Response.json(
			url === "/api/accounts/me"
				? {
						accountId: ++count === 1 ? accountId : venueId,
						eligibility: "ELIGIBLE",
					}
				: eligibility(),
		);
	try {
		await assert.rejects(
			requestExtension(bookingId, new AbortController().signal),
			(e) => e.status === 401,
		);
	} finally {
		global.fetch = original;
	}
});
test("expired session prevents endpoint call", async () => {
	const original = global.fetch,
		calls = [];
	global.fetch = async (url) => {
		calls.push(url);
		return new Response(null, { status: 401 });
	};
	try {
		await assert.rejects(
			requestExtension(bookingId, new AbortController().signal),
			(e) => e.status === 401,
		);
		assert.deepEqual(calls, ["/api/accounts/me"]);
	} finally {
		global.fetch = original;
	}
});

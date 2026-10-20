import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { bankRoute, forwardBank } from "../lib/bank-backend.ts";
import { bankLabels, validateBank } from "../lib/bank.ts";
import { BankWorkspace, BankSummary } from "../components/bank.tsx";
const id = "00000000-0000-4000-8000-000000000001";
const value = {
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels: bankLabels,
	requestState: "OPEN",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-20T10:00Z",
	expiresAt: "2026-10-20T10:01Z",
	submitBy: "2026-10-20T09:00Z",
	reviewBy: "2026-10-20T09:30Z",
	overdue: false,
	amountMinor: "12352",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
};
test("exact twelve bank operations exclude credit, allocation, refund and arbitrary proxy paths", () => {
	const paths = [
		["POST", "/api/bank/requests"],
		["GET", `/api/bank/requests/${id}`],
		["POST", `/api/bank/requests/${id}/claims`],
		["POST", `/api/bank/requests/${id}/resubmissions`],
		["POST", `/api/bank/claims/${id}/uploads`],
		["PUT", `/api/bank/uploads/${id}/content`],
		["POST", `/api/bank/uploads/${id}/complete`],
		["GET", `/api/admin/venues/${id}/bank/requests`],
		["GET", `/api/admin/venues/${id}/bank/requests/${id}`],
		["GET", `/api/admin/venues/${id}/bank/requests/${id}/evidence/${id}`],
		["POST", `/api/admin/venues/${id}/bank/requests/${id}/decisions`],
		["POST", `/api/admin/venues/${id}/bank/requests/${id}/escalations`],
	];
	for (const [method, path] of paths) {
		assert.equal(bankRoute(path, method), true);
		assert.equal(bankRoute(path, "DELETE"), false);
	}
	for (const action of ["allocate", "refund", "confirm", "verify-credit"])
		assert.equal(
			bankRoute(`/api/bank/requests/${id}/${action}`, "POST"),
			false,
		);
	assert.equal(bankRoute("/api/bank/../secrets", "GET"), false);
});
test("customer and reviewer shells always show four safety labels", () => {
	for (const reviewer of [false, true]) {
		const html = render(h(BankWorkspace, { reviewer }));
		for (const label of bankLabels) assert.ok(html.includes(label));
		assert.doesNotMatch(html, /Pay now|Confirm booking|Verify funds/);
	}
});
test("server response cannot imply real payment or confirmed booking", () => {
	assert.equal(validateBank(value), value);
	for (const change of [
		{ synthetic: false },
		{ paymentVerified: true },
		{ bookingConfirmed: true },
		{ labels: [] },
		{ revision: -1 },
		{ amountMinor: "1.5" },
		{ currency: "USD" },
		{ requestState: "PAYMENT_SUCCESS" },
	])
		assert.throws(() => validateBank({ ...value, ...change }));
});
test("review outcome, inventory expiry and correction history remain distinct", () => {
	const html = render(
		h(BankSummary, {
			value: {
				...value,
				requestState: "EXPIRED",
				reviewState: "REJECTED",
				inventoryState: "EXPIRED",
				history: [
					{
						id,
						action: "NEEDS_INFORMATION",
						reason: "CORRECTION_REQUIRED",
						at: "2026-10-17",
					},
				],
			},
		}),
	);
	assert.match(html, /EXPIRED \/ REJECTED \/ EXPIRED/);
	assert.match(html, /CORRECTION_REQUIRED/);
	assert.match(html, /minor units/);
	assert.doesNotMatch(html, /object_key|encryption-key/);
});
test("bank transport fails closed when unconfigured and refuses query/path widening", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN;
	delete process.env.CINENEST_AUTH_ORIGIN;
	try {
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}`),
				)
			).status,
			503,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}?download=true`),
				)
			).status,
			404,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/requests/${id}/allocate`, {
						method: "POST",
					}),
				)
			).status,
			404,
		);
	} finally {
		if (prior) process.env.CINENEST_AUTH_ORIGIN = prior;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("same-origin CSRF and accepted image MIME are required before forwarding", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:1";
	try {
		assert.equal(
			(
				await forwardBank(
					new Request("http://localhost/api/bank/requests", {
						method: "POST",
						headers: { "content-type": "application/json" },
						body: "{}",
					}),
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardBank(
					new Request(`http://localhost/api/bank/uploads/${id}/content`, {
						method: "PUT",
						headers: {
							origin: "http://localhost",
							"x-csrf-token": "SYNTHETIC",
							"content-type": "application/pdf",
						},
						body: "%PDF",
					}),
				)
			).status,
			415,
		);
	} finally {
		if (prior) process.env.CINENEST_AUTH_ORIGIN = prior;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});

// CN-UI-FLOW-002: protected-context transport contracts, not real-bank acceptance.
import {
	bankHref,
	bankReadinessReasons,
	validateBankContext,
	loadBankContext,
	bankCreationBody,
	bankCreationResult,
} from "../lib/bank-handoff.ts";
import {
	BankContextSummary,
	BankRequestCreation,
} from "../components/bank-handoff.tsx";
const readiness = () => ({
	quoteId: id,
	quoteRevision: 3,
	contentHash: "a".repeat(64),
	quoteExpiresAt: "2026-10-20T10:00:00Z",
	quoteSummary: {
		currency: "LKR",
		totalMinor: "12352",
		paymentMethod: "BANK",
		sessionStart: "2026-10-21T04:30:00Z",
		sessionEnd: "2026-10-21T07:30:00Z",
		timezone: "Asia/Colombo",
		packageName: "SYNTHETIC package",
	},
	verificationReadiness: "ELIGIBLE",
	eligible: true,
	reason: "ELIGIBLE",
	serverNow: "2026-10-20T04:30:00Z",
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels: bankLabels,
});
test("bank context validates exact server binding, known refusal and synthetic truth", () => {
	const v = readiness();
	assert.equal(validateBankContext(v, id), v);
	for (const reason of Object.keys(bankReadinessReasons))
		assert.equal(
			validateBankContext(
				{
					...v,
					reason,
					eligible: reason === "ELIGIBLE",
					verificationReadiness:
						reason === "VERIFICATION_REQUIRED" ? "REQUIRED" : "ELIGIBLE",
				},
				id,
			).reason,
			reason,
		);
	for (const change of [
		{ quoteId: crypto.randomUUID() },
		{ quoteRevision: -1 },
		{ contentHash: "invalid" },
		{ eligible: false },
		{ reason: "UNKNOWN" },
		{ verificationReadiness: "REQUIRED" },
		{ synthetic: false },
		{ bookingConfirmed: true },
		{ paymentVerified: true },
		{ labels: [] },
		{ quoteSummary: { ...v.quoteSummary, currency: "USD" } },
	])
		assert.throws(() => validateBankContext({ ...v, ...change }, id));
});
test("bank navigation contains only an opaque reference and rendered context has no editable hash", () => {
	const v = readiness();
	const url = new URL(bankHref(id), "https://example.test");
	assert.deepEqual([...url.searchParams.keys()], ["quote"]);
	assert.equal(url.pathname, "/bank");
	assert.throws(() => bankHref("invalid"));
	const html = render(
		h(BankContextSummary, { value: { ...v, venueId: id, roomId: id } }),
	);
	assert.match(html, /SYNTHETIC package/);
	assert.match(html, /123.52/);
	assert.doesNotMatch(html, /a{64}|contentHash|identity evidence/);
	const shell = render(
		h(BankRequestCreation, {
			reference: id,
			onCreated: () => {},
			onDenied: () => {},
		}),
	);
	assert.doesNotMatch(
		shell,
		/bank-hash|bank-quote-revision|Pay now|Confirm booking/,
	);
});
test("readiness proxy forwards only one UUID lookup, one session cookie and no client authority", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN,
		original = globalThis.fetch;
	process.env.CINENEST_AUTH_ORIGIN = "https://backend.example.test";
	let seen;
	globalThis.fetch = async (url, init) => {
		seen = { url: String(url), init };
		return Response.json(readiness());
	};
	try {
		const response = await forwardBank(
			new Request(
				`https://frontend.example.test/api/bank/requests/readiness?quote=${id}`,
				{
					headers: {
						cookie: "CINENEST_SESSION=SYNTHETIC; other=PRIVATE",
						authorization: "DO_NOT_FORWARD",
					},
				},
			),
		);
		assert.equal(response.status, 200);
		assert.match(response.headers.get("cache-control"), /private.*no-store/);
		assert.equal(
			seen.url,
			`https://backend.example.test/api/bank/requests/readiness?quote=${id}`,
		);
		assert.equal(seen.init.headers.get("cookie"), "CINENEST_SESSION=SYNTHETIC");
		assert.equal(seen.init.headers.get("authorization"), null);
		assert.equal(seen.init.method, "GET");
		for (const suffix of [
			"",
			`?quote=bad`,
			`?quote=${id}&quote=${id}`,
			`?quote=${id}&eligible=true`,
			`?quote=${id}&contentHash=SECRET`,
		])
			assert.equal(
				(
					await forwardBank(
						new Request(
							`https://frontend.example.test/api/bank/requests/readiness${suffix}`,
						),
					)
				).status,
				400,
			);
		assert.equal(bankRoute("/api/bank/requests/readiness", "POST"), false);
	} finally {
		globalThis.fetch = original;
		if (prior) process.env.CINENEST_AUTH_ORIGIN = prior;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("authoritative readiness supplies POST revision/hash without automatic submission", async () => {
	const original = globalThis.fetch;
	const calls = [];
	globalThis.fetch = async (path, init) => {
		calls.push({ path, init });
		return Response.json(readiness());
	};
	try {
		const loaded = await loadBankContext(id, false);
		assert.equal(calls.length, 1);
		assert.match(calls[0].path, /readiness\?quote=/);
		assert.equal(calls[0].init.cache, "no-store");
		assert.equal(calls[0].init.method, undefined);
		const body = bankCreationBody(loaded, id);
		assert.equal(body.quoteRevision, 3);
		assert.equal(body.contentHash, "a".repeat(64));
		assert.deepEqual(Object.keys(body), [
			"quoteId",
			"quoteRevision",
			"contentHash",
			"commandKey",
		]);
		assert.throws(() => bankCreationBody({ ...loaded, eligible: false }, id));
		assert.throws(() =>
			bankCreationResult({ ...value, quoteId: crypto.randomUUID() }, id),
		);
		assert.equal(calls.length, 1);
		for (const invalid of ["", "bad", `${id}&hash=SECRET`])
			await assert.rejects(() => loadBankContext(invalid));
		assert.equal(calls.length, 1);
	} finally {
		globalThis.fetch = original;
	}
});
test("readiness access/session/server failures cannot become eligible context", async () => {
	const original = globalThis.fetch;
	try {
		for (const status of [401, 403, 404, 409, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "safe denial" }, { status });
			await assert.rejects(
				() => loadBankContext(id, false),
				(e) => e.status === status,
			);
		}
		globalThis.fetch = async () => {
			throw new Error("network timeout");
		};
		await assert.rejects(() => loadBankContext(id, false));
	} finally {
		globalThis.fetch = original;
	}
});

test("bank arrival cross-checks protected quotation identity and current revision/hash", async () => {
	const original = globalThis.fetch;
	let mutation = {};
	const r = readiness();
	const quote = () => ({
		...r,
		quoteId: id,
		venueId: id,
		roomId: id,
		revision: 3,
		state: "ACCEPTED",
		currency: "LKR",
		totalMinor: "12352",
		paymentMethod: "BANK",
		synthetic: true,
		commercialIssuance: false,
		checkoutEnabled: false,
		bookingConfirmed: false,
		expiresAt: r.quoteExpiresAt,
		holdExpiresAt: r.quoteExpiresAt,
		serverNow: r.serverNow,
		timezone: "Asia/Colombo",
		disclosures: [
			"TEST / SYNTHETIC QUOTATION",
			"NOT A COMMERCIAL OFFER",
			"NO PAYMENT OR BOOKING CONFIRMATION",
		],
		optionalServiceIds: [],
		package: { name: "SYNTHETIC package" },
		lines: [
			{
				lineId: "BASE",
				kind: "BASE",
				label: "SYNTHETIC",
				quantity: 1,
				unitMinor: "12352",
				amountMinor: "12352",
			},
		],
		...mutation,
	});
	const calls = [];
	globalThis.fetch = async (path) => {
		calls.push(path);
		return Response.json(path.includes("readiness") ? r : quote());
	};
	try {
		assert.equal((await loadBankContext(id)).venueId, id);
		assert.equal(calls.length, 2);
		for (const change of [
			{ revision: 2 },
			{ contentHash: "b".repeat(64) },
			{ quoteId: crypto.randomUUID() },
			{ roomId: undefined },
		]) {
			mutation = change;
			await assert.rejects(
				() => loadBankContext(id),
				(e) => e.status === 409,
			);
		}
	} finally {
		globalThis.fetch = original;
	}
});

test("exact owned request navigation contains only an opaque reference", async () => {
	const { bankRequestHref, loadBankRequest } = await import(
		"../lib/bank-request.ts"
	);
	assert.equal(bankRequestHref(id), `/bank?request=${id}`);
	for (const ref of ["", "../other", `${id}&hash=secret`, "not-an-id"]) {
		assert.throws(() => bankRequestHref(ref));
		await assert.rejects(loadBankRequest(ref));
	}
	const { BankRequestDetail } = await import(
		"../components/bank-request-detail.tsx"
	);
	const html = render(h(BankRequestDetail, { reference: id }));
	assert.match(html, /Return to customer history/);
	assert.doesNotMatch(
		html,
		/type="file"|Submit claim|Create synthetic request/,
	);
});
test("exact request reload is GET-only, no-store and rejects a mismatched response", async () => {
	const { loadBankRequest } = await import("../lib/bank-request.ts");
	const old = globalThis.fetch;
	const calls = [];
	try {
		globalThis.fetch = async (path, init) => {
			calls.push({ path, init });
			return Response.json(value);
		};
		assert.equal((await loadBankRequest(id)).id, id);
		assert.equal(calls.length, 1);
		assert.equal(calls[0].path, `/api/bank/requests/${id}`);
		assert.equal(calls[0].init.cache, "no-store");
		assert.equal(calls[0].init.method, undefined);
		globalThis.fetch = async () =>
			Response.json({ ...value, id: "00000000-0000-4000-8000-000000000002" });
		await assert.rejects(loadBankRequest(id), /mismatch/);
		for (const status of [401, 403, 404, 409, 500, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "refused" }, { status });
			await assert.rejects(loadBankRequest(id), (e) => e.status === status);
		}
		globalThis.fetch = async () => {
			throw Error("network timeout");
		};
		await assert.rejects(loadBankRequest(id), /network timeout/);
	} finally {
		globalThis.fetch = old;
	}
});

test("only an explicit simulated booking state with an opaque identity offers customer navigation", async () => {
	const { confirmedBookingHref } = await import("../lib/bank-request.ts");
	assert.equal(
		confirmedBookingHref("SIMULATED_CONFIRMED", id),
		`/bookings/${id}`,
	);
	for (const status of [
		undefined,
		"NOT_CONFIRMED",
		"PENDING",
		"PARTIAL",
		"UNKNOWN",
		"REVIEWED",
		"SIMULATED_ALLOCATED",
	])
		assert.equal(confirmedBookingHref(status, id), null);
	for (const ref of [null, "", `${id}?hash=secret`, "../other"])
		assert.equal(confirmedBookingHref("SIMULATED_CONFIRMED", ref), null);
	const financial = {
		allocatedMinor: 12352,
		outstandingMinor: 0,
		syntheticFundingStatus: "SIMULATED_ALLOCATED",
		syntheticBookingStatus: "SIMULATED_CONFIRMED",
		bookingId: id,
	};
	const html = render(
		h(BankSummary, { value: { ...value, financial }, customer: true }),
	);
	assert.ok(html.includes(`href="/bookings/${id}"`));
	assert.ok(html.includes("View simulated booking details"));
	for (const state of ["NOT_CONFIRMED", "UNKNOWN"]) {
		const denied = render(
			h(BankSummary, {
				value: {
					...value,
					reviewState: "REVIEWED",
					financial: { ...financial, syntheticBookingStatus: state },
				},
				customer: true,
			}),
		);
		assert.doesNotMatch(denied, /View simulated booking details/);
	}
	assert.doesNotMatch(
		render(h(BankSummary, { value: { ...value, financial } })),
		/View simulated booking details/,
	);
});

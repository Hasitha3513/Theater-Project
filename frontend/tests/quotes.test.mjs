import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	CommercialPreviewSummary,
	QuoteSummary,
	SyntheticQuotation,
} from "../components/quotes.tsx";
import {
	commercialPreviewQuery,
	quoteDisclosures,
	validateCommercialPreview,
	validateQuote,
} from "../lib/quotes.ts";
import { forwardQuotes, quoteRoute } from "../lib/room-backend.ts";

const id = "00000000-0000-4000-8000-000000000001";
const q = {
	quoteId: id,
	revision: 1,
	contentHash: "a".repeat(64),
	state: "ISSUED",
	currency: "LKR",
	totalMinor: "250007",
	paymentMethod: "BANK",
	synthetic: true,
	commercialIssuance: false,
	checkoutEnabled: false,
	bookingConfirmed: false,
	expiresAt: "2026-10-20T00:10:00Z",
	holdExpiresAt: "2026-10-20T00:10:00Z",
	serverNow: "2026-10-20T00:01:00Z",
	timezone: "Asia/Colombo",
	disclosures: [...quoteDisclosures],
	optionalServiceIds: [],
	package: { name: "SYNTHETIC package" },
	lines: [
		{
			lineId: "BASE",
			kind: "BASE",
			label: "SYNTHETIC base",
			quantity: 1,
			unitMinor: "250007",
			amountMinor: "250007",
		},
	],
};
test("only four approved quote operations are transported", () => {
	for (const [path, method] of [
		["/api/quotes", "POST"],
		[`/api/quotes/${id}`, "GET"],
		[`/api/quotes/${id}/revisions`, "POST"],
		[`/api/quotes/${id}/accept`, "POST"],
	])
		assert.equal(quoteRoute(path, method), true);
	for (const [path, method] of [
		["/api/quotes", "GET"],
		[`/api/quotes/${id}`, "DELETE"],
		[`/api/quotes/${id}/checkout`, "POST"],
		[`/api/quotes/${id}/accept`, "GET"],
	])
		assert.equal(quoteRoute(path, method), false);
});
test("quote validator rejects commercial success malformed exact amounts and hold renewal", () => {
	assert.equal(validateQuote(q), q);
	for (const bad of [
		{ ...q, synthetic: false },
		{ ...q, commercialIssuance: true },
		{ ...q, bookingConfirmed: true },
		{ ...q, disclosures: [] },
		{ ...q, totalMinor: "1.2" },
		{ ...q, revision: -1 },
		{ ...q, expiresAt: "2026-10-20T00:11:00Z" },
		{ ...q, contentHash: "bad" },
	])
		assert.throws(() => validateQuote(bad));
});
test("itemized synthetic review labels exact money tax validity and no commercial confirmation", () => {
	const html = render(h(QuoteSummary, { quote: q }));
	for (const disclosure of quoteDisclosures)
		assert.ok(html.includes(disclosure));
	assert.match(html, /2500.07/);
	assert.match(html, /not approved Sri Lankan tax policy/);
	assert.match(html, /Original hold expiry/);
	assert.doesNotMatch(html, /Pay now|Confirm booking/);
});
test("synthetic interface has no invented package or price and guards initial acceptance", () => {
	const html = render(
		h(SyntheticQuotation, { hold: { holdId: id }, venueId: id }),
	);
	for (const disclosure of quoteDisclosures)
		assert.ok(html.includes(disclosure));
	assert.match(html, /Commercial checkout is disabled/);
	assert.doesNotMatch(html, /Accept synthetic revision only|2500/);
});
test("transport rejects cross-origin writes and preserves scoped cookie CSRF and safe errors", async () => {
	const { createServer } = await import("node:http");
	const { once } = await import("node:events");
	let observed;
	const server = createServer(async (req, res) => {
		let body = "";
		for await (const part of req) body += part;
		observed = { headers: req.headers, body, url: req.url };
		res.writeHead(409, { "Content-Type": "application/json" });
		res.end('{"message":"private upstream diagnostic"}');
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const before = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	try {
		assert.equal(
			(
				await forwardQuotes(
					new Request("http://localhost/api/quotes", {
						method: "POST",
						headers: {
							origin: "https://evil.invalid",
							"x-csrf-token": "SYNTHETIC",
						},
					}),
				)
			).status,
			403,
		);
		const result = await forwardQuotes(
			new Request("http://localhost/api/quotes", {
				method: "POST",
				headers: {
					origin: "http://localhost",
					"x-csrf-token": "SYNTHETIC",
					cookie: "unrelated=secret; CINENEST_SESSION=SYNTHETIC",
				},
				body: '{"holdId":"SYNTHETIC"}',
			}),
		);
		assert.equal(result.status, 409);
		assert.equal(result.headers.get("cache-control"), "no-store");
		assert.equal(observed.headers.cookie, "CINENEST_SESSION=SYNTHETIC");
		assert.equal(observed.headers["x-csrf-token"], "SYNTHETIC");
		assert.equal(observed.body, '{"holdId":"SYNTHETIC"}');
		assert.doesNotMatch(await result.text(), /private upstream diagnostic/);
		const previewQuery = commercialPreviewQuery(selection, pin);
		const previewResult = await forwardQuotes(
			new Request(`http://localhost/api/quotes/preview?${previewQuery}`, {
				headers: { cookie: "unrelated=secret; CINENEST_SESSION=SYNTHETIC" },
			}),
		);
		assert.equal(previewResult.status, 409);
		assert.equal(previewResult.headers.get("cache-control"), "no-store");
		assert.equal(observed.url, `/api/quotes/preview?${previewQuery}`);
		assert.equal(observed.body, "");
		assert.equal(observed.headers.cookie, "CINENEST_SESSION=SYNTHETIC");
		assert.equal(observed.headers["x-csrf-token"], undefined);
		assert.doesNotMatch(
			await previewResult.text(),
			/private upstream diagnostic/,
		);
	} finally {
		if (before === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = before;
		await new Promise((r) => server.close(r));
	}
});

// CN-UI-FLOW-001: server-owned eligibility, never a frontend clock policy.
const {
	validateHandoffQuote,
	verificationHref,
	loadHandoffQuote,
	handoffReasons,
} = await import("../lib/quote-handoff.ts");
const eligible = {
	...q,
	state: "ACCEPTED",
	verificationHandoff: { eligible: true, reason: "ELIGIBLE" },
};
test("accepted BANK handoff requires the current authoritative response", () => {
	assert.equal(validateHandoffQuote(eligible, id), eligible);
	for (const bad of [
		q,
		{ ...eligible, quoteId: "another-owner-reference" },
		{ ...eligible, state: "ISSUED" },
		{ ...eligible, paymentMethod: "GATEWAY" },
		{
			...eligible,
			verificationHandoff: { eligible: true, reason: "QUOTE_EXPIRED" },
		},
		{
			...eligible,
			verificationHandoff: { eligible: false, reason: "invented-policy" },
		},
	])
		assert.throws(() => validateHandoffQuote(bad, id));
});
test("every stable server refusal remains ineligible with a recovery explanation", () => {
	for (const reason of Object.keys(handoffReasons).filter(
		(reason) => reason !== "ELIGIBLE",
	)) {
		const value = {
			...eligible,
			verificationHandoff: { eligible: false, reason },
		};
		assert.equal(
			validateHandoffQuote(value, id).verificationHandoff.eligible,
			false,
		);
		assert.ok(handoffReasons[reason].length > 20);
	}
});
test("frontend does not reinterpret dates or ordinary catalog withdrawal as eligibility", () => {
	// Deliberately historical fixture timestamps: only the server decision controls handoff.
	assert.equal(
		validateHandoffQuote(eligible, id).verificationHandoff.eligible,
		true,
	);
	assert.throws(() =>
		validateHandoffQuote({ ...eligible, state: "WITHDRAWN" }, id),
	);
});
test("navigation contains only one opaque quote reference and no secret fields", () => {
	const url = new URL(verificationHref(id), "https://synthetic.invalid");
	assert.equal(url.pathname, "/verification");
	assert.deepEqual([...url.searchParams], [["quote", id]]);
	assert.equal(url.hash, "");
	assert.doesNotMatch(url.href, /contentHash|consent|token|evidence|grant/);
	for (const bad of [
		"",
		"../private",
		`${id}&token=secret`,
		"https://evil.invalid",
	])
		assert.throws(() => verificationHref(bad));
});
test("context reload uses the existing same-origin authenticated no-store GET, never writes", async () => {
	const before = globalThis.fetch;
	const requests = [];
	globalThis.fetch = async (path, init) => {
		requests.push({ path, init });
		return Response.json(eligible);
	};
	try {
		assert.equal((await loadHandoffQuote(id)).quoteId, id);
		assert.equal(requests.length, 1);
		assert.equal(requests[0].path, `/api/quotes/${id}`);
		assert.equal(requests[0].init.cache, "no-store");
		assert.equal(requests[0].init.method, undefined);
		await assert.rejects(() => loadHandoffQuote(""));
		await assert.rejects(() => loadHandoffQuote("malformed"));
		assert.equal(requests.length, 1);
	} finally {
		globalThis.fetch = before;
	}
});
test("session, denied, wrong-owner/missing and backend failures return no quote context", async () => {
	const before = globalThis.fetch;
	try {
		for (const status of [401, 403, 404, 409, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "Synthetic unavailable" }, { status });
			await assert.rejects(
				() => loadHandoffQuote(id),
				(error) => error.status === status,
			);
		}
		globalThis.fetch = async () => {
			throw new TypeError("Synthetic network failure");
		};
		await assert.rejects(() => loadHandoffQuote(id));
	} finally {
		globalThis.fetch = before;
	}
});

const preview = {
	classification: "SYNTHETIC_PREVIEW_NOT_OFFER",
	synthetic: true,
	commercialIssuanceEnabled: false,
	checkoutEnabled: false,
	customerAccessEnabled: false,
	commerciallyEligible: false,
	currency: "LKR",
	holdId: id,
	holdRevision: 0,
	venueId: id,
	roomId: id,
	packageId: id,
	packageVersionId: id,
	paymentMethod: "BANK",
	optionalServiceIds: [],
	durationMinutes: 180,
	guests: 4,
	sessionStart: "2030-02-02T04:30:00Z",
	sessionEnd: "2030-02-02T07:30:00Z",
	timezone: "Asia/Colombo",
	holdExpiresAt: "2026-11-06T12:10:00Z",
	serverNow: "2026-11-06T12:00:00Z",
	knownComponentsMinor: "250000",
	lines: [
		{
			lineId: "BASE",
			kind: "BASE",
			label: "Synthetic package component",
			quantity: 1,
			unitMinor: "250000",
			amountMinor: "250000",
		},
	],
	policyCandidate: null,
	blockers: ["POLICY_NOT_CONFIGURED", "COMMERCIAL_ISSUANCE_DISABLED"],
};
const pin = {
	policyId: id,
	revision: "3",
	contentHash: "b".repeat(64),
	draftReviewStatus: "APPROVED",
	scopeKind: "VENUE",
	packageId: null,
	effectiveFromProposal: null,
	effectiveUntilProposal: null,
};
const selection = {
	holdId: id,
	expectedHoldRevision: 0,
	packageId: id,
	packageVersionId: id,
	paymentMethod: "BANK",
	optionalServiceIds: [],
};
test("read-only preview selection binds hold, package, add-ons and optional exact policy revision/hash", () => {
	const query = commercialPreviewQuery(selection, pin);
	assert.equal(query.get("expectedHoldRevision"), "0");
	assert.equal(query.get("optionalServiceIds"), "");
	assert.equal(query.get("policyRevision"), "3");
	assert.equal(query.get("policyContentHash"), pin.contentHash);
	for (const bad of [
		{ ...selection, expectedHoldRevision: -1 },
		{ ...selection, paymentMethod: "CASH" },
		{ ...selection, optionalServiceIds: [id, id] },
		{ ...selection, packageVersionId: "unknown" },
	])
		assert.throws(() => commercialPreviewQuery(bad));
	assert.throws(() =>
		commercialPreviewQuery(selection, { ...pin, contentHash: "bad" }),
	);
});
test("non-offer preview validates exact components and unresolved policy without issuing quotation", () => {
	assert.equal(validateCommercialPreview(preview), preview);
	assert.equal(
		validateCommercialPreview({ ...preview, policyCandidate: pin })
			.commerciallyEligible,
		false,
	);
	for (const bad of [
		{ ...preview, quoteId: id },
		{ ...preview, totalMinor: "250000" },
		{ ...preview, commerciallyEligible: true },
		{ ...preview, commercialIssuanceEnabled: true },
		{ ...preview, checkoutEnabled: true },
		{ ...preview, customerAccessEnabled: true },
		{ ...preview, blockers: [] },
		{ ...preview, knownComponentsMinor: "250001" },
		{ ...preview, lines: [{ ...preview.lines[0], quantity: 2 }] },
		{ ...preview, knownComponentsMinor: "9223372036854775808" },
		{ ...preview, policyCandidate: { ...pin, grantId: id } },
		{
			...preview,
			policyCandidate: {
				...pin,
				scopeKind: "PACKAGE",
				packageId: "00000000-0000-4000-8000-000000000002",
			},
		},
		{ ...preview, durationMinutes: 181 },
		{ ...preview, timezone: "invalid" },
	])
		assert.throws(() => validateCommercialPreview(bad));
});
test("preview review separates known components from unknown fees and activation authority", () => {
	const html = render(
		h(CommercialPreviewSummary, {
			preview: { ...preview, policyCandidate: pin },
		}),
	);
	for (const text of [
		"SYNTHETIC_PREVIEW_NOT_OFFER",
		"not payable",
		"unresolved, not zero",
		"This draft is not active",
		"POLICY NOT CONFIGURED",
		"Revision 3",
		pin.contentHash,
	])
		assert.ok(html.includes(text));
	assert.doesNotMatch(html, /<button|Accept synthetic|Pay now|Confirm booking/);
});
test("preview proxy accepts only exact read-only selection keys and never supplied authority", async () => {
	const valid = `http://localhost/api/quotes/preview?${commercialPreviewQuery(selection)}`;
	assert.equal(quoteRoute("/api/quotes/preview", "GET"), true);
	assert.equal(quoteRoute("/api/quotes/preview", "POST"), false);
	for (const url of [
		`${valid}&unknown=1`,
		`${valid}&holdId=${id}`,
		valid.replace("expectedHoldRevision=0&", ""),
		`${valid}&policyId=${id}`,
	])
		assert.equal((await forwardQuotes(new Request(url))).status, 400);
	assert.equal(
		(
			await forwardQuotes(
				new Request(valid, { headers: { "x-role": "ADMIN" } }),
			)
		).status,
		400,
	);
	assert.equal(
		(await forwardQuotes(new Request(valid, { method: "POST" }))).status,
		404,
	);
});

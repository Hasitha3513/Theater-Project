import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { QuoteSummary, SyntheticQuotation } from "../components/quotes.tsx";
import { quoteDisclosures, validateQuote } from "../lib/quotes.ts";
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
	} finally {
		if (before === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = before;
		await new Promise((r) => server.close(r));
	}
});

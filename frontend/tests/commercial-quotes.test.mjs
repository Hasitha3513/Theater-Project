import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { POST } from "../app/api/commercial-quotes/route.ts";
import { CommercialIssuanceNotice } from "../components/quotes.tsx";

test("commercial preview explains the disabled issuance boundary", () => {
	const html = renderToStaticMarkup(
		React.createElement(CommercialIssuanceNotice),
	);
	assert.match(html, /Commercial quotation issuance is unavailable/);
	assert.doesNotMatch(html, /<button|<form/);
});

test("commercial proxy is exact and rejects missing CSRF or wrong origin", async (t) => {
	const previousOrigin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:49151";
	t.after(() => {
		if (previousOrigin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previousOrigin;
	});
	const origin = "http://localhost:48151";
	const request = (path, headers = {}, method = "POST") =>
		new Request(origin + path, { method, headers });
	const upstream = t.mock.method(globalThis, "fetch", async (url, options) => {
		assert.equal(url.origin, "http://127.0.0.1:49151");
		assert.equal(url.pathname, "/api/commercial-quotes");
		assert.equal(url.search, "");
		assert.equal(options.headers.get("cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(options.headers.get("x-csrf-token"), "test-token");
		return Response.json({ message: "Unavailable" }, { status: 503 });
	});
	assert.equal(
		(await POST(request("/api/commercial-quotes", {}, "GET"))).status,
		404,
	);
	assert.equal(
		(await POST(request("/api/commercial-quotes/other"))).status,
		404,
	);
	assert.equal(
		(await POST(request("/api/commercial-quotes", { origin }))).status,
		403,
	);
	assert.equal(
		(
			await POST(
				request("/api/commercial-quotes", { origin: "https://example.test" }),
			)
		).status,
		403,
	);
	assert.equal(upstream.mock.callCount(), 0);
	const response = await POST(
		request("/api/commercial-quotes", {
			origin,
			cookie: "other=ignored; CINENEST_SESSION=synthetic",
			"x-csrf-token": "test-token",
		}),
	);
	assert.equal(response.status, 503);
	assert.equal(response.headers.get("cache-control"), "no-store");
	assert.equal(upstream.mock.callCount(), 1);
});

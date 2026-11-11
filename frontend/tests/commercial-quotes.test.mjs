import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { operationalHoldPackage } from "../components/inventory.tsx";
import { POST } from "../app/api/commercial-quotes/route.ts";
import {
	CommercialIssuanceNotice,
	SyntheticQuotation,
} from "../components/quotes.tsx";
import {
	operationalQuoteBody,
	operationalQuoteHold,
	operationalQuoteReceipt,
} from "../lib/commercial-quotes.ts";

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
	assert.equal(
		(
			await POST(
				request("/api/commercial-quotes?venueId=untrusted", { origin }),
			)
		).status,
		400,
	);
	assert.equal(
		(
			await POST(
				request("/api/commercial-quotes", { origin, "x-account-id": "other" }),
			)
		).status,
		400,
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

test("operational quote requires the server-owned hold and current capability projection", () => {
	const reference = "10000000-0000-4000-8000-000000000001";
	const otherVenue = "10000000-0000-4000-8000-000000000002";
	const hold = {
		mode: "OPERATIONAL",
		holdId: reference,
		venueId: reference,
		roomId: reference,
		packageId: reference,
		packageVersionId: reference,
		revision: 2,
		state: "ACTIVE",
		commercialQuotationPermitted: true,
	};
	const projected = operationalQuoteHold(hold, reference);
	assert.ok(projected);
	assert.deepEqual(
		JSON.parse(operationalQuoteBody(projected, reference, "BANK")),
		{
			holdId: reference,
			holdRevision: 2,
			venueId: reference,
			roomId: reference,
			packageId: reference,
			packageVersionId: reference,
			commandKey: reference,
			method: "BANK",
			channel: "ONLINE",
			serviceIds: [],
		},
	);
	assert.equal(operationalQuoteHold(hold, otherVenue), undefined);
	assert.equal(
		operationalQuoteHold({ ...hold, mode: "SYNTHETIC" }, reference),
		undefined,
	);
	assert.throws(() =>
		operationalQuoteBody({ ...projected, state: "EXPIRED" }, reference, "BANK"),
	);
	assert.throws(() =>
		operationalQuoteBody(
			{ ...projected, commercialQuotationPermitted: false },
			reference,
			"BANK",
		),
	);
	assert.deepEqual(
		operationalQuoteReceipt({ quoteId: reference, untrusted: "discarded" }),
		{ quoteId: reference },
	);
	assert.throws(() => operationalQuoteReceipt({ quoteId: "invalid" }));
	const render = (h, venueId = reference) =>
		renderToStaticMarkup(
			React.createElement(SyntheticQuotation, { hold: h, venueId }),
		);
	assert.match(render(hold), /Request commercial quotation/);
	assert.doesNotMatch(render(hold, otherVenue), /Request commercial quotation/);
	assert.doesNotMatch(
		render({ ...hold, commercialQuotationPermitted: "true" }),
		/Request commercial quotation/,
	);
	assert.doesNotMatch(
		render({ ...hold, state: "EXPIRED" }),
		/Request commercial quotation/,
	);
});

test("operational holds send only the exact published package and version", () => {
	const packageId = "10000000-0000-4000-8000-000000000001";
	const versionId = "10000000-0000-4000-8000-000000000002";
	const listed = [{ packageId, versionId }];
	const operational = { mode: "OPERATIONAL" };
	assert.deepEqual(operationalHoldPackage(operational, listed, versionId), {
		packageId,
		packageVersionId: versionId,
	});
	assert.deepEqual(operationalHoldPackage({}, listed, versionId), {});
	assert.throws(
		() => operationalHoldPackage(operational, listed, packageId),
		/published package/,
	);
	assert.throws(
		() =>
			operationalHoldPackage(operational, [...listed, ...listed], versionId),
		/published package/,
	);
	assert.throws(
		() =>
			operationalHoldPackage(
				operational,
				[{ packageId: "invalid", versionId }],
				versionId,
			),
		/published package/,
	);
});

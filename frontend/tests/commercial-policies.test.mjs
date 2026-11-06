import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { CommercialPolicies } from "../components/commercial-policies.tsx";
import {
	emptyDocument,
	policyMessage,
	policyRoute,
	policyVenues,
	RoomRequestError,
	validateDocument,
} from "../lib/commercial-policies.ts";
import { forwardCommercialPolicies } from "../lib/room-backend.ts";

const venue = "10000000-0000-4000-8000-000000000001",
	policy = "10000000-0000-4000-8000-000000000002";
const path = `/api/admin/venues/${venue}/commercial-policies`;
test("draft defaults preserve unresolved business decisions", () => {
	const d = emptyDocument();
	assert.equal(d.taxApplicability, "UNRESOLVED");
	assert.equal(d.submittedForReview, false);
	assert.equal(d.nonRefundableComponents, null);
	assert.deepEqual(d.fees, []);
	assert.notEqual(emptyDocument(), d);
});
test("missing title and unsafe exact money have field errors", () => {
	assert.ok(validateDocument(emptyDocument()).title);
	for (const v of ["-1", "01", "1.5", "9223372036854775808", "1e4"]) {
		const d = { ...emptyDocument(), title: "Synthetic", taxAmountMinor: v };
		assert.ok(validateDocument(d).taxAmountMinor);
	}
	assert.deepEqual(
		validateDocument({
			...emptyDocument(),
			title: "Synthetic",
			taxAmountMinor: "9223372036854775807",
		}),
		{},
	);
});
test("basis points and cutoff ranges are bounded", () => {
	const d = {
		...emptyDocument(),
		title: "Synthetic",
		taxRateBasisPoints: "10001",
		cancellationCutoffMinutes: "525601",
	};
	const e = validateDocument(d);
	assert.ok(e.taxRateBasisPoints);
	assert.ok(e.cancellationCutoffMinutes);
});
test("UTC interval must be ordered and valid", () => {
	assert.ok(
		validateDocument({ ...emptyDocument(), effectiveFromProposal: "invalid" })
			.effectiveFromProposal,
	);
	assert.ok(
		validateDocument({
			...emptyDocument(),
			effectiveFromProposal: "2030-01-02T00:00Z",
			effectiveUntilProposal: "2030-01-01T00:00Z",
		}).effectiveUntilProposal,
	);
});
test("exact six API operations reject activation and traversal", () => {
	for (const [suffix, method] of [
		["", "GET"],
		["", "POST"],
		[`/${policy}`, "GET"],
		[`/${policy}`, "PUT"],
		[`/${policy}/history`, "GET"],
		[`/${policy}/reviews`, "POST"],
	])
		assert.equal(policyRoute(path + suffix, method), true);
	for (const [suffix, method] of [
		[`/${policy}/activate`, "POST"],
		[`/${policy}/publish`, "POST"],
		[`/${policy}/reviews`, "GET"],
		["", "DELETE"],
		["/../payments", "GET"],
	])
		assert.equal(policyRoute(path + suffix, method), false);
});
test("status messaging distinguishes authentication,authority and conflicts", () => {
	assert.match(
		policyMessage(new RoomRequestError(401, "x")),
		/session expired/,
	);
	assert.match(policyMessage(new RoomRequestError(403, "x")), /permission/);
	assert.match(policyMessage(new RoomRequestError(409, "x")), /Reload/);
	assert.match(policyMessage(new RoomRequestError(503, "x")), /uncertain/);
});
test("Admin SSR clearly denies commercial activation", () => {
	const html = renderToStaticMarkup(React.createElement(CommercialPolicies));
	assert.match(html, /Commercial policy drafts/);
	assert.match(html, /No commercial issuance, checkout or customer access/);
	assert.match(html, /Exact authorized venue reference/);
	assert.doesNotMatch(html, />Activate</);
	assert.doesNotMatch(html, />Publish</);
});
test("proxy rejects authority spoof,unknown queries and missing origin/CSRF", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error("Unexpected upstream");
	};
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		assert.equal(
			(
				await forwardCommercialPolicies(
					new Request(`http://localhost${path}`, {
						headers: { "X-Permission": "admin" },
					}),
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardCommercialPolicies(
					new Request(`http://localhost${path}?actor=forged`),
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardCommercialPolicies(
					new Request(`http://localhost${path}?page=0&page=1`),
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardCommercialPolicies(
					new Request(`http://localhost${path}`, {
						method: "POST",
						body: "{}",
					}),
				)
			).status,
			403,
		);
		assert.equal(calls, 0);
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});
test("proxy forwards only the existing session and CSRF contract", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let captured;
	global.fetch = async (url, init) => {
		captured = { url: String(url), ...init };
		return Response.json(
			{ policyId: policy, commercialIssuanceEnabled: false },
			{ status: 201 },
		);
	};
	try {
		const response = await forwardCommercialPolicies(
			new Request(`http://localhost${path}`, {
				method: "POST",
				headers: {
					Origin: "http://localhost",
					Cookie: "private=omit; CINENEST_SESSION=synthetic",
					"X-CSRF-TOKEN": "synthetic",
				},
				body: "{}",
			}),
		);
		assert.equal(response.status, 201);
		assert.equal(captured.headers.get("Cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(captured.headers.get("X-CSRF-TOKEN"), "synthetic");
		assert.equal(captured.redirect, "manual");
		assert.equal(captured.cache, "no-store");
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});

test("existing authorized venues API is an array, not a paginated wrapper", () => {
	const result = policyVenues([
		{
			venueId: venue,
			displayName: "Synthetic venue",
			timezone: "Asia/Colombo",
			privateExtra: "omit",
		},
	]);
	assert.deepEqual(result, [
		{
			venueId: venue,
			displayName: "Synthetic venue",
			timezone: "Asia/Colombo",
		},
	]);
	assert.throws(() => policyVenues({ items: result }));
	assert.throws(() => policyVenues([{ venueId: "wrong" }]));
});

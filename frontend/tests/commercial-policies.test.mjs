import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { POST as submitTrustedApproval } from "../app/api/admin/venues/[venueId]/commercial-policies/[policyId]/trusted-approvals/route.ts";
import {
	GET as inspectActivation,
	POST as activatePolicy,
} from "../app/api/admin/venues/[venueId]/commercial-policies/[policyId]/activation/route.ts";
import {
	CommercialPolicies,
	OperationalPolicyActivation,
} from "../components/commercial-policies.tsx";
import {
	activationReceipt,
	approvalMessage,
	activationScope,
	emptyDocument,
	maxApprovalEnvelopeBytes,
	policyMessage,
	policyRoute,
	policyVenues,
	RoomRequestError,
	validActivationSelection,
	validApprovalEnvelope,
	validateDocument,
} from "../lib/commercial-policies.ts";
import { forwardCommercialPolicies } from "../lib/room-backend.ts";

const venue = "10000000-0000-4000-8000-000000000001",
	policy = "10000000-0000-4000-8000-000000000002";
const path = `/api/admin/venues/${venue}/commercial-policies`;
const approvalPath = `${path}/${policy}/trusted-approvals`;
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
test("policy route allows exact operations and rejects unsupported paths", () => {
	for (const [suffix, method] of [
		["", "GET"],
		["", "POST"],
		[`/${policy}`, "GET"],
		[`/${policy}`, "PUT"],
		[`/${policy}/history`, "GET"],
		[`/${policy}/reviews`, "POST"],
		[`/${policy}/activation`, "GET"],
		[`/${policy}/activation`, "POST"],
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
test("signed approval allows only the exact intake POST and bounds opaque input", () => {
	assert.equal(policyRoute(approvalPath, "POST"), true);
	assert.equal(policyRoute(`${path}/trusted-approvals`, "POST"), true);
	for (const method of ["GET", "PUT", "DELETE"])
		assert.equal(policyRoute(approvalPath, method), false);
	assert.equal(policyRoute(`${approvalPath}/revoke`, "POST"), false);
	assert.equal(validApprovalEnvelope(""), false);
	assert.equal(validApprovalEnvelope({ signature: "synthetic" }), false);
	assert.equal(validApprovalEnvelope("a".repeat(maxApprovalEnvelopeBytes)), true);
	assert.equal(validApprovalEnvelope("é".repeat(maxApprovalEnvelopeBytes)), false);
	assert.match(
		approvalMessage(new RoomRequestError(404, "private")),
		/disabled or unavailable/,
	);
	assert.match(
		approvalMessage(new RoomRequestError(503, "private")),
		/Verify the outcome/,
	);
});

test("signed approval proxy binds the exact route policy and preserves existing Origin, session and CSRF guards", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	const captured = [];
	global.fetch = async (url, init) => {
		captured.push({ url: String(url), ...init });
		return Response.json({ commercialIssuanceEnabled: false }, { status: 201 });
	};
	const headers = {
		"Content-Type": "application/json",
		Origin: "http://localhost",
		Cookie: "other=omit; CINENEST_SESSION=synthetic",
		"X-CSRF-TOKEN": "synthetic",
	};
	const envelope = 'synthetic signed bytes + / = " escaped';
	const body = JSON.stringify({ envelope });
	function request(payload = body, overrides = {}, route = approvalPath) {
		return new Request(`http://localhost${route}`, {
			method: "POST",
			headers: { ...headers, ...overrides },
			body: payload,
		});
	}
	try {
		assert.equal(
			(await submitTrustedApproval(request(body, { Origin: "http://evil.test" })))
				.status,
			403,
		);
		assert.equal(
			(await submitTrustedApproval(request(body, { "X-CSRF-TOKEN": "" })))
				.status,
			403,
		);
		assert.equal(
			(await submitTrustedApproval(request(body, { "X-Permission": "admin" })))
				.status,
			400,
		);
		for (const invalid of [
			JSON.stringify({ policyId: venue, envelope }),
			JSON.stringify({ envelope, review: "APPROVE" }),
			'{"envelope":"one","envelope":"two"}',
			'{"envelope":[]}',
			JSON.stringify({ envelope: "a".repeat(maxApprovalEnvelopeBytes + 1) }),
		])
			assert.equal((await submitTrustedApproval(request(invalid))).status, 400);
		assert.equal(
			(await submitTrustedApproval(request(" ".repeat(32769)))).status,
			413,
		);
		assert.equal(
			(await submitTrustedApproval(request(body, {}, `${approvalPath}?q=forged`)))
				.status,
			400,
		);
		assert.equal(captured.length, 0);

		assert.equal((await submitTrustedApproval(request())).status, 201);
		assert.equal(captured.length, 1);
		assert.equal(new URL(captured[0].url).pathname, `${path}/trusted-approvals`);
		assert.deepEqual(JSON.parse(captured[0].body), { policyId: policy, envelope });
		assert.equal(captured[0].headers.get("Cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(captured[0].headers.get("X-CSRF-TOKEN"), "synthetic");
		assert.equal(captured[0].redirect, "manual");
		assert.equal(captured[0].cache, "no-store");
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
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

test("activation controls require a current exact-policy permission projection", () => {
	const item = {
		permitted: true,
		policyId: policy,
		venueId: venue,
		policyRevision: 2,
		policyHash: "a".repeat(64),
		effectiveFrom: "2030-01-01T00:00:00Z",
		effectiveUntil: null,
	};
	const scope = activationScope(item, venue, policy);
	assert.ok(scope);
	const render = (policy, selectedVenue) =>
		renderToStaticMarkup(
			React.createElement(OperationalPolicyActivation, {
				policy,
				venue: selectedVenue,
				disabled: false,
			}),
		);
	assert.match(render(scope, venue), /Activate this policy revision/);
	assert.equal(render(scope, policy), "");
	for (const override of [
		{ permitted: false },
		{ permitted: "true" },
		{ venueId: policy },
		{ policyId: venue },
		{ policyRevision: "2" },
		{ policyHash: "invalid" },
		{ effectiveUntil: "invalid" },
	])
		assert.equal(
			render(activationScope({ ...item, ...override }, venue, policy), venue),
			"",
		);
	assert.equal(
		validActivationSelection(
			venue,
			policy,
			"2030-01-01T00:00:00Z",
			"2030-01-02T00:00:00Z",
		),
		true,
	);
	assert.equal(
		validActivationSelection(
			venue,
			policy,
			"2030-01-01T00:00:00",
			"2030-01-02T00:00:00Z",
		),
		false,
	);
	assert.equal(
		validActivationSelection(
			venue,
			policy,
			"2030-01-03T00:00:00Z",
			"2030-01-02T00:00:00Z",
		),
		false,
	);
	assert.deepEqual(activationReceipt({ activationId: policy }), {
		activationId: policy,
	});
	assert.throws(() => activationReceipt({ activationId: "invalid" }));
});

test("activation route rejects altered paths, spoofed authority, wrong origin and missing CSRF", async (t) => {
	const old = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	t.after(() => {
		if (old === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = old;
	});
	const url = `${path}/${policy}/activation`;
	const body = JSON.stringify({
		policyRevision: 2,
		policyHash: "a".repeat(64),
	});
	const origin = "http://localhost";
	const headers = {
		Origin: origin,
		"X-CSRF-TOKEN": "synthetic",
		Cookie: "other=omit; CINENEST_SESSION=synthetic",
	};
	const request = (route = url, values = headers) =>
		new Request(origin + route, {
			method: "POST",
			headers: values,
			body,
		});
	const upstream = t.mock.method(globalThis, "fetch", async (target, init) => {
		assert.equal(target.pathname, url);
		assert.equal(target.search, "");
		assert.equal(init.headers.get("cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(init.headers.get("x-csrf-token"), "synthetic");
		assert.equal(init.body, body);
		return Response.json({ activationId: policy }, { status: 201 });
	});
	assert.equal((await activatePolicy(request(`${url}/other`))).status, 404);
	assert.equal((await activatePolicy(request(`${url}?page=0`))).status, 400);
	assert.equal(
		(await activatePolicy(request(url, { ...headers, "X-Role": "admin" })))
			.status,
		400,
	);
	assert.equal(
		(
			await activatePolicy(
				request(url, { ...headers, Origin: "https://other.test" }),
			)
		).status,
		403,
	);
	assert.equal(
		(await activatePolicy(request(url, { Origin: origin }))).status,
		403,
	);
	assert.equal(upstream.mock.callCount(), 0);
	const accepted = await activatePolicy(request());
	assert.equal(accepted.status, 201);
	assert.equal(accepted.headers.get("cache-control"), "no-store");
	assert.deepEqual(await accepted.json(), { activationId: policy });
	assert.equal(upstream.mock.callCount(), 1);
});

test("activation permission read forwards only the configured session and stays uncached", async (t) => {
	const old = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	t.after(() => {
		if (old === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = old;
	});
	const pathname = `${path}/${policy}/activation`;
	const upstream = t.mock.method(globalThis, "fetch", async (target, init) => {
		assert.equal(target.pathname, pathname);
		assert.equal(init.method, "GET");
		assert.equal(init.headers.get("cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(init.body, undefined);
		return Response.json({ permitted: false });
	});
	const response = await inspectActivation(
		new Request(`http://localhost${pathname}`, {
			headers: { Cookie: "other=omit; CINENEST_SESSION=synthetic" },
		}),
	);
	assert.equal(response.headers.get("cache-control"), "no-store");
	assert.deepEqual(await response.json(), { permitted: false });
	assert.equal(upstream.mock.callCount(), 1);
});

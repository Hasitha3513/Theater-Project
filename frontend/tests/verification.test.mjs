import assert from "node:assert/strict";
import { test } from "node:test";
import { createServer } from "node:http";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	verificationRoute,
	forwardVerification,
} from "../lib/verification-backend.ts";
import {
	verificationLabels,
	validateVerification,
} from "../lib/verification.ts";
import {
	VerificationWorkspace,
	VerificationSummary,
} from "../components/verification.tsx";
const id = "00000000-0000-4000-8000-000000000001";
const value = {
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	generation: 1,
	superseded: false,
	previousId: null,
	state: "NOT_SUBMITTED",
	validUntil: null,
	synthetic: true,
	physicalAdmission: false,
	labels: verificationLabels,
	evidence: [],
	history: [],
};
test("exact ten method/path operations, no generic evidence proxy", () => {
	const paths = [
		["POST", "/api/verification/requests"],
		["POST", `/api/verification/requests/${id}/uploads`],
		["PUT", `/api/verification/uploads/${id}/content`],
		["POST", `/api/verification/uploads/${id}/complete`],
		["GET", `/api/verification/requests/${id}`],
		["POST", `/api/verification/requests/${id}/resubmissions`],
		["GET", `/api/admin/venues/${id}/verification/requests`],
		["GET", `/api/admin/venues/${id}/verification/requests/${id}`],
		[
			"GET",
			`/api/admin/venues/${id}/verification/requests/${id}/evidence/${id}`,
		],
		["POST", `/api/admin/venues/${id}/verification/requests/${id}/decisions`],
	];
	for (const [method, path] of paths) {
		assert.equal(verificationRoute(path, method), true);
		assert.equal(verificationRoute(path, "DELETE"), false);
	}
	assert.equal(
		verificationRoute("/api/verification/../../secrets", "GET"),
		false,
	);
});
test("customer and reviewer prominently identify synthetic restrictions and initial disabled actions", () => {
	for (const reviewer of [false, true]) {
		const html = render(h(VerificationWorkspace, { reviewer }));
		for (const label of verificationLabels) assert.ok(html.includes(label));
		assert.match(html, /disabled/);
		assert.doesNotMatch(
			html,
			/Pay now|Confirm booking|Approve physical admission/,
		);
	}
});
test("server status validation rejects production admission and malformed state", () => {
	assert.equal(validateVerification(value), value);
	for (const change of [
		{ synthetic: false },
		{ physicalAdmission: true },
		{ revision: -1 },
		{ state: "APPROVED" },
		{ labels: [] },
		{ evidence: [{}, {}, {}] },
	])
		assert.throws(() => validateVerification({ ...value, ...change }));
});
test("status and immutable rejection reasons are shown without raw document metadata", () => {
	const html = render(
		h(VerificationSummary, {
			value: {
				...value,
				state: "REJECTED",
				history: [
					{
						id,
						verdict: "REJECTED",
						reason: "CORRECTION_REQUIRED",
						at: "2026-10-17",
						validUntil: "2026-10-18",
					},
				],
			},
		}),
	);
	assert.match(html, /CORRECTION_REQUIRED/);
	assert.match(html, /Generation 1/);
	assert.doesNotMatch(html, /object_key|owner_id|Date of birth/);
});
test("missing configuration and cross-origin writes fail closed", async () => {
	const original = process.env.CINENEST_AUTH_ORIGIN;
	delete process.env.CINENEST_AUTH_ORIGIN;
	try {
		assert.equal(
			(
				await forwardVerification(
					new Request(`http://localhost/api/verification/requests/${id}`),
				)
			).status,
			503,
		);
		process.env.CINENEST_AUTH_ORIGIN = "https://example.test";
		assert.equal(
			(
				await forwardVerification(
					new Request("http://localhost/api/verification/requests", {
						method: "POST",
						headers: { origin: "https://evil.test", "x-csrf-token": "x" },
					}),
				)
			).status,
			403,
		);
	} finally {
		if (original) process.env.CINENEST_AUTH_ORIGIN = original;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("bounded binary forwarding strips unrelated credentials, redacts errors and protects previews", async () => {
	let seen;
	const server = createServer((request, response) => {
		const chunks = [];
		request.on("data", (b) => chunks.push(b));
		request.on("end", () => {
			seen = {
				url: request.url,
				headers: request.headers,
				bytes: Buffer.concat(chunks),
			};
			if (request.url.includes("evidence")) {
				response.setHeader("Content-Type", "image/png");
				response.end(Buffer.from([137, 80, 78, 71]));
			} else if (request.method === "PUT") {
				response.end(JSON.stringify({ state: "QUARANTINED" }));
			} else {
				response.statusCode = 403;
				response.end("secret object key");
			}
		});
	});
	await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
	const original = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	try {
		const request = new Request(
			`http://localhost/api/verification/uploads/${id}/content`,
			{
				method: "PUT",
				headers: {
					origin: "http://localhost",
					"x-csrf-token": "test",
					"content-type": "image/png",
					cookie: "CINENEST_SESSION=test; unrelated=secret",
					authorization: "Bearer secret",
				},
				body: new Uint8Array([1, 2, 3]),
			},
		);
		const r = await forwardVerification(request);
		assert.equal(r.status, 200);
		assert.equal(seen.headers.cookie, "CINENEST_SESSION=test");
		assert.equal(seen.headers.authorization, undefined);
		assert.deepEqual([...seen.bytes], [1, 2, 3]);
		const preview = await forwardVerification(
			new Request(
				`http://localhost/api/admin/venues/${id}/verification/requests/${id}/evidence/${id}`,
			),
		);
		assert.equal(preview.headers.get("cache-control"), "no-store");
		assert.equal(preview.headers.get("content-type"), "image/png");
		const denied = await forwardVerification(
			new Request(`http://localhost/api/verification/requests/${id}`),
		);
		assert.equal(denied.status, 403);
		assert.ok(!(await denied.text()).includes("secret"));
		const large = await forwardVerification(
			new Request(`http://localhost/api/verification/uploads/${id}/content`, {
				method: "PUT",
				headers: {
					origin: "http://localhost",
					"x-csrf-token": "test",
					"content-type": "image/png",
				},
				body: new Uint8Array(5 * 1024 * 1024 + 1),
			}),
		);
		assert.equal(large.status, 413);
	} finally {
		if (original) process.env.CINENEST_AUTH_ORIGIN = original;
		else delete process.env.CINENEST_AUTH_ORIGIN;
		await new Promise((resolve) => server.close(resolve));
	}
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { readFileSync } from "node:fs";
import {
	Button,
	Field,
	SelectField,
	StateMessage,
	Badge,
	Card,
	PageHeading,
} from "../components/primitives.tsx";
import { readSession, endSession } from "../lib/session.ts";
import { forwardSession } from "../lib/backend.ts";
import { generated } from "../scripts/generate-breakpoints.mjs";
const uuid = "00000000-0000-4000-8000-000000000001";
test("button uses native disabled and busy semantics with an accessible label", () => {
	const html = render(h(Button, { busy: true }, "Submit"));
	assert.match(html, /disabled=""/);
	assert.match(html, /aria-busy="true"/);
	assert.match(html, /Please wait/);
	assert.match(render(h(Button, null, "Continue")), />Continue<\/span>/);
});
test("field associates label and validation error without relying on placeholder", () => {
	const html = render(
		h(Field, {
			id: "name",
			label: "Example name",
			error: "A label is required",
		}),
	);
	assert.match(html, /for="name"/);
	assert.match(html, /aria-describedby="name-help"/);
	assert.match(html, /aria-invalid="true"/);
	assert.match(html, /id="name-help"/);
});
test("select, card, badge and page title expose semantic text", () => {
	assert.match(
		render(
			h(
				SelectField,
				{ id: "state", label: "State" },
				h("option", null, "Unknown"),
			),
		),
		/for="state"/,
	);
	assert.match(
		render(
			h(
				Card,
				{ title: "Examples" },
				h(Badge, { tone: "warning" }, "Needs review"),
			),
		),
		/<h2>Examples<\/h2>/,
	);
	assert.match(
		render(
			h(
				PageHeading,
				{ eyebrow: "Preview", title: "Workspace" },
				"No business data",
			),
		),
		/<h1>Workspace<\/h1>/,
	);
});
for (const kind of [
	"loading",
	"empty",
	"error",
	"unauthenticated",
	"denied",
	"unconfigured",
])
	test(`state ${kind} has named, nonblank semantics`, () => {
		const html = render(h(StateMessage, { kind }));
		assert.match(html, /<h2>/);
		assert.match(html, /role="(?:status|alert)"/);
		assert.doesNotMatch(html, /stack|Exception/);
	});
for (const [status, kind] of [
	[401, "unauthenticated"],
	[403, "denied"],
	[503, "unconfigured"],
	[500, "error"],
])
	test(`session ${status} clears authority/payload`, async () => {
		const value = await readSession(
			async () =>
				new Response(JSON.stringify({ accountId: uuid, secret: "hidden" }), {
					status,
				}),
		);
		assert.deepEqual(value, { kind });
	});
test("session accepts only minimal eligible account fields", async () => {
	const result = await readSession(async () =>
		Response.json({
			accountId: uuid,
			eligibility: "ELIGIBLE",
			roles: ["admin"],
			secret: "hidden",
		}),
	);
	assert.deepEqual(result, {
		kind: "authenticated",
		account: { accountId: uuid, eligibility: "ELIGIBLE" },
	});
});
test("invalid account shape and suspended state deny presentation", async () => {
	for (const body of [
		{ accountId: "bad", eligibility: "ELIGIBLE" },
		{ accountId: uuid, eligibility: "SUSPENDED" },
		null,
	])
		assert.notEqual(
			(await readSession(async () => Response.json(body))).kind,
			"authenticated",
		);
});
test("network error produces safe state", async () => {
	assert.deepEqual(
		await readSession(async () => {
			throw Error("private backend failure");
		}),
		{ kind: "error" },
	);
});
test("logout uses server CSRF and only a 204 confirms success", async () => {
	const calls = [];
	const ok = await endSession(async (url, options) => {
		calls.push([url, options]);
		return url.endsWith("csrf")
			? Response.json({
					headerName: "X-CSRF-TOKEN",
					token: "synthetic-transient-token",
				})
			: new Response(null, { status: 204 });
	});
	assert.equal(ok, true);
	assert.equal(calls[1][1].method, "POST");
	assert.equal(
		calls[1][1].headers["X-CSRF-TOKEN"],
		"synthetic-transient-token",
	);
	assert.equal(
		await endSession(async () => new Response(null, { status: 403 })),
		false,
	);
	assert.equal(
		await endSession(async () =>
			Response.json({ headerName: "Other", token: "bad" }),
		),
		false,
	);
});
test("session adapter fails closed without configuration", async () => {
	const old = process.env.CINENEST_AUTH_ORIGIN;
	delete process.env.CINENEST_AUTH_ORIGIN;
	try {
		assert.equal(
			(
				await forwardSession(
					new Request("http://localhost/api/accounts/me"),
					"/api/accounts/me",
				)
			).status,
			503,
		);
	} finally {
		if (old) process.env.CINENEST_AUTH_ORIGIN = old;
	}
});
test("transport strips arbitrary fields, errors, redirects and client authority", async () => {
	const oldOrigin = process.env.CINENEST_AUTH_ORIGIN,
		oldFetch = globalThis.fetch;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		globalThis.fetch = async (url, options) => {
			assert.equal(options.headers.get("authorization"), null);
			assert.equal(options.headers.get("x-role"), null);
			assert.equal(new URL(url).search, "");
			return Response.json({
				accountId: uuid,
				eligibility: "ELIGIBLE",
				secret: "hidden",
			});
		};
		const response = await forwardSession(
			new Request("http://localhost/api/accounts/me?owner=other", {
				headers: { Authorization: "Bearer ignored", "X-Role": "admin" },
			}),
			"/api/accounts/me",
		);
		assert.deepEqual(await response.json(), {
			accountId: uuid,
			eligibility: "ELIGIBLE",
		});
		for (const status of [302, 401, 403, 500]) {
			globalThis.fetch = async () => new Response("private stack", { status });
			const result = await forwardSession(
				new Request("http://localhost/api/accounts/me"),
				"/api/accounts/me",
			);
			assert.equal(await result.text(), "");
			assert.equal(
				result.status,
				status === 401 || status === 403 ? status : 502,
			);
		}
		assert.equal(
			(
				await forwardSession(
					new Request("http://localhost/api/accounts/me"),
					"/api/bookings",
				)
			).status,
			404,
		);
	} finally {
		globalThis.fetch = oldFetch;
		if (oldOrigin) process.env.CINENEST_AUTH_ORIGIN = oldOrigin;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("logout adapter refuses cross-origin or missing CSRF calls", async () => {
	const old = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		for (const headers of [
			{ Origin: "https://other.invalid", "X-CSRF-TOKEN": "test" },
			{ Origin: "http://localhost" },
		])
			assert.equal(
				(
					await forwardSession(
						new Request("http://localhost/api/session/logout", {
							method: "POST",
							headers,
						}),
						"/api/session/logout",
					)
				).status,
				403,
			);
	} finally {
		if (old) process.env.CINENEST_AUTH_ORIGIN = old;
		else delete process.env.CINENEST_AUTH_ORIGIN;
	}
});
test("responsive stylesheet is generated from existing canonical breakpoints", () => {
	assert.equal(
		readFileSync(new URL("../styles/breakpoints.css", import.meta.url), "utf8"),
		generated,
	);
});

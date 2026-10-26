import { test } from "node:test";
import assert from "node:assert/strict";
import { forwardGoogleSso } from "../lib/google-sso-backend.ts";
const original = global.fetch;
test("Google S1 defaults disabled and rejects methods,duplicate callbacks and unsupported query", async () => {
	delete process.env.CINENEST_GOOGLE_CONTRACT_ISSUER;
	assert.equal(
		(
			await forwardGoogleSso(
				new Request("http://127.0.0.1:3333/api/auth/google/status"),
				"status",
			)
		).status,
		503,
	);
	for (const query of [
		"?code=x&state=y&state=z",
		"?code=x&state=y&returnTo=https://evil.invalid",
		"?state=x",
	])
		assert.equal(
			(
				await forwardGoogleSso(
					new Request("http://127.0.0.1:3333/api/auth/google/callback" + query),
					"callback",
				)
			).status,
			400,
		);
	assert.equal(
		(
			await forwardGoogleSso(
				new Request("http://127.0.0.1:3333/api/auth/google/start?issuer=evil"),
				"start",
			)
		).status,
		400,
	);
});
test("Google S1 allowlists issuer redirect and callback return;never relays error bodies", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	process.env.CINENEST_GOOGLE_CONTRACT_ISSUER = "http://127.0.0.1:9998";
	try {
		global.fetch = async () =>
			new Response(null, {
				status: 302,
				headers: { Location: "https://evil.invalid/authorize" },
			});
		assert.equal(
			(
				await forwardGoogleSso(
					new Request("http://127.0.0.1:3333/api/auth/google/start"),
					"start",
				)
			).status,
			503,
		);
		global.fetch = async () =>
			new Response("private token/secret", { status: 403 });
		const r = await forwardGoogleSso(
			new Request(
				"http://127.0.0.1:3333/api/auth/google/callback?code=private&state=random",
			),
			"callback",
		);
		assert.equal(r.status, 303);
		assert.equal(r.headers.get("Location"), "/login?google=denied");
		assert.equal(await r.text(), "");
		assert.equal(r.headers.get("referrer-policy"), "no-referrer");
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_GOOGLE_CONTRACT_ISSUER;
	}
});

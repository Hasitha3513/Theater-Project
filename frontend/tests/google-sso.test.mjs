import assert from "node:assert/strict";
import { test } from "node:test";
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

test("Google S2-A live execution defaults denied", async () => {
	process.env.CINENEST_GOOGLE_MODE = "LIVE_QUALIFICATION";
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	delete process.env.CINENEST_GOOGLE_LIVE_EXECUTION;
	try {
		global.fetch = async () =>
			Response.json({
				mode: "LIVE_QUALIFICATION",
				liveProviderQualified: false,
				executionEnabled: false,
			});
		const status = await forwardGoogleSso(
			new Request("http://127.0.0.1:42137/api/auth/google/status"),
			"status",
		);
		assert.deepEqual(await status.json(), {
			mode: "LIVE_QUALIFICATION",
			liveProviderQualified: false,
			executionEnabled: false,
		});
		for (const action of ["start", "callback"])
			assert.equal(
				(
					await forwardGoogleSso(
						new Request(
							`http://127.0.0.1:42137/api/auth/google/${action}${action === "callback" ? "?code=x&state=y" : ""}`,
						),
						action,
					)
				).status,
				503,
			);
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_GOOGLE_MODE;
	}
});
test("Google S2-A fixed redirects and callback metadata are strictly bounded", async () => {
	process.env.CINENEST_GOOGLE_MODE = "LIVE_QUALIFICATION";
	process.env.CINENEST_GOOGLE_LIVE_EXECUTION = "true";
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	const callback = "http://127.0.0.1:42137/api/auth/google/callback";
	try {
		const good = new URL("https://accounts.google.com/o/oauth2/v2/auth");
		good.search = new URLSearchParams({
			redirect_uri: callback,
			state: "s",
			nonce: "n",
			code_challenge_method: "S256",
		}).toString();
		for (const location of [
			good
				.toString()
				.replace("accounts.google.com", "accounts.google.com.evil.invalid"),
			good.toString().replace("/o/oauth2/v2/auth", "/authorize"),
			good.toString().replace("S256", "plain"),
		]) {
			global.fetch = async () =>
				new Response(null, { status: 302, headers: { Location: location } });
			assert.equal(
				(
					await forwardGoogleSso(
						new Request("http://127.0.0.1:42137/api/auth/google/start"),
						"start",
					)
				).status,
				503,
			);
		}
		global.fetch = async () =>
			new Response(null, {
				status: 302,
				headers: { Location: good.toString() },
			});
		assert.equal(
			(
				await forwardGoogleSso(
					new Request("http://127.0.0.1:42137/api/auth/google/start"),
					"start",
				)
			).status,
			303,
		);
		let seen;
		global.fetch = async (url) => {
			seen = new URL(url);
			return new Response(null, { status: 204 });
		};
		const result = await forwardGoogleSso(
			new Request(
				callback +
					"?code=private&state=s&scope=openid&authuser=0&prompt=consent&iss=https%3A%2F%2Faccounts.google.com",
			),
			"callback",
		);
		assert.equal(result.headers.get("location"), "/account");
		assert.equal(seen.search, "?code=private&state=s");
		assert.equal(await result.text(), "");
		for (const suffix of [
			"&scope=x&scope=y",
			"&returnTo=evil",
			"&iss=https://evil.invalid",
		])
			assert.equal(
				(
					await forwardGoogleSso(
						new Request(callback + "?code=c&state=s" + suffix),
						"callback",
					)
				).status,
				400,
			);
		global.fetch = async () =>
			Response.json({
				mode: "LIVE_QUALIFICATION",
				liveProviderQualified: true,
				clientSecret: "private",
			});
		assert.equal(
			(
				await forwardGoogleSso(
					new Request("http://127.0.0.1:42137/api/auth/google/status"),
					"status",
				)
			).status,
			503,
		);
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_GOOGLE_MODE;
		delete process.env.CINENEST_GOOGLE_LIVE_EXECUTION;
	}
});

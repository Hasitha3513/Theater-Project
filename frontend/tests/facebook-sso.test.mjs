import assert from "node:assert/strict";
import { test } from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { FacebookSso } from "../components/facebook-sso.tsx";
import { forwardFacebookSso } from "../lib/facebook-sso-backend.ts";
const original = global.fetch;
const base = "http://127.0.0.1:3333";
const request = (query = "", action = "callback", options) =>
	forwardFacebookSso(
		new Request(`${base}/api/auth/facebook/${action}${query}`, options),
		action,
	);
function configure() {
	process.env.CINENEST_FACEBOOK_MODE = "CONTRACT_TEST";
	process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN = "http://127.0.0.1:9998";
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
}
test("Facebook live mode requires both execution gates and a fixed HTTPS origin", async () => {
	process.env.CINENEST_FACEBOOK_MODE = "LIVE_QUALIFICATION";
	delete process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN;
	delete process.env.CINENEST_FACEBOOK_LIVE_EXECUTION;
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error("private provider diagnostic");
	};
	try {
		assert.equal((await request("", "start")).status, 503);
		assert.equal((await request("?code=fixture&state=fixture")).status, 503);
		process.env.CINENEST_FACEBOOK_LIVE_EXECUTION = "true";
		process.env.CINENEST_FACEBOOK_FRONTEND_ORIGIN = "https://localhost:42137";
		assert.equal((await request("", "start")).status, 503);
		assert.equal(calls, 0);
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_FACEBOOK_LIVE_EXECUTION;
		delete process.env.CINENEST_FACEBOOK_FRONTEND_ORIGIN;
	}
});
test("Facebook live configured status never falsely enables execution or qualification", async () => {
	process.env.CINENEST_FACEBOOK_MODE = "LIVE_QUALIFICATION";
	delete process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	global.fetch = async () =>
		Response.json({
			mode: "LIVE_QUALIFICATION",
			syntheticOnly: false,
			liveProviderQualified: false,
			executionEnabled: true,
			callbackVerified: true,
		});
	try {
		const response = await request("", "status");
		assert.equal(response.status, 200);
		assert.equal((await response.json()).executionEnabled, false);
	} finally {
		global.fetch = original;
	}
});
test("Facebook live redirect uses fixed Meta endpoint and exact configured callback", async () => {
	process.env.CINENEST_FACEBOOK_MODE = "LIVE_QUALIFICATION";
	process.env.CINENEST_FACEBOOK_LIVE_EXECUTION = "true";
	process.env.CINENEST_FACEBOOK_FRONTEND_ORIGIN = "https://localhost:42137";
	delete process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN;
	const url = new URL("https://www.facebook.com/v26.0/dialog/oauth");
	for (const [k, v] of Object.entries({
		client_id: "fixture-app",
		redirect_uri: "https://localhost:42137/api/auth/facebook/callback",
		state: "opaque-fixture",
		scope: "public_profile",
		response_type: "code",
	}))
		url.searchParams.set(k, v);
	global.fetch = async () =>
		new Response(null, { status: 302, headers: { Location: url.toString() } });
	try {
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:42137/api/auth/facebook/start"),
					"start",
				)
			).status,
			303,
		);
		url.hostname = "evil.invalid";
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:42137/api/auth/facebook/start"),
					"start",
				)
			).status,
			503,
		);
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_FACEBOOK_LIVE_EXECUTION;
		delete process.env.CINENEST_FACEBOOK_FRONTEND_ORIGIN;
	}
});
test("Facebook defaults unavailable;live mode and mutation methods never reach provider", async () => {
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error();
	};
	delete process.env.CINENEST_FACEBOOK_MODE;
	try {
		assert.equal((await request("", "start")).status, 503);
		process.env.CINENEST_FACEBOOK_MODE = "LIVE_QUALIFICATION";
		assert.equal((await request("", "start")).status, 503);
		assert.equal((await request("", "start", { method: "POST" })).status, 405);
		assert.equal(calls, 0);
		assert.match(
			renderToStaticMarkup(React.createElement(FacebookSso)),
			/Facebook Login unavailable/,
		);
	} finally {
		global.fetch = original;
	}
});
test("Facebook callback and origins are strictly allowlisted", async () => {
	configure();
	try {
		for (const query of [
			"?code=a&state=b&state=c",
			"?code=a&state=b&access_token=x",
			"?code=a&state=b&returnTo=https://evil.invalid",
			"?state=a",
			"?code=a&error=b&state=c",
		]) {
			assert.equal((await request(query)).status, 400);
		}
		assert.equal((await request("?issuer=evil", "start")).status, 400);
		process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN = "https://www.facebook.com";
		assert.equal((await request("", "start")).status, 503);
	} finally {
		global.fetch = original;
	}
});
test("Facebook redirects stay in the fixture and bind exact frontend callback/minimum scope", async () => {
	configure();
	try {
		const url = new URL("http://127.0.0.1:9998/v26.0/dialog/oauth");
		url.searchParams.set("redirect_uri", base + "/api/auth/facebook/callback");
		url.searchParams.set("client_id", "synthetic-fixture");
		url.searchParams.set("scope", "public_profile");
		url.searchParams.set("state", "opaque-fixture-state");
		url.searchParams.set("response_type", "code");
		global.fetch = async () =>
			new Response(null, {
				status: 302,
				headers: { Location: url.toString() },
			});
		assert.equal((await request("", "start")).status, 303);
		for (const [key, value] of [
			["redirect_uri", "https://evil.invalid/callback"],
			[
				"redirect_uri",
				"http://user:password@127.0.0.1:3333/api/auth/facebook/callback",
			],
			["scope", "email"],
			["response_type", "token"],
			["client_secret", "forbidden"],
		]) {
			const bad = new URL(url);
			bad.searchParams.set(key, value);
			global.fetch = async () =>
				new Response(null, {
					status: 302,
					headers: { Location: bad.toString() },
				});
			assert.equal((await request("", "start")).status, 503);
		}
	} finally {
		global.fetch = original;
	}
});
test("Facebook proxy strips presentation metadata,cookies and private errors;status cannot falsely qualify", async () => {
	configure();
	try {
		let target, headers;
		global.fetch = async (u, o) => {
			target = String(u);
			headers = o.headers;
			return new Response("PRIVATE_PROVIDER_PAYLOAD", { status: 403 });
		};
		const r = await forwardFacebookSso(
			new Request(
				base +
					"/api/auth/facebook/callback?state=fixture&error=access_denied&error_reason=user_denied&error_description=private",
				{ headers: { Cookie: "OTHER=private; CINENEST_SESSION=synthetic" } },
			),
			"callback",
		);
		assert.equal(r.headers.get("Location"), "/login?facebook=denied");
		assert.equal(await r.text(), "");
		assert.ok(
			!target.includes("error_reason") && !target.includes("error_description"),
		);
		assert.equal(headers.get("Cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(r.headers.get("Cache-Control"), "no-store");
		global.fetch = async () =>
			Response.json({
				mode: "CONTRACT_TEST",
				liveProviderQualified: true,
				syntheticOnly: true,
			});
		assert.equal((await request("", "status")).status, 503);
		global.fetch = async () =>
			Response.json({
				mode: "CONTRACT_TEST",
				liveProviderQualified: false,
				syntheticOnly: true,
			});
		assert.equal((await request("", "status")).status, 200);
	} finally {
		global.fetch = original;
		delete process.env.CINENEST_FACEBOOK_MODE;
	}
});

test("local HTTPS contract frontend is exact and does not expand provider trust", async () => {
	configure();
	let calls = 0;
	global.fetch = async () => {
		calls++;
		return Response.json({
			mode: "CONTRACT_TEST",
			syntheticOnly: true,
			liveProviderQualified: false,
		});
	};
	try {
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:4443/api/auth/facebook/status"),
					"status",
				)
			).status,
			200,
		);
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:4444/api/auth/facebook/status"),
					"status",
				)
			).status,
			503,
		);
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:31000/api/auth/facebook/status", {
						headers: { Host: "localhost:4443" },
					}),
					"status",
				)
			).status,
			200,
		);
		assert.equal(
			(
				await forwardFacebookSso(
					new Request("https://localhost:31000/api/auth/facebook/status", {
						headers: { Host: "evil.invalid:4443" },
					}),
					"status",
				)
			).status,
			503,
		);
		assert.equal(calls, 2);
	} finally {
		global.fetch = original;
	}
});

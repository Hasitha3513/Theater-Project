import { test } from "node:test";
import assert from "node:assert/strict";
import { forwardManualAuth } from "../lib/manual-auth-backend.ts";
const original = global.fetch;
const url = "http://127.0.0.1:3333";
function request(action, body, extra = {}) {
	return new Request(`${url}/api/auth/${action}`, {
		method: "POST",
		headers: {
			Origin: url,
			"X-CSRF-TOKEN": "test-csrf",
			"Content-Type": "application/json",
			Cookie: "unrelated=hidden; CINENEST_SESSION=test-session",
			...extra,
		},
		body: JSON.stringify(body),
	});
}
test("manual proxy protects methods,origin,csrf and exact schemas before forwarding", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error();
	};
	try {
		assert.equal(
			(
				await forwardManualAuth(
					request(
						"login",
						{ email: "a@example.test", password: "long secret phrase!" },
						{ Origin: "https://wrong.invalid" },
					),
					"login",
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardManualAuth(
					request(
						"login",
						{ email: "a@example.test", password: "long secret phrase!" },
						{ "X-CSRF-TOKEN": "" },
					),
					"login",
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardManualAuth(
					request("login", { email: "a", password: "x", role: "admin" }),
					"login",
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardManualAuth(
					new Request(url + "/api/auth/csrf?secret=1"),
					"csrf",
				)
			).status,
			400,
		);
		assert.equal(
			(await forwardManualAuth(new Request(url + "/api/auth/login"), "login"))
				.status,
			405,
		);
		for (const body of [
			"{broken",
			" ".repeat(2049),
			new Uint8Array([255, 254]),
		]) {
			const malformed = new Request(`${url}/api/auth/login`, {
				method: "POST",
				headers: {
					Origin: url,
					"X-CSRF-TOKEN": "test-csrf",
					"Content-Type": "application/json",
				},
				body,
			});
			assert.equal((await forwardManualAuth(malformed, "login")).status, 400);
		}
		assert.equal(calls, 0);
	} finally {
		global.fetch = original;
	}
});
test("manual proxy forwards only session and csrf, preserves original strict-parser bytes and sanitizes errors", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let seen;
	global.fetch = async (path, options) => {
		seen = { path: String(path), ...options };
		return new Response("private backend details", { status: 401 });
	};
	try {
		const r = await forwardManualAuth(
			request("login", {
				email: "a@example.test",
				password: "long secret phrase!",
			}),
			"login",
		);
		assert.equal(r.status, 401);
		assert.equal(await r.text(), "");
		assert.equal(seen.headers.get("Cookie"), "CINENEST_SESSION=test-session");
		assert.equal(seen.headers.get("X-CSRF-TOKEN"), "test-csrf");
		assert.equal(seen.headers.get("Authorization"), null);
		assert.equal(seen.redirect, "manual");
		assert.equal(seen.cache, "no-store");
		global.fetch = async () => new Response("private data", { status: 500 });
		assert.equal(
			(
				await forwardManualAuth(
					request("email-verifications", { token: "a".repeat(64) }),
					"email-verifications",
				)
			).status,
			503,
		);
	} finally {
		global.fetch = original;
	}
});
test("csrf DTO and cookies are allowlisted;unavailable upstream fails closed", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	global.fetch = async () =>
		Response.json(
			{ token: "token", headerName: "X-CSRF-TOKEN", private: "hidden" },
			{
				headers: {
					"Set-Cookie": "CINENEST_SESSION=test; HttpOnly; Secure; SameSite=Lax",
				},
			},
		);
	try {
		const r = await forwardManualAuth(
			new Request(url + "/api/auth/csrf"),
			"csrf",
		);
		assert.deepEqual(await r.json(), {
			token: "token",
			headerName: "X-CSRF-TOKEN",
		});
		assert.match(r.headers.get("Set-Cookie"), /Secure/);
		assert.equal(r.headers.get("cache-control"), "no-store");
		process.env.CINENEST_AUTH_ORIGIN = "https://user:secret@bad.invalid";
		assert.equal(
			(await forwardManualAuth(new Request(url + "/api/auth/csrf"), "csrf"))
				.status,
			503,
		);
	} finally {
		global.fetch = original;
	}
});

// Native browser fallback must never put a password into a GET query when JS is absent.
test("server-rendered auth forms use explicit POST and keep provider controls disabled", async () => {
	const { createElement } = await import("react");
	const { renderToStaticMarkup } = await import("react-dom/server");
	const { ManualAuthForm } = await import("../components/manual-auth-form.tsx");
	for (const mode of ["register", "login"]) {
		const html = renderToStaticMarkup(createElement(ManualAuthForm, { mode }));
		assert.match(html, /<form[^>]*method="post"/);
		assert.match(
			html,
			new RegExp(
				`action="/api/auth/${mode === "register" ? "registrations" : "login"}"`,
			),
		);
		assert.doesNotMatch(html, /maxLength="256"/);
		assert.match(html, /Google sign-in unavailable/);
		assert.match(html, /Facebook Login unavailable/);
	}
});

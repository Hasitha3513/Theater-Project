// Mocked transport and SSR tests; not real-backend E2E evidence.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	ManualAuthForm,
	VerificationRequestHelp,
} from "../components/manual-auth-form.tsx";
import { forwardManualAuth } from "../lib/manual-auth-backend.ts";

const base = "http://127.0.0.1:3333",
	original = global.fetch,
	commandId = "00000000-0000-4000-8000-000000000001";
const payloads = {
	registrations: {
		commandId,
		email: "customer@fixture.invalid",
		password: "Qualification passphrase 2026!",
		privacyRevision: "qualification-privacy-v1",
	},
	"email-verifications": {
		commandId,
		token: "a".repeat(64),
		password: "Qualification passphrase 2026!",
	},
	login: {
		commandId,
		email: "customer@fixture.invalid",
		password: "Qualification passphrase 2026!",
	},
	"recovery-requests": { commandId, email: "customer@fixture.invalid" },
	recoveries: {
		commandId,
		token: "b".repeat(64),
		newPassword: "Changed qualification phrase 2026!",
	},
};
function request(action, body, extra = {}) {
	return new Request(`${base}/api/auth/${action}`, {
		method: "POST",
		headers: {
			Origin: base,
			"X-CSRF-TOKEN": "test-csrf",
			"Content-Type": "application/json",
			Cookie: "unrelated=private; CINENEST_SESSION=test-session",
			...extra,
		},
		body: JSON.stringify(body),
	});
}
test("five exact customer DTOs forward with session/CSRF only and no credential URL", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		for (const [action, body] of Object.entries(payloads)) {
			let seen;
			global.fetch = async (url, options) => {
				seen = { url: String(url), ...options };
				return new Response(null, {
					status:
						action === "registrations" || action === "recovery-requests"
							? 202
							: 204,
				});
			};
			const response = await forwardManualAuth(request(action, body), action);
			assert.ok([202, 204].includes(response.status));
			assert.equal(seen.url, `http://127.0.0.1:9999/api/auth/${action}`);
			assert.deepEqual(JSON.parse(seen.body), body);
			assert.equal(seen.headers.get("Cookie"), "CINENEST_SESSION=test-session");
			assert.equal(seen.headers.get("Authorization"), null);
			assert.equal(seen.cache, "no-store");
			assert.equal(seen.redirect, "manual");
			assert.equal(response.headers.get("cache-control"), "no-store");
			assert.equal(await response.text(), "");
		}
	} finally {
		global.fetch = original;
	}
});
test("forged authority,invalid command identity,method/query/origin and oversized bodies refuse before network", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error();
	};
	try {
		for (const [action, body] of Object.entries(payloads)) {
			for (const invalid of [
				{ ...body, accountId: commandId },
				{ ...body, expectedRevision: 1 },
				{ ...body, commandId: "________-0000-0000-0000-000000000000" },
				{ ...body, commandId: 4 },
			])
				assert.equal(
					(await forwardManualAuth(request(action, invalid), action)).status,
					400,
				);
			assert.equal(
				(
					await forwardManualAuth(
						request(action, body, { Origin: "https://cross.invalid" }),
						action,
					)
				).status,
				403,
			);
			assert.equal(
				(
					await forwardManualAuth(
						request(action, body, { "X-CSRF-TOKEN": "" }),
						action,
					)
				).status,
				403,
			);
			assert.equal(
				(
					await forwardManualAuth(
						new Request(`${base}/api/auth/${action}`),
						action,
					)
				).status,
				405,
			);
		}
		assert.equal(
			(
				await forwardManualAuth(
					request("recoveries", {
						token: "a".repeat(64),
						newPassword: "Long private passphrase",
					}),
					"recoveries",
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardManualAuth(
					new Request(`${base}/api/auth/csrf?unsupported=1`),
					"csrf",
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardManualAuth(
					request("recoveries", {
						...payloads.recoveries,
						newPassword: "x".repeat(3000),
					}),
					"recoveries",
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardManualAuth(
					request("email-verifications", { commandId, token: "a".repeat(64) }),
					"email-verifications",
				)
			).status,
			400,
		);
		assert.equal(calls, 0);
	} finally {
		global.fetch = original;
	}
});
test("backend-selected csrf configuration is allowlisted and malformed mode/policy fails closed", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	const config = {
		token: "test-csrf",
		headerName: "X-CSRF-TOKEN",
		mode: "CUSTOMER_CREDENTIAL",
		privacyRevision: "privacy-v1",
		policyRevision: "security-v1",
		deliveryMode: "QUALIFICATION_ONLY",
	};
	try {
		global.fetch = async () =>
			Response.json({
				...config,
				privateMailbox: "hidden",
				accountExists: true,
			});
		assert.deepEqual(
			await (
				await forwardManualAuth(new Request(`${base}/api/auth/csrf`), "csrf")
			).json(),
			config,
		);
		for (const change of [
			{ mode: "PRODUCTION" },
			{ deliveryMode: "UNVERIFIED" },
			{ privacyRevision: "" },
			{ policyRevision: "x\nprivate" },
			{ privacyRevision: 5 },
			{ policyRevision: "x".repeat(257) },
		]) {
			global.fetch = async () => Response.json({ ...config, ...change });
			const response = await forwardManualAuth(
				new Request(`${base}/api/auth/csrf`),
				"csrf",
			);
			assert.equal(response.status, 503);
			assert.equal(await response.text(), "");
		}
		global.fetch = async () =>
			Response.json({
				token: "legacy",
				headerName: "X-CSRF-TOKEN",
				private: "hidden",
			});
		assert.deepEqual(
			await (
				await forwardManualAuth(new Request(`${base}/api/auth/csrf`), "csrf")
			).json(),
			{ token: "legacy", headerName: "X-CSRF-TOKEN" },
		);
	} finally {
		global.fetch = original;
	}
});
test("conflict,abuse,denial and unknown responses never expose private body or redirect", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		for (const status of [400, 401, 403, 404, 409, 429, 503, 302, 500]) {
			global.fetch = async () =>
				new Response("private backend details", {
					status,
					headers: { Location: "https://untrusted.invalid" },
				});
			const response = await forwardManualAuth(
				request("recoveries", payloads.recoveries),
				"recoveries",
			);
			assert.equal(response.status, [302, 500].includes(status) ? 503 : status);
			assert.equal(await response.text(), "");
			assert.equal(response.headers.get("Location"), null);
		}
	} finally {
		global.fetch = original;
	}
});
test("all credential forms use explicit POST targets and disabled provider controls", () => {
	for (const [mode, action] of Object.entries({
		register: "registrations",
		login: "login",
		verify: "email-verifications",
		recovery: "recovery-requests",
		reset: "recoveries",
	})) {
		const html = renderToStaticMarkup(createElement(ManualAuthForm, { mode }));
		assert.match(html, /<form[^>]*method="post"/);
		assert.ok(html.includes(`action="/api/auth/${action}"`));
		assert.match(html, /aria-live="polite"/);
		assert.match(html, /Google sign-in unavailable/);
		assert.match(html, /Facebook Login unavailable/);
		if (["verify", "reset"].includes(mode)) {
			assert.match(html, /name="token"/);
			assert.match(html, /type="password"/);
			assert.match(html, /autoComplete="off"|autocomplete="off"/);
		}
	}
});
test("manual token screens do not consume URL capabilities or persist browser secrets", async () => {
	const root = new URL("../", import.meta.url);
	for (const page of ["verify-email", "recover-account", "reset-password"]) {
		const source = await readFile(
			new URL(`app/${page}/page.tsx`, root),
			"utf8",
		);
		assert.doesNotMatch(
			source,
			/searchParams|cookies\(|localStorage|sessionStorage|console\./,
		);
	}
	const source = await readFile(
		new URL("components/manual-auth-form.tsx", root),
		"utf8",
	);
	assert.doesNotMatch(
		source,
		/localStorage|sessionStorage|console\.|URLSearchParams/,
	);
	assert.match(source, /crypto\.randomUUID/);
	assert.match(source, /form\.current\?\.reset\(\)/);
	assert.match(source, /No success is assumed/);
	assert.match(source, /QUALIFICATION ONLY/);
});

test("runtime configuration accepts exact private owned TLS files and rejects outside,symlink,wrong-name or shared paths", async () => {
	const { mkdtemp, mkdir, writeFile, chmod, symlink, rm } = await import(
		"node:fs/promises"
	);
	const { readCustomerCredentialConfiguration } = await import(
		"./customer-credential-e2e.mjs"
	);
	const run = await mkdtemp("/tmp/cinenest-customer-credential-config-");
	try {
		await chmod(run, 0o700);
		await mkdir(`${run}/tls`, { mode: 0o700 });
		for (const name of ["ca.crt", "server.crt", "server.key"])
			await writeFile(
				`${run}/tls/${name}`,
				"SYNTHETIC_TEST_ONLY_NOT_A_CERTIFICATE",
				{ mode: 0o600 },
			);
		const config = {
			backendOrigin: "https://127.0.0.1:9999",
			controlOrigin: "http://127.0.0.1:9998",
			caPath: `${run}/tls/ca.crt`,
			tlsCertPath: `${run}/tls/server.crt`,
			tlsKeyPath: `${run}/tls/server.key`,
		};
		const save = async (value) =>
			writeFile(`${run}/config.json`, JSON.stringify(value), { mode: 0o600 });
		await save(config);
		assert.equal(
			(await readCustomerCredentialConfiguration(run)).backend,
			"https://127.0.0.1:9999",
		);
		for (const change of [
			{ caPath: "/tmp/unowned-ca.crt" },
			{ tlsKeyPath: `${run}/tls/ca.crt` },
			{ backendOrigin: "https://remote.invalid:9999" },
			{ controlOrigin: "https://127.0.0.1:9998" },
		]) {
			await save({ ...config, ...change });
			await assert.rejects(readCustomerCredentialConfiguration(run));
		}
		await save(config);
		await chmod(`${run}/tls`, 0o755);
		await assert.rejects(readCustomerCredentialConfiguration(run));
		await chmod(`${run}/tls`, 0o700);
		await chmod(`${run}/tls/server.key`, 0o644);
		await assert.rejects(readCustomerCredentialConfiguration(run));
		await chmod(`${run}/tls/server.key`, 0o600);
		await rm(`${run}/tls/server.key`);
		await symlink(`${run}/tls/ca.crt`, `${run}/tls/server.key`);
		await assert.rejects(readCustomerCredentialConfiguration(run));
	} finally {
		await rm(run, { recursive: true, force: true });
	}
});
test("browser evidence refuses missing,failed,duplicate or intercepted groups instead of counting unexecuted PASS", async () => {
	const { validateCustomerCredentialBrowserResults } = await import(
		"./customer-credential-e2e.mjs"
	);
	const ids = [
		"configured-real-backend-forms",
		"registration-postgres-and-private-test-mail",
		"verification-single-use-and-pending-login-denial",
		"login-own-account-and-session-security",
		"generic-recovery-request-and-private-test-mail",
		"reset-invalidates-old-sessions-and-password",
		"csrf-command-replay-conflict-and-no-secret-storage",
	];
	const value = {
		status: "PASS",
		interception: false,
		interceptedRequests: 0,
		cookieInjection: false,
		groups: ids.map((id) => ({ id, status: "PASS" })),
		requests: [{ path: "/api/auth/registrations", status: 202 }],
	};
	assert.equal(validateCustomerCredentialBrowserResults(value), value);
	for (const change of [
		{ interceptedRequests: 1 },
		{ interception: true },
		{ cookieInjection: true },
		{ groups: [] },
		{ groups: value.groups.slice(1) },
		{
			groups: value.groups.map((group, index) =>
				index === 3 ? { ...group, status: "NOT_RUN" } : group,
			),
		},
		{ groups: value.groups.map(() => value.groups[0]) },
		{ requests: [] },
		{
			requests: [{ path: "/api/auth/registrations?token=hidden", status: 202 }],
		},
	])
		assert.throws(() =>
			validateCustomerCredentialBrowserResults({ ...value, ...change }),
		);
});

test("verification re-request navigation carries no token and explains chosen password,cooldown and generic delivery outcome", () => {
	const html = renderToStaticMarkup(createElement(VerificationRequestHelp));
	assert.match(html, /href="\/register"/);
	assert.match(html, /Request another verification message/);
	assert.match(html, /same email/);
	assert.match(html, /password you chose/);
	assert.match(html, /configured cooldown/);
	assert.match(html, /does not confirm account existence/);
	assert.match(html, /a message was sent, or delivery/);
	assert.doesNotMatch(html, /href="[^" ]*[?#]|token=|password=/);
});

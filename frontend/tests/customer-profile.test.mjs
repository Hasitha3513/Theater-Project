import assert from "node:assert/strict";
import { test } from "node:test";
import { profileRecord } from "../lib/customer-profile.ts";
import { forwardCustomerProfile } from "../lib/customer-profile-backend.ts";

const data = {
	customerId: "00000000-0000-0000-0000-000000000001",
	fullName: "Local Test Customer",
	phone: null,
	verifiedEmail: "profile@example.test",
	emailVerificationStatus: "VERIFIED",
	accountStatus: "ELIGIBLE",
	linkedProviderTypes: ["MANUAL"],
	revision: 1,
	updatedAt: "2026-10-28T12:00:00Z",
};
const old = global.fetch;
const base = "http://127.0.0.1:3000";
function req(body, headers = {}) {
	return new Request(`${base}/api/accounts/me/profile`, {
		method: "PUT",
		headers: {
			Origin: base,
			"Content-Type": "application/json",
			"X-CSRF-TOKEN": "synthetic",
			Cookie: "CINENEST_SESSION=synthetic; unrelated=hidden",
			...headers,
		},
		body: JSON.stringify(body),
	});
}
test("strict profile representation excludes subjects/credentials/unsupported account status", () => {
	assert.deepEqual(profileRecord(data), data);
	for (const extra of [
		{ subject: "hidden" },
		{ password: "hidden" },
		{ accountStatus: "SUSPENDED" },
		{ verifiedEmail: "real@example.com" },
	])
		assert.throws(() => profileRecord({ ...data, ...extra }));
});
test("profile proxy denies invalid origin,CSRF,mass assignment,query and oversized payload without network", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let calls = 0;
	global.fetch = async () => {
		calls++;
		throw Error();
	};
	try {
		const edit = { fullName: "Local Test", phone: null, expectedRevision: 1 };
		for (const r of [
			req(edit, { Origin: "null" }),
			req(edit, { Origin: "https://wrong.invalid" }),
			req(edit, { "X-CSRF-TOKEN": "" }),
		])
			assert.equal((await forwardCustomerProfile(r)).status, 403);
		assert.equal(
			(
				await forwardCustomerProfile(
					req({ ...edit, customerId: data.customerId }),
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardCustomerProfile(
					req({ ...edit, fullName: "x".repeat(3000) }),
				)
			).status,
			400,
		);
		assert.equal(
			(
				await forwardCustomerProfile(
					new Request(`${base}/api/accounts/me/profile?customerId=x`),
				)
			).status,
			400,
		);
		assert.equal(calls, 0);
	} finally {
		global.fetch = old;
	}
});
test("profile proxy forwards only session,CSRF,exact own route and original JSON;denies private upstream fields", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	let calls = 0;
	global.fetch = async (url, options) => {
		calls++;
		assert.equal(
			url.toString(),
			"http://127.0.0.1:9999/api/accounts/me/profile",
		);
		assert.equal(options.headers.get("Cookie"), "CINENEST_SESSION=synthetic");
		assert.equal(options.headers.get("X-CSRF-TOKEN"), "synthetic");
		assert.equal(options.redirect, "manual");
		return Response.json(
			calls === 1 ? data : { ...data, providerSubject: "hidden" },
		);
	};
	try {
		assert.equal(
			(
				await forwardCustomerProfile(
					req({ fullName: "Local Test", phone: null, expectedRevision: 1 }),
				)
			).status,
			200,
		);
		assert.equal(
			(
				await forwardCustomerProfile(
					req({ fullName: "Local Test", phone: null, expectedRevision: 1 }),
				)
			).status,
			503,
		);
	} finally {
		global.fetch = old;
	}
});
test("revision conflicts and denied/session-expired results never relay error bodies", async () => {
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
	try {
		for (const status of [400, 401, 403, 404, 409, 503]) {
			global.fetch = async () => new Response("private", { status });
			const r = await forwardCustomerProfile(
				new Request(`${base}/api/accounts/me/profile`),
			);
			assert.equal(r.status, status);
			assert.equal(await r.text(), "");
			assert.equal(r.headers.get("Cache-Control"), "no-store");
		}
	} finally {
		global.fetch = old;
	}
});

import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { readFile } from "node:fs/promises";
import {
	parseManagedGrants,
	grantPath,
	loadManagedGrants,
} from "../lib/managed-grants.ts";
import { forwardManagedGrants } from "../lib/managed-grants-backend.ts";
import { ManagedGrantInspection } from "../components/managed-grant-inspection.tsx";
import {
	fixture,
	context,
	venue,
	account,
	other,
	cursor,
} from "./managed-grants-fixtures.mjs";
const path = grantPath(context),
	url = `http://localhost${path}`;
const request = (suffix = "", options = {}) =>
	new Request(url + suffix, {
		headers: {
			Cookie: "CINENEST_SESSION=opaque-session",
			Authorization: "DO_NOT_FORWARD",
			"X-Permission": "ADMIN",
		},
		...options,
	});
async function upstream(fn) {
	const previous = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	const calls = [];
	let response = () => Response.json(fixture());
	global.fetch = async (...args) => {
		calls.push(args);
		return response();
	};
	try {
		await fn(calls, (r) => {
			response = r;
		});
	} finally {
		global.fetch = previous;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
}
test("exact managed-only projection discards unrelated/private fields", () => {
	const raw = fixture();
	raw.credentials = "PRIVATE";
	raw.items[0].evidence = "PRIVATE";
	raw.items[0].permissions = ["identity.grant.assign"];
	const p = parseManagedGrants(raw, context);
	assert.equal(p.kind, "MANAGED_GRANT_SUBSET");
	assert.equal(p.items.length, 1);
	assert.doesNotMatch(
		JSON.stringify(p),
		/PRIVATE|evidence|credentials|identity.grant.assign/,
	);
});
test("empty subset,expired/revoked rows and overlapping alternative paths remain distinct", () => {
	for (const patch of [
		{
			storedState: "REVOKED",
			effectiveAtObservation: false,
			overlapsAnotherActiveGrant: false,
		},
		{ validUntil: "2026-10-22T07:30:00Z", effectiveAtObservation: false },
	]) {
		const raw = fixture();
		Object.assign(raw.items[0], patch);
		const p = parseManagedGrants(raw, context);
		assert.equal(p.items[0].effectiveAtObservation, false);
		assert.equal(p.items[0].anotherEffectiveGrant, true);
	}
	const raw = fixture();
	raw.items = [];
	assert.deepEqual(parseManagedGrants(raw, context).items, []);
});
test("suspended target is visible only with false effectiveness", () => {
	const raw = fixture();
	raw.targetEligibility = "SUSPENDED";
	assert.throws(() => parseManagedGrants(raw, context));
	raw.items[0].effectiveAtObservation = false;
	raw.items[0].anotherEffectiveGrant = false;
	assert.equal(parseManagedGrants(raw, context).targetEligibility, "SUSPENDED");
});
test("header context,enum,dates,page bounds and cursor consistency are strict", () => {
	for (const patch of [
		{ kind: "ALL_PERMISSIONS" },
		{ venueId: other },
		{ accountId: venue },
		{ targetEligibility: ["ELIGIBLE"] },
		{ observedAt: "bad" },
		{ limit: 21 },
		{ hasNext: "true" },
		{ nextCursor: cursor },
		{ snapshotPolicy: "CACHED" },
		{ overlapBasis: "CURRENT" },
		{ items: null },
		{ hasNext: true, nextCursor: cursor },
	])
		assert.throws(() =>
			parseManagedGrants({ ...fixture(), ...patch }, context),
		);
});
test("malformed rows,unknown permissions,unsafe revision and duplicate order reject", () => {
	for (const patch of [
		{ grantId: "bad" },
		{ venueId: other },
		{ permission: "bank.synthetic.allocate" },
		{ permission: ["booking.synthetic.read"] },
		{ storedState: ["ACTIVE"] },
		{ storedState: "EXPIRED" },
		{ validFrom: "bad" },
		{ validUntil: "2020-01-01T00:00:00Z" },
		{ revision: -1 },
		{ revision: 9007199254740992 },
		{ effectiveAtObservation: "true" },
		{ anotherEffectiveGrant: null },
	]) {
		const raw = fixture();
		Object.assign(raw.items[0], patch);
		assert.throws(() => parseManagedGrants(raw, context));
	}
	const raw = fixture();
	raw.items.push({ ...raw.items[0] });
	assert.throws(() => parseManagedGrants(raw, context));
});
test("large bounded pages use actual hasNext without invented totals", () => {
	const raw = fixture();
	raw.limit = 100;
	raw.items = Array.from({ length: 100 }, (_, i) => ({
		...raw.items[0],
		grantId: `00000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`,
	}));
	raw.hasNext = true;
	raw.nextCursor = cursor;
	const p = parseManagedGrants(raw, { ...context, limit: 100 });
	assert.equal(p.items.length, 100);
	assert.equal(p.nextCursor, cursor);
	assert.equal(p.total, undefined);
	raw.items.push({ ...raw.items[0] });
	assert.throws(() => parseManagedGrants(raw, { ...context, limit: 100 }));
});
test("canonical context rejects traversal,invalid references and unsupported bounds", () => {
	for (const p of [
		{ venue: "../" },
		{ account: "ADMIN" },
		{ venue: venue.toUpperCase().replace("00000000", "ABCDEFAB") },
		{ limit: 0 },
		{ limit: 101 },
		{ limit: 1.5 },
	])
		assert.throws(() => grantPath({ ...context, ...p }));
});
test("proxy forwards default20 and maximum100 with only the approved session cookie", () =>
	upstream(async (calls) => {
		for (const limit of [null, 100]) {
			const raw = fixture();
			raw.limit = limit ?? 20;
			global.fetch = async (...args) => {
				calls.push(args);
				return Response.json(raw);
			};
			const r = await forwardManagedGrants(request(limit ? "?limit=100" : ""));
			assert.equal(r.status, 200);
			assert.equal(r.headers.get("Cache-Control"), "private, no-store");
			const opts = calls.at(-1)[1];
			assert.deepEqual(opts.headers, {
				Accept: "application/json",
				Cookie: "CINENEST_SESSION=opaque-session",
			});
			assert.equal(opts.redirect, "manual");
			assert.equal(opts.cache, "no-store");
			assert.equal(opts.method, "GET");
		}
	}));
test("proxy passes opaque pagination references without decoding or authority headers", () =>
	upstream(async (calls) => {
		assert.equal(
			(await forwardManagedGrants(request(`?limit=20&cursor=${cursor}`)))
				.status,
			200,
		);
		assert.equal(new URL(calls[0][0]).searchParams.get("cursor"), cursor);
	}));
test("duplicate,unsupported,negative,nonnumeric and malformed cursor queries reject before network", () =>
	upstream(async (calls) => {
		for (const q of [
			"limit=0",
			"limit=-1",
			"limit=101",
			"limit=01",
			"limit=1.5",
			"limit=abc",
			"limit=20&limit=20",
			"cursor=bad",
			"cursor=",
			"cursor=x&cursor=y",
			"permission=ADMIN",
			"page=0",
		]) {
			assert.equal(
				(await forwardManagedGrants(request(`?${q}`))).status,
				400,
				q,
			);
		}
		assert.equal(calls.length, 0);
	}));
test("missing/duplicate sessions,unsupported methods and malformed paths fail closed", () =>
	upstream(async (calls) => {
		assert.equal(
			(await forwardManagedGrants(request("", { headers: {} }))).status,
			401,
		);
		assert.equal(
			(
				await forwardManagedGrants(
					request("", {
						headers: { Cookie: "CINENEST_SESSION=a; CINENEST_SESSION=b" },
					}),
				)
			).status,
			401,
		);
		assert.equal(
			(await forwardManagedGrants(request("", { method: "POST" }))).status,
			404,
		);
		assert.equal(
			(await forwardManagedGrants(new Request(url.replace(venue, "bad"))))
				.status,
			404,
		);
		assert.equal(calls.length, 0);
	}));
for (const code of [400, 401, 403, 404, 409, 503])
	test(`proxy preserves sanitized ${code} including authority/session/cursor failures`, () =>
		upstream(async (_calls, set) => {
			set(() => new Response("PRIVATE_BACKEND", { status: code }));
			const r = await forwardManagedGrants(request());
			assert.equal(r.status, code);
			assert.equal(await r.text(), "");
		}));
test("proxy rejects excessive bodies,malformed DTO and unknown status without leakage", () =>
	upstream(async (_calls, set) => {
		for (const response of [
			() => new Response("x".repeat(256 * 1024 + 1)),
			() => Response.json({ ...fixture(), accountId: other }),
			() => Response.json({ evidence: "PRIVATE" }),
			() => new Response("PRIVATE", { status: 302 }),
		]) {
			set(response);
			const r = await forwardManagedGrants(request());
			assert.equal(r.status, 502);
			assert.equal(await r.text(), "");
		}
	}));
test("proxy projects out unknown fields before browser disclosure", () =>
	upstream(async (_calls, set) => {
		const f = fixture();
		f.private = "PRIVATE";
		f.items[0].secret = "PRIVATE";
		set(() => Response.json(f));
		assert.doesNotMatch(
			await (await forwardManagedGrants(request())).text(),
			/PRIVATE|secret/,
		);
	}));
async function client(fn) {
	const previous = global.fetch,
		calls = [];
	let accountId = venue,
		code = 200;
	global.fetch = async (input, init) => {
		calls.push([input, init]);
		if (input === "/api/accounts/me")
			return Response.json({ accountId, eligibility: "ELIGIBLE" });
		return code === 200
			? Response.json(fixture())
			: new Response("PRIVATE", { status: code });
	};
	try {
		await fn(calls, {
			account: (v) => {
				accountId = v;
			},
			code: (v) => {
				code = v;
			},
		});
	} finally {
		global.fetch = previous;
	}
}
test("client authenticates before and after protected GET without account.read or mutations", () =>
	client(async (calls) => {
		assert.equal(
			(await loadManagedGrants(context, null, new AbortController().signal))
				.items.length,
			1,
		);
		assert.deepEqual(
			calls.map((c) => String(c[0]).split("?")[0]),
			["/api/accounts/me", path, "/api/accounts/me"],
		);
		assert(calls.every((c) => !c[1].method || c[1].method === "GET"));
	}));
test("client preserves backend denied states and never fabricates demonstration grants", () =>
	client(async (_calls, set) => {
		for (const code of [400, 401, 403, 404, 409, 503]) {
			set.code(code);
			await assert.rejects(
				() => loadManagedGrants(context, null, new AbortController().signal),
				(e) => e.status === code,
			);
		}
	}));
test("session switch after protected response suppresses disclosure", () =>
	client(async () => {
		const prior = global.fetch;
		global.fetch = async (input, init) => {
			const response = await prior(input, init);
			if (input !== "/api/accounts/me") {
				const data = await response.json();
				return {
					ok: true,
					json: async () => {
						global.fetch = async () =>
							Response.json({ accountId: account, eligibility: "ELIGIBLE" });
						return data;
					},
				};
			}
			return response;
		};
		await assert.rejects(
			() => loadManagedGrants(context, null, new AbortController().signal),
			(e) => e.status === 409,
		);
	}));
test("aborted delayed JSON cannot return protected data", () =>
	client(async () => {
		const prior = global.fetch,
			c = new AbortController();
		global.fetch = async (input, init) => {
			if (input !== "/api/accounts/me")
				return {
					ok: true,
					json: async () => {
						c.abort();
						return fixture();
					},
				};
			return prior(input, init);
		};
		await assert.rejects(() => loadManagedGrants(context, null, c.signal));
	}));
test("initial accessible panel is truthful,independent and performs no automatic request", () => {
	const html = renderToStaticMarkup(
		React.createElement(ManagedGrantInspection),
	);
	for (const text of [
		"Managed Grant Inspection",
		"Managed grant subset only.",
		"identity.grant.read",
		"unassigned",
		"disabled",
		"Load managed grants",
		"Next page",
		"Reload first page",
		'role="status"',
	])
		assert(html.includes(text), text);
	assert.doesNotMatch(
		html,
		/<main|localStorage|sessionStorage|Grant assignment|PRIVATE/,
	);
});
test("source architecture forbids cursor persistence/logging or mutation handlers", async () => {
	const source = await readFile(
		new URL("../components/managed-grant-inspection.tsx", import.meta.url),
		"utf8",
	);
	assert.doesNotMatch(
		source,
		/localStorage|sessionStorage|console\.|atob\(|method:\s*["'](?:POST|PUT|DELETE|PATCH)/,
	);
	assert.match(source, /generation\.current !== token/);
	assert.match(source, /controller\.current\?\.abort/);
	const page = await readFile(
		new URL("../app/admin/accounts/page.tsx", import.meta.url),
		"utf8",
	);
	assert.match(page, /<ManagedGrantInspection/);
});
test("reject normalized invalid dates and reversed intervals;preserve submillisecond precision", () => {
	for (const date of ["2026-02-30T07:00:00Z", "2026-10-22T24:00:00Z"]) {
		const raw = fixture();
		raw.items[0].validFrom = date;
		assert.throws(() => parseManagedGrants(raw, context));
	}
	const raw = fixture();
	raw.items[0].validFrom = "2026-10-22T07:00:00.000001Z";
	raw.items[0].validUntil = "2026-10-22T07:00:00.000002Z";
	assert(parseManagedGrants(raw, context));
	[raw.items[0].validFrom, raw.items[0].validUntil] = [
		raw.items[0].validUntil,
		raw.items[0].validFrom,
	];
	assert.throws(() => parseManagedGrants(raw, context));
});
test("proxy rejects insecure/malformed configured origins without sending session", () =>
	upstream(async (calls) => {
		for (const origin of [
			"http://example.com",
			"https://u:p@example.com",
			"https://example.com/private",
			"https://example.com/?x=y",
		]) {
			process.env.CINENEST_AUTH_ORIGIN = origin;
			assert.equal((await forwardManagedGrants(request())).status, 503);
		}
		assert.equal(calls.length, 0);
	}));
test("unconfigured proxy is unavailable,network failure stays sanitized", () =>
	upstream(async () => {
		delete process.env.CINENEST_AUTH_ORIGIN;
		assert.equal((await forwardManagedGrants(request())).status, 503);
		process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
		global.fetch = async () => {
			throw Error("PRIVATE_NETWORK");
		};
		const r = await forwardManagedGrants(request());
		assert.equal(r.status, 502);
		assert.equal(await r.text(), "");
	}));
test("missing or denied session prevents protected fetch entirely", () =>
	client(async (calls) => {
		for (const code of [401, 403]) {
			let count = 0;
			global.fetch = async (input) => {
				count++;
				assert.equal(input, "/api/accounts/me");
				return new Response(null, { status: code });
			};
			await assert.rejects(
				() => loadManagedGrants(context, null, new AbortController().signal),
				(e) => e.status === code,
			);
			assert.equal(count, 1);
		}
		assert.equal(calls.length, 0);
	}));
test("invalid client cursor never reaches session or protected transport", () =>
	client(async (calls) => {
		for (const value of ["bad", "", cursor + "!"])
			await assert.rejects(() =>
				loadManagedGrants(context, value, new AbortController().signal),
			);
		assert.equal(calls.length, 0);
	}));

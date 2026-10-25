import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { BankInstructionPanel } from "../components/bank-instructions.tsx";
import {
	instructionPath,
	loadInstruction,
	parseInstruction,
} from "../lib/bank-instructions.ts";
import { forwardInstructions } from "../lib/bank-instructions-backend.ts";
import { context, fixture, requestId } from "./bank-instructions-fixtures.mjs";

test("strict paths and exact revision/request binding", () => {
	assert.match(instructionPath(context), /\/bank\/instructions\//);
	const c = { ...context, kind: "request", target: requestId };
	assert.deepEqual(parseInstruction(fixture(c), c), fixture(c));
	for (const x of [
		{ ...context, venue: "bad" },
		{ ...context, target: "../../secret" },
		{ ...context, kind: "all" },
	])
		assert.throws(() => instructionPath(x));
	assert.throws(() => parseInstruction(fixture(), c));
});
test("response allowlist excludes financial and secure fields", () => {
	const f = fixture();
	f.secret = "PRIVATE";
	f.instruction.reference = "PRIVATE";
	assert.deepEqual(parseInstruction(f, context), fixture());
});
for (const [key, value] of [
	["mode", "PRODUCTION"],
	["collectionEnabled", true],
	["synthetic", false],
	["approvalEvidence", "BANK_VERIFIED"],
	["venueId", requestId],
	["instructionRevisionId", requestId],
	["effectiveUntil", "2020-01-01T00:00:00Z"],
	["approvedAt", "2030-01-01T00:00:00Z"],
	["requestId", requestId],
])
	test(`reject misleading or wrong-context ${key}`, () =>
		assert.throws(() =>
			parseInstruction({ ...fixture(), [key]: value }, context),
		));
test("receiving-account substitution rejected", () => {
	const f = fixture();
	f.instruction.accountDisplay = "REAL ACCOUNT";
	assert.throws(() => parseInstruction(f, context));
});
test("abort after JSON completion discards response", async () => {
	const old = fetch,
		c = new AbortController();
	global.fetch = async () => ({
		ok: true,
		json: async () => {
			c.abort();
			return fixture();
		},
	});
	try {
		await assert.rejects(loadInstruction(context, c.signal));
	} finally {
		global.fetch = old;
	}
});
test("initial panel semantic, explicit, non-payable and no nested main", () => {
	const markup = renderToStaticMarkup(
		React.createElement(BankInstructionPanel),
	);
	assert.match(markup, /DO NOT TRANSFER MONEY/);
	assert.match(markup, /Load Instructions/);
	assert.match(markup, /role="status"/);
	assert.doesNotMatch(markup, /<main|NO REAL RECEIVING ACCOUNT/);
});
test("strict authenticated proxy, redaction and existing errors", async () => {
	const old = fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	const calls = [];
	global.fetch = async (...args) => {
		calls.push(args);
		return Response.json({ ...fixture(), secret: "PRIVATE" });
	};
	const req = (
		suffix = "",
		headers = { Cookie: "CINENEST_SESSION=test; PRIVATE=secret" },
	) =>
		new Request(`http://localhost${instructionPath(context)}${suffix}`, {
			headers,
		});
	try {
		assert.equal((await forwardInstructions(req("?extra=1"))).status, 400);
		assert.equal(
			(await forwardInstructions(req("?page=0&page=1"))).status,
			400,
		);
		assert.equal(calls.length, 0);
		assert.equal((await forwardInstructions(req("", {}))).status, 401);
		const r = await forwardInstructions(req());
		assert.equal(r.status, 200);
		assert.deepEqual(await r.json(), fixture());
		assert.match(r.headers.get("cache-control"), /no-store/);
		assert.equal(
			new Headers(calls[0][1].headers).get("Cookie"),
			"CINENEST_SESSION=test",
		);
		for (const status of [401, 403, 404, 409, 503]) {
			global.fetch = async () =>
				Response.json({ message: "PRIVATE" }, { status });
			const error = await forwardInstructions(req());
			assert.equal(error.status, status);
			assert.equal(await error.text(), "");
		}
		global.fetch = async () =>
			Response.json({ ...fixture(), collectionEnabled: true });
		assert.equal((await forwardInstructions(req())).status, 503);
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});

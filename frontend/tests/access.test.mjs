import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import {
	accessRecord,
	accessCommand,
	AccessWorkspace,
	ACCESS_LABEL,
} from "../components/access.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
const record = {
	id,
	bookingId: id,
	bookingRevision: 1,
	revision: 0,
	version: 1,
	operationId: id,
	permittedActions: [],
	synthetic: true,
	doorControlled: false,
};
test("all eight bounded access routes accept only their approved method", () => {
	for (const [path, method] of [
		[`/api/bookings/${id}/access`, "GET"],
		[`/api/bookings/${id}/access/credentials`, "POST"],
		[`/api/bookings/${id}/access/credentials/${id}/reveal`, "POST"],
		[`/api/bookings/${id}/access/simulated-attempts`, "POST"],
		[`/api/admin/venues/${id}/access/credentials`, "GET"],
		[`/api/admin/venues/${id}/access/credentials/${id}`, "GET"],
		[`/api/admin/venues/${id}/access/credentials/${id}/revocations`, "POST"],
		[`/api/admin/venues/${id}/access/credentials/${id}/simulations`, "POST"],
	]) {
		assert.equal(bankRoute(path, method), true);
		assert.equal(bankRoute(path, "DELETE"), false);
	}
	assert.equal(bankRoute(`/api/bookings/${id}/access/unlock`, "POST"), false);
});
test("mutations bind revision, stable command and operation identities without URL secrets", () => {
	for (const op of [
		"ISSUE",
		"REISSUE",
		"REVEAL",
		"ATTEMPT",
		"REVOKE",
		"SIMULATE",
	]) {
		const c = accessCommand(op, record, id, id, {
			token: "SYNTHETIC-PRIVATE",
			fixture: "SYNTHETIC-UNKNOWN",
		});
		const b = JSON.parse(c.init.body);
		assert.equal(b.commandKey, id);
		assert.equal(b.bookingRevision, 1);
		assert.equal(b.expectedRevision, 0);
		assert.equal(c.path.includes("SYNTHETIC-PRIVATE"), false);
		if (op === "SIMULATE") assert.equal(b.operationId, id);
	}
	assert.throws(() => accessCommand("UNLOCK", record, id, id));
	assert.throws(() => accessCommand("REVEAL", record, id, "invalid"));
	assert.throws(() => accessCommand("REVOKE", record, "invalid", id));
});
test("non-synthetic and actual-door success responses fail validation", () => {
	assert.equal(accessRecord(record), record);
	for (const value of [
		{ ...record, synthetic: false },
		{ ...record, doorControlled: true },
		{ ...record, bookingId: "invalid" },
		{ ...record, bookingRevision: 1.5 },
	])
		assert.throws(() => accessRecord(value));
});
test("customer and admin retain existing primitives and explicit simulation limits", () => {
	for (const admin of [false, true]) {
		const html = renderToStaticMarkup(
			React.createElement(AccessWorkspace, { admin }),
		);
		assert.ok(html.includes(ACCESS_LABEL));
		assert.ok(html.includes('role="status"'));
		assert.ok(html.includes('for="access-target"'));
		assert.ok(html.includes("No live delivery"));
		assert.equal(html.includes("SYNTHETIC-PRIVATE"), false);
	}
});
test("credential UI has no persistent browser storage or secret console logging", () => {
	const src = readFileSync(
		new URL("../components/access.tsx", import.meta.url),
		"utf8",
	);
	for (const banned of [
		"localStorage",
		"sessionStorage",
		"console.log",
		"console.error",
	])
		assert.equal(src.includes(banned), false);
	assert.ok(src.includes("visibilitychange"));
	assert.ok(src.includes('window.addEventListener("blur"'));
	assert.ok(src.includes("generation.current"));
});

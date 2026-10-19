import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	turnover,
	operationsPath,
	OperationsWorkspace,
	OPERATIONS_LABEL,
} from "../components/operations.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
const row = {
	case_id: id,
	venue_id: id,
	room_id: id,
	state: "OPEN",
	revision: 0,
	synthetic: true,
	checklist_version: "TEST_V1",
	checklist: ["SURFACES"],
	assignee_id: null,
	assignment: "UNASSIGNED",
	physicalOccupancy: "UNKNOWN",
	physicalReadiness: "UNVERIFIED",
	inventoryChanged: false,
	entries: [],
	permittedActions: ["read"],
};
test("only synthetic uncertainty-preserving operations responses are accepted", () => {
	assert.equal(turnover(row, id), row);
	for (const bad of [
		{ synthetic: false },
		{ physicalOccupancy: "EMPTY" },
		{ physicalReadiness: "READY" },
		{ inventoryChanged: true },
		{ revision: -1 },
		{ venue_id: "wrong" },
		{ state: "AVAILABLE" },
		{ checklist: [] },
	])
		assert.throws(() => turnover({ ...row, ...bad }, id));
});
test("operations transport restricts routes, scope and verbs", () => {
	const root = operationsPath(id);
	assert.equal(bankRoute(root, "GET"), true);
	assert.equal(bankRoute(root, "POST"), true);
	assert.equal(bankRoute(root, "DELETE"), false);
	for (const action of [
		"assignments",
		"acknowledgements",
		"checklist",
		"completion-assertions",
		"exceptions",
		"exception-resolutions",
		"reviews",
	]) {
		assert.equal(bankRoute(operationsPath(id, id, action), "POST"), true);
		assert.equal(bankRoute(operationsPath(id, id, action), "GET"), false);
	}
	assert.throws(() => operationsPath("../"));
	assert.throws(() => operationsPath(id, id, "unlock"));
	assert.equal(bankRoute(`${root}/${id}/unlock`, "POST"), false);
});
test("initial staff workspace labels uncertainty and contains no admission promise", () => {
	const html = renderToStaticMarkup(React.createElement(OperationsWorkspace));
	assert.ok(html.includes(OPERATIONS_LABEL));
	assert.ok(html.includes("PHYSICAL READINESS NOT VERIFIED"));
	assert.ok(html.includes("No cases loaded"));
	assert.ok(!html.includes("ROOM SAFE TO ENTER"));
	assert.ok(!html.includes("ROOM AVAILABLE"));
	assert.ok(html.includes('role="status"'));
});

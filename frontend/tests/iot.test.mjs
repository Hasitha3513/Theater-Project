import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	iotDevice,
	iotPath,
	IotWorkspace,
	IOT_LABEL,
} from "../components/iot.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
const device = {
	device_id: id,
	venue_id: id,
	room_id: id,
	revision: 1,
	online: true,
	enabled: true,
	desired: null,
	observed: "UNKNOWN",
	commands: [],
	audit: [],
	permittedActions: [],
	synthetic: true,
	equipmentControlled: false,
	physicalOccupancy: "UNKNOWN",
	physicalReadiness: "UNVERIFIED",
};
test("five IoT routes are precisely allowlisted", () => {
	const root = iotPath(id),
		detail = iotPath(id, id);
	assert(bankRoute(root, "GET"));
	assert(bankRoute(detail, "GET"));
	assert(bankRoute(iotPath(id, id, undefined, "command"), "POST"));
	for (const a of ["simulations", "reconciliations"])
		assert(bankRoute(iotPath(id, id, id, a), "POST"));
	for (const [p, m] of [
		[root, "POST"],
		[detail, "DELETE"],
		[`${detail}/unlock`, "POST"],
		[`${detail}/commands/${id}/retry`, "POST"],
		[`${detail}/commands/extra`, "POST"],
	])
		assert.equal(bankRoute(p, m), false);
});
test("response cannot claim real equipment occupancy or readiness", () => {
	assert.equal(iotDevice(device, id), device);
	for (const patch of [
		{ equipmentControlled: true },
		{ synthetic: false },
		{ physicalOccupancy: "EMPTY" },
		{ physicalReadiness: "READY" },
		{ venue_id: "wrong" },
		{ observed: "SAFE" },
		{ revision: NaN },
		{ commands: [{ id }] },
	])
		assert.throws(() => iotDevice({ ...device, ...patch }, id));
});
test("route builder rejects malformed and generic controls", () => {
	assert.throws(() => iotPath("../secret"));
	assert.throws(() => iotPath(id, id, id, "unlock"));
	assert.throws(() => iotPath(id, id, id, "command"));
});
test("staff UI makes no physical readiness or configured inventory claim", () => {
	const html = renderToStaticMarkup(React.createElement(IotWorkspace));
	assert(html.includes(IOT_LABEL));
	assert(html.includes("Physical occupancy UNKNOWN"));
	assert(html.includes("Room readiness UNVERIFIED"));
	assert(html.includes("Not configured"));
	assert(!html.includes("Unlock"));
	assert(!html.includes("Request simulated state"));
});

import assert from "node:assert/strict";
import test from "node:test";
import { sessionView } from "../components/staff-synthetic-session.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
import { item, id, venue, other } from "./staff-bookings-fixtures.mjs";
const value = () => ({
	bookingId: id,
	venueId: venue,
	roomId: other,
	bookingRevision: 1,
	rosterRevision: 0,
	startAvailable: true,
	checkoutAvailable: true,
	synthetic: true,
	inventoryChanged: false,
	physicalOccupancy: "UNKNOWN",
	physicalReadiness: "UNVERIFIED",
	checkIn: { actorId: other, commandKey: id },
	events: [],
});
test("session projection preserves scope and excludes private extra fields", () => {
	const result = sessionView({ ...value(), documentNumber: "PRIVATE" }, item());
	assert.equal(result.checkIn.actorId, other);
	assert.equal(JSON.stringify(result).includes("PRIVATE"), false);
	for (const change of [
		{ venueId: other },
		{ bookingRevision: 2 },
		{ physicalReadiness: "VERIFIED" },
		{ inventoryChanged: true },
	])
		assert.throws(() => sessionView({ ...value(), ...change }, item()));
});
test("session routes restrict exact methods and scope", () => {
	const path = `/api/admin/venues/${venue}/bookings/${id}/session`;
	assert.equal(bankRoute(path, "GET"), true);
	assert.equal(bankRoute(path, "POST"), false);
	assert.equal(bankRoute(path + "/starts", "POST"), true);
	assert.equal(bankRoute(path + "/starts", "GET"), false);
	assert.equal(bankRoute(path + "/unknown", "POST"), false);
});

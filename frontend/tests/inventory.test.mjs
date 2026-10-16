import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	HoldStatus,
	InventoryHold,
	InventoryInspection,
} from "../components/inventory.tsx";
import { remainingSeconds, validateHold, venueTime } from "../lib/inventory.ts";
import { forwardInventory, inventoryRoute } from "../lib/room-backend.ts";

const id = "00000000-0000-4000-8000-000000000001";
const hold = {
	holdId: id,
	roomId: id,
	timezone: "Asia/Colombo",
	sessionStart: "2026-10-20T04:30:00Z",
	sessionEnd: "2026-10-20T07:30:00Z",
	bufferMinutes: 7,
	blockedUntil: "2026-10-20T07:37:00Z",
	state: "ACTIVE",
	revision: 0,
	createdAt: "2026-10-18T00:00:00Z",
	serverNow: "2026-10-18T00:01:00Z",
	expiresAt: "2026-10-18T00:10:00Z",
	evidenceLevel: "INVENTORY_ONLY",
};
test("inventory adapter permits exactly approved methods and paths", () => {
	for (const [path, method] of [
		["/api/holds", "POST"],
		[`/api/holds/${id}`, "GET"],
		[`/api/holds/${id}/release`, "POST"],
		[`/api/rooms/${id}/availability`, "GET"],
		[`/api/admin/venues/${id}/inventory`, "GET"],
	])
		assert.equal(inventoryRoute(path, method), true);
	for (const path of [
		`/api/holds/${id}/confirm`,
		`/api/holds/${id}/extend`,
		"/api/bookings",
		"/api/holds/../../accounts",
	])
		assert.equal(inventoryRoute(path, "POST"), false);
	assert.equal(
		inventoryRoute(`/api/admin/venues/${id}/inventory`, "POST"),
		false,
	);
});
test("countdown uses server time plus elapsed duration without extending or confirming", () => {
	assert.equal(remainingSeconds(hold, 0), 540);
	assert.equal(remainingSeconds(hold, 540001), 0);
	assert.equal(remainingSeconds({ ...hold, state: "INVALIDATED" }, 0), 0);
	assert.match(
		render(h(HoldStatus, { hold, elapsedMs: 600000 })),
		/Deadline reached; refresh authoritative state/,
	);
	assert.match(
		render(h(HoldStatus, { hold, elapsedMs: 0 })),
		/No booking, price, payment, physical readiness or access is confirmed/,
	);
});
test("hold contract rejects false business success and malformed state", () => {
	assert.equal(validateHold(hold), hold);
	for (const bad of [
		{ ...hold, state: "CONFIRMED" },
		{ ...hold, revision: -1 },
		{ ...hold, expiresAt: "bad" },
		{ ...hold, evidenceLevel: "PRODUCTION" },
	])
		assert.throws(() => validateHold(bad));
});
test("inventory forms are labelled and no default duration or commercial action is invented", () => {
	const html = render(
		h(InventoryHold, { roomId: id, timezone: "Asia/Colombo" }),
	);
	assert.match(html, /Customer start in venue local time/);
	assert.match(html, /Customer duration \(minutes\)/);
	assert.match(html, /Check inventory \(no hold\)/);
	assert.doesNotMatch(html, /Pay now|Confirm booking|value="180"/);
	const admin = render(h(InventoryInspection));
	assert.match(admin, /inventory.read/);
	assert.match(admin, /Owner identities are not shown/);
});
test("inventory transport rejects cross-origin writes and unknown endpoints", async () => {
	assert.equal(
		(
			await forwardInventory(
				new Request("http://localhost/api/holds/unknown/confirm", {
					method: "POST",
				}),
			)
		).status,
		404,
	);
	const before = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	try {
		assert.equal(
			(
				await forwardInventory(
					new Request("http://localhost/api/holds", {
						method: "POST",
						headers: {
							origin: "https://wrong.example",
							"x-csrf-token": "SYNTHETIC",
						},
						body: "{}",
					}),
				)
			).status,
			403,
		);
	} finally {
		if (before === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = before;
	}
});

test("customer instants display in the explicit venue timezone", () => {
	assert.match(venueTime("2026-10-20T04:30:00Z", "Asia/Colombo"), /10:00/);
});

test("real HTTP inventory adapter forwards reviewed query/session and sanitizes rate failure", async () => {
	const { createServer } = await import("node:http");
	const { once } = await import("node:events");
	let observed;
	const server = createServer((req, res) => {
		observed = { url: req.url, cookie: req.headers.cookie };
		res.writeHead(429, { "Content-Type": "application/json" });
		res.end(
			JSON.stringify({ message: "SYNTHETIC private upstream diagnostic" }),
		);
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	try {
		const result = await forwardInventory(
			new Request(
				`http://localhost/api/rooms/${id}/availability?sessionStartLocal=2026-10-20T10%3A00&durationMinutes=180&guests=4&ownerId=untrusted`,
				{ headers: { cookie: "other=private; CINENEST_SESSION=SYNTHETIC" } },
			),
		);
		assert.equal(result.status, 429);
		assert.equal(result.headers.get("cache-control"), "no-store");
		assert.equal(observed.cookie, "CINENEST_SESSION=SYNTHETIC");
		assert.match(observed.url, /durationMinutes=180&guests=4/);
		assert.doesNotMatch(observed.url, /ownerId/);
		assert.doesNotMatch(await result.text(), /private upstream diagnostic/);
	} finally {
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
		await new Promise((r) => server.close(r));
	}
});

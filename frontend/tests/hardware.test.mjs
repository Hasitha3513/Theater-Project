import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import {
	hardwareCategories,
	hardwareContext,
	hardwareRoute,
	hardwareId,
	registryPath,
	assignmentPath,
	parseDevice,
	parseHardwarePage,
	parseAssignment,
	parseRegistration,
	registrationValues,
	validateRegistration,
	validateAssignment,
	HardwareCommand,
} from "../lib/hardware.ts";
import { forwardHardware } from "../lib/room-backend.ts";
import {
	HardwareManagement,
	HardwareRegistryTable,
} from "../components/hardware-management.tsx";
import { adminNavigation } from "../lib/navigation.ts";
const venue = "10000000-0000-4000-8000-000000000001",
	room = "10000000-0000-4000-8000-000000000002",
	device = "10000000-0000-4000-8000-000000000003",
	command = "10000000-0000-4000-8000-000000000004",
	assignment = "10000000-0000-4000-8000-000000000005",
	at = "2030-01-01T00:00:00Z";
const raw = {
	deviceId: device,
	venueId: venue,
	provider: "TUYA",
	revision: 1,
	manufacturer: "Synthetic Manufacturer",
	model: "Synthetic Model",
	sku: null,
	hardwareRevision: null,
	firmwareRevision: null,
	declaredCapabilities: ["SCHEDULED_PIN"],
	declarationSource: "OPERATOR_DECLARATION",
	registeredAt: at,
	recordedAt: at,
	qualification: "UNQUALIFIED",
	customerAccessEnabled: false,
	health: "UNKNOWN",
	registrationStatus: "REGISTERED",
	physicalAcceptance: "NOT_VERIFIED",
	providerOwnership: "UNVERIFIED",
	lastDeviceObservationAt: null,
	mode: "QUALIFICATION_ONLY",
	roomId: null,
};
const binding = {
	assignmentId: assignment,
	venueId: venue,
	roomId: room,
	deviceId: device,
	revision: 1,
	expectedRoomConfiguration: "0:0:fixture",
	expectedDeviceMetadataRevision: 1,
	createdAt: at,
	replayed: false,
	state: "UNQUALIFIED",
	customerAccessEnabled: false,
	physicalAcceptance: "NOT_VERIFIED",
	mode: "QUALIFICATION_ONLY",
};
const page = (items) => ({
	items,
	page: 0,
	size: 25,
	hasNext: false,
	observedAt: at,
	mode: "QUALIFICATION_ONLY",
});
const draft = {
	manufacturer: "Synthetic Manufacturer",
	model: "Synthetic Model",
	sku: "",
	hardware: "",
	firmware: "",
	reference: device,
	referenceVersion: command,
	capabilities: ["SCHEDULED_PIN"],
};
const values = () => registrationValues(draft),
	body = () => ({ ...values(), commandKey: command });
const assignmentBody = () => ({
	commandKey: command,
	deviceId: device,
	expectedAssignmentRevision: 0,
	expectedRoomConfiguration: "0:0:fixture",
	expectedDeviceMetadataRevision: 1,
});
const receipt = {
	deviceId: device,
	venueId: venue,
	registeredMetadataRevision: 1,
	registeredAt: at,
	registrationStatus: "REGISTERED",
	activationPerformed: false,
	replayed: false,
};
const req = (path, method = "GET", value, extra = {}) =>
	new Request(`http://127.0.0.1:42137${path}`, {
		method,
		headers: {
			cookie: "CINENEST_SESSION=fixture",
			...(method === "POST"
				? {
						"content-type": "application/json",
						origin: "http://127.0.0.1:42137",
						"x-csrf-token": "fixture-csrf",
					}
				: {}),
			...extra,
		},
		...(value === undefined
			? {}
			: { body: typeof value === "string" ? value : JSON.stringify(value) }),
	});
async function mockUp(value, action, status = 200) {
	const before = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN,
		calls = [];
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	global.fetch = async (u, init) => {
		calls.push({ url: String(u), init });
		return Response.json(value, { status });
	};
	try {
		return await action(calls);
	} finally {
		global.fetch = before;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
}

test("Hardware navigation preserves the Lighting Simulator", () => {
	assert(
		adminNavigation.some(
			([p, l]) => p === "/admin/hardware" && l === "Hardware Management",
		),
	);
	assert(
		adminNavigation.some(
			([p, l]) => p === "/admin/iot" && l === "Lighting simulator",
		),
	);
	assert.equal(hardwareCategories.length, 8);
});
test("paths reject unsafe references and unsupported revisions", () => {
	assert.equal(hardwareId(venue.toUpperCase()), venue);
	for (const v of ["../other", "https://provider.invalid", `${venue}?token=x`])
		assert.throws(() => registryPath(v));
	assert.throws(() => registryPath(venue, device, Number.MAX_SAFE_INTEGER + 1));
});
test("only exact registry and assignment methods are allowed", () => {
	for (const p of [
		registryPath(venue),
		registryPath(venue, device),
		registryPath(venue, device, "history"),
		registryPath(venue, device, 1),
		assignmentPath(venue, room),
		assignmentPath(venue, room, assignment),
	])
		assert(hardwareRoute(p, "GET"));
	for (const p of [registryPath(venue), assignmentPath(venue, room)])
		assert(hardwareRoute(p, "POST"));
	for (const m of ["PUT", "PATCH", "DELETE", "HEAD"])
		assert.equal(hardwareRoute(registryPath(venue), m), false);
	for (const suffix of ["activate", "pins", "discover", "connections"])
		assert.equal(
			hardwareRoute(`${registryPath(venue, device)}/${suffix}`, "POST"),
			false,
		);
	assert.equal(hardwareRoute(registryPath(venue, device), "POST"), false);
});
test("registry responses bind exact venue/device/revision and strip private fields", () => {
	const d = parseDevice(
		{ ...raw, password: "PRIVATE", provider_reference_id: "PRIVATE" },
		venue,
		device,
		1,
	);
	assert(!("password" in d));
	assert(!("provider_reference_id" in d));
	assert(!("roomId" in d));
	for (const x of [
		{ ...raw, venueId: room },
		{ ...raw, deviceId: room },
		{ ...raw, revision: 2 },
	])
		assert.throws(() => parseDevice(x, venue, device, 1));
});
test("unknown firmware stays unknown and declarations never activate access", () => {
	assert.equal(parseDevice(raw, venue).firmwareRevision, null);
	for (const x of [
		{ ...raw, qualification: "QUALIFIED" },
		{ ...raw, customerAccessEnabled: true },
		{ ...raw, health: "CONNECTED" },
		{ ...raw, providerOwnership: "VERIFIED" },
		{ ...raw, declaredCapabilities: ["UNLOCK"] },
		{ ...raw, revision: 1.5 },
		{ ...raw, recordedAt: "bad" },
	])
		assert.throws(() => parseDevice(x, venue));
});
test("BFF page output can be validated again and rejects duplicates", () => {
	const c = hardwareContext(registryPath(venue), "GET"),
		p = parseHardwarePage(page([raw]), c);
	assert.deepEqual(parseHardwarePage(p, c), p);
	assert.throws(() => parseHardwarePage(page([raw, raw]), c));
	assert.throws(() => parseHardwarePage({ ...page([]), size: 100 }, c));
});
test("room assignment proves exact V33 binding rather than registry roomId", () => {
	const a = parseAssignment(binding, venue, room, assignment);
	assert.deepEqual(parseAssignment(a, venue, room, assignment), a);
	for (const x of [
		{ ...binding, roomId: device },
		{ ...binding, venueId: room },
		{ ...binding, revision: 2 },
		{ ...binding, customerAccessEnabled: true },
	])
		assert.throws(() => parseAssignment(x, venue, room, assignment));
});
test("assignment pages retain authoritative room configuration", () => {
	const c = hardwareContext(assignmentPath(venue, room), "GET"),
		p = {
			...page([binding]),
			roomConfiguration: "0:0:fixture",
			customerAccessEnabled: false,
		};
	const result = parseHardwarePage(p, c);
	assert.deepEqual(parseHardwarePage(result, c), result);
	assert.throws(() =>
		parseHardwarePage({ ...p, customerAccessEnabled: true }, c),
	);
	assert.throws(() => parseHardwarePage({ ...p, roomConfiguration: "" }, c));
});
test("registration receipt rejects activation or a different venue", () => {
	assert.equal(parseRegistration(receipt, venue).activationPerformed, false);
	assert.throws(() =>
		parseRegistration({ ...receipt, activationPerformed: true }, venue),
	);
	assert.throws(() => parseRegistration({ ...receipt, venueId: room }, venue));
});
test("registration accepts only the existing synthetic contract", () => {
	assert.equal(validateRegistration(body()).provider, "TUYA");
	for (const b of [
		{ ...body(), provider: "OTHER" },
		{ ...body(), apiKey: "PRIVATE" },
		{ ...body(), expectedRevision: 1 },
		{
			...body(),
			providerDeviceReference: {
				id: "https://other.invalid",
				version: command,
			},
		},
	])
		assert.throws(() => validateRegistration(b));
	assert.throws(() => registrationValues({ ...draft, manufacturer: "" }));
});
test("assignment validation rejects stale/noninitial revisions and unsupported fields", () => {
	assert.equal(
		validateAssignment(assignmentBody()).expectedDeviceMetadataRevision,
		1,
	);
	for (const b of [
		{ ...assignmentBody(), expectedAssignmentRevision: 1 },
		{ ...assignmentBody(), expectedDeviceMetadataRevision: 0 },
		{ ...assignmentBody(), activate: true },
	])
		assert.throws(() => validateAssignment(b));
});
test("retry preserves command identity and rejects changed-payload replay", async () => {
	let count = 0;
	const c = new HardwareCommand(() => (++count === 1 ? command : assignment)),
		sent = [],
		send = async (_p, b) => {
			sent.push(b);
			return {};
		};
	await c.send(registryPath(venue), values(), send);
	await c.send(registryPath(venue), values(), send);
	assert.equal(sent[0], sent[1]);
	assert.equal(count, 1);
	await assert.rejects(() =>
		c.send(registryPath(venue), { ...values(), model: "Changed" }, send),
	);
	c.clear();
	await c.send(registryPath(venue), values(), send);
	assert.notEqual(sent[0], sent[2]);
});
test("unknown network outcome retains original command", async () => {
	const c = new HardwareCommand(() => command),
		sent = [];
	await assert.rejects(() =>
		c.send(registryPath(venue), values(), async (_p, b) => {
			sent.push(b);
			throw Error("unknown");
		}),
	);
	await c.send(registryPath(venue), values(), async (_p, b) => {
		sent.push(b);
		return {};
	});
	assert.equal(sent[0], sent[1]);
});
test("synchronous latch prevents duplicate HTTP submission", async () => {
	let release,
		calls = 0;
	const wait = new Promise((r) => (release = r)),
		c = new HardwareCommand(() => command),
		first = c.send(registryPath(venue), values(), async () => {
			calls++;
			await wait;
			return {};
		});
	await assert.rejects(() =>
		c.send(registryPath(venue), values(), async () => {
			calls++;
			return {};
		}),
	);
	release();
	await first;
	assert.equal(calls, 1);
});
test("session and injected authority fail before upstream traffic", async () => {
	await mockUp({}, async (calls) => {
		assert.equal(
			(
				await forwardHardware(
					new Request(`http://127.0.0.1${registryPath(venue)}`),
				)
			).status,
			401,
		);
		assert.equal(
			(
				await forwardHardware(
					req(registryPath(venue), "GET", undefined, { "x-role": "ADMIN" }),
				)
			).status,
			400,
		);
		assert.equal(calls.length, 0);
	});
});
test("unknown/duplicate queries and detail mutations cannot proxy", async () => {
	await mockUp({}, async (calls) => {
		for (const suffix of [
			"?page=1&page=2",
			"?size=25",
			"?page=-1",
			"?providerUrl=https://other.invalid",
		])
			assert.equal(
				(await forwardHardware(req(registryPath(venue) + suffix))).status,
				400,
			);
		assert.equal(
			(await forwardHardware(req(registryPath(venue, device), "POST", body())))
				.status,
			404,
		);
		assert.equal(calls.length, 0);
	});
});
test("protected GET strips client authority and binds upstream venue", async () => {
	await mockUp(page([raw]), async (calls) => {
		const r = await forwardHardware(
			req(registryPath(venue), "GET", undefined, { "x-untrusted": "PRIVATE" }),
		);
		assert.equal(r.status, 200);
		assert.equal(
			calls[0].init.headers.get("Cookie"),
			"CINENEST_SESSION=fixture",
		);
		assert.equal(calls[0].init.headers.get("x-untrusted"), null);
		assert.match(r.headers.get("cache-control"), /no-store/);
	});
	await mockUp(page([{ ...raw, venueId: room }]), async () =>
		assert.equal((await forwardHardware(req(registryPath(venue)))).status, 502),
	);
});
test("writes enforce same origin and CSRF before using existing transport", async () => {
	await mockUp(
		receipt,
		async (calls) => {
			assert.equal(
				(
					await forwardHardware(
						req(registryPath(venue), "POST", body(), { "x-csrf-token": "" }),
					)
				).status,
				403,
			);
			assert.equal(
				(
					await forwardHardware(
						req(registryPath(venue), "POST", body(), {
							origin: "https://evil.invalid",
						}),
					)
				).status,
				403,
			);
			assert.equal(calls.length, 0);
			assert.equal(
				(await forwardHardware(req(registryPath(venue), "POST", body())))
					.status,
				201,
			);
			assert.equal(calls[0].init.headers.get("X-CSRF-TOKEN"), "fixture-csrf");
			assert.equal(JSON.parse(calls[0].init.body).commandKey, command);
		},
		201,
	);
});
test("upstream authorization/revision denials remain authoritative and sanitized", async () => {
	for (const status of [401, 403, 409, 503])
		await mockUp(
			{ message: "PRIVATE INTERNAL" },
			async () => {
				const r = await forwardHardware(
					req(assignmentPath(venue, room), "POST", assignmentBody()),
				);
				assert.equal(r.status, status);
				assert.doesNotMatch(await r.text(), /PRIVATE INTERNAL/);
			},
			status,
		);
});
test("oversized bodies and provider-secret fields reject without forwarding", async () => {
	await mockUp({}, async (calls) => {
		assert.equal(
			(
				await forwardHardware(
					req(registryPath(venue), "POST", "x".repeat(16385)),
				)
			).status,
			413,
		);
		assert.equal(
			(
				await forwardHardware(
					req(registryPath(venue), "POST", { ...body(), password: "PRIVATE" }),
				)
			).status,
			400,
		);
		assert.equal(calls.length, 0);
	});
});
test("external redirects are never followed", async () => {
	const old = global.fetch,
		origin = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:8080";
	global.fetch = async (_u, init) => {
		assert.equal(init.redirect, "manual");
		return new Response(null, {
			status: 302,
			headers: { location: "https://other.invalid" },
		});
	};
	try {
		assert.equal((await forwardHardware(req(registryPath(venue)))).status, 502);
	} finally {
		global.fetch = old;
		if (origin === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = origin;
	}
});
test("registry renders parsed metadata, column headers and escaped names", () => {
	const d = parseDevice(
			{ ...raw, manufacturer: "<script>bad</script>" },
			venue,
		),
		html = renderToStaticMarkup(
			React.createElement(HardwareRegistryTable, {
				items: [d],
				inspect: () => {},
			}),
		);
	assert.match(html, /Protected Smart Door Lock registry/);
	assert.match(html, /&lt;script&gt;/);
	assert.doesNotMatch(html, /<script>/);
	assert.match(html, /scope="col"/);
	assert.match(html, /UNQUALIFIED/);
	assert.match(html, /overflow-x:auto/);
});
test("workspace preserves accessible structure and disabled physical operations", () => {
	const html = renderToStaticMarkup(React.createElement(HardwareManagement));
	assert.equal((html.match(/<h1/g) || []).length, 1);
	for (const s of [
		"Synthetic Smart Lock Registration",
		"Device Registry",
		"Room Assignment",
		"NOT_CONFIGURED",
		"NOT_EVALUATED",
		"REQUIRES_PHYSICAL_VERIFICATION",
	])
		assert(html.includes(s));
	assert.match(html, /role="status"/);
	assert.match(html, /disabled=""[^>]*>.*?Activate approved device/s);
	assert.doesNotMatch(
		html,
		/type="password"|name="apiKey"|Generate PIN|Unlock door/,
	);
	assert.match(html, /minmax\(min\(100%, 16rem\), 1fr\)/);
});
test("scope/session invalidation and future simulator link remain explicit", () => {
	const s = readFileSync(
		new URL("../components/hardware-management.tsx", import.meta.url),
		"utf8",
	);
	assert.match(s, /cinenest-session-change/);
	assert.match(s, /generation === turn.current/);
	assert.match(s, /locked.current/);
	assert.match(s, /href="\/admin\/iot"/);
	assert.doesNotMatch(
		s,
		/localStorage|sessionStorage|dangerouslySetInnerHTML|\/activate|\/pins/,
	);
});

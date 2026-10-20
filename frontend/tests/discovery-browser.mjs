// Labelled synthetic intercepted presentation tests; not real-backend E2E evidence.
import { createServer } from "node:net";
import { once } from "node:events";
import { spawn } from "node:child_process";
import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";
// Existing SYNTHETIC landing/rooms fixture values,not operational inventory or pricing.
const id = "00000000-0000-4000-8000-000000000001";
const venue = {
	venueId: id,
	code: "SYNTHETIC",
	displayName: "SYNTHETIC venue",
	timezone: "Asia/Colombo",
	windows: [{ weekday: 1, openingMinute: 610, closingMinute: 1440 }],
	cleaningBufferMinutes: 7,
	version: 0,
	configured: true,
	enabled: true,
};
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC cinema room",
	description: "Synthetic HDMI room",
	capacity: 4,
	enabled: true,
	configured: true,
	version: 0,
	facilities: [{ code: "HDMI", label: "HDMI input", sortOrder: 0 }],
	images: [],
	permittedActions: [],
	venue,
};
const pkg = {
	packageId: id,
	versionId: id,
	name: "SYNTHETIC indicative package",
	description: "Synthetic neutral equipment",
	basePriceMinor: "12345",
	includedMinutes: 180,
	includedGuests: 4,
	options: [],
};
const { chromium } = await import(
	pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH + "/index.mjs").href
);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`;
const server = spawn(
	process.execPath,
	[
		"node_modules/next/dist/bin/next",
		"start",
		"--hostname",
		"127.0.0.1",
		"--port",
		String(port),
	],
	{ stdio: "pipe" },
);
let output = "",
	browser;
server.stdout.on("data", (b) => (output += b));
server.stderr.on("data", (b) => (output += b));

let groups = 0;
const pass = (label) => {
	groups++;
	console.log("PASS " + label);
};
try {
	for (let i = 0; i < 100; i++) {
		if (server.exitCode !== null) throw Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 200));
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage({
		viewport: { width: 1280, height: 1000 },
	});
	let mode = "ok",
		writes = 0,
		reads = [],
		delay = 0;
	await page.route("**/api/**", async (route) => {
		const request = route.request(),
			url = new URL(request.url()),
			p = url.pathname;
		reads.push(url.pathname + url.search);
		if (request.method() !== "GET") {
			writes++;
			return route.fulfill({ status: 403, json: {} });
		}
		if (delay && p === "/api/catalog/rooms")
			await new Promise((r) => setTimeout(r, delay));
		if (mode === "unavailable" && p === "/api/catalog/rooms")
			return route.fulfill({ status: 503, json: {} });
		if (mode === "missing" && p === `/api/catalog/rooms/${id}`)
			return route.fulfill({ status: 404, json: {} });
		if (mode === "denied" && p === `/api/catalog/rooms/${id}`)
			return route.fulfill({ status: 403, json: {} });
		let data;
		if (p === "/api/catalog/venues") data = [venue];
		else if (p === "/api/catalog/rooms")
			data = {
				items: mode === "empty" ? [] : [room],
				total: mode === "empty" ? 0 : 1,
				page: 0,
				size: 24,
			};
		else if (p === `/api/catalog/rooms/${id}`) data = room;
		else if (p.startsWith(`/api/venues/${id}/packages`))
			data = { items: [pkg], purchasable: false };
		else return route.fulfill({ status: 401, json: {} });
		return route.fulfill({ json: data });
	});
	await page.goto(base + "/rooms");
	await page.getByRole("link", { name: /^Room details/ }).waitFor();
	assert.equal(await page.locator(".customer-discovery").count(), 1);
	assert.equal(
		await page
			.getByText("Room photograph unavailable ·", { exact: true })
			.count(),
		1,
	);
	pass("light panel and explicit absent image, factual facility/capacity");
	await Promise.all([
		page.waitForResponse((r) => r.url().includes("facility=HDMI")),
		page.getByLabel("Facility", { exact: true }).selectOption("HDMI"),
	]);
	assert.equal(
		await page.getByLabel("Facility", { exact: true }).inputValue(),
		"HDMI",
	);
	assert.ok(reads.some((r) => r.includes("facility=HDMI")));
	pass("customer label maps to existing backend facility code");
	await Promise.all([
		page.waitForResponse((r) => r.url().includes("q=Synthetic")),
		page.getByLabel("Name or code", { exact: true }).fill("Synthetic"),
	]);
	await Promise.all([
		page.waitForResponse(
			(r) =>
				r.url().includes("/api/catalog/rooms?") &&
				new URL(r.url()).searchParams.get("q") === "",
		),
		page.getByRole("button", { name: "Reset filters", exact: true }).click(),
	]);
	assert.equal(
		await page.getByLabel("Facility", { exact: true }).inputValue(),
		"",
	);
	pass("search and explicit reset retain backend filtering");
	const link = page.getByRole("link", { name: /^Room details/ });
	await link.focus();
	await page.keyboard.press("Tab");
	await page.keyboard.press("Shift+Tab");
	assert.equal(
		await link.evaluate((e) => getComputedStyle(e).outlineStyle),
		"solid",
	);
	await page.keyboard.press("Enter");
	await page.waitForURL(base + "/rooms/" + id);
	assert.equal(new URL(page.url()).search, "");
	pass("keyboard room navigation uses opaque reference only");
	await page
		.getByRole("heading", {
			name: "Indicative published packages",
			exact: true,
		})
		.waitFor();
	await page
		.getByRole("button", { name: `Package details ${pkg.name}`, exact: true })
		.click();
	await page
		.getByText("Indicative only · Purchasing is unavailable.", { exact: true })
		.waitFor();
	assert.ok(reads.some((r) => r.includes("/packages/" + pkg.packageId + "?")));
	pass(
		"existing protected package read, indicative price and explicit unavailable purchase",
	);
	await page
		.getByRole("link", { name: "Check availability", exact: true })
		.first()
		.click();
	assert.equal(new URL(page.url()).hash, "#booking-entry");
	assert.equal(writes, 0);
	pass(
		"booking entry scrolls without creating hold, quote, payment or booking",
	);
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			),
			false,
		);
		assert.ok(
			await page
				.locator(".cinema-illustration")
				.first()
				.evaluate((e) => e.getBoundingClientRect().height > 100),
		);
	}
	pass("room detail has no overflow at mobile/tablet/desktop");
	await page.getByRole("link", { name: "Back to rooms", exact: true }).click();
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		await page.getByRole("link", { name: /^Room details/ }).waitFor();
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			),
			false,
		);
	}
	assert.ok(
		await page
			.locator(".room-grid")
			.first()
			.evaluate((e) => e.firstElementChild.getBoundingClientRect().width < 500),
	);
	pass("catalog responsive and desktop card no longer stretches full width");
	mode = "empty";
	await page.reload();
	await page
		.getByRole("heading", { name: "No matching rooms", exact: true })
		.waitFor();
	assert.equal(await page.locator("[data-room-id]").count(), 0);
	pass("empty catalog never fabricates rooms or statistics");
	mode = "unavailable";
	await page.reload();
	await page
		.getByRole("heading", { name: "Service unavailable", exact: true })
		.waitFor();
	mode = "ok";
	const retry = page.getByRole("button", { name: "Retry", exact: true });
	await retry.focus();
	await page.keyboard.press("Enter");
	await page.getByRole("link", { name: /^Room details/ }).waitFor();
	pass("backend error exposes safe explicit keyboard retry");
	mode = "missing";
	await page.goto(base + "/rooms/" + id);
	await page
		.getByRole("heading", { name: "Item unavailable", exact: true })
		.waitFor();
	assert.equal(await page.locator("#booking-entry").count(), 0);
	pass("missing room exposes no booking entry");
	mode = "denied";
	await page.reload();
	await page
		.getByRole("heading", { name: "Access denied", exact: true })
		.waitFor();
	assert.equal(await page.locator("#room-packages").count(), 0);
	pass("denied room exposes no package context");
	mode = "ok";
	delay = 1500;
	await page.goto(base + "/rooms");
	await page.getByRole("heading", { name: "Loading", exact: true }).waitFor();
	await page.getByRole("link", { name: /^Room details/ }).waitFor();
	pass("loading state persists until actual intercepted response");
	assert.equal(writes, 0);
	const content = await page.locator("main").innerText();
	assert.doesNotMatch(
		content,
		/PayHere available|Payments live|Available now|Earn points|Fully staffless/,
	);
	pass("no automatic writes or misleading commercial claims");
	console.log(
		`PASS ${groups} discovery browser groups; intercepted synthetic presentation only, not backend E2E acceptance`,
	);
} finally {
	await browser?.close();
	const stopped = once(server, "exit");
	server.kill("SIGTERM");
	await stopped;
}

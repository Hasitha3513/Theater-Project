// Local layout/interaction rehearsal with explicitly SYNTHETIC intercepted API responses.
// Backend correctness is separately checked by RoomsIntegrationTest on real PostgreSQL.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import { mkdir } from "node:fs/promises";
import { pathToFileURL } from "node:url";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const child = spawn(
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
let output = "";
child.stdout.on("data", (v) => (output += v));
child.stderr.on("data", (v) => (output += v));
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
	permittedActions: ["venue.read", "venue.write"],
};
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC room",
	description: "Projector and HDMI input",
	capacity: 4,
	enabled: true,
	configured: true,
	version: 0,
	facilities: [{ code: "HDMI", label: "HDMI input", sortOrder: 0 }],
	images: [],
	permittedActions: ["room.read", "room.write"],
	venue,
};
let browser,
	checks = 0;
const pass = (s) => {
	checks++;
	console.log("PASS", s);
};
try {
	for (let n = 0; n < 100; n++) {
		if (child.exitCode !== null) throw Error(output);
		try {
			await fetch(`http://127.0.0.1:${port}`);
			break;
		} catch {
			await new Promise((r) => setTimeout(r, 100));
		}
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage();
	const base = `http://127.0.0.1:${port}`;
	const errors = [];
	page.on("pageerror", (e) => errors.push(e.message));
	let mode = "normal",
		conflict = true;
	let saved;
	await page.route("**/api/**", async (route) => {
		const request = route.request(),
			url = new URL(request.url());
		if (url.pathname === "/api/session/csrf")
			return route.fulfill({ json: { token: "SYNTHETIC-CSRF" } });
		if (mode === "slow") await new Promise((r) => setTimeout(r, 500));
		if (mode === "denied") return route.fulfill({ status: 403, json: {} });
		if (mode === "error") return route.fulfill({ status: 503, json: {} });
		if (request.method() === "PUT") {
			saved = request.postDataJSON();
			if (conflict) {
				conflict = false;
				return route.fulfill({
					status: 409,
					json: { message: "SYNTHETIC conflict" },
				});
			}
			return route.fulfill({ json: { ...room, ...saved, version: 2 } });
		}
		if (url.pathname.endsWith("/venues"))
			return route.fulfill({ json: [venue] });
		if (url.pathname.endsWith(`/rooms/${id}`))
			return route.fulfill({
				json: request.url().includes("/admin/")
					? { ...room, version: 1 }
					: room,
			});
		const match =
			mode !== "empty" &&
			(!url.searchParams.get("q") ||
				"SYNTHETIC"
					.toLowerCase()
					.includes(url.searchParams.get("q").toLowerCase()));
		return route.fulfill({
			json: {
				items: match ? [room] : [],
				total: match ? 1 : 0,
				page: 0,
				size: 24,
				permittedActions: ["room.read", "room.write"],
			},
		});
	});
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		for (const path of [
			"/rooms",
			`/rooms/${id}`,
			"/admin/venues",
			"/admin/rooms",
		]) {
			await page.goto(base + path);
			await page.getByRole("heading", { level: 1 }).waitFor();
			if (path === "/admin/rooms") {
				await page.getByLabel("Assigned venue ID").fill(id);
				await page
					.getByRole("heading", { name: "SYNTHETIC room", exact: true })
					.waitFor();
			}
			await page.waitForTimeout(80);
			assert.ok(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth + 1,
				),
				`overflow ${width} ${path}`,
			);
		}
		pass(`four routes reflow at ${width}px`);
	}
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.goto(`${base}/admin/rooms`);
	await page.getByLabel("Assigned venue ID").fill(id);
	await page
		.getByRole("heading", { name: "SYNTHETIC room", exact: true })
		.waitFor();
	const cards = await page
		.locator("[data-room-id]")
		.evaluateAll((nodes) => nodes.map((n) => n.dataset.roomId));
	await page.getByRole("button", { name: "Show table" }).click();
	assert.deepEqual(
		await page
			.locator("[data-room-id]")
			.evaluateAll((nodes) => nodes.map((n) => n.dataset.roomId)),
		cards,
	);
	assert.equal(await page.getByLabel("Assigned venue ID").inputValue(), id);
	pass("card/table share IDs and preserve scope/filter state");
	await page.getByLabel("Name or code").fill("missing");
	await page.getByRole("heading", { name: "No matching rooms" }).waitFor();
	await page.getByRole("button", { name: "Reset filters" }).click();
	await page.getByRole("button", { name: "Edit room", exact: true }).waitFor();
	pass("filter empty and reset recovery");
	await page.getByRole("button", { name: "Edit room", exact: true }).click();
	await page.getByRole("dialog").waitFor();
	await page
		.getByLabel("Name", { exact: true })
		.fill("SYNTHETIC reviewed draft");
	await page.getByRole("button", { name: "Save room", exact: true }).click();
	await page.getByRole("heading", { name: "Configuration conflict" }).waitFor();
	assert.equal(
		await page.getByLabel("Name", { exact: true }).inputValue(),
		"SYNTHETIC reviewed draft",
	);
	await page
		.getByRole("button", { name: "Load latest for comparison" })
		.click();
	await page
		.getByRole("heading", { name: /Latest saved configuration/ })
		.waitFor();
	await page
		.getByRole("button", { name: "Use latest version with my reviewed draft" })
		.click();
	await page.getByRole("button", { name: "Save room", exact: true }).click();
	await page.getByRole("dialog").waitFor({ state: "hidden" });
	assert.equal(saved.expectedVersion, 1);
	assert.equal(saved.name, "SYNTHETIC reviewed draft");
	pass("409 preserves draft, explicit compare/version retry succeeds");
	await page.getByRole("button", { name: "Edit room", exact: true }).click();
	for (let i = 0; i < 18; i++) {
		await page.keyboard.press("Tab");
		assert.ok(
			await page
				.getByRole("dialog")
				.evaluate((d) => d.contains(document.activeElement)),
		);
	}
	await page.keyboard.press("Escape");
	await page.getByRole("dialog").waitFor({ state: "hidden" });
	assert.match(
		await page.evaluate(() => document.activeElement.textContent),
		/Edit room/,
	);
	pass("editor keyboard containment, Escape and focus restoration");
	mode = "slow";
	await page.goto(`${base}/rooms`);
	await page.getByRole("heading", { name: "Loading", exact: true }).waitFor();
	await page
		.getByRole("heading", { name: "SYNTHETIC room", exact: true })
		.waitFor();
	pass("loading visible before synthetic result");
	mode = "denied";
	await page.goto(`${base}/admin/venues`);
	await page.getByRole("heading", { name: "Access denied" }).waitFor();
	pass("denied state hides protected records");
	mode = "error";
	await page.goto(`${base}/rooms`);
	await page.getByRole("heading", { name: "Service unavailable" }).waitFor();
	mode = "normal";
	await page.getByRole("button", { name: "Retry" }).click();
	await page
		.getByRole("heading", { name: "SYNTHETIC room", exact: true })
		.waitFor();
	pass("service failure and retry recovery");
	await page.goto(`${base}/rooms/${id}`);
	await page.getByText("Monday 10:10–24:00").waitFor();
	assert.ok((await page.getByText("Room photograph unavailable").count()) > 0);
	assert.equal(
		await page.getByRole("button", { name: /book|pay|unlock/i }).count(),
		0,
	);
	pass("local midnight, image fallback and no availability/entry actions");
	await page.goto(`${base}/admin/venues`);
	await page.getByRole("button", { name: "Edit venue", exact: true }).click();
	await page.getByLabel("Cleaning buffer (minutes)").waitFor();
	assert.equal(
		await page.getByLabel("Cleaning buffer (minutes)").inputValue(),
		"7",
	);
	await page.keyboard.press("Escape");
	pass("venue windows/buffer editor reflects explicit supplied config");
	await page.emulateMedia({ reducedMotion: "reduce" });
	await page.goto(`${base}/rooms`);
	await page
		.getByRole("heading", { name: "SYNTHETIC room", exact: true })
		.waitFor();
	await mkdir("../.cache/room-browser", { recursive: true });
	await page.screenshot({
		path: "../.cache/room-browser/catalog.png",
		fullPage: true,
	});
	await page.setViewportSize({ width: 375, height: 900 });
	await page.goto(`${base}/admin/rooms`);
	await page.getByLabel("Assigned venue ID").fill(id);
	await page.getByRole("button", { name: "Edit room", exact: true }).waitFor();
	assert.ok(
		await page.evaluate(
			() => document.documentElement.scrollWidth <= innerWidth + 1,
		),
		"mobile table overflow",
	);
	await page.screenshot({
		path: "../.cache/room-browser/admin-mobile.png",
		fullPage: true,
	});
	assert.deepEqual(errors, []);
	pass("no browser exceptions; reduced motion; captured desktop/mobile review");
	console.log(
		`PASS ${checks} SYNTHETIC browser groups; no customer or physical acceptance`,
	);
} finally {
	await browser?.close();
	const stopped = once(child, "exit");
	child.kill("SIGTERM");
	await stopped;
}

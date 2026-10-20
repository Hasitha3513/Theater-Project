// Intercepted SYNTHETIC presentation checks; not backend/E2E or acceptance evidence.
import assert from "node:assert/strict";
import { createServer } from "node:net";
import { once } from "node:events";
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
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
const folder = "../.cache/experience-001b/" + "after";
await mkdir(folder, { recursive: true });
const measurements = [];
let groups = 0;
try {
	for (let i = 0; i < 100; i++) {
		if (server.exitCode !== null) throw Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 200));
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [375, 1280])
		for (const [name, path, status] of [
			["customer-unconfigured", "/account", 503],
			[
				"customer-not-found",
				"/rooms/00000000-0000-4000-8000-000000000001",
				404,
			],
			[
				"admin-denied",
				"/admin/bookings?venue=00000000-0000-4000-8000-000000000001",
				403,
			],
			[
				"admin-conflict",
				"/admin/bookings?venue=00000000-0000-4000-8000-000000000001",
				409,
			],
		]) {
			const page = await browser.newPage({ viewport: { width, height: 900 } });
			await page.route("**/api/**", (route) =>
				route.fulfill({
					status,
					json: { message: "PRIVATE TEST ERROR DO NOT DISPLAY" },
				}),
			);
			await page.goto(base + path);
			if (path === "/account")
				await page.getByRole("button", { name: "Check session" }).click();
			await page.locator(".state:not(.state-loading)").first().waitFor();
			const region = page.locator(".state:not(.state-loading)").first();
			assert.equal(await region.getAttribute("role"), "alert");
			assert.equal(await region.getAttribute("aria-atomic"), "true");
			const expected = {
				"customer-unconfigured": "Connection unavailable",
				"customer-not-found": "Item unavailable",
				"admin-denied": "Access denied",
				"admin-conflict": "Information changed",
			};
			assert.equal(await region.getAttribute("aria-label"), expected[name]);
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth > innerWidth,
				),
				false,
			);
			assert.equal(
				(await page.locator("body").innerText()).includes("PRIVATE TEST ERROR"),
				false,
			);
			groups++;
			await page.screenshot({
				path: `${folder}/${name}-${width}.png`,
				fullPage: true,
			});
			measurements.push(
				await page
					.locator(".state:not(.state-loading)")
					.first()
					.evaluate(
						(e, { name, width }) => {
							const b = e.getBoundingClientRect();
							return {
								name,
								width,
								title: e.querySelector("h2").textContent,
								role: e.getAttribute("role"),
								x: b.x,
								y: b.y,
								widthState: b.width,
								height: b.height,
								overflow: document.documentElement.scrollWidth > innerWidth,
								privateError:
									document.body.innerText.includes("PRIVATE TEST ERROR"),
							};
						},
						{ name, width },
					),
			);
			await page.close();
		}
	const page = await browser.newPage({ viewport: { width: 375, height: 900 } });
	let writes = 0,
		status = 503,
		delay = 0;
	await page.route("**/api/**", async (route) => {
		if (route.request().method() !== "GET") writes++;
		if (delay) await new Promise((r) => setTimeout(r, delay));
		return route.fulfill({ status, json: { message: "PRIVATE TEST ERROR" } });
	});
	await page.goto(`${base}/rooms/00000000-0000-4000-8000-000000000001`);
	for (const [code, title] of [
		[400, "Check your details"],
		[401, "Sign-in required"],
		[403, "Access denied"],
		[404, "Item unavailable"],
		[409, "Information changed"],
		[503, "Service unavailable"],
	]) {
		status = code;
		await page.reload();
		await page.getByRole("alert", { name: title, exact: true }).waitFor();
		assert.equal(
			(await page.locator("body").innerText()).includes("PRIVATE TEST ERROR"),
			false,
		);
		groups++;
	}
	status = 503;
	await page.reload();
	const retry = page.getByRole("button", { name: "Retry", exact: true });
	await retry.waitFor();
	await retry.focus();
	assert.notEqual(
		await retry.evaluate((e) => getComputedStyle(e).outlineStyle),
		"none",
	);
	delay = 300;
	await page.keyboard.press("Enter");
	await page.getByRole("status", { name: "Loading", exact: true }).waitFor();
	await page
		.getByRole("alert", { name: "Service unavailable", exact: true })
		.waitFor();
	groups++;
	await page.goto(`${base}/admin/components`);
	const expectedKinds = [
		["empty", "No results", "status"],
		["loading", "Loading", "status"],
		["error", "Request not completed", "alert"],
		["unauthenticated", "Sign-in required", "alert"],
		["denied", "Access denied", "alert"],
		["unconfigured", "Connection unavailable", "alert"],
		["validation", "Check your details", "alert"],
		["not-found", "Item unavailable", "alert"],
		["conflict", "Information changed", "alert"],
		["unavailable", "Service unavailable", "alert"],
	];
	for (const [kind, title, role] of expectedKinds) {
		await page.getByLabel("Preview a synthetic state").selectOption(kind);
		const region = page.getByRole(role, { name: title, exact: true });
		await region.waitFor();
		assert.equal(await region.getAttribute("aria-atomic"), "true");
		assert.equal(await region.getByRole("button").count(), 0);
		groups++;
	}
	assert.equal(writes, 0);
	await page.close();
	console.log(
		"PASS",
		groups,
		"synthetic state/accessibility groups;zero writes",
	);
	await writeFile(
		`${folder}/measurements.json`,
		JSON.stringify(measurements, null, 2),
	);
	console.log(JSON.stringify(measurements));
} finally {
	await browser?.close();
	const stopped = once(server, "exit");
	server.kill("SIGTERM");
	await stopped;
}

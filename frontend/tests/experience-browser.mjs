// SYNTHETIC presentation checks; not real-backend authorization or acceptance.
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
const phase = "after";
let groups = 0;
const folder = `../.cache/experience-001a/${phase}`;
await mkdir(folder, { recursive: true });
const measurements = [];
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
		for (const [name, path] of [
			["customer", "/"],
			["admin", "/admin"],
		]) {
			const page = await browser.newPage({ viewport: { width, height: 900 } });
			await page.goto(base + path);
			assert.equal(await page.locator("main").count(), 1);
			assert.equal(await page.locator("h1").count(), 1);
			assert.equal(
				await page.getByText("Development preview", { exact: true }).count(),
				0,
			);
			assert.match(
				await page.locator("footer").innerText(),
				/test identity verification only/,
			);
			assert.match(
				await page.locator("footer").innerText(),
				/Production operations are not authorized/,
			);
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth > innerWidth,
				),
				false,
			);
			assert.equal(
				await page.locator("aside.sidebar").isVisible(),
				name === "admin" && width >= 1024,
			);
			await page.keyboard.press("Tab");
			assert.equal(
				await page
					.locator(".skip-link")
					.evaluate((e) => e === document.activeElement),
				true,
			);
			assert.notEqual(
				await page
					.locator(".skip-link")
					.evaluate((e) => getComputedStyle(e).outlineStyle),
				"none",
			);
			groups++;
            await page.locator("h1").click();
			await page.screenshot({
				path: `${folder}/${name}-${width}.png`,
				fullPage: true,
			});
			measurements.push(
				await page.evaluate(
					({ name, width }) => {
						const box = (s) => {
							const b = document.querySelector(s)?.getBoundingClientRect();
							return b
								? { x: b.x, y: b.y, width: b.width, height: b.height }
								: null;
						};
						return {
							name,
							width,
							pageHeight: document.documentElement.scrollHeight,
							overflow: document.documentElement.scrollWidth > innerWidth,
							header: box(".topbar"),
							sidebar: box(".sidebar"),
							h1: box("h1"),
							hero: box(".cinema-hero"),
							cards: document.querySelectorAll(".card").length,
							previewOccurrences: (
								document.body.innerText.match(/preview/gi) || []
							).length,
						};
					},
					{ name, width },
				),
			);
			await page.close();
		}
	const page = await browser.newPage({ viewport: { width: 375, height: 900 } });
	let mutations = 0;
	await page.route("**/api/**", (route) => {
		if (route.request().method() !== "GET") mutations++;
		return route.fulfill({ status: 401, json: {} });
	});
	await page.goto(`${base}/admin`);
	await page.getByRole("button", { name: "Open navigation" }).click();
	for (let i = 0; i < 24; i++) {
		await page.keyboard.press("Tab");
		assert.ok(
			await page.evaluate(() =>
				document.querySelector("dialog").contains(document.activeElement),
			),
		);
	}
	await page.keyboard.press("Escape");
	assert.ok(
		await page
			.getByRole("button", { name: "Open navigation" })
			.evaluate((e) => e === document.activeElement),
	);
	await page.getByRole("button", { name: "Open navigation" }).click();
	await page
		.locator("dialog")
		.getByRole("link", { name: "Bookings", exact: true })
		.click();
	await page.waitForURL("**/admin/bookings");
	assert.equal(await page.locator("dialog[open]").count(), 0);
	groups++;
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.goto(
		base +
			"/admin/bookings/venues/00000000-0000-4000-8000-000000000001/00000000-0000-4000-8000-000000000002",
	);
	assert.equal(
		await page.locator('aside a[aria-current="location"]').getAttribute("href"),
		"/admin/bookings",
	);
	assert.equal(
		await page.locator('aside a[href="/admin"][aria-current]').count(),
		0,
	);
	groups++;
	await page.goto(`${base}/account`);
	await page.route("**/api/accounts/me", async (route) => {
		await new Promise((r) => setTimeout(r, 300));
		return route.fulfill({ status: 503, json: {} });
	});
	await page.getByRole("button", { name: "Check session" }).click();
	await page.getByRole("heading", { name: "Loading", exact: true }).waitFor();
	await page
		.getByRole("heading", { name: "Connection unavailable" })
		.waitFor();
	await page.unroute("**/api/accounts/me");
	await page.getByRole("button", { name: "Check session" }).click();
	await page.getByRole("heading", { name: "Sign-in required" }).waitFor();
	groups++;
	for (const [path, label] of [
		["/bank", "DO NOT TRANSFER REAL MONEY"],
		["/verification", "NO REAL IDENTITY DOCUMENTS"],
		["/access", "SYNTHETIC ACCESS TEST — NO DOOR CONTROL"],
	]) {
		await page.goto(base + path);
		await page.getByText(label, { exact: true }).waitFor();
		groups++;
	}
	assert.equal(mutations, 0);
	await page.close();
	console.log(
		"PASS",
		groups,
		"presentation/accessibility groups; no automatic writes",
	);
	await writeFile(
		`${folder}/measurements.json`,
		JSON.stringify(measurements, null, 2),
	);
	console.log(JSON.stringify(measurements));
} finally {
	await browser?.close();
	server.kill("SIGTERM");
	await once(server, "exit");
}

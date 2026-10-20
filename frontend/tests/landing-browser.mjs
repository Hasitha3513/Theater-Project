// Intercepted SYNTHETIC browser APIs; not real banking or backend end-to-end evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir } from "node:fs/promises";
import { createServer } from "node:net";
import { pathToFileURL } from "node:url";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
	id = "00000000-0000-4000-8000-000000000001";
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
let output = "",
	browser,
	groups = 0;
child.stdout.on("data", (b) => (output += b));
child.stderr.on("data", (b) => (output += b));
try {
	for (let n = 0; n < 100; n++) {
		if (child.exitCode !== null) throw new Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 200));
	}
	browser = await chromium.launch({ headless: true });
	await mkdir("../.cache/landing-browser", { recursive: true });

	const venue = {
		venueId: id,
		code: "SYNTHETIC",
		displayName: "SYNTHETIC venue",
		timezone: "Asia/Colombo",
		windows: [],
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
		facilities: [{ code: "HDMI", label: "HDMI", sortOrder: 0 }],
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
	async function harness(width = 1280) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			writes: [],
			reads: [],
			roomStatus: 200,
			empty: false,
			packageStatus: 200,
			delay: false,
			release: null,
		};
		await page.route("**/api/**", async (route) => {
			const request = route.request(),
				path = new URL(request.url()).pathname;
			if (request.method() !== "GET") {
				state.writes.push(path);
				return route.fulfill({ status: 500, json: {} });
			}
			state.reads.push(path);
			if (path === "/api/catalog/venues")
				return route.fulfill({ json: [venue] });
			if (path === "/api/catalog/rooms") {
				if (state.delay) await new Promise((r) => (state.release = r));
				return route.fulfill({
					status: state.roomStatus,
					json:
						state.roomStatus === 200
							? {
									items: state.empty ? [] : [room],
									page: 0,
									size: 24,
									total: state.empty ? 0 : 1,
								}
							: { message: "Synthetic unavailable" },
				});
			}
			if (path === `/api/catalog/rooms/${id}`)
				return route.fulfill({ json: room });
			if (path === `/api/venues/${id}/packages`)
				return route.fulfill({
					status: state.packageStatus,
					json:
						state.packageStatus === 200
							? { items: [pkg], purchasable: false }
							: { message: "Synthetic catalog unavailable" },
				});
			return route.fulfill({
				status: 401,
				json: { message: "Session required" },
			});
		});
		return { page, state };
	}
	for (const width of [320, 375, 768, 1280, 1536]) {
		const { page, state } = await harness(width);
		await page.goto(base);
		assert.equal(await page.locator("main").count(), 1);
		assert.equal(await page.locator("h1").count(), 1);
		assert.ok(
			(await page.textContent("main")).includes("Synthetic bookings only"),
		);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		const primary = page.getByRole("link", {
			name: "Explore Cinema Rooms",
			exact: true,
		});
		await primary.focus();
		assert.equal(
			await primary.evaluate((e) => e.matches(":focus-visible")),
			true,
		);
		assert.equal(
			await primary.evaluate((e) => getComputedStyle(e).color),
			"rgb(255, 255, 255)",
		);
		await page.screenshot({
			path: `../.cache/landing-browser/${width}.png`,
			fullPage: true,
		});
		for (const label of [
			"Explore Cinema Rooms",
			"View Available Packages",
			"Start Booking",
		]) {
			if (new URL(page.url()).pathname !== "/") await page.goto(base);
			const link = page.getByRole("link", { name: label, exact: true });
			assert.equal(await link.getAttribute("href"), "/rooms");
			await link.focus();
			await page.keyboard.press("Enter");
			await page
				.getByRole("heading", { name: "Explore our rooms", exact: true })
				.waitFor();
			await page
				.getByRole("heading", { name: room.name, exact: true })
				.waitFor();
			await page.getByRole("link", { name: /^Room details/ }).click();
			await page
				.getByRole("heading", {
					name: "Indicative published packages",
					exact: true,
				})
				.waitFor();
			await page
				.getByRole("heading", { name: pkg.name, exact: true })
				.waitFor();
			await page
				.getByRole("heading", {
					name: "Check inventory and hold temporarily",
					exact: true,
				})
				.waitFor();
			assert.equal(
				await page
					.getByRole("button", { name: "Edit room", exact: true })
					.count(),
				0,
			);
			assert.equal(state.writes.length, 0);
			groups++;
		}
		await page.close();
	}
	for (const status of [500, 503]) {
		const { page, state } = await harness();
		state.roomStatus = status;
		await page.goto(base);
		await page
			.getByRole("link", { name: "Explore Cinema Rooms", exact: true })
			.click();
		await page.getByRole("button", { name: "Retry", exact: true }).waitFor();
		assert.equal(
			await page.getByRole("heading", { name: room.name, exact: true }).count(),
			0,
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.empty = true;
		await page.goto(base);
		await page
			.getByRole("link", { name: "View Available Packages", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "No matching rooms", exact: true })
			.waitFor();
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.packageStatus = 503;
		await page.goto(base);
		await page
			.getByRole("link", { name: "View Available Packages", exact: true })
			.click();
		await page.getByRole("link", { name: /^Room details/ }).click();
		await page
			.getByText("Package catalog unavailable.", { exact: true })
			.waitFor();
		assert.equal(
			await page.getByRole("heading", { name: pkg.name, exact: true }).count(),
			0,
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.delay = true;
		await page.goto(base);
		await page
			.getByRole("link", { name: "Start Booking", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Loading", exact: false })
			.waitFor();
		assert.ok(state.release);
		state.release();
		await page.getByRole("heading", { name: room.name, exact: true }).waitFor();
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic landing/catalog browser groups; no backend,financial or customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await Promise.race([
		once(child, "exit"),
		new Promise((r) => setTimeout(r, 5000)),
	]);
	if (child.exitCode === null) {
		child.kill("SIGKILL");
		await once(child, "exit");
	}
}

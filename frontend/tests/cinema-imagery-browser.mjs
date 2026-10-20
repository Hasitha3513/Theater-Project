// Actual local generated assets; labelled synthetic intercepted business context.
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
	let writes = 0;
	await page.route("**/api/**", (route) => {
		const request = route.request(),
			p = new URL(request.url()).pathname;
		if (request.method() !== "GET") {
			writes++;
			return route.fulfill({ status: 403, json: {} });
		}
		let data;
		if (p === "/api/catalog/venues" || p === "/api/admin/venues")
			data = [venue];
		else if (
			p === "/api/catalog/rooms" ||
			p === `/api/admin/venues/${id}/rooms`
		)
			data = { items: [room], total: 1, page: 0, size: 24 };
		else if (p === `/api/catalog/rooms/${id}`) data = room;
		else if (p.startsWith(`/api/venues/${id}/packages`))
			data = { items: [pkg], purchasable: false };
		else return route.fulfill({ status: 401, json: {} });
		return route.fulfill({ json: data });
	});
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		for (const path of ["/", "/rooms", "/rooms/" + id, "/admin/rooms"]) {
			await page.goto(base + path);
			if (path === "/admin/rooms")
				await page.getByLabel("Venue", { exact: true }).selectOption(id);
			const figure = page.locator(".cinema-illustration").first();
			await figure.waitFor();
			await figure.scrollIntoViewIfNeeded();
			await figure.locator("img").evaluate((image) => image.decode());
			assert.ok(
				await figure
					.locator("img")
					.evaluate(
						(image) =>
							image.complete &&
							image.naturalWidth > 0 &&
							image.currentSrc.includes("/cinema/"),
					),
			);
			assert.match(
				await figure.locator("img").getAttribute("alt"),
				/AI-generated concept/,
			);
			assert.match(
				await figure.locator("figcaption").innerText(),
				/Not a photograph/,
			);
			const contrast = await figure
				.locator("figcaption")
				.evaluate((element) => {
					const style = getComputedStyle(element);
					const lum = (value) => {
						const rgb = value
							.match(/\d+/g)
							.slice(0, 3)
							.map(Number)
							.map((v) => v / 255)
							.map((v) =>
								v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4,
							);
						return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
					};
					const a = lum(style.color),
						b = lum(style.backgroundColor);
					return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
				});
			assert.ok(contrast >= 4.5, "caption rendered contrast");
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth > innerWidth,
				),
				false,
			);
			const ratio = await figure.evaluate(
				(e) =>
					e.getBoundingClientRect().width / e.getBoundingClientRect().height,
			);
			assert.ok(Math.abs(ratio - 1672 / 941) < 0.03);
		}
		pass(
			"actual WebP loading, alt/provenance, fixed aspect and no overflow on four screens at " +
				width,
		);
	}
	await page.goto(base + "/rooms/" + id);
	await page
		.getByRole("button", { name: "Blue lighting", exact: true })
		.focus();
	await page.keyboard.press("Enter");
	const image = page.locator(".cinema-concept-gallery img");
	await image.evaluate((image) => image.decode());
	assert.match(await image.getAttribute("alt"), /blue ceiling/);
	assert.equal(
		await page
			.getByRole("button", { name: "Blue lighting", exact: true })
			.getAttribute("aria-pressed"),
		"true",
	);
	pass("keyboard blue-concept selection remains illustrative and explicit");
	for (const [label, alt] of [
		["Warm amber", "amber"],
		["Recliner seating", "recliners"],
		["Violet lighting", "violet"],
	]) {
		await page.getByRole("button", { name: label, exact: true }).click();
		await image.evaluate((image) => image.decode());
		assert.match(await image.getAttribute("alt"), new RegExp(alt, "i"));
	}
	pass("all four genuine generated assets load through concept gallery");
	const normal = await page
		.locator(".cinema-illustration")
		.first()
		.evaluate((e) => ({
			w: e.getBoundingClientRect().width,
			h: e.getBoundingClientRect().height,
		}));
	await page.route("**/cinema/**", (route) => route.abort());
	await page.reload();
	await page
		.getByText("Cinema illustration unavailable", { exact: true })
		.waitFor();
	const failed = await page
		.locator(".cinema-illustration")
		.first()
		.evaluate((e) => ({
			w: e.getBoundingClientRect().width,
			h: e.getBoundingClientRect().height,
		}));
	assert.ok(Math.abs(normal.w - failed.w) < 1);
	assert.ok(Math.abs(normal.h - failed.h) < 1);
	assert.equal(await page.locator(".cinema-concept-gallery img").count(), 0);
	assert.match(
		await page.locator(".cinema-illustration figcaption").innerText(),
		/AI-generated illustration/,
	);
	pass(
		"failed image retains ownership notice and reserved geometry without image-induced frame shift",
	);
	assert.equal(writes, 0);
	pass("no financial,identity,booking or room mutations");
	console.log(
		`PASS ${groups} image browser groups; actual local image files,intercepted synthetic business context,not backend E2E acceptance`,
	);
} finally {
	await browser?.close();
	const stopped = once(server, "exit");
	server.kill("SIGTERM");
	await stopped;
}

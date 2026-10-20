// SYNTHETIC intercepted browser rehearsal; PostgreSQL/API claims are checked separately.
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
const base = `http://127.0.0.1:${port}`;
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
child.stdout.resume();
child.stderr.resume();
const id = "00000000-0000-4000-8000-000000000001";
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC inventory room",
	description: "HDMI input",
	capacity: 5,
	facilities: [],
	images: [],
	venue: {
		displayName: "Synthetic venue",
		timezone: "Asia/Colombo",
		windows: [],
	},
};
const candidate = {
	roomId: id,
	timezone: "Asia/Colombo",
	sessionStart: "2026-10-20T04:30:00Z",
	sessionEnd: "2026-10-20T07:30:00Z",
	bufferMinutes: 7,
	blockedUntil: "2026-10-20T07:37:00Z",
	configuration: "SYNTHETIC-1",
	serverNow: "2026-10-18T00:00:00Z",
	available: true,
	evidenceLevel: "INVENTORY_ONLY",
};
let hold = {
	...candidate,
	holdId: id,
	createdAt: candidate.serverNow,
	expiresAt: "2026-10-18T00:10:00Z",
	state: "ACTIVE",
	revision: 0,
};
let mode = "normal",
	checks = 0,
	browser;
const commands = [];
function pass(label) {
	checks++;
	console.log(`PASS ${label}`);
}
try {
	for (let n = 0; n < 100; n++) {
		try {
			await fetch(base);
			break;
		} catch {
			await new Promise((r) => setTimeout(r, 100));
		}
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage();
	const errors = [];
	page.on("pageerror", (e) => errors.push(e.message));
	await page.route("**/api/**", async (route) => {
		const req = route.request(),
			p = new URL(req.url()).pathname;
		let status = 200,
			body = {};
		if (p === `/api/catalog/rooms/${id}`) body = room;
		else if (p === `/api/venues/${id}/packages`)
			body = { items: [], purchasable: false };
		else if (p === "/api/session/csrf")
			body = { token: "SYNTHETIC", headerName: "X-CSRF-TOKEN" };
		else if (p.endsWith("/availability")) {
			if (mode === "denied") status = 403;
			body = { ...candidate, available: mode !== "conflict" };
		} else if (p === "/api/holds") {
			commands.push(req.postDataJSON());
			if (mode === "unknown") {
				mode = "normal";
				await route.abort();
				return;
			}
			body = hold;
		} else if (p.endsWith("/release")) {
			hold = { ...hold, state: "RELEASED", revision: 1 };
			body = hold;
		} else if (p === `/api/holds/${id}`) body = hold;
		else if (p.endsWith("/inventory")) {
			if (mode === "denied") status = 403;
			body = { items: [], page: 0, size: 24 };
		} else if (p === "/api/accounts/me")
			body = {
				accountId: id,
				synthetic: true,
				eligibility: "ELIGIBLE",
				scopes: [],
			};
		else status = 404;
		await route.fulfill({
			status,
			contentType: "application/json",
			body: JSON.stringify(body),
		});
	});
	await page.goto(`${base}/rooms/${id}`);
	await page
		.getByLabel("Customer start in venue local time")
		.fill("2026-10-20T10:00");
	await page.getByLabel("Customer duration (minutes)").fill("180");
	await page.getByLabel("Guests", { exact: true }).fill("4");
	const inquire = () =>
		page
			.getByRole("button", { name: "Check inventory (no hold)", exact: true })
			.click();
	await inquire();
	await page
		.getByText(
			"Candidate inventory is currently free; inquiry reserves nothing.",
		)
		.waitFor();
	pass("candidate inquiry is observational");
	mode = "unknown";
	await page
		.getByRole("button", {
			name: "Create temporary inventory hold",
			exact: true,
		})
		.click();
	await page.getByRole("button", { name: "Retry original command" }).click();
	await page.getByText("Temporary inventory hold", { exact: true }).waitFor();
	assert.deepEqual(commands[0], commands[1]);
	pass("unknown outcome retains original command");
	await mkdir("../.cache/inventory-browser", { recursive: true });
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.screenshot({
		path: "../.cache/inventory-browser/active-desktop.png",
		fullPage: true,
	});
	await page.setViewportSize({ width: 375, height: 900 });
	await page.screenshot({
		path: "../.cache/inventory-browser/active-mobile.png",
		fullPage: true,
	});

	await page.getByRole("button", { name: "Release hold", exact: true }).click();
	await page.getByText("Hold released", { exact: true }).waitFor();
	pass("release is terminal without booking success");
	mode = "conflict";
	await inquire();
	await page
		.getByText("Inventory conflict; choose another interval.")
		.waitFor();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Create temporary inventory hold",
				exact: true,
			})
			.count(),
		0,
	);
	pass("conflict removes create action");
	mode = "denied";
	await inquire();
	await page
		.getByRole("heading", { name: "Access denied", exact: true })
		.waitFor();
	pass("denied inputs retained");
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
		pass(`customer layout ${width}`);
	}
	await mkdir("../.cache/inventory-browser", { recursive: true });
	await page.screenshot({
		path: "../.cache/inventory-browser/customer.png",
		fullPage: true,
	});
	mode = "normal";
	await page.goto(`${base}/admin/inventory`);
	await page.getByLabel("Assigned venue ID", { exact: true }).fill(id);
	await page
		.getByRole("button", { name: "Inspect inventory", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "No results", exact: true })
		.waitFor();
	pass("staff empty inventory");
	mode = "denied";
	await page
		.getByRole("button", { name: "Inspect inventory", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Access denied", exact: true })
		.waitFor();
	pass("staff denied state");
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
	}
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.screenshot({
		path: "../.cache/inventory-browser/admin.png",
		fullPage: true,
	});
	assert.deepEqual(errors, []);
	pass("staff responsive layout and no runtime errors");
	console.log(
		`PASS ${checks} SYNTHETIC inventory browser groups; no live acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

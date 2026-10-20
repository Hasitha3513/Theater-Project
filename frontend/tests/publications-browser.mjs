// Explicit SYNTHETIC browser rehearsal; actual PostgreSQL/API guarantees are tested separately.
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
let output = "";
child.stdout.on("data", (v) => (output += v));
child.stderr.on("data", (v) => (output += v));
const id = "00000000-0000-4000-8000-000000000001",
	room = "00000000-0000-4000-8000-000000000002";
const draft = {
	packageId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC package",
	description: "Neutral equipment",
	revision: 0,
	basePriceMinor: "12345",
	includedMinutes: 180,
	includedGuests: 4,
	maximumGuests: 5,
	options: [],
	status: "DRAFT",
};
const snapshot = {
	...draft,
	priceLabel: "INDICATIVE",
	taxStatus: "UNRESOLVED",
	feeStatus: "UNRESOLVED",
	purchasable: false,
};
let records = [],
	mode = "normal",
	published = false,
	checks = 0,
	browser;
const pass = (x) => {
	checks++;
	console.log(`PASS ${x}`);
};
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
		const u = new URL(route.request().url()),
			p = u.pathname;
		let status = 200,
			body = {};
		if (p === "/api/accounts/me")
			body = {
				accountId: id,
				synthetic: true,
				eligibility: "ELIGIBLE",
				scopes: [],
			};
		else if (p === "/api/session/csrf")
			body = { token: "SYNTHETIC-CSRF", headerName: "X-CSRF-TOKEN" };
		else if (p.endsWith("package-options"))
			body = {
				services: [],
				constraintStatus: "CONFIGURED",
				constraints: null,
			};
		else if (p.endsWith("publication-preview")) {
			if (mode === "denied") {
				status = 403;
				body = { message: "Independent publisher required" };
			} else
				body = {
					contentHash: "SYNTHETIC-HASH",
					timezone: "Asia/Colombo",
					salesFrom: "2026-10-16T03:30:00Z",
					salesTo: "2026-11-16T03:30:00Z",
					sessionFrom: "2026-10-17T03:30:00Z",
					sessionTo: "2026-11-17T03:30:00Z",
					snapshot,
				};
		} else if (
			p.endsWith("/publications") &&
			route.request().method() === "POST"
		) {
			if (mode === "conflict") {
				status = 409;
				body = { message: "Publication review changed; preview again" };
			} else {
				published = true;
				records = [
					{
						publicationId: id,
						versionId: id,
						revision: 0,
						versionNo: 1,
						state: "ACTIVE",
						timezone: "Asia/Colombo",
						salesFrom: "2026-10-16T03:30:00Z",
						salesTo: "2026-11-16T03:30:00Z",
						sessionFrom: "2026-10-17T03:30:00Z",
						sessionTo: "2026-11-17T03:30:00Z",
						snapshot,
					},
				];
				body = records[0];
			}
		} else if (p.endsWith("/retry")) {
			records[0] = {
				...records[0],
				state: "SCHEDULED",
				revision: 3,
				failureReason: null,
			};
			body = records[0];
		} else if (p.endsWith("/deactivate")) {
			records[0] = { ...records[0], state: "INACTIVE", revision: 1 };
			published = false;
			body = records[0];
		} else if (p.endsWith("/publications")) {
			if (mode === "service") {
				status = 503;
				body = { message: "Service unavailable" };
			} else
				body = {
					items: records,
					permittedActions: ["package.publish", "package.deactivate"],
				};
		} else if (p.startsWith("/api/admin/") && p.endsWith("/packages"))
			body = {
				items: [draft],
				total: 1,
				page: 0,
				size: 24,
				permittedActions: ["package.read"],
			};
		else if (p.startsWith("/api/venues/"))
			body = {
				items: published ? [{ ...snapshot, versionId: id }] : [],
				purchasable: false,
			};
		else if (p.startsWith("/api/catalog/rooms/"))
			body = {
				roomId: room,
				venueId: id,
				code: "SYNTHETIC",
				name: "SYNTHETIC room",
				description: "Neutral",
				capacity: 5,
				facilities: [],
				images: [],
				configured: true,
				venue: {
					displayName: "SYNTHETIC venue",
					timezone: "Asia/Colombo",
					windows: [],
				},
			};
		else {
			status = 404;
			body = { message: "SYNTHETIC endpoint unavailable" };
		}
		await route.fulfill({
			status,
			contentType: "application/json",
			body: JSON.stringify(body),
		});
	});
	await page.goto(`${base}/admin/settings/packages`);
	await page.getByLabel("Assigned venue ID").fill(id);
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page.getByText("No published or scheduled versions.").waitFor();
	pass("synthetic empty publication history");
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		pass(`publication layout ${width}`);
	}
	for (const key of ["salesFrom", "salesTo", "sessionFrom", "sessionTo"])
		await page
			.getByLabel(
				`${{ salesFrom: "Sales start", salesTo: "Sales end (exclusive)", sessionFrom: "Session validity start", sessionTo: "Session validity end (exclusive)" }[key]} — venue local`,
			)
			.fill(key.endsWith("From") ? "2026-10-17T09:00" : "2026-11-17T09:00");
	await page
		.getByLabel("Publication reason", { exact: true })
		.fill("SYNTHETIC independent review");
	mode = "denied";
	await page
		.getByRole("button", { name: "Review publication SYNTHETIC", exact: true })
		.click();
	await page
		.getByText("Independent publisher required", { exact: true })
		.waitFor();
	pass("denied publisher remains unpublished");
	mode = "normal";
	await page
		.getByRole("button", { name: "Review publication SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("button", {
			name: "Approve reviewed publication SYNTHETIC",
			exact: true,
		})
		.waitFor();
	pass("review exposes timezone and indicative scope");
	mode = "conflict";
	await page
		.getByRole("button", {
			name: "Approve reviewed publication SYNTHETIC",
			exact: true,
		})
		.click();
	await page
		.getByText("Publication review changed; preview again", { exact: true })
		.waitFor();
	pass("conflict preserves review without false success");
	mode = "normal";
	await page
		.getByRole("button", {
			name: "Approve reviewed publication SYNTHETIC",
			exact: true,
		})
		.click();
	await page
		.getByRole("heading", { name: "Version 1 · ACTIVE", exact: true })
		.waitFor();
	pass("synthetic publish and version history");
	await page.goto(`${base}/rooms/${room}`);
	await page
		.getByRole("button", {
			name: "Package details SYNTHETIC package",
			exact: true,
		})
		.click();
	await page
		.getByText("Indicative only · Purchasing is unavailable.", { exact: true })
		.waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: /Book now|Pay now|Checkout/ })
			.count(),
		0,
	);
	pass("public detail remains indicative without checkout");
	await page.goto(`${base}/admin/settings/packages`);
	await page.getByLabel("Assigned venue ID").fill(id);
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page.getByLabel("State-change reason").fill("SYNTHETIC withdrawal");
	await page
		.getByRole("button", { name: "Withdraw publication 1", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Version 1 · INACTIVE", exact: true })
		.waitFor();
	pass("withdraw retains version history");
	records[0] = {
		...records[0],
		state: "BLOCKED",
		revision: 2,
		failureReason: "ACTIVATION: SYNTHETIC authority restored",
	};
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Version 1 · BLOCKED", exact: true })
		.waitFor();
	await page
		.getByLabel("State-change reason")
		.fill("SYNTHETIC explicit recovery");
	await page
		.getByRole("button", { name: "Revalidate activation 1", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Version 1 · SCHEDULED", exact: true })
		.waitFor();
	pass(
		"failed activation requires explicit revalidation and shows scheduled state",
	);
	mode = "service";
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Service unavailable", exact: true })
		.waitFor();
	mode = "normal";
	await page
		.getByRole("button", { name: "Reload publications", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Version 1 · SCHEDULED", exact: true })
		.waitFor();
	pass("service failure recovers without false activation");
	await page.setViewportSize({ width: 375, height: 900 });
	await page.emulateMedia({ reducedMotion: "reduce" });
	await page.keyboard.press("Tab");
	assert.ok(
		await page.evaluate(() => document.activeElement !== document.body),
	);
	await mkdir("../.cache/publication-browser", { recursive: true });
	await page.screenshot({
		path: "../.cache/publication-browser/mobile.png",
		fullPage: true,
	});
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.screenshot({
		path: "../.cache/publication-browser/desktop.png",
		fullPage: true,
	});
	assert.deepEqual(errors, []);
	pass("keyboard/reduced motion and no browser exceptions");
	console.log(`PASS ${checks} SYNTHETIC groups; no customer acceptance`);
} finally {
	await browser?.close();
	const stopped = once(child, "exit");
	child.kill("SIGTERM");
	await stopped;
}

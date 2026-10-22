// Local layout/interaction rehearsal with explicitly SYNTHETIC intercepted API responses.
// Backend correctness is separately checked by RoomsIntegrationTest on real PostgreSQL.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
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
let browser;
let checks = 0;
const id = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002";
try {
	for (let attempt = 0; attempt < 100; attempt++) {
		try {
			const response = await fetch(`http://127.0.0.1:${port}/admin/accounts`);
			if (response.ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let state = "accounts";
		let requests = 0;
		await page.route("**/api/admin/venues/**", async (route) => {
			requests++;
			if (state === "denied") return route.fulfill({ status: 403 });
			if (state === "empty")
				return route.fulfill({
					json: { items: [], nextCursor: null, synthetic: true },
				});
			const path = new URL(route.request().url()).pathname;
			const row = path.includes("audit")
				? {
						eventId: id,
						source: "IDENTITY",
						occurredAt: "2026-10-17T00:00:00Z",
						venueId: id,
						action: "ACCOUNTS_LIST",
						outcome: "ALLOW",
						reasonCode: "SCOPED_READ",
						correlationId: other,
						actorId: id,
						resourceId: null,
					}
				: {
						accountId: id,
						synthetic: true,
						eligibility: "SUSPENDED",
						revision: 0,
						venueId: id,
					};
			const detail = path.endsWith(`/${id}`) && !path.endsWith(`venues/${id}`);
			return route.fulfill({
				json: detail
					? row
					: { items: [row], nextCursor: "SYNTHETIC_CURSOR", synthetic: true },
			});
		});
		await page.goto(`http://127.0.0.1:${port}/admin/accounts`);
		await page.getByLabel("Venue ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Load records", exact: true })
			.click();
		await page.getByRole("cell", { name: "SUSPENDED", exact: true }).waitFor();
		checks++;
		await page
			.locator("section.card")
			.filter({
				has: page.getByRole("heading", {
					name: "Scoped accounts and audit",
					exact: true,
				}),
			})
			.getByRole("button", { name: "Next page", exact: true })
			.click();
		await page.getByRole("cell", { name: "SUSPENDED", exact: true }).waitFor();
		assert.ok(requests >= 2);
		checks++;
		await page
			.getByRole("button", { name: "View detail", exact: true })
			.click();
		await page.getByRole("region", { name: "Restricted detail" }).waitFor();
		checks++;
		await page.getByLabel("Venue ID", { exact: true }).fill(other);
		assert.equal(
			await page.getByRole("region", { name: "Restricted detail" }).count(),
			0,
		);
		checks++;
		state = "empty";
		await page
			.getByRole("button", { name: "Load records", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "No visible records" })
			.waitFor();
		checks++;
		await page.getByRole("combobox", { name: /^View/ }).selectOption("audit");
		await page
			.getByLabel("From UTC", { exact: true })
			.fill("2026-10-17T00:00:00Z");
		await page
			.getByLabel("To UTC (exclusive)", { exact: true })
			.fill("2026-10-18T00:00:00Z");
		state = "accounts";
		await page
			.getByRole("button", { name: "Load records", exact: true })
			.click();
		await page
			.getByRole("cell", { name: "ACCOUNTS_LIST", exact: true })
			.waitFor();
		checks++;
		state = "denied";
		await page
			.getByRole("button", { name: "Load records", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "Access denied" })
			.waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "View detail", exact: true })
				.count(),
			0,
		);
		checks++;
		await page.getByLabel("Venue ID", { exact: true }).focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page
				.getByRole("combobox", { name: /^View/ })
				.evaluate((el) => el === document.activeElement),
			true,
		);
		checks++;
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		checks++;
		if (width === 375 || width === 1280)
			await page.screenshot({
				path: `/tmp/cinenest-admin-${width}.png`,
				fullPage: true,
			});
		await page.close();
	}
	console.log(
		`PASS ${checks} SYNTHETIC browser groups; isolated fixtures, no production/customer acceptance`,
	);
} finally {
	await browser?.close();
	const stopped = once(child, "exit");
	child.kill("SIGTERM");
	await stopped;
}

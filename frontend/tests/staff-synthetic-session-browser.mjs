// Intercepted UI qualification. PostgreSQL HTTP qualification is recorded separately.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { pathToFileURL } from "node:url";
import {
	item,
	detailRecord,
	id,
	venue,
	other,
} from "./staff-bookings-fixtures.mjs";
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
	{ stdio: "ignore" },
);
let browser,
	groups = 0;
try {
	for (let n = 0; n < 150; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [375, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const data = {
			bookingId: id,
			venueId: venue,
			roomId: other,
			bookingRevision: 1,
			rosterRevision: 0,
			startAvailable: true,
			checkoutAvailable: true,
			synthetic: true,
			inventoryChanged: false,
			physicalOccupancy: "UNKNOWN",
			physicalReadiness: "UNVERIFIED",
			checkIn: { actorId: other, commandKey: id },
			events: [],
		};
		let failure = true,
			denied = false;
		const commands = [];
		await page.route("**/api/**", async (route) => {
			const r = route.request(),
				p = new URL(r.url()).pathname;
			if (p === "/api/session/csrf")
				return route.fulfill({ json: { token: "synthetic" } });
			if (p.endsWith("/session"))
				return route.fulfill({ status: denied ? 403 : 200, json: data });
			if (p.includes("/session/cleaning-handoff/"))
				return route.fulfill({ json: { status: "PENDING" } });
			if (p.includes("/session/")) {
				assert.equal(r.headers()["x-csrf-token"], "synthetic");
				const b = r.postDataJSON();
				if (p.endsWith("/cleaning-handoff"))
					return route.fulfill({ json: { status: "RECORDED" } });
				commands.push(b);
				if (failure) {
					failure = false;
					return route.fulfill({
						status: 503,
						json: { message: "PRIVATE_FAILURE" },
					});
				}
				const event = {
					...item(),
					eventId: crypto.randomUUID(),
					actorId: other,
					bookingRevision: 1,
					eventType: p.endsWith("/starts") ? "START" : "CHECKOUT",
					recordedAt: item().scheduledStart,
					lateCheckout: false,
				};
				data.events.push(event);
				return route.fulfill({ json: event });
			}
			if (p.endsWith(`/bookings/${id}`))
				return route.fulfill({ json: detailRecord() });
			return route.fulfill({ status: 503, json: {} });
		});
		await page.goto(`${base}/admin/bookings/venues/${venue}/${id}`);
		const panel = page.getByRole("region", {
			name: "Synthetic session operations",
		});
		const start = panel.getByRole("button", {
			name: "Record synthetic START",
			exact: true,
		});
		await start.focus();
		await page.keyboard.press("Enter");
		await panel.getByText(/original command key is retained/).waitFor();
		assert.equal(await start.count(), 0);
		assert.doesNotMatch(await panel.innerText(), /PRIVATE_FAILURE/);
		groups++;
		await panel.getByRole("button", { name: "Reload session history" }).click();
		await start.click();
		await panel
			.getByRole("button", { name: "Record synthetic CHECKOUT" })
			.waitFor();
		assert.deepEqual(commands[0], commands[1]);
		groups++;
		await panel
			.getByRole("button", { name: "Record synthetic CHECKOUT" })
			.click();
		await panel
			.getByRole("button", { name: "Create or recover cleaning handoff" })
			.click();
		await panel.getByText(/Cleaning handoff recorded/).waitFor();
		assert.match(await panel.innerText(), /readiness UNVERIFIED/);
		groups++;
		denied = true;
		await panel.getByRole("button", { name: "Reload session history" }).click();
		await panel.getByText(/Session unavailable/).waitFor();
		assert.equal(await panel.locator("time").count(), 0);
		groups++;
		await page.close();
	}
	console.log(`PASS ${groups} intercepted session browser groups`);
} finally {
	if (browser) await browser.close();
	if (child.exitCode === null) {
		child.kill("SIGTERM");
		await once(child, "exit");
	}
}

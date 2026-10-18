// Intercepted synthetic UI rehearsal only; no hardware or customer acceptance.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
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
	{ stdio: "ignore" },
);
let browser;
let groups = 0;
try {
	for (let n = 0; n < 200; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 199) throw Error("No isolated UI server");
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			deny = false;
		const calls = [];
		const d = {
			device_id: id,
			venue_id: id,
			room_id: id,
			revision: 1,
			online: true,
			enabled: true,
			desired: null,
			observed: "UNKNOWN",
			observationFresh: false,
			observed_at: null,
			observation_sequence: 0,
			commands: [],
			audit: [],
			permittedActions: ["command", "simulate", "reconcile"],
			synthetic: true,
			equipmentControlled: false,
			physicalOccupancy: "UNKNOWN",
			physicalReadiness: "UNVERIFIED",
		};
		await page.route("**/api/**", async (route) => {
			const p = new URL(route.request().url()).pathname;
			if (p === "/api/accounts/me")
				return route.fulfill({ status: 401, body: "{}" });
			if (p === "/api/session/csrf")
				return route.fulfill({
					json: { token: "synthetic", headerName: "X-CSRF-TOKEN" },
				});
			if (!p.includes("/iot/"))
				return route.fulfill({ status: 404, body: "{}" });
			calls.push(p);
			if (deny) return route.fulfill({ status: 403, json: {} });
			if (mode === "UNCONFIGURED")
				return route.fulfill({ status: 503, json: {} });
			if (mode === "CONFLICT") return route.fulfill({ status: 409, json: {} });
			if (route.request().method() === "POST") {
				const b = route.request().postDataJSON();
				assert.equal(b.bookingId, id);
				assert.equal(b.bookingRevision, 1);
				if (p.endsWith("/commands")) {
					d.desired = b.desired;
					d.revision++;
					d.commands = [
						{
							id,
							booking_id: id,
							booking_revision: 1,
							state: "PENDING",
							revision: 1,
							correlation_id: id,
							desired: b.desired,
							expires_at: "2026-10-20T10:01:00Z",
							starts_at: "2026-10-20T10:00:00Z",
							ends_at: "2026-10-20T11:00:00Z",
							timezone: "Asia/Colombo",
						},
					];
				} else {
					d.commands[0].state = b.outcome;
					d.commands[0].revision++;
				}
				return route.fulfill({ json: d });
			}
			return route.fulfill({
				json: p.endsWith("/devices")
					? { items: mode === "EMPTY" ? [] : [d] }
					: d,
			});
		});
		await page.goto(`${base}/admin/iot`);
		await page
			.getByRole("heading", {
				name: "Synthetic lighting simulator",
				exact: true,
			})
			.waitFor();
		assert(
			await page
				.getByText("SYNTHETIC IOT TEST — NO REAL EQUIPMENT CONTROL", {
					exact: true,
				})
				.isVisible(),
		);
		await page.getByLabel("Authorized venue UUID").fill(id);
		await page
			.getByRole("button", { name: "Load devices", exact: true })
			.click();
		await page
			.getByRole("button", { name: "View device", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.waitFor();
		groups++;
		await page.getByLabel("Confirmed synthetic booking UUID").fill(id);
		await page
			.getByRole("button", { name: "Request simulated state", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Record simulator outcome", exact: true })
			.waitFor();
		await page
			.getByLabel("Controlled simulator outcome")
			.selectOption("UNKNOWN");
		await page
			.getByRole("button", { name: "Record simulator outcome", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Reconcile unknown outcome", exact: true })
			.waitFor();
		assert(await page.getByText(/Desired ON · Observed UNKNOWN/).isVisible());
		groups++;
		await page
			.getByLabel("Controlled simulator outcome")
			.selectOption("ACKNOWLEDGED");
		await page
			.getByRole("button", { name: "Reconcile unknown outcome", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "ACKNOWLEDGED", exact: true })
			.waitFor();
		assert(await page.getByText(/Desired ON · Observed UNKNOWN/).isVisible());
		groups++;
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		if (width === 375 || width === 1280)
			await page.screenshot({
				path: `../.cache/iot-${width}.png`,
				fullPage: true,
			});
		assert(
			await page
				.getByRole("status")
				.evaluate((e) => e === document.activeElement),
		);
		mode = "CONFLICT";
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page.getByRole("status").filter({ hasText: "Conflict" }).waitFor();
		mode = "UNCONFIGURED";
		await page
			.getByRole("button", { name: "Load devices", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "Unconfigured" })
			.waitFor();
		groups++;
		mode = "EMPTY";
		await page
			.getByRole("button", { name: "Load devices", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "No synthetic devices" })
			.waitFor();
		groups++;
		mode = "OK";
		await page
			.getByRole("button", { name: "Load devices", exact: true })
			.click();
		await page
			.getByRole("button", { name: "View device", exact: true })
			.click();
		deny = true;
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page.getByRole("status").filter({ hasText: "Denied" }).waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "Request simulated state", exact: true })
				.count(),
			0,
		);
		groups++;
		await page.getByLabel("Authorized venue UUID").focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page
				.getByRole("button", { name: "Load devices", exact: true })
				.evaluate((e) => e === document.activeElement),
			true,
		);
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		assert(calls.every((p) => !p.includes("unlock")));
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} synthetic IoT browser check groups;7 widths;not physical/customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

// Intercepted synthetic UI rehearsal only; not delivery, bank, provider or physical evidence.
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
	id = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002";
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
try {
	for (let i = 0; i < 200; i++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (i === 199) throw Error("No isolated server");
	}
	browser = await chromium.launch({ headless: true });
	let groups = 0;
	for (const width of [1440, 768, 390, 320]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let admin = false,
			denied = false,
			empty = false,
			conflict = false,
			read = false,
			state = "SIMULATOR_ACCEPTED",
			revision = 1;
		const commands = [];
		const row = () => ({
			id,
			kind: "BANK_CLAIM_RECEIVED",
			revision,
			state,
			read_at: read ? "2026-10-18T00:00:00Z" : null,
			expires_at: "2026-10-19T00:00:00Z",
			template_version: 1,
			operation_id: id,
			synthetic: true,
			liveDelivery: false,
			...(admin
				? {
						attemptHistory: [
							{
								attempt_id: id,
								fence: 1,
								outcome: state,
								created_at: "2026-10-18T00:00:00Z",
							},
						],
						history: [
							{
								audit_id: id,
								action: "LOCAL_CAPTURE",
								outcome: state,
								created_at: "2026-10-18T00:00:00Z",
							},
						],
					}
				: {
						message: "Synthetic claim received. Not verified funds.",
						destination: "/bank",
					}),
		});
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				u = new URL(req.url());
			if (u.pathname === "/api/session/csrf")
				return route.fulfill({ json: { token: "synthetic-csrf" } });
			if (denied)
				return route.fulfill({ status: 403, json: { message: "Denied" } });
			if (req.method() === "POST") {
				commands.push({ path: u.pathname, body: req.postDataJSON() });
				if (conflict)
					return route.fulfill({ status: 409, json: { message: "Changed" } });
				revision++;
				if (u.pathname.endsWith("/read")) read = true;
				if (u.pathname.endsWith("/reconciliations"))
					state = "SIMULATOR_ACCEPTED";
				if (u.pathname.endsWith("/retries")) state = "READY";
				return route.fulfill({ json: row() });
			}
			return route.fulfill({
				json: u.pathname.endsWith(`/${id}`)
					? row()
					: {
							items: empty ? [] : [row()],
							unreadCount: read ? 0 : 1,
							unassigned: 1,
							page: Number(u.searchParams.get("page") ?? 0),
							size: 10,
							hasNext: u.searchParams.get("page") !== "1",
						},
			});
		});
		await page.goto(`${base}/notifications`);
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page.getByText("Unread notifications: 1").waitFor();
		await page.getByRole("button", { name: "BANK CLAIM RECEIVED" }).click();
		await page
			.getByText("Synthetic claim received. Not verified funds.")
			.waitFor();
		assert.equal(
			await page
				.getByRole("link", { name: "Open protected workspace" })
				.getAttribute("href"),
			"/bank",
		);
		await page
			.getByRole("button", { name: "Mark as read", exact: true })
			.click();
		await page.getByText("Unread notifications: 0").waitFor();
		assert.ok(commands[0].body.commandKey);
		assert.equal(commands[0].body.expectedRevision, 1);
		groups++;
		await page.getByRole("button", { name: "Next", exact: true }).click();
		await page.getByText("Page 2", { exact: true }).waitFor();
		await page.getByRole("button", { name: "Previous", exact: true }).click();
		groups++;
		empty = true;
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page.getByText("No current notifications available.").waitFor();
		empty = false;
		denied = true;
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page
			.getByRole("status")
			.filter({ hasText: "Notification unavailable" })
			.waitFor();
		assert.equal(
			await page
				.getByText("Synthetic claim received. Not verified funds.")
				.count(),
			0,
		);
		denied = false;
		groups++;
		admin = true;
		state = "DELIVERY_UNKNOWN";
		await page.goto(`${base}/admin/notifications`);
		await page.getByLabel("Authorized venue UUID").fill(id);
		await page.getByRole("button", { name: "Open venue", exact: true }).click();
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page.getByText("Unassigned work: 1").waitFor();
		await page.getByRole("button", { name: "BANK CLAIM RECEIVED" }).click();
		await page
			.getByText("Metadata only. No private message body or credential.")
			.waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "Request safe retry" })
				.isDisabled(),
			true,
		);
		await page
			.getByRole("button", { name: "Reconcile durable evidence" })
			.click();
		await page
			.getByText("Delivery: SIMULATOR_ACCEPTED.", { exact: false })
			.waitFor();
		groups++;
		state = "DELIVERY_FAILED";
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page.getByRole("button", { name: "BANK CLAIM RECEIVED" }).click();
		await page.getByRole("button", { name: "Request safe retry" }).click();
		await page.getByText("Delivery: READY.", { exact: false }).waitFor();
		await page
			.getByLabel("Local capture fixture")
			.selectOption("UNKNOWN_FAILED");
		await Promise.all([
			page.waitForResponse(
				(r) =>
					r.url().endsWith("/simulations") && r.request().method() === "POST",
			),
			page.getByRole("button", { name: "Configure local simulation" }).click(),
		]);
		assert.equal(commands.at(-1).body.fixture, "UNKNOWN_FAILED");
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		groups++;
		conflict = true;
		await Promise.all([
			page.waitForResponse(
				(r) =>
					r.url().endsWith("/simulations") && r.request().method() === "POST",
			),
			page.getByRole("button", { name: "Configure local simulation" }).click(),
		]);
		await page
			.getByRole("status")
			.filter({ hasText: "Notification unavailable" })
			.waitFor();
		assert.equal(
			await page
				.getByText("Metadata only. No private message body or credential.")
				.count(),
			0,
		);
		conflict = false;
		await page.getByRole("button", { name: "Refresh notifications" }).click();
		await page.getByRole("button", { name: "BANK CLAIM RECEIVED" }).click();
		await page.getByLabel("Authorized venue UUID").fill(other);
		assert.equal(
			await page
				.getByText("Metadata only. No private message body or credential.")
				.count(),
			0,
		);
		groups++;
		await page.getByLabel("Authorized venue UUID").focus();
		await page.keyboard.press("Tab");
		assert.ok(
			await page
				.getByRole("button", { name: "Open venue", exact: true })
				.evaluate((el) => el === document.activeElement),
		);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
		);
		assert.equal(
			await page.evaluate(() => localStorage.length + sessionStorage.length),
			0,
		);
		assert.ok(
			(await page.locator("body").innerText()).includes(
				"SYNTHETIC NOTIFICATION TEST — NO REAL DELIVERY",
			),
		);
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} notification browser groups at 1440/768/390/320; intercepted presentation only`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

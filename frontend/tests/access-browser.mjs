// Intercepted synthetic presentation rehearsal, not provider, bank or physical evidence.
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
try {
	for (let n = 0; n < 200; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 199) throw Error("Isolated startup unavailable");
	}
	browser = await chromium.launch({ headless: true });
	let groups = 0;
	for (const width of [1440, 768, 390, 320]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let denied = false,
			conflict = false,
			empty = false,
			admin = false,
			state = "ACTIVE",
			provisioning = "SIMULATOR_INSTALLED",
			revision = 1;
		const commands = [];
		const row = () => ({
			id,
			bookingId: id,
			bookingRevision: 1,
			venueId: id,
			roomId: id,
			revision,
			version: 1,
			state,
			provisioning,
			operationId: id,
			sessionStart: "2026-10-18T07:00:00Z",
			sessionEnd: "2026-10-18T10:00:00Z",
			timezone: "Asia/Colombo",
			synthetic: true,
			doorControlled: false,
			permittedActions: admin
				? state === "REVOKED"
					? []
					: ["SIMULATE", "REVOKE"]
				: state === "CONSUMED"
					? ["REISSUE"]
					: ["REVEAL", "ATTEMPT", "REISSUE"],
			history: [
				{
					id,
					actor: id,
					action: "ISSUE",
					outcome: "ALLOW",
					at: "2026-10-18T07:00:00Z",
				},
			],
		});
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			if (url.pathname === "/api/session/csrf")
				return route.fulfill({ json: { token: "synthetic-csrf" } });
			if (req.method() === "GET") {
				if (denied)
					return route.fulfill({ status: 403, json: { message: "Denied" } });
				return route.fulfill({
					json: admin
						? {
								items: empty ? [] : [row()],
								synthetic: true,
								doorControlled: false,
							}
						: row(),
				});
			}
			const b = req.postDataJSON();
			commands.push(b);
			assert.ok(b.commandKey);
			if (conflict)
				return route.fulfill({ status: 409, json: { message: "Changed" } });
			revision++;
			let result;
			if (url.pathname.endsWith("/reveal"))
				result = { ...row(), token: "SYNTHETIC-PRIVATE-TOKEN" };
			else if (url.pathname.endsWith("/simulated-attempts")) {
				state = "CONSUMED";
				result = row();
			} else if (url.pathname.endsWith("/revocations")) {
				state = "REVOKED";
				result = row();
			} else if (url.pathname.endsWith("/simulations")) {
				assert.equal(b.reconcile, true);
				provisioning = "SIMULATOR_INSTALLED";
				result = row();
			} else {
				state = "SCHEDULED";
				result = row();
			}
			return route.fulfill({ json: result });
		});
		await page.goto(`${base}/access`);
		await page.getByLabel("Booking ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page.getByRole("button", { name: "REVEAL", exact: true }).waitFor();
		groups++;
		await page.getByRole("button", { name: "REVEAL", exact: true }).focus();
		await page.keyboard.press("Enter");
		await page.getByText("SYNTHETIC-PRIVATE-TOKEN", { exact: true }).waitFor();
		assert.equal(
			await page
				.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
				.then((v) => v.includes("SYNTHETIC-PRIVATE-TOKEN")),
			false,
		);
		await page.getByRole("button", { name: "Hide credential" }).click();
		assert.equal(
			await page.getByText("SYNTHETIC-PRIVATE-TOKEN", { exact: true }).count(),
			0,
		);
		groups++;
		await page
			.getByLabel("Synthetic credential for one simulated entry")
			.fill("SYNTHETIC-PRIVATE-TOKEN");
		await page.getByRole("button", { name: "ATTEMPT", exact: true }).click();
		await page.getByText("CONSUMED", { exact: true }).waitFor();
		await page.getByRole("button", { name: "REISSUE", exact: true }).click();
		await page.getByText("SCHEDULED", { exact: true }).waitFor();
		groups++;
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		groups++;
		conflict = true;
		await page.getByRole("button", { name: "REISSUE", exact: true }).click();
		await page.getByRole("status").filter({ hasText: "409:" }).waitFor();
		conflict = false;
		denied = true;
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page.getByRole("status").filter({ hasText: "403:" }).waitFor();
		groups++;
		denied = false;
		admin = true;
		state = "ACTIVE";
		provisioning = "UNKNOWN";
		await page.goto(`${base}/admin/access`);
		await page.getByLabel("Venue ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page.getByRole("button", { name: `Inspect ${id}` }).click();
		await page
			.getByRole("button", { name: "Reconcile simulation", exact: true })
			.click();
		await page.getByRole("button", { name: "SIMULATE", exact: true }).waitFor();
		groups++;
		await page.getByRole("button", { name: "REVOKE", exact: true }).click();
		await page.getByText("REVOKED", { exact: true }).first().waitFor();
		assert.equal(
			await page.getByText("SYNTHETIC-PRIVATE-TOKEN", { exact: true }).count(),
			0,
		);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		groups++;
		empty = true;
		await page
			.getByRole("button", { name: "Refresh status", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "No synthetic credentials" })
			.waitFor();
		groups++;
		assert.ok(commands.length >= 6);
		await page.close();
	}
	console.log(
		`PASS ${groups} intercepted access browser groups across four widths; no actual door/provider evidence`,
	);
} finally {
	if (browser) await browser.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

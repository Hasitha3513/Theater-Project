// Intercepted synthetic browser rehearsal, never physical/customer acceptance.
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
let browser,
	groups = 0;
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
		let mode = "OK";
		const calls = [];
		let c = {
			case_id: id,
			venue_id: id,
			room_id: id,
			state: "OPEN",
			revision: 0,
			synthetic: true,
			checklist_version: "SYNTHETIC_V1",
			checklist: ["SURFACES", "FLOOR"],
			assignee_id: null,
			assignment: "UNASSIGNED",
			physicalOccupancy: "UNKNOWN",
			physicalReadiness: "UNVERIFIED",
			inventoryChanged: false,
			entries: [],
			permittedActions: ["read", "record", "assign", "review"],
		};
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				p = new URL(req.url()).pathname;
			if (p === "/api/session/csrf")
				return route.fulfill({
					json: { token: "synthetic", headerName: "X-CSRF-TOKEN" },
				});
			if (!p.includes("/operations/cases"))
				return route.fulfill({ status: 401, json: {} });
			calls.push(p);
			if (mode === "DENIED") return route.fulfill({ status: 403, json: {} });
			if (mode === "CONFLICT") return route.fulfill({ status: 409, json: {} });
			if (mode === "UNAVAILABLE")
				return route.fulfill({ status: 503, json: {} });
			if (req.method() === "GET")
				return route.fulfill({
					json: p.endsWith("/cases")
						? { items: mode === "EMPTY" ? [] : [c] }
						: c,
				});
			const body = req.postDataJSON();
			assert(body.commandKey);
			assert.equal(body.expectedVersion, c.revision);
			c = { ...c, revision: c.revision + 1 };
			if (p.endsWith("/assignments"))
				c = { ...c, assignee_id: id, assignment: "ASSIGNED" };
			if (p.endsWith("/acknowledgements")) c.state = "ACKNOWLEDGED";
			if (p.endsWith("/completion-assertions")) c.state = "ASSERTED";
			if (p.endsWith("/reviews")) c.state = "RESOLVED";
			if (p.endsWith("/exceptions"))
				c.entries.push({
					entry_id: id,
					kind: "EXCEPTION",
					actor_id: id,
					case_revision: c.revision,
					created_at: new Date().toISOString(),
					data: { category: body.category },
				});
			if (p.endsWith("/exception-resolutions"))
				c.entries.push({
					entry_id: crypto.randomUUID(),
					kind: "RESOLVE_EXCEPTION",
					actor_id: id,
					case_revision: c.revision,
					created_at: new Date().toISOString(),
					data: { entryId: id },
				});
			return route.fulfill({ json: c });
		});
		await page.goto(`${base}/admin/operations`);
		await page
			.getByText("SYNTHETIC OPERATIONAL TEST", { exact: true })
			.waitFor();
		assert.equal(await page.getByRole("main").count(), 1);
		assert.equal(await page.getByRole("heading", { level: 1 }).count(), 1);
		assert.equal(calls.length, 0);
		await page.getByLabel("Venue ID", { exact: true }).focus();
		assert(
			await page
				.getByLabel("Venue ID", { exact: true })
				.evaluate((e) => e === document.activeElement),
		);
		await page.getByLabel("Venue ID", { exact: true }).fill(id);
		await page.getByRole("button", { name: "Load queue", exact: true }).click();
		await page.getByRole("button", { name: /OPEN — UNASSIGNED/ }).click();
		await page
			.getByLabel("Eligible synthetic staff account ID (empty to unassign)")
			.fill(id);
		await page
			.getByRole("button", { name: "Assign staff", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Acknowledge assigned work" })
			.click();
		await page
			.getByRole("button", { name: "Record SURFACES assertion" })
			.click();
		await page.getByRole("button", { name: "Record FLOOR assertion" }).click();
		await page
			.getByRole("button", { name: "Assert synthetic cleaning completion" })
			.click();
		await page
			.getByRole("button", { name: "Record exception", exact: true })
			.click();
		assert(
			await page
				.getByRole("button", { name: "Review and resolve synthetic case" })
				.isDisabled(),
		);
		await page
			.getByRole("button", { name: "Record synthetic exception resolution" })
			.click();
		await page
			.getByRole("button", { name: "Review and resolve synthetic case" })
			.click();
		await page.getByText(/revision 8; RESOLVED/).waitFor();
		groups++;
		assert(
			await page
				.getByText("PHYSICAL READINESS NOT VERIFIED", { exact: true })
				.isVisible(),
		);
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		assert(!calls.some((p) => p.includes("unlock") || p.includes("inventory")));
		groups++;
		for (const m of ["CONFLICT", "DENIED", "UNAVAILABLE", "EMPTY"]) {
			mode = m;
			await page
				.getByRole("button", { name: "Load queue", exact: true })
				.click();
			await page.waitForTimeout(80);
			if (m === "EMPTY") await page.getByText("No cases loaded.").waitFor();
			else
				await page
					.getByRole("status")
					.filter({
						hasText:
							m === "CONFLICT"
								? "Conflict"
								: m === "DENIED"
									? "Denied"
									: "Unavailable",
					})
					.waitFor();
			groups++;
		}
		mode = "OK";
		c = {
			...c,
			state: "OPEN",
			revision: 0,
			entries: [],
			assignee_id: null,
			assignment: "UNASSIGNED",
		};
		await page.getByLabel("Room ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Create synthetic case", exact: true })
			.click();
		await page.getByText(/Checklist SYNTHETIC_V1/).waitFor();
		groups++;
		await page.getByLabel("Venue ID", { exact: true }).fill("");
		assert.equal(
			await page
				.getByRole("button", { name: "Load queue", exact: true })
				.isDisabled(),
			true,
		);
		assert.equal(await page.getByText(/Case 00000000/).count(), 0);
		groups++;
		await page.getByLabel("Filter current queue page").focus();
		assert(
			await page
				.getByLabel("Filter current queue page")
				.evaluate((e) => e === document.activeElement),
		);
		groups++;
		if ([375, 1280].includes(width))
			await page.screenshot({
				path: `../.cache/operations-${width}.png`,
				fullPage: true,
			});
		await page.close();
	}
	console.log(
		`PASS ${groups} synthetic operations browser groups across 7 widths; no physical/customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

// Explicit SYNTHETIC browser rehearsal; actual PostgreSQL/API guarantees are tested separately.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import { mkdir } from "node:fs/promises";
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
	room = "00000000-0000-4000-8000-000000000002",
	service = "00000000-0000-4000-8000-000000000003",
	rate = "00000000-0000-4000-8000-000000000004";
const draft = {
	packageId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC package",
	description: "Neutral equipment",
	currency: "LKR",
	basePriceMinor: "12345",
	includedMinutes: 180,
	minimumMinutes: 180,
	maximumMinutes: 240,
	extensionsEnabled: true,
	extensionStepMinutes: 60,
	extensionPriceMinor: "100",
	includedGuests: 4,
	maximumGuests: 5,
	extraGuestPriceMinor: "25",
	venueWide: false,
	roomIds: [room],
	options: [{ serviceId: service, rateVersionId: rate, mode: "INCLUDED" }],
	revision: 0,
	status: "DRAFT",
	permittedActions: ["package.read", "package.edit"],
};

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
	permittedActions: ["venue.read", "venue.write"],
};
const roomValue = {
	roomId: room,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC room",
	description: "Projector and HDMI input",
	capacity: 5,
	enabled: true,
	configured: true,
	version: 0,
	facilities: [{ code: "HDMI", label: "HDMI input", sortOrder: 0 }],
	images: [],
	permittedActions: ["room.read", "room.write"],
	venue,
};
const latest = {
	...draft,
	revision: 2,
	name: "SYNTHETIC revised package",
	basePriceMinor: "23456",
	privateUnsupported: "UNSUPPORTED_SYNTHETIC_MARKER",
	options: [
		{ ...draft.options[0], privateUnsupported: "UNSUPPORTED_SYNTHETIC_MARKER" },
	],
};
const phase = process.env.CAPTURE_PHASE ?? "after",
	folder = `../.cache/experience-001j/${phase}`;
await mkdir(folder, { recursive: true });
let browser,
	groups = 0;
try {
	for (let n = 0; n < 150; n++) {
		if (child.exitCode !== null) throw Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 149) throw Error("No isolated UI server");
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } }),
			writes = [];
		let mode = "OK";
		await page.route("**/api/**", (r) => {
			const q = r.request(),
				p = new URL(q.url()).pathname;
			if (p === "/api/session/csrf")
				return r.fulfill({ json: { token: "SYNTHETIC-CSRF" } });
			if (mode === "DENIED") return r.fulfill({ status: 403, json: {} });
			if (mode === "EXPIRED") return r.fulfill({ status: 401, json: {} });
			if (mode === "ERROR") return r.fulfill({ status: 503, json: {} });
			if (q.method() !== "GET") {
				writes.push({ path: p, body: q.postDataJSON() });
				return r.fulfill({
					status: 409,
					json: { message: "SYNTHETIC stale revision" },
				});
			}
			if (p.endsWith("/package-options"))
				return r.fulfill({
					json: {
						constraintStatus: "CONFIGURED",
						constraints: {
							venueId: id,
							minimumMinutes: 180,
							policyRevision: 1,
							rooms: [{ roomId: room, capacity: 5, facilities: ["HDMI"] }],
						},
						services: [],
					},
				});
			if (p.endsWith("/history"))
				return r.fulfill({
					json: {
						items: [
							{
								revision: 2,
								snapshot: latest,
								occurredAt: "2026-10-21T00:00:00Z",
							},
							{
								revision: 0,
								snapshot: draft,
								occurredAt: "2026-10-16T00:00:00Z",
							},
						],
						total: 2,
						size: 20,
					},
				});
			if (p.endsWith("/publications"))
				return r.fulfill({
					json: {
						items: [
							{
								publicationId: id,
								versionId: id,
								revision: 1,
								versionNo: 1,
								state: "ACTIVE",
								timezone: "Asia/Colombo",
								salesFrom: "2026-10-16T03:30:00Z",
								salesTo: "2026-11-16T03:30:00Z",
								sessionFrom: "2026-10-17T03:30:00Z",
								sessionTo: "2026-11-17T03:30:00Z",
								snapshot: draft,
							},
						],
						permittedActions: ["package.publish", "package.deactivate"],
					},
				});
			if (p.endsWith(`/packages/${id}`))
				return r.fulfill({ json: writes.length ? latest : draft });
			if (p.endsWith("/packages"))
				return r.fulfill({
					json: {
						items: [draft],
						total: 1,
						size: 24,
						page: 0,
						permittedActions: ["package.read", "package.edit"],
					},
				});
			if (p.endsWith("/venues")) return r.fulfill({ json: [venue] });
			if (p.endsWith(`/rooms/${room}`))
				return r.fulfill({
					json: {
						...roomValue,
						version: 2,
						capacity: 4,
						privateUnsupported: "UNSUPPORTED_SYNTHETIC_MARKER",
					},
				});
			if (p.endsWith("/rooms"))
				return r.fulfill({
					json: {
						items: [roomValue],
						total: 1,
						size: 24,
						page: 0,
						permittedActions: ["room.read", "room.write"],
					},
				});
			return r.fulfill({ status: 404, json: {} });
		});
		async function shot(name) {
			await page.evaluate(() => {
				document.activeElement?.blur();
				window.scrollTo(0, 0);
			});
			if (name.endsWith("comparison"))
				await page
					.getByRole("heading", {
						name: name.startsWith("room")
							? /Latest saved configuration/
							: /Latest revision/,
					})
					.scrollIntoViewIfNeeded();
			await page.waitForTimeout(100);
			assert(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth + 1,
				),
			);
			await page.screenshot({
				path: `${folder}/${name}-${width}.png`,
				fullPage: true,
			});
		}
		await page.goto(`${base}/admin/settings/packages`);
		await page.getByLabel("Assigned venue ID").fill(id);
		await page
			.getByRole("button", { name: "History SYNTHETIC", exact: true })
			.waitFor();
		assert.equal(writes.length, 0);
		await shot("packages");
		await page
			.getByRole("button", { name: "History SYNTHETIC", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Revision 2", exact: true })
			.waitFor();
		await page.getByRole("dialog").locator("summary").first().click();
		if (phase !== "before") {
			assert(
				(await page
					.getByRole("dialog")
					.getByText("Changed", { exact: true })
					.count()) > 0,
			);
			assert.equal(await page.getByRole("dialog").locator("pre").count(), 0);
			assert(
				!(await page.getByRole("dialog").innerText()).includes(
					"UNSUPPORTED_SYNTHETIC_MARKER",
				),
			);
		}
		await shot("history");
		await page
			.getByRole("button", { name: "Close dialog", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
			.click();
		await page.getByLabel("Base price (LKR)").fill("100.01");
		await page
			.getByLabel("Business reason for this change")
			.fill("SYNTHETIC reviewed edit");
		await page
			.getByRole("button", { name: "Save unpublished draft", exact: true })
			.click();
		await page.getByRole("heading", { name: "Draft conflict" }).waitFor();
		await page
			.getByRole("button", { name: "Load latest for comparison" })
			.click();
		await page.getByRole("heading", { name: "Latest revision 2" }).waitFor();
		assert.equal(
			await page.getByLabel("Base price (LKR)").inputValue(),
			"100.01",
		);
		assert.equal(writes.length, 1);
		if (phase !== "before") {
			assert(
				!(await page.getByRole("dialog").innerText()).includes(
					"UNSUPPORTED_SYNTHETIC_MARKER",
				),
			);
			assert(
				(await page
					.getByRole("dialog")
					.getByText("Changed", { exact: true })
					.count()) > 0,
			);
			assert.equal(await page.getByRole("dialog").locator("pre").count(), 0);
		}
		await shot("package-comparison");
		page.once("dialog", (d) => d.accept());
		await page
			.getByRole("button", { name: "Close dialog", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Publications SYNTHETIC", exact: true })
			.click();
		await page.getByRole("heading", { name: "Version 1 · ACTIVE" }).waitFor();
		assert.equal(writes.length, 1);
		await shot("publication");
		await page.goto(`${base}/admin/rooms`);
		await page.getByLabel("Assigned venue ID").fill(id);
		await page
			.getByRole("button", { name: "Edit room", exact: true })
			.waitFor();
		await shot("rooms");
		await page.getByRole("button", { name: "Edit room", exact: true }).click();
		await page
			.getByLabel("Name", { exact: true })
			.fill("SYNTHETIC unsaved room");
		await page.getByRole("button", { name: "Save room", exact: true }).click();
		await page
			.getByRole("heading", { name: "Configuration conflict" })
			.waitFor();
		await page
			.getByRole("button", { name: "Load latest for comparison" })
			.click();
		await page
			.getByRole("heading", { name: /Latest saved configuration/ })
			.waitFor();
		if (phase !== "before") {
			assert.equal(await page.getByRole("dialog").locator("pre").count(), 0);
			assert(
				!(await page.getByRole("dialog").innerText()).includes(
					"UNSUPPORTED_SYNTHETIC_MARKER",
				),
			);
			assert(
				(await page
					.getByRole("dialog")
					.getByText("Changed", { exact: true })
					.count()) > 0,
			);
		}
		assert.equal(
			await page.getByLabel("Name", { exact: true }).inputValue(),
			"SYNTHETIC unsaved room",
		);
		assert.equal(writes.length, 2);
		await shot("room-comparison");
		for (let n = 0; n < 8; n++) {
			await page.keyboard.press("Tab");
			assert(
				await page
					.getByRole("dialog")
					.evaluate((d) => d.contains(document.activeElement)),
			);
		}
		groups += 6;
		page.once("dialog", (d) => d.accept());
		await page.keyboard.press("Escape");
		await page.getByRole("dialog").waitFor({ state: "hidden" });
		for (const [m] of [
			["DENIED", "Access denied"],
			["EXPIRED", "Sign in required"],
			["ERROR", "Request failed"],
		]) {
			mode = m;
			await page.goto(`${base}/admin/settings/packages`);
			await page.getByLabel("Assigned venue ID").fill(id);
			await page.getByRole("alert").first().waitFor();
			assert.equal(
				await page
					.getByRole("button", { name: "Create draft", exact: true })
					.count(),
				0,
			);
			assert.equal(
				await page
					.getByRole("button", { name: /Approve reviewed publication/ })
					.count(),
				0,
			);
			groups++;
		}
		await page.close();
	}
	console.log(
		`PASS ${groups} synthetic configuration/history/publication/denial/keyboard groups; intercepted business APIs,not real backend E2E`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

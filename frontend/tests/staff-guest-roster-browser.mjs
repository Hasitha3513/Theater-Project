// Intercepted synthetic UI tests; not backend authorization or real E2E evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { venue, id, other, detailRecord } from "./staff-bookings-fixtures.mjs";
import { rosterFixture } from "./staff-guest-roster-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
	mode = process.env.CINENEST_ROSTER_VISUAL === "before" ? "before" : "after",
	screenshots = `../.cache/staff-guest-roster/${mode}`;
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
	for (let n = 0; n < 100; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	await mkdir(screenshots, { recursive: true });
	async function harness(width = 1280) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } }),
			state = {
				status: 200,
				data: rosterFixture(),
				delay: 0,
				reads: 0,
				writes: [],
				network: false,
			};
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				path = new URL(req.url()).pathname;
			if (req.method() !== "GET") state.writes.push(path);
			if (path.endsWith("/guest-roster")) {
				state.reads++;
				const snapshot = structuredClone(state.data),
					status = state.status,
					delay = state.delay;
				if (state.network) return route.abort("failed");
				if (delay) await new Promise((r) => setTimeout(r, delay));
				try {
					return await route.fulfill({
						status,
						json:
							status === 200
								? snapshot
								: { message: "PRIVATE_ERROR_NOT_FOR_UI" },
					});
				} catch {
					return;
				}
			}
			if (path.startsWith("/api/admin/venues/") && path.includes("/bookings/"))
				return route.fulfill({ json: detailRecord() });
			return route.fulfill({
				status: 401,
				json: { message: "Session required" },
			});
		});
		return { page, state };
	}
	const href = `${base}/admin/bookings/venues/${venue}/${id}?page=0&size=10`,
		table = { name: "Registered synthetic guests" },
		button = { name: "Reload guest roster", exact: true };
	const loaded = (page) => page.getByRole("table", table).waitFor(),
		alert = (page) =>
			page
				.getByRole("region", { name: "Staff guest roster" })
				.getByRole("alert")
				.waitFor();
	for (const width of [375, 768, 1280]) {
		const { page, state } = await harness(width);
		await page.goto(href);
		await page
			.getByText("SYNTHETIC purchased cinema", { exact: true })
			.waitFor();
		if (mode === "after") {
			await loaded(page);
			assert.match(await page.locator("main").innerText(), /UNVERIFIED/);
			assert.equal(await page.locator("main").count(), 1);
			const reload = page.getByRole("button", button);
			await reload.focus();
			await page.keyboard.press("Enter");
			await loaded(page);
			assert.equal(
				await reload.evaluate((el) => el === document.activeElement),
				true,
			);
			assert.notEqual(
				await reload.evaluate((el) => getComputedStyle(el).outlineStyle),
				"none",
			);
			assert.ok(state.reads >= 2);
			assert.deepEqual(state.writes, []);
		}
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		await page.evaluate(() => window.scrollTo(0, 0));
		await page.screenshot({
			path: `${screenshots}/${width}.png`,
			fullPage: true,
		});
		await page.close();
		groups++;
	}
	if (mode === "after") {
		for (const status of [401, 403, 404, 503]) {
			const { page, state } = await harness();
			state.status = status;
			await page.goto(href);
			await alert(page);
			assert.equal(await page.getByRole("table", table).count(), 0);
			assert.doesNotMatch(
				await page.locator("main").innerText(),
				/PRIVATE_ERROR_NOT_FOR_UI/,
			);
			state.status = 200;
			await page.getByRole("button", button).click();
			await loaded(page);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		for (const change of [
			(r) => ({ ...r, venueId: other }),
			(r) => ({ ...r, bookingId: other }),
			(r) => ({ ...r, count: 99 }),
			(r) => ({ ...r, bookingRevision: 2 }),
		]) {
			const { page, state } = await harness();
			state.data = change(state.data);
			await page.goto(href);
			await alert(page);
			assert.equal(await page.getByRole("table", table).count(), 0);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			state.data = {
				...state.data,
				initialized: false,
				revision: 0,
				entries: [],
				count: 0,
				remaining: 4,
			};
			await page.goto(href);
			await page
				.getByText("No synthetic guest roster has been initialized.", {
					exact: true,
				})
				.waitFor();
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			await page.goto(href);
			await loaded(page);
			state.status = 403;
			await page.getByRole("button", button).click();
			await alert(page);
			assert.equal(
				await page.getByText("SYNTHETIC-1", { exact: true }).count(),
				0,
			);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}

		{
			const { page, state } = await harness();
			state.network = true;
			await page.goto(href);
			await alert(page);
			assert.equal(await page.getByRole("table", table).count(), 0);
			state.network = false;
			await page.getByRole("button", button).click();
			await loaded(page);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			await page.goto(href);
			await loaded(page);
			await page.goto(`${base}/admin/bookings/venues/${venue}/${other}`);
			await page
				.getByRole("heading", {
					name: "Protected staff booking detail",
					exact: true,
				})
				.waitFor();
			assert.equal(await page.getByRole("table", table).count(), 0);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			state.data = {
				...state.data,
				document: "PRIVATE_UNSUPPORTED",
				entries: state.data.entries.map((e) => ({
					...e,
					evidence: "PRIVATE_UNSUPPORTED",
				})),
			};
			await page.goto(href);
			await loaded(page);
			assert.doesNotMatch(
				await page.locator("main").innerText(),
				/PRIVATE_UNSUPPORTED/,
			);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			state.delay = 700;
			const started = page.waitForRequest((r) =>
				new URL(r.url()).pathname.endsWith("/guest-roster"),
			);
			await page.goto(href);
			await started;
			await page
				.getByText("Loading current authorized guest roster.", { exact: true })
				.waitFor();
			await page.evaluate(() =>
				window.dispatchEvent(new Event("cinenest-session-change")),
			);
			await page.waitForTimeout(850);
			assert.equal(await page.getByRole("table", table).count(), 0);
			assert.deepEqual(state.writes, []);
			await page.close();
			groups++;
		}
	}
	console.log(
		`PASS ${groups} ${mode} synthetic roster browser/visual groups; intercepted APIs,not backend E2E`,
	);
} finally {
	if (browser) await browser.close();
	if (child.exitCode === null) {
		child.kill("SIGTERM");
		await once(child, "exit");
	}
}

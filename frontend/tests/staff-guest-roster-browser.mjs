// Intercepted synthetic UI tests; not backend authorization or real E2E evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import {
	venue,
	id,
	other,
	item,
	detailRecord,
} from "./staff-bookings-fixtures.mjs";
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
const checkInOnly = process.argv.includes("--check-in-only");
const admissionOnly = checkInOnly || process.argv.includes("--admission-only");
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
				admission: {},
				admissionStatus: 200,
				checkIn: null,
				commands: [],
			};
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				path = new URL(req.url()).pathname;
			if (req.method() !== "GET") state.writes.push(path);
			if (checkInOnly && path.endsWith("/session/csrf"))
				return route.fulfill({ json: { token: "synthetic-csrf" } });
			if (checkInOnly && path.endsWith("/guest-check-in")) {
				if (req.method() === "GET") {
					return route.fulfill({
						json: {
							available: !state.checkIn,
							rosterRevision: 2,
							decision: state.checkIn,
						},
					});
				}
				assert.equal(req.headers()["x-csrf-token"], "synthetic-csrf");
				state.commands.push(req.postDataJSON());
				if (state.commands.length === 1) {
					return route.fulfill({
						status: 503,
						json: { message: "PRIVATE_CHECK_IN_ERROR" },
					});
				}
				state.checkIn = {
					...item(),
					bookingRevision: 1,
					rosterRevision: 2,
					actorId: other,
					recordedAt: item().scheduledStart,
					physicalEntryAuthorized: false,
					outcome: "SYNTHETIC_CHECK_IN_RECORDED",
					documentNumber: "PRIVATE_CHECK_IN_RECEIPT",
				};
				return route.fulfill({ json: state.checkIn });
			}
			if (path.endsWith("/guest-admission")) {
				return route.fulfill({
					status: state.admissionStatus,
					json: {
						...item(),
						bookingRevision: item().revision,
						bookingState: item().state,
						permittedGuestCount: 4,
						checkedGuestCount: 0,
						status: "PENDING_VERIFICATION",
						physicalEntryAuthorized: false,
						documentNumber: "PRIVATE_ADMISSION_SENTINEL",
						...state.admission,
					},
				});
			}
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
	for (const width of admissionOnly ? [] : [375, 768, 1280]) {
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
	if (mode === "after" && !admissionOnly) {
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
	if (!checkInOnly && (mode === "after" || admissionOnly)) {
		const { page, state } = await harness();
		await page.goto(href);
		const panel = page.getByRole("region", {
			name: "Guest admission assessment",
		});
		await panel.getByText("PENDING_VERIFICATION", { exact: true }).waitFor();
		assert.match(await panel.innerText(), /does not authorize physical entry/);
		assert.doesNotMatch(
			await page.locator("main").innerText(),
			/PRIVATE_ADMISSION_SENTINEL/,
		);
		assert.deepEqual(state.writes, []);
		const reload = panel.getByRole("button", {
			name: "Load admission assessment",
		});
		state.admission = {
			status: "ELIGIBLE_FOR_STAFF_REVIEW",
			checkedGuestCount: 4,
		};
		await reload.click();
		await panel
			.getByText("ELIGIBLE_FOR_STAFF_REVIEW", { exact: true })
			.waitFor();
		groups++;
		state.admission = { ...state.admission, bookingRevision: 2 };
		await reload.click();
		await panel.getByText(/Admission assessment unavailable/).waitFor();
		assert.equal(
			await panel
				.getByText("ELIGIBLE_FOR_STAFF_REVIEW", { exact: true })
				.count(),
			0,
		);
		groups++;
		state.admissionStatus = 403;
		await reload.click();
		await panel
			.getByText("Admission assessment requires current venue authority.")
			.waitFor();
		assert.deepEqual(state.writes, []);
		groups++;
		await page.close();
		groups++;
	}

	if (checkInOnly) {
		const { page, state } = await harness();
		await page.goto(href);
		const panel = page.getByRole("region", {
			name: "Synthetic staff check-in",
		});
		const record = panel.getByRole("button", {
			name: "Record synthetic check-in",
		});
		await record.focus();
		await page.keyboard.press("Enter");
		await panel
			.getByRole("alert")
			.getByText(/Retry the same request/)
			.waitFor();
		assert.doesNotMatch(await panel.innerText(), /PRIVATE_CHECK_IN_ERROR/);
		groups++;
		await record.click();
		await panel.getByText(/Synthetic decision recorded by/).waitFor();
		assert.equal(await record.count(), 0);
		assert.equal(state.commands.length, 2);
		assert.deepEqual(state.commands[0], state.commands[1]);
		assert.equal(state.commands[0].bookingRevision, item().revision);
		assert.equal(state.commands[0].rosterRevision, 2);
		assert.match(await panel.innerText(), /grants no physical entry/);
		assert.doesNotMatch(await panel.innerText(), /PRIVATE_CHECK_IN_RECEIPT/);
		groups++;
		await page.close();
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

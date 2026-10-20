// Intercepted SYNTHETIC presentation checks; NOT backend permission or real E2E evidence.
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
let output = "",
	browser,
	groups = 0;
child.stdout.on("data", (b) => (output += b));
child.stderr.on("data", (b) => (output += b));
try {
	for (let n = 0; n < 100; n++) {
		if (child.exitCode !== null) throw new Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 200));
	}
	browser = await chromium.launch({ headless: true });
	await mkdir("../.cache/booking-fe-browser", { recursive: true });
	async function harness(width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } }),
			state = {
				status: 200,
				reads: [],
				writes: [],
				delay: 0,
				network: false,
				empty: false,
				unsafe: false,
				wrongVenue: false,
				revision: 1,
			};
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				u = new URL(req.url());
			if (req.method() !== "GET") state.writes.push(u.pathname);
			if (
				u.pathname.startsWith("/api/admin/venues/") &&
				u.pathname.includes("/bookings")
			) {
				state.reads.push(u.pathname + u.search);
				if (state.network) return route.abort("failed");
				let data,
					status = state.status;
				if (!u.pathname.startsWith(`/api/admin/venues/${venue}/`)) status = 403;
				if (u.pathname.endsWith("/bookings")) {
					let items = state.empty
						? []
						: [
								{
									...item(),
									bookingId: other,
									scheduledStart: "2026-10-22T04:30:00Z",
									scheduledEnd: "2026-10-22T07:30:00Z",
								},
								item(),
							];
					if (u.searchParams.get("reference"))
						items = items.filter(
							(v) => v.bookingId === u.searchParams.get("reference"),
						);
					const number = Number(u.searchParams.get("page") ?? 0),
						size = Number(u.searchParams.get("size") ?? 10),
						start = number * size;
					data = {
						venueId: state.wrongVenue ? other : venue,
						items: items.slice(start, start + size).map((v) => ({
							...v,
							revision: state.revision,
							evidence: "PRIVATE SECRET FIXTURE",
						})),
						page: number,
						size,
						hasNext: items.length > start + size,
						observedAt: "2026-10-20T10:00:00Z",
						synthetic: true,
						realBooking: false,
					};
				} else {
					data = {
						...detailRecord(),
						booking: {
							...item(),
							bookingId: u.pathname.split("/").at(-1),
							venueId: state.wrongVenue ? other : venue,
							revision: state.revision,
							evidence: "PRIVATE SECRET FIXTURE",
							realBooking: state.unsafe,
						},
					};
				}
				const delay = state.delay;
				if (delay) await new Promise((r) => setTimeout(r, delay));
				try {
					return await route.fulfill({
						status,
						json: status === 200 ? data : { message: "PRIVATE SECRET FIXTURE" },
					});
				} catch {
					return;
				}
			}
			return route.fulfill({
				status: 401,
				json: { message: "Session required" },
			});
		});
		return { page, state };
	}
	const list = `${base}/admin/bookings?venue=${venue}&page=0&size=1`,
		detail = `${base}/admin/bookings/venues/${venue}/${id}?page=0&size=1`;
	for (const width of [320, 768, 1280]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/admin/bookings`);
		await page
			.getByRole("heading", {
				name: "Staff booking administration",
				exact: true,
			})
			.waitFor();
		assert.equal(state.reads.length, 0);
		await page
			.getByLabel("Assigned venue reference", { exact: true })
			.fill(venue);
		await page
			.getByLabel("Bookings per page (1–25)", { exact: true })
			.fill("1");
		const apply = page.getByRole("button", {
			name: "Apply booking filters",
			exact: true,
		});
		await apply.focus();
		await page.keyboard.press("Enter");
		await page
			.getByRole("link", { name: "Next booking page", exact: true })
			.waitFor();
		assert.ok(state.reads.at(-1).includes("size=1"));
		assert.equal(state.writes.length, 0);
		assert.equal(await page.locator("h1").count(), 1);
		await page
			.getByRole("link", { name: "Next booking page", exact: true })
			.click();
		await page
			.getByRole("link", {
				name: `View exact booking details for ${id}`,
				exact: true,
			})
			.waitFor();
		assert.equal(new URL(page.url()).searchParams.get("page"), "1");
		const exact = page.getByRole("link", {
			name: `View exact booking details for ${id}`,
			exact: true,
		});
		await exact.focus();
		await page.keyboard.press("Enter");
		await page
			.getByRole("region", {
				name: "Authorized staff booking detail",
				exact: true,
			})
			.waitFor();
		assert.equal(
			new URL(page.url()).pathname,
			`/admin/bookings/venues/${venue}/${id}`,
		);
		assert.ok(
			state.reads.some(
				(v) => v === `/api/admin/venues/${venue}/bookings/${id}`,
			),
		);
		assert.ok(
			!(await page.locator("body").innerText()).includes("PRIVATE SECRET"),
		);
		await page.reload();
		await page
			.getByRole("region", {
				name: "Authorized staff booking detail",
				exact: true,
			})
			.waitFor();
		state.revision = 2;
		await page
			.getByRole("button", { name: "Reload booking detail", exact: true })
			.click();
		await page.getByText("Server revision", { exact: true }).waitFor();
		await page.waitForFunction(() =>
			document
				.querySelector(".account-summary")
				?.textContent.includes("Server revision2"),
		);
		await page
			.getByRole("link", { name: "Return to booking results", exact: true })
			.click();
		await page
			.getByRole("link", { name: "Previous booking page", exact: true })
			.waitFor();
		assert.equal(new URL(page.url()).searchParams.get("page"), "1");
		await page.goBack();
		await page
			.getByRole("region", {
				name: "Authorized staff booking detail",
				exact: true,
			})
			.waitFor();
		await page.goForward();
		await page
			.getByRole("link", { name: "Previous booking page", exact: true })
			.waitFor();
		await page
			.getByLabel("Exact booking reference (optional)", { exact: true })
			.fill(id);
		await page
			.getByRole("button", { name: "Apply booking filters", exact: true })
			.click();
		await page
			.getByRole("link", {
				name: `View exact booking details for ${id}`,
				exact: true,
			})
			.waitFor();
		assert.equal(new URL(page.url()).searchParams.get("reference"), id);
		assert.equal(new URL(page.url()).searchParams.get("page"), "0");
		assert.equal(state.writes.length, 0);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.screenshot({
			path: `../.cache/booking-fe-browser/${width}.png`,
			fullPage: true,
		});
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(list);
		await page
			.getByRole("link", { name: "Next booking page", exact: true })
			.waitFor();
		const count = state.reads.length;
		await page
			.getByLabel("Scheduled start from (inclusive, offset required)", {
				exact: true,
			})
			.fill("2026-10-01T00:00:00Z");
		await page
			.getByRole("button", { name: "Apply booking filters", exact: true })
			.click();
		await page
			.getByRole("alert")
			.getByText("Supply both", { exact: false })
			.waitFor();
		assert.equal(state.reads.length, count);
		await page
			.getByLabel("Scheduled start to (exclusive, offset required)", {
				exact: true,
			})
			.fill("2026-11-01T00:00:00.000000001Z");
		await page
			.getByRole("button", { name: "Apply booking filters", exact: true })
			.click();
		await page
			.getByRole("alert")
			.getByText("at most 31 elapsed days", { exact: false })
			.waitFor();
		assert.equal(state.reads.length, count);
		await page
			.getByLabel("Scheduled start to (exclusive, offset required)", {
				exact: true,
			})
			.fill("2026-11-01T00:00:00Z");
		await page
			.getByRole("button", { name: "Apply booking filters", exact: true })
			.click();
		await page
			.getByRole("link", { name: "Next booking page", exact: true })
			.waitFor();
		assert.equal(
			new URL(page.url()).searchParams.get("to"),
			"2026-11-01T00:00:00Z",
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	for (const target of [list, detail])
		for (const status of [400, 401, 403, 404, 503, 502]) {
			const { page, state } = await harness();
			state.status = status;
			await page.goto(target);
			await page
				.getByText(
					/Session unavailable|Current booking.synthetic.read authority|Booking unavailable, missing/,
				)
				.first()
				.waitFor();
			assert.equal(
				await page
					.getByRole("region", { name: "Authorized staff booking detail" })
					.count(),
				0,
			);
			assert.equal(
				await page
					.getByRole("link", { name: /View exact booking details for/ })
					.count(),
				0,
			);
			assert.ok(
				!(await page.locator("body").innerText()).includes("PRIVATE SECRET"),
			);
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
	for (const target of [list, detail]) {
		{
			const { page, state } = await harness();
			state.network = true;
			await page.goto(target);
			await page
				.getByText("Booking unavailable, missing", { exact: false })
				.waitFor();
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			state.wrongVenue = true;
			await page.goto(target);
			await page
				.getByText("Booking unavailable, missing", { exact: false })
				.waitFor();
			assert.equal(
				await page
					.getByRole("link", { name: /View exact booking details for/ })
					.count(),
				0,
			);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			await page.goto(target);
			await page
				.getByText("Server observation:", { exact: false })
				.first()
				.waitFor();
			state.status = 403;
			await page.evaluate(() => window.dispatchEvent(new Event("focus")));
			await page
				.getByText("Current booking.synthetic.read authority", { exact: false })
				.waitFor();
			assert.equal(
				await page
					.getByRole("link", { name: /View exact booking details for/ })
					.count(),
				0,
			);
			assert.equal(
				await page
					.getByRole("region", { name: "Authorized staff booking detail" })
					.count(),
				0,
			);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness();
			state.delay = 500;
			await page.goto(target);
			await page
				.getByText("Loading current authorized booking context.", {
					exact: true,
				})
				.waitFor();
			await page.evaluate(() =>
				window.dispatchEvent(new Event("cinenest-session-change")),
			);
			await page.waitForTimeout(650);
			assert.equal(
				await page
					.getByRole("link", { name: /View exact booking details for/ })
					.count(),
				0,
			);
			assert.equal(
				await page
					.getByRole("region", { name: "Authorized staff booking detail" })
					.count(),
				0,
			);
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
	}
	{
		const { page, state } = await harness();
		state.empty = true;
		await page.goto(list);
		await page
			.getByText("No matching synthetic bookings", { exact: false })
			.waitFor();
		assert.equal(
			await page
				.getByRole("link", { name: "Next booking page", exact: true })
				.count(),
			0,
		);
		await page.close();
		groups++;
	}
	for (const path of [
		`/admin/bookings?venue=${venue}&size=26`,
		`/admin/bookings?venue=${venue}&page=0&page=1`,
		`/admin/bookings?venue=bad`,
	]) {
		const { page, state } = await harness();
		await page.goto(base + path);
		await page
			.getByRole("alert")
			.filter({ hasText: "Invalid or repeated booking filters" })
			.waitFor();
		assert.equal(state.reads.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/admin/bookings/venues/${venue}/bad`);
		await page
			.getByText("Booking unavailable, missing", { exact: false })
			.waitFor();
		assert.equal(state.reads.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.unsafe = true;
		await page.goto(detail);
		await page
			.getByText("Booking unavailable, missing", { exact: false })
			.waitFor();
		assert.equal(
			await page
				.getByRole("region", { name: "Authorized staff booking detail" })
				.count(),
			0,
		);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic booking-admin browser groups; NOT backend authorization/E2E or acceptance`,
	);
} finally {
	if (browser) await browser.close();
	if (child.exitCode === null) {
		const stop = once(child, "exit");
		child.kill("SIGTERM");
		await Promise.race([
			stop,
			new Promise((_, reject) =>
				setTimeout(
					() => reject(new Error("Owned Next shutdown timed out")),
					10000,
				).unref(),
			),
		]);
	}
}

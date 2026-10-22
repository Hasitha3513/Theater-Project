// Intercepted presentation/interaction evidence only; backend authorization is tested separately with PostgreSQL.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import {
	context,
	fixture,
	account,
} from "./booking-confirmations-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const port = 3157,
	base = `http://127.0.0.1:${port}`,
	child = spawn(
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
const observations = [];
try {
	for (let n = 0; n < 150; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		if (n === 149) throw Error("Server timeout");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch();
	await mkdir("../.cache/booking-confirmations/after", { recursive: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			reads = 0,
			release;
		const requests = [];
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			requests.push(req.method());
			assert.equal(req.method(), "GET");
			if (url.pathname === "/api/accounts/me")
				return route.fulfill({
					status: mode === "SESSION" ? 401 : 200,
					json: { accountId: account, eligibility: "ELIGIBLE" },
				});
			if (!url.pathname.endsWith("/synthetic-booking-confirmations"))
				return route.fulfill({ status: 403 });
			reads++;
			if (mode === "LATE") await new Promise((r) => (release = r));
			if (["DENIED", "FAILURE"].includes(mode))
				return route.fulfill({
					status: mode === "DENIED" ? 403 : 503,
					body: "PRIVATE",
				});
			const f = fixture();
			if (mode === "EMPTY") {
				f.total = "0";
				f.daily.forEach((d) => {
					d.count = "0";
				});
				f.rooms = [];
			}
			if (mode === "BAD") f.total = "50";
			f.privateCustomer = "PRIVATE";
			return route.fulfill({ json: f }).catch(() => {});
		});
		await page.goto(`${base}/admin`);
		const section = page.getByRole("region", {
			name: "Booking Statistics",
			exact: true,
		});
		await section
			.getByRole("heading", { name: "Synthetic Booking Confirmations" })
			.waitFor();
		assert.equal(reads, 0);
		groups++;
		await section
			.getByLabel("Statistics venue reference", { exact: true })
			.fill(context.venue);
		await section.getByLabel("From date (inclusive)").fill(context.from);
		await section.getByLabel("To date (exclusive)").fill(context.to);
		const button = section.getByRole("button", {
			name: "Load statistics",
			exact: true,
		});
		await button.click();
		await section
			.getByRole("heading", { name: "Daily synthetic confirmations" })
			.waitFor();
		assert.equal(reads, 1);
		assert.equal(await section.getByRole("row").count(), 5);
		assert(!(await section.innerText()).includes("PRIVATE"));
		assert((await section.innerText()).includes("NOT AVAILABLE"));
		groups++;
		const measurements = await page.evaluate(() => ({
			overflow: document.documentElement.scrollWidth > innerWidth,
			mains: document.querySelectorAll("main").length,
			sidebar:
				document.querySelector(".sidebar")?.getBoundingClientRect().width ??
				null,
		}));
		assert.equal(measurements.overflow, false);
		assert.equal(measurements.mains, 1);
		await page.evaluate(() => window.scrollTo(0, 0));
		await section.screenshot({
			path: `../.cache/booking-confirmations/after/panel-${width}.png`,
		});
		await page.evaluate(() => window.scrollTo(0, 0));
		await page.screenshot({
			path: `../.cache/booking-confirmations/after/${width}.png`,
			fullPage: true,
		});
		observations.push({ width, ...measurements });
		groups++;
		await section
			.getByLabel("Statistics venue reference", { exact: true })
			.focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page.locator(":focus").getAttribute("id"),
			"confirmations-room",
		);
		await page.keyboard.press("Tab");
		assert.notEqual(
			await page
				.locator(":focus")
				.evaluate((el) => getComputedStyle(el).outlineStyle),
			"none",
		);
		groups++;
		for (const next of ["DENIED", "FAILURE", "BAD", "EMPTY", "SESSION"]) {
			mode = next;
			await button.click();
			const expected =
				next === "DENIED"
					? "Access denied."
					: next === "FAILURE"
						? "Statistics unavailable."
						: next === "BAD"
							? "Unable to load statistics."
							: next === "EMPTY"
								? "No synthetic confirmations"
								: "Your session expired.";
			await section.getByRole("status").filter({ hasText: expected }).waitFor();
			assert.equal(
				await section
					.getByRole("heading", { name: "Daily synthetic confirmations" })
					.count(),
				next === "EMPTY" ? 1 : 0,
			);
			groups++;
		}
		mode = "LATE";
		await button.click();
		for (let n = 0; n < 100 && !release; n++) await page.waitForTimeout(10);
		assert(release);
		await section
			.getByLabel("Statistics venue reference", { exact: true })
			.fill(account);
		release();
		await page.waitForTimeout(100);
		assert.equal(
			await section
				.getByRole("heading", { name: "Daily synthetic confirmations" })
				.count(),
			0,
		);
		groups++;
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(
			await section
				.getByRole("heading", { name: "Daily synthetic confirmations" })
				.count(),
			0,
		);
		assert(requests.every((m) => m === "GET"));
		groups++;
		await page.close();
	}
	await writeFile(
		"../.cache/booking-confirmations/observations.json",
		JSON.stringify({ groups, observations }, null, 2),
	);
	console.log(
		`PASS ${groups} intercepted browser groups;375/768/1280;not real-backend E2E`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
}

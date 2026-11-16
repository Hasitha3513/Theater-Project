// Intercepted presentation/interaction evidence only; backend authorization is tested separately with PostgreSQL.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { context, fixture, account } from "./popular-slots-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const port = 3158,
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
	await mkdir("../.cache/popular-slots/after", { recursive: true });
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
			if (!url.pathname.endsWith("/synthetic-popular-session-starts"))
				return route.fulfill({ status: 403 });
			reads++;
			if (mode === "LATE") await new Promise((r) => (release = r));
			if (["DENIED", "FAILURE"].includes(mode))
				return route.fulfill({
					status: mode === "DENIED" ? 403 : 503,
					body: "PRIVATE",
				});
			const f = fixture();
			if (mode === "OK") {
				f.buckets[2].count = "9007199254740992";
				f.buckets[4].count = "9007199254740993";
				f.buckets[6].count = "9007199254740992";
				f.buckets[18].count = "10";
				f.buckets[20].count = "2";
				f.total = f.buckets
					.reduce((sum, row) => sum + BigInt(row.count), 0n)
					.toString();
			}
			if (mode === "EMPTY") {
				f.total = "0";
				f.buckets.forEach((d) => {
					d.count = "0";
				});
			}
			if (mode === "BAD") f.total = "50";
			f.privateCustomer = "PRIVATE";
			return route.fulfill({ json: f }).catch(() => {});
		});
		await page.goto(`${base}/admin`);
		const section = page.getByRole("region", {
			name: "Popular Reserved Session Start Times",
			exact: true,
		});
		await section
			.getByRole("heading", { name: "Popular Reserved Session Start Times" })
			.waitFor();
		assert.equal(reads, 0);
		groups++;
		const retry = section.getByRole("button", { name: "Retry", exact: true });
		const venueField = section.getByLabel("Popular slots venue reference", {
			exact: true,
		});
		const requestsBeforeInvalidRetry = requests.length;
		await retry.click();
		assert(
			await venueField.evaluate(
				(field) =>
					field.validity.valueMissing && field === document.activeElement,
			),
		);
		assert.equal(requests.length, requestsBeforeInvalidRetry);
		assert.equal(reads, 0);
		groups++;
		await section
			.getByLabel("Popular slots venue reference", { exact: true })
			.fill(context.venue);
		await section
			.getByLabel("Popular slots from date (inclusive)")
			.fill(context.from);
		await section
			.getByLabel("Popular slots to date (exclusive)")
			.fill(context.to);
		const button = section.getByRole("button", {
			name: "Load Report",
			exact: true,
		});
		await button.click();
		await section
			.getByRole("region", { name: "Popular slots report", exact: true })
			.waitFor();
		assert.equal(reads, 1);
		assert.equal(await section.getByRole("row").count(), 25);
		assert(!(await section.innerText()).includes("PRIVATE"));
		assert((await section.innerText()).includes("Not actual attendance"));
		assert.equal(await section.locator(".popular-bars li").count(), 24);
		groups++;
		const countHeader = section.getByRole("columnheader", {
			name: "Synthetic reservations",
		});
		const sort = countHeader.getByRole("button");
		const chronologicalLabels = await section
			.getByRole("rowheader")
			.allTextContents();
		const chart = section.locator(".popular-bars li");
		const chartBefore = await chart.allTextContents();
		const chronologicalCounts = await section
			.locator("tbody td")
			.allTextContents();
		const expectHours = async (hours) => {
			assert.deepEqual(
				await section.getByRole("rowheader").allTextContents(),
				hours.map((hour) => chronologicalLabels[hour]),
			);
			assert.deepEqual(
				await section.locator("tbody td").allTextContents(),
				hours.map((hour) => chronologicalCounts[hour]),
			);
		};
		const zeroHours = Array.from({ length: 24 }, (_, hour) => hour).filter(
			(hour) => ![2, 4, 6, 18, 20].includes(hour),
		);
		const readsBeforeSort = reads;
		assert.equal(await countHeader.getAttribute("aria-sort"), "none");
		await section.getByRole("button", { name: "Clear", exact: true }).focus();
		await page.keyboard.press("Tab");
		assert(await sort.evaluate((el) => el === document.activeElement));
		await page.keyboard.press("Enter");
		const announcement = section
			.getByRole("status")
			.filter({ hasText: "ascending" });
		await announcement.waitFor();
		assert.equal(await announcement.getAttribute("aria-live"), "polite");
		assert.equal(await countHeader.getAttribute("aria-sort"), "ascending");
		await expectHours([...zeroHours, 20, 18, 2, 6, 4]);
		await page.keyboard.press("Space");
		await section
			.getByRole("status")
			.filter({ hasText: "descending" })
			.waitFor();
		assert.equal(await countHeader.getAttribute("aria-sort"), "descending");
		await expectHours([4, 2, 6, 18, 20, ...zeroHours]);
		assert.deepEqual(await chart.allTextContents(), chartBefore);
		assert.equal(reads, readsBeforeSort);
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
			path: `../.cache/popular-slots/after/panel-${width}.png`,
		});
		await page.evaluate(() => window.scrollTo(0, 0));
		await page.screenshot({
			path: `../.cache/popular-slots/after/${width}.png`,
			fullPage: true,
		});
		observations.push({ width, ...measurements });
		groups++;
		await section
			.getByLabel("Popular slots venue reference", { exact: true })
			.focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page.locator(":focus").getAttribute("id"),
			"popular-room",
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
								? "No synthetic reserved session starts"
								: "Your session expired.";
			await section.getByRole("status").filter({ hasText: expected }).waitFor();
			assert.equal(
				await section
					.getByRole("region", { name: "Popular slots report", exact: true })
					.count(),
				next === "EMPTY" ? 1 : 0,
			);
			if (next !== "EMPTY") assert.equal(await sort.count(), 0);
			groups++;
		}
		mode = "OK";
		const readsBeforeRecovery = reads;
		await retry.click();
		await section
			.getByRole("region", { name: "Popular slots report", exact: true })
			.waitFor();
		assert.equal(reads, readsBeforeRecovery + 1);
		const table = await section.locator("tbody td").allTextContents();
		const bars = await section
			.locator(".popular-bars strong")
			.allTextContents();
		assert.deepEqual(table, bars);
		groups++;
		assert.equal(await countHeader.getAttribute("aria-sort"), "none");
		await sort.click();
		await venueField.fill("");
		assert.equal(await sort.count(), 0);
		const requestsBeforeClearedRetry = requests.length;
		await retry.click();
		assert.equal(requests.length, requestsBeforeClearedRetry);
		assert.equal(await sort.count(), 0);
		await venueField.fill(context.venue);
		await button.click();
		await countHeader.waitFor();
		assert.equal(await countHeader.getAttribute("aria-sort"), "none");
		groups++;
		await section.getByRole("button", { name: "Clear", exact: true }).click();
		assert.equal(
			await section
				.getByRole("region", { name: "Popular slots report", exact: true })
				.count(),
			0,
		);
		groups++;
		assert.equal(await sort.count(), 0);
		mode = "LATE";
		await button.click();
		await section
			.getByRole("status")
			.filter({ hasText: "Loading protected statistics" })
			.waitFor();
		assert.equal(await sort.count(), 0);
		for (let n = 0; n < 100 && !release; n++) await page.waitForTimeout(10);
		assert(release);
		await section
			.getByLabel("Popular slots venue reference", { exact: true })
			.fill(account);
		release();
		await page.waitForTimeout(100);
		assert.equal(
			await section
				.getByRole("region", { name: "Popular slots report", exact: true })
				.count(),
			0,
		);
		groups++;
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(
			await section
				.getByRole("region", { name: "Popular slots report", exact: true })
				.count(),
			0,
		);
		assert(requests.every((m) => m === "GET"));
		groups++;
		await page.close();
	}
	await writeFile(
		"../.cache/popular-slots/observations.json",
		JSON.stringify({ groups, observations }, null, 2),
	);
	console.log(
		`PASS ${groups} intercepted browser groups;375/768/1280;not real-backend E2E`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
}

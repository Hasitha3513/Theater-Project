// Intercepted presentation tests only: actual financial authorization remains backend/PostgreSQL evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer().listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
	venue = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002",
	huge = "900719925474099312345678901234567890";
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
const observations = [];
try {
	for (let n = 0; n < 150; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 149) throw Error("server unavailable");
	}
	browser = await chromium.launch();
	await mkdir("../.cache/admin-bank-summary/after", { recursive: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			session = venue,
			hold,
			requests = [],
			apiCount = 0;
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			requests.push({
				method: req.method(),
				path: url.pathname,
				query: url.search,
			});
			assert.equal(req.method(), "GET");
			if (url.pathname === "/api/accounts/me")
				return route.fulfill({
					status: mode === "SESSION" ? 401 : 200,
					json: { accountId: session, eligibility: "ELIGIBLE" },
				});
			if (!url.pathname.endsWith("/reports/synthetic-bank-balances"))
				return route.fulfill({ status: 401, json: {} });
			apiCount++;
			if (mode === "DELAY") await new Promise((r) => (hold = r));
			if (/^\d+$/.test(mode))
				return route.fulfill({
					status: Number(mode),
					json: { message: "PRIVATE_ERROR_DO_NOT_DISPLAY" },
				});
			const query = Object.fromEntries(url.searchParams),
				summary = {
					c: huge,
					a: "100",
					t: "50",
					r: "200",
					u: "800",
					f: "600",
					available: null,
					unavailable: "600",
				};
			const data = {
				synthetic: true,
				realFinancialReporting: false,
				venueId: url.pathname.split("/")[4],
				currency: query.currency,
				timezone: "Asia/Colombo",
				page: 0,
				size: 1,
				filters: query,
				snapshotPolicy: "CURRENT_REQUEST_ONLY",
				privateEvidence: "PRIVATE_EVIDENCE",
				snapshot: {
					snapshotAt: "2026-10-22T04:24:00Z",
					metricVersion: "SYNTHETIC-BANK-BALANCES-V1",
					total: 100,
					summary,
					rows: [
						{
							creditId: other,
							creditedAt: "2026-10-01T00:00:00Z",
							verifiedAt: "2026-10-01T00:00:00Z",
							amounts: { ...summary, c: "1" },
							eligibility: "DISPUTED",
							exceptions: [],
							creditDrilldown: false,
						},
					],
				},
			};
			if (mode === "MALFORMED") data.snapshot.summary.c = 42;
			if (mode === "MISMATCH") data.venueId = other;
			if (mode === "SESSION_RACE") session = other;
			await route.fulfill({ json: data }).catch(() => {});
		});
		await page.goto(base + "/admin");
		const section = page.getByRole("region", {
				name: "Synthetic Bank Summary",
			}),
			load = section.getByRole("button", {
				name: "Load Bank Summary",
				exact: true,
			}),
			reload = section.getByRole("button", { name: "Reload Bank Summary" }),
			status = section.getByRole("status");
		await section.waitFor();
		await page.waitForTimeout(150);
		assert.equal(apiCount, 0);
		groups++;
		const fill = async () => {
			await section.getByLabel("Venue ID", { exact: true }).fill(venue);
			await section.getByLabel("Currency code", { exact: true }).fill("LKR");
			await section
				.getByLabel("Credited from", { exact: false })
				.fill("2026-10-01");
			await section
				.getByLabel("Credited to", { exact: false })
				.fill("2026-10-02");
		};
		await fill();
		await section
			.getByLabel("Credited to", { exact: false })
			.fill("2026-09-01");
		await load.click();
		await page.waitForTimeout(50);
		assert.equal(apiCount, 0);
		assert((await status.innerText()).includes("increasing"));
		groups++;
		await fill();
		await load.focus();
		await page.keyboard.press("Enter");
		await section
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		assert((await section.innerText()).includes(huge + " LKR minor units"));
		assert((await section.innerText()).includes("Unavailable"));
		assert(!(await section.innerText()).includes("PRIVATE_EVIDENCE"));
		assert(!(await section.innerText()).includes(other));
		assert.equal(apiCount, 1);
		groups++;
		const call = requests.find((x) =>
			x.path.endsWith("synthetic-bank-balances"),
		);
		assert.equal(
			call.query,
			"?currency=LKR&from=2026-10-01&to=2026-10-02&page=0&size=1",
		);
		assert.equal(await page.locator("main").count(), 1);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		// Loading disables the submit button; check an actual keyboard-focused control.
		await section.getByLabel("Venue ID", { exact: true }).focus();
		await page.keyboard.press("Tab");
		assert.notEqual(
			await section
				.getByLabel("Currency code", { exact: true })
				.evaluate((e) => getComputedStyle(e).outlineStyle),
			"none",
		);
		groups++;
		await page.screenshot({
			path: `../.cache/admin-bank-summary/after/admin-${width}.png`,
			fullPage: true,
		});
		await section.screenshot({
			path: `../.cache/admin-bank-summary/after/summary-${width}.png`,
		});
		observations.push({
			width,
			mainLandmarks: 1,
			horizontalOverflow: false,
			filterColumns: await section
				.locator(".summary-filter-grid")
				.evaluate((e) => getComputedStyle(e).gridTemplateColumns),
			amountColumns: await section
				.locator(".report-amounts")
				.evaluate((e) => getComputedStyle(e).gridTemplateColumns),
		});
		for (const code of [
			"400",
			"401",
			"403",
			"404",
			"409",
			"429",
			"503",
			"502",
		]) {
			mode = code;
			await reload.click();
			await page.waitForFunction(
				() =>
					!document.querySelector(
						'[aria-label="Synthetic Bank Summary"] [aria-busy="true"]',
					),
			);
			assert.equal(
				await section
					.getByRole("heading", { name: "Complete-population summary" })
					.count(),
				0,
			);
			assert(!(await section.innerText()).includes(huge));
			assert(!(await section.innerText()).includes("PRIVATE_ERROR"));
			groups++;
		}
		for (const bad of ["MALFORMED", "MISMATCH", "SESSION_RACE", "SESSION"]) {
			session = venue;
			mode = bad;
			await reload.click();
			await page.waitForFunction(
				() =>
					!document.querySelector(
						'[aria-label="Synthetic Bank Summary"] [aria-busy="true"]',
					),
			);
			assert.equal(
				await section
					.getByRole("heading", { name: "Complete-population summary" })
					.count(),
				0,
			);
			groups++;
		}
		session = venue;
		mode = "OK";
		await reload.click();
		await section
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		await section.getByLabel("Venue ID", { exact: true }).fill(other);
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		groups++;
		await fill();
		await reload.click();
		await section
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		await section.getByLabel("Currency code", { exact: true }).fill("USD");
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		groups++;
		await fill();
		mode = "DELAY";
		await reload.click();
		await page.waitForFunction(() =>
			document.querySelector(
				'[aria-label="Synthetic Bank Summary"] [aria-busy="true"]',
			),
		);
		while (!hold) await page.waitForTimeout(10);
		await section
			.getByLabel("Credited from", { exact: false })
			.fill("2026-10-02");
		hold();
		hold = undefined;
		await page.waitForTimeout(100);
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		groups++;
		await fill();
		mode = "OK";
		await reload.click();
		await section
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		const before = apiCount;
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		await page.waitForTimeout(50);
		assert.equal(apiCount, before);
		groups++;
		mode = "DELAY";
		await reload.click();
		while (!hold) await page.waitForTimeout(10);
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		hold();
		hold = undefined;
		await page.waitForTimeout(100);
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		groups++;
		mode = "OK";
		session = venue;
		await reload.click();
		await section
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		await page.evaluate(() => window.dispatchEvent(new Event("blur")));
		assert.equal(
			await section
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		assert(requests.every((x) => x.method === "GET"));
		groups++;
		await section.getByRole("button", { name: "Reset summary" }).click();
		assert.equal(
			await section.getByLabel("Venue ID", { exact: true }).inputValue(),
			"",
		);
		assert(requests.every((x) => x.method === "GET"));
		assert(!new URL(page.url()).search);
		assert.equal(
			await section
				.getByRole("link", { name: "Open detailed synthetic bank report →" })
				.getAttribute("href"),
			"/admin/reports/bank",
		);
		groups++;
		await page.close();
	}
	await writeFile(
		"../.cache/admin-bank-summary/observations.json",
		JSON.stringify(
			{ groups, observations, classification: "INTERCEPTED_BROWSER_ONLY" },
			null,
			2,
		),
	);
	console.log(
		`PASS ${groups} intercepted admin summary browser groups;375/768/1280;zero mutation requests`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

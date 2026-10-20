// Intercepted synthetic display checks, not banking or customer acceptance.
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
		if (n === 199) throw Error("UI server unavailable");
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			sequence = 0;
		const calls = [];
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			if (!url.pathname.includes("/reports/synthetic-bank-balances"))
				return route.fulfill({ status: 401, json: {} });
			calls.push(url);
			assert.equal(req.method(), "GET");
			if (["DENIED", "INVARIANT", "UNAVAILABLE"].includes(mode))
				return route.fulfill({
					status: mode === "DENIED" ? 403 : mode === "INVARIANT" ? 422 : 503,
					json: {},
				});
			sequence++;
			const summary = {
					c: "1000",
					a: "100",
					t: "100",
					r: "200",
					u: "800",
					f: "600",
					available: "500",
					unavailable: "100",
				},
				amounts = {
					c: "100",
					a: "0",
					t: "0",
					r: "0",
					u: "100",
					f: "100",
					available: null,
					unavailable: "100",
				};
			return route.fulfill({
				json: {
					synthetic: true,
					realFinancialReporting: false,
					venueId: id,
					currency: "LKR",
					timezone: "Asia/Colombo",
					page: Number(url.searchParams.get("page")),
					size: 25,
					filters: Object.fromEntries(url.searchParams),
					snapshotPolicy: "CURRENT_REQUEST_ONLY",
					snapshot: {
						snapshotAt: `2026-10-19T00:00:${String(sequence).padStart(2, "0")}Z`,
						metricVersion: "SYNTHETIC-BANK-BALANCES-V1",
						total: mode === "EMPTY" ? 0 : 26,
						summary:
							mode === "EMPTY"
								? Object.fromEntries(Object.keys(summary).map((k) => [k, "0"]))
								: summary,
						rows:
							mode === "EMPTY"
								? []
								: [
										{
											creditId: id,
											creditedAt: "2026-10-18T00:00:00Z",
											verifiedAt: "2026-10-19T00:00:00Z",
											amounts,
											eligibility: "DISPUTED",
											exceptions: [{ kind: "UNMATCHED", state: "OPEN" }],
											creditDrilldown: mode === "LINK",
										},
									],
					},
				},
			});
		});
		await page.goto(`${base}/admin/reports/bank`);
		await page
			.getByText("SYNTHETIC FINANCIAL REPORT — NO REAL MONEY", { exact: true })
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
		await page
			.getByRole("button", { name: "Load current report", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		const card = page
			.getByRole("heading", { name: "Complete-population summary" })
			.locator("..");
		assert((await card.innerText()).includes("1000 LKR minor units"));
		assert(
			(
				await page
					.getByText("Unavailable for allocation:", { exact: false })
					.innerText()
			).includes("DISPUTED"),
		);
		assert.equal(
			await page
				.getByRole("link", { name: "Open protected credit source" })
				.count(),
			0,
		);
		groups++;
		await page.getByRole("button", { name: "Next page", exact: true }).click();
		await page.getByText(/Snapshot changed from/).waitFor();
		assert.equal(calls.at(-1).searchParams.get("page"), "1");
		groups++;
		await page
			.getByLabel("Credited from (venue-local date, inclusive)")
			.fill("2026-10-18");
		await page
			.getByLabel("Credited to (venue-local date, exclusive)")
			.fill("2026-10-19");
		assert.equal(
			await page
				.getByRole("heading", { name: "Complete-population summary" })
				.count(),
			0,
		);
		await page
			.getByRole("button", { name: "Load current report", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Complete-population summary" })
			.waitFor();
		assert.equal(calls.at(-1).searchParams.get("from"), "2026-10-18");
		groups++;
		mode = "LINK";
		await page
			.getByRole("button", { name: "Load current report", exact: true })
			.click();
		await page
			.getByRole("link", { name: "Open protected credit source" })
			.waitFor();
		assert(
			(
				await page
					.getByRole("link", { name: "Open protected credit source" })
					.getAttribute("href")
			).endsWith(`/bank/credits/${id}`),
		);
		groups++;
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		if ([375, 1280].includes(width))
			await page.screenshot({
				path: `../.cache/bank-report-${width}.png`,
				fullPage: true,
			});
		groups++;
		for (const m of ["DENIED", "INVARIANT", "UNAVAILABLE", "EMPTY"]) {
			mode = m;
			await page
				.getByRole("button", { name: "Load current report", exact: true })
				.click();
			if (m === "EMPTY")
				await page.getByText("No synthetic credits in this scope.").waitFor();
			else {
				await page
					.getByRole("status")
					.filter({
						hasText:
							m === "DENIED"
								? "Denied"
								: m === "INVARIANT"
									? "Financial invariant error"
									: "unavailable",
					})
					.waitFor();
				assert.equal(
					await page
						.getByRole("heading", { name: "Complete-population summary" })
						.count(),
					0,
				);
			}
			groups++;
		}
		await page.getByLabel("Venue ID", { exact: true }).fill("");
		assert(
			await page
				.getByRole("button", { name: "Load current report", exact: true })
				.isDisabled(),
		);
		await page.getByLabel("Currency", { exact: true }).focus();
		assert(
			await page
				.getByLabel("Currency", { exact: true })
				.evaluate((e) => e === document.activeElement),
		);
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} synthetic report browser groups across 7 widths; no banking/customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

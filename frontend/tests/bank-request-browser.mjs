// Intercepted SYNTHETIC browser APIs; not real banking or backend end-to-end evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir } from "node:fs/promises";
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
const labels = [
	"SYNTHETIC BANK-PAYMENT TEST",
	"DO NOT TRANSFER REAL MONEY",
	"PAYMENT NOT VERIFIED",
	"BOOKING NOT CONFIRMED",
];
const initial = () => ({
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels,
	requestState: "OPEN",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-20T10:00Z",
	initiateBy: "2026-10-20T08:30Z",
	reviewWindow: "00:00–23:59",
	instructionVersion: "SYNTHETIC-BANK1",
	claims: [],
	work: [],
	expiresAt: "2026-10-20T10:01Z",
	submitBy: "2026-10-20T09:00Z",
	reviewBy: "2026-10-20T09:30Z",
	overdue: false,
	amountMinor: "12352",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
});
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
	await mkdir("../.cache/bank-request-browser", { recursive: true });

	async function harness(width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			status: 200,
			data: initial(),
			writes: [],
			reads: [],
			delay: false,
			release: null,
		};
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				path = new URL(req.url()).pathname;
			if (req.method() !== "GET") {
				state.writes.push(path);
				return route.fulfill({ status: 500, json: {} });
			}
			state.reads.push(path);
			if (path === "/api/accounts/me")
				return route.fulfill({
					json: { accountId: id, eligibility: "ELIGIBLE" },
				});
			if (path === `/api/bank/requests/${id}`) {
				const data = structuredClone(state.data),
					status = state.status;
				if (state.delay) await new Promise((r) => (state.release = r));
				try {
					return await route.fulfill({
						status,
						json: status === 200 ? data : { message: "PRIVATE BACKEND ERROR" },
					});
				} catch {
					return;
				}
			}
			if (path === "/api/bank/requests")
				return route.fulfill({
					json: {
						items: [
							{
								id,
								venueId: id,
								revision: 0,
								state: "OPEN",
								reviewState: "PENDING",
								claimSubmitted: false,
								amountMinor: "12352",
								currency: "LKR",
								timezone: "Asia/Colombo",
								initiateBy: "2026-10-20T08:30Z",
								submitBy: "2026-10-20T09:00Z",
								reviewBy: "2026-10-20T09:30Z",
								expiresAt: "2026-10-20T10:01Z",
								funding: {
									realPayment: false,
									status: "UNFUNDED",
									bookingStatus: "NOT_CONFIRMED",
									allocatedMinor: 0,
									outstandingMinor: 12352,
									verifiedMatchedMinor: 0,
									bookingId: null,
								},
								resolutions: [],
								synthetic: true,
							},
						],
						page: 0,
						size: 10,
						hasNext: false,
						observedAt: "2026-10-20T08:00Z",
						synthetic: true,
					},
				});
			if (path === "/api/bookings")
				return route.fulfill({
					json: {
						items: [],
						page: 0,
						size: 10,
						hasNext: false,
						observedAt: "2026-10-20T08:00Z",
						synthetic: true,
					},
				});
			return route.fulfill({ status: 401, json: { message: "Unavailable" } });
		});
		return { page, state };
	}
	const detail = (page) =>
		page.getByRole("region", { name: "Owned bank request detail" });
	for (const width of [320, 375, 768, 1280, 1536]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page)
			.getByRole("heading", { name: `Request ${id}`, exact: true })
			.waitFor();
		await page.reload();
		await detail(page).getByText("Current server revision: 0").waitFor();
		assert.equal(await page.locator("input").count(), 0);
		assert.equal(state.writes.length, 0);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		await page.screenshot({
			path: `../.cache/bank-request-browser/${width}.png`,
			fullPage: true,
		});
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bookings`);
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		const link = page.getByRole("link", {
			name: "View Bank Request",
			exact: true,
		});
		await link.waitFor();
		assert.equal(await link.getAttribute("href"), `/bank?request=${id}`);
		await link.focus();
		await page.keyboard.press("Enter");
		await detail(page).getByText("Current server revision: 0").waitFor();
		assert.equal(new URL(page.url()).search, `?request=${id}`);
		await page.goBack();
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await link.waitFor();
		await page.goForward();
		await detail(page).getByText("Current server revision: 0").waitFor();
		await detail(page)
			.getByRole("link", { name: "Return to customer history" })
			.click();
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await link.waitFor();
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	for (const status of [401, 403, 404, 409, 500, 503]) {
		const { page, state } = await harness();
		state.status = status;
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByRole("alert").waitFor();
		assert.equal(
			await detail(page)
				.getByRole("heading", { name: `Request ${id}`, exact: true })
				.count(),
			0,
		);
		assert.equal(
			(await page.textContent("body")).includes("PRIVATE BACKEND ERROR"),
			false,
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	for (const query of [
		"request=",
		"request=bad",
		`request=${id}&request=${id}`,
		`request=${id}&quote=${id}`,
	]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bank?${query}`);
		await detail(page).getByRole("alert").waitFor();
		assert.equal(
			state.reads.some((p) => p === `/api/bank/requests/${id}`),
			false,
		);
		await page.close();
		groups++;
	}
	for (const review of [
		"PENDING",
		"NEEDS_INFORMATION",
		"REJECTED",
		"REVIEWED",
		"UNKNOWN",
	]) {
		const { page, state } = await harness();
		state.data.reviewState = review;
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByText("Current server revision: 0").waitFor();
		assert.ok((await detail(page).textContent()).includes(review));
		assert.ok((await detail(page).textContent()).includes("unresolved"));
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByText("Current server revision: 0").waitFor();
		state.data.revision = 1;
		state.data.reviewState = "REJECTED";
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await detail(page).getByText("Current server revision: 1").waitFor();
		state.status = 401;
		await page.evaluate(() => window.dispatchEvent(new Event("pageshow")));
		await detail(page).getByRole("alert").waitFor();
		assert.equal(
			await detail(page).getByText("Current server revision: 1").count(),
			0,
		);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByText("Current server revision: 0").waitFor();
		state.delay = true;
		await detail(page)
			.getByRole("button", { name: "Reload current request" })
			.click();
		while (!state.release) await new Promise((r) => setTimeout(r, 10));
		await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
		state.release();
		await page.waitForTimeout(150);
		assert.equal(
			await detail(page).getByText("Current server revision: 0").count(),
			0,
		);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.data.id = "00000000-0000-4000-8000-000000000002";
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByRole("alert").waitFor();
		assert.equal(
			await detail(page).getByText("Current server revision: 0").count(),
			0,
		);
		await page.close();
		groups++;
	}

	for (const funding of [
		"UNFUNDED",
		"PARTIAL",
		"DISPUTED",
		"SIMULATED_ALLOCATED",
	]) {
		const { page, state } = await harness();
		state.data.financial = {
			allocatedMinor: funding === "SIMULATED_ALLOCATED" ? 12352 : 0,
			outstandingMinor: funding === "SIMULATED_ALLOCATED" ? 0 : 12352,
			syntheticFundingStatus: funding,
			syntheticBookingStatus:
				funding === "SIMULATED_ALLOCATED"
					? "SIMULATED_CONFIRMED"
					: "NOT_CONFIRMED",
		};
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByText("Current server revision: 0").waitFor();
		assert.ok((await detail(page).textContent()).includes(funding));
		assert.ok(
			(await detail(page).textContent()).includes(
				"Commercial payment and confirmation remain disabled",
			),
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page } = await harness();
		await page.route(`**/api/bank/requests/${id}`, (route) =>
			route.abort("timedout"),
		);
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByRole("alert").waitFor();
		await page.close();
		groups++;
	}
	{
		const { page } = await harness();
		await page.goto(`${base}/bank?request=${id}`);
		await detail(page).getByText("Current server revision: 0").waitFor();
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(
			await detail(page).getByText("Current server revision: 0").count(),
			0,
		);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic bank request browser groups; no backend/financial acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await Promise.race([
		once(child, "exit"),
		new Promise((r) => setTimeout(r, 5000)),
	]);
	if (child.exitCode === null) {
		child.kill("SIGKILL");
		await once(child, "exit");
	}
}

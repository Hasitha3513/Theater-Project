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
const other = "00000000-0000-4000-8000-000000000002";
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
	await mkdir("../.cache/booking-handoff-browser", { recursive: true });

	const financial = () => ({
		allocatedMinor: 12352,
		outstandingMinor: 0,
		verifiedMatchedMinor: "12352",
		fundingShortfallMinor: 0,
		syntheticFundingStatus: "SIMULATED_ALLOCATED",
		syntheticBookingStatus: "SIMULATED_CONFIRMED",
		bookingId: other,
	});
	const booking = () => ({
		id: other,
		requestId: id,
		venueId: id,
		revision: 1,
		synthetic: true,
		state: "SIMULATED_CONFIRMED",
		syntheticBookingStatus: "SIMULATED_CONFIRMED",
		confirmedAt: "2026-10-20T08:00:00Z",
		observedAt: "2026-10-20T08:00:00Z",
		purchase: {
			name: "SYNTHETIC purchased package",
			description: "Retained original terms",
			packageVersionId: id,
			sessionStart: "2026-10-21T04:30:00Z",
			sessionEnd: "2026-10-21T07:30:00Z",
			timezone: "Asia/Colombo",
			roomId: id,
			amountMinor: "12352",
			currency: "LKR",
			lines: [],
		},
	});
	async function harness(width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			bank: { ...initial(), financial: financial() },
			booking: booking(),
			status: 200,
			account: 200,
			bankStatus: 200,
			writes: [],
			reads: [],
			delay: false,
			release: null,
		};
		await page.route("**/api/**", async (route) => {
			const request = route.request(),
				path = new URL(request.url()).pathname;
			if (request.method() !== "GET") {
				state.writes.push(path);
				return route.fulfill({ status: 500, json: {} });
			}
			state.reads.push(path);
			if (path === "/api/accounts/me")
				return route.fulfill({
					status: state.account,
					json: { accountId: id, eligibility: "ELIGIBLE" },
				});
			if (path === `/api/bank/requests/${id}`)
				return route.fulfill({
					status: state.bankStatus,
					json: state.bankStatus === 200 ? state.bank : { message: "PRIVATE" },
				});
			if (path === `/api/bank/requests/${id}/refunds`)
				return route.fulfill({
					json: {
						items: [],
						page: 0,
						size: 10,
						hasNext: false,
						synthetic: true,
						observedAt: "2026-10-20T08:00:00Z",
					},
				});
			if (path === `/api/bookings/${other}`) {
				const value = structuredClone(state.booking),
					status = state.status;
				if (state.delay) await new Promise((r) => (state.release = r));
				try {
					return await route.fulfill({
						status,
						json: status === 200 ? value : { message: "PRIVATE" },
					});
				} catch {
					return;
				}
			}
			return route.fulfill({ status: 404, json: {} });
		});
		return { page, state };
	}
	const snapshot = (page) =>
		page.getByRole("heading", { name: "Purchased snapshot", exact: true });
	for (const width of [320, 375, 768, 1280, 1536]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bank?request=${id}`);
		const link = page.getByRole("link", {
			name: "View simulated booking details",
			exact: true,
		});
		await link.waitFor();
		assert.equal(await link.getAttribute("href"), `/bookings/${other}`);
		await link.focus();
		await page.keyboard.press("Enter");
		await snapshot(page).waitFor();
		assert.equal(new URL(page.url()).pathname, `/bookings/${other}`);
		assert.equal(new URL(page.url()).search, "");
		await page.reload();
		await snapshot(page).waitFor();
		await page.goBack();
		await link.waitFor();
		await page.goForward();
		await snapshot(page).waitFor();
		assert.ok(state.reads.includes(`/api/bookings/${other}`));
		assert.equal(state.writes.length, 0);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		await page.screenshot({
			path: `../.cache/booking-handoff-browser/${width}.png`,
			fullPage: true,
		});
		await page
			.getByRole("link", { name: "Return to customer history", exact: true })
			.click();
		assert.equal(new URL(page.url()).pathname, "/bookings");
		await page.close();
		groups++;
	}
	for (const status of [
		"NOT_CONFIRMED",
		"PENDING",
		"PARTIAL",
		"UNKNOWN",
		undefined,
	]) {
		const { page, state } = await harness();
		state.bank.financial.syntheticBookingStatus = status;
		state.bank.reviewState = "REVIEWED";
		await page.goto(`${base}/bank?request=${id}`);
		await page.getByText("Current server revision: 0").waitFor();
		assert.equal(
			await page
				.getByRole("link", { name: "View simulated booking details" })
				.count(),
			0,
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	for (const status of [401, 403, 404, 409, 500, 503]) {
		const { page, state } = await harness();
		state.status = status;
		await page.goto(`${base}/bookings/${other}`);
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		assert.equal((await page.textContent("body")).includes("PRIVATE"), false);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	for (const change of [
		{ state: "PENDING", syntheticBookingStatus: "NOT_CONFIRMED" },
		{ id },
		{ synthetic: false },
	]) {
		const { page, state } = await harness();
		Object.assign(state.booking, change);
		await page.goto(`${base}/bookings/${other}`);
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bookings/${other}`);
		await snapshot(page).waitFor();
		state.bank.financial.syntheticBookingStatus = "NOT_CONFIRMED";
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		await page.close();
		groups++;
	}
	for (const account of [401, 403]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bookings/${other}`);
		await snapshot(page).waitFor();
		state.account = account;
		await page.evaluate(() => window.dispatchEvent(new Event("pageshow")));
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bookings/bad`);
		await page
			.getByRole("status")
			.filter({ hasText: "Invalid booking reference" })
			.waitFor();
		assert.equal(state.reads.includes("/api/bookings/bad"), false);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.bankStatus = 404;
		await page.goto(`${base}/bookings/${other}`);
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.delay = true;
		await page.goto(`${base}/bookings/${other}`);
		for (let n = 0; n < 100 && !state.release; n++)
			await page.waitForTimeout(20);
		assert.ok(state.release);
		await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
		state.release();
		await page.waitForTimeout(150);
		assert.equal(await snapshot(page).count(), 0);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}

	{
		const { page, state } = await harness();
		state.bank.financial.syntheticFundingStatus = "PARTIAL";
		state.bank.financial.syntheticBookingStatus = "NOT_CONFIRMED";
		await page.goto(`${base}/bank?request=${id}`);
		await page.getByText("Current server revision: 0").waitFor();
		assert.equal(
			await page
				.getByRole("link", { name: "View simulated booking details" })
				.count(),
			0,
		);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.route(`**/api/bookings/${other}`, (r) => r.abort("timedout"));
		await page.goto(`${base}/bookings/${other}`);
		await page.getByRole("status").filter({ hasText: "Unavailable" }).waitFor();
		assert.equal(await snapshot(page).count(), 0);
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic confirmation-to-booking browser groups; not real backend/financial/customer acceptance`,
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

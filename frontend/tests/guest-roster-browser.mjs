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
	await mkdir("../.cache/guest-roster-browser", { recursive: true });

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
			rosterStatus: 200,
			roster: {
				bookingId: other,
				bookingRevision: 1,
				revision: 0,
				initialized: false,
				synthetic: true,
				status: "UNVERIFIED",
				physicalAdmission: false,
				ageVerified: false,
				accessIssued: false,
				purchasedLimit: 4,
				roomCapacity: 5,
				count: 0,
				remaining: 4,
				entries: [],
				permittedActions: ["INITIALIZE"],
			},
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
			if (
				path === `/api/bookings/${other}/guest-roster` ||
				path === `/api/bookings/${other}/guest-roster/companions`
			) {
				state.reads.push(path);
				if (state.rosterStatus !== 200)
					return route.fulfill({
						status: state.rosterStatus,
						json: { message: "PRIVATE" },
					});
				if (request.method() === "POST") {
					state.writes.push(path);
					const b = request.postDataJSON();
					if (path.endsWith("/companions")) {
						state.roster.entries.push({
							id: crypto.randomUUID(),
							kind: "COMPANION",
							label: b.label,
							status: "UNVERIFIED",
						});
						state.roster.revision++;
					} else {
						state.roster.initialized = true;
						state.roster.entries = [
							{ id, kind: "BOOKER", label: "BOOKER", status: "UNVERIFIED" },
						];
					}
					state.roster.count = state.roster.entries.length;
					state.roster.remaining = 4 - state.roster.count;
					state.roster.permittedActions = state.roster.remaining ? ["ADD"] : [];
				}
				return route.fulfill({ json: state.roster });
			}
			if (path === "/api/session/csrf")
				return route.fulfill({
					json: { token: "synthetic-csrf", headerName: "X-CSRF-TOKEN" },
				});
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

	for (const width of [320, 768, 1280]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bookings/${other}`);
		const create = page.getByRole("button", {
			name: "Create synthetic roster",
			exact: true,
		});
		await create.waitFor();
		assert.equal(state.writes.length, 0);
		await create.focus();
		await page.keyboard.press("Enter");
		await page
			.getByText("Server-bound BOOKER", { exact: false })
			.last()
			.waitFor();
		await page.getByLabel("Synthetic companion number").fill("1");
		await page
			.getByRole("button", { name: "Add synthetic companion", exact: true })
			.click();
		await page
			.getByText("SYNTHETIC-1 — UNVERIFIED / SYNTHETIC", { exact: true })
			.waitFor();
		assert.equal(state.writes.length, 2);
		await page.reload();
		await page
			.getByText("SYNTHETIC-1 — UNVERIFIED / SYNTHETIC", { exact: true })
			.waitFor();
		assert.equal(state.writes.length, 2);
		assert.equal(new URL(page.url()).search, "");
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.screenshot({
			path: `../.cache/guest-roster-browser/${width}.png`,
			fullPage: true,
		});
		await page.close();
		groups++;
	}
	for (const status of [401, 403, 404, 500]) {
		const { page, state } = await harness();
		state.rosterStatus = status;
		await page.goto(`${base}/bookings/${other}`);
		await page
			.getByText("Roster unavailable, denied or changed.", { exact: false })
			.waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "Add synthetic companion", exact: true })
				.count(),
			0,
		);
		assert.equal(state.writes.length, 0);
		assert.ok(!(await page.locator("body").innerText()).includes("PRIVATE"));
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.roster.ageVerified = true;
		await page.goto(`${base}/bookings/${other}`);
		await page
			.getByText("Roster unavailable, denied or changed.", { exact: false })
			.waitFor();
		assert.equal(state.writes.length, 0);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic guest-roster browser groups; NOT real backend E2E or physical acceptance`,
	);
} finally {
	if (browser) await browser.close();
	if(child.exitCode===null){const stopped = once(child, "exit");child.kill("SIGTERM");await stopped;}
}

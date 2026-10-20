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
const readiness = () => ({
	quoteId: id,
	quoteRevision: 1,
	contentHash: "a".repeat(64),
	quoteExpiresAt: "2026-10-20T10:00:00Z",
	quoteSummary: {
		currency: "LKR",
		totalMinor: "12352",
		paymentMethod: "BANK",
		sessionStart: "2026-10-21T04:30:00Z",
		sessionEnd: "2026-10-21T07:30:00Z",
		timezone: "Asia/Colombo",
		packageName: "SYNTHETIC package",
	},
	verificationReadiness: "ELIGIBLE",
	eligible: true,
	reason: "ELIGIBLE",
	serverNow: "2026-10-20T04:30:00Z",
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels,
});
const quoteContext = () => ({
	quoteId: id,
	venueId: id,
	roomId: id,
	revision: 1,
	contentHash: "a".repeat(64),
	state: "ACCEPTED",
	currency: "LKR",
	totalMinor: "12352",
	paymentMethod: "BANK",
	synthetic: true,
	commercialIssuance: false,
	checkoutEnabled: false,
	bookingConfirmed: false,
	expiresAt: "2026-10-20T10:00:00Z",
	holdExpiresAt: "2026-10-20T10:00:00Z",
	serverNow: "2026-10-20T04:30:00Z",
	timezone: "Asia/Colombo",
	disclosures: [
		"TEST / SYNTHETIC QUOTATION",
		"NOT A COMMERCIAL OFFER",
		"NO PAYMENT OR BOOKING CONFIRMATION",
	],
	optionalServiceIds: [],
	package: { name: "SYNTHETIC package" },
	lines: [
		{
			lineId: "BASE",
			kind: "BASE",
			label: "SYNTHETIC",
			quantity: 1,
			unitMinor: "12352",
			amountMinor: "12352",
		},
	],
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
	await mkdir("../.cache/bank-handoff-browser", { recursive: true });
	async function harness(width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			reason: "ELIGIBLE",
			status: 200,
			revision: 1,
			hash: "a".repeat(64),
			posts: [],
			gets: [],
			postStatus: 200,
			abortPost: false,
			delayed: null,
			holdRead: false,
		};
		await page.route("**/api/**", async (route) => {
			const request = route.request(),
				url = new URL(request.url()),
				path = url.pathname;
			state.gets.push(path);
			if (path === "/api/session/csrf")
				return route.fulfill({
					json: { token: "SYNTHETIC", headerName: "X-CSRF-TOKEN" },
				});
			if (request.method() !== "GET") {
				state.posts.push({ path, body: request.postDataJSON() });
				if (state.abortPost) {
					state.abortPost = false;
					return route.abort("failed");
				}
				if (state.postStatus !== 200) {
					if (state.postStatus === 409) state.reason = "HOLD_ALREADY_CONVERTED";
					if (state.postStatus === 401) state.status = 401;
					return route.fulfill({
						status: state.postStatus,
						json: { message: "SYNTHETIC refusal" },
					});
				}
				assert.equal(path, "/api/bank/requests");
				return route.fulfill({ json: initial() });
			}
			if (path === `/api/verification/requests/${id}`)
				return route.fulfill({
					json: {
						id,
						venueId: id,
						quoteId: id,
						revision: 1,
						generation: 1,
						superseded: false,
						previousId: null,
						state: "VERIFIED",
						validUntil: "2026-10-21T10:00:00Z",
						synthetic: true,
						physicalAdmission: false,
						labels: [
							"TEST / SYNTHETIC IDENTITY VERIFICATION",
							"NO REAL IDENTITY DOCUMENTS",
							"NOT VALID FOR PHYSICAL ADMISSION",
						],
						evidence: [],
						history: [],
					},
				});
			if (path === `/api/quotes/${id}`)
				return route.fulfill({
					json: {
						...quoteContext(),
						revision: state.revision,
						contentHash: state.hash,
						verificationHandoff: { eligible: true, reason: "ELIGIBLE" },
					},
				});
			if (path === "/api/bank/requests/readiness") {
				if (state.holdRead) {
					await new Promise((resolve) => (state.delayed = resolve));
				}
				if (state.status !== 200)
					return route.fulfill({
						status: state.status,
						json: { message: "SYNTHETIC access failure" },
					});
				return route.fulfill({
					json: {
						...readiness(),
						quoteRevision: state.revision,
						contentHash: state.hash,
						reason: state.reason,
						eligible: state.reason === "ELIGIBLE",
						verificationReadiness:
							state.reason === "VERIFICATION_REQUIRED"
								? "REQUIRED"
								: "ELIGIBLE",
					},
				});
			}
			return route.fulfill({
				status: 404,
				json: { message: "No synthetic fixture for route" },
			});
		});
		return { page, state };
	}
	const confirm = (p) =>
		p.getByLabel("I reviewed this synthetic quotation", { exact: false });
	const create = (p) =>
		p.getByRole("button", { name: "Create synthetic request", exact: true });
	async function loaded(p) {
		await p
			.getByRole("region", { name: "Selected bank-request quotation" })
			.waitFor();
	}
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		assert.equal(state.posts.length, 0);
		assert.equal(await confirm(page).isChecked(), false);
		assert.equal(await create(page).isDisabled(), true);
		assert.equal(
			await page.locator("#bank-hash,#bank-quote-revision").count(),
			0,
		);
		assert.ok(
			!(await page.locator("body").innerText()).includes("a".repeat(64)),
		);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
		await confirm(page).focus();
		await page.keyboard.press("Space");
		await create(page).focus();
		await page.keyboard.press("Enter");
		await page
			.getByRole("region", { name: "Request and review status", exact: true })
			.filter({ has: page.getByText("PENDING", { exact: true }) })
			.filter({ has: page.getByText("ACTIVE", { exact: true }) })
			.getByText("OPEN", { exact: true })
			.waitFor();
		assert.equal(state.posts.length, 1);
		assert.deepEqual(
			Object.keys(state.posts[0].body).sort(),
			["commandKey", "contentHash", "quoteId", "quoteRevision"].sort(),
		);
		assert.equal(state.posts[0].body.contentHash, "a".repeat(64));
		assert.equal(state.posts[0].body.quoteRevision, 1);
		assert.deepEqual([...new URL(page.url()).searchParams.keys()], ["quote"]);
		groups++;
		if (width === 320 || width === 1280)
			await page.screenshot({
				path: `../.cache/bank-handoff-browser/${width}.png`,
				fullPage: true,
			});
		await page.close();
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/verification?quote=${id}`);
		await page.getByLabel("Existing request ID", { exact: true }).fill(id);
		await page
			.getByRole("button", {
				name: "Refresh current request and history",
				exact: true,
			})
			.click();
		const link = page.getByRole("link", {
			name: "Continue to Bank Request",
			exact: true,
		});
		await link.waitFor();
		assert.equal(state.posts.length, 0);
		await link.focus();
		await page.keyboard.press("Enter");
		await page.waitForURL(`**/bank?quote=${id}`);
		await loaded(page);
		assert.equal(await confirm(page).isChecked(), false);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	for (const reason of [
		"VERIFICATION_REQUIRED",
		"QUOTE_EXPIRED",
		"HOLD_INELIGIBLE",
		"HOLD_EXPIRED",
		"QUOTE_NOT_ACCEPTED",
		"HOLD_CHANGED",
		"HOLD_ALREADY_CONVERTED",
		"LEAD_TIME_REQUIRED",
		"DEADLINES_EXCEED_SESSION_START",
		"REVIEW_WINDOW_CLOSED",
	]) {
		const { page, state } = await harness();
		state.reason = reason;
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		assert.equal(await create(page).count(), 0);
		assert.equal(state.posts.length, 0);
		assert.ok(
			await page
				.getByRole("link", { name: "Return to verification", exact: true })
				.count(),
		);
		await page.close();
		groups++;
	}
	for (const status of [401, 403, 404, 503]) {
		const { page, state } = await harness();
		state.status = status;
		await page.goto(`${base}/bank?quote=${id}`);
		await page
			.getByText("Bank context changed, is inaccessible or unavailable.", {
				exact: false,
			})
			.waitFor();
		assert.equal(await create(page).count(), 0);
		assert.equal(
			await page
				.getByRole("region", { name: "Selected bank-request quotation" })
				.count(),
			0,
		);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	for (const suffix of ["", "?quote=invalid", `?quote=${id}&quote=${id}`]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bank${suffix}`);
		await page
			.getByText("A quotation reference is missing or malformed.", {
				exact: false,
			})
			.waitFor();
		assert.equal(state.posts.length, 0);
		assert.equal(
			state.gets.filter(
				(p) => p.includes("readiness") || p.includes("/quotes/"),
			).length,
			0,
		);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		await confirm(page).check();
		state.revision = 2;
		state.hash = "b".repeat(64);
		await create(page).click();
		await page
			.getByText("Readiness or quotation terms changed.", { exact: false })
			.waitFor();
		assert.equal(state.posts.length, 0);
		assert.equal(await confirm(page).isChecked(), false);
		await confirm(page).check();
		await create(page).click();
		await page
			.getByRole("region", { name: "Request and review status", exact: true })
			.filter({ has: page.getByText("PENDING", { exact: true }) })
			.filter({ has: page.getByText("ACTIVE", { exact: true }) })
			.getByText("OPEN", { exact: true })
			.waitFor();
		assert.equal(state.posts[0].body.quoteRevision, 2);
		assert.equal(state.posts[0].body.contentHash, "b".repeat(64));
		await page.close();
		groups++;
	}
	for (const status of [409, 401]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		await confirm(page).check();
		state.postStatus = status;
		await create(page).click();
		await page
			.getByRole("alert")
			.filter({ hasText: "server did not create" })
			.waitFor();
		assert.equal(
			await page
				.getByRole("region", { name: "Request and review status", exact: true })
				.filter({ has: page.getByText("PENDING", { exact: true }) })
				.filter({ has: page.getByText("ACTIVE", { exact: true }) })
				.getByText("OPEN", { exact: true })
				.count(),
			0,
		);
		assert.equal(state.posts.length, 1);
		assert.equal(await create(page).count(), 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		await confirm(page).check();
		state.abortPost = true;
		await create(page).click();
		await page
			.getByRole("button", { name: "Retry original bank request", exact: true })
			.waitFor();
		const original = state.posts[0].body;
		state.reason = "HOLD_ALREADY_CONVERTED";
		await page
			.getByRole("button", { name: "Retry original bank request", exact: true })
			.click();
		await page
			.getByRole("region", { name: "Request and review status", exact: true })
			.filter({ has: page.getByText("PENDING", { exact: true }) })
			.filter({ has: page.getByText("ACTIVE", { exact: true }) })
			.getByText("OPEN", { exact: true })
			.waitFor();
		assert.equal(state.posts.length, 2);
		assert.deepEqual(state.posts[1].body, original);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		await confirm(page).check();
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await loaded(page);
		assert.equal(await confirm(page).isChecked(), false);
		state.status = 401;
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await page
			.getByText("Bank context changed, is inaccessible or unavailable.", {
				exact: false,
			})
			.waitFor();
		assert.equal(await create(page).count(), 0);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?quote=${id}`);
		await loaded(page);
		state.holdRead = true;
		await page
			.getByRole("button", { name: "Reload bank context", exact: true })
			.click();
		for (let n = 0; n < 50 && !state.delayed; n++)
			await new Promise((r) => setTimeout(r, 20));
		assert.ok(state.delayed);
		await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
		state.holdRead = false;
		state.delayed();
		await page.waitForTimeout(100);
		assert.equal(
			await page
				.getByRole("region", { name: "Selected bank-request quotation" })
				.count(),
			0,
		);
		assert.equal(state.posts.length, 0);
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await loaded(page);
		assert.equal(await confirm(page).isChecked(), false);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} synthetic bank-handoff browser groups; intercepted API responses only, no real funds or acceptance.`,
	);
} finally {
	if (browser) await browser.close();
	child.kill("SIGTERM");
	await Promise.race([
		once(child, "exit"),
		new Promise((r) => setTimeout(r, 5000)),
	]);
	if (child.exitCode === null) child.kill("SIGKILL");
}

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
const fixturePng = Buffer.from(
	"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",
	"base64",
);
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
	await mkdir("../.cache/bank-evidence-browser", { recursive: true });

	const claim = "00000000-0000-4000-8000-000000000002",
		upload = "00000000-0000-4000-8000-000000000003";
	async function harness(width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			value: initial(),
			status: 200,
			posts: [],
			puts: [],
			gets: [],
			abort: "",
			putOutcome: "QUARANTINED",
			replyMismatch: false,
			commands: new Map(),
		};
		await page.route("**/api/**", async (route) => {
			const r = route.request(),
				path = new URL(r.url()).pathname,
				method = r.method();
			if (path === "/api/session/csrf")
				return route.fulfill({
					json: { token: "SYNTHETIC", headerName: "X-CSRF-TOKEN" },
				});
			if (method === "GET") {
				state.gets.push(path);
				if (path === `/api/bank/requests/${id}`)
					return route.fulfill({
						status: state.status,
						json:
							state.status === 200
								? { ...state.value, id: state.replyMismatch ? claim : id }
								: { message: "PRIVATE ERROR" },
					});
				return route.fulfill({ status: 401, json: {} });
			}
			assert.equal(r.headers()["x-csrf-token"], "SYNTHETIC");
			if (method === "PUT") {
				state.puts.push(path);
				state.value.evidence[0].state = state.putOutcome;
				if (state.abort === "put") {
					state.abort = "";
					return route.abort("failed");
				}
				return route.fulfill({ json: { state: "QUARANTINED" } });
			}
			assert.equal(method, "POST");
			const body = r.postDataJSON();
			state.posts.push({ path, body });
			if (state.commands.has(body.commandKey))
				return route.fulfill({ json: state.commands.get(body.commandKey) });
			assert.equal(body.revision, state.value.revision);
			let kind = "";
			if (
				path === `/api/bank/requests/${id}/claims` ||
				path === `/api/bank/requests/${id}/resubmissions`
			) {
				kind = "claim";
				state.value = {
					...state.value,
					revision: state.value.revision + 1,
					claimId: claim,
					requestState: "REVIEW_PENDING",
					reviewState: "PENDING",
					evidence: [],
				};
			} else if (path === `/api/bank/claims/${claim}/uploads`) {
				kind = "allocate";
				state.value = {
					...state.value,
					revision: state.value.revision + 1,
					uploadId: upload,
					evidence: [
						{
							id: upload,
							state: "ALLOCATED",
							expiresAt: state.value.expiresAt,
						},
					],
				};
			} else if (path === `/api/bank/uploads/${upload}/complete`) {
				kind = "complete";
				state.value = {
					...state.value,
					revision: state.value.revision + 1,
					evidence: [
						{ id: upload, state: "READY", expiresAt: state.value.expiresAt },
					],
				};
			} else throw Error(`Unexpected mutation ${path}`);
			state.commands.set(body.commandKey, structuredClone(state.value));
			if (state.abort === kind) {
				state.abort = "";
				return route.abort("failed");
			}
			return route.fulfill({ json: state.value });
		});
		return { page, state };
	}
	const submit = (p) =>
		p.getByRole("button", { name: "Submit synthetic claim", exact: true });
	const receipt = (p) =>
		p.getByRole("button", {
			name: "Upload private synthetic receipt",
			exact: true,
		});
	async function fill(page) {
		await page
			.getByLabel("Declared amount in minor units (not proof of funds)")
			.fill("12352");
		await page
			.getByLabel("Declared payment time (ISO UTC, unverified)")
			.fill("2026-10-20T08:00:00Z");
		await page.getByLabel("Synthetic declared reference").fill("SYNTHETIC");
	}
	async function file(page) {
		await page.getByLabel("Approved synthetic JPEG/PNG fixture").setInputFiles({
			name: "SYNTHETIC.png",
			mimeType: "image/png",
			buffer: fixturePng,
		});
	}

	// Focused CN-FE-EXPERIENCE-001F presentation checks; business APIs remain intercepted.
	for (const width of [375, 768, 1280]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bank?evidence=${id}`);
		await submit(page).waitFor();
		await page
			.getByRole("heading", { name: "Request overview", exact: true })
			.waitFor();
		assert.equal(
			await page.getByText("LKR 123.52", { exact: true }).count(),
			1,
		);
		const timings = page.getByText("Timing and technical references", {
			exact: true,
		});
		await timings.focus();
		await page.keyboard.press("Enter");
		await page
			.getByText("20 Oct 2026, 10:00 UTC", { exact: true })
			.first()
			.waitFor();
		const amount = page.getByLabel(
			"Declared amount in minor units (not proof of funds)",
		);
		assert.match(
			await page.locator("#bank-declared-amount-help").innerText(),
			/100 minor units = LKR 1.00/,
		);
		await amount.focus();
		await page.keyboard.press("Tab");
		await page.keyboard.press("Shift+Tab");
		assert.notEqual(
			await amount.evaluate((el) => getComputedStyle(el).outlineStyle),
			"none",
		);
		assert.equal(
			await page
				.getByLabel("Approved synthetic JPEG/PNG fixture")
				.getAttribute("aria-describedby"),
			"bank-file-help",
		);
		assert.equal(state.posts.length, 0);
		assert.equal(state.puts.length, 0);
		assert.equal(new URL(page.url()).search, `?evidence=${id}`);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			),
			false,
		);
		await page.close();
		groups++;
	}
	for (const width of [320, 768, 1280]) {
		const { page, state } = await harness(width);
		await page.goto(`${base}/bank?request=${id}`);
		const link = page.getByRole("link", {
			name: "Open synthetic claim and receipt workflow",
			exact: true,
		});
		await link.waitFor();
		assert.equal(await link.getAttribute("href"), `/bank?evidence=${id}`);
		await link.focus();
		await page.keyboard.press("Enter");
		await submit(page).waitFor();
		assert.equal(state.posts.length, 0);
		assert.equal(state.puts.length, 0);
		assert.equal(
			await page.getByLabel("Request ID", { exact: true }).count(),
			0,
		);
		assert.equal(new URL(page.url()).search, `?evidence=${id}`);
		await fill(page);
		await submit(page).click();
		await page
			.getByRole("button", { name: "Append corrected claim", exact: true })
			.waitFor();
		await file(page);
		await receipt(page).click();
		await page.getByText(/READY ·/).waitFor();
		assert.equal(state.posts.length, 3);
		assert.equal(state.puts.length, 1);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		await page.screenshot({
			path: `../.cache/bank-evidence-browser/${width}.png`,
			fullPage: true,
		});
		await page.close();
		groups++;
	}
	for (const status of [401, 403, 404, 503]) {
		const { page, state } = await harness();
		state.status = status;
		await page.goto(`${base}/bank?evidence=${id}`);
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(await submit(page).count(), 0);
		assert.equal(
			(await page.textContent("body")).includes("PRIVATE ERROR"),
			false,
		);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	for (const query of [
		"evidence=",
		"evidence=bad",
		`evidence=${id}&evidence=${id}`,
		`evidence=${id}&request=${id}`,
		`evidence=${id}&quote=${id}`,
	]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bank?${query}`);
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?evidence=${id}`);
		await submit(page).waitFor();
		await fill(page);
		state.value.revision++;
		await submit(page).click();
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	for (const abort of ["claim", "allocate", "put", "complete"]) {
		const { page, state } = await harness();
		await page.goto(`${base}/bank?evidence=${id}`);
		await submit(page).waitFor();
		await fill(page);
		if (abort === "claim") state.abort = abort;
		await submit(page).click();
		if (abort === "claim") {
			await page.locator("main").getByRole("alert").waitFor();
			await submit(page).click();
		}
		await page
			.getByRole("button", { name: "Append corrected claim", exact: true })
			.waitFor();
		await file(page);
		if (abort !== "claim") state.abort = abort;
		await receipt(page).click();
		if (abort !== "claim") {
			await page.locator("main").getByRole("alert").waitFor();
			await receipt(page).click();
		}
		await page.getByText(/READY ·/).waitFor();
		assert.equal(state.puts.length, 1);
		const duplicate = state.posts.filter((x) =>
			x.path.endsWith(
				abort === "claim"
					? "/claims"
					: abort === "allocate"
						? "/uploads"
						: "/complete",
			),
		);
		if (abort !== "put") {
			assert.equal(duplicate.length, 2);
			assert.deepEqual(duplicate[0].body, duplicate[1].body);
		}
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.value = { ...initial(), claimId: claim, reviewState: "REJECTED" };
		await page.goto(`${base}/bank?evidence=${id}`);
		const correction = page.getByRole("button", {
			name: "Append corrected claim",
			exact: true,
		});
		await correction.waitFor();
		assert.equal(state.posts.length, 0);
		await fill(page);
		await correction.click();
		await page
			.getByRole("region", { name: "Request and review status", exact: true })
			.filter({ has: page.getByText("PENDING", { exact: true }) })
			.filter({ has: page.getByText("ACTIVE", { exact: true }) })
			.getByText("REVIEW_PENDING", { exact: true })
			.waitFor();
		assert.ok(state.posts[0].path.endsWith("/resubmissions"));
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.value = { ...initial(), claimId: claim };
		await page.goto(`${base}/bank?evidence=${id}`);
		await receipt(page).waitFor();
		await file(page);
		state.abort = "put";
		state.putOutcome = "UPLOADING";
		await receipt(page).click();
		await page.locator("main").getByRole("alert").waitFor();
		await receipt(page).click();
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(state.puts.length, 1);
		assert.equal(state.posts.length, 1);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.value = { ...initial(), claimId: claim };
		await page.goto(`${base}/bank?evidence=${id}`);
		await receipt(page).waitFor();
		await page.getByLabel("Approved synthetic JPEG/PNG fixture").setInputFiles({
			name: "bad.pdf",
			mimeType: "application/pdf",
			buffer: Buffer.from("SYNTHETIC"),
		});
		await receipt(page).click();
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}

	{
		const { page, state } = await harness();
		state.value = {
			...initial(),
			claimId: claim,
			evidence: [
				{ id: upload, state: "QUARANTINED", expiresAt: initial().expiresAt },
			],
		};
		await page.goto(`${base}/bank?evidence=${id}`);
		await receipt(page).waitFor();
		assert.equal(state.posts.length, 0);
		await receipt(page).click();
		await page.getByText(/READY ·/).waitFor();
		assert.equal(state.puts.length, 0);
		assert.equal(state.posts.length, 1);
		assert.ok(state.posts[0].path.endsWith("/complete"));
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		await page.goto(`${base}/bank?evidence=${id}`);
		await submit(page).waitFor();
		await fill(page);
		state.status = 401;
		await page.evaluate(() => window.dispatchEvent(new Event("focus")));
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(await submit(page).count(), 0);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	{
		const { page, state } = await harness();
		state.replyMismatch = true;
		await page.goto(`${base}/bank?evidence=${id}`);
		await page.locator("main").getByRole("alert").waitFor();
		assert.equal(await submit(page).count(), 0);
		assert.equal(state.posts.length, 0);
		await page.close();
		groups++;
	}
	console.log(
		`PASS ${groups} intercepted synthetic contextual evidence browser groups; no real bank or customer acceptance`,
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

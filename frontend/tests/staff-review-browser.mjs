// Intercepted SYNTHETIC presentation tests; no claim of real backend authorization or E2E.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { pathToFileURL } from "node:url";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const verificationLabels = [
	"TEST / SYNTHETIC IDENTITY VERIFICATION",
	"NO REAL IDENTITY DOCUMENTS",
	"NOT VALID FOR PHYSICAL ADMISSION",
];
const bankLabels = [
	"SYNTHETIC BANK-PAYMENT TEST",
	"DO NOT TRANSFER REAL MONEY",
	"PAYMENT NOT VERIFIED",
	"BOOKING NOT CONFIRMED",
];
const id = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002";
const verification = () => ({
	id,
	venueId: other,
	quoteId: id,
	revision: 1,
	generation: 1,
	superseded: false,
	previousId: null,
	state: "REVIEW_REQUIRED",
	validUntil: null,
	synthetic: true,
	physicalAdmission: false,
	labels: verificationLabels,
	evidence: [],
	history: [],
});
const bank = () => ({
	id,
	venueId: other,
	quoteId: id,
	revision: 1,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels: bankLabels,
	requestState: "REVIEW_PENDING",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-21T10:00:00Z",
	expiresAt: "2026-10-21T10:00:00Z",
	submitBy: "2026-10-21T09:00:00Z",
	reviewBy: "2026-10-21T09:30:00Z",
	overdue: false,
	amountMinor: "10000",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
});

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
	async function harness(kind, width = 1024) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const state = {
			status: 200,
			writes: [],
			reads: 0,
			record: kind === "bank" ? bank() : verification(),
			delay: 0,
			failNetwork: false,
		};
		await page.route("**/api/**", async (route) => {
			const url = new URL(route.request().url());
			if (route.request().method() !== "GET") state.writes.push(url.pathname);
			if (url.pathname === `/api/admin/venues/${other}/${kind}/requests`)
				return route.fulfill({ json: [state.record] });
			if (
				url.pathname === `/api/admin/venues/${other}/${kind}/requests/${id}`
			) {
				state.reads++;
				if (state.failNetwork) return route.abort("failed");
				const record = structuredClone(state.record),
					status = state.status;
				if (state.delay) await new Promise((r) => setTimeout(r, state.delay));
				try {
					return await route.fulfill({
						status,
						json:
							status === 200 ? record : { message: "PRIVATE SECRET EVIDENCE" },
					});
				} catch {
					return;
				}
			}
			return route.fulfill({
				status: 401,
				json: { message: "Sign in required" },
			});
		});
		return { page, state };
	}
	for (const kind of ["verification", "bank"]) {
		const queue = `${base}/admin/${kind}?venue=${other}`,
			detail = `${base}/admin/${kind}/venues/${other}/requests/${id}`;
		for (const width of [320, 375, 768, 1280]) {
			const { page, state } = await harness(kind, width);
			await page.goto(queue);
			await page
				.getByRole("button", {
					name:
						kind === "bank" ? "Load scoped queue" : "Load venue review queue",
					exact: true,
				})
				.click();
			const link = page.getByRole("link", {
				name: `Open exact ${kind} review details`,
				exact: true,
			});
			await link.waitFor();
			await link.focus();
			await page.keyboard.press("Enter");
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();

			const crumbs = page.getByRole("navigation", {
				name: "Administration breadcrumbs",
			});
			assert.equal(await crumbs.count(), 1);
			assert.equal(await page.locator("h1").count(), 1);
			assert.equal(
				await crumbs
					.getByRole("link", {
						name: kind === "bank" ? "Bank review" : "Verification review",
						exact: true,
					})
					.getAttribute("href"),
				`/admin/${kind}?venue=${other}`,
			);
			assert.match(await crumbs.innerText(), new RegExp(other));
			if (width < 1024) {
				await page
					.getByRole("button", { name: "Open navigation", exact: true })
					.click();
			}
			const parent = page
				.getByRole("navigation", { name: "Administration navigation" })
				.getByRole("link", {
					name: kind === "bank" ? "Bank review" : "Verification review",
					exact: true,
				});
			assert.equal(await parent.getAttribute("aria-current"), "location");
			if (width < 1024) await page.keyboard.press("Escape");
			assert.equal(state.writes.length, 0);
			groups++;
			assert.equal(page.url(), detail);
			assert.ok(state.reads > 0);
			assert.equal(state.writes.length, 0);
			await page.reload();
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();
			await page
				.getByRole("link", { name: "Return to review queue", exact: true })
				.click();
			await page
				.getByRole("button", {
					name:
						kind === "bank" ? "Load scoped queue" : "Load venue review queue",
					exact: true,
				})
				.waitFor();
			await page.goBack();
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();
			await page.goForward();
			await page
				.getByRole("button", {
					name:
						kind === "bank" ? "Load scoped queue" : "Load venue review queue",
					exact: true,
				})
				.waitFor();
			assert.equal(state.writes.length, 0);
			await page.goto(detail);
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();
			assert.ok(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
			);
			state.record.revision = 2;
			if (kind === "bank") state.record.reviewState = "REJECTED";
			else {
				state.record.state = "REJECTED";
				state.record.superseded = true;
			}
			await page
				.getByRole("button", {
					name: "Reload protected review details",
					exact: true,
				})
				.click();
			await page
				.getByText("Current server revision: 2", { exact: true })
				.waitFor();
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
		for (const status of [401, 403, 404, 409, 503]) {
			const { page, state } = await harness(kind);
			state.status = status;
			await page.goto(detail);
			await page
				.getByRole("button", {
					name: "Reload protected review details",
					exact: true,
				})
				.waitFor();
			await page.waitForFunction(
				() =>
					!document.body.textContent.includes(
						"Loading current protected review context.",
					),
			);
			assert.equal(
				await page
					.getByRole("region", { name: "Server-authoritative review context" })
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
		{
			const { page, state } = await harness(kind);
			state.record.venueId = id;
			await page.goto(detail);
			await page
				.getByText("Review unavailable, missing, withdrawn or changed", {
					exact: false,
				})
				.waitFor();
			assert.equal(
				await page
					.getByRole("region", { name: "Server-authoritative review context" })
					.count(),
				0,
			);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness(kind);
			await page.goto(detail);
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();
			state.status = 403;
			await page.evaluate(() => window.dispatchEvent(new Event("focus")));
			await page
				.getByText("Current venue review permission is required", {
					exact: false,
				})
				.waitFor();
			assert.equal(
				await page
					.getByRole("region", { name: "Server-authoritative review context" })
					.count(),
				0,
			);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness(kind);
			await page.goto(detail);
			await page
				.getByRole("region", { name: "Server-authoritative review context" })
				.waitFor();
			state.delay = 400;
			await page
				.getByRole("button", {
					name: "Reload protected review details",
					exact: true,
				})
				.click();
			await page.evaluate(() =>
				window.dispatchEvent(new Event("cinenest-session-change")),
			);
			await page.waitForTimeout(500);
			assert.equal(
				await page
					.getByRole("region", { name: "Server-authoritative review context" })
					.count(),
				0,
			);
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness(kind);
			await page.goto(
				`${base}/admin/${kind}/venues/${other}/requests/not-a-uuid`,
			);
			await page
				.getByText("Review unavailable, missing, withdrawn or changed", {
					exact: false,
				})
				.waitFor();
			assert.equal(state.reads, 0);
			await page.close();
			groups++;
		}
		{
			const { page, state } = await harness(kind);
			state.failNetwork = true;
			await page.goto(detail);
			await page
				.getByText("Review unavailable, missing, withdrawn or changed", {
					exact: false,
				})
				.waitFor();
			assert.equal(
				await page
					.getByRole("region", { name: "Server-authoritative review context" })
					.count(),
				0,
			);
			assert.equal(state.writes.length, 0);
			await page.close();
			groups++;
		}
	}
	console.log(
		`PASS ${groups} intercepted synthetic staff review browser groups; NOT backend authorization/E2E or acceptance`,
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
					() => reject(new Error("Owned Next process termination timed out")),
					10000,
				).unref(),
			),
		]);
	}
}

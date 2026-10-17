// Intercepted synthetic UI rehearsal only; PostgreSQL/API claims are tested separately.
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
	{ stdio: "ignore", env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1" } },
);
let browser;
try {
	for (let n = 0; n < 200; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 199) throw new Error("Isolated Next startup unavailable");
	}
	browser = await chromium.launch({ headless: true });
	let groups = 0;
	for (const width of [1440, 390]) {
		const page = await browser.newPage({ viewport: { width, height: 900 } });
		let denied = false,
			conflict = false,
			empty = false,
			state = "REQUESTED",
			revision = 0;
		const evidence = [];
		const commands = [];
		await page.route("**/api/**", async (route) => {
			const request = route.request(),
				path = new URL(request.url()).pathname;
			if (path === "/api/accounts/me")
				return route.fulfill({
					json: { id, displayName: "Synthetic reviewer", synthetic: true },
				});
			if (path === "/api/session/csrf")
				return route.fulfill({
					json: { headerName: "X-CSRF-TOKEN", token: "SYNTHETIC" },
				});
			if (denied)
				return route.fulfill({
					status: 403,
					json: { message: "Synthetic denied" },
				});
			if (conflict)
				return route.fulfill({
					status: 409,
					json: { message: "Changed; refresh" },
				});
			let data;
			if (request.method() === "POST") {
				data = request.postDataJSON();
				commands.push({ path, data });
				if (path.endsWith("/decisions"))
					state =
						data.action === "REVIEW"
							? "UNDER_REVIEW"
							: data.action === "APPROVE"
								? "APPROVED"
								: data.action === "REJECT"
									? "REJECTED"
									: "WITHDRAWN";
				if (path.endsWith("/attempts")) state = "AWAITING_EVIDENCE";
				if (path.endsWith("/evidence") || path.endsWith("/reconciliations")) {
					const recorded = evidence.some((e) => e.fixture === data.fixtureKey);
					evidence.push({
						fixture: data.fixtureKey,
						phase: recorded ? "VERIFIED" : "RECORDED",
						at: new Date().toISOString(),
					});
					if (recorded)
						state =
							data.fixtureKey === "SYNTHETIC-UNKNOWN"
								? "UNKNOWN_RECONCILIATION_REQUIRED"
								: "SIMULATED_CONFIRMED";
				}
				revision++;
			}
			const value = {
				id,
				requestId: id,
				creditId: id,
				venueId: id,
				revision,
				state,
				amountMinor: "100",
				approvedMinor: state === "REQUESTED" ? "0" : "100",
				currency: "LKR",
				availableMinor: "49900",
				destinationVerified:
					state !== "UNDER_REVIEW" ||
					commands.some((c) => c.path.endsWith("/destination-verifications")),
				destinationReference: "SYNTHETIC-PAYER-50000",
				resolution:
					state === "UNKNOWN_RECONCILIATION_REQUIRED"
						? "RECONCILIATION_REQUIRED"
						: "REVIEW_REQUIRED",
				synthetic: true,
				realRepayment: false,
				decisions: [],
				attempts: [
					"AWAITING_EVIDENCE",
					"UNKNOWN_RECONCILIATION_REQUIRED",
					"SIMULATED_CONFIRMED",
				].includes(state)
					? [{ id, sequence: 1, evidence }]
					: [],
				audit: [],
			};
			return route.fulfill({
				json: empty
					? []
					: path.endsWith("/refunds") && request.method() === "GET"
						? [value]
						: value,
			});
		});
		await page.goto(`${base}/admin/refunds`);
		await page.getByLabel("Finance venue ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Load refund queue", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "REQUESTED", exact: true })
			.waitFor();
		await page
			.getByLabel("Reason (synthetic information only)", { exact: true })
			.fill("SYNTHETIC review");
		await page
			.getByRole("button", { name: "Begin independent review", exact: true })
			.focus();
		await page.keyboard.press("Enter");
		await page
			.getByRole("button", {
				name: "Verify controlled original-payer destination",
				exact: true,
			})
			.click();
		await page
			.getByRole("button", {
				name: "Approve and reserve synthetic funds",
				exact: true,
			})
			.click();
		assert.ok(
			await page
				.getByText("APPROVED DOES NOT MEAN REPAID", { exact: false })
				.count(),
		);
		await page
			.getByRole("button", {
				name: "Prepare independent simulated attempt",
				exact: true,
			})
			.click();
		await page
			.getByRole("button", { name: "Record simulated UNKNOWN", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "AWAITING EVIDENCE", exact: true })
			.waitFor();
		await page
			.getByRole("button", {
				name: "Independently verify simulated UNKNOWN",
				exact: true,
			})
			.click();
		await page
			.getByText("UNKNOWN OUTGOING RESULT — DO NOT RESEND", { exact: true })
			.waitFor();
		assert.equal(
			await page
				.getByRole("button", {
					name: "Prepare independent simulated attempt",
					exact: true,
				})
				.count(),
			0,
		);
		await page
			.getByRole("button", { name: "Record simulated CONFIRMED", exact: true })
			.click();
		await page
			.getByRole("button", {
				name: "Independently verify simulated CONFIRMED",
				exact: true,
			})
			.click();
		await page
			.getByRole("heading", { name: "SIMULATED CONFIRMED", exact: true })
			.waitFor();
		assert.ok(
			commands.every(
				(c) => c.data.commandKey && Number.isInteger(c.data.expectedRevision),
			),
		);
		groups += 10;
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		groups++;
		denied = true;
		await page
			.getByRole("button", { name: "Load refund queue", exact: true })
			.click();
		await page.getByText("Synthetic denied", { exact: false }).waitFor();
		assert.equal(
			await page.getByLabel("Finance venue ID", { exact: true }).inputValue(),
			"",
		);
		assert.equal(
			await page
				.getByRole("heading", { name: "SIMULATED CONFIRMED", exact: true })
				.count(),
			0,
		);
		groups++;
		denied = false;
		await page.goto(`${base}/refunds`);
		await page
			.getByLabel(
				"Refund ID to inspect or owned bank request ID to request a refund",
				{ exact: true },
			)
			.fill(id);
		await page
			.getByLabel("Controlled-matched synthetic credit ID", { exact: true })
			.fill(id);
		await page
			.getByLabel("Requested LKR minor units", { exact: true })
			.fill("100");
		await page
			.getByLabel("Reason (synthetic information only)", { exact: true })
			.fill("SYNTHETIC return");
		await page
			.getByRole("button", { name: "Request synthetic return", exact: true })
			.click();
		assert.ok(
			await page
				.getByText("NO REAL MONEY TRANSFERRED", { exact: false })
				.count(),
		);
		conflict = true;
		await page
			.getByRole("button", { name: "Load refund status", exact: true })
			.click();
		await page.getByText("Changed; refresh", { exact: false }).waitFor();
		groups += 2;
		conflict = false;
		empty = true;
		await page
			.getByRole("button", { name: "Load refund status", exact: true })
			.click();
		await page
			.getByText("No refund records loaded.", { exact: false })
			.waitFor();
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} intercepted synthetic refund browser groups (desktop/mobile, keyboard, denied, conflict, empty, lifecycle); no real transfer evidence`,
	);
} finally {
	if (browser) await browser.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

// Intercepted browser fixtures: presentation and interaction evidence,not real-backend E2E.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import {
	bookingId,
	venueId,
	accountId,
	requestId,
	preview,
	committed,
	customerBooking,
	staffBooking,
} from "./booking-cancellation-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const phase = process.env.CN_CANCELLATION_PHASE ?? "after",
	socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
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
let browser;
const evidence = [];
let checks = 0;
try {
	for (let n = 0; n < 150; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		if (n === 149) throw Error("Server unavailable");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch();
	await mkdir(`../.cache/cancellation-fe/${phase}`, { recursive: true });
	for (const kind of ["customer", "staff"])
		for (const width of [375, 768, 1280]) {
			const page = await browser.newPage({ viewport: { width, height: 1000 } });
			let status = 200,
				reason = "ELIGIBLE",
				done = false,
				slow = false,
				postMode = "OK",
				release,
				posts = [],
				account = accountId;
			const calls = [];
			await page.route("**/api/**", async (route) => {
				const req = route.request(),
					u = new URL(req.url());
				calls.push({ method: req.method(), path: u.pathname });
				const json = (data, code = 200) =>
					route.fulfill({ json: data, status: code }).catch(() => {});
				if (u.pathname === "/api/accounts/me")
					return json({ accountId: account, eligibility: "ELIGIBLE" });
				if (u.pathname === "/api/session/csrf")
					return json({
						headerName: "X-CSRF-TOKEN",
						token: "SYNTHETIC-CSRF-ONLY",
					});
				if (/\/cancellations?$/.test(u.pathname)) {
					if (slow) await new Promise((r) => (release = r));
					if (status !== 200)
						return json(
							{ message: "PRIVATE EVIDENCE MUST NOT RENDER" },
							status,
						);
					if (req.method() === "POST") {
						posts.push(req.postDataJSON());
						assert.equal(req.headers()["x-csrf-token"], "SYNTHETIC-CSRF-ONLY");
						if (posts.length > 1) assert.deepEqual(posts.at(-1), posts[0]);
						if (postMode === "UNKNOWN") {
							postMode = "OK";
							return route.abort("failed");
						}
						done = true;
						return json(committed());
					}
					return json(
						done
							? committed()
							: {
									...preview(),
									eligible: reason === "ELIGIBLE",
									reason,
									...(kind === "staff"
										? { refundPrincipalMinor: undefined, currency: undefined }
										: {}),
								},
					);
				}
				if (u.pathname === `/api/bookings/${bookingId}`)
					return json({
						...customerBooking(),
						...(done
							? {
									state: "SIMULATED_CANCELLED",
									syntheticBookingStatus: "SIMULATED_CANCELLED",
								}
							: {}),
					});
				if (u.pathname === `/api/bank/requests/${requestId}`)
					return json({
						id: requestId,
						revision: 1,
						requestState: "OPEN",
						reviewState: "REVIEWED",
						history: [],
						financial: {
							verifiedMatchedMinor: "50000",
							fundingShortfallMinor: 0,
							bookingId,
							syntheticBookingStatus: done
								? "SIMULATED_CANCELLED"
								: "SIMULATED_CONFIRMED",
							allocatedMinor: "30007",
							outstandingMinor: "0",
							syntheticFundingStatus: "SIMULATED_ALLOCATED",
						},
					});
				if (u.pathname === `/api/bank/requests/${requestId}/refunds`)
					return json({
						items: [],
						page: 0,
						size: 10,
						hasNext: false,
						observedAt: "2026-10-23T04:00:00Z",
						synthetic: true,
					});
				if (u.pathname === `/api/admin/venues/${venueId}/bookings/${bookingId}`)
					return json({
						booking: {
							...staffBooking(),
							state: done ? "SIMULATED_CANCELLED" : "SIMULATED_CONFIRMED",
						},
						observedAt: "2026-10-23T04:00:00Z",
						synthetic: true,
						realBooking: false,
					});
				return json({}, 403);
			});
			await page.goto(
				kind === "customer"
					? `${base}/bookings/${bookingId}`
					: `${base}/admin/bookings/venues/${venueId}/${bookingId}`,
			);
			await page
				.getByRole("heading", {
					name:
						kind === "customer"
							? "Purchased snapshot"
							: "Protected staff booking detail",
					exact: true,
				})
				.waitFor();
			if (phase === "before") {
				assert.equal(
					await page
						.getByRole("button", {
							name: "Check Cancellation Eligibility",
							exact: true,
						})
						.count(),
					0,
				);
				await page.screenshot({
					path: `../.cache/cancellation-fe/before/${kind}-${width}.png`,
				});
				checks++;
				await page.close();
				continue;
			}
			const panel = page.locator(".cancellation-panel");
			await panel.waitFor();
			assert.equal(posts.length, 0);
			const check = panel.getByRole("button", {
				name: "Check Cancellation Eligibility",
				exact: true,
			});
			await check.click();
			await panel
				.getByRole("heading", { name: "Review the consequences" })
				.waitFor();
			assert.equal(posts.length, 0);
			assert.equal(await panel.getByRole("checkbox").isChecked(), false);
			assert.equal(
				await panel
					.getByRole("button", {
						name: "Confirm synthetic cancellation",
						exact: true,
					})
					.isDisabled(),
				true,
			);
			await panel
				.getByLabel("Cancellation reason", { exact: true })
				.fill("Synthetic schedule change");
			await panel.getByRole("checkbox").check();
			await page.waitForFunction(() => {
				const button = document.querySelector(
					'.cancellation-panel button[type="submit"]',
				);
				return button && !button.disabled;
			});
			await panel.locator('button[type="submit"]').evaluate(async (button) => {
				await Promise.all(
					button
						.getAnimations()
						.map((animation) => animation.finished.catch(() => {})),
				);
			});
			await panel.locator('button[type="submit"]').evaluate(async () => {
				await new Promise(requestAnimationFrame);
				await new Promise(requestAnimationFrame);
			});
			const buttonPaint = await panel
				.locator('button[type="submit"]')
				.evaluate((button) => ({
					disabled: button.disabled,
					background: getComputedStyle(button).background,
					opacity: getComputedStyle(button).opacity,
					transition: getComputedStyle(button).transition,
					animations: button.getAnimations().length,
				}));
			console.log(JSON.stringify({ kind, width, buttonPaint }));
			await panel.scrollIntoViewIfNeeded();
			await page.screenshot({
				path: `../.cache/cancellation-fe/after/${kind}-${width}.png`,
				animations: "disabled",
			});
			const overflow = await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			);
			assert.equal(overflow, false);
			assert.equal(await page.locator("main").count(), 1);
			await panel.getByRole("checkbox").focus();
			await page.keyboard.press("Tab");
			assert(
				(await page.locator(":focus").innerText()).includes(
					"Confirm synthetic cancellation",
				),
			);
			assert.notEqual(
				await page.evaluate(
					() => getComputedStyle(document.activeElement).outlineStyle,
				),
				"none",
			);
			if (width === 375) await page.keyboard.press("Enter");
			else
				await panel
					.getByRole("button", {
						name: "Confirm synthetic cancellation",
						exact: true,
					})
					.evaluate((b) => {
						b.click();
						b.click();
					});
			await panel
				.getByText("Reserved for financial review.", { exact: true })
				.waitFor();
			assert.equal(posts.length, 1);
			assert.equal(posts[0].expectedPurchasedRevision, 1);
			assert.equal(posts[0].expectedLifecycleVersion, 0);
			assert(!page.url().includes("commandKey"));
			assert(
				!(await page.locator("body").innerText()).includes("PRIVATE EVIDENCE"),
			);
			await panel
				.getByRole("button", {
					name: "Confirm synthetic cancellation",
					exact: true,
				})
				.count()
				.then((n) => assert.equal(n, 0));
			// New protected read,then denial must clear references and consequences.
			status = 403;
			await check.click();
			await panel.getByText(/Cancellation denied/).waitFor();
			assert.equal(
				await panel
					.getByText("RESERVED_REVIEW_REQUIRED", { exact: true })
					.count(),
				0,
			);
			status = 200;
			done = false;
			for (const blocked of [
				"CREDENTIAL_EVER_ISSUED",
				"SESSION_STARTED",
				"AMENDMENT_PENDING",
				"FINANCIAL_REVIEW_REQUIRED",
			]) {
				reason = blocked;
				await check.click();
				await panel
					.getByText(
						{
							CREDENTIAL_EVER_ISSUED:
								"A credential was previously issued. Even expired or revoked credentials exclude this workflow.",
							SESSION_STARTED:
								"The purchased session has started. Pre-start cancellation is unavailable.",
							AMENDMENT_PENDING:
								"An amendment is pending. Resolve it through the existing amendment workflow first.",
							FINANCIAL_REVIEW_REQUIRED:
								"Funding needs independent financial review. Cancellation cannot proceed automatically.",
						}[blocked],
						{ exact: true },
					)
					.waitFor();
				assert.equal(
					await panel
						.getByRole("button", {
							name: "Confirm synthetic cancellation",
							exact: true,
						})
						.count(),
					0,
				);
			}
			reason = "ELIGIBLE";
			for (const failure of [400, 401, 404, 409, 503]) {
				status = failure;
				await check.click();
				await panel
					.locator('[role="alert"], [role="status"]')
					.filter({ hasText: /invalid|unavailable|changed/i })
					.first()
					.waitFor();
				assert.equal(await panel.getByRole("checkbox").count(), 0);
			}
			status = 200;
			// Fresh rehearsal: uncertain POST is never retried automatically and an explicit retry keeps the original payload.
			posts = [];
			done = false;
			await page.reload();
			await panel.waitFor();
			await check.click();
			await panel
				.getByRole("heading", { name: "Review the consequences" })
				.waitFor();
			await panel
				.getByLabel("Cancellation reason", { exact: true })
				.fill("Synthetic uncertain cancellation");
			await panel.getByRole("checkbox").check();
			postMode = "UNKNOWN";
			await panel
				.getByRole("button", {
					name: "Confirm synthetic cancellation",
					exact: true,
				})
				.click();
			await panel.getByText(/Cancellation outcome is uncertain/).waitFor();
			assert.equal(posts.length, 1);
			assert.equal(await panel.getByRole("checkbox").count(), 0);
			await check.click();
			await panel
				.getByRole("button", {
					name: "Retry same cancellation command",
					exact: true,
				})
				.waitFor();
			assert.equal(await panel.getByRole("checkbox").isChecked(), false);
			assert.equal(
				await panel
					.getByLabel("Cancellation reason", { exact: true })
					.getAttribute("readonly"),
				"",
			);
			await panel.getByRole("checkbox").check();
			await panel
				.getByRole("button", {
					name: "Retry same cancellation command",
					exact: true,
				})
				.click();
			await panel
				.getByText("Reserved for financial review.", { exact: true })
				.waitFor();
			assert.equal(posts.length, 2);
			assert.deepEqual(posts[0], posts[1]);
			reason = "ELIGIBLE";
			slow = true;
			await check.click();
			await page.waitForTimeout(30);
			await page.evaluate(() =>
				window.dispatchEvent(new Event("cinenest-session-change")),
			);
			slow = false;
			if (release) release();
			await page.waitForTimeout(60);
			assert.equal(
				await panel
					.getByRole("heading", { name: "Review the consequences" })
					.count(),
				0,
			);
			assert(
				calls
					.filter((c) => c.method !== "GET")
					.every((c) => c.path.endsWith("/cancellations")),
			);
			evidence.push({
				kind,
				width,
				mainLandmarks: 1,
				horizontalOverflow: overflow,
				explicitPosts: calls.filter((c) => c.method === "POST").length,
				buttonPaint,
				classification: "INTERCEPTED_SYNTHETIC_BROWSER",
			});
			checks += 12;
			await page.close();
		}
	await writeFile(
		`../.cache/cancellation-fe/${phase}/observations.json`,
		JSON.stringify({ checks, evidence }, null, 2),
	);
	console.log(
		`PASS ${phase === "before" ? checks : evidence.length} ${phase} customer/staff responsive scenarios;intercepted fixtures only`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

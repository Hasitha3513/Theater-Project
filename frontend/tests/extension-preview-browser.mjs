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
const phase = process.env.CN_EXTENSION_PHASE ?? "after",
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
	browser = await chromium.launch({
		args: ["--disable-dev-shm-usage", "--disable-gpu"],
	});
	await mkdir(`../.cache/extension-preview-fe/${phase}`, { recursive: true });
	for (const kind of ["customer"])
		for (const width of [375, 768, 1280]) {
			const page = await browser.newPage({ viewport: { width, height: 1000 } });
			let status = 200,
				reason = "ELIGIBLE",
				done = false,
				slow = false,
				postMode = "OK",
				release,
				posts = [],
				extensionStatus = 200,
				extensionReason = "ELIGIBLE",
				extensionSlow = false,
				releaseExtension,
				extensionCalls = [],
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
				if (/\/extension-(eligibility|quotes)$/.test(u.pathname)) {
					extensionCalls.push({ path: u.pathname, method: req.method() });
					if (extensionSlow) await new Promise((r) => (releaseExtension = r));
					if (extensionStatus !== 200)
						return json(
							{ message: "PRIVATE MUST NOT RENDER" },
							extensionStatus,
						);
					const body = {
						bookingId,
						purchasedRevision: 1,
						lifecycleVersion: 0,
						eligible: extensionReason === "ELIGIBLE",
						reason: extensionReason,
						observedAt: "2026-10-24T04:00:00Z",
						synthetic: true,
						warning:
							"Indicative synthetic extension preview. No additional time is reserved. No payment has been requested. Your booking has not changed.",
						timeReserved: false,
						paymentRequested: false,
						bookingChanged: false,
						occupancy: "UNKNOWN",
						readiness: "UNVERIFIED",
						...(extensionReason === "ELIGIBLE"
							? {
									durationUnits: {
										incrementMinutes: 30,
										minimumAdditionalMinutes: 30,
										maximumAdditionalMinutes: 120,
									},
								}
							: {}),
					};
					if (req.method() === "POST") {
						assert.equal(req.headers()["x-csrf-token"], "SYNTHETIC-CSRF-ONLY");
						assert.deepEqual(req.postDataJSON(), {
							additionalMinutes: 30,
							expectedPurchasedRevision: 1,
							expectedLifecycleVersion: 0,
						});
						Object.assign(body, {
							currentDurationMinutes: 180,
							additionalMinutes: 30,
							proposedDurationMinutes: 210,
							additionalMinor: "12345",
							currency: "LKR",
							packageVersionId: venueId,
							pricingBasis: "FROZEN_PURCHASED_TERMS",
						});
					}
					return json(body);
				}
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
				await page.screenshot({
					path: `../.cache/extension-preview-fe/before/customer-${width}.png`,
				});
				await page.close();
				continue;
			}
			const panel = page.locator(".extension-preview-panel"),
				check = panel.getByRole("button", {
					name: "Check Extension Options",
					exact: true,
				});
			await panel.waitFor();
			assert.equal(extensionCalls.length, 0);
			await check.focus();
			assert.equal(
				await check.evaluate((el) => el === document.activeElement),
				true,
			);
			const focusStyle = await check.evaluate(
				(el) => getComputedStyle(el).outlineStyle,
			);
			assert.notEqual(focusStyle, "none");
			await page.keyboard.press("Enter");
			const input = panel.getByLabel("Additional duration (minutes)", {
				exact: true,
			});
			await input.waitFor();
			assert.equal(extensionCalls.filter((x) => x.method === "POST").length, 0);
			await input.fill("31");
			assert.equal(
				await panel
					.getByRole("button", {
						name: "Preview Additional Price",
						exact: true,
					})
					.isDisabled(),
				true,
			);
			await input.fill("30");
			await panel
				.getByRole("button", { name: "Preview Additional Price", exact: true })
				.click();
			await panel
				.getByRole("heading", { name: "Additional time preview", exact: true })
				.waitFor();
			assert.ok((await panel.innerText()).includes("LKR 123.45"));
			assert.ok((await panel.innerText()).includes("13:30"));
			assert.equal(await page.locator("main").count(), 1);
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
				true,
			);
			await panel.scrollIntoViewIfNeeded();
			await page.screenshot({
				path: `../.cache/extension-preview-fe/after/customer-${width}.png`,
			});
			await panel.screenshot({
				path: `../.cache/extension-preview-fe/after/panel-${width}.png`,
			});
			const panelHeight=(await panel.boundingBox()).height;
 const summaryColumns=await panel.locator(".extension-price-summary dl").evaluate(el=>getComputedStyle(el).gridTemplateColumns);
 for (const code of [400, 401, 403, 404, 409, 429, 503]) {
				extensionStatus = code;
				await check.click();
				await panel.locator(".state:not(.state-loading)").waitFor();
				assert.equal(
					await panel
						.getByRole("heading", {
							name: "Additional time preview",
							exact: true,
						})
						.count(),
					0,
				);
				assert.equal(
					(await page.innerText("body")).includes("PRIVATE MUST NOT RENDER"),
					false,
				);
				checks++;
			}
			extensionStatus = 200;
			for (const why of [
				"SESSION_STARTED",
				"CREDENTIAL_EVER_ISSUED",
				"AMENDMENT_PENDING",
				"EXTENSION_PENDING",
			]) {
				extensionReason = why;
				await check.click();
				await page.waitForFunction(
					() =>
						!document.querySelector(".extension-preview-panel > button")
							?.disabled,
				);
				assert.equal(
					await panel
						.getByRole("button", {
							name: "Preview Additional Price",
							exact: true,
						})
						.count(),
					0,
				);
				checks++;
			}
			extensionReason = "ELIGIBLE";
			extensionSlow = true;
			await check.click();
			await page.waitForFunction(
				() =>
					document.querySelector(".extension-preview-panel > button")?.disabled,
			);
			await page.evaluate(() =>
				window.dispatchEvent(new Event("cinenest-session-change")),
			);
			if (releaseExtension) releaseExtension();
			extensionSlow = false;
			await page.waitForTimeout(100);
			assert.equal(
				await panel
					.getByRole("heading", {
						name: "Additional time preview",
						exact: true,
					})
					.count(),
				0,
			);
			checks++;
            await page.reload();
            await panel.waitFor();
			await check.click();
			await input.waitFor();
			assert.equal(
				calls.filter(
					(x) => x.method !== "GET" && !x.path.endsWith("/extension-quotes"),
				).length,
				0,
			);
			evidence.push({
				width,
                panelHeight,
                summaryColumns,
				mainLandmarks: 1,
				horizontalOverflow: false,
				focusStyle,
				explicitPreview: true,
				privateErrorsHidden: true,
				noFinancialOrBookingMutations: true,
			});
			checks++;
			await page.close();
		}
	await writeFile(
		`../.cache/extension-preview-fe/${phase}/evidence.json`,
		JSON.stringify(
			{
				kind: "INTERCEPTED_BROWSER_FIXTURES_NOT_BACKEND_E2E",
				checks,
				evidence,
			},
			null,
			2,
		),
	);
	console.log(
		`PASS ${checks} extension preview browser checks at 375/768/1280; intercepted fixtures only`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

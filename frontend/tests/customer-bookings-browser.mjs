// Intercepted synthetic presentation rehearsal; not live financial/customer acceptance.
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
	id = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002";
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
let browser;
try {
	for (let i = 0; i < 200; i++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (i === 199) throw Error("No isolated server");
	}
	browser = await chromium.launch({ headless: true });
	let groups = 0;
	for (const width of [1440, 768, 390, 320]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let denied = false,
			empty = false,
			delay = false,
			refundState = "APPROVED",
			amendMode = false,
			amendConflict = false,
			amendDelay = false,
			amendmentState = "PROPOSED";
		const calls = [];
		const purchase = {
			name: "Historical synthetic package",
			description: "Immutable purchased terms, not current catalog",
			packageVersionId: id,
			sessionStart: "2026-10-18T10:00:00Z",
			sessionEnd: "2026-10-18T13:00:00Z",
			timezone: "Asia/Colombo",
			roomId: id,
			amountMinor: "30007",
			currency: "LKR",
			lines: [
				{
					lineId: "BASE",
					kind: "BASE",
					label: "Purchased session",
					quantity: "1",
					amountMinor: "30007",
				},
			],
		};
		const booking = {
			id,
			requestId: other,
			venueId: id,
			state: "SIMULATED_CONFIRMED",
			syntheticBookingStatus: "SIMULATED_CONFIRMED",
			revision: 1,
			purchase,
			synthetic: true,
			purchasedAt: "2026-10-18T09:00:00Z",
			confirmedAt: "2026-10-18T09:00:00Z",
			observedAt: "2026-10-18T11:00:00Z",
		};
		const original = {
			...purchase,
			package: { name: purchase.name },
			guests: 4,
			optionalServiceIds: [],
			paymentMethod: "BANK",
			totalMinor: "30007",
		};
		const proposed = {
			...original,
			sessionStart: "2026-10-20T08:30:00Z",
			sessionEnd: "2026-10-20T11:30:00Z",
		};
		const amendment = () => ({
			id: other,
			bookingId: id,
			revision: amendmentState === "PROPOSED" ? 0 : 1,
			state: amendmentState,
			proposalHash: "a".repeat(64),
			expiresAt: "2026-10-18T16:00:00Z",
			original,
			proposed,
			differenceMinor: "0",
			synthetic: true,
			realOperations: false,
			serverNow: "2026-10-18T15:55:00Z",
		});
		const funding = {
			allocatedMinor: "30007",
			outstandingMinor: "0",
			fundingShortfallMinor: "0",
			verifiedMatchedMinor: "50000",
			status: "SIMULATED_ALLOCATED",
			bookingStatus: "SIMULATED_CONFIRMED",
			bookingId: id,
			realPayment: false,
		};
		await page.route("**/api/**", async (route) => {
			const u = new URL(route.request().url());
			calls.push([u.pathname, route.request().method()]);
			const json = (value, status = 200) =>
				route.fulfill({ status, json: value });
			if (u.pathname === "/api/accounts/me")
				return json({ accountId: id, eligibility: "ELIGIBLE" });
			if (denied) return json({ message: "Denied" }, 403);
			if (delay) await new Promise((r) => setTimeout(r, 350));
			const envelope = (items) => ({
				items: empty ? [] : items,
				page: Number(u.searchParams.get("page") ?? 0),
				size: 10,
				hasNext: u.searchParams.get("page") !== "1",
				observedAt: booking.observedAt,
				synthetic: true,
			});

			if (amendMode) {
				if (u.pathname === "/api/session/csrf")
					return json({ token: "SYNTHETIC-CSRF", headerName: "X-CSRF-TOKEN" });
				if (u.pathname.includes("/availability"))
					return json({ available: true, configuration: "SYNTHETIC" });
				if (u.pathname === "/api/holds")
					return json({ holdId: other, revision: 0 });
				if (u.pathname === "/api/quotes")
					return json({
						quoteId: other,
						revision: 1,
						contentHash: "b".repeat(64),
					});
				if (u.pathname.startsWith(`/api/bookings/${id}/amendments`)) {
					if (amendDelay) await new Promise((r) => setTimeout(r, 400));
					if (amendConflict && u.pathname.endsWith("/confirm"))
						return json(
							{
								message:
									"Credential history or stale proposal denies confirmation",
							},
							409,
						);
					if (u.pathname.endsWith("/confirm")) {
						amendmentState = "CONFIRMED";
						booking.revision = 2;
						booking.latestAmendmentId = other;
						booking.purchase = {
							...purchase,
							sessionStart: proposed.sessionStart,
							sessionEnd: proposed.sessionEnd,
						};
					}
					if (u.pathname.endsWith("/withdrawals")) amendmentState = "WITHDRAWN";
					return json(amendment());
				}
			}
			if (u.pathname === "/api/bookings") return json(envelope([booking]));
			if (u.pathname === `/api/bookings/${id}`) return json(booking);
			if (u.pathname === "/api/bank/requests")
				return json(
					envelope([
						{
							id: other,
							revision: 2,
							state: "OPEN",
							reviewState: "NEEDS_INFORMATION",
							claimSubmitted: true,
							amountMinor: "30007",
							currency: "LKR",
							expiresAt: "2026-10-18T12:00:00Z",
							submitBy: "2026-10-18T11:30:00Z",
							reviewBy: "2026-10-18T11:45:00Z",
							funding: {
								...funding,
								status: "UNFUNDED",
								bookingStatus: "NOT_CONFIRMED",
								bookingId: null,
							},
							resolutions: [{ kind: "PARTIAL", state: "OPEN" }],
							synthetic: true,
						},
					]),
				);
			if (u.pathname === `/api/bank/requests/${other}`)
				return json({
					id: other,
					revision: 2,
					requestState: "OPEN",
					reviewState: "REVIEWED",
					financial: {
						syntheticFundingStatus: "SIMULATED_ALLOCATED",
						verifiedMatchedMinor: "50000",
						allocatedMinor: 30007,
						outstandingMinor: 0,
						fundingShortfallMinor: 0,
						bookingId: id,
					},
					history: [
						{
							action: "REVIEWED",
							reason: "Recorded synthetic review only",
							at: booking.observedAt,
						},
					],
				});
			if (u.pathname === `/api/bank/requests/${other}/refunds`)
				return json(
					envelope([
						{
							id: other,
							requestId: other,
							venueId: id,
							revision: 2,
							state: refundState,
							amountMinor: "100",
							currency: "LKR",
							resolution: "REVIEW_REQUIRED",
							synthetic: true,
							realRepayment: false,
						},
					]),
				);
			return json({}, 404);
		});
		await page.goto(`${base}/bookings`);
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page
			.getByText("Historical synthetic package", { exact: true })
			.waitFor();
		assert.ok(
			await page
				.getByText("Pending/unconfirmed request — not a booking")
				.isVisible(),
		);
		assert.ok(
			await page
				.getByRole("heading", { name: "Current", exact: true })
				.isVisible(),
		);
		groups++;
		await page
			.getByRole("button", { name: "Next", exact: true })
			.first()
			.click();
		await page.getByText("Page 2", { exact: true }).waitFor();
		assert.ok(calls.every((c) => c[1] === "GET"));
		groups++;
		await page.goto(`${base}/bookings/${id}`);
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page.getByRole("heading", { name: "Purchased snapshot" }).waitFor();
		assert.ok(
			await page
				.getByText("Original purchase: LKR 300.07", { exact: false })
				.isVisible(),
		);
		assert.ok(
			await page
				.getByText("Recorded synthetic review only", { exact: false })
				.isVisible(),
		);
		assert.ok(
			await page
				.getByRole("link", { name: "Open protected access status" })
				.isVisible(),
		);
		assert.ok(
			await page
				.getByRole("link", { name: "Open protected notifications" })
				.isVisible(),
		);
		assert.ok(!calls.some((c) => c[1] !== "GET"));
		groups++;
		for (const state of [
			"UNKNOWN_RECONCILIATION_REQUIRED",
			"SIMULATED_CONFIRMED",
		]) {
			refundState = state;
			await page
				.getByRole("button", { name: "Refresh booking history" })
				.click();
			await page
				.getByRole("heading", { name: "Synthetic refund status" })
				.waitFor();
			assert.ok(
				(await page.textContent("main")).includes(`${state} — LKR 1.00`),
			);
		}
		groups++;
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.keyboard.press("Tab");
		assert.ok(
			await page.evaluate(() => document.activeElement !== document.body),
		);
		if (width === 390)
			await page.screenshot({
				path: "/tmp/cinenest-customer-mobile.png",
				fullPage: true,
			});
		groups++;
		denied = true;
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page
			.getByText("Unavailable, signed out, denied or changed.", {
				exact: false,
			})
			.waitFor();
		assert.equal(
			await page.getByRole("heading", { name: "Purchased snapshot" }).count(),
			0,
		);
		denied = false;
		groups++;
		await page.goto(`${base}/bookings`);
		empty = true;
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page.getByText("No owned bookings.", { exact: true }).waitFor();
		assert.ok(
			await page
				.getByText("No owned bank-payment requests.", { exact: true })
				.isVisible(),
		);
		empty = false;
		groups++;
		delay = true;
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page.getByText("Loading owned history…", { exact: true }).waitFor();
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		await page.waitForTimeout(800);
		assert.equal(
			await page
				.getByText("Historical synthetic package", { exact: true })
				.count(),
			0,
		);
		assert.ok(
			await page.evaluate(
				() => localStorage.length === 0 && sessionStorage.length === 0,
			),
		);
		groups++;

		// New amendment UI uses only intercepted synthetic contracts; no backend/financial proof.
		delay = false;
		amendMode = true;
		booking.amendmentPlanning = {
			packageId: id,
			guests: 4,
			optionalServiceIds: [],
			paymentMethod: "BANK",
		};
		await page.goto(`${base}/bookings/${id}`);
		await page.getByRole("button", { name: "Refresh booking history" }).click();
		await page
			.getByLabel("Replacement session start (venue local)")
			.fill("2026-10-20T14:00");
		await page
			.getByRole("button", { name: "Prepare synthetic proposal" })
			.click();
		await page.getByText("PROPOSED", { exact: true }).waitFor();
		assert.ok(
			await page
				.getByText("Financial difference: LKR 0.00.", { exact: false })
				.isVisible(),
		);
		assert.ok(
			await page
				.getByRole("button", { name: "Confirm synthetic reschedule" })
				.isDisabled(),
		);
		groups++;
		await page
			.getByRole("checkbox", {
				name: "I accept this exact replacement interval and unchanged purchased terms.",
			})
			.check();
		amendConflict = true;
		await page
			.getByRole("button", { name: "Confirm synthetic reschedule" })
			.click();
		await page
			.getByRole("alert")
			.filter({ hasText: "Credential history or stale" })
			.waitFor();
		assert.equal(booking.revision, 1);
		assert.equal(amendmentState, "PROPOSED");
		groups++;
		amendConflict = false;
		await page.getByRole("button", { name: "Withdraw proposal" }).click();
		await page.getByText("WITHDRAWN", { exact: true }).waitFor();
		assert.equal(booking.revision, 1);
		groups++;
		amendmentState = "EXPIRED";
		await page.getByRole("button", { name: "Refresh amendment" }).click();
		await page.getByText("EXPIRED", { exact: true }).waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "Confirm synthetic reschedule" })
				.count(),
			0,
		);
		groups++;
		amendmentState = "PROPOSED";
		await page.getByRole("button", { name: "Refresh amendment" }).click();
		await page.getByText("PROPOSED", { exact: true }).waitFor();
		await page
			.getByRole("checkbox", {
				name: "I accept this exact replacement interval and unchanged purchased terms.",
			})
			.check();
		await page
			.getByRole("button", { name: "Confirm synthetic reschedule" })
			.click();
		await page
			.getByText("History: retained immutable revision 2", { exact: false })
			.waitFor();
		groups++;
		await page.getByRole("button", { name: "Refresh amendment" }).click();
		await page.getByText("CONFIRMED", { exact: true }).waitFor();
		assert.ok(
			await page
				.getByText("Backend confirmed revision 2", { exact: false })
				.isVisible(),
		);
		assert.ok(
			await page
				.getByRole("link", { name: "Protected Access workspace" })
				.isVisible(),
		);
		groups++;
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.getByRole("button", { name: "Refresh amendment" }).focus();
		assert.ok(
			await page
				.getByRole("button", { name: "Refresh amendment" })
				.evaluate((e) => e === document.activeElement),
		);
		groups++;
		if (width === 390)
			await page.screenshot({
				path: "/tmp/cinenest-amend-mobile.png",
				fullPage: true,
			});
		amendDelay = true;
		await page.getByRole("button", { name: "Refresh amendment" }).click();
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		await page.waitForTimeout(650);
		assert.equal(
			await page
				.getByText("Backend confirmed revision 2", { exact: false })
				.count(),
			0,
		);
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} intercepted customer browser groups at 1440/768/390/320; synthetic presentation only`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

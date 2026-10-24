// Intercepted browser fixtures: UI/security interaction evidence, NOT real-backend E2E.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import {
	bookingId,
	venueId,
	accountId,
	requestId,
} from "./booking-cancellation-fixtures.mjs";
import {
	booking,
	reservation,
	recovery,
	reserveId,
} from "./extension-reservation-fixtures.mjs";
import { extensionWarning } from "../lib/extension-preview.ts";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
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
	{ stdio: "ignore" },
);
let browser;
let checks = 0;
const evidence = [];
const out = "../.cache/extension-reservation-fe";
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
	await mkdir(out, { recursive: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let v = reservation(),
			createMode = "OK",
			withdrawMode = "OK",
			statusCode = 200,
			recoveryCode = 200,
			accountCode = 200,
			quoteRevision = 1,
			calendarSlow = false,
			releaseCalendar,
			createCount = 0,
			withdrawCount = 0,
			recoveryCount = 0;
		const mutations = [];
		const quote = () => ({
			bookingId,
			purchasedRevision: quoteRevision,
			lifecycleVersion: 0,
			eligible: true,
			reason: "ELIGIBLE",
			observedAt: "2026-10-24T04:00:00Z",
			synthetic: true,
			warning: extensionWarning,
			timeReserved: false,
			paymentRequested: false,
			bookingChanged: false,
			occupancy: "UNKNOWN",
			readiness: "UNVERIFIED",
			durationUnits: {
				incrementMinutes: 30,
				minimumAdditionalMinutes: 30,
				maximumAdditionalMinutes: 120,
			},
		});
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				u = new URL(req.url()),
				p = u.pathname;
			const json = (data, code = 200) =>
				route.fulfill({ json: data, status: code }).catch(() => {});
			if (req.method() !== "GET")
				mutations.push({ path: p, body: req.postDataJSON() });
			if (p === "/api/accounts/me")
				return json({ accountId, eligibility: "ELIGIBLE" }, accountCode);
			if (p === "/api/session/csrf")
				return json({ headerName: "X-CSRF-TOKEN", token: "SYNTHETIC-CSRF" });
			if (p === `/api/bookings/${bookingId}`) return json(booking());
			if (p === `/api/bank/requests/${requestId}`)
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
						syntheticBookingStatus: "SIMULATED_CONFIRMED",
						allocatedMinor: "30007",
						outstandingMinor: "0",
						syntheticFundingStatus: "SIMULATED_ALLOCATED",
					},
				});
			if (p === `/api/bank/requests/${requestId}/refunds`)
				return json({
					items: [],
					page: 0,
					size: 10,
					hasNext: false,
					observedAt: "2026-10-23T04:00:00Z",
					synthetic: true,
				});
			if (p.endsWith("/extension-eligibility")) return json(quote());
			if (p.endsWith("/extension-quotes")) {
				assert.equal(req.headers()["x-csrf-token"], "SYNTHETIC-CSRF");
				return json({
					...quote(),
					currentDurationMinutes: 180,
					additionalMinutes: 30,
					proposedDurationMinutes: 210,
					additionalMinor: "12345",
					currency: "LKR",
					packageVersionId: venueId,
					pricingBasis: "FROZEN_PURCHASED_TERMS",
				});
			}
			if (p.endsWith("/availability/calendar")) {
				if (calendarSlow) await new Promise((r) => (releaseCalendar = r));
				return json({
					roomId: venueId,
					venueId,
					date: "2026-11-01",
					durationMinutes: 210,
					guests: 4,
					timezone: "Asia/Colombo",
					configuration: "room-v1:venue-v1",
					observedAt: "2026-10-24T04:00:00Z",
					evidenceLevel: "INVENTORY_ONLY",
					complete: true,
					items: [],
				});
			}
			if (p === `/api/bookings/${bookingId}/extension-reservations`) {
				createCount++;
				assert.equal(req.headers()["x-csrf-token"], "SYNTHETIC-CSRF");
				const body = req.postDataJSON();
				assert.equal(body.expectedAdditionalMinor, "12345");
				assert.equal(body.expectedConfiguration, "room-v1:venue-v1");
				assert.equal(body.acknowledged, true);
				if (createMode === "LOST") {
					createMode = "OK";
					return route.abort("failed");
				}
				if (createMode === "CONFLICT")
					return json({ message: "PRIVATE INTERNAL" }, 409);
				return json(v);
			}
			if (p.includes("/extension-reservations/commands/")) {
				recoveryCount++;
				return json(recovery(p.split("/").at(-1)), recoveryCode);
			}
			if (p.endsWith("/withdrawals")) {
				withdrawCount++;
				assert.equal(req.headers()["x-csrf-token"], "SYNTHETIC-CSRF");
				v = { ...v, state: "WITHDRAWN", proposalRevision: 1, effective: false };
				if (withdrawMode === "LOST") {
					withdrawMode = "OK";
					return route.abort("failed");
				}
				return json(v);
			}
			if (
				p === `/api/bookings/${bookingId}/extension-reservations/${reserveId}`
			)
				return json(v, statusCode);
			return json({}, 403);
		});
		const panel = page.getByRole("region", { name: "Extension reservation" }),
			reserve = panel.getByRole("button", {
				name: "Reserve Extension Time",
				exact: true,
			}),
			reload = panel.getByRole("button", {
				name: "Reload Extension Reservation",
			});
		const setup = async () => {
			await page.goto(`${base}/bookings/${bookingId}`);
			await panel.waitFor();
			await page
				.getByRole("button", { name: "Check Extension Options", exact: true })
				.click();
			await page
				.getByRole("button", { name: "Preview Additional Price", exact: true })
				.click();
			await page
				.getByRole("heading", { name: "Additional time preview", exact: true })
				.waitFor();
			await panel
				.getByRole("button", {
					name: "Review Extension Reservation",
					exact: true,
				})
				.click();
			await reserve.waitFor();
		};
		const consent = () =>
			panel.getByRole("checkbox", {
				name: /I understand this reserves temporary/,
			});
		const withdrawConsent = () =>
			panel.getByRole("checkbox", { name: /I explicitly request withdrawal/ });
		const reset = async () => {
			await page.evaluate(() => sessionStorage.clear());
			v = reservation();
			createMode = "OK";
			statusCode = 200;
			recoveryCode = 200;
			accountCode = 200;
			quoteRevision = 1;
			calendarSlow = false;
			await setup();
		};
		await setup();
		assert.equal(createCount, 0);
		assert.equal(await consent().isChecked(), false);
		assert.equal(await reserve.isDisabled(), true);
		checks++;
		await consent().focus();
		await page.keyboard.press("Space");
		await reserve.focus();
		assert.notEqual(
			await reserve.evaluate((e) => getComputedStyle(e).outlineStyle),
			"none",
		);
		await page.keyboard.press("Enter");
		await panel.getByRole("heading", { name: "Current reservation" }).waitFor();
		assert.equal(createCount, 1);
		assert.ok((await panel.innerText()).includes("LKR 123.45"));
		assert.ok((await panel.innerText()).includes("ACTIVE"));
		assert.equal(await withdrawConsent().isChecked(), false);
		checks++;
		assert.equal(await page.locator("main").count(), 1);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		await panel.getByRole("button", { name: "Reload Extension Reservation", exact: true }).focus();
		await panel.screenshot({ path: `${out}/after-${width}.png` });
		await page
			.locator(".extension-preview-panel")
			.screenshot({ path: `${out}/after-panel-${width}.png` });
		await page.screenshot({ path: `${out}/after-customer-${width}.png` });
		const bounds = await panel.boundingBox();
		evidence.push({
			width,
			panelWidth: bounds.width,
			panelHeight: bounds.height,
			oneMain: true,
			horizontalOverflow: false,
			keyboardExplicit: true,
		});
		checks++;
		const stored = await page.evaluate(() => Object.values(sessionStorage));
		assert.equal(
			stored.some(
				(x) =>
					x.includes("additionalMinor") ||
					x.includes("csrf") ||
					x.includes("configuration"),
			),
			false,
		);
		await page.reload();
		await panel.getByRole("heading", { name: "Current reservation" }).waitFor();
		assert.equal(createCount, 1);
		checks++;
		await withdrawConsent().check();
		withdrawMode = "LOST";
		await panel
			.getByRole("button", {
				name: "Withdraw Extension Reservation",
				exact: true,
			})
			.click();
		await panel.getByText("WITHDRAWN", { exact: true }).waitFor();
		assert.equal(withdrawCount, 1);
		assert.equal(createCount, 1);
		checks++;
		await reset();
		createMode = "LOST";
		await consent().check();
		await reserve.click();
		await panel.getByRole("heading", { name: "Current reservation" }).waitFor();
		assert.ok(recoveryCount > 0);
		assert.equal(createCount, 2);
		checks++;
		await reset();
		createMode = "LOST";
		recoveryCode = 404;
		await consent().check();
		await reserve.click();
		await panel
			.getByText(/A missing recovery receipt leaves creation unresolved/)
			.waitFor();
		assert.ok(
			(await panel.innerText()).includes("unknown") ||
				(await panel.innerText()).includes("unresolved"),
		);
		assert.equal(await reserve.count(), 0);
		const before = createCount;
		await page.reload();
		await panel
			.getByText(/A missing recovery receipt leaves creation unresolved/)
			.waitFor();
		assert.equal(createCount, before);
		recoveryCode = 200;
		await reload.click();
		await panel.getByRole("heading", { name: "Current reservation" }).waitFor();
		checks++;
		await reset();
		statusCode = 409;
		await consent().check();
		await reserve.click();
		await panel.getByText(/Outcome unknown/).waitFor();
		assert.equal(await reserve.count(), 0);
		statusCode = 200;
		await reload.click();
		await panel.getByRole("heading", { name: "Current reservation" }).waitFor();
		assert.equal(createCount, before + 1);
		checks++;
		v = {
			...reservation(),
			effective: false,
			pendingReconciliation: true,
			reasons: ["PENDING_RECONCILIATION"],
			observedAt: "2026-10-24T04:11:00Z",
		};
		await reload.click();
		await panel
			.getByText("ACTIVE · PENDING_RECONCILIATION", { exact: true })
			.waitFor();
		assert.ok((await panel.innerText()).includes("not availability"));
		checks++;
		for (const state of ["EXPIRED", "INVALIDATED", "WITHDRAWN"]) {
			v = { ...reservation(), state, effective: false };
			await reload.click();
			await panel.getByText(state, { exact: true }).waitFor();
		}
		checks++;
		for (const code of [403, 404, 409, 429, 503]) {
			statusCode = code;
			await reload.click();
			await panel
				.getByRole("heading", { name: "Current reservation" })
				.waitFor({ state: "hidden" });
			assert.equal(
				(await panel.innerText()).includes("PRIVATE INTERNAL"),
				false,
			);
		}
		statusCode = 200;
		checks++;
		accountCode = 401;
		await reload.click();
		await panel.getByText(/Your session expired or changed/).waitFor();
		assert.equal(
			await panel.getByRole("heading", { name: "Current reservation" }).count(),
			0,
		);
		accountCode = 200;
		checks++;
		await reset();
		createMode = "CONFLICT";
		await consent().check();
		await reserve.click();
		await panel
			.getByText(/The booking, price, room inventory or revision changed/)
			.waitFor();
		assert.equal((await panel.innerText()).includes("PRIVATE INTERNAL"), false);
		checks++;
		calendarSlow = true;
		releaseCalendar = undefined;
		await panel
			.getByRole("button", {
				name: "Review Extension Reservation",
				exact: true,
			})
			.click();
		for (let n = 0; n < 100 && !releaseCalendar; n++)
			await new Promise((r) => setTimeout(r, 10));
		assert.ok(releaseCalendar);
		await page
			.getByLabel("Additional duration (minutes)", { exact: true })
			.fill("31");
		releaseCalendar();
		calendarSlow = false;
		await page.waitForTimeout(100);
		assert.equal(await reserve.count(), 0);
		checks++;
		assert.equal(
			mutations.some(
				(x) =>
					!x.path.endsWith("/extension-quotes") &&
					!x.path.endsWith("/extension-reservations") &&
					!x.path.endsWith("/withdrawals"),
			),
			false,
		);
		assert.equal(
			mutations.some((x) =>
				/funding|payments|allocations|credentials|cancellations/.test(x.path),
			),
			false,
		);
		checks++;
		await page.close();
	}
	await writeFile(
		`${out}/evidence.json`,
		JSON.stringify(
			{
				kind: "INTERCEPTED_BROWSER_FIXTURES_NOT_REAL_BACKEND_E2E",
				checks,
				evidence,
			},
			null,
			2,
		),
	);
	console.log(
		`PASS ${checks} reservation browser checks at375/768/1280; intercepted fixtures only`,
	);
} catch (e) {
	for (const page of browser?.contexts().flatMap((c) => c.pages()) ?? []) {
		await page.screenshot({
			path: "/tmp/cinenest-extension-reservation-fe/browser-failure.png",
		});
		console.error(await page.locator("body").innerText());
	}
	throw e;
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

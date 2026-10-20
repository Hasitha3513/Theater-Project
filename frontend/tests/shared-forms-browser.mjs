// Intercepted synthetic browser rehearsal, never physical/customer acceptance.
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
const id = "00000000-0000-4000-8000-000000000001";
const handoffQuote = {
	quoteId: id,
	revision: 1,
	contentHash: "a".repeat(64),
	state: "ACCEPTED",
	currency: "LKR",
	totalMinor: "7",
	paymentMethod: "BANK",
	synthetic: true,
	commercialIssuance: false,
	checkoutEnabled: false,
	bookingConfirmed: false,
	expiresAt: "2026-10-20T00:10:00Z",
	holdExpiresAt: "2026-10-20T00:10:00Z",
	serverNow: "2026-10-20T00:01:00Z",
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
			label: "SYNTHETIC base",
			quantity: 1,
			unitMinor: "7",
			amountMinor: "7",
		},
	],
	verificationHandoff: { eligible: true, reason: "ELIGIBLE" },
};
let browser,
	groups = 0;
try {
	for (let n = 0; n < 200; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
		if (n === 199) throw Error("No UI server");
	}
	browser = await chromium.launch({ headless: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		const writes = [];
		await page.route("**/api/**", (r) => {
			if (r.request().method() !== "GET") writes.push(r.request().url());
			if (new URL(r.request().url()).pathname === `/api/quotes/${id}`)
				return r.fulfill({ json: handoffQuote });
			return r.fulfill({
				status: 401,
				json: { message: "PRIVATE ERROR MUST NOT APPEAR" },
			});
		});
		await page.goto(`${base}/verification?quote=${id}`);
		const checkbox = page.locator("#verification-consent");
		await checkbox.waitFor();
		await page.getByText(/SYNTHETIC package · Revision 1/).waitFor();
		assert.equal(await checkbox.isChecked(), false);
		await checkbox.focus();
		await page.keyboard.press("Space");
		assert.equal(await checkbox.isChecked(), true);
		await page.keyboard.press("Space");
		assert.equal(await checkbox.isChecked(), false);
		assert(
			await checkbox.evaluate(
				(e) =>
					e === document.activeElement &&
					getComputedStyle(e).outlineStyle !== "none",
			),
		);
		assert.equal(await page.getByRole("main").count(), 1);
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		assert.equal(writes.length, 0);
		assert(
			!(await page.locator("body").innerText()).includes(
				"PRIVATE ERROR MUST NOT APPEAR",
			),
		);
		groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} native checkbox keyboard/focus/unchecked-consent/no-write groups; intercepted presentation only`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

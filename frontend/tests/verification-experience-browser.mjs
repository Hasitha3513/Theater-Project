// SYNTHETIC intercepted browser checks. Real PostgreSQL/API behavior is tested separately.
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
	{ stdio: "pipe" },
);
let output = "";
child.stdout.on("data", (b) => {
	output += b;
});
child.stderr.on("data", (b) => {
	output += b;
});
const id = "00000000-0000-4000-8000-000000000001";
const png = Buffer.from(
	"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6XgAAAABJRU5ErkJggg==",
	"base64",
);
let value = {
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	generation: 1,
	superseded: false,
	previousId: null,
	state: "NOT_SUBMITTED",
	validUntil: null,
	synthetic: true,
	physicalAdmission: false,
	labels: [
		"TEST / SYNTHETIC IDENTITY VERIFICATION",
		"NO REAL IDENTITY DOCUMENTS",
		"NOT VALID FOR PHYSICAL ADMISSION",
	],
	evidence: [],
	history: [],
};
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
let mode = "normal",
	groups = 0,
	browser;
const keys = [];
const traffic = [];
try {
	for (let i = 0; i < 150; i++) {
		if (child.exitCode !== null) throw new Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage();
	await page.route("**/api/**", async (route) => {
		const request = route.request(),
			path = new URL(request.url()).pathname;
		if (path === "/api/bank/requests/readiness")
			return route.fulfill({
				json: {
					quoteId: id,
					quoteRevision: 1,
					contentHash: "a".repeat(64),
					quoteExpiresAt: "2026-10-20T10:00:00Z",
					quoteSummary: {
						currency: "LKR",
						totalMinor: "7",
						paymentMethod: "BANK",
						sessionStart: "2026-10-21T04:30:00Z",
						sessionEnd: "2026-10-21T07:30:00Z",
						timezone: "Asia/Colombo",
						packageName: "SYNTHETIC",
					},
					verificationReadiness: "REQUIRED",
					eligible: false,
					reason: "VERIFICATION_REQUIRED",
					serverNow: "2026-10-20T04:30:00Z",
					synthetic: true,
					paymentVerified: false,
					bookingConfirmed: false,
					labels: [
						"SYNTHETIC BANK-PAYMENT TEST",
						"DO NOT TRANSFER REAL MONEY",
						"PAYMENT NOT VERIFIED",
						"BOOKING NOT CONFIRMED",
					],
				},
			});
		traffic.push({ path, method: request.method() });
		let result = value;
		if (path === "/api/session/csrf")
			return route.fulfill({ json: { token: "SYNTHETIC-CSRF" } });
		if (mode === "expired")
			return route.fulfill({
				status: 401,
				json: { message: "Current session required" },
			});
		if (mode === "unavailable")
			return route.fulfill({ status: 503, json: { message: "Unavailable" } });
		if (mode === "denied")
			return route.fulfill({
				status: 403,
				json: { message: "Synthetic denied" },
			});
		if (path === `/api/quotes/${id}`)
			return route.fulfill({ json: handoffQuote });
		if (path.includes("/evidence/"))
			return route.fulfill({ contentType: "image/png", body: png });
		if (request.method() === "POST") {
			const body = request.postDataJSON();
			keys.push(body.commandKey);
			if (mode === "conflict")
				return route.fulfill({
					status: 409,
					json: { message: "Synthetic changed revision" },
				});
			if (path.endsWith("/uploads")) {
				const next =
					value.evidence.length === 0
						? id
						: "00000000-0000-4000-8000-000000000002";
				value = {
					...value,
					revision: value.revision + 1,
					state: "SUBMITTED",
					evidence: [
						...value.evidence,
						{
							id: next,
							type: "NIC",
							state: "ALLOCATED",
							expiresAt: "2026-11-01T00:01:00Z",
						},
					],
				};
				result = { ...value, uploadId: next };
			} else if (path.endsWith("/complete")) {
				value = { ...value, revision: value.revision + 1, state: "PROCESSING" };
				result = value;
			} else if (path.endsWith("/resubmissions")) {
				value = {
					...value,
					generation: 2,
					revision: 0,
					state: "NOT_SUBMITTED",
					evidence: [],
				};
				result = value;
			} else if (path.endsWith("/decisions")) {
				value = {
					...value,
					revision: value.revision + 1,
					state: body.verdict,
					history: [
						{
							id,
							revision: value.revision,
							verdict: body.verdict,
							reason: body.reason,
							at: "2026-10-17",
							validUntil: "2026-10-18",
						},
					],
				};
				result = value;
			}
		}
		if (path.endsWith("/verification/requests") && request.method() === "GET")
			result = [value];
		return route.fulfill({ json: result });
	});
	function pass(label) {
		groups++;
		console.log("PASS " + label);
	}
	await page.goto(`${base}/verification?quote=${id}`);
	await page.getByText(/SYNTHETIC package · Revision 1/).waitFor();
	assert.equal(
		await page
			.getByLabel(
				"I consent to this versioned synthetic test processing; no real documents",
				{ exact: true },
			)
			.isChecked(),
		false,
	);
	assert.equal(keys.length, 0);
	assert.ok(!page.url().includes("aaaa"));
	pass(
		"protected quotation, unchecked consent, opaque URL, no automatic submission",
	);
	const nav = page.getByRole("navigation", {
		name: "Identity verification sections",
	});
	await nav.getByRole("link", { name: "Consent", exact: true }).focus();
	await page.keyboard.press("Enter");
	assert.match(page.url(), /#verification-permission$/);
	assert.equal(keys.length, 0);
	pass("keyboard section navigation without commands");
	await page
		.getByLabel("Operator-provided synthetic policy version", { exact: true })
		.fill("SYNTHETIC-V1");
	const consent = page.getByLabel(
		"I consent to this versioned synthetic test processing; no real documents",
		{ exact: true },
	);
	await consent.check();
	await page
		.getByRole("button", { name: "Reload quotation context", exact: true })
		.click();
	await page.getByText(/SYNTHETIC package · Revision 1/).waitFor();
	assert.equal(await consent.isChecked(), false);
	pass("context refresh invalidates consent");
	await page.getByLabel("Existing request ID", { exact: true }).fill(id);
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page
		.getByText("No human decision recorded.", { exact: true })
		.waitFor();
	const input = page.getByLabel(
		"Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)",
		{ exact: true },
	);
	assert.equal(await input.getAttribute("accept"), "image/jpeg,image/png");
	assert.match(
		await page.locator("#verification-file-help").innerText(),
		/5 MiB and 20 megapixels/,
	);
	await input.setInputFiles({
		name: "SYNTHETIC.png",
		mimeType: "image/png",
		buffer: png,
	});
	assert.equal(keys.length, 0);
	pass("upload restrictions and selection alone never uploads");
	for (const state of ["REVIEW_REQUIRED", "REJECTED", "EXPIRED", "REVOKED"]) {
		value = { ...value, state };
		await page
			.getByRole("button", {
				name: "Refresh current request and history",
				exact: true,
			})
			.click();
		await page.getByText("Server status: " + state, { exact: true }).waitFor();
		assert.equal(keys.length, 0);
	}
	pass(
		"pending rejected expired revoked statuses from server with no implicit action",
	);
 handoffQuote.state = "EXPIRED";
 handoffQuote.verificationHandoff = {eligible:false,reason:"QUOTE_EXPIRED"};
 await page.getByRole("button",{name:"Reload quotation context",exact:true}).click();
 await page.getByText("The quotation expired. Return to room selection for a new hold and quotation review.",{exact:true}).waitFor();
 assert.equal(await page.getByRole("button",{name:"Start synthetic request",exact:true}).isDisabled(),true);
 pass("expired quote disables request start without renewing the hold");
 handoffQuote.state = "ACCEPTED";
 handoffQuote.verificationHandoff = {eligible:false,reason:"QUOTE_REVISION_CHANGED"};
 await page.getByRole("button",{name:"Reload quotation context",exact:true}).click();
 await page.getByText("The quotation changed. Return to quotation review and reload its current revision.",{exact:true}).waitFor();
 assert.equal(await consent.isChecked(),false);assert.equal(keys.length,0);
 pass("stale quotation requires explicit review and fresh consent");
 handoffQuote.verificationHandoff = {eligible:true,reason:"ELIGIBLE"};

	mode = "denied";
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page
		.getByRole("heading", { name: "Access denied", exact: true })
		.waitFor();
	assert.equal(await page.getByText(/Generation 1/).count(), 0);
	pass("wrong-owner denial clears protected request");
	mode = "expired";
	await page
		.getByRole("button", { name: "Reload quotation context", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Sign-in required", exact: true })
		.waitFor();
	pass("session expiration with explicit recovery");
	mode = "unavailable";
	await page
		.getByRole("button", { name: "Reload quotation context", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Service unavailable", exact: true })
		.waitFor();
	pass("backend unavailable announced without fake result");
	mode = "normal";
	value = { ...value, state: "REVIEW_REQUIRED" };
	await page
		.getByRole("button", { name: "Reload quotation context", exact: true })
		.click();
	await page.getByText(/SYNTHETIC package · Revision 1/).waitFor();
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page
		.getByText("Server status: REVIEW_REQUIRED", { exact: true })
		.waitFor();
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			),
			false,
		);
		assert.equal(await page.locator("h1").count(), 1);
		assert.equal(await consent.isChecked(), false);
		await nav.getByRole("link", { name: "Consent", exact: true }).focus();
		assert.equal(
			await page.evaluate(
				() =>
					document.activeElement.id ||
					document.activeElement.getAttribute("href"),
			),
			"#verification-permission",
		);
		await page.keyboard.press("Tab");
        await page.keyboard.press("Shift+Tab");
        const focus = await nav
			.getByRole("link", { name: "Consent", exact: true })
			.evaluate((el) => getComputedStyle(el).outlineStyle);
		assert.notEqual(focus, "none");
		pass("responsive no overflow and visible keyboard focus " + width);
	}
	assert.equal(keys.length, 0);
	assert.equal(traffic.filter((x) => x.method !== "GET").length, 0);
	pass(
		"no verification upload bank or booking mutation from page navigation/status checks",
	);
	console.log(
		`PASS ${groups} focused synthetic verification UX browser groups; intercepted APIs, not real-backend E2E`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

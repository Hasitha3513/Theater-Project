// SYNTHETIC intercepted browser rehearsal; PostgreSQL/API claims are checked separately.
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
child.stdout.resume();
child.stderr.resume();
const id = "00000000-0000-4000-8000-000000000001";
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC inventory room",
	description: "HDMI input",
	capacity: 5,
	facilities: [],
	images: [],
	venue: {
		displayName: "Synthetic venue",
		timezone: "Asia/Colombo",
		windows: [],
	},
};
const candidate = {
	roomId: id,
	timezone: "Asia/Colombo",
	sessionStart: "2026-10-20T04:30:00Z",
	sessionEnd: "2026-10-20T07:30:00Z",
	bufferMinutes: 7,
	blockedUntil: "2026-10-20T07:37:00Z",
	configuration: "SYNTHETIC-1",
	serverNow: "2026-10-18T00:00:00Z",
	available: true,
	evidenceLevel: "INVENTORY_ONLY",
};
let hold = {
	...candidate,
	holdId: id,
	createdAt: candidate.serverNow,
	expiresAt: "2026-10-18T00:10:00Z",
	state: "ACTIVE",
	revision: 0,
};
let mode = "normal",
	checks = 0,
	browser;
const commands = [];
const traffic = [];
let slowRelease;
let quoteMode = "normal";
let quote = {
	quoteId: id,
	revision: 1,
	contentHash: "a".repeat(64),
	state: "ISSUED",
	verificationHandoff: { eligible: false, reason: "QUOTE_NOT_ACCEPTED" },
	currency: "LKR",
	totalMinor: "250007",
	paymentMethod: "BANK",
	synthetic: true,
	commercialIssuance: false,
	checkoutEnabled: false,
	bookingConfirmed: false,
	expiresAt: hold.expiresAt,
	holdExpiresAt: hold.expiresAt,
	serverNow: hold.serverNow,
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
			unitMinor: "250000",
			amountMinor: "250000",
		},
		{
			lineId: "SYNTHETIC_TAX",
			kind: "SYNTHETIC_TAX",
			label: "SYNTHETIC fixed tax fixture",
			quantity: 1,
			unitMinor: "7",
			amountMinor: "7",
		},
	],
};
function pass(label) {
	checks++;
	console.log(`PASS ${label}`);
}
try {
	for (let n = 0; n < 100; n++) {
		try {
			await fetch(base);
			break;
		} catch {
			await new Promise((r) => setTimeout(r, 100));
		}
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage();
	const errors = [];
	page.on("pageerror", (e) => errors.push(e.message));
	await page.route("**/api/**", async (route) => {
		const req = route.request(),
			p = new URL(req.url()).pathname;
		traffic.push({ path: p, method: req.method() });
		let status = 200,
			body = {};
		if (p === `/api/catalog/rooms/${id}`) body = room;
		else if (p === `/api/venues/${id}/packages`)
			body = {
				items: [
					{
						packageId: id,
						versionId: id,
						name: "SYNTHETIC package",
						description: "SYNTHETIC",
						basePriceMinor: "250000",
						includedMinutes: 180,
						includedGuests: 4,
						options: [],
					},
				],
				purchasable: false,
			};
		else if (p === "/api/session/csrf")
			body = { token: "SYNTHETIC", headerName: "X-CSRF-TOKEN" };
		else if (p.endsWith("/availability")) {
			if (mode === "denied") status = 403;
			body = { ...candidate, available: mode !== "conflict" };
		} else if (p === "/api/holds") {
			commands.push(req.postDataJSON());
			if (mode === "unknown") {
				mode = "normal";
				await route.abort();
				return;
			}
			body = hold;
		} else if (p.endsWith("/release")) {
			hold = { ...hold, state: "RELEASED", revision: 1 };
			body = hold;
		} else if (p === `/api/holds/${id}`) body = hold;
		else if (p === "/api/quotes") {
			commands.push(req.postDataJSON());
			if (quoteMode === "unknown") {
				quoteMode = "normal";
				await route.abort();
				return;
			}
			body = quote;
		} else if (p.endsWith("/revisions")) {
			quote = {
				...quote,
				revision: quote.revision + 1,
				paymentMethod: req.postDataJSON().paymentMethod,
				contentHash: "b".repeat(64),
			};
			body = quote;
		} else if (p.endsWith("/accept")) {
			if (quoteMode === "conflict") status = 409;
			else
				quote = {
					...quote,
					state: "ACCEPTED",
					verificationHandoff: {
						eligible: quote.paymentMethod === "BANK",
						reason:
							quote.paymentMethod === "BANK"
								? "ELIGIBLE"
								: "BANK_METHOD_REQUIRED",
					},
				};
			body = quote;
		} else if (p === `/api/quotes/${id}`) {
			if (quoteMode === "slow")
				await new Promise((resolve) => {
					slowRelease = resolve;
				});
			if (quoteMode === "network") return route.abort();
			if (quoteMode === "denied") status = 403;
			if (quoteMode === "missing") status = 404;
			if (quoteMode === "session") status = 401;
			if (quoteMode === "unavailable") status = 503;
			body =
				quoteMode === "expired"
					? {
							...quote,
							verificationHandoff: { eligible: false, reason: "QUOTE_EXPIRED" },
						}
					: quoteMode === "withdrawn"
						? {
								...quote,
								verificationHandoff: {
									eligible: false,
									reason: "PACKAGE_INCOMPATIBLE",
								},
							}
						: quoteMode === "stale"
							? { ...quote, quoteId: "00000000-0000-4000-8000-000000000002" }
							: quote;
			if (status !== 200) body = { message: "Synthetic unavailable" };
		} else if (p.endsWith("/inventory")) {
			if (mode === "denied") status = 403;
			body = { items: [], page: 0, size: 24 };
		} else if (p === "/api/accounts/me")
			body = {
				accountId: id,
				synthetic: true,
				eligibility: "ELIGIBLE",
				scopes: [],
			};
		else status = 404;
		await route.fulfill({
			status,
			contentType: "application/json",
			body: JSON.stringify(body),
		});
	});

	await page.goto(`${base}/rooms/${id}`);
	await page
		.getByLabel("Customer start in venue local time")
		.fill("2026-10-20T10:00");
	await page.getByLabel("Customer duration (minutes)").fill("180");
	await page.getByLabel("Guests", { exact: true }).fill("4");
	await page
		.getByRole("button", { name: "Check inventory (no hold)", exact: true })
		.click();
	await page
		.getByRole("button", {
			name: "Create temporary inventory hold",
			exact: true,
		})
		.click();
	await page
		.getByRole("heading", {
			name: "Synthetic quotation laboratory",
			exact: true,
		})
		.waitFor();
	await page
		.getByRole("button", { name: "Load applicable synthetic package choices" })
		.click();
	await page.getByLabel("Published package", { exact: true }).selectOption(id);
	quoteMode = "unknown";
	await page
		.getByRole("button", { name: "Calculate synthetic quotation" })
		.click();
	await page
		.getByRole("button", { name: "Retry original quotation command" })
		.click();
	await page
		.getByRole("region", { name: "Synthetic quotation summary" })
		.waitFor();
	assert.deepEqual(commands.at(-1), commands.at(-2));
	pass("unknown quote outcome retains identical command");
	for (const label of quote.disclosures)
		assert.ok((await page.getByText(label, { exact: true }).count()) > 0);
	assert.equal(
		await page
			.getByRole("button", { name: "Accept synthetic revision only" })
			.isEnabled(),
		false,
	);
	pass("disclosures and explicit acceptance guard");
	await page
		.getByLabel("Intended method (no payment)", { exact: true })
		.selectOption("GATEWAY");
	assert.equal(
		await page
			.getByLabel(
				"I reviewed this exact synthetic revision and all three disclosures",
			)
			.isEnabled(),
		false,
	);
	await page
		.getByRole("button", {
			name: "Review new revision for changed selections/method",
		})
		.click();
	await page.getByText(/Revision 2/).waitFor();
	pass("method change creates reviewed server revision");
	quoteMode = "conflict";
	await page
		.getByLabel(
			"I reviewed this exact synthetic revision and all three disclosures",
		)
		.check();
	await page
		.getByRole("button", { name: "Accept synthetic revision only" })
		.click();
	await page
		.getByText(/Refresh and review; no payment or booking confirmation/)
		.waitFor();
	pass("conflict never claims accepted or confirmed");
	quoteMode = "normal";
	await page
		.getByLabel("Intended method (no payment)", { exact: true })
		.selectOption("BANK");
	await page
		.getByRole("button", {
			name: "Review new revision for changed selections/method",
		})
		.click();
	await page.getByText(/Revision 3/).waitFor();
	await page
		.getByLabel(
			"I reviewed this exact synthetic revision and all three disclosures",
		)
		.check();
	await page
		.getByRole("button", { name: "Accept synthetic revision only" })
		.click();
	await page.getByText(/Revision 3 · ACCEPTED/).waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Accept synthetic revision only" })
			.count(),
		0,
	);
	pass("synthetic acceptance no commercial checkout");
	await mkdir("../.cache/quote-browser", { recursive: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
		pass(`synthetic quote responsive ${width}`);
		if (width === 375 || width === 1280)
			await page.screenshot({
				path: `../.cache/quote-browser/accepted-${width}.png`,
				fullPage: true,
			});
	}
	const continuation = page.getByRole("link", {
		name: "Continue to Verification",
		exact: true,
	});
	await continuation.waitFor();
	assert.equal(
		await continuation.evaluate((element) => getComputedStyle(element).color),
		await page
			.getByRole("button", {
				name: "Reload verification eligibility",
				exact: true,
			})
			.evaluate((element) => getComputedStyle(element).color),
	);
	assert.equal(
		await continuation.getAttribute("href"),
		`/verification?quote=${id}`,
	);
	await continuation.focus();
	await page.keyboard.press("Enter");
	await page.waitForURL(`${base}/verification?quote=${id}`);
	await page
		.getByRole("region", { name: "Synthetic quotation summary" })
		.waitFor();
	assert.equal(
		await page
			.getByLabel("Owned synthetic quotation ID", { exact: true })
			.inputValue(),
		id,
	);
	assert.equal(
		await page
			.getByLabel("Owned synthetic quotation ID", { exact: true })
			.getAttribute("readonly"),
		"",
	);
	assert.equal(
		await page
			.getByLabel(
				"I consent to this versioned synthetic test processing; no real documents",
			)
			.isChecked(),
		false,
	);
	assert.equal(
		await page
			.getByRole("button", { name: "Start synthetic request", exact: true })
			.isEnabled(),
		false,
	);
	assert.deepEqual([...new URL(page.url()).searchParams], [["quote", id]]);
	assert.ok(
		traffic.some(
			(item) => item.path === `/api/quotes/${id}` && item.method === "GET",
		),
	);
	pass(
		"keyboard opaque navigation, authenticated reload, correct summary and unchecked consent",
	);
	for (const width of [375, 1280]) {
		await page.setViewportSize({ width, height: 900 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
		await page.screenshot({
			path: `../.cache/quote-browser/handoff-${width}.png`,
			fullPage: true,
		});
		pass(`verification handoff responsive ${width}`);
	}

	await page
		.getByLabel("Operator-provided synthetic policy version")
		.fill("SYNTHETIC-V1");
	await page
		.getByLabel(
			"I consent to this versioned synthetic test processing; no real documents",
		)
		.check();
	quoteMode = "expired";
	await page
		.getByRole("button", { name: "Reload quotation context", exact: true })
		.click();
	await page.getByText(/The quotation expired/).waitFor();
	assert.equal(
		await page
			.getByLabel(
				"I consent to this versioned synthetic test processing; no real documents",
			)
			.isChecked(),
		false,
	);
	assert.equal(
		await page
			.getByRole("button", { name: "Start synthetic request", exact: true })
			.isEnabled(),
		false,
	);
	pass("server refusal after earlier eligibility resets explicit consent");
	for (const testMode of [
		"withdrawn",
		"missing",
		"denied",
		"session",
		"unavailable",
		"network",
		"stale",
	]) {
		quoteMode = testMode;
		await page.goto(`${base}/verification?quote=${id}`);
		if (testMode === "withdrawn")
			await page
				.getByText(/room or package configuration is no longer applicable/)
				.waitFor();
		else
			await page.getByText(/Quotation unavailable or inaccessible/).waitFor();
		if (testMode !== "withdrawn")
			assert.equal(
				await page
					.getByRole("region", { name: "Synthetic quotation summary" })
					.count(),
				0,
			);
		assert.equal(
			await page
				.getByRole("button", { name: "Start synthetic request", exact: true })
				.isEnabled(),
			false,
		);
		pass(`protected handoff refusal ${testMode}`);
	}
	quoteMode = "normal";
	// Ordinary publication withdrawal can remain eligible: the UI follows the server,
	// and does not infer a new policy from catalog visibility or historical dates.
	await page.goto(`${base}/verification?quote=${id}`);
	await page
		.getByRole("region", { name: "Synthetic quotation summary" })
		.waitFor();
	pass(
		"server-honored frozen quotation remains usable without frontend date/catalog policy",
	);
	for (const suffix of ["", "?quote=malformed", `?quote=${id}&quote=${id}`]) {
		await page.goto(`${base}/verification${suffix}`);
		await page
			.getByText(/Quotation reference is missing or malformed/)
			.waitFor();
		assert.equal(
			await page
				.getByRole("region", { name: "Synthetic quotation summary" })
				.count(),
			0,
		);
		pass(`missing or malformed navigation ${suffix || "no reference"}`);
	}
	quoteMode = "slow";
	await page.goto(`${base}/verification?quote=${id}`);
	await page.getByText("Reloading protected quotation context.").waitFor();
	assert.equal(
		await page
			.getByRole("region", { name: "Synthetic quotation summary" })
			.count(),
		0,
	);
	while (!slowRelease) await new Promise((resolve) => setTimeout(resolve, 10));
	slowRelease();
	quoteMode = "normal";
	await page
		.getByRole("region", { name: "Synthetic quotation summary" })
		.waitFor();
	await page
		.getByLabel(
			"I consent to this versioned synthetic test processing; no real documents",
		)
		.check();
	quoteMode = "session";
	await page.evaluate(() => window.dispatchEvent(new Event("focus")));
	await page.getByText(/Quotation unavailable or inaccessible/).waitFor();
	assert.equal(
		await page
			.getByRole("region", { name: "Synthetic quotation summary" })
			.count(),
		0,
	);
	assert.equal(
		await page
			.getByLabel(
				"I consent to this versioned synthetic test processing; no real documents",
			)
			.isChecked(),
		false,
	);
	pass(
		"loading and stale-browser focus revalidation do not retain protected context",
	);
	assert.equal(
		traffic.filter(
			(item) =>
				item.method !== "GET" &&
				/verification|bank|bookings|checkout/.test(item.path),
		).length,
		0,
	);
	pass("no automatic verification, evidence, bank request or booking commands");
	assert.deepEqual(errors, []);
	pass("no runtime errors across handoff states");
	console.log(
		`PASS ${checks} SYNTHETIC quotation/handoff browser groups; intercepted API fixtures only, no backend or customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

// SYNTHETIC intercepted browser rehearsal; PostgreSQL/API claims are checked separately.
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
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage({
		viewport: { width: 1280, height: 1000 },
	});
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
			if (mode === "loading")
				await new Promise((resolve) => setTimeout(resolve, 1000));
			if (mode === "denied") status = 403;
			if (mode === "unavailable") status = 503;
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
			if (mode === "unavailable") status = 503;
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
		.getByRole("navigation", { name: "Booking section guide" })
		.waitFor();
	for (const section of [
		"room-overview",
		"booking-date",
		"booking-hold",
		"booking-packages",
		"booking-review",
		"booking-accept",
	])
		assert.equal(await page.locator("#" + section).count(), 1);
	assert.equal(commands.length, 0);
	pass(
		"all six initial sections reachable,zero automatic commands or fake completion",
	);
	const date = page.getByRole("link", { name: "Date & time", exact: true });
	await date.focus();
	await page.keyboard.press("Tab");
	await page.keyboard.press("Shift+Tab");
	assert.equal(
		await date.evaluate((element) => getComputedStyle(element).outlineStyle),
		"solid",
	);
	await page.keyboard.press("Enter");
	assert.equal(new URL(page.url()).hash, "#booking-date");
	assert.equal(commands.length, 0);
	pass("keyboard section navigation performs no submission");
	await page
		.getByLabel("Customer start in venue local time")
		.fill("2026-10-20T10:00");
	await page.getByLabel("Customer duration (minutes)").fill("180");
	await page.getByLabel("Guests", { exact: true }).fill("4");
	mode = "loading";
	await page
		.getByRole("button", { name: "Check inventory (no hold)", exact: true })
		.click();
	await page.getByRole("heading", { name: "Loading", exact: true }).waitFor();
	assert.equal(commands.length, 0);
	await page
		.getByRole("button", {
			name: "Create temporary inventory hold",
			exact: true,
		})
		.waitFor();
	pass(
		"loading feedback waits for inventory response without automatically holding",
	);
	mode = "conflict";
	await page
		.getByRole("button", { name: "Check inventory (no hold)", exact: true })
		.click();
	await page
		.getByText("Inventory conflict; choose another interval.", { exact: true })
		.waitFor();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Create temporary inventory hold",
				exact: true,
			})
			.count(),
		0,
	);
	assert.equal(commands.length, 0);
	pass("unavailable interval cannot display a hold action");
	mode = "unavailable";
	await page
		.getByRole("button", { name: "Check inventory (no hold)", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Service unavailable", exact: true })
		.waitFor();
	assert.equal(commands.length, 0);
	pass("backend failure never becomes available inventory");
	mode = "normal";
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
		.getByRole("button", { name: "Load applicable synthetic package choices" })
		.waitFor();
	assert.equal(commands.length, 1);
	assert.equal(traffic.filter((t) => t.path === "/api/quotes").length, 0);
	pass(
		"hold requires explicit click,does not automatically calculate or accept a quote",
	);
	hold = { ...hold, state: "EXPIRED", revision: 1 };
	await page
		.getByRole("button", { name: "Refresh hold state", exact: true })
		.click();
	await page.getByText("Hold expired", { exact: true }).waitFor();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Load applicable synthetic package choices",
			})
			.count(),
		0,
	);
	assert.equal(await page.locator("#booking-accept input").count(), 0);
	pass(
		"server-expired hold removes quotation entry and retains navigable explanation",
	);
	hold = { ...hold, state: "ACTIVE", revision: 2 };
	await page
		.getByRole("button", { name: "Refresh hold state", exact: true })
		.click();
	await page
		.getByRole("button", { name: "Load applicable synthetic package choices" })
		.click();
	await page.getByLabel("Published package", { exact: true }).selectOption(id);
	assert.equal(commands.length, 1);
	await page
		.getByRole("button", { name: "Calculate synthetic quotation" })
		.click();
	await page
		.getByRole("region", { name: "Synthetic quotation summary" })
		.waitFor();
	assert.equal(commands.length, 2);
	assert.equal(
		await page
			.getByRole("button", { name: "Accept synthetic revision only" })
			.isEnabled(),
		false,
	);
	pass(
		"calculation explicit,exact revision/disclosures require unchecked review",
	);
	await page
		.getByLabel("Intended method (no payment)", { exact: true })
		.selectOption("GATEWAY");
	await page
		.getByText(
			"Your selections differ from this quotation. Request a new revision, then review its terms again.",
			{ exact: true },
		)
		.waitFor();
	assert.equal(
		await page
			.getByLabel(
				"I reviewed this exact synthetic revision and all three disclosures",
			)
			.isEnabled(),
		false,
	);
	assert.equal(commands.length, 2);
	await page
		.getByRole("button", {
			name: "Review new revision for changed selections/method",
		})
		.click();
	await page.getByText(/Revision 2/).waitFor();
	assert.equal(
		await page
			.getByLabel(
				"I reviewed this exact synthetic revision and all three disclosures",
			)
			.isChecked(),
		false,
	);
	pass(
		"changed selections cannot accept old revision,new server revision needs fresh review",
	);
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth > innerWidth,
			),
			false,
		);
		for (const id of [
			"booking-date",
			"booking-hold",
			"booking-packages",
			"booking-review",
			"booking-accept",
		])
			assert.equal(await page.locator("#" + id).count(), 1);
	}
	pass("unique live-stage anchors,no horizontal overflow at all three widths");
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
	assert.equal(
		await page
			.getByRole("link", { name: "Continue to Verification", exact: true })
			.count(),
		0,
	);
	pass("acceptance conflict cannot claim accepted,paid or booked");
	quoteMode = "normal";
	quote = { ...quote, state: "EXPIRED" };
	await page
		.getByRole("button", { name: "Refresh quotation state", exact: true })
		.click();
	await page.getByText(/Quotation cannot be accepted/).waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Accept synthetic revision only" })
			.count(),
		0,
	);
	pass("server-expired quote retains summary but no acceptance action");
	quoteMode = "session";
	await page
		.getByRole("button", { name: "Refresh quotation state", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Sign-in required", exact: true })
		.waitFor();
	assert.equal(
		await page
			.getByRole("region", { name: "Synthetic quotation summary" })
			.count(),
		0,
	);
	pass("expired session clears protected quotation display");
	assert.equal(
		traffic.filter(
			(t) =>
				t.method !== "GET" &&
				/bank|verification|bookings|checkout/.test(t.path),
		).length,
		0,
	);
	assert.equal(new URL(page.url()).search, "");
	pass(
		"no automatic financial,verification or booking operations,no sensitive navigation",
	);
	console.log(
		`PASS ${checks} synthetic booking-step browser groups;intercepted APIs only,not backend E2E or acceptance`,
	);
} finally {
	await browser?.close();
	const ended = once(child, "exit");
	child.kill("SIGTERM");
	await ended;
}

// SYNTHETIC intercepted browser checks. Real PostgreSQL/API behavior is tested separately.
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
		let result = value;
		if (path === "/api/session/csrf")
			return route.fulfill({ json: { token: "SYNTHETIC-CSRF" } });
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
	await page.goto(`${base}/verification`);
	for (const label of value.labels)
		await page.getByText(label, { exact: true }).waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Start synthetic request", exact: true })
			.isDisabled(),
		true,
	);
	groups++;
	await page
		.getByLabel("Owned synthetic quotation ID", { exact: true })
		.fill(id);
	await page
		.getByLabel("Operator-provided synthetic policy version", { exact: true })
		.fill("SYNTHETIC-V1");
	await page
		.getByLabel(
			"I consent to this versioned synthetic test processing; no real documents",
			{ exact: true },
		)
		.check();
	await page
		.getByRole("button", { name: "Start synthetic request", exact: true })
		.click();
	await page
		.getByText("No human decision recorded.", { exact: true })
		.waitFor();
	groups++;
	await page
		.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)", {
			exact: true,
		})
		.setInputFiles({
			name: "SYNTHETIC.png",
			mimeType: "image/png",
			buffer: png,
		});
	await page
		.getByRole("button", {
			name: "Upload and request simulated scan",
			exact: true,
		})
		.click();
	await page.getByText(/PROCESSING · Revision/).waitFor();
	groups++;
	value = {
		...value,
		state: "REVIEW_REQUIRED",
		revision: 3,
		evidence: [{ ...value.evidence[0], state: "READY" }],
	};
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page.getByText(/REVIEW_REQUIRED · Revision/).waitFor();
	groups++;
	await page
		.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)", {
			exact: true,
		})
		.setInputFiles({
			name: "SYNTHETIC-NOT-SUBMITTED.png",
			mimeType: "image/png",
			buffer: png,
		});
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
	mode = "normal";
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page.getByText(/REVIEW_REQUIRED · Revision/).waitFor();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Upload and request simulated scan",
				exact: true,
			})
			.isDisabled(),
		true,
	);
	await page
		.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)", {
			exact: true,
		})
		.setInputFiles({
			name: "SYNTHETIC-BACK.png",
			mimeType: "image/png",
			buffer: png,
		});
	await page
		.getByRole("button", {
			name: "Upload and request simulated scan",
			exact: true,
		})
		.click();
	await page.getByText(/PROCESSING · Revision/).waitFor();
	value = {
		...value,
		state: "REVIEW_REQUIRED",
		revision: value.revision + 1,
		evidence: value.evidence.map((e) => ({ ...e, state: "READY" })),
	};
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	await page.getByText(/REVIEW_REQUIRED · Revision/).waitFor();
	assert.equal(value.evidence.length, 2);
	assert.equal(
		await page
			.getByRole("button", {
				name: "Upload and request simulated scan",
				exact: true,
			})
			.isDisabled(),
		true,
	);
	await page.goto(`${base}/admin/verification`);
	await page.getByLabel("Authorized venue ID", { exact: true }).fill(id);
	await page
		.getByRole("button", { name: "Load venue review queue", exact: true })
		.click();
	await page
		.getByRole("button", { name: `REVIEW_REQUIRED · ${id}`, exact: true })
		.click();
	await page
		.getByRole("button", { name: "Open private NIC preview", exact: true })
		.first()
		.click();
	await page
		.getByAltText(
			"Private explicitly synthetic fixture for current manual review",
			{ exact: true },
		)
		.waitFor();
	await page
		.getByRole("button", { name: "Close private preview", exact: true })
		.click();
	groups++;
	await page
		.getByLabel(
			"I manually reviewed the current synthetic evidence, booker match and 18+ policy; this does not approve admission",
			{ exact: true },
		)
		.check();
	mode = "conflict";
	await page
		.getByRole("button", {
			name: "Record exact-revision manual decision",
			exact: true,
		})
		.click();
	await page.getByText(/Synthetic changed revision/).waitFor();
	mode = "normal";
	await page
		.getByRole("button", {
			name: "Refresh current request and history",
			exact: true,
		})
		.click();
	assert.equal(
		await page
			.getByRole("button", {
				name: "Record exact-revision manual decision",
				exact: true,
			})
			.isDisabled(),
		true,
	);
	groups++;
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
	groups++;
	mode = "normal";
	await mkdir("../.cache/verification-browser", { recursive: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536])
		for (const route of ["/verification", "/admin/verification"]) {
			await page.setViewportSize({ width, height: 950 });
			await page.goto(base + route);
			await page
				.getByText("NO REAL IDENTITY DOCUMENTS", { exact: true })
				.waitFor();
			assert.equal(await page.locator("h1").count(), 1);
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth > innerWidth,
				),
				false,
			);
			if (width === 375 || width === 1280)
				await page.screenshot({
					path: `../.cache/verification-browser/${route.includes("admin") ? "reviewer" : "customer"}-${width}.png`,
					fullPage: true,
				});
			groups++;
		}
	assert.ok(keys.length >= 3);
	console.log(
		`PASS ${groups} SYNTHETIC verification browser groups at seven widths; no real identity/provider/customer acceptance`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

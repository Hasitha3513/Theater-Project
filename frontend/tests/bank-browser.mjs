// Intercepted SYNTHETIC UI checks only. Real PostgreSQL/API evidence is separate.
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
const base = `http://127.0.0.1:${port}`,
	id = "00000000-0000-4000-8000-000000000001";
const fixturePng = Buffer.from(
	"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",
	"base64",
);
const labels = [
	"SYNTHETIC BANK-PAYMENT TEST",
	"DO NOT TRANSFER REAL MONEY",
	"PAYMENT NOT VERIFIED",
	"BOOKING NOT CONFIRMED",
];
const initial = () => ({
	id,
	venueId: id,
	quoteId: id,
	revision: 0,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels,
	requestState: "OPEN",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-20T10:00Z",
	initiateBy: "2026-10-20T08:30Z",
	reviewWindow: "00:00–23:59",
	instructionVersion: "SYNTHETIC-BANK1",
	claims: [],
	work: [],
	expiresAt: "2026-10-20T10:01Z",
	submitBy: "2026-10-20T09:00Z",
	reviewBy: "2026-10-20T09:30Z",
	overdue: false,
	amountMinor: "12352",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
});
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
let output = "",
	browser,
	groups = 0;
child.stdout.on("data", (b) => (output += b));
child.stderr.on("data", (b) => (output += b));
try {
	for (let i = 0; i < 150; i++) {
		if (child.exitCode !== null) throw new Error(output);
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 200));
	}
	browser = await chromium.launch({ headless: true });
	await mkdir("../.cache/bank-browser", { recursive: true });
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let value = initial(),
			mode = "normal";
		await page.route("**/api/**", async (route) => {
			const path = new URL(route.request().url()).pathname;
			if (path === "/api/session/csrf")
				return route.fulfill({
					json: { token: "SYNTHETIC", headerName: "X-CSRF-TOKEN" },
				});
			if (mode === "denied")
				return route.fulfill({
					status: 403,
					json: { message: "Current scoped review denied" },
				});
			if (mode === "conflict")
				return route.fulfill({
					status: 409,
					json: { message: "Request changed; refresh history" },
				});
            if(path.includes("/bank/credits") || path.includes("/bank/exceptions")) return route.fulfill({json:mode==="empty"?[]:{id,synthetic:true,paymentVerified:false,syntheticFundingStatus:"SIMULATED_VERIFIED",amountMinor:50000,availableMinor:50000,currency:"LKR",fixtureKey:"SYNTHETIC-BANK-50000"}});
            if(path.endsWith("/allocations"))return route.fulfill({json:{synthetic:true,paymentVerified:false,bookingConfirmed:false,syntheticFundingStatus:"SIMULATED_ALLOCATED",syntheticBookingStatus:"SIMULATED_CONFIRMED",outstandingMinor:0}});
			if (path.endsWith("/claims") || path.endsWith("/resubmissions"))
				value = {
					...value,
					revision: value.revision + 1,
					claimId: id,
					requestState: "REVIEW_PENDING",
				};
			if (path.endsWith("/uploads"))
				value = {
					...value,
					revision: value.revision + 1,
					uploadId: id,
					evidence: [{ id, state: "ALLOCATED", expiresAt: value.expiresAt }],
				};
			if (path.endsWith("/content"))
				return route.fulfill({ json: { state: "QUARANTINED" } });
			if (path.endsWith("/complete"))
				value = {
					...value,
					revision: value.revision + 1,
					evidence: [{ id, state: "READY", expiresAt: value.expiresAt }],
				};
			if (path.endsWith("/decisions"))
				value = {
					...value,
					revision: value.revision + 1,
					reviewState: "REVIEWED",
					requestState: "REVIEWED",
					history: [
						{
							id,
							action: "REVIEWED",
							reason: "SYNTHETIC_REVIEW",
							at: "2026-10-17",
						},
					],
				};
			if (path.includes("/evidence/"))
				return route.fulfill({
					contentType: "image/png",
					body: fixturePng,
				});
			if (path.endsWith("/escalations"))
				value = {
					...value,
					revision: value.revision + 1,
					history: [
						...value.history,
						{
							id: "escalation",
							action: "ESCALATE",
							reason: "OVERDUE",
							at: "2026-10-17",
						},
					],
				};
			if (path === `/api/admin/venues/${id}/bank/requests`)
				return route.fulfill({ json: [value] });
			return route.fulfill({ json: value });
		});
		await page.goto(`${base}/bank`);
		for (const label of labels)
			await page.getByText(label, { exact: true }).waitFor();
		await page.getByLabel("Accepted synthetic BANK quote ID").fill(id);
		await page.getByLabel("Accepted quote revision").fill("1");
		await page.getByLabel("Accepted content hash").fill("a".repeat(64));
		await page
			.getByRole("button", { name: "Create synthetic request", exact: true })
			.click();
		await page.getByText("OPEN / PENDING / ACTIVE", { exact: true }).waitFor();
		await page
			.getByLabel("Declared amount in minor units (not proof of funds)")
			.fill("1");
		await page
			.getByLabel("Declared payment time (ISO UTC, unverified)")
			.fill("2026-10-17T00:00:00Z");
		await page.getByLabel("Synthetic declared reference").fill("SYNTHETIC");
		await page
			.getByRole("button", { name: "Submit synthetic claim", exact: true })
			.click();
		await page
			.getByText("REVIEW_PENDING / PENDING / ACTIVE", { exact: true })
			.waitFor();
		await page.getByLabel("Approved synthetic JPEG/PNG fixture").setInputFiles({
			name: "SYNTHETIC.png",
			mimeType: "image/png",
			buffer: fixturePng,
		});
		await page
			.getByRole("button", {
				name: "Upload private synthetic receipt",
				exact: true,
			})
			.click();
		await page.getByText(/READY ·/).waitFor();
		assert.equal(
			await page.getByLabel("Approved synthetic JPEG/PNG fixture").inputValue(),
			"",
		);
		groups++;
		await page
			.getByLabel("Declared amount in minor units (not proof of funds)")
			.focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page.evaluate(() => document.activeElement.id),
			"bank-declared-time",
		);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		await page.screenshot({
			path: `../.cache/bank-browser/customer-${width}.png`,
			fullPage: true,
		});
		groups++;
		await page.goto(`${base}/admin/bank`);
		await page.getByLabel("Venue ID", { exact: true }).fill(id);
		await page
			.getByRole("button", { name: "Load scoped queue", exact: true })
			.click();
		await page
			.getByRole("button", { name: `${id} · PENDING`, exact: true })
			.click();
		await page
			.getByRole("button", { name: "Record review action", exact: true })
			.click();
		await page
			.getByText("REVIEWED / REVIEWED / ACTIVE", { exact: true })
			.waitFor();
		for (const label of labels)
			await page.getByText(label, { exact: true }).waitFor();
		assert.equal(value.paymentVerified, false);
		assert.equal(value.bookingConfirmed, false);
		await page
			.getByRole("button", {
				name: "Open audited synthetic preview",
				exact: true,
			})
			.click();
		await page
			.getByAltText("Private synthetic receipt preview", { exact: true })
			.waitFor();
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		await page.screenshot({
			path: `../.cache/bank-browser/reviewer-${width}.png`,
			fullPage: true,
		});
		groups++;
		await page
			.getByLabel("Review action", { exact: true })
			.selectOption("ESCALATE");
		await page
			.getByLabel("Attributed reason", { exact: true })
			.selectOption("OVERDUE");
		await page
			.getByRole("button", { name: "Record review action", exact: true })
			.click();
		await page.getByText(/ESCALATE · OVERDUE/).waitFor();
		mode = "conflict";
		await page
			.getByRole("button", {
				name: "Refresh request and recover current state",
				exact: true,
			})
			.click();
		await page.getByRole("alert").waitFor();
		groups++;
		mode = "denied";
		await page
			.getByRole("button", {
				name: "Refresh request and recover current state",
				exact: true,
			})
			.click();
		await page
			.getByText("Select or create a synthetic request.", { exact: true })
			.waitFor();
		assert.equal(
			await page
				.getByText("REVIEWED / REVIEWED / ACTIVE", { exact: true })
				.count(),
			0,
		);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		await page.screenshot({
			path: `../.cache/bank-browser/denied-${width}.png`,
			fullPage: true,
		});
		groups++;
        mode="normal";
        await page.getByLabel("Finance venue ID",{exact:true}).fill(id);
        await page.getByLabel("Financial review reason",{exact:true}).fill("SYNTHETIC independent test");
        await page.getByLabel("Financial operation",{exact:true}).selectOption("Verify fixture");
        await page.getByLabel("Financial review reason",{exact:true}).fill("SYNTHETIC independent test");
        await page.getByLabel("Independent synthetic source fixture",{exact:true}).selectOption("SYNTHETIC-BANK-50000");
        await page.getByRole("button",{name:"Run selected synthetic operation",exact:true}).click();
        await page.getByText("SIMULATED_VERIFIED",{exact:true}).waitFor();groups++;
        await page.getByLabel("Financial operation",{exact:true}).selectOption("Allocate funds");
        await page.getByLabel("Financial review reason",{exact:true}).fill("SYNTHETIC independent test");
        await page.getByLabel("Target credit, request, exception or correction ID",{exact:true}).fill(id);
        await page.getByLabel("Verified credit ID",{exact:true}).fill(id);
        await page.getByLabel("Allocation LKR minor units",{exact:true}).fill("100");
        await page.getByRole("button",{name:"Run selected synthetic operation",exact:true}).click();
        await page.getByText("SIMULATED_CONFIRMED",{exact:true}).waitFor();groups++;
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
        await page.getByLabel("Financial operation",{exact:true}).focus();await page.keyboard.press("Tab");assert.equal(await page.getByLabel("Target credit, request, exception or correction ID",{exact:true}).evaluate(el=>el===document.activeElement),true);
        await page.screenshot({path:`../.cache/bank-browser/finance-${width}.png`,fullPage:true});groups++;
        mode="denied";await page.getByRole("button",{name:"Run selected synthetic operation",exact:true}).click();
        await page.getByText("No financial result loaded.",{exact:true}).waitFor();assert.equal(await page.getByText("SIMULATED_CONFIRMED",{exact:true}).count(),0);groups++;
        await page.getByRole("heading",{name:"Synthetic credit and reconciliation",exact:true}).locator("..").getByRole("alert").waitFor();
        assert.equal(await page.getByLabel("Verified credit ID",{exact:true}).inputValue(),"");
        mode="empty";await page.getByLabel("Financial operation",{exact:true}).selectOption("Inspect exceptions");await page.getByLabel("Finance venue ID",{exact:true}).fill(id);await page.getByLabel("Financial review reason",{exact:true}).fill("SYNTHETIC queue review");await page.getByRole("button",{name:"Run selected synthetic operation",exact:true}).click();await page.getByText("No matching financial records.",{exact:true}).waitFor();
        await page.getByRole("button",{name:"Clear private context",exact:true}).click();await page.getByText("No financial result loaded.",{exact:true}).waitFor();groups++;
		await page.close();
	}
	console.log(
		`PASS ${groups} synthetic bank browser groups at seven widths. No real bank/provider/customer acceptance claimed.`,
	);
} finally {
	if (browser) await browser.close();
	child.kill("SIGTERM");
	await Promise.race([
		once(child, "exit"),
		new Promise((r) => setTimeout(r, 5000)),
	]);
	if (child.exitCode === null) child.kill("SIGKILL");
}

// Explicit SYNTHETIC browser rehearsal; actual PostgreSQL/API guarantees are tested separately.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import { mkdir } from "node:fs/promises";
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
child.stdout.on("data", (v) => (output += v));
child.stderr.on("data", (v) => (output += v));
const id = "00000000-0000-4000-8000-000000000001",
	room = "00000000-0000-4000-8000-000000000002",
	service = "00000000-0000-4000-8000-000000000003",
	rate = "00000000-0000-4000-8000-000000000004";
let draft = {
	packageId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC package",
	description: "Neutral equipment",
	currency: "LKR",
	basePriceMinor: "12345",
	includedMinutes: 180,
	minimumMinutes: 180,
	maximumMinutes: 240,
	extensionsEnabled: true,
	extensionStepMinutes: 60,
	extensionPriceMinor: "100",
	includedGuests: 4,
	maximumGuests: 5,
	extraGuestPriceMinor: "25",
	venueWide: false,
	roomIds: [room],
	options: [{ serviceId: service, rateVersionId: rate, mode: "INCLUDED" }],
	revision: 0,
	status: "DRAFT",
	permittedActions: ["package.read", "package.edit"],
};
let mode = "normal",
	conflict = true,
	readonly = false,
	observed,
	copy;
let browser,
	checks = 0;
const pass = (s) => {
	checks++;
	console.log("PASS", s);
};
try {
	for (let n = 0; n < 100; n++) {
		if (child.exitCode !== null) throw Error(output);
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
		const request = route.request(),
			url = new URL(request.url());
		if (url.pathname === "/api/session/csrf")
			return route.fulfill({ json: { token: "SYNTHETIC-CSRF" } });
		if (mode === "slow") await new Promise((r) => setTimeout(r, 600));
		if (mode === "denied") return route.fulfill({ status: 403, json: {} });
		if (mode === "error") return route.fulfill({ status: 503, json: {} });
		if (url.pathname.endsWith("/package-options"))
			return route.fulfill({
				json: {
					constraintStatus:
						mode === "missing-policy"
							? "MISSING_POLICY_OR_ROOMS"
							: "CONFIGURED",
					constraints:
						mode === "missing-policy"
							? null
							: {
									venueId: id,
									minimumMinutes: 180,
									policyRevision: 1,
									rooms: [{ roomId: room, capacity: 5, facilities: ["HDMI"] }],
								},
					services: [
						{
							serviceId: service,
							rateVersionId: rate,
							code: "HDMI",
							label: "SYNTHETIC HDMI equipment",
							revision: 1,
							unitPriceMinor: "123",
							requiredFacilityCode: "HDMI",
						},
					],
				},
			});
		if (url.pathname.endsWith("/history"))
			return route.fulfill({
				json: {
					items: [
						{
							revision: 0,
							snapshot: draft,
							occurredAt: "2026-10-16T00:00:00Z",
						},
					],
					total: 1,
					page: 0,
					size: 20,
				},
			});
		if (url.pathname.endsWith("/preview"))
			return route.fulfill({
				json: {
					label: "DRAFT PREVIEW - NOT FOR SALE / NOT A CHECKOUT QUOTATION",
					knownPackageComponentsMinor: draft.basePriceMinor,
					taxStatus: "UNRESOLVED",
					feeStatus: "UNRESOLVED",
					options: [
						{
							serviceId: service,
							label: "SYNTHETIC HDMI equipment",
							mode: "INCLUDED",
							additionalChargeMinor: "0",
							selected: true,
						},
					],
				},
			});
		if (url.pathname.endsWith("/duplicate")) {
			const body = request.postDataJSON();
			copy = { ...draft, packageId: room, code: body.code };
			return route.fulfill({ status: 201, json: copy });
		}
		if (request.method() === "PUT") {
			observed = request.postDataJSON();
			if (conflict) {
				conflict = false;
				return route.fulfill({
					status: 409,
					json: { message: "SYNTHETIC stale revision" },
				});
			}
			draft = { ...draft, ...observed, revision: 2 };
			return route.fulfill({ json: draft });
		}
		if (request.method() === "POST") {
			observed = request.postDataJSON();
			return route.fulfill({ status: 201, json: { ...draft, ...observed } });
		}
		if (url.pathname.endsWith(`/packages/${id}`))
			return route.fulfill({
				json: { ...draft, revision: conflict ? draft.revision : 1 },
			});
		const match =
			mode !== "empty" &&
			(!url.searchParams.get("q") ||
				draft.code
					.toLowerCase()
					.includes(url.searchParams.get("q").toLowerCase()));
		return route.fulfill({
			json: {
				items: match ? [draft, ...(copy ? [copy] : [])] : [],
				total: match ? (copy ? 2 : 1) : 0,
				page: 0,
				size: 24,
				permittedActions: readonly
					? ["package.read"]
					: ["package.read", "package.edit"],
				canPublish: false,
			},
		});
	});
	const open = async () => {
		await page.goto(`${base}/admin/settings/packages`);
		await page.getByLabel("Assigned venue ID").fill(id);
		await page
			.getByRole("button", { name: "Preview SYNTHETIC", exact: true })
			.waitFor();
	};
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		await open();
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
			`overflow at ${width}`,
		);
		pass(`package page reflow at ${width}px`);
	}
	await page.setViewportSize({ width: 1280, height: 900 });
	await open();
	await page.getByLabel("Package name or code").fill("absent");
	await page.getByText("No matching package drafts.").waitFor();
	await page.getByLabel("Package name or code").fill("");
	await page
		.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
		.waitFor();
	pass("scoped list search and empty recovery");
	await page
		.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
		.click();
	await page.getByLabel("Base price (LKR)").fill("1.234");
	await page
		.getByLabel("Business reason for this change")
		.fill("SYNTHETIC precise edit");
	await page.getByRole("button", { name: "Save unpublished draft" }).click();
	await page
		.getByRole("alert")
		.getByText(/at most two decimal places/)
		.waitFor();
	assert.equal(await page.getByLabel("Base price (LKR)").inputValue(), "1.234");
	await page.getByLabel("Base price (LKR)").fill("100.01");
	await page.getByRole("button", { name: "Save unpublished draft" }).click();
	await page.getByRole("heading", { name: "Draft conflict" }).waitFor();
	assert.equal(observed.basePriceMinor, "10001");
	await page
		.getByRole("button", { name: "Load latest for comparison" })
		.click();
	await page.getByRole("heading", { name: "Latest revision 1" }).waitFor();
	await page
		.getByRole("button", { name: "Use latest revision with my reviewed draft" })
		.click();
	await page.getByRole("button", { name: "Save unpublished draft" }).click();
	await page.getByRole("dialog").waitFor({ state: "hidden" });
	assert.equal(observed.expectedRevision, 1);
	assert.equal(observed.basePriceMinor, "10001");
	pass(
		"precision rejection, retained inputs and explicit stale-revision recovery",
	);
	await page
		.getByRole("button", { name: "Preview SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("heading", { name: /DRAFT PREVIEW - NOT FOR SALE/ })
		.waitFor();
	await page
		.getByText("This is not a total payable amount.", { exact: false })
		.waitFor();
	await page.getByText("Tax: UNRESOLVED", { exact: false }).waitFor();
	await page.getByRole("button", { name: "Close dialog" }).click();
	pass("preview labels unresolved tax/fees and no additional included charge");
	await page
		.getByRole("button", { name: "History SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("heading", { name: "Revision 0", exact: true })
		.waitFor();
	await page.getByRole("button", { name: "Close dialog" }).click();
	pass("saved draft history visible separately from publication");
	page.once("dialog", async (d) => d.accept("SYNTHETIC-COPY"));
	await page
		.getByRole("button", { name: "Duplicate SYNTHETIC", exact: true })
		.click();
	await page
		.getByRole("button", { name: "Edit SYNTHETIC-COPY", exact: true })
		.waitFor();
	pass("same-venue duplicate remains draft");
	await page
		.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
		.click();
	for (let i = 0; i < 35; i++) {
		await page.keyboard.press("Tab");
		assert.ok(
			await page
				.getByRole("dialog")
				.evaluate((d) => d.contains(document.activeElement)),
		);
	}
	await page.keyboard.press("Escape");
	await page.getByRole("dialog").waitFor({ state: "hidden" });
	assert.match(
		await page.evaluate(() => document.activeElement.textContent),
		/Edit SYNTHETIC/,
	);
	pass("editor keyboard containment, Escape and focus restoration");
	readonly = true;
	await open();
	assert.equal(
		await page
			.getByRole("button", { name: "Create draft", exact: true })
			.count(),
		0,
	);
	assert.equal(
		await page
			.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
			.count(),
		0,
	);
	readonly = false;
	pass("read-only server capability hides mutations");
	mode = "missing-policy";
	await open();
	await page
		.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
		.click();
	assert.equal(
		await page
			.getByRole("button", { name: "Save unpublished draft" })
			.isDisabled(),
		true,
	);
	await page.keyboard.press("Escape");
	mode = "normal";
	pass("missing policy blocks validated save");
	mode = "slow";
	await page.goto(`${base}/admin/settings/packages`);
	await page.getByLabel("Assigned venue ID").fill(id);
	await page.getByRole("heading", { name: "Loading", exact: true }).waitFor();
	await page
		.getByRole("button", { name: "Preview SYNTHETIC", exact: true })
		.waitFor();
	mode = "denied";
	await page.getByLabel("Package name or code").fill("denied");
	await page.getByRole("heading", { name: "Access denied" }).waitFor();
	mode = "error";
	await page.getByRole("button", { name: "Retry", exact: true }).click();
	await page.getByRole("heading", { name: "Unable to connect" }).waitFor();
	mode = "normal";
	await page.getByLabel("Package name or code").fill("");
	await page
		.getByRole("button", { name: "Preview SYNTHETIC", exact: true })
		.waitFor();
	pass("loading, denied, service failure and retry recover safely");
	await page.emulateMedia({ reducedMotion: "reduce" });
	await page.setViewportSize({ width: 375, height: 900 });
	await page
		.getByRole("button", { name: "Edit SYNTHETIC", exact: true })
		.click();
	assert.ok(
		await page
			.getByRole("dialog")
			.evaluate((d) => d.scrollWidth <= d.clientWidth + 1),
		"mobile editor overflow",
	);
	await mkdir("../.cache/package-browser", { recursive: true });
	await page.screenshot({
		path: "../.cache/package-browser/editor-mobile.png",
		fullPage: true,
	});
	await page.keyboard.press("Escape");
	await page.setViewportSize({ width: 1280, height: 900 });
	await page.screenshot({
		path: "../.cache/package-browser/list-desktop.png",
		fullPage: true,
	});
	assert.deepEqual(errors, []);
	pass("mobile editor reflow, reduced motion and zero browser exceptions");
	console.log(
		`PASS ${checks} SYNTHETIC browser groups; no customer/production acceptance`,
	);
} finally {
	await browser?.close();
	const stopped = once(child, "exit");
	child.kill("SIGTERM");
	await stopped;
}

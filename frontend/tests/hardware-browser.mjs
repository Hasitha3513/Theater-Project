// Prepared UI rehearsal only; no application startup, fake responses or backend acceptance.
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
if (process.env.CINENEST_HARDWARE_BROWSER_TESTS !== "AUTHORIZED_REHEARSAL")
	throw Error("Separate bounded browser/runtime authorization required");
const base = new URL(process.env.CINENEST_HARDWARE_BROWSER_ORIGIN ?? "");
assert(["127.0.0.1", "localhost", "[::1]"].includes(base.hostname));
assert(["http:", "https:"].includes(base.protocol));
assert(
	!base.username &&
		!base.password &&
		base.pathname === "/" &&
		!base.search &&
		!base.hash,
);
const output = process.env.CINENEST_HARDWARE_BROWSER_EVIDENCE;
assert.match(output ?? "", /^\/tmp\/cinenest-hardware-browser-[a-f0-9]{32}$/);
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const browser = await chromium.launch({ headless: true });
let external = 0;
const checks = [];
try {
	const context = await browser.newContext(),
		page = await context.newPage();
	await context.route("**/*", (route) => {
		const u = new URL(route.request().url());
		if (["http:", "https:"].includes(u.protocol) && u.origin !== base.origin) {
			external++;
			return route.abort();
		}
		return route.continue();
	});
	await page.goto(new URL("/admin/hardware", base).href);
	await page
		.getByRole("heading", { name: "Hardware Management", exact: true })
		.waitFor();
	await page
		.getByLabel("Device category", { exact: true })
		.selectOption("Lighting Controllers");
	await page
		.getByRole("link", {
			name: "Open existing Lighting Simulator",
			exact: true,
		})
		.waitFor();
	assert(
		await page
			.getByRole("button", { name: "Register synthetic device", exact: true })
			.isDisabled(),
	);
	checks.push("unsupported-category-commands-disabled");
	await page
		.getByLabel("Device category", { exact: true })
		.selectOption("Smart Door Locks");
	assert(
		await page
			.getByRole("button", { name: "Activate approved device", exact: true })
			.isDisabled(),
	);
	await page
		.getByLabel("Venue reference", { exact: true })
		.fill("10000000-0000-4000-8000-000000000001");
	await page
		.getByRole("button", { name: "Load device registry", exact: true })
		.click();
	await page
		.getByRole("status")
		.filter({ hasText: /denied or unavailable/ })
		.waitFor();
	checks.push("anonymous-protected-read-denied");
	await mkdir(output, { recursive: true, mode: 0o700 });
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.screenshot({
			path: `${output}/hardware-${width}.png`,
			fullPage: true,
		});
		checks.push(`responsive-${width}`);
	}
	await page.getByLabel("Venue reference", { exact: true }).focus();
	assert(
		await page
			.getByLabel("Venue reference", { exact: true })
			.evaluate((el) => el === document.activeElement),
	);
	checks.push("keyboard-field-focus");
	assert.equal(external, 0);
	await writeFile(
		`${output}/results.json`,
		JSON.stringify({
			classification: "UI_REHEARSAL_ONLY",
			checks,
			externalRequests: external,
			ownerAcceptance: false,
			backendQualification: false,
		}),
		{ mode: 0o600 },
	);
} finally {
	await browser.close();
}

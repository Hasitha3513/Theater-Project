// Optional local browser verification using an existing host Playwright installation, not an application dependency.
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
child.stdout.on("data", (b) => (output += b));
child.stderr.on("data", (b) => (output += b));
let browser;
let checks = 0;
const pass = (label) => {
	checks++;
	console.log("PASS", label);
};
try {
	for (let i = 0; i < 100; i++) {
		if (child.exitCode !== null) throw Error(output);
		try {
			await fetch(`http://127.0.0.1:${port}`);
			break;
		} catch {
			await new Promise((r) => setTimeout(r, 100));
		}
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage();
	const base = `http://127.0.0.1:${port}`;
	const errors = [];
	page.on("pageerror", (error) => errors.push(error.message));
	for (const width of [320, 375, 640, 768, 1024, 1280, 1536]) {
		await page.setViewportSize({ width, height: 900 });
		for (const path of ["/", "/admin", "/admin/components", "/account"]) {
			await page.goto(base + path);
			await page.locator("main").waitFor();
			assert.equal(await page.locator("main").count(), 1);
			assert.equal(await page.locator("h1").count(), 1);
      if (path.startsWith("/admin")) assert.equal(await page.locator(".sidebar").isVisible(), width >= 1024);
			assert.ok(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth + 1,
				),
				`overflow ${width} ${path}`,
			);
		}
		pass(`customer/admin/examples/account landmarks and reflow at ${width}px`);
	}
	await page.setViewportSize({ width: 375, height: 812 });
	await page.goto(`${base}/admin`);
	await page.getByRole("button", { name: "Open navigation" }).click();
	assert.ok(await page.locator("dialog").evaluate((d) => d.open));
	for (let i = 0; i < 18; i++) {
		await page.keyboard.press("Tab");
		assert.ok(
			await page.evaluate(() =>
				document.querySelector("dialog").contains(document.activeElement),
			),
		);
	}
	await page.keyboard.press("Escape");
	assert.ok(
		await page
			.getByRole("button", { name: "Open navigation" })
			.evaluate((e) => e === document.activeElement),
	);
	pass("mobile dialog traps focus, Escape closes and restores focus");
	await page.getByRole("button", { name: "Open navigation" }).click();
	await page
		.locator("dialog")
		.getByRole("link", { name: "UI examples" })
		.click();
	await page.waitForURL("**/admin/components");
	assert.equal(new URL(page.url()).pathname, "/admin/components");
	assert.equal(await page.locator("dialog[open]").count(), 0);
	pass("mobile navigation changes route and closes");
	await page.setViewportSize({ width: 1280, height: 900 });
	assert.equal(
		await page.locator('aside a[aria-current="page"]').textContent(),
		"UI examples",
	);
	pass("active route is exact and native navigation is labelled");
	await page.getByLabel("Filter examples").fill("beta");
	assert.equal(await page.locator(".example-card").count(), 1);
	assert.match(await page.locator(".example-card").innerText(), /SAMPLE-02/);
	await page.getByRole("button", { name: "Table view" }).click();
	assert.equal(await page.locator("tbody tr").count(), 1);
	assert.match(await page.locator("tbody").innerText(), /SAMPLE-02/);
	await page.getByLabel("Filter examples").fill("no match");
	await page.getByRole("button", { name: "Clear filter" }).waitFor();
	await page.getByRole("button", { name: "Clear filter" }).click();
	assert.equal(await page.locator("tbody tr").count(), 3);
	pass("card/table parity, retained filter, empty state and reset");
	for (const state of [
		"loading",
		"error",
		"unauthenticated",
		"denied",
		"unconfigured",
	]) {
		await page.getByLabel("Preview a synthetic state").selectOption(state);
		assert.equal(await page.locator(`.state-${state}`).count(), 1);
	}
	pass("all shared states render without blank pages");
	assert.ok(
		await page
			.getByRole("button", { name: "Unavailable example" })
			.isDisabled(),
	);
	assert.ok(
		await page.getByRole("button", { name: "Please wait…" }).isDisabled(),
	);
	pass("disabled and busy controls prevent actions");
	await page.goto(`${base}/admin/account`);
	await page.getByRole("button", { name: "Check session" }).click();
	await page
		.getByRole("heading", { name: "Session service not configured" })
		.waitFor();
	pass("unconfigured real adapter reports honestly");
	const account = {
		accountId: "00000000-0000-4000-8000-000000000001",
		eligibility: "ELIGIBLE",
	};
	for (const [status, title] of [
		[401, "Sign-in required"],
		[403, "Access denied"],
		[500, "Unable to connect"],
	]) {
		await page.route("**/api/accounts/me", (r) =>
			r.fulfill({ status, body: "" }),
		);
		await page.getByRole("button", { name: "Check session" }).click();
		await page.getByRole("heading", { name: title }).waitFor();
		assert.equal(await page.locator(".account-summary").count(), 0);
		await page.unroute("**/api/accounts/me");
	}
	pass(
		"SIMULATED responses: unauthenticated/denied/error clear protected content",
	);
	await page.route("**/api/accounts/me", (r) => r.fulfill({ json: account }));
	await page.route("**/api/session/csrf", (r) =>
		r.fulfill({
			json: { headerName: "X-CSRF-TOKEN", token: "synthetic-csrf" },
		}),
	);
	await page.route("**/api/session/logout", (r) => {
		assert.equal(r.request().method(), "POST");
		assert.equal(r.request().headers()["x-csrf-token"], "synthetic-csrf");
		return r.fulfill({ status: 204, body: "" });
	});
	await page.getByRole("button", { name: "Check session" }).click();
	await page.getByText(account.accountId, { exact: true }).waitFor();
	await page
		.getByRole("button", { name: "Log out of current session" })
		.click();
	await page.getByRole("heading", { name: "Sign-in required" }).waitFor();
	assert.equal(await page.locator(".account-summary").count(), 0);
	pass(
		"SIMULATED account/CSRF/logout presentation; no provider admission claim",
	);
	await page.goto(`${base}/admin`);
	await page.keyboard.press("Tab");
	assert.equal(
		await page.evaluate(() => document.activeElement?.textContent),
		"Skip to content",
	);
	await page.keyboard.press("Enter");
	assert.equal(
		await page.evaluate(() => document.activeElement?.id),
		"main-content",
	);
	pass("skip link reaches the single main landmark");
	await page.emulateMedia({ reducedMotion: "reduce" });
	assert.match(
		await page.evaluate(() =>
			getComputedStyle(document.documentElement)
				.getPropertyValue("--cn-motion-fast")
				.trim(),
		),
		/^0(?:ms|s)$/,
	);
	pass("reduced-motion token applies");
	await mkdir("../.cache", { recursive: true });
	for (const [path, name, width] of [
		["/admin", "admin-desktop", 1280],
		["/", "customer-desktop", 1280],
		["/admin/components", "examples-mobile", 375],
	]) {
		await page.setViewportSize({ width, height: 900 });
		await page.goto(base + path);
		await page.screenshot({ path: `../.cache/ui-${name}.png`, fullPage: true });
	}
	assert.deepEqual(errors, []);
	pass("no browser runtime errors");
	console.log(
		`Browser checks: ${checks} passed. Screenshots are local ignored QA artifacts. No WCAG certification, real provider or real account claim.`,
	);
} finally {
	if (browser) await browser.close();
	const stopped = once(child, "exit");
	child.kill("SIGTERM");
	await stopped;
}

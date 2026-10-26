// Simulated OIDC provider;real Browser -> Next -> Spring -> PostgreSQL. Not live Google qualification.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { pathToFileURL, fileURLToPath } from "node:url";
const run = process.argv[2];
assert.match(run ?? "", /^\/tmp\/cinenest-google-e2e-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const base = `http://127.0.0.1:${config.port}`;
const shots = new URL("../../.cache/google-sso-e2e/", import.meta.url);
await mkdir(shots, { recursive: true, mode: 0o700 });
const server = spawn(
	process.execPath,
	[
		"node_modules/next/dist/bin/next",
		"start",
		"--hostname",
		"127.0.0.1",
		"--port",
		String(config.port),
	],
	{
		cwd: new URL("..", import.meta.url),
		env: {
			...process.env,
			CINENEST_AUTH_ORIGIN: config.backend,
			CINENEST_GOOGLE_CONTRACT_ISSUER: config.issuer,
			NEXT_TELEMETRY_DISABLED: "1",
		},
		stdio: "ignore",
	},
);
let browser;
const results = [];
try {
	for (let i = 0; i < 100; i++) {
		try {
			if ((await fetch(`${base}/login`)).status === 200) break;
		} catch {}
		if (i === 99) throw Error("Next startup");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch();
	const context = await browser.newContext();
	const page = await context.newPage();
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 950 });
		await page.goto(`${base}/login`);
		await page.getByRole("button", { name: /Continue with Google/ }).waitFor();
		assert.equal(await page.locator("main").count(), 1);
		assert.equal(await page.locator("h1").count(), 1);
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.screenshot({
			path: fileURLToPath(new URL(`login-${width}.png`, shots)),
			fullPage: true,
		});
	}
	results.push(
		"three responsive qualified-only Google controls;one main/h1;no overflow",
	);
	await page.getByRole("button", { name: /Continue with Google/ }).focus();
	await page.keyboard.press("Enter");
	await page.waitForURL(`${base}/account`);
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	assert.ok(
		(await page.locator("main").textContent()).includes(config.account),
	);
	results.push(
		"keyboard Google protocol login;pre-associated existing UUID;real JDBC session",
	);
	const cookie = (await context.cookies()).find(
		(c) => c.name === "CINENEST_SESSION",
	);
	assert.ok(cookie.secure && cookie.httpOnly && cookie.sameSite === "Lax");
	await page.reload();
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	results.push("secure cookies and refresh continuity;no cookie injection");
	await page
		.getByRole("button", { name: /Log out of current session/ })
		.click();
	await page.getByText("Current session ended.", { exact: false }).waitFor();
	assert.equal(
		await page.evaluate(async () => (await fetch("/api/accounts/me")).status),
		401,
	);
	results.push("existing CSRF/logout session invalidation");
	await page.goto(
		`${base}/api/auth/google/callback?code=invalid&state=invalid`,
	);
	await page.waitForURL(`${base}/login?google=denied`);
	await page
		.getByText("Google sign-in was rejected, cancelled or unavailable.", {
			exact: false,
		})
		.waitFor();
	assert.ok(
		!(await page.locator("body").textContent()).includes("invalid&state"),
	);
	results.push("sanitized callback rejection and frontend error state");
	await writeFile(
		`${run}/results.json`,
		JSON.stringify(
			{
				status: "PASS",
				provider: "SIMULATED_NOT_GOOGLE",
				interception: false,
				cookieInjection: false,
				results,
				screenshots: 3,
			},
			null,
			2,
		),
	);
	console.log(
		JSON.stringify({
			status: "PASS",
			provider: "SIMULATED_NOT_GOOGLE",
			results,
		}),
	);
} finally {
	await browser?.close();
	server.kill("SIGTERM");
	await once(server, "exit").catch(() => {});
}

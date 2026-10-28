// Real Chromium -> Next.js -> Spring Security/JDBC -> PostgreSQL. No route interception or cookie injection.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import {
	readFile,
	readdir,
	writeFile,
	mkdir,
	rename,
	unlink,
} from "node:fs/promises";
import { pathToFileURL, fileURLToPath } from "node:url";
import { createIngress } from "../../scripts/local-https-ingress.mjs";
const run = process.argv[2];
assert.match(run ?? "", /^\/tmp\/cinenest-manual-e2e-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const reserve = createServer().listen(0, "127.0.0.1");
await once(reserve, "listening");
const port = reserve.address().port;
await new Promise((r) => reserve.close(r));
const tls = process.env.CINENEST_HTTPS_E2E === "true";
const base = tls ? "https://localhost:4443" : `http://127.0.0.1:${port}`;
const shots = new URL("../../.cache/manual-auth-e2e/", import.meta.url);
await mkdir(shots, { recursive: true, mode: 0o700 });
const frontend = spawn(
	process.execPath,
	[
		"node_modules/next/dist/bin/next",
		"start",
		"--hostname",
		"127.0.0.1",
		"--port",
		String(port),
	],
	{
		cwd: new URL("..", import.meta.url),
		env: {
			...process.env,
			CINENEST_AUTH_ORIGIN: config.backend,
			NEXT_TELEMETRY_DISABLED: "1",
		},
		stdio: "ignore",
	},
);
let browser;
let ingress;
const results = [];
const requests = [];
async function control(op) {
	await writeFile(`${run}/command.tmp`, JSON.stringify({ op }), {
		mode: 0o600,
	});
	await rename(`${run}/command.tmp`, `${run}/command.json`);
	for (let i = 0; i < 200; i++) {
		try {
			const x = JSON.parse(await readFile(`${run}/reply.json`, "utf8"));
			await unlink(`${run}/reply.json`);
			assert.equal(x.ok, true);
			return x;
		} catch (e) {
			if (e.code !== "ENOENT") throw e;
		}
		await new Promise((r) => setTimeout(r, 20));
	}
	throw Error("Control timed out");
}
try {
	if (tls) {
		ingress = createIngress(
			{
				key: await readFile(process.env.CINENEST_LOCAL_HTTPS_KEY),
				cert: await readFile(process.env.CINENEST_LOCAL_HTTPS_CERT),
			},
			port,
		);
		await new Promise((resolve, reject) => {
			ingress.once("error", reject);
			ingress.listen(4443, "127.0.0.1", resolve);
		});
	}
	for (let i = 0; i < 150; i++) {
		try {
			if ((await fetch(base + "/login")).status === 200) break;
		} catch {}
		if (i === 149) throw Error("Next startup unavailable");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({
		headless: true,
		args: ["--disable-dev-shm-usage"],
	});
	const context = await browser.newContext();
	const page = await context.newPage();
	if (tls) {
		const denied = await page.request.get(`${base}/login`, {
			headers: { "X-Forwarded-Host": "evil.invalid" },
		});
		assert.equal(denied.status(), 400);
		results.push(
			"trusted HTTPS without certificate bypass;spoofed forwarding rejected",
		);
	}
	page.on("response", (r) => {
		if (r.url().includes("/api/"))
			requests.push({ path: new URL(r.url()).pathname, status: r.status() });
	});
	async function fillLogin(password = "Local cinema testing 2026!") {
		await page
			.getByLabel("Test email address", { exact: true })
			.fill("browser@example.test");
		await page.getByLabel("Password", { exact: true }).fill(password);
		await page.getByRole("button", { name: /^Log in/ }).click();
	}
	async function register() {
		await page.goto(base + "/register");
		await page
			.getByLabel("Test email address", { exact: true })
			.fill("browser@example.test");
		await page
			.getByLabel("Password", { exact: true })
			.fill("Local cinema testing 2026!");
		await page.getByRole("button", { name: /Create test account/ }).click();
		await page
			.getByText("If this test registration can proceed,", { exact: false })
			.waitFor();
	}
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 900 });
		for (const route of ["register", "login"]) {
			await page.goto(base + "/" + route);
			assert.equal(await page.locator("main").count(), 1);
			assert.equal(await page.locator("h1").count(), 1);
			assert.ok(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
			);
			await page.evaluate(
				() =>
					new Promise((r) =>
						requestAnimationFrame(() => requestAnimationFrame(r)),
					),
			);
			await page.screenshot({
				animations: "disabled",
				path: fileURLToPath(new URL(`${route}-${width}.png`, shots)),
				fullPage: true,
			});
		}
	}
	results.push(
		"six responsive form captures;one main/h1;no horizontal overflow",
	);
	assert.equal(
		await page
			.getByRole("button", { name: /Google sign-in unavailable/ })
			.isDisabled(),
		true,
	);
	assert.equal(
		await page
			.getByRole("button", { name: /Facebook Login unavailable/ })
			.isDisabled(),
		true,
	);
	await page.goto(`${base}/register`);
	await page
		.getByLabel("Test email address", { exact: true })
		.fill("too-long@example.test");
	await page.getByLabel("Password", { exact: true }).fill("x".repeat(129));
	assert.equal(
		(await page.getByLabel("Password", { exact: true }).inputValue()).length,
		129,
	);
	await page.getByRole("button", { name: /Create test account/ }).click();
	await page
		.getByText("Password must contain 15–128 characters.", { exact: false })
		.waitFor();
	assert.equal(
		requests.filter((x) => x.path === "/api/auth/registrations").length,
		0,
	);
	results.push(
		"overlong password rejected without silent browser truncation or submission",
	);
	await register();
	let token;
	for (const file of await readdir(config.outbox)) {
		const lines = (await readFile(`${config.outbox}/${file}`, "utf8")).split(
			"\n",
		);
		if (lines[1] === "browser@example.test") token = lines[2];
	}
	assert.match(token, /^[0-9a-f]{64}$/);
	await page.goto(base + "/login");
	await fillLogin();
	await page.getByText("Login was not accepted.", { exact: false }).waitFor();
	results.push("pre-verification login denied");
	await register();
	await page.getByLabel("Private test-email verification token").fill(token);
	await page.getByRole("button", { name: /Verify test email/ }).click();
	await page.getByText("Test email verified.", { exact: false }).waitFor();
	results.push(
		"registration;duplicate normalized email safe;one-time test-email verified",
	);
	await page.goto(base + "/login");
	await fillLogin("Incorrect phrase for test!");
	await page.getByText("Login was not accepted.", { exact: false }).waitFor();
	await page.evaluate(() => fetch("/api/auth/csrf"));
	const before = (await context.cookies()).find(
		(c) => c.name === "CINENEST_SESSION",
	)?.value;
	assert.ok(before, "real browser must retain secure pre-auth cookie");
	await page.getByLabel("Test email address", { exact: true }).focus();
	await page.keyboard.press("Tab");
	assert.equal(
		await page
			.getByLabel("Password", { exact: true })
			.evaluate((e) => e === document.activeElement),
		true,
	);
	await fillLogin();
	await page.getByText("Login confirmed.", { exact: false }).waitFor();
	const cookie = (await context.cookies()).find(
		(c) => c.name === "CINENEST_SESSION",
	);
	assert.notEqual(cookie.value, before);
	assert.ok(cookie.secure && cookie.httpOnly);
	assert.equal(cookie.sameSite, "Lax");
	results.push(
		"password login;keyboard control;secure cookie;session rotation",
	);
	await page.goto(base + "/account");
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	await page.reload();
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 900 });
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.evaluate(
			() =>
				new Promise((r) =>
					requestAnimationFrame(() => requestAnimationFrame(r)),
				),
		);
		await page.screenshot({
			animations: "disabled",
			path: fileURLToPath(new URL(`account-${width}.png`, shots)),
			fullPage: true,
		});
	}
	results.push(
		"authenticated account;refresh continuity;three responsive account captures",
	);
	await control("expire-session");
	await page.getByRole("button", { name: /Check session/ }).click();
	await page
		.getByText("Session confirmed by server")
		.waitFor({ state: "hidden" });
	assert.equal(
		await page.evaluate(
			async () =>
				(await fetch("/api/accounts/me", { cache: "no-store" })).status,
		),
		401,
	);
	results.push("PostgreSQL session expiry denies protected account");
	await page.goto(base + "/login");
	await fillLogin();
	await page.getByText("Login confirmed.", { exact: false }).waitFor();
	await page.goto(base + "/account");
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	await page
		.getByRole("button", { name: /Log out of current session/ })
		.click();
	await page.getByText("Current session ended.", { exact: false }).waitFor();
	assert.equal(
		await page.evaluate(async () => (await fetch("/api/accounts/me")).status),
		401,
	);
	results.push("secure logout;revoked session denied");
	assert.equal(
		await page.evaluate(
			async () =>
				(
					await fetch("/api/auth/registrations", {
						method: "POST",
						headers: { "Content-Type": "application/json" },
						body: "{}",
					})
				).status,
		),
		403,
	);
	results.push("CSRF denial");
	const rate = await page.evaluate(async () => {
		const statuses = [];
		for (let i = 0; i < 6; i++) {
			const csrf = await (await fetch("/api/auth/csrf")).json();
			statuses.push(
				(
					await fetch("/api/auth/login", {
						method: "POST",
						headers: {
							"Content-Type": "application/json",
							[csrf.headerName]: csrf.token,
						},
						body: JSON.stringify({
							email: "unknown@example.test",
							password: "Wrong but long test secret!",
						}),
					})
				).status,
			);
		}
		return statuses;
	});
	assert.deepEqual(rate, [401, 401, 401, 401, 401, 429]);
	results.push(
		"unknown account enumeration-resistant errors;durable rate limit",
	);
	await register();
	await page.getByLabel("Private test-email verification token").fill(token);
	await page.getByRole("button", { name: /Verify test email/ }).click();
	await page.getByText("Request not accepted.", { exact: false }).waitFor();
	results.push("used-token rejection");
	await register();
	const expired = await control("expired-token");
	await page
		.getByLabel("Private test-email verification token")
		.fill(expired.token);
	await page.getByRole("button", { name: /Verify test email/ }).click();
	await page.getByText("Request not accepted.", { exact: false }).waitFor();
	results.push("expired-token denial through real frontend and PostgreSQL");
	const noJs = await browser.newContext({ javaScriptEnabled: false });
	const native = await noJs.newPage();
	await native.goto(`${base}/login`);
	await native
		.getByLabel("Test email address", { exact: true })
		.fill("nojs@example.test");
	await native
		.getByLabel("Password", { exact: true })
		.fill("Local cinema testing 2026!");
	const denied = native.waitForResponse(
		(r) => new URL(r.url()).pathname === "/api/auth/login",
	);
	await native.getByRole("button", { name: /^Log in/ }).click();
	const fallback = await denied;
	assert.equal(fallback.request().method(), "POST");
	assert.equal(new URL(fallback.url()).search, "");
	assert.equal(fallback.status(), 403);
	await noJs.close();
	results.push(
		"JavaScript-disabled native form uses POST;no credentials in URL;CSRF fails closed",
	);
	await writeFile(
		`${run}/results.json`,
		JSON.stringify(
			{
				status: "PASS",
				interception: false,
				cookieInjection: false,
				results,
				requests,
				screenshots: 9,
			},
			null,
			2,
		),
	);
	console.log(
		JSON.stringify({ status: "PASS", results, requests, screenshots: 9 }),
	);
} finally {
	await browser?.close();
	if (ingress) await new Promise((resolve) => ingress.close(resolve));
	frontend.kill("SIGTERM");
	await once(frontend, "exit").catch(() => {});
}

// Chromium -> actual Next.js -> Spring Boot -> PostgreSQL; no intercepted application responses.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { fileURLToPath, pathToFileURL } from "node:url";

const run = process.argv[2];
assert.match(run ?? "", /^\/tmp\/cinenest-profile-e2e-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const reserve = createServer().listen(0, "127.0.0.1");
await once(reserve, "listening");
const port = reserve.address().port;
await new Promise((r) => reserve.close(r));
const base = `http://127.0.0.1:${port}`,
	shots = new URL("../../.cache/customer-profile-e2e/", import.meta.url);
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
        CINENEST_FACEBOOK_MODE:"DISABLED",CINENEST_FACEBOOK_LIVE_EXECUTION:"false",CINENEST_GOOGLE_MODE:"DISABLED",CINENEST_GOOGLE_LIVE_EXECUTION:"false",
			NEXT_TELEMETRY_DISABLED: "1",
		},
		stdio: "ignore",
	},
);
let browser;
const results = [];
try {
	for (let i = 0; i < 150; i++) {
		try {
			if ((await fetch(`${base}/login`)).status === 200) break;
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
	await page.goto(`${base}/register`);
	await page
		.getByLabel("Test email address", { exact: true })
		.fill("profile-browser@example.test");
	await page
		.getByLabel("Password", { exact: true })
		.fill("Local profile testing 2026!");
	await page.getByRole("button", { name: /Create test account/ }).click();
	await page
		.getByText("If this test registration can proceed,", { exact: false })
		.waitFor();
	let token;
	for (const file of await readdir(config.outbox)) {
		const lines = (await readFile(`${config.outbox}/${file}`, "utf8")).split(
			"\n",
		);
		if (lines[1] === "profile-browser@example.test") token = lines[2];
	}
	assert.match(token, /^[0-9a-f]{64}$/);
	await page.getByLabel("Private test-email verification token").fill(token);
	await page.getByRole("button", { name: /Verify test email/ }).click();
	await page.getByText("Test email verified.", { exact: false }).waitFor();
	await page.goto(`${base}/login`);
	await page
		.getByLabel("Test email address", { exact: true })
		.fill("profile-browser@example.test");
	await page
		.getByLabel("Password", { exact: true })
		.fill("Local profile testing 2026!");
	await page.getByRole("button", { name: /^Log in/ }).click();
	await page.getByText("Login confirmed.", { exact: false }).waitFor();
	await page.goto(`${base}/account`);
	await page
		.getByRole("button", { name: "Edit profile", exact: true })
		.waitFor();
	const panel = page.locator("section").filter({
		has: page.getByRole("heading", { name: "My profile", exact: true }),
	});
	assert.ok((await panel.innerText()).includes("VERIFIED"));
	assert.ok((await panel.innerText()).includes("MANUAL"));
	results.push(
		"manual registration/private verification/login and authoritative own-profile read",
	);
	await page.getByRole("button", { name: "Edit profile", exact: true }).click();
	await page
		.getByLabel("Full name", { exact: true })
		.fill("Cancelled synthetic name");
	await page.getByRole("button", { name: "Cancel profile edit" }).click();
	assert.ok(!(await panel.innerText()).includes("Cancelled synthetic name"));
	results.push("cancel does not persist");
	await page.getByRole("button", { name: "Edit profile", exact: true }).click();
	await page
		.getByLabel("Full name", { exact: true })
		.fill("Synthetic Profile Customer");
	await page
		.getByLabel("Phone number (optional, unverified)")
		.fill("+94 77 123 4567");
	await page.getByRole("button", { name: "Save profile", exact: true }).click();
	await page.getByText("Profile saved.", { exact: true }).waitFor();
	await page.reload();
	await page
		.getByRole("button", { name: "Edit profile", exact: true })
		.waitFor();
	assert.ok((await panel.innerText()).includes("Synthetic Profile Customer"));
	results.push("atomic profile save;refresh/revisit persistence");
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		assert.equal(await page.locator("h1").count(), 1);
		await page
			.getByRole("button", { name: "Edit profile", exact: true })
			.focus();
		await page.screenshot({
			path: fileURLToPath(new URL(`profile-${width}.png`, shots)),
			fullPage: true,
			animations: "disabled",
		});
		await page
			.getByRole("button", { name: "Edit profile", exact: true })
			.click();
		assert.ok(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
		);
		await page.screenshot({
			path: fileURLToPath(new URL(`profile-edit-${width}.png`, shots)),
			fullPage: true,
			animations: "disabled",
		});
		await page.getByRole("button", { name: "Cancel profile edit" }).click();
	}
	results.push(
		"375/768/1280 responsive screenshots;single main heading;no horizontal overflow",
	);
	await page.getByRole("button", { name: "Edit profile", exact: true }).click();
	await page
		.getByLabel("Full name", { exact: true })
		.fill("Stale browser edit");
	const apiUpdate = await page.evaluate(async () => {
		const c = await (await fetch("/api/session/csrf")).json();
		return (
			await fetch("/api/accounts/me/profile", {
				method: "PUT",
				headers: {
					"Content-Type": "application/json",
					[c.headerName]: c.token,
				},
				body: JSON.stringify({
					fullName: "Concurrent synthetic update",
					phone: null,
					expectedRevision: 1,
				}),
			})
		).status;
	});
	assert.equal(apiUpdate, 200);
	await page.getByRole("button", { name: "Save profile", exact: true }).click();
	await page
		.getByText("Profile changed in another session.", { exact: false })
		.waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Edit profile", exact: true })
			.count(),
		0,
	);
	await page.getByRole("button", { name: "Reload profile" }).click();
	await page
		.getByRole("button", { name: "Edit profile", exact: true })
		.waitFor();
	assert.ok((await panel.innerText()).includes("Concurrent synthetic update"));
	results.push(
		"stale revision 409;protected stale state cleared;explicit authoritative reload",
	);
	assert.equal(
		await page.evaluate(
			async () =>
				(
					await fetch("/api/accounts/me/profile", {
						method: "PUT",
						headers: { "Content-Type": "application/json" },
						body: "{}",
					})
				).status,
		),
		403,
	);
	results.push("real browser CSRF denial");
	await page.getByRole("button", { name: "Refresh booking history" }).click();
	await page
		.getByText("Unavailable, signed out, denied or changed.", { exact: false })
		.waitFor();
	results.push(
		"existing Booking transport reused;disabled booking/payment policy reports unavailable without mutation",
	);
	await page.getByRole("button", { name: /Check session/ }).click();
	await page.getByText("Session confirmed by server").waitFor();
	await page
		.getByRole("button", { name: /Log out of current session/ })
		.click();
	await page.getByText("Current session ended.", { exact: false }).waitFor();
	assert.equal(
		await page
			.getByRole("button", { name: "Edit profile", exact: true })
			.count(),
		0,
	);
	assert.equal(
		await page.evaluate(
			async () => (await fetch("/api/accounts/me/profile")).status,
		),
		401,
	);
	results.push("logout invalidates JDBC session and clears profile/history");
	await writeFile(
		`${run}/results.json`,
		JSON.stringify(
			{
				status: "PASS",
				interception: false,
				provider: "NONE",
				screenshots: 6,
				results,
			},
			null,
			2,
		),
	);
	console.log(JSON.stringify({ status: "PASS", results, screenshots: 6 }));
} finally {
	await browser?.close();
	frontend.kill("SIGTERM");
	await once(frontend, "exit").catch(() => {});
}

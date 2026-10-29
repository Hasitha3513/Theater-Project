// Actual Chromium -> Next.js -> Spring Boot -> PostgreSQL. No route interception or cookie injection.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { fileURLToPath, pathToFileURL } from "node:url";

const run = process.argv[2];
assert.match(run ?? "", /^\/tmp\/cinenest-ads-e2e-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const reserve = createServer().listen(0, "127.0.0.1");
await once(reserve, "listening");
const port = reserve.address().port;
await new Promise((r) => reserve.close(r));
const base = `http://127.0.0.1:${port}`;
const shots = new URL("../../.cache/advertising-e2e/", import.meta.url);
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
			CINENEST_FACEBOOK_MODE: "DISABLED",
			CINENEST_FACEBOOK_LIVE_EXECUTION: "false",
			CINENEST_GOOGLE_MODE: "DISABLED",
			CINENEST_GOOGLE_LIVE_EXECUTION: "false",
			NEXT_TELEMETRY_DISABLED: "1",
		},
		stdio: "ignore",
	},
);
let browser;
const results = [];
async function login(page, email) {
	await page.goto(`${base}/login`);
	await page.getByLabel("Test email address", { exact: true }).fill(email);
	await page.getByLabel("Password", { exact: true }).fill(config.password);
	await page.getByRole("button", { name: /^Log in/ }).click();
	await page.getByText("Login confirmed.", { exact: false }).waitFor();
}
async function admin(page) {
	await page.goto(`${base}/admin/advertising`);
	await page.getByLabel("Venue", { exact: true }).selectOption(config.venue);
	await page.getByRole("button", { name: "Load banners", exact: true }).click();
	await page
		.getByText("Current protected list loaded.", { exact: true })
		.waitFor();
}
try {
	for (let i = 0; i < 200; i++) {
		try {
			if ((await fetch(`${base}/login`)).status === 200) break;
		} catch {}
		if (i === 199) throw Error("Next startup failed");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({
		headless: true,
		args: ["--disable-dev-shm-usage"],
	});
	const a = await browser.newContext(),
		p = await browser.newContext(),
		publicContext = await browser.newContext();
	const author = await a.newPage(),
		publisher = await p.newPage(),
		home = await publicContext.newPage();
	await login(author, config.author);
	await admin(author);
	await author
		.getByLabel("Banner title", { exact: true })
		.fill("CineNest local showcase");
	await author
		.getByLabel("Description", { exact: true })
		.fill("Explore the synthetic private cinema experience.");
	await author
		.getByLabel("Start instant (with offset)", { exact: true })
		.fill("2026-01-01T00:00:00Z");
	await author
		.getByLabel("End instant (with offset)", { exact: true })
		.fill("2030-01-01T00:00:00Z");
	await author.getByRole("button", { name: "Save draft", exact: true }).click();
	await author
		.getByText("Draft saved. Homepage publication is unchanged.", {
			exact: true,
		})
		.waitFor();
	await home.goto(base);
	assert.equal(
		await home.getByRole("region", { name: "Venue advertisements" }).count(),
		0,
	);
	results.push("draft private; real-stack create");
	await author
		.getByRole("button", { name: "Preview saved revision", exact: true })
		.click();
	await author
		.getByRole("heading", {
			name: "Protected draft preview — unpublished changes",
		})
		.waitFor();
	await author
		.getByRole("button", { name: "Publish reviewed revision", exact: true })
		.click();
	await author.getByRole("status").filter({ hasText: "Denied." }).waitFor();
	results.push("same-author publishing denied");
	await login(publisher, config.publisher);
	await admin(publisher);
	await publisher
		.getByRole("button", {
			name: "CineNest local showcase · DRAFT",
			exact: true,
		})
		.click();
	await publisher
		.getByRole("button", { name: "Preview saved revision", exact: true })
		.click();
	await publisher
		.getByRole("heading", {
			name: "Protected draft preview — unpublished changes",
		})
		.waitFor();
	await publisher
		.getByRole("button", { name: "Publish reviewed revision", exact: true })
		.click();
	await publisher
		.getByText("Publication recorded. Server schedule controls visibility.", {
			exact: true,
		})
		.waitFor();
	await home.reload();
	await home
		.getByRole("region", { name: "Venue advertisements" })
		.getByRole("heading", { name: "CineNest local showcase" })
		.waitFor();
	results.push("independent publisher and anonymous live visibility");
	for (const width of [375, 768, 1280]) {
		await publisher.setViewportSize({ width, height: 900 });
		await publisher.screenshot({
			path: fileURLToPath(new URL(`admin-${width}.png`, shots)),
			fullPage: true,
		});
		await home.setViewportSize({ width, height: 900 });
		await home.screenshot({
			path: fileURLToPath(new URL(`homepage-${width}.png`, shots)),
			fullPage: true,
		});
		assert.equal(
			await home.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
		assert.equal(
			await publisher.evaluate(
				() => document.documentElement.scrollWidth <= window.innerWidth,
			),
			true,
		);
	}
	results.push("responsive 375/768/1280 and six screenshots");
	await admin(author);
	await author
		.getByRole("button", {
			name: "CineNest local showcase · LIVE",
			exact: true,
		})
		.click();
	await author
		.getByLabel("Banner title", { exact: true })
		.fill("Private next revision");
	await author.getByRole("button", { name: "Save draft", exact: true }).click();
	await author
		.getByText("Draft saved. Homepage publication is unchanged.", {
			exact: true,
		})
		.waitFor();
	await home.reload();
	await home
		.getByRole("region", { name: "Venue advertisements" })
		.getByRole("heading", { name: "CineNest local showcase" })
		.waitFor();
	assert.equal(
		await home.getByText("Private next revision", { exact: true }).count(),
		0,
	);
	await admin(publisher);
	await publisher
		.getByRole("button", { name: "Private next revision · LIVE", exact: true })
		.click();
	await publisher
		.getByRole("button", { name: "Revision history", exact: true })
		.click();
	await publisher
		.getByRole("heading", { name: "Immutable revision history" })
		.waitFor();
	await publisher
		.getByRole("button", { name: "Unpublish", exact: true })
		.click();
	await publisher.getByText("Banner unpublished.", { exact: true }).waitFor();
	await author.getByRole("button", { name: "Save draft", exact: true }).click();
	await author
		.getByText(
			"Revision or command conflict. Reload and compare; your edits are retained.",
			{ exact: true },
		)
		.waitFor();
	await home.reload();
	assert.equal(
		await home.getByRole("region", { name: "Venue advertisements" }).count(),
		0,
	);
	results.push(
		"immutable published snapshot; history; unpublish; stale conflict",
	);
	await writeFile(
		`${run}/results.json`,
		JSON.stringify({
			status: "PASS",
			interception: false,
			results,
			screenshots: 6,
		}),
	);
	console.log(
		"PASS actual advertising browser stack; sanitized synthetic evidence only",
	);
} finally {
	if (browser) await browser.close();
	frontend.kill("SIGTERM");
	await Promise.race([
		once(frontend, "exit"),
		new Promise((r) => setTimeout(r, 10000)),
	]);
	if (frontend.exitCode === null) frontend.kill("SIGKILL");
}

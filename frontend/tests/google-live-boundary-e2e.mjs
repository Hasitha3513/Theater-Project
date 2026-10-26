// Actual local stack, offline provider registration fixture. No live Google execution.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";

const run = process.argv[2];
assert.match(run ?? "", /^\/tmp\/cinenest-google-live-boundary-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const base = "http://127.0.0.1:42137";
const server = spawn(
	process.execPath,
	[
		"node_modules/next/dist/bin/next",
		"start",
		"--hostname",
		"127.0.0.1",
		"--port",
		"42137",
	],
	{
		cwd: new URL("..", import.meta.url),
		env: {
			...process.env,
			CINENEST_AUTH_ORIGIN: config.backend,
			CINENEST_GOOGLE_MODE: "LIVE_QUALIFICATION",
			CINENEST_GOOGLE_LIVE_EXECUTION: "false",
			NEXT_TELEMETRY_DISABLED: "1",
		},
		stdio: "ignore",
	},
);
let browser;
try {
	for (let i = 0; i < 100; i++) {
		if (server.exitCode !== null)
			throw Error("Fixed qualification origin unavailable");
		try {
			if ((await fetch(`${base}/login`)).status === 200) break;
		} catch {}
		if (i === 99) throw Error("Next startup");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch();
	const page = await browser.newPage();
	let external = 0;
	page.on("request", (r) => {
		if (!new URL(r.url()).hostname.match(/^(127\.0\.0\.1|localhost)$/))
			external++;
	});
	const shots = new URL(
		"../../.cache/google-live-boundary-e2e/",
		import.meta.url,
	);
	await mkdir(shots, { recursive: true, mode: 0o700 });
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 950 });
		await page.goto(`${base}/login`);
		await page
			.getByText(/Google adapter configured; live execution remains disabled/)
			.waitFor();
		assert.equal(
			await page
				.getByRole("button", { name: "Google sign-in unavailable" })
				.isDisabled(),
			true,
		);
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
	for (const route of [
		"/api/auth/google/start",
		"/api/auth/google/callback?code=fixture&state=fixture",
	]) {
		const response = await page.request.get(base + route);
		assert.equal(response.status(), 503);
	}
	await page.reload();
	await page
		.getByText(/Google adapter configured; live execution remains disabled/)
		.waitFor();
	assert.equal(external, 0);
	await writeFile(
		`${run}/results.json`,
		JSON.stringify(
			{
				status: "PASS",
				provider: "OFFLINE_GOOGLE_REGISTRATION_FIXTURE",
				callback: `${base}/api/auth/google/callback`,
				groups: [
					"three responsive configured/unqualified disabled controls",
					"start/callback default DENY through actual Next/backend stack",
					"refresh retains blocked state;zero external browser requests",
				],
				liveGoogleExecuted: false,
			},
			null,
			2,
		),
	);
} finally {
	if (browser) await browser.close();
	server.kill("SIGTERM");
	await once(server, "exit").catch(() => {});
}

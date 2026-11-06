// Actual Chromium -> Next.js -> Spring Boot -> PostgreSQL; no interception or cookie injection.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { pathToFileURL, fileURLToPath } from "node:url";

export async function runAuthorizedRoomBlocking({
	page,
	base,
	manifest,
	evidenceDir,
}) {
	assert(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
	assert.match(
		evidenceDir,
		/^\/tmp\/(?:cinenest-room-block-e2e-|cn-part1-preview-)[^/]+$/,
	);
	const config = {
		...manifest,
		email: manifest.email ?? "creator-commercial@example.test",
		password: manifest.password ?? process.env.CINENEST_SYNTHETIC_PASSWORD,
		venue: manifest.venue ?? manifest.fixtureIds?.venue,
		room: manifest.room ?? manifest.fixtureIds?.room,
	};
	let stage = "STARTUP",
		externalRequests = 0;
	const checks = [],
		responses = [],
		dimensions = [];
	let blockReceipt, availabilityEvidence;
	const responseListener = (r) => {
		const u = new URL(r.url());
		if (
			u.pathname.startsWith("/api/") &&
			(u.pathname.includes("/blocks") || u.pathname.includes("/availability"))
		)
			responses.push({
				stage,
				path: u.pathname,
				method: r.request().method(),
				status: r.status(),
			});
	};
	const requestListener = (r) => {
		const u = new URL(r.url());
		if (
			["http:", "https:"].includes(u.protocol) &&
			!["127.0.0.1", "localhost"].includes(u.hostname)
		)
			externalRequests++;
	};
	page.on("response", responseListener);
	page.on("request", requestListener);
	try {
		stage = "MANUAL_LOGIN";
		await page.goto(`${base}/login`);
		await page
			.getByLabel("Test email address", { exact: true })
			.fill(config.email);
		await page.getByLabel("Password", { exact: true }).fill(config.password);
		await page.getByRole("button", { name: /^Log in/ }).click();
		await page.getByText("Login confirmed.", { exact: false }).waitFor();
		checks.push("manual-login-through-existing-JDBC-session");
		stage = "ADMIN_CONTEXT";
		await page.goto(`${base}/admin/room-blocking`);
		await page
			.getByRole("button", { name: "Find venues by name", exact: true })
			.click();
		await page.getByLabel("Venue", { exact: true }).selectOption(config.venue);
		await page.getByRole("button", { name: "Find rooms", exact: true }).click();
		await page.getByLabel("Room", { exact: true }).selectOption(config.room);
		assert.equal(
			await page.getByLabel("Venue", { exact: true }).inputValue(),
			config.venue,
		);
		assert.equal(
			await page.getByLabel("Room", { exact: true }).inputValue(),
			config.room,
		);
		await page.getByText("Selected:", { exact: false }).waitFor();
		await page
			.getByRole("button", { name: "Load room blocks", exact: true })
			.click();
		await page
			.getByText("Current inventory context loaded.", { exact: false })
			.waitFor();
		await page.getByText("Sri Lanka time", { exact: false }).waitFor();
		stage = "CREATE";
		await page.getByLabel("Start date", { exact: true }).fill("2030-02-01");
		await page
			.getByLabel("Start time (24-hour)", { exact: true })
			.fill("10:00");
		await page.getByLabel("End date", { exact: true }).fill("2030-02-01");
		await page.getByLabel("End time (24-hour)", { exact: true }).fill("12:00");
		await page.getByText("Review before saving:", { exact: false }).waitFor();
		assert.match(
			await page
				.getByText("Review before saving:", { exact: false })
				.textContent(),
			/1 February 2030.*10:00:00 GMT\+05:30.*12:00:00 GMT\+05:30/,
		);
		const blockResponse = () =>
			page.waitForResponse(
				(r) =>
					new URL(r.url()).pathname ===
						`/api/admin/venues/${config.venue}/rooms/${config.room}/blocks` &&
					r.request().method() === "POST",
			);
		const [created] = await Promise.all([
			blockResponse(),
			page
				.getByRole("button", { name: "Create room block", exact: true })
				.click(),
		]);
		assert.equal(created.status(), 200);
		blockReceipt = await created.json();
		await page.getByText("Room block saved.", { exact: false }).waitFor();
		checks.push("create-offset-interval-with-CSRF-and-current-configuration");
		stage = "READ";
		await page
			.getByRole("button", { name: "Load room blocks", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Inspect block", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Saved block detail", exact: true })
			.waitFor();
		const detail = page
			.getByRole("heading", { name: "Saved block detail", exact: true })
			.locator("..");
		assert.match(
			await detail.textContent(),
			/1 February 2030.*10:00:00 GMT\+05:30/,
		);
		assert.match(await detail.textContent(), /Revision 0/);
		await detail
			.getByText("Exact original timestamps", { exact: true })
			.click();
		assert.match(await detail.textContent(), /2030-02-01T04:30:00(?:\.000)?Z/);
		checks.push("authoritative-list-and-protected-detail");
		stage = "CALENDAR";
		const calendar = await page.evaluate(async (room) => {
			const r = await fetch(
				`/api/rooms/${room}/availability/calendar?date=2030-02-01&durationMinutes=180&guests=1`,
				{ cache: "no-store" },
			);
			if (r.status !== 200) return null;
			return r.json();
		}, config.room);
		availabilityEvidence = calendar?.items.find(
			(x) => x.startLocal === "2030-02-01T10:00",
		);
		assert.equal(availabilityEvidence?.available, false);
		assert(availabilityEvidence.reasons.includes("MAINTENANCE_BLOCK"));
		checks.push("customer-calendar-rejects-maintenance-overlap");
		stage = "OVERLAP";
		const [conflict] = await Promise.all([
			blockResponse(),
			page
				.getByRole("button", { name: "Create room block", exact: true })
				.click(),
		]);
		assert.equal(conflict.status(), 409);
		await page
			.getByText(
				/overlaps existing inventory|Inventory conflicts or its configuration changed/,
			)
			.waitFor();
		checks.push("overlap-rejected-without-cancellation");
		stage = "SCOPE_DENIAL";
		await page
			.getByText("Use exact venue and room references instead", { exact: true })
			.click();
		await page
			.getByLabel("Venue reference (UUID)", { exact: true })
			.fill("00000000-0000-4000-8000-000000000001");
		await page
			.getByLabel("Room reference (UUID)", { exact: true })
			.fill(config.room);
		const [scopeDenied] = await Promise.all([
			page.waitForResponse(
				(r) =>
					new URL(r.url()).pathname ===
						`/api/admin/venues/00000000-0000-4000-8000-000000000001/rooms/${config.room}/blocks` &&
					r.request().method() === "GET",
			),
			page
				.getByRole("button", { name: "Load room blocks", exact: true })
				.click(),
		]);
		assert.equal(scopeDenied.status(), 403);
		await page.getByText("Access denied.", { exact: false }).waitFor();
		assert.equal(
			await page
				.getByRole("heading", { name: "Saved block detail", exact: true })
				.count(),
			0,
		);
		checks.push("wrong-venue-denied-and-protected-state-cleared");
		await page
			.getByLabel("Venue reference (UUID)", { exact: true })
			.fill(config.venue);
		await page
			.getByLabel("Room reference (UUID)", { exact: true })
			.fill(config.room);
		await page
			.getByRole("button", { name: "Load room blocks", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Inspect block", exact: true })
			.click();
		await page
			.getByRole("heading", { name: "Saved block detail", exact: true })
			.waitFor();
		await page.waitForFunction(() =>
			[...document.querySelectorAll("button")].some(
				(b) =>
					b.querySelector(".button-label")?.textContent === "Inspect block" &&
					!b.disabled,
			),
		);
		stage = "KEYBOARD";
		await page
			.getByRole("button", { name: "Load room blocks", exact: true })
			.focus();
		await page.keyboard.press("Tab");
		assert(
			await page.evaluate(() => {
				const e = document.activeElement,
					c = getComputedStyle(e),
					r = e.getBoundingClientRect();
				return (
					e !== document.body &&
					r.width > 0 &&
					r.height > 0 &&
					(c.outlineStyle !== "none" || c.boxShadow !== "none")
				);
			}),
		);
		checks.push("keyboard-visible-focus");
		stage = "RESPONSIVE";
		for (const width of [375, 768, 1280]) {
			await page.setViewportSize({ width, height: 1000 });
			const d = await page.evaluate(() => ({
				viewport: innerWidth,
				page: document.documentElement.scrollWidth,
			}));
			dimensions.push(d);
			assert(d.page <= d.viewport);
			await page.screenshot({
				path: fileURLToPath(
					new URL(
						`room-blocking-${width}.png`,
						pathToFileURL(evidenceDir + "/"),
					),
				),
				fullPage: true,
			});
			checks.push(`responsive-${width}`);
		}
		stage = "LOGOUT";
		await page.goto(`${base}/account`);
		await page.getByRole("button", { name: /Check session/ }).click();
		await page.getByText("Session confirmed by server").waitFor();
		await page
			.getByRole("button", { name: /Log out of current session/ })
			.click();
		await page.getByText("Current session ended.", { exact: false }).waitFor();
		const denied = await page.evaluate(
			async (path) => (await fetch(path, { cache: "no-store" })).status,
			`/api/admin/venues/${config.venue}/rooms/${config.room}/blocks`,
		);
		assert.equal(denied, 401);
		checks.push("logout-revokes-protected-room-block-access");
		assert.equal(externalRequests, 0);
		const result = {
			status: "PASS",
			classification: "AUTOMATED_TECHNICAL_NOT_OWNER_UAT",
			checks,
			responses,
			dimensions,
			blockReceipt,
			availabilityEvidence,
			externalRequests,
			interception: false,
			cookieInjection: false,
		};
		await writeFile(
			`${evidenceDir}/room-blocks-results.json`,
			JSON.stringify(result, null, 2),
			{ mode: 0o600 },
		);
		return result;
	} catch (error) {
		await writeFile(
			`${evidenceDir}/room-blocks-results.json`,
			JSON.stringify({
				status: "FAIL",
				stage,
				checks,
				responses,
				dimensions,
				externalRequests,
				interception: false,
				cookieInjection: false,
			}),
			{ mode: 0o600 },
		);
		const diagnostic = String(error.message).replaceAll(
			config.password ?? "__NO_SECRET__",
			"[REDACTED]",
		);
		throw new Error(`Room Blocking browser failed at ${stage}: ${diagnostic}`);
	} finally {
		page.off("response", responseListener);
		page.off("request", requestListener);
	}
}

async function legacyMain() {
	const run = process.argv[2];
	assert.match(run ?? "", /^\/tmp\/cinenest-room-block-e2e-[^/]+$/);
	const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
	const { chromium } = await import(
		pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
	);
	const reserve = createServer().listen(0, "127.0.0.1");
	await once(reserve, "listening");
	const port = reserve.address().port;
	await new Promise((r) => reserve.close(r));
	const base = `http://127.0.0.1:${port}`;
	await mkdir(run, { recursive: true, mode: 0o700 });
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
				PATH: process.env.PATH,
				NEXT_TELEMETRY_DISABLED: "1",
				CINENEST_AUTH_ORIGIN: config.backend,
				CINENEST_FACEBOOK_MODE: "DISABLED",
				CINENEST_FACEBOOK_LIVE_EXECUTION: "false",
				CINENEST_GOOGLE_MODE: "DISABLED",
				CINENEST_GOOGLE_LIVE_EXECUTION: "false",
			},
			stdio: "ignore",
		},
	);
	let browser;
	try {
		for (let i = 0; i < 150; i++) {
			try {
				if ((await fetch(`${base}/login`)).status === 200) break;
			} catch {}
			if (i === 149) throw Error("Frontend unavailable");
			await new Promise((r) => setTimeout(r, 100));
		}
		browser = await chromium.launch({
			headless: true,
			args: ["--disable-dev-shm-usage"],
		});
		const context = await browser.newContext();
		const page = await context.newPage();
		const result = await runAuthorizedRoomBlocking({
			page,
			base,
			manifest: config,
			evidenceDir: run,
		});
		await writeFile(`${run}/results.json`, JSON.stringify(result), {
			mode: 0o600,
		});
	} catch (error) {
		process.exitCode = 1;
		console.error(error.message);
	} finally {
		await browser?.close();
		frontend.kill("SIGTERM");
		await Promise.race([
			once(frontend, "exit"),
			new Promise((r) => setTimeout(r, 10000)),
		]);
		if (frontend.exitCode === null) frontend.kill("SIGKILL");
	}
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
	await legacyMain();

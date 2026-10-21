// SYNTHETIC intercepted browser rehearsal; PostgreSQL/API claims are checked separately.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { pathToFileURL } from "node:url";

const { mkdir } = await import("node:fs/promises");
await mkdir("../.cache/customer-calendar", { recursive: true });
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
child.stdout.resume();
child.stderr.resume();
const id = "00000000-0000-4000-8000-000000000001";
const room = {
	roomId: id,
	venueId: id,
	name: "SYNTHETIC inventory room",
	code: "SYNTHETIC",
	description: "HDMI input",
	capacity: 5,
	facilities: [],
	images: [],
	venue: {
		displayName: "Synthetic venue",
		timezone: "Asia/Colombo",
		windows: [],
	},
};
let browser,
	mode = "normal",
	checks = 0;
const commands = [],
	traffic = [];
function pass(label) {
	checks++;
	console.log(`PASS ${label}`);
}
try {
	for (let i = 0; i < 100; i++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	const page = await browser.newPage({
		viewport: { width: 1280, height: 1000 },
	});
	await page.route("**/api/**", async (route) => {
		const req = route.request(),
			p = new URL(req.url()).pathname;
		traffic.push({ path: p, method: req.method() });
		let status = 200,
			body = {};
		if (p.startsWith("/api/catalog/rooms/"))
			body = { ...room, roomId: p.split("/").at(-1) };
		else if (p.endsWith("/packages"))
			body = {
				items: [
					{
						packageId: id,
						versionId: id,
						name: "SYNTHETIC package",
						description: "SYNTHETIC",
						basePriceMinor: "250000",
						includedMinutes: 180,
						includedGuests: 4,
						options: [],
					},
				],
				purchasable: false,
			};
		else if (p.endsWith("/availability/calendar")) {
			const query = new URL(req.url()).searchParams,
				date = query.get("date");
			body = {
				roomId: id,
				venueId: id,
				date,
				timezone: "Asia/Colombo",
				durationMinutes: Number(query.get("durationMinutes")),
				guests: Number(query.get("guests")),
				configuration: "SYNTHETIC",
				observedAt: "2027-01-01T00:00:00Z",
				evidenceLevel: "INVENTORY_ONLY",
				complete: true,
				items: Array.from({ length: 1440 }, (_, m) => ({
					startLocal: `${date}T${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`,
					sessionStart: m === 600 ? `${date}T04:30:00Z` : null,
					sessionEnd: m === 600 ? `${date}T07:30:00Z` : null,
					blockedUntil: m === 600 ? `${date}T07:45:00Z` : null,
					available:
						mode === "empty"
							? false
							: m === 600
								? true
								: m === 601
									? null
									: false,
					reasons:
						m === 600 && mode !== "empty"
							? []
							: m === 601 && mode !== "empty"
								? ["PENDING_RECONCILIATION"]
								: ["OUTSIDE_OPERATING_HOURS"],
				})),
			};
			if (mode === "slow") await new Promise((r) => setTimeout(r, 800));
			if (mode === "empty-response") body = { ...body, items: [] };
			if (mode === "malformed") body = { ...body, roomId: "bad" };
			if (["session", "denied", "unavailable"].includes(mode)) {
				status = { session: 401, denied: 403, unavailable: 503 }[mode];
				body = { message: "Sanitized synthetic error" };
			}
			if (mode === "network") return route.abort();
		} else if (p === "/api/accounts/me")
			body = {
				accountId: id,
				synthetic: true,
				eligibility: "ELIGIBLE",
				scopes: [],
			};
		else status = 404;
		await route
			.fulfill({
				status,
				contentType: "application/json",
				body: JSON.stringify(body),
			})
			.catch(() => {});
	});
	await page.goto(`${base}/rooms/${id}`);
	await page
		.getByRole("heading", { name: "Find a session time", exact: true })
		.waitFor();
	await page.getByLabel("Calendar date (venue local)").fill("2027-02-01");
	await page.getByLabel("Calendar duration (minutes)").fill("180");
	await page.getByLabel("Calendar guests", { exact: true }).fill("4");
	const load = page.getByRole("button", {
		name: "Load availability calendar",
		exact: true,
	});
	await load.click();
	await page
		.getByText("1 available · 1438 unavailable · 1 uncertain", { exact: true })
		.waitFor();
	assert.equal(commands.length, 0);
	pass("server tri-state calendar; no automatic hold");
	await page
		.getByLabel("Session start (Asia/Colombo)")
		.selectOption("2027-02-01T10:00");
	await page
		.getByRole("button", { name: "Use selected time", exact: true })
		.click();
	assert.equal(
		await page.getByLabel("Customer start in venue local time").inputValue(),
		"2027-02-01T10:00",
	);
	assert.equal(commands.length, 0);
	pass("explicit selection fills existing form, no reservation");
	const measures = [];
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 1000 });
        await page.getByRole("button", {name:"Use selected time",exact:true}).focus();
        await page.evaluate(() => window.scrollTo(0,0));
        await page.waitForTimeout(100);
		await page.screenshot({
			path: `../.cache/customer-calendar/after-${width}.png`,
			fullPage: true,
		});
		await page
			.locator(".customer-calendar")
			.screenshot({ path: `../.cache/customer-calendar/panel-${width}.png` });
		const m = await page.evaluate(() => ({
			width: innerWidth,
			scroll: document.documentElement.scrollWidth,
			main: document.querySelectorAll("main").length,
			height: document.documentElement.scrollHeight,
		}));
		assert.equal(m.width, m.scroll);
		assert.equal(m.main, 1);
		measures.push(m);
	}
	console.log("MEASUREMENTS", JSON.stringify(measures));
	pass("responsive single main/no overflow at375/768/1280");
	await page.getByLabel("Calendar guests", { exact: true }).fill("3");
	assert.equal(
		await page.getByLabel("Customer start in venue local time").inputValue(),
		"",
	);
	assert.equal(
		await page.getByLabel("Session start (Asia/Colombo)").count(),
		0,
	);
	pass("guest change clears selection");
	mode = "slow";
	await load.click();
	await page.getByLabel("Calendar date (venue local)").fill("2027-02-02");
	await page.waitForTimeout(1000);
	assert.equal(
		await page.getByLabel("Session start (Asia/Colombo)").count(),
		0,
	);
	pass("stale asynchronous response discarded");
	mode = "empty";
	await load.click();
	await page
		.getByText("No available starts for this request.", { exact: true })
		.waitFor();
	mode = "empty-response";
	await load.click();
	await page
		.getByText("0 available · 0 unavailable · 0 uncertain", { exact: true })
		.waitFor();
	pass("empty/unavailable day fails closed");
	for (const [value, title] of [
		["session", "Sign-in required"],
		["denied", "Access denied"],
		["unavailable", "Service unavailable"],
		["network", "Service unavailable"],
		["malformed", "Request not completed"],
	]) {
		mode = value;
		await load.click();
		await page.getByRole("heading", { name: title, exact: true }).waitFor();
		assert.equal(
			await page.getByLabel("Session start (Asia/Colombo)").count(),
			0,
		);
	}
	pass("session/denied/network/backend/malformed clear results");
	mode = "normal";
	await page
		.getByRole("button", { name: "Retry calendar", exact: true })
		.focus();
	await page.keyboard.press("Enter");
	await page.getByLabel("Session start (Asia/Colombo)").waitFor();
	const next = page.getByRole("button", {
		name: "Next calendar day",
		exact: true,
	});
	await next.focus();
	assert.equal(
		await next.evaluate((e) => getComputedStyle(e).outlineStyle),
		"solid",
	);
	await page.keyboard.press("Enter");
	assert.equal(
		await page.getByLabel("Calendar date (venue local)").inputValue(),
		"2027-02-03",
	);
	assert.equal(
		await page.getByLabel("Session start (Asia/Colombo)").count(),
		0,
	);
	pass("keyboard date navigation,visible focus,retry");
	await load.click();
	await page.getByLabel("Session start (Asia/Colombo)").waitFor();
	await page.evaluate(() =>
		window.dispatchEvent(new Event("cinenest-session-change")),
	);
	assert.equal(
		await page.getByLabel("Session start (Asia/Colombo)").count(),
		0,
	);
	pass("session event clears protected state");
	await load.click();
	await page.getByLabel("Session start (Asia/Colombo)").waitFor();
	await page.goto(`${base}/rooms/00000000-0000-4000-8000-000000000002`);
	await page
		.getByRole("heading", { name: "Find a session time", exact: true })
		.waitFor();
	assert.equal(
		await page.getByLabel("Session start (Asia/Colombo)").count(),
		0,
	);
	pass("room route change clears calendar");
	assert.equal(traffic.filter((x) => x.method !== "GET").length, 0);
	assert.equal(new URL(page.url()).search, "");
	pass("zero mutations,no sensitive URLs");
	console.log(
		`PASS ${checks} intercepted calendar browser groups;not real backend E2E`,
	);
} finally {
	await browser?.close();
	const ended = once(child, "exit");
	child.kill("SIGTERM");
	await ended;
}

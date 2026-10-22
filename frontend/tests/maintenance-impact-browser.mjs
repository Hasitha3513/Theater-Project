// Intercepted presentation tests only; not real-backend E2E or authorization proof.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { context, fixture, account } from "./maintenance-impact-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer().listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
	child = spawn(
		process.execPath,
		[
			"node_modules/next/dist/bin/next",
			"start",
			"--hostname",
			"127.0.0.1",
			"--port",
			String(port),
		],
		{ stdio: "ignore" },
	);
let browser;
let groups = 0;
const observations = [];
try {
	for (let n = 0; n < 150; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		if (n === 149) throw Error("Server timeout");
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch();
	await mkdir("../.cache/maintenance-impact/after", { recursive: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			waiting,
			reads = 0;
		const requests = [];
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			requests.push({ method: req.method(), path: url.pathname });
			assert.equal(req.method(), "GET");
			if (url.pathname === "/api/accounts/me")
				return route.fulfill({
					status: mode === "SESSION" ? 401 : 200,
					json: { accountId: account, eligibility: "ELIGIBLE" },
				});
			if (!url.pathname.endsWith("/maintenance-impact"))
				return route.fulfill({ status: 403, body: "" });
			reads++;
			assert.equal(url.searchParams.get("startLocal"), context.startLocal);
			assert.equal(url.searchParams.get("endLocal"), context.endLocal);
			if (mode === "DELAY")
				await new Promise((r) => {
					waiting = r;
				});
			if (/^\d+$/.test(mode))
				return route
					.fulfill({ status: Number(mode), body: "PRIVATE_ERROR" })
					.catch(() => {});
			const data = fixture();
			if (mode === "ZERO")
				for (const k of Object.keys(data.counts)) data.counts[k] = 0;
			if (mode === "INCOMPLETE") {
				data.complete = false;
				data.counts = null;
				data.incompleteReason = "RECORD_LIMIT";
			}
			if (mode === "WRONG") data.roomId = account;
			data.privateEvidence = "PRIVATE_SECRET";
			return route.fulfill({ json: data }).catch(() => {});
		});
		await page.goto(`${base}/admin/operations`);
		const panel = page.locator(".maintenance-impact");
		await panel.waitFor();
		assert.equal(reads, 0);
		groups++;
		const fill = async () => {
			for (const [id, key] of [
				["venue", "venue"],
				["room", "room"],
				["start", "startLocal"],
				["end", "endLocal"],
			])
				await page.locator(`#impact-${id}`).fill(context[key]);
		};
		const load = async () => {
			await panel
				.getByRole("button", { name: "Load Preview", exact: true })
				.click();
		};
		const loaded = async () => {
			await panel.locator(".impact-result").waitFor();
		};
		await fill();
		assert.equal(reads, 0);
		await load();
		await loaded();
		assert.equal(await panel.locator(".impact-counts > div").count(), 5);
		assert.equal(await page.locator("main").count(), 1);
		assert.equal(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth,
			),
			true,
		);
		assert.doesNotMatch(
			await panel.innerText(),
			/PRIVATE_SECRET|safeToEnter|canClose/,
		);
		await page.screenshot({
			path: `../.cache/maintenance-impact/after/${width}.png`,
			fullPage: true,
		});
		await panel.screenshot({path:`../.cache/maintenance-impact/after/panel-${width}.png`});
        const inputColumns=await panel.locator('.filter-grid').evaluate(e=>getComputedStyle(e).gridTemplateColumns.split(' ').length);
        assert.equal(inputColumns,width===375?1:2);
        groups++;
        mode = "INCOMPLETE";
		await load();
		await loaded();
		assert.equal(await panel.locator(".impact-counts").count(), 0);
		assert.match(await panel.innerText(), /Conflict counts are unavailable/);
		await page.screenshot({
			path: `../.cache/maintenance-impact/after/incomplete-${width}.png`,
			fullPage: true,
		});
		groups++;
		mode = "ZERO";
		await load();
		await panel
			.getByText(
				"No overlapping blocking inventory was observed for this interval.",
				{ exact: true },
			)
			.waitFor();
		groups++;
		for (const code of [400, 401, 403, 404, 409, 503]) {
			mode = String(code);
			await load();
			await page.waitForFunction(
				() =>
					!document.querySelector(
						'.maintenance-impact button[aria-busy="true"]',
					),
			);
			assert.equal(await panel.locator(".impact-result").count(), 0);
			assert.doesNotMatch(await panel.innerText(), /PRIVATE_ERROR/);
			assert.equal(
				await panel
					.getByRole("button", { name: "Retry", exact: true })
					.isEnabled(),
				true,
			);
			groups++;
		}
		mode = "OK";
		await panel.getByRole("button", { name: "Retry", exact: true }).click();
		await loaded();
		groups++;
		for (const id of ["venue", "room", "start", "end"]) {
			await page
				.locator(`#impact-${id}`)
				.fill(id === "venue" || id === "room" ? account : "2026-10-22T11:00");
			assert.equal(await panel.locator(".impact-result").count(), 0);
			await fill();
			await load();
			await loaded();
			groups++;
		}
		mode = "DELAY";
		waiting = undefined;
		await load();
		for (let n = 0; !waiting && n < 100; n++) await page.waitForTimeout(10);
		assert.ok(waiting);
		await page.locator("#impact-room").fill(account);
		waiting();
		await page.waitForTimeout(80);
		assert.equal(await panel.locator(".impact-result").count(), 0);
		groups++;
		mode = "OK";
		await fill();
		await load();
		await loaded();
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(await panel.locator(".impact-result").count(), 0);
		groups++;
		mode = "WRONG";
		await load();
		await panel
			.getByText("Preview unavailable or response invalid.", { exact: false })
			.waitFor();
		assert.equal(await panel.locator(".impact-result").count(), 0);
		groups++;
		mode = "SESSION";
		const before = reads;
		await load();
		await panel
			.getByText("Session expired or unavailable.", { exact: false })
			.waitFor();
		assert.equal(reads, before);
		groups++;
		mode = "OK";
		await load();
		await loaded();
		await page.evaluate(() =>
			window.dispatchEvent(new PopStateEvent("popstate")),
		);
		assert.equal(await panel.locator(".impact-result").count(), 0);
		groups++;
		await page.locator("#impact-venue").focus();
		await page.keyboard.press("Tab");
		assert.equal(
			await page
				.locator("#impact-room")
				.evaluate((e) => e === document.activeElement),
			true,
		);
		const focus = await page
			.locator("#impact-room")
			.evaluate((e) => getComputedStyle(e).outlineStyle);
		assert.notEqual(focus, "none");
		groups++;
		await panel.getByRole("button", { name: "Clear", exact: true }).click();
		assert.equal(await page.locator("#impact-venue").inputValue(), "");
		assert.equal(await panel.locator(".impact-result").count(), 0);
		assert.ok(requests.every((r) => r.method === "GET"));
		observations.push({
			width,
			groups,
			reads,
			main: 1,
			overflow: false,
			focus,
		});
		await page.close();
	}
	await writeFile(
		"../.cache/maintenance-impact/after/results.json",
		JSON.stringify({ groups, observations }, null, 2),
	);
	console.log(`PASS ${groups} intercepted browser groups; all widths`);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit").catch(() => {});
}

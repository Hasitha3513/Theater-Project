// Intercepted presentation evidence only; real authority remains backend/PostgreSQL evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import {
	venue,
	account,
	other,
	cursor,
	fixture,
} from "./managed-grants-fixtures.mjs";
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
let browser,
	groups = 0;
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
	await mkdir("../.cache/grant-inspection/after", { recursive: true });
	for (const width of [375, 768, 1280]) {
		const page = await browser.newPage({ viewport: { width, height: 1000 } });
		let mode = "OK",
			session = venue,
			waiting,
			apiCount = 0;
		await page.route("**/api/**", async (route) => {
			const req = route.request(),
				url = new URL(req.url());
			assert.equal(req.method(), "GET");
			if (url.pathname === "/api/accounts/me")
				return route.fulfill({
					status: mode === "SESSION" ? 401 : 200,
					json: { accountId: session, eligibility: "ELIGIBLE" },
				});
			if (!url.pathname.endsWith("/grants"))
				return route.fulfill({ status: 403, json: {} });
			apiCount++;
			if (mode === "DELAY")
				await new Promise((r) => {
					waiting = r;
				});
			if (/^\d+$/.test(mode))
				return route
					.fulfill({ status: Number(mode), body: "PRIVATE_ERROR" })
					.catch(() => {});
			const data = fixture();
			data.venueId = url.pathname.split("/")[4];
			data.accountId = url.pathname.split("/")[6];
			data.items[0].venueId = data.venueId;
			if (mode === "EMPTY") data.items = [];
			if (mode === "REVOKED") {
				data.items[0].storedState = "REVOKED";
				data.items[0].overlapsAnotherActiveGrant = false;
				data.items[0].effectiveAtObservation = false;
			}
			if (mode === "EXPIRED") {
				data.items[0].validUntil = "2026-10-22T07:30:00Z";
				data.items[0].effectiveAtObservation = false;
			}
			if (mode === "SUSPENDED") {
				data.targetEligibility = "SUSPENDED";
				data.items[0].anotherEffectiveGrant = false;
				data.items[0].effectiveAtObservation = false;
			}
			if (mode === "BAD") data.accountId = "bad";
			if (mode === "PAGE" && !url.searchParams.has("cursor")) {
				data.items = Array.from({ length: 20 }, (_, i) => ({
					...data.items[0],
					grantId: `00000000-0000-4000-8000-${String(i + 10).padStart(12, "0")}`,
				}));
				data.hasNext = true;
				data.nextCursor = cursor;
			}
			if (mode === "PAGE" && url.searchParams.has("cursor")) {
				assert.equal(url.searchParams.get("cursor"), cursor);
				data.items[0].grantId = "00000000-0000-4000-8000-000000000099";
			}
			if (mode === "SWITCH") session = other;
			data.private = "PRIVATE";
			if (data.items[0]) data.items[0].evidence = "PRIVATE";
			await route.fulfill({ json: data }).catch(() => {});
		});
		await page.goto(`${base}/admin/accounts`);
		const panel = page.getByRole("region", {
				name: "Managed Grant Inspection",
				exact: true,
			}),
			status = panel.getByRole("status");
		await panel.waitFor();
		assert.equal(apiCount, 0);
		assert.equal(await page.locator("main").count(), 1);
		groups++;
		const v = panel.getByLabel("Venue reference", { exact: true }),
			a = panel.getByLabel("Account reference", { exact: true });
		await v.fill(venue);
		await a.fill(account);
		const load = () =>
				panel
					.getByRole("button", { name: "Load managed grants", exact: true })
					.click(),
			reload = () =>
				panel
					.getByRole("button", { name: "Reload first page", exact: true })
					.click(),
			result = () =>
				panel.getByRole("heading", {
					name: "Current observation",
					exact: true,
				}),
			visible = () => result().waitFor();
		await panel
			.getByRole("button", { name: "Load managed grants", exact: true })
			.focus();
		assert(
			await page.evaluate(
				() => getComputedStyle(document.activeElement).outlineStyle !== "none",
			),
		);
		await page.keyboard.press("Enter");
		await visible();
		assert(!(await panel.innerText()).includes("PRIVATE"));
		assert(!page.url().includes(cursor));
		groups++;
		await page.evaluate(() => window.scrollTo(0, 0));
		await page.screenshot({
			path: `../.cache/grant-inspection/after/accounts-${width}.png`,
			animations: "disabled",
		});
		await result().scrollIntoViewIfNeeded();
		await page.screenshot({
			path: `../.cache/grant-inspection/after/results-${width}.png`,
			animations: "disabled",
		});
		await panel
			.locator(width < 768 ? ".grant-mobile article" : ".grant-desktop")
			.first()
			.scrollIntoViewIfNeeded();
		await page.screenshot({
			path: `../.cache/grant-inspection/after/grants-${width}.png`,
			animations: "disabled",
		});
		const layout = await page.evaluate(() => ({
			width: innerWidth,
			scroll: document.documentElement.scrollWidth,
			main: document.querySelectorAll("main").length,
			mobile: getComputedStyle(document.querySelector(".grant-mobile")).display,
			desktop: getComputedStyle(document.querySelector(".grant-desktop"))
				.display,
			card: getComputedStyle(
				document.querySelector(".managed-grant-inspection .card"),
			).backgroundColor,
		}));
		assert(layout.scroll <= width, JSON.stringify(layout));
		assert.equal(width < 768 ? layout.desktop : layout.mobile, "none");
		observations.push(layout);
		groups++;
		for (const state of ["EMPTY", "REVOKED", "EXPIRED", "SUSPENDED"]) {
			mode = state;
			await reload();
			await visible();
			assert(
				(await panel.innerText()).includes(
					state === "EMPTY"
						? "Other permissions may exist"
						: state === "EXPIRED"
							? "07:30:00"
							: state,
				),
			);
			groups++;
		}
		mode = "PAGE";
		await reload();
		await visible();
		const next = panel.getByRole("button", { name: "Next page", exact: true });
		assert(await next.isEnabled());
		await next.click();
		await visible();
		assert((await panel.innerText()).includes("000000000099"));
		assert(!(await next.isEnabled()));
		groups++;
		for (const code of [401, 403, 404, 409, 400, 503]) {
			mode = String(code);
			await reload();
			await page.waitForFunction(
				() =>
					!document.querySelector(
						'.managed-grant-inspection [aria-busy="true"]',
					),
			);
			assert.equal(await result().count(), 0);
			assert(!(await panel.innerText()).includes("PRIVATE_ERROR"));
			assert(!(await next.isEnabled()));
			groups++;
		}
		mode = "BAD";
		await reload();
		await status.filter({ hasText: "response invalid" }).waitFor();
		groups++;
		mode = "OK";
		await reload();
		await visible();
		await v.fill(other);
		assert.equal(await result().count(), 0);
		await v.fill(venue);
		groups++;
		mode = "DELAY";
		await load();
		while (!waiting) await new Promise((r) => setTimeout(r, 10));
		await a.fill(other);
		waiting();
		await page.waitForTimeout(100);
		assert.equal(await result().count(), 0);
		await a.fill(account);
		groups++;
		mode = "OK";
		await page.evaluate(() => {
			const original = window.fetch;
			window.fetch = async (...args) => {
				const response = await original(...args);
				if (String(args[0]).includes("/grants")) {
					const json = response.json.bind(response);
					response.json = async () => {
						const data = await json();
						window.grantJsonWaiting = true;
						await new Promise((resolve) => {
							window.releaseGrantJson = resolve;
						});
						return data;
					};
				}
				return response;
			};
			window.restoreGrantFetch = () => {
				window.fetch = original;
			};
		});
		await load();
		await page.waitForFunction(() => window.grantJsonWaiting);
		await a.fill(other);
		await page.evaluate(() => {
			window.releaseGrantJson();
			window.restoreGrantFetch();
		});
		await page.waitForTimeout(100);
		assert.equal(await result().count(), 0);
		await a.fill(account);
		groups++;
		mode = "SWITCH";
		session = venue;
		await load();
		await status.filter({ hasText: "context changed" }).waitFor();
		assert.equal(await result().count(), 0);
		mode = "OK";
		session = venue;
		groups++;
		await load();
		await visible();
		await page.evaluate(() =>
			window.dispatchEvent(new Event("cinenest-session-change")),
		);
		assert.equal(await result().count(), 0);
		groups++;
		await load();
		await visible();
		await page.goto(`${base}/admin/account`);
		await page.goBack();
		await panel.waitFor();
		assert.equal(await result().count(), 0);
		groups++;
		await page.close();
	}
	await writeFile(
		"../.cache/grant-inspection/observations.json",
		JSON.stringify(
			{
				groups,
				observations,
				classification:
					"INTERCEPTED_BROWSER_ONLY; no operational assignments or full E2E acceptance",
			},
			null,
			2,
		),
	);
	console.log(
		`PASS ${groups} intercepted browser groups;375/768/1280;zero mutation requests`,
	);
} finally {
	await browser?.close();
	child.kill("SIGTERM");
	await once(child, "exit");
}

// Real backend journey. Supervised QA can inject one failure on an allowlisted loopback read.
import assert from "node:assert/strict";
import { recoveryScenarios, expiredSessionRecovery } from "./release1-recovery-e2e.mjs";
import {extensionScenarios,extensionReservationJourney} from "./extension-reservation-e2e.mjs";
import {release1Scenarios,release1PendingJourney} from "./release1-booking-e2e.mjs";
import { uploadFaultProxy } from "./upload-fault-proxy.mjs";
import {
	safeCustomerNavigation,
	safeLoopbackResource,
} from "./safe-navigation.mjs";
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { once } from "node:events";
import { createServer } from "node:net";
import {
	readFile,
	writeFile,
	rename,
	unlink,
	mkdir,
	chmod,
	realpath,
	lstat,
} from "node:fs/promises";
import { pathToFileURL } from "node:url";

const mode = process.env.CINENEST_E2E_MODE ?? "campaign";
assert.ok(["campaign", "h1", "supervised-h1"].includes(mode), "unknown browser journey mode");
const supervised = mode === "supervised-h1";
const h1 = mode === "h1" || supervised;
let supervisedOutcome;
const run = process.env.CINENEST_E2E_RUN;
assert.match(run ?? "", /^\/tmp\/cinenest-e2e-[^/]+$/);
const config = JSON.parse(await readFile(`${run}/config.json`, "utf8"));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const results = [];
let stage = "startup",
	browser,
	frontend;
const requests = [];
const observations = [];
let calendarObservation,
    financeObservation,
	expiryObservation,
	invalidHoldObservation,
	rejectionObservation,
	uploadObservation,
	assertionFailure,
	activePage;
const navigations = [];
// Sanitized logical browser trace only: never retain cookies, headers, bodies or query values.
const diagnostics = new URL(
	`../../.cache/e2e-booking-diagnosis/${run.split("/").at(-1)}/`,
	import.meta.url,
);
await mkdir(diagnostics, { recursive: true, mode: 0o700 });
const diagnosticPath = decodeURIComponent(diagnostics.pathname).replace(/\/$/, "");
const diagnosticStat = await lstat(diagnosticPath);
assert.ok(diagnosticStat.isDirectory() && diagnosticStat.uid === process.getuid());
assert.equal(await realpath(diagnosticPath), diagnosticPath);
await chmod(diagnostics, 0o700);
const campaignPlan = [
 ...extensionScenarios,
 ...release1Scenarios,
 ...recoveryScenarios,
	"startup",
	"customer-discovery-auth",
	"hold-quote-accept",
	"verification-explicit-consent",
	"E-pending-verification",
	"independent-verification-api-support",
	"explicit-bank-request",
	"H-missing-CSRF",
	"I-invalid-CSRF",
	"J-cross-origin",
	"L-idempotent-replay",
	"M-changed-body-key",
	"K-stale-readiness",
	"claim-receipt-explicit",
	"simulated-confirmation-api-support",
	"C-wrong-owner",
	"D-wrong-venue-grant",
	"O-malformed-reference",
	"P-safe-URLs",
	"G-expired-session",
	"A-expired-quote",
	"B-invalid-expired-hold",
	"F-rejected-verification",
	"N-uncertain-upload",
];
const planned = h1 ? [
	"startup", "customer-discovery-auth", "hold-quote-accept",
	"verification-explicit-consent", "E-pending-verification",
	"independent-verification-api-support", "explicit-bank-request",
	"H1-pending-recovery", "H1-admin-review", "H-missing-CSRF",
	"I-invalid-CSRF", "J-cross-origin", "L-idempotent-replay",
	"M-changed-body-key", "C-wrong-owner", "D-wrong-venue-grant",
	"O-malformed-reference", "P-safe-URLs", "G-expired-session",
] : campaignPlan;
const screenshots = [];
async function captureH1(page, name, width) {
	const targets = {
		customer: ["/bank", "UNFUNDED · NOT_CONFIRMED"],
		admin: ["/admin/verification", "Synthetic manual review queue"],
	};
	assert.ok(h1 && Object.hasOwn(targets, name) && [375, 1280].includes(width));
	const [pathname, label] = targets[name];
	const url = new URL(page.url());
	assert.equal(url.origin, base);
	assert.equal(url.pathname, pathname);
	await page.setViewportSize({ width, height: 900 });
	assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
	const target = page.getByText(label, { exact: true });
	await target.waitFor({ state: "visible" });
	assert.equal(await target.count(), 1);
	assert.ok(await target.evaluate((element, expected) => {
		const style = getComputedStyle(element);
		return element.textContent.trim() === expected &&
			element.childElementCount === 0 &&
			!element.closest("form input, iframe, [contenteditable]") &&
			style.backgroundImage === "none" &&
			getComputedStyle(element, "::before").content === "none" &&
			getComputedStyle(element, "::after").content === "none";
	}, label), "screenshot target contains unapproved content");
	const filename = `${name}-${width}.png`;
	// Isolate the approved label during rasterization. Visibility preserves layout
	// while suppressing overlays, ancestor text, controls and private previews.
	assert.equal(await page.locator("[data-h1-evidence-label]").count(), 0);
	await target.evaluate(element => element.setAttribute("data-h1-evidence-label", ""));
	let bytes;
	try {
		bytes = await target.screenshot({
			animations: "disabled",
			style: `*, *::before, *::after { visibility: hidden !important; }
				[data-h1-evidence-label] { visibility: visible !important;
					color: #fff !important; background: #161616 !important; }`,
		});
	} finally {
		await target.evaluate(element => element.removeAttribute("data-h1-evidence-label"));
	}
	await writeFile(new URL(filename, diagnostics), bytes, { mode: 0o600, flag: "wx" });
	screenshots.push({ filename, width, sha256: createHash("sha256").update(bytes).digest("hex") });
}

let apiFailure;

const notRun = (id, reason) => results.push({ id, status: "NOT_RUN", reason });
async function scenario(id, action) {
	stage = id;
	let timer;
	try {
		await Promise.race([
			action(),
			new Promise((_, reject) => {
				timer = setTimeout(() => reject(new Error("scenario timeout")), 60000);
			}),
		]);
		results.push({ id, status: "PASS" });
	} finally {
		clearTimeout(timer);
	}
}
async function control(body) {
	await writeFile(`${run}/command.tmp`, JSON.stringify(body), { mode: 0o600 });
	await rename(`${run}/command.tmp`, `${run}/command.json`);
	for (let n = 0; n < 400; n++) {
		try {
			const result = JSON.parse(await readFile(`${run}/reply.json`, "utf8"));
			await unlink(`${run}/reply.json`);
 if(!result.controlOk)await writeFile(new URL("last-control.json",diagnostics),JSON.stringify({changedTables:result.changedTables,newAuditActions:result.newAuditActions}),{mode:0o600});
			assert.equal(
				result.controlOk,
				true,
				"supporting API/test control failed",
			);
			return result;
		} catch (error) {
			if (error.code !== "ENOENT") throw error;
		}
		await new Promise((r) => setTimeout(r, 25));
	}
	throw new Error("supporting control timeout");
}
async function api(page, path, method = "GET", body, csrf = true) {
	return page.evaluate(
		async ({ path, method, body, csrf }) => {
			const headers = {};
			if (method !== "GET") {
				headers["Content-Type"] = "application/json";
				if (csrf) {
					const r = await fetch("/api/session/csrf");
					const token = await r.json();
					headers["X-CSRF-TOKEN"] = token.token;
				}
			}
			const r = await fetch(path, {
				method,
				headers,
				body: body === undefined ? undefined : JSON.stringify(body),
			});
			let value;
			try {
				value = await r.json();
			} catch {}
			return { status: r.status, value };
		},
		{ path, method, body, csrf },
	);
}
async function json(page, path) {
	const r = await api(page, path);
	if (r.status !== 200)
		apiFailure = {
			status: r.status,
			path: path
				.split("?")[0]
				.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, "{reference}"),
		};
	assert.equal(r.status, 200);
	return r.value;
}
async function clickResponse(page, name, path, method = "POST") {
	const [r] = await Promise.all([
		page.waitForResponse(
			response => new URL(response.url()).pathname === path && response.request().method() === method,
		),
		page.getByRole("button", { name, exact: true }).click(),
	]);
	assert.equal(r.status(), 200);
	return r.json();
}
async function startSupervisedVerification(page, base, quote) {
	const path = "/api/verification/requests";
	const quotePath = `/api/quotes/${quote.quoteId}`;
	const matches = (request, pathname, method) => {
		const url = new URL(request.url());
		return url.origin === base && url.pathname === pathname && request.method() === method;
	};
	const events = [];
	let submitted;
	const onRequest = request => {
		if (matches(request, path, "POST")) {
			submitted = request;
			events.push("verification-post-sent");
		}
	};
	const onFailed = request => {
		if (matches(request, path, "POST") || matches(request, quotePath, "GET"))
			events.push(request === submitted ? "verification-post-failed" : "quote-refresh-failed");
	};
	const onResponse = response => {
		if (matches(response.request(), quotePath, "GET"))
			events.push(`quote-refresh-http-${response.status()}`);
	};
	page.on("request", onRequest);
	page.on("requestfailed", onFailed);
	page.on("response", onResponse);
	try {
		const [response] = await Promise.all([
			page.waitForResponse(response => submitted !== undefined && response.request() === submitted, { timeout: 12000 }),
			page.getByRole("button", { name: "Start synthetic request", exact: true }).click({ timeout: 10000 }),
		]);
		events.push(`verification-post-http-${response.status()}`);
		assert.equal(response.status(), 200);
		const payload = submitted.postDataJSON();
		assert.equal(payload.quoteId, quote.quoteId);
		assert.equal(payload.policyVersion, "SYNTHETIC-V1");
		assert.equal(payload.consent, true);
		const verification = await response.json();
		assert.equal(verification.quoteId, quote.quoteId);
		assert.ok(verification.id);
		return verification;
	} catch (error) {
		const changed = await page.getByText("Quotation context changed. Review the refreshed quotation and consent again.", { exact: true })
			.isVisible().catch(() => false);
		if (changed) events.push("ui-quote-context-changed");
		error.verificationEvents = events;
		throw error;
	} finally {
		page.off("request", onRequest);
		page.off("requestfailed", onFailed);
		page.off("response", onResponse);
	}
}

async function reloadH1BankRequest(page, requestId) {
	const detail = page.getByRole("region", { name: "Owned bank request detail", exact: true });
	await detail.getByRole("region", { name: "Current bank request summary", exact: true })
		.waitFor({ state: "visible" });
	const button = detail.getByRole("button", { name: "Reload current request", exact: true });
	assert.equal(await button.count(), 1, "expected one request-detail reload button");
	assert.ok(await button.isVisible(), "request-detail reload button must be visible");
	assert.ok(await button.isEnabled(), "request-detail reload button must be enabled");
	const path = "/api/bank/requests/" + requestId;
	const [response] = await Promise.all([
		page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === "GET"),
		button.click(),
	]);
	assert.equal(response.status(), 200);
	const current = await response.json();
	assert.equal(current.id, requestId);
	return current;
}

function ownedH1EvidenceUrl(href, origin, requestId) {
	assert.equal(typeof href, "string");
	const url = new URL(href, origin);
	assert.ok(safeCustomerNavigation(url.href, origin));
	assert.equal(url.pathname, "/bank");
	assert.deepEqual([...url.searchParams], [["evidence", requestId]]);
	return url.href;
}

async function login(page, subject) {
	await control({ op: "subject", subject });
	// A normal browser OIDC redirect creates the session. Never addCookies/storageState.
	await page
		.goto(`${config.backend}/oauth2/authorization/cinenest`)
		.catch((error) => {
			// Successful callback is HTTP204; Chromium may report an aborted navigation.
			if (!error.message.includes("ERR_ABORTED")) throw error;
		});
	await page.goto(base + "/account");
	const me = await json(page, "/api/accounts/me");
	assert.ok(me.accountId);
	const cookies = await page.context().cookies();
	const session = cookies.find((c) => c.name === "CINENEST_SESSION");
	assert.ok(
		session?.secure && session.httpOnly && session.sameSite === "Lax",
		"normal Secure browser cookie required",
	);
	return me.accountId;
}
async function checkSupervisedCatalog(page, base, config, record) {
	const announce = id => page.evaluate(testId => {
		document.title = `${testId} · CineNest synthetic QA`;
	}, id);
	const listPath = `/api/venues/${config.venue}/packages`;
	const matches = (response, path) => {
		const url = new URL(response.url());
		return url.origin === base && url.pathname === path &&
			url.searchParams.get("roomId") === config.room &&
			response.request().method() === "GET";
	};
	const packages = page.getByRole("region", { name: "Room packages" });
	await page.bringToFront();
	await announce("CT-002");
	await page.getByRole("heading", { name: "Explore our rooms" }).waitFor();
	await record({ id: "CT-002", status: "PASS" });
	const room = page.locator(`a[href="/rooms/${config.room}"]`);
	await room.waitFor();
	const listPromise = page.waitForResponse(response => matches(response, listPath), { timeout: 10000 });
	await room.click();
	await page.waitForURL(base + "/rooms/" + config.room);
	await announce("CT-003");
	await packages.getByRole("heading", { name: "Indicative published packages" }).waitFor();
	await record({ id: "CT-003", status: "PASS" });
	await announce("CT-004");
	const list = await listPromise;
	const payload = list.ok() ? await list.json() : null;
	const item = payload?.items?.[0];
	const listed = list.status() === 200 && payload?.purchasable === false &&
		typeof item?.packageId === "string" && typeof item?.versionId === "string";
	await record({ id: "CT-004", status: listed ? "PASS" : "FAIL", httpStatus: list.status() });

	if (listed) {
		await announce("CT-006");
		const button = packages.getByRole("button", { name: `Package details ${item.name}`, exact: true });
		const detailPromise = page.waitForResponse(
			response => matches(response, `${listPath}/${item.packageId}`), { timeout: 10000 },
		);
		await button.click();
		const detail = await detailPromise;
		const value = detail.ok() ? await detail.json() : null;
		const versionMatches = detail.status() === 200 && value?.purchasable === false &&
			value.items?.some(row => row.packageId === item.packageId && row.versionId === item.versionId);
		const displayed = versionMatches && await packages.getByText(item.description, { exact: true })
			.waitFor({ timeout: 5000 }).then(() => true, () => false);
		await record({ id: "CT-006", status: displayed ? "PASS" : "FAIL", httpStatus: detail.status(),
			scope: "Indicative detail and published version only" });
	}

	const retry = packages.getByRole("button", { name: "Retry catalog", exact: true });
	if (await retry.isVisible()) {
		await announce("CT-005");
		const retryPromise = page.waitForResponse(response => matches(response, listPath), { timeout: 10000 });
		await retry.click();
		const response = await retryPromise;
		const recovered = response.status() === 200 &&
			await packages.getByRole("button", { name: /^Package details / }).first()
				.waitFor({ timeout: 5000 }).then(() => true, () => false);
		await record({ id: "CT-005", status: recovered ? "PASS" : "FAIL", httpStatus: response.status() });
	} else {
		await record({ id: "CT-005", status: "NOT_RUN", reason: "No genuine catalog error exposed a retry control" });
	}
}

async function supervisedReadFailure(page, base, path, kind) {
	const allowed = kind === "catalog"
		? /^\/api\/venues\/[a-f0-9-]{36}\/packages$/
		: kind === "staff-bookings" && /^\/api\/admin\/venues\/[a-f0-9-]{36}\/bookings$/;
	assert.ok(allowed && allowed.test(path) && new URL(base).hostname === "127.0.0.1");
	const matcher = url => url.origin === base && url.pathname === path;
	let injected = false;
	const handler = async route => {
		if (injected || route.request().method() !== "GET") return route.fallback();
		injected = true;
		await route.fulfill({
			status: 503,
			contentType: "application/json",
			body: JSON.stringify({ message: "Synthetic service unavailable" }),
		});
	};
	await page.route(matcher, handler);
	return () => page.unroute(matcher, handler);
}

function supervisedOrigins(config, base) {
	const backend = new URL(config.backend);
	assert.equal(backend.hostname, "127.0.0.1");
	const origins = new Set([base, ...config.ports.map(port => {
		assert.ok(Number.isInteger(port) && port > 0 && port <= 65535);
		return `http://127.0.0.1:${port}`;
	})]);
	assert.ok(origins.has(backend.origin));
	return origins;
}

function waitForSupervisedExit(pages, browser, frontend) {
	return Promise.race([
		...pages.map(({ page }) => page.isClosed() ? Promise.resolve() : once(page, "close")),
		once(browser, "disconnected"),
		once(frontend, "exit").then(() => { throw new Error("Supervised frontend exited early"); }),
	]);
}

const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`;
const faultTransport = h1 ? null : await uploadFaultProxy(config.backend);
await writeFile(
	`${run}/frontend-port.json`,
	JSON.stringify({
		ports: faultTransport ? [port, Number(new URL(faultTransport.origin).port)] : [port],
	}),
);
try {
	frontend = spawn(
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
				CINENEST_AUTH_ORIGIN: faultTransport?.origin ?? config.backend,
				NEXT_TELEMETRY_DISABLED: "1",
			},
			stdio: "ignore",
		},
	);
	await scenario("startup", async () => {
		for (let n = 0; n < 100; n++) {
			if (frontend.exitCode !== null) throw new Error("Next startup failed");
			try {
				const r = await fetch(base);
				if (r.ok) return;
			} catch {}
			await new Promise((r) => setTimeout(r, 100));
		}
		throw new Error("Next startup timeout");
	});
	browser = await chromium.launch({ headless: !supervised });
	assert.equal(browser.version(), "151.0.7922.34");
	if (supervised) {
		const origins = supervisedOrigins(config, base);
		const pages = [];
		for (const [subject, route] of [["customer-a", "/rooms"], ["staff", "/admin"], ["customer-b", "/rooms"]]) {
			const context = await browser.newContext({ viewport: null, serviceWorkers: "block" });
			let authenticating = true;
			await context.route("**/*", (route) => {
				const url = new URL(route.request().url());
				return origins.has(url.origin) && (url.origin === base || authenticating)
					? route.continue() : route.abort();
			});
			const page = await context.newPage();
			const accountId = await login(page, subject);
			authenticating = false;
			await page.goto(base + route);

			pages.push({ page, accountId, route });
		}
		await control({ op: "seal-supervised-auth" });
		assert.equal(new Set(pages.map(({ accountId }) => accountId)).size, pages.length);
		assert.equal(await json(pages[0].page, "/api/accounts/me").then(me => me.accountId), pages[0].accountId);
		const customer = pages[0].page;
		const staff = pages[1].page;
		const report = [];
		const evidenceFile = `${run}/remaining-results.json`;
		const save = async () => {
			await writeFile(`${evidenceFile}.tmp`, JSON.stringify(report), { mode: 0o600 });
			await rename(`${evidenceFile}.tmp`, evidenceFile);
		};
		const test = async (id, page, action) => {
			const check = { id, status: "ERROR", http: [], timestamp: new Date().toISOString() };
			const response = r => {
				const url = new URL(r.url());
				if (url.origin === base && url.pathname.startsWith("/api/")) {
					check.http.push({ method: r.request().method(), status: r.status(),
						path: url.pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, "{id}") });
				}
			};
			page.on("response", response);
			try {
				await page.bringToFront();
				await page.evaluate(label => { document.title = label + " · CineNest synthetic QA"; }, id);
				check.actual = await action();
				check.status = "PASS";
			} catch (error) {
				check.status = error.name === "AssertionError" ? "FAIL" : "ERROR";
				check.reason = error.name;
				if (error.verificationEvents) check.verificationEvents = error.verificationEvents;
				check.source = String(error.stack).split("\n").find(line => line.includes("synthetic-e2e.mjs:"))?.trim();
			} finally {
				page.off("response", response);
			}
			report.push(check);
			results.push({ id, status: check.status });
			await save();
			return check.status === "PASS";
		};
		const roomUrl = base + "/rooms/" + config.room;
		const catalogPath = `/api/venues/${config.venue}/packages`;
		const respond = (page, path, method = "GET") => page.waitForResponse(r =>
			new URL(r.url()).pathname === path && r.request().method() === method, { timeout: 10000 });
		await test("CT-005", customer, async () => {
			const disarm = await supervisedReadFailure(customer, base, catalogPath, "catalog");
			try {
				const initial = respond(customer, catalogPath);
				await customer.goto(roomUrl);
				assert.equal((await initial).status(), 503);
				await customer.getByText("Package catalog unavailable.", { exact: true }).waitFor();
				const recovered = await clickResponse(customer, "Retry catalog", catalogPath, "GET");
				const item = recovered.items[0];
				assert.ok(item.packageId && item.versionId && item.description);
				const detail = await clickResponse(customer, `Package details ${item.name}`, `${catalogPath}/${item.packageId}`, "GET");
				assert.ok(detail.items.some(row => row.packageId === item.packageId && row.versionId === item.versionId));
				await customer.getByText(item.description, { exact: true }).waitFor();
				return "One injected catalog 503, then real list and published-version detail recovered";
			} finally { await disarm(); }
		});
		await customer.goto(roomUrl);
		let selected;
		let hold;
		const slotReady = await test("CT-007", customer, async () => {
			const date = config.local.split("T")[0];
			await customer.getByLabel("Calendar date (venue local)").fill(date);
			await customer.getByLabel("Calendar duration (minutes)").fill("180");
			await customer.getByLabel("Calendar guests", { exact: true }).fill("4");
			const calendarResponse = respond(customer, `/api/rooms/${config.room}/availability/calendar`);
			await customer.getByRole("button", { name: "Load availability calendar", exact: true }).click();
			const r = await calendarResponse;
			assert.equal(r.status(), 200);
			const calendar = await r.json();
			assert.equal(calendar.roomId, config.room);
			assert.equal(calendar.venueId, config.venue);
			assert.equal(calendar.date, date);
			assert.equal(calendar.durationMinutes, 180);
			assert.equal(calendar.guests, 4);
			assert.equal(calendar.complete, true);
			selected = calendar.items.find(item => item.available && item.startLocal === config.local) ?? calendar.items.find(item => item.available);
			assert.ok(selected);
			await customer.getByLabel(`Session start (${calendar.timezone})`).selectOption(selected.startLocal);
			await customer.getByRole("button", { name: "Use selected time", exact: true }).click();
			assert.equal(await customer.getByLabel("Customer start in venue local time").inputValue(), selected.startLocal);
			return "Available slot selected and copied to the inventory form";
		});
		const holdReady = slotReady && await test("CT-008", customer, async () => {
			await clickResponse(customer, "Check inventory (no hold)", `/api/rooms/${config.room}/availability`, "GET");
			hold = await clickResponse(customer, "Create temporary inventory hold", "/api/holds");
			assert.equal(hold.state, "ACTIVE");
			assert.equal(hold.sessionStart, selected.sessionStart);
			assert.ok(Number.isInteger(hold.revision));
			assert.ok(Date.parse(hold.expiresAt) > Date.now());
			await customer.getByText("Temporary inventory hold", { exact: true }).waitFor();
			await customer.getByText(/^Server expiry:/).waitFor();
			const panel = customer.getByRole("region", { name: "Inventory hold status" });
			await writeFile(`${run}/ct-008.png`, await panel.screenshot({ animations: "disabled" }), { mode: 0o600 });
			return "Active hold matched selected start and rendered its expiry";
		});
		if (holdReady) await test("CT-010", pages[2].page, async () => {
			const contender = pages[2].page;
			await contender.goto(roomUrl);
			const inquiry = await api(contender, `/api/rooms/${config.room}/availability?${new URLSearchParams({
				sessionStartLocal: selected.startLocal, durationMinutes: "180", guests: "4",
			})}`);
			assert.equal(inquiry.status, 200);
			assert.equal(inquiry.value.available, false);
			const conflict = await api(contender, "/api/holds", "POST", {
				roomId: config.room, sessionStartLocal: selected.startLocal, durationMinutes: 180, guests: 4,
				expectedConfiguration: inquiry.value.configuration, idempotencyKey: crypto.randomUUID(),
			});
			assert.equal(conflict.status, 409);
			assert.equal(conflict.value.message, "Inventory conflict or changed configuration. Inquire again; no booking is confirmed.");
			await control({ op: "supervised-conflict-inspect", holdId: hold.holdId });
			return "Second customer received an overlap 409 and the original hold remained active";
		});
		if (holdReady) await test("CT-012", customer, async () => {
			await customer.getByRole("button", { name: "Load applicable synthetic package choices", exact: true }).click();
			const packageChoice = customer.getByLabel("Published package");
			await packageChoice.selectOption({ index: 1 });
			await customer.getByLabel("Intended method (no payment)").selectOption("BANK");
			const quote = await clickResponse(customer, "Calculate synthetic quotation", "/api/quotes");
			assert.equal(quote.state, "ISSUED");
			assert.equal(quote.revision, 1);
			assert.equal(quote.lines.reduce((sum, line) => sum + BigInt(line.amountMinor), 0n), BigInt(quote.totalMinor));
			await customer.getByText(/^Total: LKR/).waitFor();
			await customer.getByLabel("I reviewed this exact synthetic revision and all three disclosures").check();
			const accepted = await clickResponse(customer, "Accept synthetic revision only", `/api/quotes/${quote.quoteId}/accept`);
			for (const field of ["quoteId", "revision", "contentHash", "totalMinor"]) {
				assert.equal(accepted[field], quote[field]);
			}
			assert.equal(accepted.state, "ACCEPTED");
			await customer.getByRole("link", { name: "Continue to Verification", exact: true }).waitFor();
			await customer.getByRole("link", { name: "Continue to Verification", exact: true }).click();
			await customer.waitForURL(`${base}/verification?quote=${quote.quoteId}`);
			const handoff = await json(customer, `/api/quotes/${quote.quoteId}`);
			assert.equal(handoff.contentHash, accepted.contentHash);
			assert.equal(handoff.revision, accepted.revision);
			assert.equal(handoff.verificationHandoff.eligible, true);
			await customer.getByLabel("Operator-provided synthetic policy version").fill("SYNTHETIC-V1");
			await customer.getByLabel("I consent to this versioned synthetic test processing; no real documents").check();
			const verification = await startSupervisedVerification(customer, base, quote);
			await customer.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)").setInputFiles(`${run}/identity.png`);
			const [completed] = await Promise.all([
				customer.waitForResponse(r => /\/api\/verification\/uploads\/[^/]+\/complete$/.test(new URL(r.url()).pathname) && r.request().method() === "POST"),
				customer.getByRole("button", { name: "Upload and request simulated scan", exact: true }).click(),
			]);
			assert.equal(completed.status(), 200);
			assert.equal((await control({ op: "review", id: verification.id, verdict: "VERIFIED" })).state, "VERIFIED");
			await customer.reload();
			await customer.getByLabel("Existing request ID").fill(verification.id);
			await customer.getByRole("button", { name: "Refresh current request and history", exact: true }).click();
			await customer.getByRole("link", { name: "Continue to Bank Request", exact: true }).click();
			await customer.getByLabel("I reviewed this synthetic quotation and explicitly request creation. No money transfer or booking confirmation is authorized.").check();
			const [request, bank] = await Promise.all([
				customer.waitForRequest(r => new URL(r.url()).pathname === "/api/bank/requests" && r.method() === "POST"),
				clickResponse(customer, "Create synthetic request", "/api/bank/requests"),
			]);
			assert.ok(bank.id);
			await customer.goto(`${base}/bank?request=${bank.id}`);
			const current = await reloadH1BankRequest(customer, bank.id);
			assert.equal(current.quoteId, quote.quoteId);
			assert.equal(current.requestState, "OPEN");
			assert.equal(current.reviewState, "PENDING");
			assert.equal(current.paymentVerified, false);
			assert.equal(current.bookingConfirmed, false);
			assert.equal(current.financial.syntheticFundingStatus, "UNFUNDED");
			assert.equal(current.financial.syntheticBookingStatus, "NOT_CONFIRMED");
			const pending = customer.getByRole("region", { name: "Current bank request summary", exact: true });
			await pending.getByText(/UNFUNDED/).first().waitFor();
			await pending.getByText(/NOT_CONFIRMED/).first().waitFor();
			await control({ op: "r1-pending-inspect", quoteId: quote.quoteId, requestId: bank.id,
				commandKey: request.postDataJSON().commandKey });
			return "Bank request remained OPEN / PENDING / UNFUNDED / NOT_CONFIRMED with no credit, booking or admission effects";
		});
		const bookingSearch = async (page, venue) => {
			await page.goto(base + "/admin/bookings");
			await page.getByLabel("Assigned venue reference").fill(venue);
			const [response] = await Promise.all([
				respond(page, `/api/admin/venues/${venue}/bookings`),
				page.getByRole("button", { name: "Apply booking filters", exact: true }).click(),
			]);
			return response;
		};
		await test("CT-023", staff, async () => {
			const path = `/api/admin/venues/${config.venue}/bookings`;
			const before = await control({ op: "supervised-booking-count" });
			const disarm = await supervisedReadFailure(staff, base, path, "staff-bookings");
			try {
				assert.equal((await bookingSearch(staff, config.venue)).status(), 503);
				await staff.getByText(/Booking unavailable, missing, changed or refused/).waitFor();
				assert.equal(await staff.getByText(/^Server observation:/).count(), 0);
				assert.equal((await bookingSearch(staff, config.venue)).status(), 200);
				await staff.getByText(/^Server observation:/).waitFor();
				assert.equal((await control({ op: "supervised-booking-count" })).bookings, before.bookings);
				return "One injected read-only booking 503 cleared results and a real refresh restored them";
			} finally { await disarm(); }
		});
		await test("CT-017", staff, async () => {
			const path = `/api/admin/venues/${config.venue}/rooms/${config.room}/blocks`;
			await staff.goto(base + "/admin/room-blocking");
			await staff.getByRole("button", { name: "Find venues by name", exact: true }).click();
			await staff.getByLabel("Venue", { exact: true }).selectOption(config.venue);
			await staff.getByRole("button", { name: "Find rooms", exact: true }).click();
			await staff.getByLabel("Room", { exact: true }).selectOption(config.room);
			await clickResponse(staff, "Load room blocks", path, "GET");
			const date = new Date(Date.parse(config.local.slice(0, 10) + "T00:00:00Z") + 86400000).toISOString().slice(0, 10);
			await staff.getByLabel("Start date", { exact: true }).fill(date);
			await staff.getByLabel("End date", { exact: true }).fill(date);
			await staff.getByLabel("Start time (24-hour)", { exact: true }).fill("01:00");
			await staff.getByLabel("End time (24-hour)", { exact: true }).fill("02:00");
			const block = await clickResponse(staff, "Create room block", path);
			await staff.getByText(/Room block saved/).waitFor();
			const saved = await clickResponse(staff, "Load room blocks", path, "GET");
			assert.equal(Date.parse(block.startAt), Date.parse(date + "T01:00:00+05:30"));
			assert.equal(Date.parse(block.endAt), Date.parse(date + "T02:00:00+05:30"));
			assert.equal(block.revision, 0);
			assert.deepEqual(saved.items.find(item => item.blockId === block.blockId), block);
			await staff.getByRole("button", { name: "Inspect block", exact: true }).waitFor();
			await writeFile(`${run}/ct-017.png`, await staff.getByRole("button", { name: "Inspect block", exact: true })
				.locator("..").screenshot({ animations: "disabled" }), { mode: 0o600 });
			assert.equal((await api(customer, path)).status, 403);
			await control({ op: "supervised-block-inspect", blockId: block.blockId });
			return "Staff created and reloaded one maintenance block with attributable audit; customer denied";
		});
		await pages[0].page.goto(base + "/rooms");
		await pages[0].page.bringToFront();
		supervisedOutcome = { routes: pages.slice(0, 2).map(({ route }) => base + route), ready: true };
		await writeFile(`${run}/supervised-ready.json`, JSON.stringify({
			syntheticOnly: true,
			customerUrl: supervisedOutcome.routes[0],
			staffUrl: supervisedOutcome.routes[1],
		}), { mode: 0o600, flag: "wx" });
		await waitForSupervisedExit(pages, browser, frontend);
		supervisedOutcome.finished = true;
		results.push({ id: "supervised-session", status: "PASS" });
	} else {
	const context = await browser.newContext({
		viewport: { width: 1280, height: 900 },
	});

	const page = await context.newPage();
	activePage = page;
	page.on("framenavigated", (frame) => {
		if (frame !== page.mainFrame()) return;
		const u = new URL(frame.url());
		// Authentication redirects are a separate contract; never record their queries.
		if (u.origin !== base) return;
		const expectedNegative =
			stage === "O-malformed-reference" &&
			u.href === base + "/bank?evidence=malformed";
		navigations.push({
			path: u.pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, "{reference}"),
			queryKeys: [...u.searchParams.keys()].map((k) =>
				["evidence", "request", "quote"].includes(k) ? k : "{other}",
			),
			stage,
			expectedNegative,
			safe: safeCustomerNavigation(u.href, base),
		});
	});
	context.on("response", (response) => {
		const url = new URL(response.url());
		if (url.pathname.startsWith("/api/"))
			observations.push({
				method: response.request().method(),
				path: url.pathname.replace(
					/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
					"{reference}",
				),
				status: response.status(),
			});
	});
	page.setDefaultTimeout(12000);
	context.on("request", (request) => {
		const url = new URL(request.url());
		assert.ok(
			["127.0.0.1", "localhost"].includes(url.hostname),
			"external network request prohibited",
		);
		if (url.pathname.startsWith("/api/"))
			requests.push({
				method: request.method(),
				path: url.pathname.replace(
					/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
					"{reference}",
				),
			});
	});
	let quote, verification, bank, creationBody;
	await scenario("customer-discovery-auth", async () => {
		await page.goto(base);
		await page
			.getByRole("link", { name: "Explore Cinema Rooms", exact: true })
			.click();
		await page.waitForURL(base + "/rooms");
		await page.locator(`a[href="/rooms/${config.room}"]`).first().click();
		await page.waitForURL(base + "/rooms/" + config.room);
		assert.ok(
			(
				await json(
					page,
					`/api/venues/${config.venue}/packages?roomId=${config.room}`,
				)
			).items.length > 0,
		);
		await login(page, "customer-a");
	});
	await scenario("hold-quote-accept", async () => {
		await page.goto(base + "/rooms/" + config.room);

        const checkpoint=requests.length;
        const before=await control({op:"calendar-hold-read",phase:"before"});
        assert.equal(before.holds,0);assert.equal(before.quotes,0);
        const date=config.local.split("T")[0],duration=180,guests=4;
        await page.getByLabel("Calendar date (venue local)").fill(date);
        await page.getByLabel("Calendar duration (minutes)").fill(String(duration));
        await page.getByLabel("Calendar guests",{exact:true}).fill(String(guests));
        const calendarPath=`/api/rooms/${config.room}/availability/calendar`;
        const calendarResponse=page.waitForResponse(r=>new URL(r.url()).pathname===calendarPath&&r.request().method()==="GET");
        await page.getByRole("button",{name:"Load availability calendar",exact:true}).click();
        const cr=await calendarResponse;assert.equal(cr.status(),200);
        assert.deepEqual([...new URL(cr.url()).searchParams.entries()],[['date',date],['durationMinutes',String(duration)],['guests',String(guests)]]);
        const calendar=await cr.json();assert.equal(calendar.roomId,config.room);assert.equal(calendar.venueId,config.venue);assert.equal(calendar.date,date);assert.equal(calendar.durationMinutes,duration);assert.equal(calendar.guests,guests);assert.equal(calendar.complete,true);assert.equal(calendar.evidenceLevel,"INVENTORY_ONLY");assert.equal(calendar.items.length,1440);
        const selected=calendar.items.find(i=>i.startLocal===config.local&&i.available===true);
        assert.ok(selected,"Fixture start must actually be returned available by the server");
        calendarObservation={date,timezone:calendar.timezone,selectedStart:selected.startLocal,sessionStart:selected.sessionStart,sessionEnd:selected.sessionEnd,blockedUntil:selected.blockedUntil,calendarStatus:cr.status(),noAutomaticMutations:false};
        await page.getByText(`Venue local time · ${calendar.timezone}`,{exact:true}).waitFor();
        // Establish a previous point result; selecting the calendar start must invalidate it.
        await page.getByLabel("Customer start in venue local time").fill(selected.startLocal);
        await page.getByLabel("Customer duration (minutes)").fill(String(duration));
        await page.getByLabel("Guests",{exact:true}).fill(String(guests));
        await clickResponse(page,"Check inventory (no hold)",`/api/rooms/${config.room}/availability`,"GET");
        await page.getByRole("button",{name:"Create temporary inventory hold",exact:true}).waitFor();
        await page.getByLabel(`Session start (${calendar.timezone})`).selectOption(selected.startLocal);
        await page.getByRole("button",{name:"Use selected time",exact:true}).click();
        assert.equal(await page.getByLabel("Customer start in venue local time").inputValue(),selected.startLocal);
        assert.equal(await page.getByLabel("Customer duration (minutes)").inputValue(),String(duration));
        assert.equal(await page.getByLabel("Guests",{exact:true}).inputValue(),String(guests));
        assert.equal(await page.getByRole("button",{name:"Create temporary inventory hold",exact:true}).count(),0);
        calendarObservation.formHandoffVerified=true;calendarObservation.previousPointCleared=true;
        const unmutated=await control({op:"calendar-hold-read",phase:"selected"});assert.equal(unmutated.unchanged,true);
        assert.equal(requests.slice(checkpoint).filter(r=>r.method!=="GET").length,0);calendarObservation.noAutomaticMutations=true;
        const pointResponse=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/rooms/${config.room}/availability`&&r.request().method()==="GET");
        await page.getByRole("button",{name:"Check inventory (no hold)",exact:true}).click();const pr=await pointResponse;assert.equal(pr.status(),200);
        const pq=new URL(pr.url()).searchParams;assert.equal(pq.get("sessionStartLocal"),selected.startLocal);assert.equal(pq.get("durationMinutes"),String(duration));assert.equal(pq.get("guests"),String(guests));
        const point=await pr.json();assert.equal(point.roomId,config.room);assert.equal(point.available,true);assert.equal(point.timezone,calendar.timezone);assert.equal(point.sessionStart,selected.sessionStart);assert.equal(point.sessionEnd,selected.sessionEnd);assert.equal(point.blockedUntil,selected.blockedUntil);
        calendarObservation.pointStatus=pr.status();calendarObservation.pointMatchesSelection=true;
        const hold=await clickResponse(page,"Create temporary inventory hold","/api/holds");
        const held=await control({op:"calendar-hold-read",phase:"held",holdId:hold.holdId,configuration:point.configuration});
        assert.equal(held.scopeVerified,true);assert.equal(held.configurationMatches,true);assert.equal(held.guests,guests);assert.equal(held.timezone,calendar.timezone);assert.equal(held.session_start,selected.sessionStart);assert.equal(held.session_end,selected.sessionEnd);assert.equal(held.blocked_until,selected.blockedUntil);assert.equal(held.buffer_minutes,point.bufferMinutes);assert.equal(held.expires_at,hold.expiresAt);assert.equal(hold.state,"ACTIVE");
        assert.equal(requests.slice(checkpoint).filter(r=>r.method==="POST"&&r.path==="/api/holds").length,1);
        calendarObservation.holdStatus=200;calendarObservation.persisted=held;

		await page
			.getByRole("button", {
				name: "Load applicable synthetic package choices",
				exact: true,
			})
			.click();
		const select = page.getByLabel("Published package");
		await select.selectOption({ index: 1 });
		await page.getByLabel("Intended method (no payment)").selectOption("BANK");
		quote = await clickResponse(
			page,
			"Calculate synthetic quotation",
			"/api/quotes",
		);
		await page
			.getByLabel(
				"I reviewed this exact synthetic revision and all three disclosures",
			)
			.check();
        assert.equal(quote.state,"ISSUED");assert.equal(quote.revision,1);
        assert.equal(quote.lines.reduce((sum,line)=>sum+BigInt(line.amountMinor),0n),BigInt(quote.totalMinor));
        const accepted=await clickResponse(page,"Accept synthetic revision only",`/api/quotes/${quote.quoteId}/accept`);
        for(const field of ["quoteId","revision","contentHash","totalMinor"])assert.equal(accepted[field],quote[field]);assert.equal(accepted.state,"ACCEPTED");
        const acceptedRead=await control({op:"calendar-hold-read",phase:"accepted",holdId:hold.holdId,configuration:point.configuration,quoteId:quote.quoteId});assert.equal(acceptedRead.acceptedQuoteBindingVerified,true);
        calendarObservation.quotation={state:accepted.state,revision:accepted.revision,totalMinor:accepted.totalMinor,currency:accepted.currency,bindingVerified:true};
		await page
			.getByRole("link", { name: "Continue to Verification", exact: true })
			.click();
		await page.waitForURL(base + "/verification?quote=" + quote.quoteId);
	});
	await scenario("verification-explicit-consent", async () => {
		const consent = page.getByLabel(
			"I consent to this versioned synthetic test processing; no real documents",
		);
		assert.equal(await consent.isChecked(), false);
		await page
			.getByLabel("Operator-provided synthetic policy version")
			.fill("SYNTHETIC-V1");
		await consent.check();
		verification = await clickResponse(
			page,
			"Start synthetic request",
			"/api/verification/requests",
		);
		await page
			.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)")
			.setInputFiles(`${run}/identity.png`);
		const completed = page.waitForResponse(
			(r) =>
				/\/api\/verification\/uploads\/[^/]+\/complete$/.test(
					new URL(r.url()).pathname,
				) && r.request().method() === "POST",
		);
		await page
			.getByRole("button", {
				name: "Upload and request simulated scan",
				exact: true,
			})
			.click();
		assert.equal((await completed).status(), 200);
		// Completion, not transfer progress, is the boundary for the supporting scanner/reviewer.
		await page
			.getByRole("button", {
				name: "Upload and request simulated scan",
				exact: true,
			})
			.waitFor();
		for (let n = 0; n < 100; n++) {
			const r = await json(
				page,
				`/api/verification/requests/${verification.id}`,
			);
			if (
				r.evidence.some((e) => e.state === "QUARANTINED" || e.state === "READY")
			)
				break;
			await new Promise((r) => setTimeout(r, 50));
			if (n === 99) throw new Error("verification completion missing");
		}
	});
	await scenario("E-pending-verification", async () => {
		const readiness = await json(
			page,
			`/api/bank/requests/readiness?quote=${quote.quoteId}`,
		);
		assert.notEqual(readiness.reason, "ELIGIBLE");
        const denied=await api(page,"/api/bank/requests","POST",{quoteId:quote.quoteId,quoteRevision:quote.revision,contentHash:quote.contentHash,commandKey:crypto.randomUUID()});
        assert.equal(denied.status,409);
        await control({op:"r1-no-request",quoteId:quote.quoteId});
	});
	await scenario("independent-verification-api-support", async () => {
		const result = await control({
			op: "review",
			id: verification.id,
			verdict: "VERIFIED",
		});
		assert.equal(result.state, "VERIFIED");
		await page.reload();
		await page.getByLabel("Existing request ID").fill(verification.id);
		await page
			.getByRole("button", {
				name: "Refresh current request and history",
				exact: true,
			})
			.click();
		await page
			.getByRole("link", { name: "Continue to Bank Request", exact: true })
			.click();
		await page.waitForURL(base + "/bank?quote=" + quote.quoteId);
	});
	await scenario("explicit-bank-request", async () => {
		const consent = page.getByLabel(
			"I reviewed this synthetic quotation and explicitly request creation. No money transfer or booking confirmation is authorized.",
		);
		assert.equal(await consent.isChecked(), false);
		await consent.check();
		const observed = page.waitForRequest(
			(r) =>
				new URL(r.url()).pathname === "/api/bank/requests" &&
				r.method() === "POST",
		);
        if (faultTransport) faultTransport.armBankCreate();
        const timedOut=page.waitForResponse(r=>new URL(r.url()).pathname==="/api/bank/requests"&&r.request().method()==="POST",{timeout:25000});
        await page.getByRole("button",{name:"Create synthetic request",exact:true}).click();
        creationBody=(await observed).postDataJSON();
        const response = await timedOut;
        if (h1) {
            assert.equal(response.status(), 200);
            bank = await response.json();
        } else {
            assert.equal(response.status(), 502);
            assert.equal(faultTransport.bankCreateReceipt.upstreamStatus, 200);
            assert.equal(faultTransport.bankCreateReceipt.stalled, 1);
        }
	});
	if (h1) {
		await scenario("H1-pending-recovery", async () => {
			const before = requests.filter(r => r.method === "POST").length;
			await page.goto(base + "/bank?request=" + bank.id);
			const current = await reloadH1BankRequest(page, bank.id);
			assert.equal(current.quoteId, quote.quoteId);
			assert.equal(current.requestState, "OPEN");
			assert.equal(current.reviewState, "PENDING");
			assert.equal(current.paymentVerified, false);
			assert.equal(current.bookingConfirmed, false);
			assert.equal(current.financial.syntheticFundingStatus, "UNFUNDED");
			assert.equal(current.financial.syntheticBookingStatus, "NOT_CONFIRMED");
			assert.equal(current.financial.allocatedMinor, 0);
			assert.equal(current.financial.bookingId, null);
			assert.equal(requests.filter(r => r.method === "POST").length, before);
			const evidence = await control({ op: "r1-pending-inspect", quoteId: quote.quoteId,
				requestId: bank.id, commandKey: creationBody.commandKey });
			for (const key of ["credits", "verifications", "allocations", "bookings", "credentials", "provisioningWork"])
				assert.equal(evidence[key], 0, key);
			assert.equal(evidence.requests, 1);
			assert.equal(evidence.commandReceipts, 1);
			assert.equal(evidence.bindingVerified, true);
			for (const width of [375, 1280]) await captureH1(page, "customer", width);
		});
		await scenario("H1-admin-review", async () => {
			const staff = await browser.newContext();
			try {
				const review = await staff.newPage();
				await login(review, "staff");
				await review.goto(base + "/admin/verification?venue=" + config.venue);
				const route = `/api/admin/venues/${config.venue}/verification/requests/${verification.id}`;
				await review.getByLabel("Existing request ID").fill(verification.id);
				const record = await clickResponse(review, "Refresh current request and history", route, "GET");
				assert.equal(record.id, verification.id);
				assert.equal(record.state, "VERIFIED");
				assert.equal((await api(review,
					`/api/admin/venues/${config.otherVenue}/verification/requests/${verification.id}`)).status, 403);
				for (const width of [375, 1280]) await captureH1(review, "admin", width);
			} finally {
				await staff.close();
			}
		});
	} else bank=await release1PendingJourney({page,browser,base,config,api,json,login,control,scenario,bank,quote,creationBody,calendarObservation,faultTransport,diagnostics,requests});
	await scenario("H-missing-CSRF", async () => {
		assert.equal(
			(await api(page, "/api/bank/requests", "POST", creationBody, false))
				.status,
			403,
		);
	});
	await scenario("I-invalid-CSRF", async () => {
		const r = await context.request.post(base + "/api/bank/requests", {
			data: creationBody,
			headers: { Origin: base, "X-CSRF-TOKEN": "invalid-synthetic-token" },
		});
		assert.equal(r.status(), 403);
	});
	await scenario("J-cross-origin", async () => {
		const csrf = await json(page, "/api/session/csrf");
		const r = await context.request.post(base + "/api/bank/requests", {
			data: creationBody,
			headers: {
				Origin: "https://unrelated.invalid",
				"X-CSRF-TOKEN": csrf.token,
			},
		});
		assert.equal(r.status(), 403);
	});
	await scenario("L-idempotent-replay", async () => {
		const r = await api(page, "/api/bank/requests", "POST", creationBody);
		assert.equal(r.status, 200);
		assert.equal(r.value.id, bank.id);
	});
	await scenario("M-changed-body-key", async () => {
		const r = await api(page, "/api/bank/requests", "POST", {
			...creationBody,
			quoteRevision: creationBody.quoteRevision + 1,
		});
		assert.ok([400, 409].includes(r.status));
	});
	if (!h1) {
	await scenario("K-stale-readiness", async () => {
		const r = await api(page, "/api/bank/requests", "POST", {
			...creationBody,
			commandKey: crypto.randomUUID(),
		});
		assert.ok([400, 409].includes(r.status));
	});
	await scenario("claim-receipt-explicit", async () => {
		await page.goto(base + "/bank?request=" + bank.id);
		const link = page.getByRole("link", {
			name: "Open synthetic claim and receipt workflow",
			exact: true,
		});
		await link.focus();
		await page.keyboard.press("Enter");
		await page.waitForURL(base + "/bank?evidence=" + bank.id);
		await page
			.getByLabel("Declared amount in minor units (not proof of funds)")
			.fill(bank.amountMinor);
		await page
			.getByLabel("Declared payment time (ISO UTC, unverified)")
			.fill(new Date().toISOString());
		await page
			.getByLabel("Synthetic declared reference")
			.fill("SYNTHETIC-ONLY");
		const claim = await clickResponse(
			page,
			"Submit synthetic claim",
			`/api/bank/requests/${bank.id}/claims`,
		);
		assert.equal(claim.paymentVerified, false);
		assert.equal(claim.bookingConfirmed, false);
		await page
			.getByLabel("Approved synthetic JPEG/PNG fixture")
			.setInputFiles(`${run}/receipt.png`);
		const completed = page.waitForResponse(
			(r) =>
				/\/api\/bank\/uploads\/[^/]+\/complete$/.test(
					new URL(r.url()).pathname,
				) && r.request().method() === "POST",
		);
		await page
			.getByRole("button", {
				name: "Upload private synthetic receipt",
				exact: true,
			})
			.click();
		assert.equal((await completed).status(), 200);
		for (let n = 0; n < 100; n++) {
			const r = await json(page, `/api/bank/requests/${bank.id}`);
			if (r.evidence.some((e) => ["QUARANTINED", "READY"].includes(e.state))) {
				assert.equal(r.bookingConfirmed, false);
				return;
			}
			await new Promise((r) => setTimeout(r, 50));
		}
		throw new Error("receipt completion missing");
	});

	await scenario("N-uncertain-upload", async () => {
		// Scan the first explicitly completed receipt through the existing worker.
		await control({ op: "prepare-upload-fault", id: bank.id });
		await page.reload();
		await page
			.getByRole("button", {
				name: "Upload private synthetic receipt",
				exact: true,
			})
			.waitFor();
		const before = await json(page, `/api/bank/requests/${bank.id}`);
		assert.equal(before.evidence.length, 1);
		assert.equal(before.evidence[0].state, "READY");
		await page
			.getByLabel("Approved synthetic JPEG/PNG fixture")
			.setInputFiles(`${run}/receipt.png`);
		const attempts = faultTransport.receipt.attempts;
		const commandStart = requests.length;
		const failed = page.waitForResponse(
			(r) =>
				/\/api\/bank\/uploads\/[^/]+\/content$/.test(
					new URL(r.url()).pathname,
				) && r.request().method() === "PUT",
		);
		faultTransport.arm();
		await page
			.getByRole("button", {
				name: "Upload private synthetic receipt",
				exact: true,
			})
			.click();
		assert.equal((await failed).status(), 502);
		await page
			.getByText("Submission outcome is not confirmed.", { exact: false })
			.waitFor();
		const uncertain = await json(page, `/api/bank/requests/${bank.id}`);
		const item = uncertain.evidence.find((e) => e.state === "QUARANTINED");
		assert.ok(item);
		assert.equal(uncertain.evidence.length, 2);
		assert.equal(uncertain.paymentVerified, false);
		assert.equal(uncertain.bookingConfirmed, false);
		assert.equal(faultTransport.receipt.attempts, attempts + 1);
		assert.equal(faultTransport.receipt.dropped, 1);
		assert.equal(faultTransport.receipt.upstreamStatus, 200);
		const pending = await control({
			op: "inspect-upload-uncertain",
			id: bank.id,
			evidence: item.id,
		});
		assert.equal(pending.completed, false);
		assert.equal(pending.scanQueued, 0);
		assert.equal(pending.state, "QUARANTINED");
		assert.equal(
			requests.slice(commandStart).filter((r) => r.method === "POST").length,
			1,
			"Only explicit allocation, no automatic completion",
		);
		const other = await browser.newContext();
		try {
			const p = await other.newPage();
			await login(p, "customer-b");
			assert.equal((await api(p, `/api/bank/requests/${bank.id}`)).status, 404);
			assert.equal(
				(
					await api(p, `/api/bank/uploads/${item.id}/complete`, "POST", {
						revision: uncertain.revision,
						commandKey: crypto.randomUUID(),
					})
				).status,
				404,
			);
		} finally {
			await other.close();
		}
		// Explicit retry reloads QUARANTINED and completes the original allocation; no PUT.
		const complete = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === `/api/bank/uploads/${item.id}/complete` &&
				r.request().method() === "POST",
		);
		await page
			.getByRole("button", {
				name: "Upload private synthetic receipt",
				exact: true,
			})
			.click();
		assert.equal((await complete).status(), 200);
		const recovered = await json(page, `/api/bank/requests/${bank.id}`);
		assert.equal(recovered.evidence.length, 2);
		assert.equal(
			recovered.evidence.find((e) => e.id === item.id).state,
			"QUARANTINED",
		);
		assert.equal(faultTransport.receipt.attempts, attempts + 1);
		assert.equal(recovered.paymentVerified, false);
		assert.equal(recovered.bookingConfirmed, false);
		uploadObservation = await control({
			op: "inspect-upload-fault",
			id: bank.id,
			evidence: item.id,
		});
		assert.equal(uploadObservation.protectedStateUnchanged, true);
		assert.equal(uploadObservation.state, "QUARANTINED");
		assert.equal(uploadObservation.scanQueued, 1);
		assert.equal(uploadObservation.completed, true);
		assert.equal(uploadObservation.allocationCount, 2);
		assert.equal(
			requests.slice(commandStart).filter((r) => r.method === "POST").length,
			2,
			"Same allocation followed by one explicit completion",
		);
		uploadObservation = {
			...uploadObservation,
			transport: { ...faultTransport.receipt },
			uncertainPersistence: pending,
			browserStatus: 502,
			recoveryStatus: 200,
			bytesResent: false,
		};
	});
	await scenario("simulated-confirmation-api-support", async () => {
		const result = await control({ op: "finance", id: bank.id });
		financeObservation = {
			bookingId: result.bookingId,
			syntheticBookingStatus: result.syntheticBookingStatus,
			bookingConfirmed: result.bookingConfirmed,
			persistence: result.diagnosticBooking,
		};
		assert.equal(result.syntheticBookingStatus, "SIMULATED_CONFIRMED");
		assert.equal(result.bookingConfirmed, false);
		await page.goto(base + "/bank?request=" + bank.id);
		await page
			.getByRole("link", {
				name: "View simulated booking details",
				exact: true,
			})
			.click();
		await page.waitForURL(base + "/bookings/" + result.bookingId);
		assert.equal(
			(await json(page, "/api/bookings/" + result.bookingId)).id,
			result.bookingId,
		);
		await page
			.getByRole("heading", { name: "Purchased snapshot", exact: true })
			.waitFor({ state: "visible" });
		const historyResponse = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === "/api/bookings" &&
				r.request().method() === "GET",
		);
		await page.goto(base + "/bookings");
		await page
			.getByRole("button", { name: "Refresh booking history", exact: true })
			.click();
		assert.equal(
			(await historyResponse).status(),
			200,
			"owned booking history must load successfully",
		);
		await page
			.locator(`a[href="/bookings/${result.bookingId}"]`)
			.first()
			.waitFor({ state: "visible" });
		assert.ok(
			(await page.locator(`a[href="/bookings/${result.bookingId}"]`).count()) >
				0,
		);
	});
 await extensionReservationJourney({browser,page,base,booking:financeObservation.bookingId,api,json,login,control,scenario,transport:faultTransport,diagnostics,observations});
	}
	await scenario("C-wrong-owner", async () => {
		const other = await browser.newContext();
		const p = await other.newPage();
		try {
			await login(p, "customer-b");
			assert.equal((await api(p, "/api/bank/requests/" + bank.id)).status, 404);
			if (h1) {
				const denied = p.waitForResponse(r =>
					new URL(r.url()).pathname === "/api/bank/requests/" + bank.id &&
					r.request().method() === "GET");
				await p.goto(base + "/bank?evidence=" + bank.id);
				assert.equal((await denied).status(), 404);
				await p.getByRole("alert").filter({ hasText: "Request unavailable" }).waitFor();
				assert.equal(await p.getByRole("region", { name: "Current bank request summary" }).count(), 0);
				assert.equal(await p.getByRole("button", { name: "Submit synthetic claim", exact: true }).count(), 0);
				observations.push({ check: "wrong-owner-evidence-route", status: 404, protectedUiAbsent: true });
			}
		} finally {
			await other.close();
		}
	});
	await scenario("D-wrong-venue-grant", async () => {
		const other = await browser.newContext();
		const p = await other.newPage();
		try {
			await login(p, "staff");
			// Same staff actor has review rights at A, not arbitrary venues.
			assert.ok(
				[403, 404].includes(
					(
						await api(
							p,
							`/api/admin/venues/${config.otherVenue}/verification/requests`,
						)
					).status,
				),
			);
			if (h1) {
				const denied = await api(p, `/api/admin/venues/${config.otherVenue}/bank/requests/${bank.id}`);
				assert.ok([403, 404].includes(denied.status));
				observations.push({ check: "wrong-venue-bank-reference", status: denied.status });
			}
		} finally {
			await other.close();
		}
	});
	await scenario("O-malformed-reference", async () => {
		await page.goto(base + "/bank?evidence=malformed");
		assert.equal(
			await page
				.getByRole("button", { name: "Submit synthetic claim", exact: true })
				.count(),
			0,
		);
	});
	await scenario("P-safe-URLs", async () => {
		assert.ok(requests.length > 10);
		assert.equal(
			safeCustomerNavigation(page.url(), base),
			false,
			"intentional malformed-reference URL must remain invalid",
		);
		const reload = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === "/api/bank/requests/" + bank.id &&
				r.request().method() === "GET",
		);
		await page.goto(base + "/bank?request=" + bank.id);
		assert.equal(
			(await reload).status(),
			200,
			"protected request reload must succeed",
		);
		if (h1) {
			await page.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true }).waitFor();
		} else {
			await page
				.getByRole("link", {
					name: "View simulated booking details",
					exact: true,
				})
				.waitFor({ state: "visible" });
		}
		assert.equal(page.url(), base + "/bank?request=" + bank.id);
		assert.equal(safeCustomerNavigation(page.url(), base), true);
		assert.ok(!page.url().includes(creationBody.contentHash));
		if (h1) {
			const detail = page.getByRole("region", { name: "Owned bank request detail", exact: true });
			const link = detail.getByRole("link", { name: "Open synthetic claim and receipt workflow", exact: true });
			assert.equal(await link.count(), 1);
			assert.ok(await link.isVisible());
			const destination = ownedH1EvidenceUrl(await link.getAttribute("href"), base, bank.id);
			const path = "/api/bank/requests/" + bank.id;
			const before = requests.length;
			const [response] = await Promise.all([
				page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === "GET"),
				link.click(),
			]);
			await page.waitForURL(destination);
			assert.equal(response.status(), 200);
			const owned = await response.json();
			assert.equal(owned.id, bank.id);
			assert.equal(owned.venueId, config.venue);
			assert.equal(owned.quoteId, quote.quoteId);
			assert.equal(owned.claimId, null);
			assert.deepEqual(owned.evidence, []);
			assert.equal(owned.paymentVerified, false);
			assert.equal(owned.bookingConfirmed, false);
			await page.getByRole("region", { name: "Current bank request summary", exact: true }).waitFor();
			await page.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true }).waitFor();
			assert.equal(await page.locator('img[src^="blob:"], iframe[src^="blob:"], embed').count(), 0);
			assert.ok(requests.slice(before).every(r =>
				r.method === "GET" && !/\/(uploads|evidence|credentials)(\/|$)/.test(r.path)));
			observations.push({ check: "owned-evidence-handoff", status: 200,
				sameRequest: true, sameVenue: true, privateEvidenceAbsent: true, readOnly: true });
		}
		assert.ok(
			navigations.some((n) => n.queryKeys.includes("evidence") && n.safe),
			"actual opaque evidence handoff must be covered",
		);
		assert.ok(
			navigations.some((n) => n.expectedNegative && !n.safe),
			"malformed negative control must be covered",
		);
		assert.ok(
			navigations.every((n) => n.safe || n.expectedNegative),
			"all legitimate customer navigation must satisfy the explicit route/reference allowlist",
		);
	});
	if (!h1) {
	await scenario("A-expired-quote", async () => {
		// Independent non-overlapping session; do not alter the positive journey's purchase.
		await page.goto(base + "/rooms/" + config.room);
		await page
			.getByLabel("Customer start in venue local time")
			.fill(config.local.replace(/T\d\d:\d\d/, "T14:00"));
		await page.getByLabel("Customer duration (minutes)").fill("180");
		await page.getByLabel("Guests", { exact: true }).fill("4");
		await page
			.getByRole("button", { name: "Check inventory (no hold)", exact: true })
			.click();
		const hold = await clickResponse(
			page,
			"Create temporary inventory hold",
			"/api/holds",
		);
		await page
			.getByRole("button", {
				name: "Load applicable synthetic package choices",
				exact: true,
			})
			.click();
		await page.getByLabel("Published package").selectOption({ index: 1 });
		await page.getByLabel("Intended method (no payment)").selectOption("BANK");
		assert.equal(
			(await control({ op: "prepare-expiring-quote", holdId: hold.holdId }))
				.prepared,
			true,
		);
		const expiring = await clickResponse(
			page,
			"Calculate synthetic quotation",
			"/api/quotes",
		);
		assert.equal(expiring.state, "ISSUED");
		assert.equal(expiring.expiresAt, expiring.holdExpiresAt);
		const review = page.getByLabel(
			"I reviewed this exact synthetic revision and all three disclosures",
		);
		assert.equal(await review.isChecked(), false);
		await review.check();
		assert.equal(
			(await control({ op: "await-quote-expiry", quoteId: expiring.quoteId }))
				.serverExpired,
			true,
		);
		const rejection = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname ===
					`/api/quotes/${expiring.quoteId}/accept` &&
				r.request().method() === "POST",
		);
		await page
			.getByRole("button", {
				name: "Accept synthetic revision only",
				exact: true,
			})
			.click();
		const denied = await rejection;
		assert.equal(denied.status(), 409);
		const refusal = await denied.json();
		assert.equal(
			refusal.message,
			"Configuration changed or code already exists. Reload and compare; your draft is retained.",
		);
		await page
			.getByText(
				/Configuration changed or code already exists. Reload and compare; your draft is retained./,
			)
			.waitFor({ state: "visible" });
		const refresh = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === `/api/quotes/${expiring.quoteId}` &&
				r.request().method() === "GET",
		);
		await page
			.getByRole("button", { name: "Refresh quotation state", exact: true })
			.click();
		const fresh = await refresh;
		assert.equal(fresh.status(), 200);
		const current = await fresh.json();
		assert.equal(current.state, "EXPIRED");
		assert.equal(current.revision, expiring.revision);
		assert.equal(current.contentHash, expiring.contentHash);
		assert.equal(current.bookingConfirmed, false);
		assert.equal(current.commercialIssuance, false);
		await page
			.getByText(
				"Quotation cannot be accepted. Return to inventory and obtain a new applicable hold.",
				{ exact: true },
			)
			.waitFor({ state: "visible" });
		assert.equal(
			await page
				.getByRole("button", {
					name: "Accept synthetic revision only",
					exact: true,
				})
				.count(),
			0,
		);
		assert.equal(
			await page
				.getByRole("link", { name: "Continue to Verification", exact: true })
				.count(),
			0,
		);
		expiryObservation = await control({ op: "inspect-expired-quote" });
		assert.equal(expiryObservation.snapshotAndFinancialStateUnchanged, true);
		for (const field of [
			"quoteAcceptances",
			"quoteAcceptCommands",
			"quoteBankRequests",
			"holdBookings",
		])
			assert.equal(expiryObservation[field], 0);
		assert.equal(safeCustomerNavigation(page.url(), base), true);
		assert.ok(!page.url().includes(expiring.contentHash));
		// Existing recovery navigation only; do not run another hold-expiry scenario.
		await page.getByRole("link", { name: "Rooms", exact: true }).click();
		await page.waitForURL(base + "/rooms");
		expiryObservation = {
			...expiryObservation,
			httpStatus: 409,
			quoteState: current.state,
			recoveryPath: "/rooms",
		};
	});
	await scenario("B-invalid-expired-hold", async () => {
		const other = await browser.newContext();
		const p = await other.newPage();
		const statuses = {};
		invalidHoldObservation = { statuses };
		other.on("request", (request) =>
			assert.ok(
				["127.0.0.1", "localhost"].includes(new URL(request.url()).hostname),
				"external request prohibited",
			),
		);
		other.on("response", (response) => {
			const u = new URL(response.url());
			if (u.pathname.startsWith("/api/"))
				observations.push({
					stage: "B-invalid-expired-hold",
					method: response.request().method(),
					path: u.pathname.replace(
						/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
						"{reference}",
					),
					status: response.status(),
				});
		});
		try {
			await login(p, "customer-b");
			await p.goto(base + "/rooms/" + config.room);
			await p
				.getByLabel("Customer start in venue local time")
				.fill(config.local.replace(/T\d\d:\d\d/, "T14:00"));
			await p.getByLabel("Customer duration (minutes)").fill("180");
			await p.getByLabel("Guests", { exact: true }).fill("4");
			await p
				.getByRole("button", { name: "Check inventory (no hold)", exact: true })
				.click();
			const hold = await clickResponse(
				p,
				"Create temporary inventory hold",
				"/api/holds",
			);
			assert.equal(hold.state, "ACTIVE");
			assert.equal(hold.roomId, config.room);
			await p
				.getByRole("button", {
					name: "Load applicable synthetic package choices",
					exact: true,
				})
				.click();
			await p.getByLabel("Published package").selectOption({ index: 1 });
			await p.getByLabel("Intended method (no payment)").selectOption("BANK");
			const catalog = await json(
				p,
				`/api/venues/${config.venue}/packages?roomId=${config.room}&sessionStart=${encodeURIComponent(hold.sessionStart)}&sessionEnd=${encodeURIComponent(hold.sessionEnd)}`,
			);
			const selected = catalog.items[0];
			assert.ok(selected?.packageId && selected?.versionId);
			assert.equal(
				selected.versionId,
				await p.getByLabel("Published package").inputValue(),
			);
			const missing = "00000000-0000-4000-8000-000000000099";
			const create = {
				holdId: hold.holdId,
				expectedHoldRevision: hold.revision,
				packageId: selected.packageId,
				packageVersionId: selected.versionId,
				optionalServiceIds: [],
				paymentMethod: "BANK",
			};
			const quoteAttempt = (actor, extra) =>
				api(actor, "/api/quotes", "POST", {
					...create,
					...extra,
					idempotencyKey: crypto.randomUUID(),
				});
			statuses.missingRead = (await api(p, `/api/holds/${missing}`)).status;
			assert.equal(statuses.missingRead, 404);
			statuses.malformedIdentity = (
				await quoteAttempt(p, { holdId: "malformed" })
			).status;
			assert.equal(statuses.malformedIdentity, 400);
			statuses.missingQuoteHold = (
				await quoteAttempt(p, { holdId: missing })
			).status;
			assert.equal(statuses.missingQuoteHold, 404);
			statuses.wrongOwnerRead = (
				await api(page, `/api/holds/${hold.holdId}`)
			).status;
			assert.equal(statuses.wrongOwnerRead, 403);
			statuses.wrongOwnerRelease = (
				await api(page, `/api/holds/${hold.holdId}/release`, "POST", {
					expectedRevision: hold.revision,
					idempotencyKey: crypto.randomUUID(),
				})
			).status;
			assert.equal(statuses.wrongOwnerRelease, 403);
			statuses.wrongOwnerQuote = (await quoteAttempt(page, {})).status;
			assert.equal(statuses.wrongOwnerQuote, 404);
			statuses.venueOverride = (
				await quoteAttempt(p, { venueId: config.otherVenue })
			).status;
			assert.equal(statuses.venueOverride, 400);
			statuses.roomOverride = (
				await quoteAttempt(p, { roomId: missing })
			).status;
			assert.equal(statuses.roomOverride, 400);
			const acceptBody = () => ({
				expectedRevision: quote.revision,
				contentHash: quote.contentHash,
				disclosures: quote.disclosures,
				idempotencyKey: crypto.randomUUID(),
			});
			statuses.missingQuoteAcceptance = (
				await api(p, `/api/quotes/${missing}/accept`, "POST", acceptBody())
			).status;
			assert.equal(statuses.missingQuoteAcceptance, 404);
			statuses.wrongOwnerAcceptance = (
				await api(
					p,
					`/api/quotes/${quote.quoteId}/accept`,
					"POST",
					acceptBody(),
				)
			).status;
			assert.equal(statuses.wrongOwnerAcceptance, 404);

			assert.equal(
				(await control({ op: "prepare-invalid-hold", holdId: hold.holdId }))
					.serverExpired,
				true,
			);
			const rejection = p.waitForResponse(
				(r) =>
					new URL(r.url()).pathname === "/api/quotes" &&
					r.request().method() === "POST",
			);
			await p
				.getByRole("button", {
					name: "Calculate synthetic quotation",
					exact: true,
				})
				.click();
			const denied = await rejection;
			statuses.expiredQuote = denied.status();
			assert.equal(denied.status(), 409);
			assert.equal(
				(await denied.json()).message,
				"Configuration changed or code already exists. Reload and compare; your draft is retained.",
			);
			await p
				.getByText(
					/Configuration changed or code already exists. Reload and compare; your draft is retained./,
				)
				.waitFor({ state: "visible" });
			const reload = p.waitForResponse(
				(r) =>
					new URL(r.url()).pathname === `/api/holds/${hold.holdId}` &&
					r.request().method() === "GET",
			);
			await p
				.getByRole("button", { name: "Refresh hold state", exact: true })
				.click();
			const refreshed = await reload;
			statuses.expiredRead = refreshed.status();
			assert.equal(refreshed.status(), 200);
			const current = await refreshed.json();
			assert.equal(current.state, "EXPIRED");
			assert.ok(Date.parse(current.serverNow) >= Date.parse(current.expiresAt));
			await p
				.getByText("Hold expired", { exact: true })
				.waitFor({ state: "visible" });
			assert.equal(
				await p
					.getByRole("button", {
						name: "Calculate synthetic quotation",
						exact: true,
					})
					.count(),
				0,
			);
			assert.equal(
				await p
					.getByRole("button", {
						name: "Accept synthetic revision only",
						exact: true,
					})
					.count(),
				0,
			);
			const inquiry = p.waitForResponse(
				(r) =>
					new URL(r.url()).pathname ===
						`/api/rooms/${config.room}/availability` &&
					r.request().method() === "GET",
			);
			await p
				.getByRole("button", { name: "Check inventory (no hold)", exact: true })
				.click();
			const recovered = await inquiry;
			assert.equal(recovered.status(), 200);
			assert.equal((await recovered.json()).available, true);
			assert.equal(
				await p
					.getByRole("button", {
						name: "Create temporary inventory hold",
						exact: true,
					})
					.isEnabled(),
				true,
			);
			invalidHoldObservation = await control({ op: "inspect-invalid-hold" });
			assert.equal(
				invalidHoldObservation.scopeAndFinancialStateUnchanged,
				true,
			);
			for (const key of ["quotes", "acceptances", "requests", "bookings"])
				assert.equal(invalidHoldObservation[key], 0);
			assert.equal(safeCustomerNavigation(p.url(), base), true);
			invalidHoldObservation = {
				...invalidHoldObservation,
				statuses,
				recovery: "EXPLICIT_INQUIRY_ONLY_NO_NEW_HOLD",
			};
		} finally {
			await other.close();
		}
	});
	await scenario("F-rejected-verification", async () => {
		const customer = await browser.newContext(),
			reviewer = await browser.newContext();
		const p = await customer.newPage(),
			staffPage = await reviewer.newPage();
		const statuses = {};
		rejectionObservation = { statuses };
		for (const context of [customer, reviewer]) {
			context.on("request", (r) =>
				assert.ok(
					safeLoopbackResource(r.url(), base),
					"external or non-owned preview resource prohibited",
				),
			);
			context.on("response", (r) => {
				const u = new URL(r.url());
				if (u.pathname.startsWith("/api/"))
					observations.push({
						stage: "F-rejected-verification",
						method: r.request().method(),
						path: u.pathname.replace(
							/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
							"{reference}",
						),
						status: r.status(),
					});
			});
		}
		try {
			await login(p, "customer-b");
			await p.goto(base + "/rooms/" + config.room);
			await p
				.getByLabel("Customer start in venue local time")
				.fill(config.local.replace(/T\d\d:\d\d/, "T14:00"));
			await p.getByLabel("Customer duration (minutes)").fill("180");
			await p.getByLabel("Guests", { exact: true }).fill("4");
			await p
				.getByRole("button", { name: "Check inventory (no hold)", exact: true })
				.click();
			await clickResponse(p, "Create temporary inventory hold", "/api/holds");
			await p
				.getByRole("button", {
					name: "Load applicable synthetic package choices",
					exact: true,
				})
				.click();
			await p.getByLabel("Published package").selectOption({ index: 1 });
			await p.getByLabel("Intended method (no payment)").selectOption("BANK");
			const q = await clickResponse(
				p,
				"Calculate synthetic quotation",
				"/api/quotes",
			);
			await p
				.getByLabel(
					"I reviewed this exact synthetic revision and all three disclosures",
				)
				.check();
			await clickResponse(
				p,
				"Accept synthetic revision only",
				`/api/quotes/${q.quoteId}/accept`,
			);
			await p
				.getByRole("link", { name: "Continue to Verification", exact: true })
				.click();
			await p.waitForURL(base + "/verification?quote=" + q.quoteId);
			const consent = p.getByLabel(
				"I consent to this versioned synthetic test processing; no real documents",
			);
			assert.equal(await consent.isChecked(), false);
			await p
				.getByLabel("Operator-provided synthetic policy version")
				.fill("SYNTHETIC-V1");
			await consent.check();
			const r = await clickResponse(
				p,
				"Start synthetic request",
				"/api/verification/requests",
			);
			await p
				.getByLabel("Approved synthetic image only (JPEG/PNG, 5 MiB, 20 MP)")
				.setInputFiles(`${run}/identity.png`);
			const complete = p.waitForResponse(
				(r) =>
					/\/api\/verification\/uploads\/[^/]+\/complete$/.test(
						new URL(r.url()).pathname,
					) && r.request().method() === "POST",
			);
			await p
				.getByRole("button", {
					name: "Upload and request simulated scan",
					exact: true,
				})
				.click();
			assert.equal((await complete).status(), 200);
			await p
				.getByRole("button", {
					name: "Upload and request simulated scan",
					exact: true,
				})
				.waitFor();
			assert.equal(
				(await control({ op: "prepare-rejection-review", id: r.id })).state,
				"REVIEW_REQUIRED",
			);
			const pending = await json(p, `/api/verification/requests/${r.id}`);
			assert.equal(pending.state, "REVIEW_REQUIRED");
			assert.equal(pending.history.length, 0);
			assert.equal(pending.evidence[0].state, "READY");
			const path = `/api/admin/venues/${config.venue}/verification/requests/${r.id}`;
			const decision = {
				revision: pending.revision,
				verdict: "REJECTED",
				reason: "CORRECTION_REQUIRED",
				commandKey: crypto.randomUUID(),
			};
			statuses.unprivilegedDecision = (
				await api(p, path + "/decisions", "POST", decision)
			).status;
			assert.equal(statuses.unprivilegedDecision, 403);
			statuses.customerEvidence = (
				await api(p, path + "/evidence/" + pending.evidence[0].id)
			).status;
			assert.equal(statuses.customerEvidence, 403);
			statuses.wrongOwnerRead = (
				await api(page, `/api/verification/requests/${r.id}`)
			).status;
			assert.equal(statuses.wrongOwnerRead, 404);
			await login(staffPage, "staff");
			statuses.wrongVenueDecision = (
				await api(
					staffPage,
					`/api/admin/venues/${config.otherVenue}/verification/requests/${r.id}/decisions`,
					"POST",
					{ ...decision, commandKey: crypto.randomUUID() },
				)
			).status;
			assert.equal(statuses.wrongVenueDecision, 403);
			await staffPage.goto(base + "/admin/verification?venue=" + config.venue);
			await staffPage.getByLabel("Existing request ID").fill(r.id);
			await clickResponse(
				staffPage,
				"Refresh current request and history",
				path,
				"GET",
			);
			const record = staffPage.getByRole("button", {
				name: "Record exact-revision manual decision",
				exact: true,
			});
			assert.equal(await record.isDisabled(), true);
			assert.equal(
				(await json(p, `/api/verification/requests/${r.id}`)).history.length,
				0,
			);
			const preview = staffPage.waitForResponse(
				(v) =>
					new URL(v.url()).pathname ===
					path + "/evidence/" + pending.evidence[0].id,
			);
			await staffPage
				.getByRole("button", { name: "Open private NIC preview", exact: true })
				.click();
			const image = await preview;
			assert.equal(image.status(), 200);
			assert.match(image.headers()["content-type"], /image\/png/);
			assert.equal(image.headers()["cache-control"], "no-store");
			await staffPage
				.getByRole("img", {
					name: "Private explicitly synthetic fixture for current manual review",
					exact: true,
				})
				.waitFor({ state: "visible" });
			const previewSource = new URL(
				await staffPage
					.getByRole("img", {
						name: "Private explicitly synthetic fixture for current manual review",
						exact: true,
					})
					.getAttribute("src"),
			);
			assert.equal(previewSource.protocol, "blob:");
			assert.equal(previewSource.origin, base);
			statuses.privatePreview = 200;
			await staffPage
				.getByRole("button", { name: "Close private preview", exact: true })
				.click();
			await staffPage
				.getByLabel("Manual digital test decision")
				.selectOption("REJECTED");
			await staffPage
				.getByLabel("Attributable decision reason")
				.selectOption("CORRECTION_REQUIRED");
			const reviewed = staffPage.getByLabel(
				"I manually reviewed the current synthetic evidence, booker match and 18+ policy; this does not approve admission",
			);
			assert.equal(await reviewed.isChecked(), false);
			await reviewed.check();
			const rejected = await clickResponse(
				staffPage,
				"Record exact-revision manual decision",
				path + "/decisions",
			);
			statuses.explicitDecision = 200;
			assert.equal(rejected.state, "REJECTED");
			assert.equal(rejected.history.length, 1);
			assert.equal(rejected.history[0].verdict, "REJECTED");
			await p.getByLabel("Existing request ID").fill(r.id);
			await clickResponse(
				p,
				"Refresh current request and history",
				`/api/verification/requests/${r.id}`,
				"GET",
			);
			await p
				.getByText("Server status: REJECTED", { exact: true })
				.waitFor({ state: "visible" });
			await p
				.getByText(
					"The server reports a rejected test decision. Review the recorded decision history. A correction needs the current quotation, policy and fresh consent.",
					{ exact: true },
				)
				.waitFor({ state: "visible" });
			const readiness = await json(
				p,
				`/api/bank/requests/readiness?quote=${q.quoteId}`,
			);
			assert.equal(readiness.eligible, false);
			assert.equal(readiness.reason, "VERIFICATION_REQUIRED");
			assert.equal(readiness.verificationReadiness, "REQUIRED");
			statuses.bankBypass = (
				await api(p, "/api/bank/requests", "POST", {
					quoteId: q.quoteId,
					quoteRevision: q.revision,
					contentHash: q.contentHash,
					commandKey: crypto.randomUUID(),
				})
			).status;
			assert.equal(statuses.bankBypass, 409);
			assert.equal(
				await p
					.getByRole("link", { name: "Continue to Bank Request", exact: true })
					.count(),
				0,
			);
			await consent.uncheck();
			assert.equal(
				await p
					.getByRole("button", {
						name: "Create correction generation with current consent",
						exact: true,
					})
					.isDisabled(),
				true,
			);
			rejectionObservation = await control({ op: "inspect-rejection" });
			assert.equal(rejectionObservation.state, "REJECTED");
			assert.equal(rejectionObservation.bankEligible, false);
			assert.equal(rejectionObservation.protectedStateUnchanged, true);
			assert.equal(rejectionObservation.bankRequests, 0);
			assert.equal(rejectionObservation.bookings, 0);
			assert.equal(safeCustomerNavigation(p.url(), base), true);
			assert.ok(!p.url().includes(q.contentHash));
			rejectionObservation = {
				...rejectionObservation,
				statuses,
				customerState: "REJECTED",
				recovery: "CORRECTION_DISPLAYED_FRESH_CONSENT_REQUIRED_NOT_SUBMITTED",
			};
		} finally {
			await customer.close();
			await reviewer.close();
		}
	});
	}
	await scenario("G-expired-session", async () => {
        const expired=await control({op:"expire-session"});assert.equal(expired.sessionDeadlineExpired,true);assert.ok(expired.expiredSessions>0);
		assert.equal((await api(page, "/api/accounts/me")).status, 401);
        assert.equal((await api(page, `/api/bank/requests/${bank.id}`)).status,401);
	});
 if (!h1) await expiredSessionRecovery({page,base,bank,scenario,diagnostics,login,api});
	}
} catch (failure) {
	assertionFailure = {
		name: failure.name,
		message:
			failure.name === "AssertionError"
				? "Harness assertion failed; inspect source location"
				: "Harness operation failed",
		stack: String(failure.stack)
			.split("\n")
			.filter((line) => /synthetic-e2e\.mjs:|extension-reservation-e2e\.mjs:|release1-booking-e2e\.mjs:|release1-recovery-e2e\.mjs:/.test(line))
			.slice(0, 4),
	};
	results.push({
		id: stage,
		status: "FAIL",
		reason:
			"Real harness assertion/startup failure; raw private diagnostics not published",
		apiFailure,
		assertionFailure,
		calendarObservation,
		financeObservation,
		expiryObservation,
		invalidHoldObservation,
		rejectionObservation,
		uploadObservation,
	});
	process.exitCode = 1;
} finally {
	if (!supervised) for (const id of planned)
		if (!results.some((r) => r.id === id))
			notRun(
				id,
				"Stopped after failure or missing independent safe scenario control",
			);
	await writeFile(
		new URL("observations.json", diagnostics),
		JSON.stringify({
			stage,
			assertionFailure,
			calendarObservation,
		financeObservation,
			expiryObservation,
			invalidHoldObservation,
			rejectionObservation,
			uploadObservation,
			observations,
			navigations,
			finalPath: activePage
				? new URL(activePage.url()).pathname.replace(
						/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
						"{reference}",
					)
				: undefined,
		}),
		{ mode: 0o600 },
	);
	if (browser) await browser.close();
	if (faultTransport) await faultTransport.close();
	if (frontend && frontend.exitCode === null) {
		frontend.kill("SIGTERM");
		await Promise.race([
			once(frontend, "exit"),
			new Promise((r) => setTimeout(r, 3000)),
		]);
		if (frontend.exitCode === null) {
			frontend.kill("SIGKILL");
			await once(frontend, "exit");
		}
	}
	await writeFile(
		`${run}/result.json`,
		JSON.stringify({
			syntheticOnly: true,
            mode,
            supervisedOutcome,
            screenshots,
            calendarObservation,
			results,
			environment: {
				backendPid: config.backendPid,
				frontendPid: frontend?.pid,
				browserControllerPid: process.pid,
				ports: [...config.ports, port],
			},
			requestObservations: requests,
			counts: Object.fromEntries(
				["PASS", "FAIL", "NOT_RUN"].map((s) => [
					s,
					results.filter((r) => r.status === s).length,
				]),
			),
		}),
	);
}

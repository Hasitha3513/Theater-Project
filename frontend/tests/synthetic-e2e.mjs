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
assert.ok(["campaign", "h1", "supervised-h1", "cancellation", "extension-funding", "reschedule-refund", "package-booking", "customer-turnover"].includes(mode), "unknown browser journey mode");
const operational = mode === "customer-turnover";
const cancellation = mode === "cancellation";
const funding = mode === "extension-funding";
const packageBooking = mode === "package-booking";
const twoJourneys = mode === "reschedule-refund";
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
const cancellationPlan = [
	"startup", "cancellation-owner-venue-denials", "cancellation-customer",
	"cancellation-replay-persistence", "cancellation-staff-view",
];
const fundingPlan = ["startup", "funding-scope-denials", "funding-ui-recovery", "funding-replay-expiry"];
const planned = operational ? ['startup','operational-customer-quote','operational-verification-bank','operational-independent-finance','operational-roster-eligibility','operational-staff-session','operational-independent-turnover','operational-persistence'] : packageBooking ? ["startup", "package-publish", "package-customer-quote", "package-history"] : twoJourneys ? ["startup", "two-reschedule", "two-refund"] : funding ? fundingPlan : cancellation ? cancellationPlan : h1 ? [
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
 if(!result.controlOk)await writeFile(new URL("last-control.json",diagnostics),JSON.stringify({changedTables:result.changedTables,newAuditActions:result.newAuditActions,fixtureError:result.fixtureError,fixtureLine:result.fixtureLine}),{mode:0o600});
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
async function runAdminQualification({ base, roles, test, respond }) {
	const qa = config.adminQa;
	const { customer, staff, administrator, publisher, wrong, ungranted, revoked } = roles;
	const venue = `/api/admin/venues/${config.venue}`;
	const room = `${venue}/rooms/${config.room}`;
	const pkg = `${venue}/packages/${qa.packageId}`;
	const detail = `${venue}/bookings/${qa.bookingId}`;
	const bank = `${venue}/bank/requests/${qa.requestId}`;
	const read = async (page, path) => {
		const result = await api(page, path);
		assert.equal(result.status, 200);
		return result.value;
	};
	const show = async (page, route, heading) => {
		await page.goto(base + route);
		await page.getByRole("heading", { name: heading, exact: true }).waitFor();
	};
	const venueBody = v => ({
		code: v.code, displayName: "Synthetic Venue Admin QA", timezone: v.timezone,
		cleaningBufferMinutes: v.cleaningBufferMinutes,
		postCloseCleaningApproved: v.postCloseCleaningApproved,
		operationalApprovalReference: v.operationalApprovalReference ?? "",
		enabled: v.enabled, windows: v.windows, expectedVersion: v.version,
	});
	const bookingRoute = `/admin/bookings/venues/${config.venue}/${qa.bookingId}`;
	await test("T-CN-A03/venue", administrator, async () => {
		await show(administrator, "/admin/venues", "Venue configuration");
		await administrator.getByRole("heading", { name: "Synthetic Venue A", exact: true }).waitFor();
		const old = await read(administrator, venue);
		await administrator.getByRole("button", { name: "Edit venue", exact: true }).click();
		await administrator.getByRole("dialog").getByLabel("Name", { exact: true }).fill(venueBody(old).displayName);
		const updated = await clickResponse(administrator, "Save venue", venue, "PUT");
		assert.equal(updated.version, old.version + 1);
		await administrator.reload();
		await administrator.getByRole("heading", { name: updated.displayName, exact: true }).waitFor();
		assert.equal((await read(administrator, venue)).version, updated.version);
		return "Venue metadata persisted at the next version";
	});
	await test("T-CN-A04/room", administrator, async () => {
		await show(administrator, "/admin/rooms", "Room configuration");
		await administrator.getByLabel("Venue", { exact: true }).selectOption(config.venue);
		await administrator.getByRole("heading", { name: "Synthetic room", exact: true }).waitFor();
		const old = await read(administrator, room);
		assert.equal(old.venueId, config.venue);
		const payload = { code: old.code, name: old.name, description: old.description ?? "",
			capacity: 0, facilities: old.facilities, images: old.images, enabled: old.enabled, expectedVersion: old.version };
		assert.equal((await api(administrator, room, "PUT", payload)).status, 400);
		assert.equal((await read(administrator, room)).version, old.version);
		const saved = await api(administrator, room, "PUT", { ...payload, capacity: old.capacity + 1 });
		assert.equal(saved.status, 200);
		assert.equal((await read(administrator, room)).version, old.version + 1);
		assert.equal((await read(administrator, room)).capacity, old.capacity + 1);
		return "Scoped room update persisted; invalid capacity was rejected";
	});
	await test("T-CN-A12/package", administrator, async () => {
		await show(administrator, "/admin/settings/packages", "Package drafts");
		const before = await read(publisher, pkg + "/publications");
		assert.equal(before.items.length, 1);
		const previous = before.items[0];
		const draft = await read(administrator, pkg);
		const body = { ...qa.draft, description: "SYNTHETIC Admin QA package revision", expectedRevision: draft.revision };
		assert.equal((await api(wrong, pkg + "/draft", "PUT", body)).status, 403);
		const saved = await api(administrator, pkg + "/draft", "PUT", body);
		assert.equal(saved.status, 200);
		assert.equal(saved.value.revision, draft.revision + 1);
		const publication = { ...qa.publication, expectedRevision: saved.value.revision,
			replacesPublicationId: previous.publicationId, replacesRevision: previous.revision };
		assert.equal((await api(administrator, pkg + "/publication-preview", "POST", publication)).status, 403);
		const preview = await api(publisher, pkg + "/publication-preview", "POST", publication);
		assert.equal(preview.status, 200);
		const published = await api(publisher, pkg + "/publications", "POST", { ...publication, contentHash: preview.value.contentHash });
		assert.equal(published.status, 200);
		assert.notEqual(published.value.versionId, previous.versionId);
		const history = (await read(publisher, pkg + "/publications")).items;
		assert.ok(history.some(item => item.versionId === previous.versionId && item.state === "INACTIVE"));
		assert.ok(history.some(item => item.versionId === published.value.versionId && item.state === "ACTIVE"));
		return "Separate editor and publisher produced a new version and preserved history";
	});
	await test("T-CN-A02/details", staff, async () => {
		await staff.goto(base + "/admin/bookings");
		await staff.getByLabel("Assigned venue reference").fill(config.venue);
		await staff.getByLabel("Exact booking reference (optional)").fill(qa.bookingId);
		const response = respond(staff, venue + "/bookings");
		await staff.getByRole("button", { name: "Apply booking filters", exact: true }).click();
		const list = await response;
		assert.equal(list.status(), 200);
		assert.deepEqual((await list.json()).items.map(item => item.bookingId), [qa.bookingId]);
		await show(staff, bookingRoute, "Staff booking details");
		assert.equal((await read(staff, detail)).booking.bookingId, qa.bookingId);
		return "Scoped search and persisted detail matched";
	});
	await test("T-CN-A02/revision", staff, async () => {
		await show(staff, bookingRoute, "Staff booking details");
		const { booking } = await read(staff, detail);
		assert.equal(booking.revision, 1);
		assert.equal(booking.roomId, config.room);
		assert.equal(booking.venueId, config.venue);
		assert.equal(booking.state, "SIMULATED_CONFIRMED");
		return "Persisted synthetic booking revision and scope matched";
	});
	await test("T-CN-A13/payment", staff, async () => {
		const before = await control({ op: "admin-financial" });
		for (const key of ["credits", "allocations", "verified", "credentials"]) assert.equal(before.effects[key], 0);
		await show(staff, `/admin/bank/venues/${config.venue}/requests/${qa.requestId}`, "Bank review details");
		const request = await read(staff, bank);
		assert.equal(request.requestState, "OPEN");
		assert.equal(request.reviewState, "PENDING");
		assert.equal(request.financial.syntheticBookingStatus, "NOT_CONFIRMED");
		assert.equal(request.financial.syntheticFundingStatus, "UNFUNDED");
		assert.equal(request.bookingConfirmed, false);
		assert.equal(request.paymentVerified, false);
		assert.equal((await api(ungranted, bank)).status, 403);
		assert.deepEqual((await control({ op: "admin-financial" })).effects, before.effects);
		return "Scoped payment view stayed pending and unfunded without side effects";
	});
	await test("T-CN-A06/staff", staff, async () => {
		await show(staff, bookingRoute, "Staff booking details");
		await read(staff, detail);
		assert.equal((await api(ungranted, detail)).status, 403);
		return "Venue membership alone did not grant booking access";
	});
	await test("T-CN-A06/wrong-venue", wrong, async () => {
		await show(wrong, "/admin/venues", "Venue configuration");
		await read(wrong, `/api/admin/venues/${config.otherVenue}`);
		assert.equal((await api(wrong, venue)).status, 403);
		assert.equal((await api(wrong, detail)).status, 403);
		assert.equal((await api(wrong, room)).status, 403);
		return "Venue B staff could read B but could not read Venue A";
	});
	await test("T-CN-A06/customer", customer, async () => {
		await show(customer, bookingRoute, "Staff booking details");
		assert.equal((await api(customer, detail)).status, 403);
		return "The authenticated booking owner was denied the staff route";
	});
	await test("T-CN-A06/revocation", revoked, async () => {
		await show(revoked, bookingRoute, "Staff booking details");
		await read(revoked, detail);
		await control({ op: "admin-revoke" });
		assert.equal((await api(revoked, detail)).status, 401);
		await read(staff, detail);
		return "Only the dedicated revoked session lost access";
	});
	await test("T-CN-A06/csrf", administrator, async () => {
		const before = await read(administrator, venue);
		const audit = await control({ op: "admin-audit" });
		assert.equal((await api(administrator, venue, "PUT", venueBody(before), false)).status, 403);
		assert.equal((await read(administrator, venue)).version, before.version);
		assert.equal((await control({ op: "admin-audit" })).writes, audit.writes);
		return "Missing CSRF was denied without a write or success audit";
	});
	await test("T-CN-A11/audit", administrator, async () => {
		await show(administrator, "/admin/venues", "Venue configuration");
		assert.equal((await control({ op: "admin-audit" })).writes, 1);
		return "Exactly one successful venue update was attributed to the scoped administrator";
	});
}

async function supervisedReadFailure(page, base, path, kind) {
	const allowed = kind === "catalog"
		? /^\/api\/venues\/[a-f0-9-]{36}\/packages$/
		: kind === "staff-bookings" && /^\/api\/admin\/venues\/[a-f0-9-]{36}\/bookings$/;
	assert.ok(allowed?.test(path) && new URL(base).hostname === "127.0.0.1");
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

async function runCancellationQualification(base) {
	const booking = config.cancellationBooking;
	assert.match(booking, /^[0-9a-f-]{36}$/);
	const customerPath = `/api/bookings/${booking}/cancellation`;
	const commandPath = `${customerPath}s`;
	const staffPath = (venue) => `/api/admin/venues/${venue}/bookings/${booking}`;
	const origins = supervisedOrigins(config, base);
	const open = async () => {
		const context = await browser.newContext({ serviceWorkers: "block" });
		await context.route("**/*", (route) => origins.has(new URL(route.request().url()).origin)
			? route.continue() : route.abort());
		return context.newPage();
	};
	const customer = await open();
	const other = await open();
	const staff = await open();
	let submitted = 0;
	customer.on("request", (request) => {
		if (new URL(request.url()).pathname === commandPath && request.method() === "POST") submitted++;
	});
	const click = (page, name) => page.getByRole("button", { name, exact: true }).click();
	const inspect = async (op, commandKey) => {
		const { controlOk, ...evidence } = await control({ op, commandKey });
		observations.push({ stage, ...evidence });
		return evidence;
	};
	let command, committed;
	try {
		await scenario("cancellation-owner-venue-denials", async () => {
			const ownerId = await login(customer, "customer-a");
			const otherId = await login(other, "customer-b");
			const staffId = await login(staff, "staff");
			assert.equal(new Set([ownerId, otherId, staffId]).size, 3);
			assert.equal((await api(other, customerPath)).status, 404);
			assert.equal((await api(staff, `${staffPath(config.otherVenue)}/cancellation`)).status, 404);
			const current = await json(customer, customerPath);
			assert.equal(current.eligible, true);
			const stale = {
				reason: "Synthetic stale cancellation", commandKey: crypto.randomUUID(),
				expectedPurchasedRevision: current.purchasedRevision + 1,
				expectedLifecycleVersion: current.lifecycleVersion,
			};
			const staleResponse = await api(customer, commandPath, "POST", stale);
			observations.push({
				check: "stale-cancellation", status: staleResponse.status,
				parsedResponseBody: staleResponse.value === undefined ? "ABSENT_OR_NOT_JSON" : "REDACTED_JSON",
				origin: base, contentType: "application/json", csrfRequested: true,
				expectedPurchasedRevision: stale.expectedPurchasedRevision,
				actualPurchasedRevision: current.purchasedRevision,
				expectedLifecycleVersion: stale.expectedLifecycleVersion,
			});
			assert.equal(staleResponse.status, 409);
			assert.equal((await api(other, commandPath, "POST", stale)).status, 404);
			assert.equal((await api(customer, commandPath, "POST", stale, false)).status, 403);
			assert.deepEqual(await inspect("cancellation-before"), {
				state: "SIMULATED_CONFIRMED", blocking: true, audits: 0, obligations: 0,
				protectedStateUnchanged: true,
			});
		});
		await scenario("cancellation-customer", async () => {
			const beforeSubmit = submitted;
			await customer.goto(`${base}/bookings/${booking}`);
			await click(customer, "Refresh booking history");
			await customer.getByRole("heading", { name: "Booking cancellation", exact: true }).waitFor();
			await click(customer, "Check Cancellation Eligibility");
			const confirm = customer.getByRole("button", { name: "Confirm synthetic cancellation", exact: true });
			await confirm.waitFor();
			assert.equal(await confirm.isEnabled(), false);
			await customer.getByLabel("Cancellation reason").fill("Synthetic pre-start cancellation");
			await customer.getByRole("checkbox", { name: /I understand the synthetic cancellation/ }).check();
			// Let the real backend commit once, then replace only the browser-visible response.
			const intercept = base + commandPath;
			await customer.route(intercept, async (route) => {
				assert.equal(route.request().method(), "POST");
				command = route.request().postDataJSON();
				const response = await route.fetch({ maxRedirects: 0, maxRetries: 0 });
				committed = { status: response.status(), value: await response.json() };
				await route.abort("failed");
			}, { times: 1 });
			const lost = customer.waitForEvent("requestfailed", (request) =>
				request.url() === intercept && request.method() === "POST");
			await confirm.click();
			await lost;
			assert.equal(committed?.status, 200);
			assert.equal(committed.value.state, "SIMULATED_CANCELLED");
			assert.equal(committed.value.realRepayment, false);
			assert.equal(committed.value.refundStatus, "RESERVED_REVIEW_REQUIRED");
			assert.equal(committed.value.inventoryReleased, true);
			await customer.getByRole("alert").filter({ hasText: "Cancellation outcome is uncertain" }).waitFor();
			await click(customer, "Check Cancellation Eligibility");
			await customer.getByRole("heading", { name: "Refund obligation — separate from repayment" }).waitFor();
			await customer.getByText("Reserved for financial review.", { exact: true }).waitFor();
			assert.equal(await confirm.count(), 0);
			assert.equal(submitted, beforeSubmit + 1, "recovery must not automatically resubmit");
		});
		await scenario("cancellation-replay-persistence", async () => {
			const stable = await inspect("cancellation-committed", command.commandKey);
			assert.deepEqual(stable, {
				state: "SIMULATED_CANCELLED", blocking: false, audits: 1, obligations: 1,
				protectedStateUnchanged: true,
			});
			assert.deepEqual(await api(customer, commandPath, "POST", command), committed);
			assert.equal((await api(customer, commandPath, "POST", { ...command, reason: "Changed reason" })).status, 409);
			assert.deepEqual(await inspect("cancellation-committed", command.commandKey), stable);
		});
		await scenario("cancellation-staff-view", async () => {
			await staff.goto(`${base}/admin/bookings/venues/${config.venue}/${booking}`);
			await click(staff, "Reload booking detail");
			const detail = staff.getByRole("region", { name: "Authorized staff booking detail" });
			await detail.getByText("SIMULATED_CANCELLED / SYNTHETIC ONLY", { exact: true }).waitFor();
			assert.equal(await detail.getByText("SIMULATED_CONFIRMED / SYNTHETIC ONLY", { exact: true }).count(), 0);
			await click(staff, "Check Cancellation Eligibility");
			await staff.getByText(/Current purchased block released; physical readiness remains/).waitFor();
			assert.equal((await inspect("cancellation-committed", command.commandKey)).audits, 1);
		});
	} finally {
		await Promise.all([customer.context().close(), other.context().close(), staff.context().close()]);
	}
}

async function runFundingQualification(base) {
	const booking = config.fundingBooking;
	assert.match(booking, /^[0-9a-f-]{36}$/);
	const origins = supervisedOrigins(config, base);
	const open = async () => {
		const context = await browser.newContext({ serviceWorkers: "block" });
		await context.route("**/*", (route) => origins.has(new URL(route.request().url()).origin)
			? route.continue() : route.abort());
		return context.newPage();
	};
	const owner = await open(), other = await open();
	let path, panel, command, committed, posts = 0;
	const inspect = async (op, commandKey) => {
		const result = await control({ op, commandKey });
		const { controlOk, ...evidence } = result;
		assert.equal(controlOk, true);
		observations.push({ stage, ...evidence });
		return evidence;
	};
	try {
		await scenario("funding-scope-denials", async () => {
			const ownerId = await login(owner, "customer-a");
			assert.notEqual(await login(other, "customer-b"), ownerId);
			await owner.goto(`${base}/bookings/${booking}`);
			await owner.getByRole("button", { name: "Refresh booking history", exact: true }).click();
			const reload = owner.getByRole("button", { name: "Reload Extension Reservation", exact: true });
			await reload.waitFor();
			const { reservation, reservationKey } = await control({ op: "funding-prepare" });
			assert.equal(reservation.state, "ACTIVE");
			path = `/api/bookings/${booking}/extension-reservations/${reservation.reservationId}/funding-request`;
			const reference = { commandKey: reservationKey, reservationId: reservation.reservationId };
			// Seed only the opaque reference for the reservation created by the synthetic fixture.
			await owner.evaluate(({ key, reference }) => sessionStorage.setItem(key, JSON.stringify(reference)),
				{ key: `cinenest.extension.reference.v1:${ownerId}:${booking}`, reference });
			await reload.click();
			panel = owner.getByRole("region", { name: "Synthetic extension funding request", exact: true });
			await panel.waitFor();
			const candidate = {
				commandKey: crypto.randomUUID(), venueId: config.venue, purchasedRevision: reservation.purchasedRevision,
				reservationRevision: reservation.proposalRevision, packageVersionId: reservation.packageVersionId,
				termsVersion: reservation.termsVersion, amountMinor: reservation.additionalMinor,
				currency: reservation.currency, acknowledged: true,
			};
			assert.equal((await api(other, path, "POST", candidate)).status, 404);
			assert.equal((await api(owner, path, "POST", { ...candidate, venueId: config.otherVenue })).status, 404);
			assert.equal((await api(owner, path, "POST", candidate, false)).status, 403);
			assert.equal((await api(other, path)).status, 404);
			assert.deepEqual(await inspect("funding-before"), { requests: 0, audits: 0, protectedStateUnchanged: true });
		});
		await scenario("funding-ui-recovery", async () => {
			owner.on("request", (request) => {
				if (new URL(request.url()).pathname === path && request.method() === "POST") posts++;
			});
			const create = panel.getByRole("button", { name: "Create Synthetic Funding Request", exact: true });
			assert.equal(await create.isDisabled(), true);
			await panel.getByRole("checkbox").check();
			const routed = Promise.withResolvers();
			await owner.route(base + path, async (route) => {
				try {
					assert.equal(route.request().method(), "POST");
					command = route.request().postDataJSON();
					await panel.getByText("Checking funding request status.", { exact: true }).waitFor();
					const response = await route.fetch({ maxRedirects: 0, maxRetries: 0 });
					assert.equal(response.status(), 200);
					committed = await response.json();
					assert.equal(committed.funded, false);
					await route.abort("failed");
					routed.resolve();
				} catch (error) {
					await route.abort("failed").catch(() => {});
					routed.reject(error);
				}
			}, { times: 1 });
			await Promise.all([routed.promise, create.click()]);
			await panel.getByText(/Request status could not be verified.*original reference/).waitFor();
			const recoveryPath = `${path}/commands/${command.commandKey}`;
			const recovered = owner.waitForResponse((r) => new URL(r.url()).pathname === recoveryPath && r.request().method() === "GET");
			await panel.getByRole("button", { name: "Reload Funding Request", exact: true }).click();
			const response = await recovered;
			assert.equal(response.status(), 200);
			assert.equal((await response.json()).requestId, committed.requestId);
			await panel.getByText(/PENDING · UNFUNDED/).waitFor();
			assert.equal(posts, 1);
			for (const name of ["packageVersionId", "purchasedRevision", "reservationRevision", "termsVersion", "amountMinor", "currency", "commandKey"])
				assert.equal(committed[name], command[name]);
			assert.deepEqual(await inspect("funding-committed", command.commandKey), { requests: 1, audits: 1, protectedStateUnchanged: true });
		});
		await scenario("funding-replay-expiry", async () => {
			const recovery = `${path}/commands/${command.commandKey}`;
			assert.equal((await api(other, recovery)).status, 404);
			assert.equal((await json(owner, path)).requestId, committed.requestId);
			const replay = await api(owner, path, "POST", command);
			assert.equal(replay.status, 200);
			assert.equal(replay.value.requestId, committed.requestId);
			assert.equal(replay.value.funded, false);
			assert.equal((await api(owner, path, "POST", { ...command, amountMinor: (BigInt(command.amountMinor) + 1n).toString() })).status, 409);
			const until = Date.now() + 52_000;
			let status;
			do {
				status = await json(owner, recovery);
				if (status.state === "EXPIRED") break;
				await new Promise((resolve) => setTimeout(resolve, 500));
			} while (Date.now() < until);
			assert.equal(status.state, "EXPIRED");
			assert.equal(status.requestId, committed.requestId);
			assert.equal(status.funded, false);
			assert.equal((await api(owner, path, "POST", { ...command, commandKey: crypto.randomUUID() })).status, 409);
			await panel.getByRole("button", { name: "Reload Funding Request", exact: true }).click();
			await panel.getByText(/EXPIRED · UNFUNDED/).waitFor();
			assert.deepEqual(await inspect("funding-expired", command.commandKey), { requests: 1, audits: 1, protectedStateUnchanged: true });
		});
	} finally {
		await Promise.all([owner.context().close(), other.context().close()]);
	}
}

async function runTwoJourneys(base) {
	const origins = supervisedOrigins(config, base);
	const attempt = async (id, body) => {
		const opened = [];
		const open = async (subject) => {
			const context = await browser.newContext({ serviceWorkers: "block" });
			opened.push(context);
			await context.route("**/*", (route) => origins.has(new URL(route.request().url()).origin)
				? route.continue() : route.abort());
			const page = await context.newPage();
			await login(page, subject);
			return page;
		};
		try {
			await scenario(id, () => body(open));
			return true;
		} catch (error) {
			const diagnostic = new URL("last-control.json", diagnostics);
			const fixture = await readFile(diagnostic, "utf8").then(JSON.parse).catch(() => undefined);
			await unlink(diagnostic).catch(() => {});
			observations.push({ stage: id, status: "FAIL", type: error.name, fixture,
				location: error.stack?.split("\n").find(line => line.includes("synthetic-e2e.mjs"))?.trim() });
			results.push({ id, status: "FAIL", reason: "Inspect the bounded journey assertion location" });
			return false;
		} finally {
			await Promise.all(opened.map(context => context.close()));
		}
	};
	const click = (page, name) => page.getByRole("button", { name, exact: true }).click();
	const mutation = async (page, name, path, suffix = false) => {
		const response = page.waitForResponse(r => {
			const pathname = new URL(r.url()).pathname;
			return r.request().method() === "POST" && (suffix ? pathname.endsWith(path) : pathname === path);
		});
		await click(page, name);
		const observed = await response;
		observations.push({ stage, status: observed.status(), action: name });
		assert.equal(observed.status(), 200);
		return { body: observed.request().postDataJSON(), data: await observed.json() };
	};
	const inspect = async (op, expected) => {
		const { controlOk, ...actual } = await control({ op });
		assert.equal(controlOk, true);
		assert.deepEqual(actual, expected);
		observations.push({ stage, ...actual });
	};
	const reschedule = await attempt("two-reschedule", async (open) => {
		const { booking, targetLocal } = await control({ op: "two-reschedule-prepare" });
		const owner = await open("customer-a"), other = await open("customer-b"), staff = await open("staff");
		const root = `/api/bookings/${booking}`;
		const path = root + "/amendments";
		const staffPath = venue => `/api/admin/venues/${venue}/bookings/${booking}`;
		await owner.goto(`${base}/bookings/${booking}`);
		await click(owner, "Refresh booking history");
		await owner.getByText("History: retained immutable revision 1,", { exact: false }).waitFor();
		await owner.getByLabel("Replacement session start (venue local)", { exact: true }).fill(targetLocal);
		const { data: proposal } = await mutation(owner, "Prepare synthetic proposal", path);
		assert.equal(proposal.state, "PROPOSED");
		await owner.getByText(/Financial difference: LKR 0.00/).waitFor();
		const confirmPath = `${path}/${proposal.id}/confirm`;
		const stale = { expectedBookingRevision: 2, expectedAmendmentRevision: proposal.revision,
			proposalHash: proposal.proposalHash, commandKey: crypto.randomUUID(), reason: "Synthetic stale revision" };
		assert.equal((await api(owner, confirmPath, "POST", { ...stale, expectedBookingRevision: 1 }, false)).status, 403);
		assert.equal((await api(other, confirmPath, "POST", { ...stale, expectedBookingRevision: 1 })).status, 404);
		assert.equal((await api(owner, confirmPath, "POST", stale)).status, 409);
		assert.equal((await api(staff, staffPath(config.otherVenue))).status, 404);
		assert.equal(await owner.getByRole("button", { name: "Confirm synthetic reschedule", exact: true }).isDisabled(), true);
		await owner.getByLabel(/I accept this exact replacement interval/).check();
		const confirmed = await mutation(owner, "Confirm synthetic reschedule", confirmPath);
		assert.equal(confirmed.data.state, "CONFIRMED");
		assert.equal((await api(owner, confirmPath, "POST", confirmed.body)).value.state, "CONFIRMED");
		assert.equal((await api(owner, confirmPath, "POST", { ...confirmed.body, reason: "Synthetic changed replay" })).status, 409);
		const updated = await json(owner, root);
		assert.equal(updated.revision, 2);
		assert.equal(updated.purchase.sessionStart, proposal.proposed.sessionStart);
		await click(owner, "Refresh booking history");
		await owner.getByText("History: retained immutable revision 2,", { exact: false }).waitFor();
		await staff.goto(`${base}/admin/bookings/venues/${config.venue}/${booking}`);
		await click(staff, "Reload booking detail");
		const details = staff.getByRole("region", { name: "Authorized staff booking detail" });
		await details.getByText("Server revision", { exact: true }).waitFor();
		await details.locator("dt").filter({ hasText: /^Server revision$/ }).locator("xpath=following-sibling::dd[1]").filter({ hasText: /^2$/ }).waitFor();
		assert.equal((await json(staff, staffPath(config.venue))).booking.revision, 2);
		await inspect("two-reschedule-inspect", { revisions: 2, blocking: 1, confirmationAudits: 1, paymentUnchanged: true });
	});
	const refund = await attempt("two-refund", async (open) => {
		const setup = await control({ op: "two-refund-prepare" });
		const owner = await open("customer-a"), reviewer = await open("staff"), executor = await open("customer-b");
		const requestPath = `/api/bank/requests/${setup.request}/refunds`;
		const unauthorised = { creditId: setup.credit, amountMinor: "100", currency: "LKR",
			expectedRevision: setup.revision, commandKey: crypto.randomUUID(), reason: "Synthetic denial" };
		assert.equal((await api(executor, requestPath, "POST", unauthorised)).status, 403);
		assert.equal((await api(owner, requestPath, "POST", unauthorised, false)).status, 403);
		await owner.goto(base + "/refunds");
		const target = "Refund ID to inspect or owned bank request ID to request a refund";
		const reason = "Reason (synthetic information only)";
		await owner.getByLabel(target, { exact: true }).fill(setup.request);
		await owner.getByLabel("Controlled-matched synthetic credit ID").fill(setup.credit);
		await owner.getByLabel("Requested LKR minor units").fill("100");
		await owner.getByLabel("Current bank request revision").fill(String(setup.revision));
		await owner.getByLabel(reason, { exact: true }).fill("Synthetic customer request");
		const created = await mutation(owner, "Request synthetic return", requestPath);
		assert.equal(created.data.state, "REQUESTED");
		assert.equal(created.data.realRepayment, false);
		const replay = await api(owner, requestPath, "POST", created.body);
		assert.equal(replay.status, 200);
		assert.equal(replay.value.id, created.data.id);
		const id = created.data.id;
		const path = `/api/admin/venues/${config.venue}/refunds/${id}`;
		const readPath = `/api/refunds/${id}`;
		const staffAction = { expectedRevision: created.data.revision, commandKey: crypto.randomUUID(),
			reason: "Synthetic restricted request", action: "REVIEW" };
		assert.equal((await api(executor, path + "/decisions", "POST", staffAction)).status, 403);
		assert.equal((await api(owner, path + "/decisions", "POST", staffAction)).status, 403);
		assert.equal((await api(reviewer, path.replace(config.venue, config.otherVenue) + "/decisions", "POST", staffAction)).status, 403);
		async function load(page) {
			await page.goto(base + "/admin/refunds");
			await page.getByLabel("Finance venue ID").fill(config.venue);
			await page.getByLabel(target, { exact: true }).fill(id);
			await page.getByLabel(reason, { exact: true }).fill("Synthetic staff action");
			const response = page.waitForResponse(r => new URL(r.url()).pathname === readPath && r.request().method() === "GET");
			await click(page, "Load refund status");
			assert.equal((await response).status(), 200);
		}
		await load(reviewer);
		assert.equal((await mutation(reviewer, "Begin independent review", path + "/decisions")).data.state, "UNDER_REVIEW");
		assert.equal((await mutation(reviewer, "Verify controlled original-payer destination", path + "/destination-verifications")).data.destinationVerified, true);
		const { data: approved } = await mutation(reviewer, "Approve and reserve synthetic funds", path + "/decisions");
		assert.equal(approved.state, "APPROVED");
		const start = { expectedRevision: approved.revision, commandKey: crypto.randomUUID(), reason: "Synthetic denied execution" };
		assert.equal((await api(reviewer, path + "/attempts", "POST", start)).status, 403);
		await load(executor);
		const prepared = await mutation(executor, "Prepare independent simulated attempt", path + "/attempts");
		assert.equal(prepared.data.state, "AWAITING_EVIDENCE");
		const attemptId = prepared.data.attempts.at(-1).id;
		const evidencePath = `${path}/attempts/${attemptId}/evidence`;
		const recorded = await mutation(executor, "Record simulated UNKNOWN", evidencePath);
		assert.equal(recorded.data.state, "AWAITING_EVIDENCE");
		assert.equal((await api(executor, evidencePath, "POST", { ...recorded.body, expectedRevision: recorded.data.revision, commandKey: crypto.randomUUID() })).status, 403);
		await load(reviewer);
		const unknown = await mutation(reviewer, "Independently verify simulated UNKNOWN", evidencePath);
		assert.equal(unknown.data.state, "UNKNOWN_RECONCILIATION_REQUIRED");
		await reviewer.getByRole("heading", { name: "UNKNOWN RECONCILIATION REQUIRED", exact: true }).waitFor();
		assert.equal((await api(executor, path + "/attempts", "POST", { ...prepared.body, expectedRevision: unknown.data.revision, commandKey: crypto.randomUUID() })).status, 409);
		await load(executor);
		await mutation(executor, "Record simulated CONFIRMED", path + "/reconciliations");
		await load(reviewer);
		const completed = await mutation(reviewer, "Independently verify simulated CONFIRMED", path + "/reconciliations");
		assert.equal(completed.data.state, "SIMULATED_CONFIRMED");
		assert.equal(completed.data.realRepayment, false);
		assert.equal((await api(reviewer, path + "/reconciliations", "POST", completed.body)).value.state, "SIMULATED_CONFIRMED");
		await reviewer.getByRole("heading", { name: "SIMULATED CONFIRMED", exact: true }).waitFor();
		await owner.getByLabel(target, { exact: true }).fill(id);
		await click(owner, "Load refund status");
		await owner.getByRole("heading", { name: "SIMULATED CONFIRMED", exact: true }).waitFor();
		await inspect("two-refund-inspect", { attempts: 1, immutableEvidence: 4, simulatedMinor: 100, protectedStateUnchanged: true });
	});
	if (!reschedule || !refund) process.exitCode = 1;
}

async function runPackageBooking(base) {
	const { booking, packageId, original, publication, unrelated } = config.packageBooking;
	const root = `/api/admin/venues/${config.venue}/packages/${packageId}`;
	const replacementName = "SYNTHETIC revised customer offer";
	const revisedDuration = 200;
	const contexts = [];
	const click = (page, name) => page.getByRole("button", { name, exact: true }).click();
	const open = async (subject) => {
		const context = await browser.newContext({ serviceWorkers: "block" });
		contexts.push(context);
		const origins = supervisedOrigins(config, base);
		await context.route("**/*", route => origins.has(new URL(route.request().url()).origin)
			? route.continue() : route.abort());
		const page = await context.newPage();
		await login(page, subject);
		return page;
	};
	const admin = async (page) => {
		await page.goto(base + "/admin/settings/packages");
		await page.getByLabel("Assigned venue ID").fill(config.venue);
		await page.getByRole("heading", { name: "Package drafts" }).waitFor();
	};
	const loadChoices = async (page) => {
		const [response] = await Promise.all([
			page.waitForResponse(r => r.request().method() === "GET" && new URL(r.url()).pathname === `/api/venues/${config.venue}/packages`),
			click(page, "Load applicable synthetic package choices"),
		]);
		assert.equal(response.status(), 200);
		return page.getByLabel("Published package");
	};
	const choices = async (page, time, { expiring = false, duration = 180 } = {}) => {
		await page.goto(base + "/rooms/" + config.room);
		await page.getByLabel("Customer start in venue local time").fill(config.local.replace(/T\d\d:\d\d/, time));
		await page.getByLabel("Customer duration (minutes)").fill(String(duration));
		await page.getByLabel("Guests", { exact: true }).fill("4");
		await click(page, "Check inventory (no hold)");
		const hold = await clickResponse(page, "Create temporary inventory hold", "/api/holds");
		if (expiring) await control({ op: "prepare-expiring-quote", holdId: hold.holdId });
		return { hold, select: await loadChoices(page) };
	};
	const quote = async (page, select, version) => {
		await select.selectOption(version);
		await page.getByLabel("Intended method (no payment)").selectOption("BANK");
		return clickResponse(page, "Calculate synthetic quotation", "/api/quotes");
	};
	const frozenTerms = value => [value.packageVersionId, value.contentHash, value.totalMinor, value.currency];

	let editor, publisher, customer, first, firstHold, replacement, revised, newBooking, receiptContext;
	try {
		await scenario("package-publish", async () => {
			editor = await open("staff");
			publisher = await open("customer-b");
			customer = await open("customer-a");
			const initial = await choices(customer, "T14:00");
			firstHold = initial.hold;
			first = await quote(customer, initial.select, original.version_id);
			assert.equal(first.packageVersionId, original.version_id);

			await admin(editor);
			await click(editor, "Edit PUBLIC");
			await editor.getByLabel("Package name", { exact: true }).fill(replacementName);
			await editor.getByLabel("Base price (LKR)").fill("95.79");
			await editor.getByLabel("Included customer minutes").fill(String(revisedDuration));
			await editor.getByLabel("Maximum selectable minutes").fill(String(revisedDuration + 60));
			await editor.getByLabel("Business reason for this change").fill("SYNTHETIC qualification edit");
			const saved = await clickResponse(editor, "Save unpublished draft", root + "/draft", "PUT");
			assert.equal(saved.includedMinutes, revisedDuration);
			assert.equal(saved.maximumMinutes, revisedDuration + 60);

			const request = { ...publication, expectedRevision: saved.revision,
				replacesPublicationId: original.publication_id, replacesRevision: original.revision };
			assert.equal((await api(editor, root + "/publication-preview", "POST", request)).status, 403);
			assert.equal((await api(publisher, root + "/publication-preview", "POST", request, false)).status, 403);
			assert.equal((await api(publisher, root.replace(config.venue, config.otherVenue))).status, 403);

			const catalog = await customer.context().newPage();
			try {
				await catalog.goto(base + "/rooms/" + config.room);
				await catalog.getByRole("heading", { name: "SYNTHETIC package", exact: true }).waitFor();
				assert.equal(await catalog.getByRole("heading", { name: replacementName, exact: true }).count(), 0);
			} finally {
				await catalog.close();
			}

			await admin(publisher);
			await click(publisher, "Publications PUBLIC");
			await publisher.getByLabel("Sales start — venue local", { exact: true }).fill(publication.salesFromLocal.slice(0, 16));
			await publisher.getByLabel("Sales end (exclusive) — venue local", { exact: true }).fill(publication.salesToLocal.slice(0, 16));
			await publisher.getByLabel("Session validity start — venue local", { exact: true }).fill(publication.sessionFromLocal.slice(0, 16));
			await publisher.getByLabel("Session validity end (exclusive) — venue local", { exact: true }).fill(publication.sessionToLocal.slice(0, 16));
			await publisher.getByLabel("Explicit schedule replacement (optional)").selectOption(original.publication_id);
			await publisher.getByLabel("Publication reason").fill(publication.reason);
			const reviewForm = publisher.getByRole("button", { name: "Review publication PUBLIC", exact: true });
			assert.equal(await reviewForm.evaluate(button => button.form.checkValidity()), true);
			const preview = await clickResponse(publisher, "Review publication PUBLIC", root + "/publication-preview");
			assert.equal(preview.snapshot.includedMinutes, revisedDuration);
			replacement = await clickResponse(publisher, "Approve reviewed publication PUBLIC", root + "/publications");
			assert.equal(replacement.state, "ACTIVE");
			assert.equal(replacement.snapshot.includedMinutes, revisedDuration);
			assert.notEqual(replacement.versionId, original.version_id);
			const history = await json(publisher, root + "/publications");
			assert.ok(history.items.some(row => row.versionId === original.version_id && row.state === "INACTIVE"));
		});

		await scenario("package-customer-quote", async () => {
			assert.deepEqual(frozenTerms(await json(customer, `/api/quotes/${first.quoteId}`)), frozenTerms(first));
			const released = await clickResponse(customer, "Release hold", `/api/holds/${firstHold.holdId}/release`);
			assert.equal(released.state, "RELEASED");
			const { select } = await choices(customer, "T18:00", { duration: revisedDuration });
			const revisedOption = select.locator(`option[value="${replacement.versionId}"]`);
			await revisedOption.waitFor({ state: "attached" });
			assert.equal(await revisedOption.textContent(), replacementName);
			assert.equal(await select.locator(`option[value="${original.version_id}"]`).count(), 0);
			revised = await quote(customer, select, replacement.versionId);
			assert.equal(revised.packageVersionId, replacement.versionId);
			assert.notEqual(revised.totalMinor, first.totalMinor);
			await customer.getByLabel("I reviewed this exact synthetic revision and all three disclosures").check();
			const accepted = await clickResponse(customer, "Accept synthetic revision only", `/api/quotes/${revised.quoteId}/accept`);
			assert.equal(accepted.state, "ACCEPTED");
			assert.deepEqual(frozenTerms(accepted), frozenTerms(revised));
			const confirmed = await control({ op: "package-confirm", quoteId: revised.quoteId, versionId: replacement.versionId });
			assert.equal(confirmed.syntheticPurchasedVersionVerified, true);
			newBooking = confirmed.booking;
			const requestPath = `/api/bank/requests/${confirmed.bankRequest}`;
			const before = await json(customer, requestPath);
			assert.equal(before.amountMinor, revised.totalMinor);
			assert.equal(before.evidence.find(row => row.id === confirmed.receiptId)?.state, "READY");
			receiptContext = { request: confirmed.bankRequest, evidence: confirmed.receiptId,
				metadata: before.evidence, amount: before.amountMinor };

			const expiryChoice = await choices(customer, "T14:00", { expiring: true, duration: revisedDuration });
			const expiring = await quote(customer, expiryChoice.select, replacement.versionId);
			assert.equal((await control({ op: "await-quote-expiry", quoteId: expiring.quoteId })).serverExpired, true);
			const expired = await api(customer, `/api/quotes/${expiring.quoteId}/accept`, "POST", {
				expectedRevision: expiring.revision, contentHash: expiring.contentHash,
				disclosures: expiring.disclosures, idempotencyKey: crypto.randomUUID(),
			});
			assert.equal(expired.status, 409);
			assert.equal((await control({ op: "inspect-expired-quote" })).snapshotAndFinancialStateUnchanged, true);
			const freshChoice = await choices(customer, "T14:00", { duration: revisedDuration });
			assert.notEqual(freshChoice.hold.holdId, expiryChoice.hold.holdId);
			const refreshed = await quote(customer, freshChoice.select, replacement.versionId);
			assert.notEqual(refreshed.quoteId, expiring.quoteId);
			assert.equal(refreshed.state, "ISSUED");
			const review = customer.getByLabel("I reviewed this exact synthetic revision and all three disclosures");
			const accept = customer.getByRole("button", { name: "Accept synthetic revision only", exact: true });
			assert.equal(await review.isChecked(), false);
			assert.equal(await accept.isDisabled(), true);
			const staleReview = await api(customer, `/api/quotes/${refreshed.quoteId}/accept`, "POST", {
				expectedRevision: refreshed.revision, contentHash: expiring.contentHash,
				disclosures: expiring.disclosures, idempotencyKey: crypto.randomUUID(),
			});
			assert.equal(staleReview.status, 409);
			await review.check();
			const freshAcceptance = await clickResponse(customer, "Accept synthetic revision only",
				`/api/quotes/${refreshed.quoteId}/accept`);
			assert.equal(freshAcceptance.state, "ACCEPTED");
			assert.deepEqual(frozenTerms(freshAcceptance), frozenTerms(refreshed));
			assert.equal((await clickResponse(customer, "Release hold",
				`/api/holds/${freshChoice.hold.holdId}/release`)).state, "RELEASED");
			const persisted = await control({ op: "package-inspect", versionId: replacement.versionId, quoteId: revised.quoteId });
			assert.equal(persisted.attributablePublicationAudit, true);
			observations.push({ stage: "package-customer-quote", ...persisted, expiredStatus: expired.status });
		});

		await scenario("package-history", async () => {
			const stranger = await open("qa-admin-history-observer");
			const publicationPath = id => `${root}/publications/${id}`;
			const originalRecord = await json(publisher, publicationPath(original.publication_id));
			const replacementRecord = await json(publisher, publicationPath(replacement.publicationId));
			assert.equal(originalRecord.state, "INACTIVE");
			assert.equal(originalRecord.snapshot.includedMinutes, 180);
			assert.equal(replacementRecord.snapshot.includedMinutes, revisedDuration);
			await publisher.getByLabel("State-change reason").fill("SYNTHETIC lifecycle withdrawal");
			const retired = await clickResponse(publisher, "Withdraw publication " + replacement.versionNo,
				root + "/publications/" + replacement.publicationId + "/deactivate");
			assert.equal(retired.state, "INACTIVE");
			const withdrawnHistory = await json(publisher, publicationPath(replacement.publicationId));
			assert.equal(withdrawnHistory.state, "INACTIVE");
			assert.deepEqual(withdrawnHistory.snapshot, replacementRecord.snapshot);
			assert.deepEqual((await json(publisher, publicationPath(original.publication_id))).snapshot, originalRecord.snapshot);
			assert.equal((await api(customer, publicationPath(replacement.publicationId))).status, 403);
			assert.equal((await api(publisher, publicationPath(replacement.publicationId).replace(config.venue, config.otherVenue))).status, 403);

			assert.deepEqual(frozenTerms(await json(customer, `/api/quotes/${revised.quoteId}`)), frozenTerms(revised));
			const persisted = await control({ op: "package-inspect", versionId: replacement.versionId,
				quoteId: revised.quoteId, retired: true });
			assert.equal(persisted.purchaseAndPaymentsUnchanged, true);
			observations.push({ stage: "package-history-persistence", ...persisted });

			const catalogResponse = customer.waitForResponse(response => {
				const url = new URL(response.url());
				return response.request().method() === "GET" &&
					url.pathname === `/api/venues/${config.venue}/packages` &&
					url.searchParams.get("roomId") === config.room;
			});
			await customer.goto(base + "/rooms/" + config.room);
			const catalog = await catalogResponse;
			assert.equal(catalog.status(), 200);
			const visible = (await catalog.json()).items.map(row => ({ packageId: row.packageId, versionId: row.versionId }));
			observations.push({ stage: "package-history-catalog", status: catalog.status(), visible,
				withdrawnVersion: replacement.versionId });
			assert.deepEqual(visible, [{ packageId: unrelated.packageId, versionId: unrelated.versionId }]);
			const packages = customer.getByRole("region", { name: "Room packages" });
			await packages.getByRole("heading", { name: "SYNTHETIC unrelated customer offer", exact: true }).waitFor();
			assert.equal(await packages.getByRole("heading", { name: replacementName, exact: true }).count(), 0);
			assert.equal(await packages.getByRole("heading", { name: "SYNTHETIC package", exact: true }).count(), 0);
			const { hold, select } = await choices(customer, "T14:00");
			await select.locator(`option[value="${unrelated.versionId}"]`).waitFor({ state: "attached" });
			assert.equal(await select.locator(`option[value="${replacement.versionId}"]`).count(), 0);
			const deniedQuote = await api(customer, "/api/quotes", "POST", {
				holdId: hold.holdId, expectedHoldRevision: hold.revision, packageId,
				packageVersionId: replacement.versionId, optionalServiceIds: [], paymentMethod: "BANK",
				idempotencyKey: crypto.randomUUID(),
			});
			observations.push({ stage: "withdrawn-quote-rejection", status: deniedQuote.status });
			assert.equal(deniedQuote.status, 409);
			assert.equal((await clickResponse(customer, "Release hold", `/api/holds/${hold.holdId}/release`)).state, "RELEASED");

			for (const [id, name, version, amount, minutes] of [[newBooking, replacementName, replacement.versionId, revised.totalMinor, revisedDuration],
				[booking, "SYNTHETIC package", original.version_id, first.totalMinor, 180]]) {
				await customer.goto(`${base}/bookings/${id}`);
				await click(customer, "Refresh booking history");
				await customer.getByRole("heading", { name, exact: true }).waitFor();
				await customer.getByText("History: retained immutable revision 1,", { exact: false }).waitFor();
				const purchase = await json(customer, `/api/bookings/${id}`);
				assert.equal(purchase.revision, 1);
				assert.equal(purchase.purchase.packageVersionId, version);
				assert.equal(purchase.purchase.amountMinor, amount);
				assert.equal((Date.parse(purchase.purchase.sessionEnd) - Date.parse(purchase.purchase.sessionStart)) / 60000, minutes);
				await customer.getByText(/^Original purchase:/).waitFor();
				assert.equal((await api(stranger, `/api/bookings/${id}`)).status, 404);
				await editor.goto(`${base}/admin/bookings/venues/${config.venue}/${id}`);
				await click(editor, "Reload booking detail");
				const detail = editor.getByRole("region", { name: "Authorized staff booking detail" });
				await detail.getByText("Server revision", { exact: true }).waitFor();
				await detail.locator("dt").filter({ hasText: /^Purchased package$/ })
					.locator("xpath=following-sibling::dd[1]").getByText(name, { exact: true }).waitFor();
				const staffBooking = (await json(editor, `/api/admin/venues/${config.venue}/bookings/${id}`)).booking;
				assert.equal(staffBooking.revision, 1);
				assert.equal(staffBooking.packageName, name);
				assert.equal((Date.parse(staffBooking.scheduledEnd) - Date.parse(staffBooking.scheduledStart)) / 60000, minutes);
				assert.equal((await api(editor, `/api/admin/venues/${config.otherVenue}/bookings/${id}`)).status, 403);
				observations.push({ stage: "package-history-view", bookingId: id, packageVersionId: version,
					customerRendered: true, staffRendered: true });
			}
			const requestPath = `/api/bank/requests/${receiptContext.request}`;
			const staffRequest = `/api/admin/venues/${config.venue}/bank/requests/${receiptContext.request}`;
			const previewPath = staffRequest + "/evidence/" + receiptContext.evidence;
			const receipt = await json(customer, requestPath);
			assert.equal(receipt.amountMinor, receiptContext.amount);
			assert.deepEqual(receipt.evidence, receiptContext.metadata);
			assert.equal((await api(stranger, requestPath)).status, 404);
			assert.equal((await api(customer, previewPath)).status, 403);
			assert.equal((await api(editor, previewPath.replace(config.venue, config.otherVenue))).status, 403);
			assert.equal((await api(editor, staffRequest)).status, 200);
			await customer.goto(`${base}/bank?evidence=${receiptContext.request}`);
			await customer.getByRole("heading", { name: "Receipt evidence", exact: true }).waitFor();
			await customer.getByText(/^READY · /).waitFor();
			const preview = await editor.evaluate(async path => {
				const response = await fetch(path, { cache: "no-store" });
				return { status: response.status, type: response.headers.get("content-type"),
					bytes: (await response.arrayBuffer()).byteLength };
			}, previewPath);
			assert.equal(preview.status, 200);
			assert.equal(preview.type, "image/png");
			assert.ok(preview.bytes > 0);
			assert.equal((await control({ op: "package-inspect", retired: true, previewed: true,
				versionId: replacement.versionId, quoteId: revised.quoteId })).purchaseAndPaymentsUnchanged, true);
			observations.push({ stage: "protected-receipt-history", metadataUnchanged: true, staffPreview: 200,
				otherCustomer: 404, ownerPreview: 403, wrongVenuePreview: 403 });
		});
	} finally {
		await Promise.all(contexts.map(context => context.close()));
	}
}

async function runOperationalJourney(base) {
 const pages=[];
 const open=async(subject)=>{
  const c=await browser.newContext({serviceWorkers:'block'}); const p=await c.newPage(); pages.push(p);
  c.on('request',r=>{assert.ok(['127.0.0.1','localhost'].includes(new URL(r.url()).hostname),'external request prohibited');});
  await login(p,subject);return p;
 };
 const demo=config.demo;
 const subject=role=>demo?.principals[role].subject;
 const customer=await open(demo?subject('customer'):'customer-a'), staff=await open(demo?subject('operations'):'staff'), reviewer=await open(demo?subject('independent-turnover-reviewer'):'customer-b');
 const verifier=demo?await open(subject('bank-verifier')):staff;
 const allocator=demo?await open(subject('bank-allocator')):reviewer;
 const admission=demo?await open(subject('admission')):staff;
 const cleaner=demo?await open(subject('cleaner')):staff;
 const inventory=demo?await open(subject('booking-inventory')):null;
 const guests=demo?5:4;
 if(demo){assert.equal(demo.classification,'SYNTHETIC_TEST_ONLY');assert.equal(new Set(Object.values(demo.principals).map(p=>p.id)).size,8);assert.equal(new Set(Object.values(demo.principals).map(p=>p.subject)).size,8);}

 const send=async(p,path,body)=>{const r=await api(p,path,'POST',body); observations.push({stage,path:path.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,'{reference}'),status:r.status,source:'REAL_HTTP'});assert.equal(r.status,200);return r.value;};
 const cmd=(revision)=>({revision,commandKey:crypto.randomUUID()});
 const financial=(revision)=>({expectedRevision:revision,commandKey:crypto.randomUUID(),reason:'SYNTHETIC integrated qualification'});
 const upload=async(p,prefix,allocation,file)=>{
  const bytes=[...await readFile(`${run}/${file}`)];
  const status=await p.evaluate(async({prefix,allocation,bytes})=>{
   const csrf=await(await fetch('/api/session/csrf')).json();
   return (await fetch(`${prefix}/${allocation.uploadId}/content`,{method:'PUT',headers:{'X-CSRF-TOKEN':csrf.token,'Content-Type':'image/png'},body:new Uint8Array(bytes)})).status;
  },{prefix,allocation,bytes});assert.equal(status,200);
  return send(p,`${prefix}/${allocation.uploadId}/complete`,cmd(allocation.revision));
 };
 let hold,quote,verification,bank,booking,roster,check,startBody,started,ended,turnover;
 const bankRoot=`/api/admin/venues/${config.venue}/bank`;
 try {
 await scenario('operational-customer-quote',async()=>{
  await customer.goto(`${base}/rooms/${config.room}`);
  const point=await json(customer,`/api/rooms/${config.room}/availability?sessionStartLocal=${config.operationalStartLocal}&durationMinutes=180&guests=${guests}`);
  assert.equal(point.available,true);
  hold=await send(customer,'/api/holds',{roomId:config.room,sessionStartLocal:config.operationalStartLocal,durationMinutes:180,guests,expectedConfiguration:point.configuration,idempotencyKey:crypto.randomUUID()});
  const catalog=await json(customer,`/api/venues/${config.venue}/packages?roomId=${config.room}&sessionStart=${encodeURIComponent(point.sessionStart)}&sessionEnd=${encodeURIComponent(point.sessionEnd)}`);
  const pkg=catalog.items[0];assert.ok(pkg?.packageId&&pkg.versionId);
  if(demo){assert.equal(Date.parse(point.sessionEnd)-Date.parse(point.sessionStart),180*60000);assert.equal(Date.parse(point.blockedUntil)-Date.parse(point.sessionEnd),15*60000);
   assert.equal((await api(customer,`/api/rooms/${config.room}/availability?sessionStartLocal=${config.operationalStartLocal}&durationMinutes=180&guests=6`)).status,400);
  }
  quote=await send(customer,'/api/quotes',{holdId:hold.holdId,packageId:pkg.packageId,packageVersionId:pkg.versionId,expectedHoldRevision:hold.revision,optionalServiceIds:[],paymentMethod:'BANK',idempotencyKey:crypto.randomUUID()});
  await send(customer,`/api/quotes/${quote.quoteId}/accept`,{expectedRevision:quote.revision,contentHash:quote.contentHash,disclosures:quote.disclosures,idempotencyKey:crypto.randomUUID()});
  assert.equal((await json(customer,`/api/quotes/${quote.quoteId}`)).state,'ACCEPTED');
  if(demo)assert.ok(JSON.stringify(quote).includes('330000'),'five-guest exact synthetic payable total');
 });
 await scenario('operational-verification-bank',async()=>{
  verification=await send(customer,'/api/verification/requests',{commandKey:crypto.randomUUID(),quoteId:quote.quoteId,policyVersion:'SYNTHETIC-V1',consent:true});
  let allocation=await send(customer,`/api/verification/requests/${verification.id}/uploads`,{...cmd(verification.revision),documentType:'NIC'});
  await upload(customer,'/api/verification/uploads',allocation,'identity.png');
  await control({op:'operational-scan'});
  verification=await json(customer,`/api/verification/requests/${verification.id}`);
  await send(admission,`/api/admin/venues/${config.venue}/verification/requests/${verification.id}/decisions`,{...cmd(verification.revision),verdict:'VERIFIED',reason:'SYNTHETIC_MATCH_18_PLUS'});
  bank=await send(customer,'/api/bank/requests',{quoteId:quote.quoteId,quoteRevision:quote.revision,contentHash:quote.contentHash,commandKey:crypto.randomUUID()});
  const claim=await send(customer,`/api/bank/requests/${bank.id}/claims`,{...cmd(bank.revision),declaredPaidAt:new Date().toISOString(),declaredAmountMinor:bank.amountMinor,declaredReference:'SYNTHETIC-NOT-A-BANK-REFERENCE'});
  allocation=await send(customer,`/api/bank/claims/${claim.claimId}/uploads`,cmd(claim.revision));
  await upload(customer,'/api/bank/uploads',allocation,'receipt.png');
  await control({op:'operational-scan'});
  bank=await json(customer,`/api/bank/requests/${bank.id}`);
  assert.equal(bank.paymentVerified,false);assert.equal(bank.bookingConfirmed,false);
  assert.equal((await api(reviewer,`/api/bank/requests/${bank.id}`)).status,404);
  await send(verifier,`${bankRoot}/requests/${bank.id}/decisions`,{...cmd(bank.revision),action:'REVIEWED',reason:'SYNTHETIC_REVIEW'});
 });
 await scenario('operational-independent-finance',async()=>{
  const creditBody={...financial(0),fixtureKey:demo?'SYNTHETIC-DEMO-BANK-330000':'SYNTHETIC-BANK-50000'};
  if(demo){bank=await json(customer,`/api/bank/requests/${bank.id}`);const missing=await api(allocator,`${bankRoot}/requests/${bank.id}/allocations`,'POST',{...financial(bank.revision),creditId:crypto.randomUUID(),amountMinor:Number(bank.amountMinor)});assert.ok([403,404,409].includes(missing.status));assert.equal((await json(customer,`/api/bank/requests/${bank.id}`)).bookingConfirmed,false);const empty=await control({op:'operational-demo-unfunded'});assert.equal(empty.bookings,0);assert.equal(empty.verifiedCredits,0);assert.equal(empty.allocations,0);}
  const credit=await send(verifier,`${bankRoot}/credits`,creditBody);
  if(demo){assert.equal((await send(verifier,`${bankRoot}/credits`,creditBody)).id,credit.id);assert.equal((await api(verifier,`${bankRoot}/credits`,'POST',{...creditBody,commandKey:crypto.randomUUID()})).status,409);}
  bank=await json(customer,`/api/bank/requests/${bank.id}`);
  const match={...financial(bank.revision),requestId:bank.id};
  assert.equal((await api(verifier,`${bankRoot}/credits/${credit.id}/matches`,'POST',match)).status,403);
  await send(allocator,`${bankRoot}/credits/${credit.id}/matches`,match);
  bank=await json(customer,`/api/bank/requests/${bank.id}`);
  const allocationBody={...financial(bank.revision),creditId:credit.id,amountMinor:Number(bank.amountMinor)};
  const allocation=await send(allocator,`${bankRoot}/requests/${bank.id}/allocations`,allocationBody);
  if(demo)assert.deepEqual(await send(allocator,`${bankRoot}/requests/${bank.id}/allocations`,allocationBody),allocation);
  assert.equal(allocation.syntheticBookingStatus,'SIMULATED_CONFIRMED');assert.equal(allocation.bookingConfirmed,false);
  booking=allocation.bookingId;assert.ok(booking);
  await control({op:'operational-baseline',bookingId:booking});
  await customer.goto(`${base}/bookings/${booking}`);
  await customer.getByRole('heading',{name:'Purchased snapshot',exact:true}).waitFor();
  if(demo)await customer.screenshot({path:run+'/demo-customer.png',fullPage:true});
 });
 const root=()=>`/api/admin/venues/${config.venue}/bookings/${booking}`;
 await scenario('operational-roster-eligibility',async()=>{
  const path=`/api/bookings/${booking}/guest-roster`;
  roster=await json(customer,path);
  roster=await send(customer,path,{commandKey:crypto.randomUUID(),bookingRevision:roster.bookingRevision,revision:roster.revision});
  for(let i=1;i<guests;i++) roster=await send(customer,path+'/companions',{commandKey:crypto.randomUUID(),bookingRevision:roster.bookingRevision,revision:roster.revision,label:`SYNTHETIC-${i}`});
  assert.equal(roster.count,guests);
  assert.equal((await api(reviewer,path)).status,404);
  const absent=await json(admission,root()+'/guest-admission');assert.notEqual(absent.status,'ELIGIBLE_FOR_STAFF_REVIEW');
  const fixture=await control({op:'operational-eligibility',bookingId:booking});assert.equal(fixture.originalDocumentInspected,false);
  const eligible=await json(admission,root()+'/guest-admission');assert.equal(eligible.status,'ELIGIBLE_FOR_STAFF_REVIEW');assert.equal(eligible.physicalEntryAuthorized,false);
 });
 // Wait for authoritative time naturally; do not rewrite clocks, purchases or policy boundaries.
 const deadline=Date.now()+240000;
 while(Date.now()<Date.parse(hold.sessionStart)&&Date.now()<deadline) await new Promise(r=>setTimeout(r,250));
 // The existing 120-second test sessions may expire while awaiting real time.
 // Use genuine OIDC sign-in again; never extend or rewrite a stored session.
 if(demo){for(const [role,p] of [['customer',customer],['operations',staff],['booking-inventory',inventory],['bank-verifier',verifier],['bank-allocator',allocator],['admission',admission],['cleaner',cleaner],['independent-turnover-reviewer',reviewer]])await login(p,subject(role));}
 else {await login(staff,'staff');await login(customer,'customer-a');await login(reviewer,'customer-b');}
 await scenario('operational-staff-session',async()=>{
  const path=root()+'/guest-check-in';check=await json(admission,path);
  const body={commandKey:crypto.randomUUID(),roomId:config.room,bookingRevision:roster.bookingRevision,rosterRevision:roster.revision};
  assert.equal((await api(admission,path,'POST',body,false)).status,403);
  assert.equal((await api(customer,path,'POST',body)).status,403);
  assert.equal((await api(admission,path.replace(config.venue,config.otherVenue),'POST',body)).status,403);
  assert.equal((await api(admission,path,'POST',{...body,rosterRevision:roster.revision+1})).status,409);
  if(demo){assert.equal((await api(staff,path,'POST',body)).status,403);await control({op:'operational-demo-revoke'});assert.equal((await api(admission,path,'POST',body)).status,403);await control({op:'operational-demo-restore'});}
  check=await send(admission,path,body);assert.equal(check.physicalEntryAuthorized,false);
  assert.deepEqual(await send(admission,path,body),check);
  const session=await json(staff,root()+'/session');
  startBody={commandKey:crypto.randomUUID(),bookingRevision:roster.bookingRevision,rosterRevision:roster.revision,checkInActorId:session.checkIn.actorId,checkInCommandKey:session.checkIn.commandKey};
  assert.equal((await api(staff,root()+'/session/checkouts','POST',{...startBody,startEventId:crypto.randomUUID()})).status,409);
  started=await send(staff,root()+'/session/starts',startBody);
  assert.deepEqual(await send(staff,root()+'/session/starts',startBody),started);
  assert.equal((await api(staff,root()+'/session/starts','POST',{...startBody,bookingRevision:roster.bookingRevision+1})).status,409);
  ended=await send(staff,root()+'/session/checkouts',{...startBody,commandKey:crypto.randomUUID(),startEventId:started.eventId});
  await staff.goto(base+'/admin/bookings');
  await staff.getByLabel('Assigned venue reference').fill(config.venue);
  await staff.getByRole('button',{name:'Apply booking filters',exact:true}).click();
  await staff.getByRole('link',{name:`View exact booking details for ${booking}`,exact:true}).click();
  await staff.getByRole('region',{name:'Synthetic session operations'}).getByText(/CHECKOUT/).waitFor();
  const handoff=await send(staff,root()+'/session/cleaning-handoff',{checkoutEventId:ended.eventId});
  turnover=handoff.case;assert.equal(handoff.status,'RECORDED');
  assert.equal((await send(staff,root()+'/session/cleaning-handoff',{checkoutEventId:ended.eventId})).case.case_id,turnover.case_id);
 });
 await scenario('operational-independent-turnover',async()=>{
  const path=`/api/admin/venues/${config.venue}/operations/cases/${turnover.case_id}`;
  const action=async(p,kind,extra={})=>{const body={commandKey:crypto.randomUUID(),expectedVersion:turnover.revision,...extra};turnover=await send(p,path+'/'+kind,body);return body;};
  await action(staff,'assignments',{assigneeId:demo?demo.principals.cleaner.id:config.staffId});await action(cleaner,'acknowledgements');
  for(const item of turnover.checklist)await action(cleaner,'checklist',{item,asserted:true});
  await action(cleaner,'completion-assertions');
  const review={commandKey:crypto.randomUUID(),expectedVersion:turnover.revision};
  assert.equal((await api(cleaner,path+'/reviews','POST',review)).status,403);
  turnover=await send(reviewer,path+'/reviews',review);assert.equal(turnover.state,'RESOLVED');
  assert.deepEqual(await send(reviewer,path+'/reviews',review),turnover);
  assert.equal(turnover.physicalOccupancy,'UNKNOWN');assert.equal(turnover.physicalReadiness,'UNVERIFIED');assert.equal(turnover.inventoryChanged,false);
 });
 await scenario('operational-persistence',async()=>{
  if(demo)await staff.screenshot({path:run+'/demo-staff.png',fullPage:true});
  const persisted=await control({op:'operational-persistence',bookingId:booking});
  assert.equal(persisted.reviewerIndependent,true);assert.deepEqual(persisted.sessionEvents,['START','CHECKOUT']);
  observations.push({source:'POSTGRESQL_ASSERTIONS',...persisted});
 });
 } finally {await Promise.all(pages.map(p=>p.context().close()));}
}

const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`;
const faultTransport = operational || cancellation || funding || twoJourneys || packageBooking ? null : h1 ? null : await uploadFaultProxy(config.backend);
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
	if (operational) {
        await runOperationalJourney(base);
    } else if (packageBooking) {
		await runPackageBooking(base);
	} else if (twoJourneys) {
		await runTwoJourneys(base);
	} else if (funding) {
		await runFundingQualification(base);
	} else if (cancellation) {
		await runCancellationQualification(base);
	} else if (supervised) {
		const origins = supervisedOrigins(config, base);
		const pages = [];
		const subjects = [["customer-a", "/rooms"], ["staff", "/admin"], ["customer-b", "/rooms"]];
		if (config.adminQa) {
			for (const role of ["administrator", "publisher", "wrong-venue", "ungranted", "revoked"]) {
				subjects.push([`qa-admin-${role}`, "/admin"]);
			}
		}
		for (const [subject, route] of subjects) {
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
			if (config.adminQa) {
				console.log(`${id} ${check.status}`);
				const heading = page.locator("main h1").first();
				if (await heading.isVisible()) {
					const label = await heading.innerText();
					if (["Venue configuration", "Room configuration", "Package drafts", "Staff booking details", "Bank review details"].includes(label)) {
						await writeFile(`${run}/admin-case-${report.length + 1}.png`, await heading.screenshot({ timeout: 2000 }), { mode: 0o600 }).catch(() => {});
					}
				}
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
		if (config.adminQa) {
			const [admin, publish, other, denied, revoke] = pages.slice(3).map(({ page }) => page);
			await runAdminQualification({ base, test, respond, roles: {
				customer, staff, administrator: admin, publisher: publish, wrong: other, ungranted: denied, revoked: revoke,
			} });
			assert.equal(report.length, 12);
			assert.ok(report.every(item => item.status === "PASS"), "Admin qualification has failures");
		} else {
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
		}
		await pages[0].page.goto(base + "/rooms");
		await pages[0].page.bringToFront();
		supervisedOutcome = { routes: pages.slice(0, 2).map(({ route }) => base + route), ready: true };
		await writeFile(`${run}/supervised-ready.json`, JSON.stringify({
			syntheticOnly: true,
			customerUrl: supervisedOutcome.routes[0],
			staffUrl: supervisedOutcome.routes[1],
		}), { mode: 0o600, flag: "wx" });
		if (!config.adminQa) await waitForSupervisedExit(pages, browser, frontend);
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

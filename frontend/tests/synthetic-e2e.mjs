// Opt-in REAL backend journey. No page.route, route.fulfill, session injection or business stubs.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { readFile, writeFile, rename, unlink } from "node:fs/promises";
import { pathToFileURL } from "node:url";

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
const planned = [
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
	const response = page.waitForResponse(
		(r) =>
			new URL(r.url()).pathname === path && r.request().method() === method,
	);
	await page.getByRole("button", { name, exact: true }).click();
	const r = await response;
	assert.equal(r.status(), 200);
	return r.json();
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
}
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`;
await writeFile(`${run}/frontend-port.json`, JSON.stringify({ ports: [port] }));
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
				CINENEST_AUTH_ORIGIN: config.backend,
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
	browser = await chromium.launch({ headless: true });
	assert.equal(browser.version(), "151.0.7922.34");
	const context = await browser.newContext({
		viewport: { width: 1280, height: 900 },
	});
	const page = await context.newPage();
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
		await page
			.getByLabel("Customer start in venue local time")
			.fill(config.local);
		await page.getByLabel("Customer duration (minutes)").fill("180");
		await page.getByLabel("Guests", { exact: true }).fill("4");
		await page
			.getByRole("button", { name: "Check inventory (no hold)", exact: true })
			.click();
		await clickResponse(page, "Create temporary inventory hold", "/api/holds");
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
		await clickResponse(
			page,
			"Accept synthetic revision only",
			`/api/quotes/${quote.quoteId}/accept`,
		);
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
		bank = await clickResponse(
			page,
			"Create synthetic request",
			"/api/bank/requests",
		);
		creationBody = (await observed).postDataJSON();
		assert.equal(bank.bookingConfirmed, false);
		assert.equal(bank.paymentVerified, false);
	});
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
	await scenario("simulated-confirmation-api-support", async () => {
		const result = await control({ op: "finance", id: bank.id });
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
		await page.goto(base + "/bookings");
		assert.ok(
			(await page.locator(`a[href="/bookings/${result.bookingId}"]`).count()) >
				0,
		);
	});
	await scenario("C-wrong-owner", async () => {
		const other = await browser.newContext();
		const p = await other.newPage();
		try {
			await login(p, "customer-b");
			assert.equal((await api(p, "/api/bank/requests/" + bank.id)).status, 404);
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
		assert.ok(
			!/[?&](?:hash|token|secret|evidence|amount|csrf)=/i.test(page.url()),
		);
		assert.ok(!page.url().includes(creationBody.contentHash));
	});
	await scenario("G-expired-session", async () => {
		await control({ op: "expire-session" });
		assert.equal((await api(page, "/api/accounts/me")).status, 401);
	});
	notRun(
		"A-expired-quote",
		"No mutation of immutable quote snapshots; dedicated test clock/case remains needed",
	);
	notRun(
		"B-invalid-expired-hold",
		"Independent case/reset needed to avoid corrupting positive journey",
	);
	notRun(
		"F-rejected-verification",
		"Independent case/reset needed; positive review cannot be rewritten",
	);
	notRun(
		"N-uncertain-upload",
		"No safely qualified connection-failure transport; no fabricated responses",
	);
} catch {
	results.push({
		id: stage,
		status: "FAIL",
		reason:
			"Real harness assertion/startup failure; raw private diagnostics not published",
		apiFailure,
	});
	process.exitCode = 1;
} finally {
	for (const id of planned)
		if (!results.some((r) => r.id === id))
			notRun(
				id,
				"Stopped after failure or missing independent safe scenario control",
			);
	if (browser) await browser.close();
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

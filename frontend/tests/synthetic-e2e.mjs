// Opt-in REAL backend journey. No page.route, route.fulfill, session injection or business stubs.
import assert from "node:assert/strict";
import {extensionScenarios,extensionReservationJourney} from "./extension-reservation-e2e.mjs";
import { uploadFaultProxy } from "./upload-fault-proxy.mjs";
import {
	safeCustomerNavigation,
	safeLoopbackResource,
} from "./safe-navigation.mjs";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import {
	readFile,
	writeFile,
	rename,
	unlink,
	mkdir,
	chmod,
} from "node:fs/promises";
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
await chmod(diagnostics, 0o700);
const planned = [
 ...extensionScenarios,
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
const faultTransport = await uploadFaultProxy(config.backend);
await writeFile(
	`${run}/frontend-port.json`,
	JSON.stringify({
		ports: [port, Number(new URL(faultTransport.origin).port)],
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
				CINENEST_AUTH_ORIGIN: faultTransport.origin,
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
		await page
			.getByRole("link", {
				name: "View simulated booking details",
				exact: true,
			})
			.waitFor({ state: "visible" });
		assert.equal(page.url(), base + "/bank?request=" + bank.id);
		assert.equal(safeCustomerNavigation(page.url(), base), true);
		assert.ok(!page.url().includes(creationBody.contentHash));
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
	await scenario("G-expired-session", async () => {
		await control({ op: "expire-session" });
		assert.equal((await api(page, "/api/accounts/me")).status, 401);
	});
} catch (failure) {
	assertionFailure = {
		name: failure.name,
		message:
			failure.name === "AssertionError"
				? "Harness assertion failed; inspect source location"
				: "Harness operation failed",
		stack: String(failure.stack)
			.split("\n")
			.filter((line) => /synthetic-e2e\.mjs:|extension-reservation-e2e\.mjs:/.test(line))
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
	for (const id of planned)
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
	await faultTransport.close();
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

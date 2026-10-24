// Real-stack Release1 checks. No business interception, cookie injection or mocked API success.
import assert from "node:assert/strict";
import { unknownRevisit, refreshPending } from "./release1-recovery-e2e.mjs";
import { writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

export const release1Scenarios = [
	"R1-bank-timeout-recovery",
	"R1-pending-state",
	"R1-inventory-negative",
	"R1-concurrent-inventory",
	"R1-concurrent-bank-replay",
];
// Fixture-only wall-time arithmetic; the server still validates Asia/Colombo intervals.
const localPlus = (local, minutes) =>
	new Date(Date.parse(local + "Z") + minutes * 60000)
		.toISOString()
		.slice(0, 16);
export async function release1PendingJourney({
	page,
	browser,
	base,
	config,
	api,
	json,
	login,
	control,
	scenario,
	bank,
	quote,
	creationBody,
	calendarObservation,
	faultTransport,
	diagnostics,
	requests,
}) {
	assert.equal(calendarObservation.timezone, "Asia/Colombo");
	const evidence = {
		syntheticOnly: true,
		database: [],
		http: [],
		screenshots: [],
		fault: undefined,
	};
	try {
		async function inspect(phase) {
			const result = await control({
				op: "r1-pending-inspect",
				quoteId: quote.quoteId,
				requestId: bank.id,
				commandKey: creationBody.commandKey,
			});
			for (const key of [
				"credits",
				"verifications",
				"allocations",
				"bookings",
				"credentials",
				"provisioningWork",
			])
				assert.equal(result[key], 0, key);
			assert.equal(result.requests, 1);
			assert.equal(result.commandReceipts, 1);
			assert.equal(result.bindingVerified, true);
			assert.equal(result.databaseExclusionVerified, true);
			assert.equal(result.overlapPairs, 0);
			evidence.database.push({ phase, ...result });
		}
		const record = (name, result) => {
			evidence.http.push({ name, status: result.status });
			return result;
		};
		await scenario("R1-bank-timeout-recovery", async () => {
			assert.deepEqual(faultTransport.bankCreateReceipt, {
				attempts: 2,
				stalled: 1,
				upstreamStatus: 200,
			});
			await page
				.getByRole("alert")
				.filter({ hasText: "The request outcome is unknown" })
				.waitFor();
			assert.equal(
				await page
					.getByRole("button", {
						name: "Create synthetic request",
						exact: true,
					})
					.count(),
				0,
			);
			assert.equal(
				await page
					.getByRole("link", {
						name: "View simulated booking details",
						exact: true,
					})
					.count(),
				0,
			);
			await unknownRevisit({page,base,quote,creationBody,json,control,diagnostics,scenario});
			const observed = page.waitForRequest(
				(r) =>
					new URL(r.url()).pathname === "/api/bank/requests" &&
					r.method() === "POST",
			);
			const response = page.waitForResponse(
				(r) =>
					new URL(r.url()).pathname === "/api/bank/requests" &&
					r.request().method() === "POST",
			);
			await page
				.getByRole("button", {
					name: "Retry original bank request",
					exact: true,
				})
				.focus();
			await page.keyboard.press("Enter");
			const r = await response;
			assert.equal(r.status(), 200);
			bank = await r.json();
			assert.deepEqual(
				(await observed).postDataJSON(),
				creationBody,
				"explicit retry preserves exact original command",
			);
			assert.equal(bank.requestState, "OPEN");
			assert.equal(bank.reviewState, "PENDING");
			assert.equal(bank.paymentVerified, false);
			assert.equal(bank.bookingConfirmed, false);
			assert.equal(faultTransport.bankCreateReceipt.attempts, 3);
			evidence.fault = { ...faultTransport.bankCreateReceipt };
			await inspect("explicit-original-command-recovery");
		});
		await scenario("R1-pending-state", async () => {
			const checkpoint = requests.length;
			await page.goto(`${base}/bank?request=${bank.id}`);
			await page
				.getByRole("region", {
					name: "Current bank request summary",
					exact: true,
				})
				.waitFor();
			const authoritative = await json(page, `/api/bank/requests/${bank.id}`);
			assert.equal(authoritative.quoteId, quote.quoteId);
			assert.equal(authoritative.amountMinor, quote.totalMinor);
			assert.equal(authoritative.currency, "LKR");
			assert.equal(authoritative.financial.syntheticFundingStatus, "UNFUNDED");
			assert.equal(
				authoritative.financial.syntheticBookingStatus,
				"NOT_CONFIRMED",
			);
			assert.equal(authoritative.financial.allocatedMinor, 0);
			assert.equal(authoritative.financial.bookingId, null);
			assert.equal(
				await page
					.getByRole("link", {
						name: "View simulated booking details",
						exact: true,
					})
					.count(),
				0,
			);
			await page
				.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true })
				.waitFor();
			await page
				.getByText("DO NOT TRANSFER REAL MONEY", { exact: true })
				.waitFor();
			for (const width of [375, 768, 1280]) {
				await page.setViewportSize({ width, height: 900 });
				assert.equal(await page.locator("main").count(), 1);
				assert.ok(
					await page.evaluate(
						() => document.documentElement.scrollWidth <= innerWidth,
					),
				);
				const path = new URL(`release1-pending-${width}.png`, diagnostics);
				await page.screenshot({ path: fileURLToPath(path), fullPage: true });
				evidence.screenshots.push({
					width,
					file: `release1-pending-${width}.png`,
				});
			}
			assert.equal(
				requests.slice(checkpoint).filter((r) => r.method !== "GET").length,
				0,
				"pending reads never create or fund anything",
			);
			await inspect("pending-page-and-responsive-reads");
		});
		await refreshPending({page,bank,requests,control,quote,creationBody,diagnostics,scenario});
		const contexts = [];
		try {
			const peers = [];
			for (const subject of ["customer-b", "staff"]) {
				const c = await browser.newContext();
				contexts.push(c);
				const p = await c.newPage();
				p.setDefaultTimeout(12000);
				c.on("request", (r) =>
					assert.ok(
						["127.0.0.1", "localhost"].includes(new URL(r.url()).hostname),
						"external test request prohibited",
					),
				);
				await login(p, subject);
				peers.push(p);
			}
			const query = (start, duration = 180, guests = 4) =>
				`/api/rooms/${config.room}/availability?` +
				new URLSearchParams({
					sessionStartLocal: start,
					durationMinutes: String(duration),
					guests: String(guests),
				});
			const current = await json(peers[0], query(config.local));
			const body = (start, overrides = {}) => ({
				roomId: config.room,
				sessionStartLocal: start,
				durationMinutes: 180,
				guests: 4,
				expectedConfiguration: current.configuration,
				idempotencyKey: crypto.randomUUID(),
				...overrides,
			});
			await scenario("R1-inventory-negative", async () => {
				for (const [name, change, status] of [
					["capacity", { guests: 6 }, 400],
					["nonpositive-duration", { durationMinutes: 0 }, 400],
					["below-minimum", { durationMinutes: 179 }, 409],
				]) {
					assert.equal(
						record(
							name,
							await api(
								peers[0],
								"/api/holds",
								"POST",
								body(config.local, change),
							),
						).status,
						status,
					);
				}
				assert.equal(
					record(
						"occupied-hold",
						await api(peers[0], "/api/holds", "POST", body(config.local)),
					).status,
					409,
				);
				const minutes =
					(Date.parse(calendarObservation.sessionEnd) -
						Date.parse(calendarObservation.sessionStart)) /
					60000;
				const buffer =
					(Date.parse(calendarObservation.blockedUntil) -
						Date.parse(calendarObservation.sessionEnd)) /
					60000;
				assert.ok(
					buffer > 0,
					"existing synthetic fixture has a positive configured buffer",
				);
				const duringBuffer = localPlus(config.local, minutes),
					afterBuffer = localPlus(duringBuffer, buffer);
				assert.equal(
					(await json(peers[0], query(duringBuffer))).available,
					false,
				);
				assert.equal(
					record(
						"cleaning-buffer-overlap",
						await api(peers[0], "/api/holds", "POST", body(duringBuffer)),
					).status,
					409,
				);
				assert.equal(
					(await json(peers[0], query(afterBuffer))).available,
					true,
				);
				assert.equal(
					record(
						"wrong-owner-pending",
						await api(peers[0], `/api/bank/requests/${bank.id}`),
					).status,
					404,
				);
				await inspect("invalid-and-competing-requests");
			});
			await scenario("R1-concurrent-inventory", async () => {
				const future = localPlus(config.local, 7 * 1440);
				assert.equal((await json(peers[0], query(future))).available, true);
				const bodies = [body(future), body(future)];
				const responses = await Promise.all(
					peers.map((p, i) => api(p, "/api/holds", "POST", bodies[i])),
				);
				assert.deepEqual(responses.map((r) => r.status).sort(), [200, 409]);
				responses.forEach((r, i) => {
					record(`initial-hold-race-${i}`, r);
				});
				const winner = responses.findIndex((r) => r.status === 200),
					hold = responses[winner].value;
				const persisted = await control({
					op: "r1-race-inspect",
					keys: bodies.map((b) => b.idempotencyKey),
					holdId: hold.holdId,
					released: false,
				});
				assert.equal(persisted.createReceipts, 1);
				assert.equal(persisted.blocking, true);
				assert.equal(persisted.overlapPairs, 0);
				evidence.database.push({ phase: "initial-hold-race", ...persisted });
				const release = await api(
					peers[winner],
					`/api/holds/${hold.holdId}/release`,
					"POST",
					{
						expectedRevision: hold.revision,
						idempotencyKey: crypto.randomUUID(),
					},
				);
				assert.equal(release.status, 200);
				assert.equal(release.value.state, "RELEASED");
				const released = await control({
					op: "r1-race-inspect",
					keys: bodies.map((b) => b.idempotencyKey),
					holdId: hold.holdId,
					released: true,
				});
				assert.equal(released.blocking, false);
				evidence.database.push({
					phase: "owned-race-fixture-released",
					...released,
				});
				await inspect("after-race-fixture-release");
			});
			await scenario("R1-concurrent-bank-replay", async () => {
				const replay = await Promise.all([
					api(page, "/api/bank/requests", "POST", creationBody),
					api(page, "/api/bank/requests", "POST", creationBody),
				]);
				assert.deepEqual(
					replay.map((r) => r.status),
					[200, 200],
				);
				for (const r of replay) assert.equal(r.value.id, bank.id);
				const competing = await Promise.all([
					api(page, "/api/bank/requests", "POST", {
						...creationBody,
						commandKey: crypto.randomUUID(),
					}),
					api(page, "/api/bank/requests", "POST", {
						...creationBody,
						commandKey: crypto.randomUUID(),
					}),
				]);
				assert.deepEqual(
					competing.map((r) => r.status),
					[409, 409],
				);
				const changed = await api(page, "/api/bank/requests", "POST", {
					...creationBody,
					contentHash: "0".repeat(64),
				});
				assert.equal(changed.status, 409);
				[...replay, ...competing, changed].forEach((r, i) => {
					record(`bank-concurrency-${i}`, r);
				});
				await inspect("bank-replays-and-conflicts");
			});
		} finally {
			for (const c of contexts) await c.close();
		}
		return bank;
	} finally {
		await writeFile(
			new URL("release1-evidence.json", diagnostics),
			JSON.stringify(evidence),
			{ mode: 0o600 },
		);
	}
}

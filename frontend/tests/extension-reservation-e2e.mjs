// Real stack only. Transport faults consume actual responses or drop before forwarding.
// Never installs browser routes or fabricates a business response.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { writeFile } from "node:fs/promises";
export const extensionScenarios = [
	"X-A-preview",
	"X-B-create",
	"X-C-status",
	"X-E-withdraw",
	"X-D-lost-response",
	"X-F-authorization",
	"X-G-concurrency-expiry",
	"X-D-unresolved",
];
export async function extensionReservationJourney({
	browser,
	page,
	base,
	booking,
	api,
	json,
	login,
	control,
	scenario,
	transport,
	diagnostics,
	observations,
}) {
	const path = `/api/bookings/${booking}/extension-reservations`;
	let current = page,
		created,
		body,
		key,
		first;
	const databaseObservations = [];
	const inspect = async () => {
		const value = await control({ op: "extension-inspect" });
		databaseObservations.push({
			stage: databaseObservations.length,
			reservations: value.reservations,
			active: value.active,
			creates: value.creates,
			withdrawals: value.withdrawals,
			auditCreates: value.auditCreates,
			originalFinancialAccessNotificationStateUnchanged:
				value.originalFinancialAccessNotificationStateUnchanged,
		});
		return value;
	};
	const response = (p, url, method = "GET") =>
		p.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === url && r.request().method() === method,
		);
	async function preview(p) {
		await p.goto(`${base}/bookings/${booking}`);
		const eligibility = response(
			p,
			`/api/bookings/${booking}/extension-eligibility`,
		);
		await p
			.getByRole("button", { name: "Check Extension Options", exact: true })
			.click();
		const e = await eligibility;
		assert.equal(e.status(), 200);
		assert.equal((await e.json()).eligible, true);
		await p
			.getByLabel("Additional duration (minutes)", { exact: true })
			.fill("60");
		const quoted = response(
			p,
			`/api/bookings/${booking}/extension-quotes`,
			"POST",
		);
		await p
			.getByRole("button", { name: "Preview Additional Price", exact: true })
			.click();
		const q = await quoted;
		const requestHeaders = await q.request().allHeaders();
		await writeFile(
			new URL("extension-http.json", diagnostics),
			JSON.stringify({
				status: q.status(),
				origin: requestHeaders.origin,
				host: new URL(q.url()).host,
				csrfPresent: !!requestHeaders["x-csrf-token"],
				upstream: transport.extensionHttp,
			}),
			{ mode: 0o600 },
		);
		assert.equal(q.status(), 200);
		const v = await q.json();
		assert.equal(v.additionalMinor, "100");
		assert.equal(v.currency, "LKR");
		return v;
	}
	async function reserve(p, mode) {
		await p
			.getByRole("button", {
				name: "Review Extension Reservation",
				exact: true,
			})
			.click();
		const consent = p.getByLabel(
			"I understand this reserves temporary time only; my purchased booking and payments remain unchanged.",
			{ exact: true },
		);
		await consent.waitFor();
		assert.equal(await consent.isChecked(), false);
		const button = p.getByRole("button", {
			name: "Reserve Extension Time",
			exact: true,
		});
		assert.equal(await button.isDisabled(), true);
		await consent.focus();
		await p.keyboard.press("Space");
		if (mode) transport.armExtension(mode);
		const request = p.waitForRequest(
			(r) => new URL(r.url()).pathname === path && r.method() === "POST",
		);
		await button.focus();
		await p.keyboard.press("Enter");
		body = (await request).postDataJSON();
		key = body.commandKey;
		assert.equal(body.acknowledged, true);
		assert.equal(body.expectedAdditionalMinor, "100");
		if (mode === "before-forward") {
			await p
				.getByText(/Outcome unknown\. Keep the original reference/)
				.waitFor();
			return;
		}
		const panel = p.getByRole("region", {
			name: "Extension reservation",
			exact: true,
		});
		await panel
			.getByRole("heading", { name: "Current reservation", exact: true })
			.waitFor();
		created = await inspect();
		assert.equal(created.active, 1);
		assert.equal(created.creates, created.auditCreates);
		const references = await p.evaluate(() =>
			Object.entries(sessionStorage)
				.filter(([k]) => k.startsWith("cinenest.extension.reference.v1:"))
				.map(([, v]) => JSON.parse(v)),
		);
		assert.equal(references.length, 1);
		assert.equal(references[0].commandKey, key);
		assert.ok(references[0].reservationId);
		created = await json(p, `${path}/${references[0].reservationId}`);
		assert.equal(created.state, "ACTIVE");
		assert.equal(created.bookingChanged, false);
		assert.equal(created.paymentRequested, false);
	}
	const observe = (p) =>
		p.on("response", (r) => {
			const u = new URL(r.url());
			if (u.pathname.includes("/extension-"))
				observations.push({
					method: r.request().method(),
					path: u.pathname.replace(
						/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
						"{reference}",
					),
					status: r.status(),
				});
		});
	async function fresh() {
		const c = await browser.newContext({
			viewport: { width: 1280, height: 900 },
		});
		const p = await c.newPage();
		observe(p);
		await login(p, "customer-a");
		return { c, p };
	}
	await page.waitForLoadState("networkidle");
	const otherBooking = await control({ op: "extension-cross-booking-fixture" });
	assert.equal(otherBooking.fixtureOnly, true);
	await control({ op: "extension-baseline" });
	await scenario("X-A-preview", async () => {
		await preview(page);
		const d = await inspect();
		assert.equal(d.creates, 0);
		assert.equal(d.active, 0);
	});
	await scenario("X-B-create", async () => {
		await reserve(page);
		first = created;
		assert.equal((await inspect()).creates, 1);
	});
	await scenario("X-C-status", async () => {
		assert.equal(created.bookingId, booking);
		assert.ok(Date.parse(created.expiresAt) > Date.parse(created.createdAt));
		assert.ok(
			Date.parse(created.expiresAt) - Date.parse(created.createdAt) <= 600000,
		);
		await page.reload();
		await page
			.getByRole("heading", { name: "Current reservation", exact: true })
			.waitFor();
		assert.equal(
			(await json(page, `${path}/${first.reservationId}`)).reservationId,
			first.reservationId,
		);
		const b = await json(page, `/api/bookings/${booking}`);
		await page
			.getByText(
				`Times shown in ${b.purchase.timezone}; deadlines and state are server observations.`,
				{ exact: true },
			)
			.waitFor();
		for (const width of [375, 768, 1280]) {
			await page.setViewportSize({ width, height: 900 });
			const focusTarget = page
				.getByRole("region", { name: "Extension reservation", exact: true })
				.getByRole("button", {
					name: "Reload Extension Reservation",
					exact: true,
				});
			await focusTarget.focus();
			assert.notEqual(
				await focusTarget.evaluate((el) => getComputedStyle(el).outlineStyle),
				"none",
			);
			assert.equal(
				await page.evaluate(
					() => document.documentElement.scrollWidth <= innerWidth,
				),
				true,
			);
			await page
				.getByRole("region", { name: "Extension reservation", exact: true })
				.screenshot({
					path: fileURLToPath(
						new URL(`extension-active-${width}.png`, diagnostics),
					),
				});
		}
		await inspect();
	});
	await scenario("X-E-withdraw", async () => {
		const consent = page.getByLabel(
			"I explicitly request withdrawal of this temporary extension reservation.",
			{ exact: true },
		);
		assert.equal(await consent.isChecked(), false);
		const button = page.getByRole("button", {
			name: "Withdraw Extension Reservation",
			exact: true,
		});
		assert.equal(await button.isDisabled(), true);
		await consent.check();
		const posted = response(
			page,
			`${path}/${created.reservationId}/withdrawals`,
			"POST",
		);
		await button.click();
		assert.equal((await posted).status(), 200);
		await page
			.getByRole("region", { name: "Extension reservation", exact: true })
			.getByText("WITHDRAWN", { exact: true })
			.waitFor();
		assert.equal((await inspect()).active, 0);
	});
	const second = await fresh();
	current = second.p;
	try {
		await scenario("X-D-lost-response", async () => {
			await preview(current);
			await reserve(current, "after-commit");
			assert.equal(transport.extensionReceipt.upstreamStatus, 200);
			assert.equal(transport.extensionReceipt.committedDrops, 1);
			assert.equal((await inspect()).creates, 2);
			assert.equal(transport.extensionReceipt.attempts, 2);
		});
		await scenario("X-F-authorization", async () => {
			const other = await browser.newContext();
			const p = await other.newPage();
			observe(p);
			try {
				await login(p, "customer-b");
				const denied = await api(p, `${path}/${created.reservationId}`);
				assert.equal(denied.status, 404);
				assert.equal(denied.value?.reservationId, undefined);
				assert.equal((await api(p, `${path}/commands/${key}`)).status, 404);
			} finally {
				await other.close();
			}
			const none = await browser.newContext();
			const n = await none.newPage();
			observe(n);
			try {
				await n.goto(base + "/account");
				assert.equal(
					(await api(n, `${path}/${created.reservationId}`)).status,
					401,
				);
			} finally {
				await none.close();
			}
			assert.equal(
				(
					await api(
						current,
						`${path.replace(booking, otherBooking.bookingId)}/${created.reservationId}`,
					)
				).status,
				404,
			);
			assert.equal(
				(
					await api(
						current,
						`${path}/${created.reservationId}/withdrawals`,
						"POST",
						{ expectedRevision: 0, commandKey: randomUUID() },
						false,
					)
				).status,
				403,
			);
			await control({ op: "extension-suspend", suspended: true });
			try {
				assert.equal(
					(await api(current, `${path}/${created.reservationId}`)).status,
					403,
				);
			} finally {
				await control({ op: "extension-suspend", suspended: false });
			}
			await inspect();
		});
		await scenario("X-G-concurrency-expiry", async () => {
			const replay = await Promise.all([
				api(current, path, "POST", body),
				api(current, path, "POST", body),
			]);
			for (const r of replay) {
				assert.equal(r.status, 200);
				assert.equal(r.value.reservationId, created.reservationId);
			}
			assert.equal((await inspect()).creates, 2);
			assert.equal(
				(
					await api(current, path, "POST", {
						...body,
						expectedAdditionalMinor: "101",
					})
				).status,
				409,
			);
			assert.equal(
				(
					await api(
						current,
						`${path}/${created.reservationId}/withdrawals`,
						"POST",
						{ expectedRevision: 99, commandKey: randomUUID() },
					)
				).status,
				409,
			);
			// A real competing ordinary hold against the persisted extension tail must fail.
			const b = await json(current, `/api/bookings/${booking}`);
			const start = new Date(created.additionalCoverage.start);
			const parts = new Intl.DateTimeFormat("sv-SE", {
				timeZone: b.purchase.timezone,
				year: "numeric",
				month: "2-digit",
				day: "2-digit",
				hour: "2-digit",
				minute: "2-digit",
				hour12: false,
			})
				.format(start)
				.replace(" ", "T");
			const held = await api(current, `/api/holds`, "POST", {
				roomId: b.purchase.roomId,
				sessionStartLocal: parts,
				durationMinutes: 180,
				guests: 4,
				expectedConfiguration: body.expectedConfiguration,
				idempotencyKey: randomUUID(),
			});
			assert.equal(held.status, 409);
			await control({ op: "extension-expire" });
			const expired = await json(current, `${path}/${created.reservationId}`);
			assert.equal(expired.effective, false);
			assert.equal(expired.pendingReconciliation, true);
			assert.equal((await inspect()).active, 1);
			await control({ op: "extension-reconcile" });
			assert.equal(
				(await json(current, `${path}/${created.reservationId}`)).state,
				"EXPIRED",
			);
			assert.equal((await inspect()).active, 0);
			const restarted = await control({ op: "extension-restart" });
			try {
				transport.pointBackend(restarted.backend);
				assert.equal(
					(await json(current, `${path}/${created.reservationId}`)).state,
					"EXPIRED",
				);
				assert.equal(
					(await json(current, `${path}/commands/${key}`)).reservationId,
					created.reservationId,
				);
			} finally {
				transport.restoreBackend();
				await control({ op: "extension-stop-restart" });
			}
			await inspect();
		});
	} finally {
		await second.c.close();
	}
	const third = await fresh();
	try {
		await scenario("X-D-unresolved", async () => {
			await preview(third.p);
			await reserve(third.p, "before-forward");
			const before = await inspect();
			assert.equal(before.creates, 2);
			assert.equal(before.active, 0);
			const missing = await api(third.p, `${path}/commands/${key}`);
			assert.equal(missing.status, 404);
			assert.equal(
				missing.value,
				undefined,
			); /* Sanitized proxy returns no raw backend error body. */
			const attempts = transport.extensionReceipt.attempts;
			await third.p.reload();
			await third.p
				.getByText(/Saved reference could not be resolved/)
				.waitFor();
			assert.equal(transport.extensionReceipt.attempts, attempts);
			const retained = await third.p.evaluate(() =>
				Object.entries(sessionStorage)
					.filter(([name]) =>
						name.startsWith("cinenest.extension.reference.v1:"),
					)
					.map(([, value]) => JSON.parse(value)),
			);
			assert.equal(retained.length, 1);
			assert.equal(retained[0].commandKey, key);
			assert.equal(retained[0].reservationId, undefined);
			assert.equal((await inspect()).creates, 2);
		});
	} finally {
		await third.c.close();
	}
	await inspect();
	await writeFile(
		new URL("extension-evidence.json", diagnostics),
		JSON.stringify({
			taskId: "CN-E2E-EXTENSION-RESERVATION-001",
			requirements: ["CN-C08", "CN-NFR-02", "CN-NFR-03"],
			syntheticOnly: true,
			scenarios: extensionScenarios,
			databaseObservations,
			transport: transport.extensionReceipt,
			freshBackendInstanceReadVerified: true,
			noBusinessInterception: true,
			noExtensionFundingOrAllocation: true,
			noPurchasedRevisionOrCredentialChange: true,
		}),
		{ mode: 0o600 },
	);
}

// Opt-in real-stack recovery. No mocked business responses or session injection.
import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";

export const recoveryScenarios = [
	"R1-recovery-unknown-revisit",
	"R1-recovery-refresh-cache",
	"R1-recovery-session-clearing",
];
export async function unknownRevisit({
	page,
	base,
	quote,
	creationBody,
	json,
	control,
	diagnostics,
	scenario,
}) {
	await scenario(recoveryScenarios[0], async () => {
		const tab = await page.context().newPage();
		const writes = [];
		tab.on("request", (r) => {
			if (new URL(r.url()).pathname.startsWith("/api/") && r.method() !== "GET")
				writes.push(r.method());
		});
		try {
			// A fresh tab has no in-memory command or request ID. Discover through owned history.
			await tab.goto(`${base}/bank?quote=${quote.quoteId}`);
			await tab
				.getByText(
					"A bank request already uses this hold. Open your existing request history; do not create another request.",
					{ exact: true },
				)
				.waitFor();
			await tab.reload();
			await tab
				.getByText(
					"A bank request already uses this hold. Open your existing request history; do not create another request.",
					{ exact: true },
				)
				.waitFor();
			assert.equal(
				await tab
					.getByRole("button", {
						name: "Create synthetic request",
						exact: true,
					})
					.count(),
				0,
			);
			await tab
				.getByRole("link", { name: "Open your request history", exact: true })
				.click();
			await tab
				.getByRole("button", { name: "Refresh booking history", exact: true })
				.click();
			await tab
				.getByText("Pending/unconfirmed request — not a booking", {
					exact: true,
				})
				.waitFor();
			assert.equal(
				await tab
					.getByRole("link", { name: "View booking details", exact: true })
					.count(),
				0,
			);
			const link = tab.getByRole("link", {
				name: "View Bank Request",
				exact: true,
			});
			assert.equal(await link.count(), 1);
			const href = await link.getAttribute("href");
			assert.match(href, /^\/bank\?request=[0-9a-f-]{36}$/);
			const id = new URL(href, base).searchParams.get("request");
			await link.focus();
			await tab.keyboard.press("Enter");
			await tab
				.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true })
				.waitFor();
			const value = await json(tab, `/api/bank/requests/${id}`);
			assert.equal(value.quoteId, quote.quoteId);
			assert.equal(value.requestState, "OPEN");
			assert.equal(value.reviewState, "PENDING");
			const db = await control({
				op: "r1-pending-inspect",
				quoteId: quote.quoteId,
				requestId: id,
				commandKey: creationBody.commandKey,
			});
			assert.equal(writes.length, 0);
			await writeFile(
				new URL("recovery-revisit.json", diagnostics),
				JSON.stringify({
					discoveredThroughOwnedHistory: true,
					creationMemoryReused: false,
					writeRequests: writes.length,
					database: db,
				}),
				{ mode: 0o600 },
			);
		} finally {
			await tab.close();
		}
	});
}
export async function refreshPending({
	page,
	bank,
	requests,
	control,
	quote,
	creationBody,
	diagnostics,
	scenario,
}) {
	await scenario(recoveryScenarios[1], async () => {
		const checkpoint = requests.length;
		await page.reload();
		await page.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true }).waitFor();
		await page.evaluate(() =>
			window.dispatchEvent(
				new PageTransitionEvent("pagehide", { persisted: true }),
			),
		);
		assert.equal(
			await page
				.getByRole("region", {
					name: "Current bank request summary",
					exact: true,
				})
				.count(),
			0,
		);
		const response = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === `/api/bank/requests/${bank.id}` &&
				r.request().method() === "GET",
		);
		await page.evaluate(() =>
			window.dispatchEvent(
				new PageTransitionEvent("pageshow", { persisted: true }),
			),
		);
		assert.equal((await response).status(), 200);
		await page.getByText("UNFUNDED · NOT_CONFIRMED", { exact: true }).waitFor();
		const db = await control({
			op: "r1-pending-inspect",
			quoteId: quote.quoteId,
			requestId: bank.id,
			commandKey: creationBody.commandKey,
		});
		assert.equal(
			requests.slice(checkpoint).filter((r) => r.method !== "GET").length,
			0,
		);
		await writeFile(
			new URL("recovery-refresh.json", diagnostics),
			JSON.stringify({
				hardRefresh: true,
				controlledPageLifecycle: true,
				physicalBFCacheQualification: false,
				writeRequests: 0,
				database: db,
			}),
			{ mode: 0o600 },
		);
	});
}
export async function expiredSessionRecovery({
	page,
	base,
	bank,
	login,
	api,
	scenario,
	diagnostics,
}) {
	await scenario(recoveryScenarios[2], async () => {
		const denied = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === `/api/bank/requests/${bank.id}` &&
				r.request().method() === "GET",
		);
		await page.goto(`${base}/bank?request=${bank.id}`);
		assert.equal((await denied).status(), 401);
		await page
			.getByRole("alert")
			.filter({ hasText: "Your session is unavailable" })
			.waitFor();
		assert.equal(
			await page
				.getByRole("region", {
					name: "Current bank request summary",
					exact: true,
				})
				.count(),
			0,
		);
		await page
			.getByRole("link", { name: "Return to customer history", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Refresh booking history", exact: true })
			.click();
		await page
			.getByRole("status")
			.filter({ hasText: "Unavailable, signed out, denied or changed" })
			.waitFor();
		assert.equal(
			await page
				.getByRole("link", { name: "View Bank Request", exact: true })
				.count(),
			0,
		);
		await login(page, "customer-a");
		await page.goto(`${base}/account`);
		await page
			.getByRole("button", { name: "Check session", exact: true })
			.click();
		await page
			.getByRole("button", { name: "Log out of current session", exact: true })
			.waitFor();
		const logout = page.waitForResponse(
			(r) =>
				new URL(r.url()).pathname === "/api/session/logout" &&
				r.request().method() === "POST",
		);
		await page
			.getByRole("button", { name: "Log out of current session", exact: true })
			.click();
		assert.equal((await logout).status(), 204);
		assert.equal(
			(await api(page, `/api/bank/requests/${bank.id}`)).status,
			401,
		);
		await writeFile(
			new URL("recovery-session.json", diagnostics),
			JSON.stringify({
				requestStatus: 401,
				logoutStatus: 204,
				postLogoutReadStatus: 401,
				protectedSummaryCleared: true,
				historyCleared: true,
			}),
			{ mode: 0o600 },
		);
	});
}

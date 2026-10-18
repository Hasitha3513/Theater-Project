import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	NotificationWorkspace,
	NOTIFICATION_LABEL,
	notificationRecord,
} from "../components/notifications.tsx";
import { bankRoute, forwardBank } from "../lib/bank-backend.ts";
const id = "00000000-0000-4000-8000-000000000001";
test("eight notification operations reject wrong methods and arbitrary event submission", () => {
	for (const [path, method] of [
		[`/api/notifications`, "GET"],
		[`/api/notifications/${id}`, "GET"],
		[`/api/notifications/${id}/read`, "POST"],
		[`/api/admin/venues/${id}/notifications`, "GET"],
		[`/api/admin/venues/${id}/notifications/${id}`, "GET"],
		...["retries", "reconciliations", "simulations"].map((s) => [
			`/api/admin/venues/${id}/notifications/${id}/${s}`,
			"POST",
		]),
	]) {
		assert.equal(bankRoute(path, method), true);
		assert.equal(bankRoute(path, "DELETE"), false);
		assert.equal(bankRoute(path, method === "GET" ? "POST" : "GET"), false);
	}
	assert.equal(bankRoute("/api/notifications/events", "POST"), false);
});
test("customer and administrator have labelled non-live accessible shells", () => {
	for (const admin of [false, true]) {
		const html = renderToStaticMarkup(
			React.createElement(NotificationWorkspace, { admin }),
		);
		assert.ok(html.includes(NOTIFICATION_LABEL));
		assert.ok(html.includes('aria-live="polite"'));
		assert.ok(html.includes("No email, SMS"));
		if (admin) assert.ok(html.includes("Authorized venue UUID"));
		assert.ok(!html.includes("localStorage"));
	}
});
test("transport rejects secret queries, unknown paths and cross-origin mutations before fetch", async () => {
	for (const [path, method] of [
		["/api/notifications?token=private", "GET"],
		["/api/notifications/events", "POST"],
		[`/api/notifications/${id}/read`, "POST"],
	]) {
		const r = await forwardBank(
			new Request(`https://example.test${path}`, { method }),
		);
		assert.ok([403, 404, 503].includes(r.status));
		assert.equal(r.headers.get("cache-control"), "no-store");
	}
});

test("presentation rejects live or malformed notification payloads and external destinations", () => {
	const r = {
		id,
		synthetic: true,
		liveDelivery: false,
		revision: 1,
		template_version: 1,
		state: "SIMULATOR_ACCEPTED",
		destination: "/bank",
	};
	assert.equal(notificationRecord(r), r);
	for (const drift of [
		{ synthetic: false },
		{ liveDelivery: true },
		{ destination: "https://external.test" },
		{ revision: -1 },
		{ template_version: 2 },
		{ id: "invalid" },
	])
		assert.throws(() => notificationRecord({ ...r, ...drift }));
});

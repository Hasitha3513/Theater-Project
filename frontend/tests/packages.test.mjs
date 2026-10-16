import assert from "node:assert/strict";
import { once } from "node:events";
import { createServer } from "node:http";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { PackageAdministration, PreviewView } from "../components/packages.tsx";
import { displayMoney, parseMoney } from "../lib/packages.ts";
import {
	forwardPackages,
	packageRoute,
	roomRoute,
} from "../lib/room-backend.ts";

const id = "00000000-0000-4000-8000-000000000001";
test("money parses exact minor units without floating point or rounding", () => {
	assert.equal(parseMoney("12.34"), "1234");
	assert.equal(parseMoney("0.01"), "1");
	assert.equal(displayMoney("9007199254740993"), "90071992547409.93");
	assert.equal(parseMoney("92233720368547758.07"), "9223372036854775807");
});
test("excess precision negative input exponent and overflow reject", () => {
	for (const value of ["1.234", "-1", "NaN", "1e3", "92233720368547758.08"])
		assert.throws(() => parseMoney(value));
});
test("draft page names the unavailable sales and fee boundaries", () => {
	const html = render(h(PackageAdministration));
	assert.match(html, /Package drafts/);
	assert.match(html, /requires independent publication approval/);
	assert.match(html, /Assigned venue ID/);
	assert.doesNotMatch(html, /Publish now|Checkout now/);
});
test("preview never represents unknown tax or fees as a payable total", () => {
	const html = render(
		h(PreviewView, {
			value: {
				label: "DRAFT PREVIEW - NOT FOR SALE",
				knownPackageComponentsMinor: "12345",
				taxStatus: "UNRESOLVED",
				feeStatus: "UNRESOLVED",
				options: [
					{
						serviceId: id,
						label: "HDMI equipment",
						mode: "INCLUDED",
						additionalChargeMinor: "0",
						selected: true,
					},
				],
			},
		}),
	);
	assert.match(html, /123.45/);
	assert.match(html, /not a total payable amount/);
	assert.match(html, /UNRESOLVED/);
	assert.match(html, /No additional charge/);
});
test("package adapter accepts exactly draft operations and excludes publication", () => {
	const base = `/api/admin/venues/${id}/packages`;
	for (const [path, method] of [
		[base, "GET"],
		[base, "POST"],
		[`${base}/${id}`, "GET"],
		[`${base}/${id}/draft`, "PUT"],
		[`${base}/${id}/duplicate`, "POST"],
		[`${base}/${id}/preview`, "POST"],
		[`${base}/${id}/history`, "GET"],
		[`/api/admin/venues/${id}/package-options`, "GET"],
	])
		assert.equal(packageRoute(path, method), true);
	for (const path of [
		`${base}/${id}/publish`,
		`${base}/${id}/deactivate`,
		"/api/quotes",
		"/api/bookings",
	])
		assert.equal(packageRoute(path, "POST"), false);
	assert.equal(roomRoute(base, "GET"), false);
});
test("package transport rejects cross-origin missing csrf and invalid backend configuration", async () => {
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	try {
		process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
		for (const headers of [
			{ origin: "https://other.invalid" },
			{ origin: "http://localhost" },
		])
			assert.equal(
				(
					await forwardPackages(
						new Request(`http://localhost/api/admin/venues/${id}/packages`, {
							method: "POST",
							headers,
						}),
					)
				).status,
				403,
			);
		process.env.CINENEST_AUTH_ORIGIN = "http://user:secret@127.0.0.1";
		assert.equal(
			(
				await forwardPackages(
					new Request(`http://localhost/api/admin/venues/${id}/packages`),
				)
			).status,
			503,
		);
	} finally {
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
	}
});
test("package transport uses real HTTP while excluding client authority and unrelated cookies", async () => {
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	let observed;
	const server = createServer((request, response) => {
		observed = request;
		response.writeHead(200, { "Content-Type": "application/json" });
		response.end(JSON.stringify({ items: [], total: 0, page: 0, size: 24 }));
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	try {
		process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
		const response = await forwardPackages(
			new Request(
				`http://localhost/api/admin/venues/${id}/packages?role=ADMIN`,
				{
					headers: {
						cookie: "unrelated=private; CINENEST_SESSION=synthetic",
						authorization: "Bearer ignored",
					},
				},
			),
		);
		assert.equal(response.status, 200);
		assert.equal(observed.headers.cookie, "CINENEST_SESSION=synthetic");
		assert.equal(observed.headers.authorization, undefined);
		assert.equal(observed.url.includes("role="), false);
		assert.equal(response.headers.get("cache-control"), "no-store");
	} finally {
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
		await new Promise((r) => server.close(r));
	}
});

import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { once } from "node:events";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	bookingFilters,
	offsetNanos,
	bookingFilterParams,
	filtersFromUrl,
	staffBookingRecord,
	staffBookingPage,
	staffBookingListHref,
	staffBookingDetailHref,
	loadStaffBookings,
	loadStaffBooking,
} from "../lib/staff-bookings.ts";
import {
	staffBookingRoute,
	forwardStaffBookings,
	adminRoute,
} from "../lib/admin-backend.ts";
import {
	StaffBookingList,
	StaffBookingSummary,
} from "../components/staff-bookings.tsx";
import {
	venue,
	id,
	other,
	filters,
	item,
	pageRecord,
	detailRecord,
} from "./staff-bookings-fixtures.mjs";
test("omitted range and existing bounded pagination have no invented date window", () => {
	assert.equal(bookingFilterParams(filters()).toString(), "page=0&size=10");
	for (const bad of [{ page: -1 }, { page: 10001 }, { size: 26 }, { size: 0 }])
		assert.throws(() => bookingFilters({ ...filters(), ...bad }));
});
test("offset-aware inclusive/exclusive range uses exact 31 elapsed days", () => {
	const f = {
		...filters(),
		from: "2026-10-01T05:30:00+05:30",
		to: "2026-11-01T00:00:00Z",
	};
	assert.equal(bookingFilters(f).from, f.from);
	assert.throws(() =>
		bookingFilters({ ...f, to: "2026-11-01T00:00:00.000000001Z" }),
	);
	assert.equal(offsetNanos(f.from), offsetNanos("2026-10-01T00:00:00Z"));
});
test("invalid calendar,offset,unpaired and reversed ranges are refused", () => {
	for (const from of [
		"invalid",
		"2026-02-30T00:00:00Z",
		"2026-10-01T00:00:00",
		"2026-10-01T00:00:00+19:00",
	]) {
		assert.throws(() =>
			bookingFilters({ ...filters(), from, to: "2026-10-02T00:00:00Z" }),
		);
	}
	assert.throws(() =>
		bookingFilters({ ...filters(), from: "2026-10-01T00:00:00Z" }),
	);
	assert.throws(() =>
		bookingFilters({
			...filters(),
			from: "2026-10-02T00:00:00Z",
			to: "2026-10-01T00:00:00Z",
		}),
	);
});
test("opaque exact reference and safe navigation preserve only applied filters", () => {
	const f = { ...filters(), reference: id, page: 2, size: 1 };
	assert.equal(
		filtersFromUrl(
			new URL(staffBookingListHref(f), "https://local").searchParams,
		).reference,
		id,
	);
	const link = staffBookingDetailHref(venue, id, f);
	assert.equal(
		new URL(link, "https://local").pathname,
		`/admin/bookings/venues/${venue}/${id}`,
	);
	assert.doesNotMatch(link, /evidence|token|hash|credential|amount|grant/);
	assert.throws(() => staffBookingDetailHref(other, id, f));
	assert.throws(() => bookingFilters({ ...f, reference: "../private" }));
});
test("unknown/repeated UI filters and oversized URL pages are rejected", () => {
	for (const suffix of [
		"&page=1&page=1",
		"&owner=PRIVATE",
		"&size=26",
		"&reference=bad",
	]) {
		assert.throws(() =>
			filtersFromUrl(new URLSearchParams(`venue=${venue}${suffix}`)),
		);
	}
});
test("minimal response projection drops all unknown private material", () => {
	const projected = staffBookingRecord(
		{
			...item(),
			evidence: "PRIVATE",
			receipt: "PRIVATE",
			token: "PRIVATE",
			grants: ["PRIVATE"],
		},
		venue,
		id,
	);
	assert.deepEqual(projected, item());
	assert.equal(JSON.stringify(projected).includes("PRIVATE"), false);
});
test("wrong resource or venue and unsafe synthetic/state responses fail closed", () => {
	for (const override of [
		{ venueId: other },
		{ bookingId: other },
		{ synthetic: false },
		{ realBooking: true },
		{ state: "CONFIRMED" },
		{ timezone: undefined },
		{ revision: 0 },
		{ purchasedGuestCount: 0 },
	])
		assert.throws(() =>
			staffBookingRecord({ ...item(), ...override }, venue, id),
		);
});
test("server order retained and page/scope/identity consistency checked", () => {
	const f = { ...filters(), size: 2 },
		p = { ...pageRecord(f), items: [{ ...item(), bookingId: other }, item()] };
	assert.equal(staffBookingPage(p, f).items[0].bookingId, other);
	for (const override of [
		{ page: 1 },
		{ venueId: other },
		{ items: [item(), item()] },
		{ size: 1 },
		{ hasNext: true, items: [] },
	])
		assert.throws(() => staffBookingPage({ ...p, ...override }, f));
});
test("staff proxy has only the two new GET shapes, legacy admin scope unchanged", async () => {
	const path = `/api/admin/venues/${venue}/bookings`;
	assert.ok(staffBookingRoute(path));
	assert.ok(staffBookingRoute(`${path}/${id}`));
	assert.equal(adminRoute(path), false);
	assert.equal(staffBookingRoute(`${path}/${id}/guest-roster`), false);
	for (const method of ["POST", "PUT", "DELETE"]) {
		assert.equal(
			(
				await forwardStaffBookings(
					new Request(`https://local${path}`, { method }),
				)
			).status,
			404,
		);
	}
	for (const query of ["?size=1&size=1", "?private=SECRET"]) {
		assert.equal(
			(await forwardStaffBookings(new Request(`https://local${path}${query}`)))
				.status,
			400,
		);
	}
	assert.equal(
		(
			await forwardStaffBookings(
				new Request(`https://local${path}/${id}?page=0`),
			)
		).status,
		400,
	);
});
test("authenticated read loaders use only exact protected GET contexts", async () => {
	const old = globalThis.fetch,
		calls = [];
	try {
		globalThis.fetch = async (path, init) => {
			calls.push({ path, init });
			return Response.json(path.endsWith(id) ? detailRecord() : pageRecord());
		};
		await loadStaffBookings(filters());
		await loadStaffBooking(venue, id);
		assert.equal(calls.length, 2);
		assert.equal(calls[1].path, `/api/admin/venues/${venue}/bookings/${id}`);
		assert.ok(
			calls.every((c) => c.init.cache === "no-store" && !c.init.method),
		);
		for (const status of [401, 403, 404, 400, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "PRIVATE" }, { status });
			await assert.rejects(
				loadStaffBooking(venue, id),
				(e) => e.status === status,
			);
		}
		globalThis.fetch = async () => {
			throw new DOMException("timed out", "TimeoutError");
		};
		await assert.rejects(loadStaffBookings(filters()));
	} finally {
		globalThis.fetch = old;
	}
});
test("initial UI has labelled selection and no mutation/evidence controls", () => {
	const html = renderToStaticMarkup(React.createElement(StaffBookingList));
	assert.match(html, /Assigned venue reference/);
	assert.match(html, /31 elapsed days/);
	assert.match(html, /SYNTHETIC/);
	assert.doesNotMatch(
		html,
		/type="file"|Cancel booking|Create booking|Allocate|PRIVATE/,
	);
	const summary = renderToStaticMarkup(
		React.createElement(StaffBookingSummary, { booking: item() }),
	);
	assert.match(summary, /SIMULATED_CONFIRMED/);
	assert.match(summary, /Purchased guest count/);
});
test("real HTTP proxy transport restricts cookies,headers,redirects and private errors", async () => {
	let received;
	const server = createServer((req, res) => {
		received = { url: req.url, headers: req.headers };
		res.setHeader("X-Correlation-ID", other);
		if (req.url.includes("page=1")) {
			res.writeHead(403);
			return res.end("PRIVATE EVIDENCE");
		}
		if (req.url.includes("page=2")) {
			res.writeHead(302, { Location: "https://example.invalid/private" });
			return res.end();
		}
		res.setHeader("Content-Type", "application/json");
		res.end(JSON.stringify(pageRecord()));
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = `http://127.0.0.1:${server.address().port}`;
	try {
		const path = `https://local/api/admin/venues/${venue}/bookings`;
		const response = await forwardStaffBookings(
			new Request(`${path}?page=0&size=10`, {
				headers: {
					Cookie: "UNRELATED=PRIVATE; CINENEST_SESSION=SYNTHETIC-ONLY",
					Authorization: "PRIVATE",
					"X-Role": "PRIVATE",
				},
			}),
		);
		assert.equal(response.status, 200);
		assert.equal(response.headers.get("cache-control"), "private, no-store");
		assert.equal(response.headers.get("x-correlation-id"), other);
		assert.equal(received.headers.cookie, "CINENEST_SESSION=SYNTHETIC-ONLY");
		assert.equal(received.headers.authorization, undefined);
		assert.equal(received.headers["x-role"], undefined);
		const denied = await forwardStaffBookings(new Request(`${path}?page=1`));
		assert.equal(denied.status, 403);
		assert.equal(await denied.text(), "");
		assert.equal(
			(await forwardStaffBookings(new Request(`${path}?page=2`))).status,
			502,
		);
	} finally {
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
		server.closeAllConnections();
		await new Promise((r) => server.close(r));
	}
});

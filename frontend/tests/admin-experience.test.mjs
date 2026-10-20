import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	adminRouteContext,
	adminSection,
	isActive,
	adminNavigation,
} from "../lib/navigation.ts";
import { AdminBreadcrumb, ShellFrame } from "../components/shell.tsx";
import { StaffVenueContext } from "../components/staff-venue-context.tsx";
const venue = "00000000-0000-4000-8000-000000000001",
	id = "00000000-0000-4000-8000-000000000002";
const render = (C, p) => renderToStaticMarkup(React.createElement(C, p));
test("booking and review details retain exactly one matching parent section", () => {
	for (const kind of ["bookings", "verification", "bank"]) {
		const path = `/admin/${kind}/venues/${venue}/${kind === "bookings" ? id : "requests/" + id}`;
		assert.equal(adminSection(path)[0], `/admin/${kind}`);
		assert.equal(
			adminNavigation.filter(([href]) => isActive(path, href)).length,
			1,
		);
	}
	assert.equal(adminSection("/admin/bank-other"), undefined);
	assert.equal(adminSection("/administrator"), undefined);
});
test("breadcrumb queue links carry only the valid venue selection not request or private data", () => {
	for (const kind of ["bookings", "verification", "bank"]) {
		const path = `/admin/${kind}/venues/${venue}/${kind === "bookings" ? id : "requests/" + id}`;
		const c = adminRouteContext(path);
		assert.equal(c.href, `/admin/${kind}?venue=${venue}`);
		assert.equal(c.venue, venue);
		assert.equal(c.detail, true);
		assert.ok(!c.href.includes(id));
	}
});
test("malformed detail paths cannot reflect arbitrary return addresses or venue text", () => {
	for (const path of [
		"/admin/bank/venues/private-secret/requests/" + id,
		"/admin/bank/venues/" +
			venue +
			"/requests/" +
			id +
			"?return=https://evil.example",
	]) {
		const c = adminRouteContext(path);
		assert.equal(c.venue, undefined);
		assert.equal(c.href, "/admin/bank");
		assert.equal(c.detail, false);
	}
});
test("shell owns one structural breadcrumb and no extra page heading", () => {
	const s = render(ShellFrame, {
		admin: true,
		path: `/admin/bank/venues/${venue}/requests/${id}`,
		children: React.createElement("h1", null, "Bank review details"),
	});
	assert.equal(
		(s.match(/aria-label="Administration breadcrumbs"/g) || []).length,
		1,
	);
	assert.equal((s.match(/<main/g) || []).length, 1);
	assert.equal((s.match(/<h1/g) || []).length, 1);
	assert.match(s, /aria-current="location"/);
	assert.match(s, /Selection only/);
	assert.doesNotMatch(s, /credit received|PIN|token=/);
});
test("selected venue is explicitly non-authoritative and invalid input is not reflected", () => {
	const s = render(StaffVenueContext, { venue });
	assert.match(s, /Selection is not authorization/);
	assert.match(s, /current permission and resource scope/);
	const invalid = render(StaffVenueContext, { venue: "PRIVATE SECRET" });
	assert.doesNotMatch(invalid, /PRIVATE SECRET/);
	assert.match(invalid, /Select an assigned venue reference/);
});
test("customer shell has no admin breadcrumb and admin home does not fabricate a detail venue", () => {
	const s = render(ShellFrame, {
		admin: false,
		path: "/rooms",
		children: React.createElement("h1", null, "Rooms"),
	});
	assert.doesNotMatch(s, /Administration breadcrumbs/);
	const admin = render(AdminBreadcrumb, { path: "/admin" });
	assert.doesNotMatch(admin, /Route venue|Details/);
});

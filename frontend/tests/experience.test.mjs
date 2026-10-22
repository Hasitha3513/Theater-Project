import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { isActive } from "../lib/navigation.ts";
import Home from "../app/page.tsx";
import Admin from "../app/admin/page.tsx";
const source = (p) => readFileSync(new URL(p, import.meta.url), "utf8");
test("active sections match segment boundaries without matching the admin root everywhere", () => {
	assert.ok(isActive("/rooms/a", "/rooms"));
	assert.ok(isActive("/admin/bookings/venues/v/b", "/admin/bookings"));
	assert.ok(
		isActive("/admin/verification/venues/v/requests/r", "/admin/verification"),
	);
	assert.ok(isActive("/admin", "/admin"));
	for (const [p, l] of [
		["/rooms/a", "/"],
		["/admin/bookings", "/admin"],
		["/admin/accounts", "/admin/account"],
		["/rooms-other", "/rooms"],
	])
		assert.equal(isActive(p, l), false);
});
test("customer landing retains the real room-first routes and required safety boundaries", () => {
	const s = render(h(Home));
	assert.match(s, /Private cinema\. Your way/);
	assert.equal((s.match(/href="\/rooms"/g) || []).length, 3);
	for (const t of [
		"Synthetic bookings only",
		"Do not transfer real money",
		"PayHere is deferred",
		"production booking is not authorized",
		"No rewards or discounts are active",
	])
		assert.ok(s.includes(t));
	assert.doesNotMatch(s, /Development preview|<form|Pay now|Unlock door/);
});
test("staff home links existing tools without fabricated operational data or authority", () => {
	const s = render(h(Admin));
	for (const p of [
		"/admin/bookings",
		"/admin/rooms",
		"/admin/settings/packages",
		"/admin/verification",
		"/admin/bank",
		"/admin/operations",
		"/admin/account",
		"/admin/reports/bank",
	])
		assert.ok(s.includes(`href="${p}"`));
	assert.match(s, /Synthetic operations only/);
	assert.match(s, /Additional operational dashboard metrics are not available/);
	assert.match(s, /Receipt review does not establish verified funds/);
	assert.doesNotMatch(
		s,
		/Business modules are not connected|Shell preview|Today.s revenue/,
	);
	// The owner-authorized summary now has explicit read filters, never mutation forms.
	assert.equal((s.match(/<form/g) ?? []).length, 3);
	assert.match(s, /Load Bank Summary/);
	assert.match(s, /Synthetic Booking Confirmations/);
	assert.match(s, /Load statistics/);
	assert.match(s, /No financial data loaded/);
	assert.doesNotMatch(
		s,
		/Complete-population summary|Confirm payment|Allocate funds/,
	);
});
test("shell keeps unqualified identity, banking, hardware and production boundaries explicit", () => {
	const s = source("../components/shell.tsx");
	for (const t of [
		"Synthetic environment",
		"test identity verification only",
		"Real payments, physical access and equipment controls are not enabled",
		"Production operations are not authorized",
		"Tools remain synthetic",
	])
		assert.ok(s.includes(t));
	assert.doesNotMatch(
		s,
		/Development preview|Future modules|Admin shell preview/,
	);
	assert.match(s, /path === href\s*\? "page"\s*: "location"/);
});
test("metadata describes existing discovery without commercial readiness or indexing changes", () => {
	const s = source("../app/layout.tsx");
	assert.match(s, /CineNest · Private cinema/);
	assert.match(s, /Synthetic booking environment/);
	assert.match(s, /index: false, follow: false/);
	assert.doesNotMatch(s, /Customer services are not available/);
});
test("contracted financial and identity disclosures remain visible in their owning screens", () => {
	for (const p of ["bank", "verification", "quotes"]) {
		const s = source(`../lib/${p}.ts`);
		assert.match(s, /SYNTHETIC/);
	}
	assert.match(source("../components/bank.tsx"), /bankLabels.map/);
	assert.match(
		source("../components/verification.tsx"),
		/verificationLabels.map/,
	);
	assert.match(source("../components/quotes.tsx"), /quoteDisclosures.map/);
});

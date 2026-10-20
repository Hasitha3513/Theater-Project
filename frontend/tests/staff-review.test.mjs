import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	reviewReference,
	reviewHref,
	reviewQueueHref,
	reviewApiPath,
	reviewRecord,
	loadStaffReview,
} from "../lib/staff-review.ts";
import { verificationLabels } from "../lib/verification.ts";
import { bankLabels } from "../lib/bank.ts";
import { StaffReviewDetail } from "../components/staff-review-detail.tsx";
const id = "00000000-0000-4000-8000-000000000001",
	other = "00000000-0000-4000-8000-000000000002";
const verification = () => ({
	id,
	venueId: other,
	quoteId: id,
	revision: 1,
	generation: 1,
	superseded: false,
	previousId: null,
	state: "REVIEW_REQUIRED",
	validUntil: null,
	synthetic: true,
	physicalAdmission: false,
	labels: verificationLabels,
	evidence: [],
	history: [],
});
const bank = () => ({
	id,
	venueId: other,
	quoteId: id,
	revision: 1,
	synthetic: true,
	paymentVerified: false,
	bookingConfirmed: false,
	labels: bankLabels,
	requestState: "REVIEW_PENDING",
	reviewState: "PENDING",
	inventoryState: "ACTIVE",
	inventoryExpiresAt: "2026-10-21T10:00:00Z",
	expiresAt: "2026-10-21T10:00:00Z",
	submitBy: "2026-10-21T09:00:00Z",
	reviewBy: "2026-10-21T09:30:00Z",
	overdue: false,
	amountMinor: "10000",
	currency: "LKR",
	instructions: "SYNTHETIC ONLY — NO RECEIVING ACCOUNT",
	claimId: null,
	evidence: [],
	history: [],
});

for (const kind of ["verification", "bank"]) {
	test(`${kind}: exact opaque navigation and queue recovery`, () => {
		assert.equal(
			reviewHref(kind, other, id),
			`/admin/${kind}/venues/${other}/requests/${id}`,
		);
		assert.equal(reviewQueueHref(kind, other), `/admin/${kind}?venue=${other}`);
		assert.equal(
			reviewApiPath(kind, other, id),
			`/api/admin/venues/${other}/${kind}/requests/${id}`,
		);
		assert.doesNotMatch(
			reviewHref(kind, other, id),
			/hash|evidence|receipt|token|amount|grant|secret|\?/i,
		);
	});
	test(`${kind}: no client or response scope substitution`, () => {
		const v = kind === "verification" ? verification() : bank();
		assert.equal(reviewRecord(kind, v, other, id).revision, 1);
		assert.throws(() => reviewRecord(kind, v, id, id));
		assert.throws(() => reviewRecord(kind, v, other, other));
	});
	test(`${kind}: authoritative processed state and synthetic restrictions`, () => {
		const v =
			kind === "verification"
				? { ...verification(), state: "REJECTED", superseded: true }
				: { ...bank(), requestState: "CANCELLED", reviewState: "REJECTED" };
		assert.equal(reviewRecord(kind, v, other, id).revision, 1);
		assert.throws(() =>
			reviewRecord(kind, { ...v, synthetic: false }, other, id),
		);
	});
	test(`${kind}: no initial protected data or review/financial forms`, () => {
		const html = renderToStaticMarkup(
			React.createElement(StaffReviewDetail, {
				kind,
				venue: other,
				request: id,
			}),
		);
		assert.match(html, /Read-only synthetic review context/);
		assert.match(html, /Return to review queue/);
		assert.doesNotMatch(
			html,
			/<form|type="file"|Server-authoritative review context/,
		);
	});
}
test("malformed/path-injected references rejected before any transport", () => {
	for (const bad of [
		"",
		"../secrets",
		`${id}\n`,
		`${id}?token=SECRET`,
		`${id}/evidence`,
		"not-a-uuid",
	]) {
		assert.throws(() => reviewReference(bad));
		assert.throws(() => reviewHref("bank", other, bad));
	}
});
test("protected loader GET/no-store and denial propagation; no mutations", async () => {
	const previous = globalThis.fetch;
	const calls = [];
	try {
		globalThis.fetch = async (path, init) => {
			calls.push({ path, init });
			return Response.json(bank());
		};
		const controller = new AbortController();
		assert.equal(
			(await loadStaffReview("bank", other, id, controller.signal)).id,
			id,
		);
		assert.equal(calls.length, 1);
		assert.equal(calls[0].path, reviewApiPath("bank", other, id));
		assert.equal(calls[0].init.cache, "no-store");
		assert.equal(calls[0].init.signal, controller.signal);
		assert.equal(calls[0].init.method, undefined);
		for (const status of [401, 403, 404, 409, 503]) {
			globalThis.fetch = async () =>
				Response.json({ message: "PRIVATE" }, { status });
			await assert.rejects(
				loadStaffReview("verification", other, id),
				(e) => e.status === status,
			);
		}
	} finally {
		globalThis.fetch = previous;
	}
});

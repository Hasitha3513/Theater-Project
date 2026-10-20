import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
	VerificationStages,
	VerificationReviewGuidance,
} from "../components/verification-stages.tsx";
const render = (Component, props = {}) =>
	renderToStaticMarkup(React.createElement(Component, props));
test("verification destinations never imply completed milestones", () => {
	const s = render(VerificationStages);
	assert.match(s, /href="#verification-context"/);
	assert.match(s, /href="#verification-permission"/);
	assert.doesNotMatch(s, /aria-current|complete|checked=""/);
	assert.match(s, /Steps do not indicate approval/);
});
test("pending digital states explain that submission is not approval", () => {
	for (const state of [
		"NOT_SUBMITTED",
		"SUBMITTED",
		"PROCESSING",
		"REVIEW_REQUIRED",
	]) {
		const s = render(VerificationReviewGuidance, { state, superseded: false });
		assert.match(s, new RegExp(state));
		assert.match(s, /role="status"/);
		assert.doesNotMatch(s, /<button|<a |<img/);
	}
});
test("rejected expired revoked and superseded reviews offer no implicit approval", () => {
	for (const state of ["REJECTED", "EXPIRED", "REVOKED"])
		assert.match(
			render(VerificationReviewGuidance, { state, superseded: false }),
			/server reports/i,
		);
	assert.match(
		render(VerificationReviewGuidance, { state: "VERIFIED", superseded: true }),
		/superseded/,
	);
});
test("verified synthetic decision is not physical admission or bank readiness", () => {
	const s = render(VerificationReviewGuidance, {
		state: "VERIFIED",
		superseded: false,
	});
	assert.match(s, /not physical admission approval/);
	assert.match(s, /bank-request prerequisites are checked separately/);
	assert.doesNotMatch(s, /href=|<button/);
});
test("unknown presentation state requests reload rather than inventing eligibility", () => {
	assert.match(
		render(VerificationReviewGuidance, {
			state: "UNRECOGNIZED",
			superseded: false,
		}),
		/Reload the protected request/,
	);
});

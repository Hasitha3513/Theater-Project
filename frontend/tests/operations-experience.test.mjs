import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { readFileSync } from "node:fs";
import { OperationsWorkspace } from "../components/operations.tsx";
import { BankReportWorkspace } from "../components/bank-report.tsx";
import { Field, StateMessage } from "../components/primitives.tsx";
import { ShellFrame } from "../components/shell.tsx";
for (const [Component, path] of [
	[OperationsWorkspace, "/admin/operations"],
	[BankReportWorkspace, "/admin/reports/bank"],
]) {
	test(`${path} retains exactly one shell main and page heading`, () => {
		const html = render(
			React.createElement(
				ShellFrame,
				{ admin: true, path },
				React.createElement(Component),
			),
		);
		assert.equal((html.match(/<main\b/g) || []).length, 1);
		assert.equal((html.match(/<h1\b/g) || []).length, 1);
		assert(html.includes('role="status" aria-atomic="true"'));
		assert(
			html.includes(
				'for="' +
					(path.includes("reports") ? "report" : "operations") +
					'-venue"',
			),
		);
		assert(html.includes("SYNTHETIC"));
	});
}
test("checkbox preserves native state, label, explicit consent and error association", () => {
	const html = render(
		React.createElement(Field, {
			id: "consent",
			label: "I consent",
			type: "checkbox",
			checked: false,
			onChange: () => {},
			error: "Confirm only when ready",
		}),
	);
	assert(html.includes("field-checkbox"));
	assert(html.includes('for="consent"'));
	assert(!html.includes('checked=""'));
	assert(html.includes('aria-describedby="consent-help"'));
	assert(html.includes('aria-invalid="true"'));
	const text = render(
		React.createElement(Field, { id: "name", label: "Name" }),
	);
	assert(!text.includes("field-checkbox"));
});
test("shared alerts preserve urgent versus nonurgent semantics", () => {
	for (const kind of ["loading", "empty", "denied", "error"]) {
		const html = render(React.createElement(StateMessage, { kind }));
		assert(html.includes('aria-atomic="true"'));
		assert(
			html.includes(
				kind === "denied" || kind === "error"
					? 'role="alert"'
					: 'role="status"',
			),
		);
	}
});
test("operations and report business handler prefixes remain exact baseline bytes", () => {
	for (const name of ["operations", "bank-report"]) {
		const path = `frontend/components/${name}.tsx`,
			now = readFileSync(`../${path}`, "utf8"),
			old = execFileSync(
				"git",
				["show", `0dfaba067e3f75ba67348cb48a66a16011e8d3cc:${path}`],
				{ encoding: "utf8" },
			);
		// Imports may add presentation components; everything from the original constant
		// through request/authorization/state logic must remain exact.
		assert.equal(
			now.slice(now.indexOf("export const"), now.lastIndexOf("\n\treturn (")),
			old.slice(old.indexOf("export const"), old.lastIndexOf("\n\treturn (")),
		);
	}
});
import { execFileSync } from "node:child_process";

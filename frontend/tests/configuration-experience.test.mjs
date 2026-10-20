import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	ConfigurationSummary,
	configurationValue,
	configurationFields,
} from "../components/configuration-summary.tsx";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
const view = (props) =>
	render(React.createElement(ConfigurationSummary, props));
test("package saved versions expose only the explicit field vocabulary", () => {
	const html = view({
		kind: "package",
		value: {
			name: "SYNTHETIC package",
			revision: 2,
			basePriceMinor: "9007199254740993",
			maximumGuests: 5,
			privateEvidence: "SECRET_SENTINEL",
			permittedActions: ["SECRET_SENTINEL"],
			options: [
				{
					serviceId: "SYNTHETIC",
					rateVersionId: "REV1",
					mode: "INCLUDED",
					secret: "SECRET_SENTINEL",
				},
			],
		},
	});
	assert(html.includes("90071992547409.93"));
	assert(html.includes("Maximum guests"));
	assert(!html.includes("SECRET_SENTINEL"));
	assert(!html.includes("<input"));
	assert(!html.includes("<pre"));
});
test("changed fields have text markers and both saved values without implying unsaved comparison", () => {
	const html = view({
		kind: "package",
		value: { name: "SYNTHETIC new", basePriceMinor: "200", revision: 2 },
		previous: { name: "SYNTHETIC old", basePriceMinor: "100", revision: 1 },
	});
	assert.equal((html.match(/class="configuration-change"/g) || []).length, 3);
	assert(html.includes("Changed"));
	assert(html.includes("Previously loaded: 1.00"));
	assert(html.includes("Latest saved: 2.00"));
	assert(html.includes("Unsaved form inputs are excluded"));
});
test("unknown field differences do not create changes or reveal their contents", () => {
	const html = view({
		kind: "room",
		value: { name: "Same", private: "NEW_SECRET" },
		previous: { name: "Same", private: "OLD_SECRET" },
	});
	assert(!html.includes("is-changed"));
	assert(!html.includes("SECRET"));
});
test("room facilities and image references remain text with nested unknown fields omitted", () => {
	const html = view({
		kind: "room",
		value: {
			capacity: 4,
			facilities: [{ code: "HDMI", label: "HDMI input", private: "SECRET" }],
			images: [
				{
					assetPath: "cinema/test.webp",
					altText: "Synthetic concept",
					private: "SECRET",
				},
			],
		},
	});
	assert(html.includes("HDMI input (HDMI)"));
	assert(html.includes("Synthetic concept"));
	assert(!html.includes("SECRET"));
	assert(!html.includes("<img"));
	assert(!html.includes("href="));
});
test("venue windows and buffer are explicit local configuration without invented readiness", () => {
	const html = view({
		kind: "venue",
		value: {
			timezone: "Asia/Colombo",
			windows: [{ weekday: 1, openingMinute: 610, closingMinute: 1440 }],
			cleaningBufferMinutes: null,
		},
	});
	assert(html.includes("Monday: 10:10–24:00 (venue local)"));
	assert(html.includes("Not configured"));
	assert(html.includes("does not establish bookable availability"));
});
test("missing and malformed amounts/numbers never fabricate zero or throw", () => {
	for (const value of [undefined, {}, "NaN", "-1", 100])
		assert.equal(configurationValue(value, "money"), "Not supplied");
	assert.equal(configurationValue("0", "money"), "0.00");
	assert.equal(configurationValue(false, "boolean"), "No");
	assert.equal(configurationValue(null, "money"), "Not configured");
	assert.equal(
		configurationValue({ private: "SECRET" }, "text"),
		"Not supplied",
	);
	assert(configurationFields.package.every((x) => x.length === 3));
});
test("publication,package and room business handlers remain baseline exact", () => {
	for (const name of ["publications", "packages", "rooms"]) {
		const path = `frontend/components/${name}.tsx`,
			now = readFileSync(`../${path}`, "utf8"),
			old = execFileSync(
				"git",
				["show", `f795c7b66cb0adf3a3dbbbbdfc4f0cbe34e8b42d:${path}`],
				{ encoding: "utf8" },
			);
		// All component logic before each JSX return is unchanged (presentation imports excluded).
		for (const signature of name === "rooms"
			? ["function ConfigurationEditor("]
			: name === "packages"
				? [
						"export function PackageAdministration(",
						"function DraftEditor(",
						"function HistoryView(",
					]
				: ["export function PublicationAdministration("]) {
			const a = now.indexOf(signature),
				b = old.indexOf(signature);
			assert(a >= 0 && b >= 0);
			assert.equal(
				now.slice(a, now.indexOf("\n\treturn (", a)),
				old.slice(b, old.indexOf("\n\treturn (", b)),
			);
		}
	}
});

test("historical comparison never calls an old revision the current live version", () => {
	const html = view({
		kind: "package",
		value: { revision: 3 },
		previous: { revision: 1 },
		historical: true,
	});
	assert(html.includes("This saved snapshot: 3"));
	assert(html.includes("Older loaded snapshot: 1"));
	assert(html.includes("neither is asserted to be the current live version"));
	assert(!html.includes("Latest saved:"));
});

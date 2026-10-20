import { test } from "node:test";
import assert from "node:assert/strict";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { RoomSummary, observedFacilities } from "../components/rooms.tsx";
import { readFileSync } from "node:fs";
const id = "00000000-0000-4000-8000-000000000001";
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "SYNTHETIC room",
	description: "Synthetic HDMI room",
	capacity: 4,
	facilities: [{ code: "HDMI", label: "HDMI input", sortOrder: 0 }],
	images: [],
};
test("customer media leads a factual room summary and explicit detail link", () => {
	const html = render(h(RoomSummary, { room, admin: false }));
	assert.ok(html.indexOf("photograph unavailable") < html.indexOf("<h2>"));
	assert.match(html, /Up to 4 guests/);
	assert.match(html, /HDMI input/);
	assert.match(html, new RegExp(`href="/rooms/${id}"`));
	assert.doesNotMatch(html, /LKR|Available now|Book now|<form|<button/);
});
test("detail actions are section navigation, never financial or hold submission", () => {
	const html = render(h(RoomSummary, { room, admin: false, detail: true }));
	assert.match(html, /href="#booking-entry"/);
	assert.match(html, /href="#room-packages"/);
	assert.doesNotMatch(html, /href="\/rooms\/|<form|<button/);
});
test("missing photos and facilities remain missing, never generated benefits", () => {
	const html = render(
		h(RoomSummary, { room: { ...room, facilities: [] }, admin: false }),
	);
	assert.match(html, /Room photograph unavailable/);
	assert.match(html, /No facilities recorded/);
	assert.doesNotMatch(html, /<img|Netflix|4K|Popcorn/);
});
test("backend facility labels are deduplicated and sorted without inventing a facet inventory", () => {
	assert.deepEqual(
		observedFacilities([
			room,
			room,
			{
				...room,
				facilities: [{ code: "A", label: "Audio input", sortOrder: 1 }],
			},
		]).map((f) => [f.code, f.label]),
		[
			["A", "Audio input"],
			["HDMI", "HDMI input"],
		],
	);
	assert.deepEqual(observedFacilities([]), []);
});
test("room names/descriptions/facility labels remain escaped", () => {
	const html = render(
		h(RoomSummary, {
			room: {
				...room,
				name: "<script>x</script>",
				description: "<img src=x>",
				facilities: [{ code: "X", label: "<svg onload=x>", sortOrder: 0 }],
			},
			admin: false,
		}),
	);
	assert.doesNotMatch(html, /<script>|<img src=x>|<svg onload/);
	assert.match(html, /&lt;script&gt;/);
});
test("admin permissions and catalog state remain separate from customer discovery", () => {
	const html = render(
		h(RoomSummary, {
			room: { ...room, enabled: true, configured: true, permittedActions: [] },
			admin: true,
		}),
	);
	assert.match(html, /Catalog enabled/);
	assert.doesNotMatch(html, /Edit room|discovery-room-body/);
});
test("discovery styling consumes defined tokens and preserves existing theme asset", () => {
	const css = readFileSync(
		new URL("../app/globals.css", import.meta.url),
		"utf8",
	).split("/* Customer discovery:")[1];
	const theme = readFileSync(
		new URL("../../knowledge/styles/cinenest-theme.css", import.meta.url),
		"utf8",
	);
	for (const variable of css.matchAll(/var\((--cn-[^)]+)\)/g))
		assert.ok(theme.includes(variable[1] + ":"), variable[1]);
	assert.match(css, /auto-fill/);
	assert.match(css, /aspect-ratio: 16 \/ 9/);
});

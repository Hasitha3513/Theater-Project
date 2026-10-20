import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, statSync } from "node:fs";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	CinemaIllustration,
	CinemaConceptGallery,
	cinemaAssets,
} from "../components/cinema-imagery.tsx";
test("five generated concepts have descriptive non-venue alt text", () => {
	assert.deepEqual(Object.keys(cinemaAssets), [
		"hero",
		"violet",
		"blue",
		"amber",
		"recliners",
	]);
	for (const item of Object.values(cinemaAssets))
		assert.match(
			item.alt,
			/AI-generated concept.*cinema|AI-generated concept.*screening/,
		);
});
test("source PNGs retain original dimensions and three real WebP variants per image", () => {
	for (const name of Object.keys(cinemaAssets)) {
		const source = new URL(
			`../public/cinema/sources/${name}.png`,
			import.meta.url,
		);
		const b = readFileSync(source);
		assert.equal(b.readUInt32BE(16), 1672);
		assert.equal(b.readUInt32BE(20), 941);
		for (const width of [480, 960, 1672]) {
			const file = new URL(
					`../public/cinema/${name}-${width}.webp`,
					import.meta.url,
				),
				webp = readFileSync(file);
			assert.equal(webp.subarray(0, 4).toString(), "RIFF");
			assert.equal(webp.subarray(8, 12).toString(), "WEBP");
			assert.ok(statSync(file).size < statSync(source).size);
		}
	}
});
test("responsive illustration exposes width descriptors, dimensions and explicit provenance", () => {
	const html = render(
		h(CinemaIllustration, { asset: "amber", roomFallback: true }),
	);
	assert.match(html, /amber-480.webp 480w/);
	assert.match(html, /amber-960.webp 960w/);
	assert.match(html, /amber-1672.webp 1672w/);
	assert.match(html, /width="1672"/);
	assert.match(html, /Room photograph unavailable/);
	assert.match(html, /AI-generated illustration/);
	assert.match(html, /Not a photograph of this venue or room/);
});
test("only hero eagerly prioritizes image loading; room illustrations remain lazy", () => {
	assert.match(
		render(h(CinemaIllustration, { asset: "hero", hero: true })),
		/fetchPriority="high"/,
	);
	assert.match(
		render(h(CinemaIllustration, { asset: "blue" })),
		/loading="lazy"/,
	);
});
test("gallery controls are explicit pressed buttons, not room or operational records", () => {
	const html = render(h(CinemaConceptGallery));
	assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1);
	assert.equal((html.match(/aria-pressed="false"/g) || []).length, 3);
	assert.match(html, /Illustrative cinema concepts/);
	assert.doesNotMatch(html, /<form|Available now|LKR|Book now|\/api\//);
});

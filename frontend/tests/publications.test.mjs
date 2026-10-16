import assert from "node:assert/strict";
import { test } from "node:test";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import {
	IndicativeCatalog,
	PublicationAdministration,
} from "../components/publications.tsx";
import {
	forwardPublications,
	packageRoute,
	publicationRoute,
	roomRoute,
} from "../lib/room-backend.ts";

const id = "00000000-0000-4000-8000-000000000001",
	base = `/api/admin/venues/${id}/packages/${id}`;
test("publication adapter allows exactly specified operations and rejects nearby business routes", () => {
	for (const [path, method] of [
		[`${base}/publication-preview`, "POST"],
		[`${base}/publications`, "POST"],
		[`${base}/publications`, "GET"],
		[`${base}/publications/${id}`, "GET"],
		[`${base}/publications/${id}/deactivate`, "POST"],
		[`${base}/publications/${id}/retry`, "POST"],
		[`/api/venues/${id}/packages`, "GET"],
		[`/api/venues/${id}/packages/${id}`, "GET"],
	])
		assert.equal(publicationRoute(path, method), true);
	for (const suffix of [
		"publish",
		"quotes",
		"checkout",
		"publications/other",
		`publications/${id}/delete`,
	])
		assert.equal(publicationRoute(`${base}/${suffix}`, "POST"), false);
	assert.equal(publicationRoute(`${base}/publications`, "PUT"), false);
	assert.equal(packageRoute(`${base}/publications`, "POST"), false);
	assert.equal(roomRoute(`${base}/publications`, "POST"), false);
});
test("public catalog declares unresolved charges and no purchase controls", () => {
	const html = render(h(IndicativeCatalog, { venueId: id, roomId: id }));
	assert.match(html, /Tax and checkout fees unresolved/);
	assert.match(html, /not payable quotations/);
	assert.doesNotMatch(html, /<button[^>]*>.*(?:Pay now|Book now)/);
});
test("publication controls start collapsed with accessible disclosure", () => {
	const html = render(
		h(PublicationAdministration, {
			draft: { packageId: id, venueId: id, code: "SYNTHETIC" },
		}),
	);
	assert.match(html, /aria-expanded="false"/);
	assert.match(html, /Publications SYNTHETIC/);
	assert.doesNotMatch(html, /Approve reviewed publication/);
});
test("publication writes enforce same-origin CSRF before transport", async () => {
	const prior = process.env.CINENEST_AUTH_ORIGIN;
	process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9";
	try {
		const r = await forwardPublications(
			new Request(`http://localhost${base}/publications`, {
				method: "POST",
				headers: { origin: "http://evil.example" },
				body: "{}",
			}),
		);
		assert.equal(r.status, 403);
	} finally {
		if (prior === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = prior;
	}
});

import { catalogItems } from "../lib/publications.ts";

test("malformed catalog data fails safely before exact-price formatting", () => {
	for (const d of [
		null,
		{ items: [] },
		{ items: [{ name: "room", capacity: 5 }], purchasable: false },
	])
		assert.throws(() => catalogItems(d));
	assert.deepEqual(catalogItems({ items: [], purchasable: false }), []);
});

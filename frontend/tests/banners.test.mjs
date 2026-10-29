import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { bannerRoute } from "../lib/room-backend.ts";

const venue = "11111111-1111-1111-1111-111111111111",
	banner = "22222222-2222-2222-2222-222222222222";
test("exact advertising routes reject unrelated and executable targets", () => {
	const root = `/api/admin/venues/${venue}/banners`;
	for (const [p, m] of [
		[root, "POST"],
		[root, "GET"],
		[`${root}/${banner}`, "PUT"],
		[`${root}/${banner}/preview`, "GET"],
		[`${root}/${banner}/publications`, "POST"],
		["/api/catalog/banners", "GET"],
	])
		assert.equal(bannerRoute(p, m), true);
	for (const p of [
		`${root}/../bank`,
		`${root}/${banner}/delete`,
		"https://evil.test/api/catalog/banners",
		"/api/catalog/banners/other",
	])
		assert.equal(bannerRoute(p, "GET"), false);
	assert.equal(bannerRoute("/api/catalog/banners", "POST"), false);
});
test("approved illustrative bytes match the backend immutable catalogue", async () => {
	const source = await readFile(
		new URL(
			"../../backend/src/main/java/com/cinenest/rooms/internal/BannerAssets.java",
			import.meta.url,
		),
		"utf8",
	);
	let count = 0;
	for (const match of source.matchAll(
		/new Asset\("([a-z]+)",1,"(\/cinema\/[a-z]+-960.webp)","([a-f0-9]{64})",(\d+)\)/g,
	)) {
		const data = await readFile(
			new URL(`../public${match[2]}`, import.meta.url),
		);
		assert.equal(data.subarray(0, 4).toString(), "RIFF");
		assert.equal(data.subarray(8, 12).toString(), "WEBP");
		assert.equal(createHash("sha256").update(data).digest("hex"), match[3]);
		assert.equal(data.length, Number(match[4]));
		count++;
	}
	assert.equal(count, 5);
});

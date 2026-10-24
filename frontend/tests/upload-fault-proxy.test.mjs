import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { once } from "node:events";
import { uploadFaultProxy } from "./upload-fault-proxy.mjs";

test("fault transport rejects non-loopback or decorated upstreams", async () => {
	for (const origin of [
		"https://example.com",
		"http://127.0.0.1/path",
		"http://user@127.0.0.1",
		"http://127.0.0.1/?secret=x",
	])
		await assert.rejects(uploadFaultProxy(origin));
});
test("one loss occurs only after the real upload response; no resend or mock success", async () => {
	let uploads = 0,
		body = "";
	const upstream = createServer(async (req, res) => {
		if (req.method === "PUT") {
			uploads++;
			for await (const part of req) body += part;
		}
		res.end('{"real":true}');
	});
	upstream.listen(0, "127.0.0.1");
	await once(upstream, "listening");
	const proxy = await uploadFaultProxy(
		`http://127.0.0.1:${upstream.address().port}`,
	);
	try {
		assert.deepEqual(await (await fetch(proxy.origin + "/api/read")).json(), {
			real: true,
		});
		proxy.arm();
		assert.throws(() => proxy.arm());
		const path =
			"/api/bank/uploads/11111111-1111-4111-8111-111111111111/content";
		await assert.rejects(
			fetch(proxy.origin + path, { method: "PUT", body: "SYNTHETIC" }),
		);
		assert.equal(uploads, 1);
		assert.equal(body, "SYNTHETIC");
		assert.deepEqual(proxy.receipt, {
			attempts: 1,
			dropped: 1,
			upstreamStatus: 200,
		});
		assert.throws(() => proxy.arm());
		assert.deepEqual(await (await fetch(proxy.origin + "/api/read")).json(), {
			real: true,
		});
		assert.equal(uploads, 1);
	} finally {
		await proxy.close();
		await new Promise((r) => upstream.close(r));
	}
	await assert.rejects(fetch(proxy.origin + "/api/read"));
});

test("extension faults lose a committed response or prevent forwarding without resending", async () => {
	let writes = 0;
	const upstream = createServer(async (req, res) => {
		for await (const _chunk of req) {
		}
		if (req.method === "POST") writes++;
		res.setHeader("Content-Type", "application/json");
		res.end(JSON.stringify({ writes }));
	});
	upstream.listen(0, "127.0.0.1");
	await once(upstream, "listening");
	const proxy = await uploadFaultProxy(
		`http://127.0.0.1:${upstream.address().port}`,
	);
	const path =
		"/api/bookings/11111111-1111-4111-8111-111111111111/extension-reservations";
	try {
		assert.throws(() => proxy.armExtension("invented"));
		proxy.armExtension("after-commit");
		assert.throws(() => proxy.armExtension("before-forward"));
		await assert.rejects(
			fetch(proxy.origin + path, { method: "POST", body: "{}" }),
		);
		assert.equal(writes, 1);
		assert.equal(proxy.extensionReceipt.committedDrops, 1);
		assert.equal(proxy.extensionReceipt.upstreamStatus, 200);
		assert.equal(
			(await (await fetch(proxy.origin + "/api/read")).json()).writes,
			1,
		);
		proxy.armExtension("before-forward");
		await assert.rejects(
			fetch(proxy.origin + path, { method: "POST", body: "{}" }),
		);
		assert.equal(writes, 1);
		assert.equal(proxy.extensionReceipt.beforeForwardDrops, 1);
		assert.equal(
			(await fetch(proxy.origin + path, { method: "POST", body: "{}" })).status,
			200,
		);
		assert.equal(writes, 2);
		assert.equal(proxy.extensionReceipt.attempts, 3);
	} finally {
		await proxy.close();
		await new Promise((r) => upstream.close(r));
	}
});

test("bank timeout withholds only the real exact CREATE response and never resends", async () => {
	let creates = 0;
	const upstream = createServer(async (req, res) => {
		for await (const _chunk of req) {
		}
		if (req.url === "/api/bank/requests" && req.method === "POST") creates++;
		res.setHeader("Content-Type", "application/json");
		res.end(JSON.stringify({ creates }));
	});
	upstream.listen(0, "127.0.0.1");
	await once(upstream, "listening");
	const proxy = await uploadFaultProxy(
		`http://127.0.0.1:${upstream.address().port}`,
	);
	try {
		proxy.armBankCreate();
		assert.throws(() => proxy.armBankCreate());
		for (const [path, method] of [
			["/api/bank/requests", "GET"],
			["/api/bank/requests/readiness", "GET"],
			["/api/bank/requests?unsupported=x", "POST"],
		]) {
			assert.equal((await fetch(proxy.origin + path, { method })).status, 200);
		}
		assert.equal(proxy.bankCreateReceipt.attempts, 0);
		const controller = new AbortController();
		const rejected = assert.rejects(
			fetch(proxy.origin + "/api/bank/requests", {
				method: "POST",
				body: "{}",
				signal: controller.signal,
			}),
		);
		for (let n = 0; !proxy.bankCreateReceipt.stalled && n < 100; n++)
			await new Promise((r) => setTimeout(r, 5));
		assert.deepEqual(proxy.bankCreateReceipt, {
			attempts: 1,
			stalled: 1,
			upstreamStatus: 200,
		});
		controller.abort();
		await rejected;
		assert.equal(creates, 1);
		assert.throws(() => proxy.armBankCreate());
		assert.equal(
			(
				await fetch(proxy.origin + "/api/bank/requests", {
					method: "POST",
					body: "{}",
				})
			).status,
			200,
		);
		assert.equal(creates, 2);
		assert.equal(proxy.bankCreateReceipt.attempts, 2);
	} finally {
		await proxy.close();
		await new Promise((r) => upstream.close(r));
	}
});

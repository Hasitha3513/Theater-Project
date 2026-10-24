// Opt-in harness transport only. Real upstream bytes/statuses; no business stubs.
import { createServer, request } from "node:http";
import { once } from "node:events";

export async function uploadFaultProxy(backend) {
	let origin = new URL(backend);
	const originalOrigin = origin;
	if (
		origin.protocol !== "http:" ||
		origin.hostname !== "127.0.0.1" ||
		origin.username ||
		origin.password ||
		origin.pathname !== "/" ||
		origin.search ||
		origin.hash
	)
		throw new Error("Exact loopback backend required");
	let armed = false,
		used = false;
	const receipt = { attempts: 0, dropped: 0, upstreamStatus: null };
	const sockets = new Set();
	let extensionFault;
	const extensionHttp = [];
	const extensionReceipt = {
		attempts: 0,
		committedDrops: 0,
		beforeForwardDrops: 0,
		upstreamStatus: null,
	};
	const server = createServer((incoming, outgoing) => {
		if (!incoming.url.startsWith("/api/") || incoming.url.startsWith("//")) {
			outgoing.destroy();
			return;
		}
		const extension =
			incoming.method === "POST" &&
			/^\/api\/bookings\/[0-9a-f-]{36}\/extension-reservations$/.test(
				incoming.url,
			);
		const extensionDrop = extension && extensionFault;
		if (extension) {
			extensionReceipt.attempts++;
			extensionFault = undefined;
		}
		if (extensionDrop === "before-forward") {
			extensionReceipt.beforeForwardDrops++;
			incoming.resume();
			outgoing.destroy();
			return;
		}
		const upload =
			incoming.method === "PUT" &&
			/^\/api\/bank\/uploads\/[0-9a-f-]{36}\/content$/.test(incoming.url);
		const drop = armed && upload;
		if (drop) {
			armed = false;
			used = true;
		}
		if (upload) receipt.attempts++;
		const upstream = request(
			new URL(incoming.url, origin),
			{
				method: incoming.method,
				headers: {
					...incoming.headers,
					host: origin.host,
					connection: "close",
				},
				timeout: 16000,
			},
			(response) => {
				if (incoming.url.includes("/extension-"))
					extensionHttp.push({
						method: incoming.method,
						status: response.statusCode,
						path: incoming.url.replace(
							/[0-9a-f]{8}-[0-9a-f-]{27,}/gi,
							"{reference}",
						),
					});
				if (drop || extensionDrop === "after-commit") {
					// Consume the actual backend response before losing it. Never invent success.
					response.resume();
					response.on("end", () => {
						if (drop) {
							receipt.upstreamStatus = response.statusCode;
							receipt.dropped++;
						} else {
							extensionReceipt.upstreamStatus = response.statusCode;
							extensionReceipt.committedDrops++;
						}
						outgoing.destroy();
					});
				} else {
					outgoing.writeHead(response.statusCode, response.headers);
					response.pipe(outgoing);
				}
				response.on("error", () => outgoing.destroy());
			},
		);
		upstream.on("timeout", () => upstream.destroy());
		upstream.on("error", () => outgoing.destroy());
		incoming.on("aborted", () => upstream.destroy());
		incoming.pipe(upstream);
	});
	server.on("connection", (socket) => {
		sockets.add(socket);
		socket.on("close", () => sockets.delete(socket));
	});
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	return {
		origin: `http://127.0.0.1:${server.address().port}`,
		arm() {
			if (armed || used) throw new Error("Single fault only");
			armed = true;
		},
		receipt,
		extensionReceipt,
		extensionHttp,
		pointBackend(value) {
			const candidate = new URL(value);
			if (
				candidate.protocol !== "http:" ||
				candidate.hostname !== "127.0.0.1" ||
				candidate.username ||
				candidate.password ||
				candidate.pathname !== "/" ||
				candidate.search ||
				candidate.hash
			)
				throw new Error("Exact loopback backend required");
			origin = candidate;
		},
		restoreBackend() {
			origin = originalOrigin;
		},
		armExtension(mode) {
			if (extensionFault || !["before-forward", "after-commit"].includes(mode))
				throw new Error("Invalid extension fault");
			extensionFault = mode;
		},
		async close() {
			const closed = new Promise((resolve, reject) =>
				server.close((e) => (e ? reject(e) : resolve())),
			);
			for (const socket of sockets) socket.destroy();
			await closed;
		},
	};
}

// Opt-in harness transport only. Real upstream bytes/statuses; no business stubs.
import { createServer, request } from "node:http";
import { once } from "node:events";

export async function uploadFaultProxy(backend) {
	const origin = new URL(backend);
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
	const server = createServer((incoming, outgoing) => {
		if (!incoming.url.startsWith("/api/") || incoming.url.startsWith("//")) {
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
				if (drop) {
					// Consume the actual backend response before losing it. Never invent success.
					response.resume();
					response.on("end", () => {
						receipt.upstreamStatus = response.statusCode;
						receipt.dropped++;
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
		async close() {
			const closed = new Promise((resolve, reject) =>
				server.close((e) => (e ? reject(e) : resolve())),
			);
			for (const socket of sockets) socket.destroy();
			await closed;
		},
	};
}

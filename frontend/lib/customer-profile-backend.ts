import { profileEdit, profileRecord } from "./customer-profile";
export async function forwardCustomerProfile(
	request: Request,
): Promise<Response> {
	const empty = (status: number) =>
		new Response(null, { status, headers: { "Cache-Control": "no-store" } });
	const url = new URL(request.url);
	if (url.pathname !== "/api/accounts/me/profile" || url.search)
		return empty(400);
	if (!["GET", "PUT"].includes(request.method)) return empty(405);
	const write = request.method === "PUT";
	if (
		write &&
		(request.headers.get("origin") !==
			`${url.protocol}//${request.headers.get("host") ?? url.host}` ||
			!request.headers.get("x-csrf-token"))
	)
		return empty(403);
	try {
		const target = new URL(process.env.CINENEST_AUTH_ORIGIN ?? "");
		if (
			target.username ||
			target.password ||
			target.pathname !== "/" ||
			target.search ||
			target.hash ||
			!(
				target.protocol === "https:" ||
				(target.protocol === "http:" &&
					["127.0.0.1", "localhost", "[::1]"].includes(target.hostname))
			)
		)
			return empty(503);
		let body: string | undefined;
		if (write) {
			if (
				request.headers.get("content-type")?.split(";", 1)[0].trim() !==
				"application/json"
			)
				return empty(400);
			const reader = request.body?.getReader();
			if (!reader) return empty(400);
			const chunks: Uint8Array[] = [];
			let size = 0;
			try {
				while (true) {
					const part = await reader.read();
					if (part.done) break;
					size += part.value.byteLength;
					if (size > 2048) {
						await reader.cancel();
						return empty(400);
					}
					chunks.push(part.value);
				}
			} finally {
				reader.releaseLock();
			}
			const bytes = new Uint8Array(size);
			let at = 0;
			for (const chunk of chunks) {
				bytes.set(chunk, at);
				at += chunk.length;
			}
			body = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
			if (!profileEdit(JSON.parse(body))) return empty(400);
		}
		const headers = new Headers({ Accept: "application/json" });
		const cookies = (request.headers.get("cookie") ?? "")
			.split(";")
			.map((x) => x.trim())
			.filter((x) => x.startsWith("CINENEST_SESSION="));
		if (cookies.length > 1) return empty(403);
		if (cookies[0]) headers.set("Cookie", cookies[0]);
		if (write) {
			headers.set("Content-Type", "application/json");
			headers.set("X-CSRF-TOKEN", request.headers.get("x-csrf-token") ?? "");
		}
		const upstream = await fetch(new URL("/api/accounts/me/profile", target), {
			method: request.method,
			headers,
			body,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(5000),
		});
		const output = new Headers({ "Cache-Control": "no-store" });
		for (const cookie of upstream.headers.getSetCookie())
			if (cookie.startsWith("CINENEST_SESSION="))
				output.append("Set-Cookie", cookie);
		if (upstream.status === 200) {
			const text = await upstream.text();
			if (text.length > 16384) return empty(503);
			return Response.json(profileRecord(JSON.parse(text)), {
				headers: output,
			});
		}
		return new Response(null, {
			status: [400, 401, 403, 404, 409, 503].includes(upstream.status)
				? upstream.status
				: 503,
			headers: output,
		});
	} catch {
		return empty(503);
	}
}

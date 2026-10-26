// Exact local-synthetic Identity transport; no credentials or tokens are logged or retained here.
const actions = new Set([
	"csrf",
	"registrations",
	"email-verifications",
	"login",
]);
export async function forwardManualAuth(
	request: Request,
	action: string,
): Promise<Response> {
	const empty = (status: number) =>
		new Response(null, { status, headers: { "Cache-Control": "no-store" } });
	if (!actions.has(action) || new URL(request.url).search) return empty(400);
	const csrf = action === "csrf";
	if (request.method !== (csrf ? "GET" : "POST")) return empty(405);
	if (
		!csrf &&
		(request.headers.get("origin") !==
			`${new URL(request.url).protocol}//${request.headers.get("host") ?? new URL(request.url).host}` ||
			!request.headers.get("x-csrf-token"))
	)
		return empty(403);
	try {
		const origin = new URL(process.env.CINENEST_AUTH_ORIGIN ?? "");
		if (
			origin.username ||
			origin.password ||
			origin.pathname !== "/" ||
			origin.search ||
			origin.hash ||
			!(
				origin.protocol === "https:" ||
				(origin.protocol === "http:" &&
					["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname))
			)
		)
			return empty(503);
		let body: string | undefined;
		if (!csrf) {
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
			let offset = 0;
			for (const part of chunks) {
				bytes.set(part, offset);
				offset += part.byteLength;
			}
			let value: Record<string, unknown>;
			try {
				body = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
				value = JSON.parse(body);
			} catch {
				return empty(400);
			}
			const keys =
				action === "email-verifications" ? ["token"] : ["email", "password"];
			if (
				!value ||
				Array.isArray(value) ||
				Object.keys(value).length !== keys.length ||
				keys.some((key) => typeof value[key] !== "string")
			)
				return empty(400);
			// Original bounded bytes preserve duplicate-member evidence for the backend's strict parser.
		}
		const headers = new Headers({ Accept: "application/json" });
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((x) => x.trim())
			.find((x) => x.startsWith("CINENEST_SESSION="));
		if (cookie) headers.set("Cookie", cookie);
		if (!csrf) {
			headers.set("Content-Type", "application/json");
			headers.set("X-CSRF-TOKEN", request.headers.get("x-csrf-token") ?? "");
		}
		const upstream = await fetch(new URL(`/api/auth/${action}`, origin), {
			method: request.method,
			headers,
			body,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(15000),
		});
		const output = new Headers({ "Cache-Control": "no-store" });
		for (const value of upstream.headers.getSetCookie())
			if (value.startsWith("CINENEST_SESSION="))
				output.append("Set-Cookie", value);
		if (csrf && upstream.status === 200) {
			const value = await upstream.json();
			if (
				typeof value.token !== "string" ||
				value.headerName !== "X-CSRF-TOKEN"
			)
				return empty(503);
			return Response.json(
				{ token: value.token, headerName: value.headerName },
				{ headers: output },
			);
		}
		return new Response(null, {
			status: [202, 204, 400, 401, 403, 404, 429, 503].includes(upstream.status)
				? upstream.status
				: 503,
			headers: output,
		});
	} catch {
		return empty(503);
	}
}

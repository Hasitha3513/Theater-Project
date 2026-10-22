import { confirmationUuid } from "./booking-confirmations";
import { popularPath, parsePopular } from "./popular-slots";

export async function forwardPopularSlots(request: Request): Promise<Response> {
	const headers = {
		"Cache-Control": "private, no-store",
		"X-Content-Type-Options": "nosniff",
	};
	const fail = (status: number) => new Response(null, { status, headers });
	try {
		const url = new URL(request.url);
		const route =
			/^\/api\/admin\/venues\/([^/]+)\/reports\/synthetic-popular-session-starts$/.exec(
				url.pathname,
			);
		if (request.method !== "GET" || !route) return fail(404);
		if (!confirmationUuid.test(route[1])) return fail(400);
		if (
			[
				"authorization",
				"x-permission",
				"x-permissions",
				"x-role",
				"x-roles",
				"x-venue-id",
				"x-account-id",
				"x-user-id",
			].some((name) => request.headers.has(name))
		)
			return fail(400);
		const q = url.searchParams;
		if (
			(q.has("roomId") && !q.get("roomId")) ||
			[...q.keys()].some(
				(k) =>
					!["from", "to", "roomId"].includes(k) || q.getAll(k).length !== 1,
			)
		)
			return fail(400);
		const context = {
			venue: route[1],
			room: q.get("roomId") ?? "",
			from: q.get("from") ?? "",
			to: q.get("to") ?? "",
		};
		try {
			popularPath(context);
		} catch {
			return fail(400);
		}
		const cookies =
			request.headers
				.get("cookie")
				?.split(";")
				.map((v) => v.trim())
				.filter((v) => v.startsWith("CINENEST_SESSION=")) ?? [];
		if (
			cookies.length !== 1 ||
			!/^CINENEST_SESSION=[A-Za-z0-9_-]+$/.test(cookies[0])
		)
			return fail(401);
		const configured = process.env.CINENEST_AUTH_ORIGIN;
		if (!configured) return fail(503);
		const origin = new URL(configured);
		if (
			origin.username ||
			origin.password ||
			origin.pathname !== "/" ||
			origin.search ||
			origin.hash ||
			!(
				origin.protocol === "https:" ||
				(origin.protocol === "http:" &&
					["127.0.0.1", "localhost", "[::1]"].includes(origin.hostname))
			)
		)
			return fail(503);
		const upstream = await fetch(new URL(url.pathname + url.search, origin), {
			method: "GET",
			headers: { Accept: "application/json", Cookie: cookies[0] },
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(5000),
		});
		if (upstream.status !== 200)
			return fail(
				[400, 401, 403, 404, 409, 503].includes(upstream.status)
					? upstream.status
					: 502,
			);
		const reader = upstream.body?.getReader();
		if (!reader) return fail(502);
		const parts: Uint8Array[] = [];
		let total = 0;
		try {
			for (;;) {
				const part = await reader.read();
				if (part.done) break;
				total += part.value.length;
				if (total > 64 * 1024) {
					await reader.cancel();
					return fail(502);
				}
				parts.push(part.value);
			}
		} finally {
			reader.releaseLock();
		}
		const bytes = new Uint8Array(total);
		let offset = 0;
		for (const p of parts) {
			bytes.set(p, offset);
			offset += p.length;
		}
		const safe = parsePopular(
			JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)),
			context,
		);
		return Response.json(safe, { headers });
	} catch {
		return fail(502);
	}
}

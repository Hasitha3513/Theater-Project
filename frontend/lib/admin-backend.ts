const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
export function adminRoute(path: string) {
	return new RegExp(
		`^/api/admin/venues/${uuid}/(accounts|audit)(/${uuid})?$`,
	).test(path);
}
export async function forwardAdmin(request: Request): Promise<Response> {
	return forwardAdminRead(request, adminRoute);
}
export function staffBookingRoute(path: string) {
	return new RegExp(`^/api/admin/venues/${uuid}/bookings(/${uuid})?$`).test(
		path,
	);
}
export async function forwardStaffBookings(
	request: Request,
): Promise<Response> {
	const url = new URL(request.url),
		headers = { "Cache-Control": "private, no-store" };
	if (request.method !== "GET" || !staffBookingRoute(url.pathname))
		return new Response(null, { status: 404, headers });
	const list = url.pathname.endsWith("/bookings");
	if (
		(!list && url.search) ||
		[...url.searchParams.keys()].some(
			(k) =>
				!["reference", "from", "to", "page", "size"].includes(k) ||
				url.searchParams.getAll(k).length !== 1,
		) ||
		[...url.searchParams.values()].some((v) => !v || v.length > 40)
	)
		return new Response(null, { status: 400, headers });
	return forwardAdminRead(request, staffBookingRoute, true);
}
async function forwardAdminRead(
	request: Request,
	allowed: (path: string) => boolean,
	privateRead = false,
): Promise<Response> {
	const headers = new Headers({
		"Cache-Control": privateRead ? "private, no-store" : "no-store",
		"X-Content-Type-Options": "nosniff",
	});
	try {
		const url = new URL(request.url);
		if (request.method !== "GET" || !allowed(url.pathname))
			return new Response(null, { status: 404, headers });
		const value = process.env.CINENEST_AUTH_ORIGIN;
		if (!value) return new Response(null, { status: 503, headers });
		const origin = new URL(value);
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
			return new Response(null, { status: 503, headers });
		const upstream = new Headers({ Accept: "application/json" });
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((v) => v.trim())
			.find((v) => v.startsWith("CINENEST_SESSION="));
		if (cookie) upstream.set("Cookie", cookie);
		const response = await fetch(new URL(url.pathname + url.search, origin), {
			headers: upstream,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(5000),
		});
		const correlation = response.headers.get("X-Correlation-ID");
		if (
			privateRead &&
			correlation &&
			new RegExp(`^${uuid}$`, "i").test(correlation)
		)
			headers.set("X-Correlation-ID", correlation);
		if (!response.ok)
			return new Response(null, {
				status: [400, 401, 403, 404, 503].includes(response.status)
					? response.status
					: 502,
				headers,
			});
		const reader = response.body?.getReader();
		let total = 0;
		const parts: Uint8Array[] = [];
		if (reader) {
			try {
				for (;;) {
					const part = await reader.read();
					if (part.done) break;
					total += part.value.length;
					if (total > 256 * 1024) {
						await reader.cancel();
						throw new Error();
					}
					parts.push(part.value);
				}
			} finally {
				reader.releaseLock();
			}
		}
		const bytes = new Uint8Array(total);
		let at = 0;
		for (const part of parts) {
			bytes.set(part, at);
			at += part.length;
		}
		return Response.json(JSON.parse(new TextDecoder().decode(bytes)), {
			headers,
		});
	} catch {
		return new Response(null, { status: 502, headers });
	}
}

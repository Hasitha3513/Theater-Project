// Dedicated read-only calendar transport; no changes to other proxy routes.
export function calendarQuery(url: URL) {
	if (
		!/^\/api\/rooms\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/availability\/calendar$/.test(
			url.pathname,
		)
	)
		return false;
	const keys = ["date", "durationMinutes", "guests"];
	if (
		[...url.searchParams.keys()].some((k) => !keys.includes(k)) ||
		keys.some((k) => url.searchParams.getAll(k).length !== 1)
	)
		return false;
	const date = url.searchParams.get("date") ?? "",
		duration = url.searchParams.get("durationMinutes") ?? "",
		guests = url.searchParams.get("guests") ?? "";
	const parsed = new Date(`${date}T00:00:00Z`);
	return (
		/^\d{4}-\d{2}-\d{2}$/.test(date) &&
		Number.isFinite(parsed.getTime()) &&
		parsed.toISOString().slice(0, 10) === date &&
		/^[1-9]\d{0,3}$/.test(duration) &&
		Number(duration) <= 1440 &&
		/^[1-9]\d{0,9}$/.test(guests) &&
		Number(guests) <= 2147483647
	);
}
export async function forwardCalendar(request: Request) {
	const headers = { "Cache-Control": "no-store" };
	if (request.method !== "GET")
		return new Response(null, { status: 404, headers });
	const url = new URL(request.url);
	if (!calendarQuery(url)) return new Response(null, { status: 400, headers });
	try {
		const configured = process.env.CINENEST_AUTH_ORIGIN;
		if (!configured) return new Response(null, { status: 503, headers });
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
			return new Response(null, { status: 503, headers });
		const upstream = new Headers({ Accept: "application/json" }),
			cookie = request.headers
				.get("cookie")
				?.split(";")
				.map((c) => c.trim())
				.find((c) => c.startsWith("CINENEST_SESSION="));
		if (cookie) upstream.set("Cookie", cookie);
		const r = await fetch(new URL(url.pathname + url.search, origin), {
			method: "GET",
			headers: upstream,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(15000),
		});
		if (r.status !== 200)
			return Response.json(
				{
					message:
						"Calendar unavailable, invalid or denied. Reload before selecting a session.",
				},
				{
					status: [400, 401, 403, 404, 409, 429, 503].includes(r.status)
						? r.status
						: 503,
					headers,
				},
			);
		const reader = r.body?.getReader();
		if (!reader) throw Error();
		let size = 0;
		const chunks: Uint8Array[] = [];
		try {
			for (;;) {
				const part = await reader.read();
				if (part.done) break;
				size += part.value.length;
				if (size > 512 * 1024) {
					await reader.cancel();
					throw Error();
				}
				chunks.push(part.value);
			}
		} finally {
			reader.releaseLock();
		}
		const bytes = new Uint8Array(size);
		let offset = 0;
		for (const c of chunks) {
			bytes.set(c, offset);
			offset += c.length;
		}
		JSON.parse(new TextDecoder().decode(bytes));
		return new Response(bytes, {
			headers: { ...headers, "Content-Type": "application/json" },
		});
	} catch {
		return Response.json(
			{ message: "Calendar unavailable. Retry this read explicitly." },
			{ status: 503, headers },
		);
	}
}

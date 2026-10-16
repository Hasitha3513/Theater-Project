// Explicit eleven-route adapter; no arbitrary upstream proxy.
const id = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
export function roomRoute(path: string, method: string) {
	if (
		/^\/api\/catalog\/(venues|rooms)$/.test(path) ||
		new RegExp(`^/api/catalog/rooms/${id}$`).test(path)
	)
		return method === "GET";
	if (
		path === "/api/admin/venues" ||
		new RegExp(`^/api/admin/venues/${id}/rooms$`).test(path)
	)
		return ["GET", "POST"].includes(method);
	if (new RegExp(`^/api/admin/venues/${id}(/rooms/${id})?$`).test(path))
		return ["GET", "PUT"].includes(method);
	return false;
}
export async function forwardRooms(request: Request): Promise<Response> {
	const url = new URL(request.url);
	if (!roomRoute(url.pathname, request.method))
		return new Response(null, { status: 404 });
	const origin = process.env.CINENEST_AUTH_ORIGIN;
	if (!origin) return new Response(null, { status: 503 });
	try {
		const target = new URL(origin);
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
			return new Response(null, { status: 503 });
		const write = request.method !== "GET";
		const headers = new Headers({ Accept: "application/json" });
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((v) => v.trim())
			.find((v) => v.startsWith("CINENEST_SESSION="));
		if (cookie) headers.set("Cookie", cookie);
		if (write) {
			if (
				request.headers.get("origin") !==
				`${url.protocol}//${request.headers.get("host") ?? url.host}`
			)
				return new Response(null, { status: 403 });
			const csrf = request.headers.get("x-csrf-token");
			if (!csrf) return new Response(null, { status: 403 });
			headers.set("X-CSRF-TOKEN", csrf);
			headers.set("Content-Type", "application/json");
		}
		const body = write ? await request.text() : undefined;
		if (body && body.length > 65536) return new Response(null, { status: 413 });
		const query = new URLSearchParams();
		for (const key of [
			"venueId",
			"q",
			"minimumCapacity",
			"facility",
			"enabled",
			"page",
			"size",
		])
			if (url.searchParams.has(key))
				query.set(key, url.searchParams.get(key) ?? "");
		const response = await fetch(new URL(`${url.pathname}?${query}`, target), {
			method: request.method,
			headers,
			body,
			redirect: "manual",
			cache: "no-store",
			signal: AbortSignal.timeout(5000),
		});
		const out = new Headers({ "Cache-Control": "no-store" });
		if (![200, 201, 400, 401, 403, 404, 409, 503].includes(response.status))
			return new Response(null, { status: 502, headers: out });
		if (!response.ok)
			return Response.json(
				{
					message:
						response.status === 409
							? "Configuration changed or code already exists. Reload and compare; your draft is retained."
							: response.status === 400
								? "Invalid configuration. Check the fields and supported values."
								: "Request could not be completed.",
				},
				{ status: response.status, headers: out },
			);
		const location = response.headers.get("location");
		if (location && roomRoute(location, "GET")) out.set("Location", location);
		return Response.json(await response.json(), {
			status: response.status,
			headers: out,
		});
	} catch {
		return new Response(null, {
			status: 502,
			headers: { "Cache-Control": "no-store" },
		});
	}
}

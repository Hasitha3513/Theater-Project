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
export function publicationRoute(path: string, method: string) {
	if (new RegExp(`^/api/venues/${id}/packages(/${id})?$`).test(path))
		return method === "GET";
	if (
		new RegExp(
			`^/api/admin/venues/${id}/packages/${id}/publication-preview$`,
		).test(path)
	)
		return method === "POST";
	if (
		new RegExp(`^/api/admin/venues/${id}/packages/${id}/publications$`).test(
			path,
		)
	)
		return ["GET", "POST"].includes(method);
	if (
		new RegExp(
			`^/api/admin/venues/${id}/packages/${id}/publications/${id}$`,
		).test(path)
	)
		return method === "GET";
	return (
		new RegExp(
			`^/api/admin/venues/${id}/packages/${id}/publications/${id}/(deactivate|retry)$`,
		).test(path) && method === "POST"
	);
}
export async function forwardPublications(request: Request) {
	return forwardConfigured(request, publicationRoute);
}
export function packageRoute(path: string, method: string) {
	if (new RegExp(`^/api/admin/venues/${id}/packages$`).test(path))
		return ["GET", "POST"].includes(method);
	if (new RegExp(`^/api/admin/venues/${id}/package-options$`).test(path))
		return method === "GET";
	if (new RegExp(`^/api/admin/venues/${id}/packages/${id}$`).test(path))
		return method === "GET";
	if (new RegExp(`^/api/admin/venues/${id}/packages/${id}/draft$`).test(path))
		return method === "PUT";
	if (
		new RegExp(
			`^/api/admin/venues/${id}/packages/${id}/(preview|duplicate)$`,
		).test(path)
	)
		return method === "POST";
	return (
		new RegExp(`^/api/admin/venues/${id}/packages/${id}/history$`).test(path) &&
		method === "GET"
	);
}
export async function forwardPackages(request: Request): Promise<Response> {
	return forwardConfigured(request, packageRoute);
}
export async function forwardRooms(request: Request): Promise<Response> {
	return forwardConfigured(request, roomRoute);
}
async function forwardConfigured(
	request: Request,
	allowed: (path: string, method: string) => boolean,
): Promise<Response> {
	const url = new URL(request.url);
	if (!allowed(url.pathname, request.method))
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
			"roomId",
			"sessionStartLocal",
			"durationMinutes",
			"guests",
			"sessionStart",
			"sessionEnd",
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
		if (
			![200, 201, 400, 401, 403, 404, 409, 429, 503].includes(response.status)
		)
			return new Response(null, { status: 502, headers: out });
		if (!response.ok)
			return Response.json(
				{
					message:
						allowed === inventoryRoute
							? response.status === 409
								? "Inventory conflict or changed configuration. Inquire again; no booking is confirmed."
								: response.status === 429
									? "Too many hold attempts. Wait before retrying."
									: "Inventory request unavailable, invalid or denied. Your inputs are retained."
							: response.status === 409
								? "Configuration changed or code already exists. Reload and compare; your draft is retained."
								: response.status === 400
									? "Invalid configuration. Check the fields and supported values."
									: "Request could not be completed.",
				},
				{ status: response.status, headers: out },
			);
		const location = response.headers.get("location");
		if (location && allowed(location, "GET")) out.set("Location", location);
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

export function inventoryRoute(path: string, method: string) {
	if (path === "/api/holds") return method === "POST";
	if (new RegExp(`^/api/holds/${id}$`).test(path)) return method === "GET";
	if (new RegExp(`^/api/holds/${id}/release$`).test(path))
		return method === "POST";
	return (
		method === "GET" &&
		(new RegExp(`^/api/rooms/${id}/availability$`).test(path) ||
			new RegExp(`^/api/admin/venues/${id}/inventory$`).test(path))
	);
}
export async function forwardInventory(request: Request) {
	return forwardConfigured(request, inventoryRoute);
}

export function quoteRoute(path: string, method: string) {
	if (path === "/api/quotes") return method === "POST";
	if (new RegExp(`^/api/quotes/${id}$`).test(path)) return method === "GET";
	return (
		new RegExp(`^/api/quotes/${id}/(revisions|accept)$`).test(path) &&
		method === "POST"
	);
}
export async function forwardQuotes(request: Request) {
	return forwardConfigured(request, quoteRoute);
}

// Approved exact advertising routes; existing session/CSRF/upstream checks reused.
export function bannerRoute(path: string, method: string) {
	const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
	if (path === "/api/catalog/banners") return method === "GET";
	const root = `/api/admin/venues/${uuid}`;
	if (new RegExp(`^${root}/banner-assets$`).test(path)) return method === "GET";
	if (new RegExp(`^${root}/banners$`).test(path))
		return ["GET", "POST"].includes(method);
	if (new RegExp(`^${root}/banners/${uuid}$`).test(path))
		return ["GET", "PUT"].includes(method);
	if (new RegExp(`^${root}/banners/${uuid}/(preview|revisions)$`).test(path))
		return method === "GET";
	return (
		new RegExp(`^${root}/banners/${uuid}/(publications|unpublish)$`).test(
			path,
		) && method === "POST"
	);
}
export async function forwardBanners(request: Request) {
	return forwardConfigured(request, bannerRoute);
}

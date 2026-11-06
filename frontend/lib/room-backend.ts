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
	allowedQuery?: readonly string[],
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
		for (const key of allowedQuery ?? [
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
			![200, 201, 400, 401, 403, 404, 409, 429, 503].includes(
				response.status,
			) &&
			!(
				response.status === 413 && url.pathname.includes("/commercial-policies")
			)
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
	if (path === "/api/quotes/preview") return method === "GET";
	if (path === "/api/quotes") return method === "POST";
	if (new RegExp(`^/api/quotes/${id}$`).test(path)) return method === "GET";
	return (
		new RegExp(`^/api/quotes/${id}/(revisions|accept)$`).test(path) &&
		method === "POST"
	);
}
export async function forwardQuotes(request: Request) {
	const url = new URL(request.url);
	if (url.pathname === "/api/quotes/preview") {
		if (request.method !== "GET") return new Response(null, { status: 404 });
		const required = [
			"holdId",
			"expectedHoldRevision",
			"packageId",
			"packageVersionId",
			"paymentMethod",
			"optionalServiceIds",
		];
		const pins = ["policyId", "policyRevision", "policyContentHash"];
		const keys = [...required, ...pins];
		if (
			url.search.length > 4096 ||
			[...url.searchParams.keys()].some((key) => !keys.includes(key)) ||
			keys.some((key) => url.searchParams.getAll(key).length > 1) ||
			required.some((key) => !url.searchParams.has(key)) ||
			(pins.some((key) => url.searchParams.has(key)) &&
				!pins.every((key) => url.searchParams.has(key))) ||
			[
				"authorization",
				"x-role",
				"x-permission",
				"x-venue-id",
				"x-account-id",
			].some((key) => request.headers.has(key))
		)
			return new Response(null, { status: 400 });
		return forwardConfigured(request, quoteRoute, keys);
	}
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

export async function forwardRoomBlocks(request: Request): Promise<Response> {
	const url = new URL(request.url);
	const match = new RegExp(
		`^/api/admin/venues/(${id})/rooms/(${id})/blocks(?:/(${id}))?$`,
	).exec(url.pathname);
	const fail = (status: number) =>
		new Response(null, {
			status,
			headers: { "Cache-Control": "private, no-store" },
		});
	if (
		!match ||
		!["GET", "POST"].includes(request.method) ||
		(match[3] && request.method !== "GET")
	)
		return fail(404);
	if (
		[
			"authorization",
			"x-role",
			"x-permission",
			"x-venue-id",
			"x-account-id",
		].some((x) => request.headers.has(x))
	)
		return fail(400);
	if (
		[...url.searchParams.keys()].some(
			(k) =>
				request.method !== "GET" ||
				match[3] ||
				!["page", "size"].includes(k) ||
				url.searchParams.getAll(k).length !== 1,
		)
	)
		return fail(400);
	const response = await forwardConfigured(
		request,
		(path, method) => path === url.pathname && method === request.method,
	);
	if (response.status !== 200) return response;
	try {
		const { parseBlock, parseBlockList } = await import("./room-blocks");
		const value = await response.json();
		const result =
			request.method === "GET" && !match[3]
				? parseBlockList(value, match[1], match[2])
				: parseBlock(value, match[1], match[2]);
		return Response.json(result, {
			headers: {
				"Cache-Control": "private, no-store",
				"X-Content-Type-Options": "nosniff",
			},
		});
	} catch {
		return fail(502);
	}
}

// Hardware M1 reuses the bounded transport; no activation/provider/configuration proxy.
export async function forwardHardware(request: Request): Promise<Response> {
	const {
		hardwareContext,
		validateRegistration,
		validateAssignment,
		parseHardwarePage,
		parseDevice,
		parseAssignment,
		parseRegistration,
	} = await import("./hardware");
	const fail = (status: number) =>
		new Response(null, {
			status,
			headers: {
				"Cache-Control": "private, no-store",
				"X-Content-Type-Options": "nosniff",
			},
		});
	const url = new URL(request.url);
	let context: ReturnType<typeof hardwareContext>;
	try {
		context = hardwareContext(url.pathname, request.method);
	} catch {
		return fail(404);
	}
	if (
		[
			"authorization",
			"x-role",
			"x-permission",
			"x-venue-id",
			"x-account-id",
		].some((k) => request.headers.has(k))
	)
		return fail(400);
	const cookies = (request.headers.get("cookie") ?? "")
		.split(";")
		.map((c) => c.trim())
		.filter((c) => c.startsWith("CINENEST_SESSION="));
	if (cookies.length !== 1 || !/^CINENEST_SESSION=[^;\s]+$/.test(cookies[0]))
		return fail(401);
	if (
		[...url.searchParams.keys()].some(
			(k) =>
				request.method !== "GET" ||
				!context.collection ||
				k !== "page" ||
				url.searchParams.getAll(k).length !== 1,
		)
	)
		return fail(400);
	const page = url.searchParams.get("page");
	if (
		page !== null &&
		(!/^(0|[1-9][0-9]{0,4})$/.test(page) || Number(page) > 10000)
	)
		return fail(400);
	let forwarded = request;
	if (request.method === "POST") {
		if (
			request.headers
				.get("content-type")
				?.split(";", 1)[0]
				.trim()
				.toLowerCase() !== "application/json"
		)
			return fail(400);
		try {
			const reader = request.body?.getReader();
			if (!reader) return fail(400);
			const maximum = context.kind === "assignment" ? 32768 : 16384;
			const chunks: Uint8Array[] = [];
			let size = 0;
			for (;;) {
				const item = await reader.read();
				if (item.done) break;
				size += item.value.byteLength;
				if (size > maximum) {
					await reader.cancel();
					return fail(413);
				}
				chunks.push(item.value);
			}
			const bytes = new Uint8Array(size);
			let offset = 0;
			for (const chunk of chunks) {
				bytes.set(chunk, offset);
				offset += chunk.byteLength;
			}
			const raw = new TextDecoder("utf-8", { fatal: true }).decode(bytes),
				value = JSON.parse(raw);
			if (context.kind === "assignment") validateAssignment(value);
			else validateRegistration(value);
			// Preserve original JSON so the backend's strict duplicate-member check remains effective.
			forwarded = new Request(url, {
				method: "POST",
				headers: request.headers,
				body: raw,
			});
		} catch {
			return fail(400);
		}
	} else if (request.body) return fail(400);
	const response = await forwardConfigured(
		forwarded,
		(path, method) => path === url.pathname && method === request.method,
	);
	if (!response.ok) return response;
	try {
		const value = await response.json();
		const parsed =
			request.method === "POST" && context.kind !== "assignment"
				? parseRegistration(value, context.venue)
				: request.method === "GET" && context.collection
					? parseHardwarePage(value, context)
					: context.kind === "assignment"
						? parseAssignment(
								value,
								context.venue,
								context.room as string,
								request.method === "GET" ? context.device : undefined,
							)
						: parseDevice(
								value,
								context.venue,
								context.device,
								context.revision,
							);
		return Response.json(parsed, {
			status: response.status,
			headers: {
				"Cache-Control": "private, no-store",
				"X-Content-Type-Options": "nosniff",
			},
		});
	} catch {
		return fail(502);
	}
}

// Exact non-activating Commercial Policy routes; existing session/CSRF transport only.
export async function forwardCommercialPolicies(
	request: Request,
): Promise<Response> {
	const { policyRoute } = await import("./commercial-policies");
	const url = new URL(request.url);
	if (!policyRoute(url.pathname, request.method))
		return new Response(null, { status: 404 });
	if (
		[...url.searchParams].some(([k]) => !["q", "page", "size"].includes(k)) ||
		["q", "page", "size"].some((k) => url.searchParams.getAll(k).length > 1)
	)
		return new Response(null, { status: 400 });
	if (
		[
			"authorization",
			"x-role",
			"x-permission",
			"x-venue-id",
			"x-account-id",
		].some((k) => request.headers.has(k))
	)
		return new Response(null, { status: 400 });
	return forwardConfigured(request, policyRoute);
}

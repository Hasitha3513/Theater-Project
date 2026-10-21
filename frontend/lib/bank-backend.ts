// Dedicated bounded binary transport. Existing JSON adapters remain unchanged.
const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
export function bankRoute(path: string, method: string) {
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/bookings/${uuid}/operations/cases$`,
		).test(path)
	)
		return method === "GET";
	if (
		new RegExp(`^/api/admin/venues/${uuid}/bookings/${uuid}/refunds$`).test(
			path,
		)
	)
		return method === "GET";
	if (new RegExp(`^/api/bookings/${uuid}/guest-roster$`).test(path))
		return ["GET", "POST"].includes(method);
	if (new RegExp(`^/api/bookings/${uuid}/guest-roster/companions$`).test(path))
		return method === "POST";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/bookings/${uuid}/guest-roster$`,
		).test(path)
	)
		return method === "GET";
	if (path === "/api/bank/requests/readiness") return method === "GET";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/reports/synthetic-bank-balances$`,
		).test(path)
	)
		return method === "GET";
	if (new RegExp(`^/api/admin/venues/${uuid}/operations/cases$`).test(path))
		return ["GET", "POST"].includes(method);
	if (
		new RegExp(`^/api/admin/venues/${uuid}/operations/cases/${uuid}$`).test(
			path,
		)
	)
		return method === "GET";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/operations/cases/${uuid}/(assignments|acknowledgements|checklist|completion-assertions|exceptions|exception-resolutions|reviews)$`,
		).test(path)
	)
		return method === "POST";
	if (
		new RegExp(`^/api/admin/venues/${uuid}/iot/devices(/${uuid})?$`).test(path)
	)
		return method === "GET";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/iot/devices/${uuid}/commands(/${uuid}/(simulations|reconciliations))?$`,
		).test(path)
	)
		return method === "POST";

	if (new RegExp(`^/api/bookings/${uuid}/amendments$`).test(path))
		return method === "POST";
	if (new RegExp(`^/api/bookings/${uuid}/amendments/${uuid}$`).test(path))
		return method === "GET";
	if (
		new RegExp(
			`^/api/bookings/${uuid}/amendments/${uuid}/(confirm|withdrawals)$`,
		).test(path)
	)
		return method === "POST";
	if (
		new RegExp(`^/api/notifications(/${uuid})?$`).test(path) ||
		new RegExp(`^/api/admin/venues/${uuid}/notifications(/${uuid})?$`).test(
			path,
		)
	)
		return method === "GET";
	if (
		new RegExp(`^/api/notifications/${uuid}/read$`).test(path) ||
		new RegExp(
			`^/api/admin/venues/${uuid}/notifications/${uuid}/(retries|reconciliations|simulations)$`,
		).test(path)
	)
		return method === "POST";
	if (
		new RegExp(`^/api/bookings/${uuid}/access$`).test(path) ||
		new RegExp(`^/api/admin/venues/${uuid}/bookings/${uuid}/access$`).test(
			path,
		) ||
		new RegExp(
			`^/api/admin/venues/${uuid}/access/credentials(/${uuid})?$`,
		).test(path)
	)
		return method === "GET";
	if (
		new RegExp(
			`^/api/bookings/${uuid}/access/(credentials|simulated-attempts)$`,
		).test(path) ||
		new RegExp(
			`^/api/bookings/${uuid}/access/credentials/${uuid}/reveal$`,
		).test(path) ||
		new RegExp(
			`^/api/admin/venues/${uuid}/access/credentials/${uuid}/(revocations|simulations)$`,
		).test(path)
	)
		return method === "POST";

	if (
		new RegExp(`^/api/refunds/${uuid}$`).test(path) ||
		new RegExp(`^/api/admin/venues/${uuid}/refunds$`).test(path)
	)
		return method === "GET";
	if (new RegExp(`^/api/bank/requests/${uuid}/refunds$`).test(path))
		return method === "GET" || method === "POST";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/refunds/${uuid}/(destination-verifications|decisions|attempts|reconciliations)$`,
		).test(path) ||
		new RegExp(
			`^/api/admin/venues/${uuid}/refunds/${uuid}/attempts/${uuid}/evidence$`,
		).test(path)
	)
		return method === "POST";
	if (
		path === "/api/bookings" ||
		new RegExp(`^/api/bookings/${uuid}$`).test(path)
	)
		return method === "GET";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/bank/(credits|exceptions)(/${uuid})?$`,
		).test(path)
	)
		return method === "GET" || (method === "POST" && path.endsWith("/credits"));
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/bank/(credits/${uuid}/(matches|corrections)|requests/${uuid}/allocations|exceptions/${uuid}/actions|corrections/${uuid}/decisions)$`,
		).test(path)
	)
		return method === "POST";

	if (path === "/api/bank/requests")
		return method === "POST" || method === "GET";
	if (new RegExp(`^/api/bank/requests/${uuid}$`).test(path))
		return method === "GET";
	if (
		new RegExp(`^/api/bank/requests/${uuid}/(claims|resubmissions)$`).test(path)
	)
		return method === "POST";
	if (new RegExp(`^/api/bank/uploads/${uuid}/content$`).test(path))
		return method === "PUT";
	if (new RegExp(`^/api/bank/uploads/${uuid}/complete$`).test(path))
		return method === "POST";
	if (
		new RegExp(
			`^/api/admin/venues/${uuid}/bank/requests(/${uuid}(/evidence/${uuid})?)?$`,
		).test(path)
	)
		return method === "GET";
	if (new RegExp(`^/api/bank/claims/${uuid}/uploads$`).test(path))
		return method === "POST";
	return (
		new RegExp(
			`^/api/admin/venues/${uuid}/bank/requests/${uuid}/(decisions|escalations)$`,
		).test(path) && method === "POST"
	);
}
async function bounded(
	stream: ReadableStream<Uint8Array> | null,
	limit: number,
) {
	const reader = stream?.getReader();
	if (!reader) return new Uint8Array();
	const parts: Uint8Array[] = [];
	let total = 0;
	try {
		for (;;) {
			const part = await reader.read();
			if (part.done) break;
			total += part.value.length;
			if (total > limit) {
				await reader.cancel();
				throw new RangeError("Too large");
			}
			parts.push(part.value);
		}
	} finally {
		reader.releaseLock();
	}
	const bytes = new Uint8Array(total);
	let at = 0;
	for (const part of parts) {
		bytes.set(part, at);
		at += part.length;
	}
	return bytes;
}
export async function forwardBank(request: Request): Promise<Response> {
	const headers = new Headers({
		"Cache-Control": "no-store",
		"X-Content-Type-Options": "nosniff",
	});
	try {
		const url = new URL(request.url);
		const readiness = url.pathname === "/api/bank/requests/readiness";
		const validReadiness =
			readiness &&
			request.method === "GET" &&
			[...url.searchParams.keys()].length === 1 &&
			url.searchParams.getAll("quote").length === 1 &&
			new RegExp(`^${uuid}$`, "i").test(url.searchParams.get("quote") ?? "");
		if (readiness && !validReadiness)
			return new Response(null, { status: 400, headers });
		if (readiness) headers.set("Cache-Control", "private, no-store");
		const operationsQueue = new RegExp(
			`^/api/admin/venues/${uuid}/operations/cases$`,
		).test(url.pathname);
		const queuePage =
			operationsQueue &&
			request.method === "GET" &&
			[...url.searchParams.keys()].every(
				(k) => k === "page" && url.searchParams.getAll(k).length === 1,
			) &&
			/^\d{1,6}$/.test(url.searchParams.get("page") ?? "0") &&
			Number(url.searchParams.get("page") ?? "0") <= 100000;
		if (operationsQueue && url.search && !queuePage)
			return new Response(null, { status: 404, headers });
		const bookingOperations = new RegExp(
			`^/api/admin/venues/${uuid}/bookings/${uuid}/operations/cases$`,
		).test(url.pathname);
		const operationsPage =
			bookingOperations &&
			request.method === "GET" &&
			[...url.searchParams.keys()].every(
				(k) => k === "page" && url.searchParams.getAll(k).length === 1,
			) &&
			/^\d{1,6}$/.test(url.searchParams.get("page") ?? "0") &&
			Number(url.searchParams.get("page") ?? "0") <= 100000;
		if (bookingOperations && !operationsPage)
			return new Response(null, { status: 404, headers });
		const bookingRefund = new RegExp(
			`^/api/admin/venues/${uuid}/bookings/${uuid}/refunds$`,
		).test(url.pathname);
		const refundPage =
			bookingRefund &&
			request.method === "GET" &&
			[...url.searchParams.keys()].every(
				(k) =>
					["page", "size"].includes(k) &&
					url.searchParams.getAll(k).length === 1,
			) &&
			[...url.searchParams.values()].every((v) => /^\d{1,5}$/.test(v)) &&
			Number(url.searchParams.get("page") ?? "0") <= 10000 &&
			Number(url.searchParams.get("size") ?? "10") >= 1 &&
			Number(url.searchParams.get("size") ?? "10") <= 25;
		if (bookingRefund && !refundPage)
			return new Response(null, { status: 404, headers });
		const accessPage =
			(new RegExp(`^/api/admin/venues/${uuid}/access/credentials$`).test(
				url.pathname,
			) ||
				new RegExp(`^/api/admin/venues/${uuid}/notifications$`).test(
					url.pathname,
				) ||
				url.pathname === "/api/notifications" ||
				url.pathname === "/api/bookings" ||
				url.pathname === "/api/bank/requests" ||
				new RegExp(`^/api/bank/requests/${uuid}/refunds$`).test(
					url.pathname,
				)) &&
			request.method === "GET" &&
			[...url.searchParams.keys()].every((k) => ["page", "size"].includes(k)) &&
			[...url.searchParams.values()].every((v) => /^\d{1,5}$/.test(v));
		if (
			!bankRoute(url.pathname, request.method) ||
			(url.search &&
				!accessPage &&
				!validReadiness &&
				!refundPage &&
				!operationsPage &&
				!queuePage)
		)
			return new Response(null, { status: 404, headers });
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
		const upstream = new Headers();
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((v) => v.trim())
			.find((v) => v.startsWith("CINENEST_SESSION="));
		if (cookie) upstream.set("Cookie", cookie);
		const binary = request.method === "PUT";
		let body: Uint8Array | undefined;
		if (request.method !== "GET") {
			if (
				request.headers.get("origin") !==
					`${url.protocol}//${request.headers.get("host") ?? url.host}` ||
				!request.headers.get("x-csrf-token")
			)
				return new Response(null, { status: 403, headers });
			upstream.set("X-CSRF-TOKEN", request.headers.get("x-csrf-token") ?? "");
			const content = request.headers.get("content-type") ?? "";
			if (
				binary
					? !["image/png", "image/jpeg"].includes(content)
					: !content.startsWith("application/json")
			)
				return new Response(null, { status: 415, headers });
			upstream.set("Content-Type", binary ? content : "application/json");
			body = await bounded(request.body, binary ? 5 * 1024 * 1024 : 65536);
		}
		const response = await fetch(new URL(url.pathname + url.search, origin), {
			method: request.method,
			headers: upstream,
			body: body as BodyInit | undefined,
			redirect: "manual",
			cache: "no-store",
			signal: AbortSignal.timeout(15000),
		});
		if (
			![200, 201, 400, 401, 403, 404, 409, 413, 415, 429, 503].includes(
				response.status,
			)
		)
			return new Response(null, { status: 502, headers });
		if (!response.ok)
			return Response.json(
				{
					message: url.pathname.includes("/amendments")
						? "Synthetic amendment denied, changed, expired or unavailable. Refresh your booking and proposal before retrying."
						: response.status === 409
							? "Bank request changed or expired. Refresh current history before retrying."
							: response.status === 413
								? "Synthetic image exceeds 5 MiB."
								: response.status === 415
									? "Only approved synthetic JPEG/PNG fixtures are allowed."
									: "Synthetic bank review unavailable, invalid or denied.",
				},
				{ status: response.status, headers },
			);
		const preview =
			url.pathname.includes("/bank/requests/") &&
			url.pathname.includes("/evidence/");
		const bytes = await bounded(
			response.body,
			preview ? 5 * 1024 * 1024 : 256 * 1024,
		);
		if (preview) {
			if (response.headers.get("content-type") !== "image/png")
				throw new Error();
			headers.set("Content-Type", "image/png");
			headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
			return new Response(bytes, { headers });
		}
		return Response.json(JSON.parse(new TextDecoder().decode(bytes)), {
			headers,
		});
	} catch (error) {
		return new Response(null, {
			status: error instanceof RangeError ? 413 : 502,
			headers,
		});
	}
}

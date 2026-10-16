// Transport adapter for three existing CN-AUTH-001 routes; no policy or second session store.
const routes = new Set([
	"/api/accounts/me",
	"/api/session/csrf",
	"/api/session/logout",
]);
export async function forwardSession(
	request: Request,
	path: string,
): Promise<Response> {
	const unavailable = () =>
		new Response(null, {
			status: 503,
			headers: { "Cache-Control": "no-store" },
		});
	if (!routes.has(path)) return new Response(null, { status: 404 });
	const origin = process.env.CINENEST_AUTH_ORIGIN;
	if (!origin) return unavailable();
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
			return unavailable();
		const logout = path === "/api/session/logout";
		if (request.method !== (logout ? "POST" : "GET"))
			return new Response(null, { status: 405 });
		if (logout && request.headers.get("origin") !== `${new URL(request.url).protocol}//${request.headers.get("host") ?? new URL(request.url).host}`)
			return new Response(null, { status: 403 });
		const headers = new Headers({ Accept: "application/json" });
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((value) => value.trim())
			.find((value) => value.startsWith("CINENEST_SESSION="));
		if (cookie) headers.set("Cookie", cookie);
		if (logout) {
			const token = request.headers.get("x-csrf-token");
			if (!token) return new Response(null, { status: 403 });
			headers.set("X-CSRF-TOKEN", token);
		}
		const upstream = await fetch(new URL(path, target), {
			method: request.method,
			headers,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(5000),
		});
		// Never relay stack traces, redirect bodies, user roles, arbitrary response fields or error payloads.
		const output = new Headers({
			"Cache-Control": "no-store",
			"Content-Type": "application/json",
		});
		for (const value of upstream.headers.getSetCookie())
			if (value.startsWith("CINENEST_SESSION="))
				output.append("Set-Cookie", value);
		if (logout)
			return new Response(null, {
				status:
					upstream.status === 204
						? 204
						: upstream.status === 401 || upstream.status === 403
							? upstream.status
							: 502,
				headers: output,
			});
		if (upstream.status === 401 || upstream.status === 403)
			return new Response(null, { status: upstream.status, headers: output });
		if (upstream.status !== 200)
			return new Response(null, { status: 502, headers: output });
		const value = await upstream.json();
		const body =
			path === "/api/accounts/me"
				? { accountId: value.accountId, eligibility: value.eligibility }
				: { token: value.token, headerName: value.headerName };
		return Response.json(body, { headers: output });
	} catch {
		return new Response(null, {
			status: 502,
			headers: { "Cache-Control": "no-store" },
		});
	}
}

// S1: isolated protocol qualification only. Never an arbitrary redirect or live-provider activation.
export async function forwardGoogleSso(
	request: Request,
	action: string,
): Promise<Response> {
	const headers = new Headers({
		"Cache-Control": "no-store",
		"Referrer-Policy": "no-referrer",
		"Content-Security-Policy": "default-src 'none'",
	});
	const empty = (status: number) => new Response(null, { status, headers });
	if (
		!["status", "start", "callback"].includes(action) ||
		request.method !== "GET"
	)
		return empty(405);
	const input = new URL(request.url);
	const query = input.searchParams;
	if (action !== "callback" && input.search) return empty(400);
	if (
		action === "callback" &&
		([...query.keys()].some(
			(k) =>
				!["code", "state", "error"].includes(k) || query.getAll(k).length !== 1,
		) ||
			!query.get("state") ||
			Boolean(query.get("code")) === Boolean(query.get("error")) ||
			[...query.values()].some((v) => v.length > 2048))
	)
		return empty(400);
	try {
		const issuer = new URL(process.env.CINENEST_GOOGLE_CONTRACT_ISSUER ?? "");
		const backend = new URL(process.env.CINENEST_AUTH_ORIGIN ?? "");
		for (const u of [issuer, backend])
			if (
				u.protocol !== "http:" ||
				!["127.0.0.1", "localhost", "[::1]"].includes(u.hostname) ||
				!u.port ||
				u.username ||
				u.password ||
				u.search ||
				u.hash ||
				u.pathname !== "/"
			)
				return empty(503);
		const path =
			action === "status"
				? "/api/auth/google/status"
				: action === "start"
					? "/oauth2/authorization/google"
					: "/login/oauth2/code/google";
		const target = new URL(path, backend);
		if (action === "callback") target.search = query.toString();
		const forwarded = new Headers();
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((x) => x.trim())
			.find((x) => x.startsWith("CINENEST_SESSION="));
		if (cookie) forwarded.set("Cookie", cookie);
		const response = await fetch(target, {
			headers: forwarded,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(10000),
		});
		for (const cookie of response.headers.getSetCookie())
			if (cookie.startsWith("CINENEST_SESSION="))
				headers.append("Set-Cookie", cookie);
		if (action === "status") {
			if (response.status !== 200) return empty(503);
			const value = await response.json();
			if (
				value.mode !== "CONTRACT_TEST" ||
				value.liveProviderQualified !== false
			)
				return empty(503);
			return Response.json(
				{ mode: "CONTRACT_TEST", liveProviderQualified: false },
				{ headers },
			);
		}
		if (action === "start") {
			const location = new URL(response.headers.get("location") ?? "");
			if (
				response.status !== 302 ||
				location.origin !== issuer.origin ||
				location.pathname !== "/authorize" ||
				location.username ||
				location.password ||
				location.hash
			)
				return empty(503);
			const callback = new URL(location.searchParams.get("redirect_uri") ?? "");
			const expected = `${input.protocol}//${request.headers.get("host") ?? input.host}`;
			if (
				callback.origin !== expected ||
				callback.pathname !== "/api/auth/google/callback" ||
				callback.search ||
				callback.hash ||
				!location.searchParams.get("state") ||
				!location.searchParams.get("nonce") ||
				location.searchParams.get("code_challenge_method") !== "S256"
			)
				return empty(503);
			headers.set("Location", location.toString());
			return new Response(null, { status: 303, headers });
		}
		headers.set(
			"Location",
			response.status === 204 ? "/account" : "/login?google=denied",
		);
		return new Response(null, { status: 303, headers });
	} catch {
		if (action === "callback") {
			headers.set("Location", "/login?google=unavailable");
			return new Response(null, { status: 303, headers });
		}
		return empty(503);
	}
}

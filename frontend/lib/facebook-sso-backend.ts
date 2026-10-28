// S1: isolated protocol qualification only. Never an arbitrary redirect or live-provider activation.
export async function forwardFacebookSso(
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
	if (process.env.CINENEST_FACEBOOK_MODE !== "CONTRACT_TEST") return empty(503);
	const input = new URL(request.url);
	if (
		input.protocol !== "http:" ||
		!["127.0.0.1", "localhost", "[::1]"].includes(input.hostname) ||
		!input.port
	)
		return empty(503);
	const query = input.searchParams;
	if (action !== "callback" && input.search) return empty(400);
	if (
		action === "callback" &&
		([...query.keys()].some(
			(k) =>
				![
					"code",
					"state",
					"error",
					"error_reason",
					"error_description",
				].includes(k) || query.getAll(k).length !== 1,
		) ||
			!query.get("state") ||
			Boolean(query.get("code")) === Boolean(query.get("error")) ||
			[...query.values()].some((v) => v.length > 2048))
	)
		return empty(400);
	if (input.search.length > 8192) return empty(400);
	try {
		const issuer = new URL(process.env.CINENEST_FACEBOOK_CONTRACT_ORIGIN ?? "");
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
				? "/api/auth/facebook/status"
				: action === "start"
					? "/oauth2/authorization/facebook"
					: "/login/oauth2/code/facebook";
		const target = new URL(path, backend);
		if (action === "callback") {
			// Provider presentation metadata never becomes identity/authorization input.
			for (const key of ["code", "state", "error"]) {
				const value = query.get(key);
				if (value !== null) target.searchParams.set(key, value);
			}
		}
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
				value.liveProviderQualified !== false ||
				value.syntheticOnly !== true
			)
				return empty(503);
			return Response.json(
				{
					mode: value.mode,
					liveProviderQualified: false,
				},
				{ headers },
			);
		}
		if (action === "start") {
			const location = new URL(response.headers.get("location") ?? "");
			if (
				response.status !== 302 ||
				location.origin !== issuer.origin ||
				location.pathname !== "/v26.0/dialog/oauth" ||
				location.username ||
				location.password ||
				location.hash ||
				[...location.searchParams.keys()].some(
					(key) =>
						![
							"client_id",
							"redirect_uri",
							"response_type",
							"scope",
							"state",
						].includes(key) || location.searchParams.getAll(key).length !== 1,
				)
			)
				return empty(503);
			const callback = new URL(location.searchParams.get("redirect_uri") ?? "");
			const expected = `${input.protocol}//${request.headers.get("host") ?? input.host}`;
			if (
				callback.origin !== expected ||
				callback.username || callback.password ||
				callback.pathname !== "/api/auth/facebook/callback" ||
				callback.search ||
				callback.hash ||
				!location.searchParams.get("state") ||
				location.searchParams.get("response_type") !== "code" ||
				location.searchParams.get("scope") !== "public_profile"
			)
				return empty(503);
			headers.set("Location", location.toString());
			return new Response(null, { status: 303, headers });
		}
		headers.set(
			"Location",
			response.status === 204 ? "/account" : "/login?facebook=denied",
		);
		return new Response(null, { status: 303, headers });
	} catch {
		if (action === "callback") {
			headers.set("Location", "/login?facebook=unavailable");
			return new Response(null, { status: 303, headers });
		}
		return empty(503);
	}
}

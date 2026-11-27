// Exact Identity transport. The backend selects the mode; no credentials or tokens are retained here.
const actions = new Set([
	"csrf",
	"registrations",
	"email-verifications",
	"login",
	"recovery-requests",
	"recoveries",
]);
export async function forwardManualAuth(
	request: Request,
	action: string,
): Promise<Response> {
	const empty = (status: number) =>
		new Response(null, { status, headers: { "Cache-Control": "no-store" } });
	if (!actions.has(action) || new URL(request.url).search) return empty(400);
	const csrf = action === "csrf";
	if (request.method !== (csrf ? "GET" : "POST")) return empty(405);
	if (
		!csrf &&
		(request.headers.get("origin") !==
			`${new URL(request.url).protocol}//${request.headers.get("host") ?? new URL(request.url).host}` ||
			!request.headers.get("x-csrf-token"))
	)
		return empty(403);
	try {
		const origin = new URL(process.env.CINENEST_AUTH_ORIGIN ?? "");
		if (
			origin.username ||
			origin.password ||
			origin.pathname !== "/" ||
			origin.search ||
			origin.hash ||
			!(
				origin.protocol === "https:" ||
				(origin.protocol === "http:" &&
					["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname))
			)
		)
			return empty(503);
		let body: string | undefined;
		if (!csrf) {
			if (
				request.headers.get("content-type")?.split(";", 1)[0].trim() !==
				"application/json"
			)
				return empty(400);
			const reader = request.body?.getReader();
			if (!reader) return empty(400);
			const chunks: Uint8Array[] = [];
			let size = 0;
			try {
				while (true) {
					const part = await reader.read();
					if (part.done) break;
					size += part.value.byteLength;
					if (size > 2048) {
						await reader.cancel();
						return empty(400);
					}
					chunks.push(part.value);
				}
			} finally {
				reader.releaseLock();
			}
			const bytes = new Uint8Array(size);
			let offset = 0;
			for (const part of chunks) {
				bytes.set(part, offset);
				offset += part.byteLength;
			}
			let value: Record<string, unknown>;
			try {
				body = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
				value = JSON.parse(body);
			} catch {
				return empty(400);
			}
			const customer = Object.hasOwn(value ?? {}, "commandId");
			const keys = customer
				? action === "registrations"
					? ["commandId", "email", "password", "privacyRevision"]
					: action === "email-verifications"
						? ["commandId", "token", "password"]
						: action === "recovery-requests"
							? ["commandId", "email"]
							: action === "recoveries"
								? ["commandId", "token", "newPassword"]
								: ["commandId", "email", "password"]
				: action === "email-verifications"
					? ["token"]
					: ["email", "password"];
			if (
				!value ||
				Array.isArray(value) ||
				Object.keys(value).length !== keys.length ||
				keys.some((key) => typeof value[key] !== "string") ||
				(!customer && ["recovery-requests", "recoveries"].includes(action)) ||
				(customer &&
					!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
						String(value.commandId),
					))
			)
				return empty(400);
			// Original bounded bytes preserve duplicate-member evidence for the backend's strict parser.
		}
		const headers = new Headers({ Accept: "application/json" });
		const cookie = request.headers
			.get("cookie")
			?.split(";")
			.map((x) => x.trim())
			.find((x) => x.startsWith("CINENEST_SESSION="));
		if (cookie) headers.set("Cookie", cookie);
		if (!csrf) {
			headers.set("Content-Type", "application/json");
			headers.set("X-CSRF-TOKEN", request.headers.get("x-csrf-token") ?? "");
		}
		const upstream = await fetch(new URL(`/api/auth/${action}`, origin), {
			method: request.method,
			headers,
			body,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.timeout(15000),
		});
		const output = new Headers({ "Cache-Control": "no-store" });
		for (const value of upstream.headers.getSetCookie())
			if (value.startsWith("CINENEST_SESSION="))
				output.append("Set-Cookie", value);
		if (csrf && upstream.status === 200) {
			const value = await upstream.json();
			if (
				typeof value.token !== "string" ||
				value.headerName !== "X-CSRF-TOKEN"
			)
				return empty(503);
			if (value.mode === undefined)
				return Response.json(
					{ token: value.token, headerName: value.headerName },
					{ headers: output },
				);
			if (
				value.mode !== "CUSTOMER_CREDENTIAL" ||
				!["QUALIFICATION_ONLY", "APPROVED"].includes(value.deliveryMode) ||
				![value.privacyRevision, value.policyRevision].every(
					(reference) =>
						typeof reference === "string" &&
						reference.length > 0 &&
						reference.length <= 256 &&
						Array.from(reference).every((character) => {
							const point = character.codePointAt(0) ?? 0;
							return (
								point >= 32 &&
								(point < 127 || point > 159) &&
								(point < 55296 || point > 57343)
							);
						}),
				)
			)
				return empty(503);
			return Response.json(
				{
					token: value.token,
					headerName: value.headerName,
					mode: value.mode,
					privacyRevision: value.privacyRevision,
					policyRevision: value.policyRevision,
					deliveryMode: value.deliveryMode,
				},
				{ headers: output },
			);
		}
		return new Response(null, {
			status: [202, 204, 400, 401, 403, 404, 409, 429, 503].includes(
				upstream.status,
			)
				? upstream.status
				: 503,
			headers: output,
		});
	} catch {
		return empty(503);
	}
}

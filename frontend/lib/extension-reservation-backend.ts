import {
	reservationCommand,
	reservationResult,
	recoveryResult,
} from "./extension-reservation";
import { ExtensionError } from "./extension-preview";
import { cancellationUuid } from "./booking-cancellation";
async function bounded(stream: ReadableStream<Uint8Array> | null) {
	const reader = stream?.getReader();
	if (!reader) throw Error();
	const parts: Uint8Array[] = [];
	let n = 0;
	try {
		for (;;) {
			const p = await reader.read();
			if (p.done) break;
			n += p.value.length;
			if (n > 8192) {
				await reader.cancel();
				throw Error();
			}
			parts.push(p.value);
		}
	} finally {
		reader.releaseLock();
	}
	const bytes = new Uint8Array(n);
	let at = 0;
	for (const p of parts) {
		bytes.set(p, at);
		at += p.length;
	}
	return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}
export async function forwardReservation(request: Request): Promise<Response> {
	const headers = {
		"Cache-Control": "private, no-store",
		"X-Content-Type-Options": "nosniff",
	};
	const fail = (status: number) => new Response(null, { status, headers });
	try {
		const url = new URL(request.url);
		const route =
			/^\/api\/bookings\/([^/]+)\/extension-reservations(?:\/(commands)\/([^/]+)|\/([^/]+)(\/withdrawals)?)?$/.exec(
				url.pathname,
			);
		if (
			!route ||
			!cancellationUuid.test(route[1]) ||
			((route[3] || route[4]) &&
				!cancellationUuid.test(route[3] || route[4])) ||
			request.method !== ((!route[3] && !route[4]) || route[5] ? "POST" : "GET")
		)
			return fail(404);
		const recovery = !!route[2],
			withdraw = !!route[5],
			reference = route[3] || route[4];
		if (url.search) return fail(400);
		const bookingId = route[1];
		const cookies =
			request.headers
				.get("cookie")
				?.split(";")
				.map((v) => v.trim())
				.filter((v) => v.startsWith("CINENEST_SESSION=")) ?? [];
		if (
			cookies.length !== 1 ||
			!/^CINENEST_SESSION=[A-Za-z0-9_-]+$/.test(cookies[0])
		)
			return fail(401);
		const upstream = new Headers({
			Accept: "application/json",
			Cookie: cookies[0],
		});
		let body: string | undefined;
		if (request.method === "POST") {
			if (
				request.headers.get("origin") !== url.origin ||
				!request.headers.get("x-csrf-token")
			)
				return fail(403);
			if (
				request.headers.get("content-type")?.split(";")[0].trim() !==
				"application/json"
			)
				return fail(400);
			try {
				body = JSON.stringify(
					reservationCommand(await bounded(request.body), withdraw),
				);
			} catch {
				return fail(400);
			}
			upstream.set("Content-Type", "application/json");
			upstream.set("X-CSRF-TOKEN", request.headers.get("x-csrf-token") ?? "");
		}
		const configured = process.env.CINENEST_AUTH_ORIGIN;
		if (!configured) return fail(503);
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
			return fail(503);
		const response = await fetch(new URL(url.pathname, origin), {
			method: request.method,
			headers: upstream,
			body,
			cache: "no-store",
			redirect: "manual",
			signal: AbortSignal.any([request.signal, AbortSignal.timeout(15000)]),
		});
		if (response.status !== 200)
			return fail(
				[400, 401, 403, 404, 409, 429, 503].includes(response.status)
					? response.status
					: 503,
			);
		if (!response.headers.get("content-type")?.startsWith("application/json"))
			return fail(503);
		const raw = await bounded(response.body);
		const safe = recovery
			? recoveryResult(raw, bookingId, reference)
			: reservationResult(raw, bookingId, route[4]);
		if (body && !withdraw) {
			const c = reservationCommand(
				JSON.parse(body),
			) as import("./extension-reservation").CreateReservation;
			const v = safe as import("./extension-reservation").Reservation;
			if (
				v.additionalMinutes !== c.additionalMinutes ||
				v.additionalMinor !== c.expectedAdditionalMinor ||
				v.packageVersionId !== c.expectedPackageVersionId
			)
				return fail(503);
		}
		request.signal.throwIfAborted();
		return Response.json(safe, { headers });
	} catch (e) {
		return fail(e instanceof ExtensionError ? e.status : 503);
	}
}

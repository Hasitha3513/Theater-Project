import { forwardBank } from "./bank-backend";
import {
	type InstructionContext,
	instructionPath,
	parseInstruction,
} from "./bank-instructions";
export async function forwardInstructions(request: Request): Promise<Response> {
	const headers = {
		"Cache-Control": "private, no-store",
		"X-Content-Type-Options": "nosniff",
	};
	try {
		const u = new URL(request.url),
			parts = u.pathname.split("/");
		const bound = parts[6] === "requests";
		const c: InstructionContext = {
			venue: parts[4],
			target: parts[7],
			kind: bound ? "request" : "revision",
		};
		if (
			request.method !== "GET" ||
			u.search ||
			request.body ||
			instructionPath(c) !== u.pathname
		)
			return new Response(null, { status: 400, headers });
		if (
			!/(?:^|;)\s*CINENEST_SESSION=[^;\s]+(?:;|$)/.test(
				request.headers.get("cookie") ?? "",
			)
		)
			return new Response(null, { status: 401, headers });
		const response = await forwardBank(request);
		if (!response.ok)
			return new Response(null, { status: response.status, headers });
		return Response.json(parseInstruction(await response.json(), c), {
			headers,
		});
	} catch {
		return new Response(null, { status: 503, headers });
	}
}

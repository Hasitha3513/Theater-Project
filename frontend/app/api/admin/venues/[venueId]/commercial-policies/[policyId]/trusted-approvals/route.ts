import {
	approvalTarget,
	validApprovalEnvelope,
} from "../../../../../../../../lib/commercial-policies";
import { forwardCommercialPolicies } from "../../../../../../../../lib/room-backend";

const maxBodyBytes = 32 * 1024;
const envelopeObject =
	/^\s*\{\s*"envelope"\s*:\s*("(?:[^"\\]|\\.)*")\s*\}\s*$/;

export async function POST(request: Request): Promise<Response> {
	const url = new URL(request.url);
	const target = approvalTarget(url.pathname);
	if (!target || request.method !== "POST")
		return new Response(null, { status: 404 });
	const type = request.headers.get("content-type")?.split(";", 1)[0];
	if (url.search || type?.trim().toLowerCase() !== "application/json")
		return new Response(null, { status: 400 });

	const reader = request.body?.getReader();
	if (!reader) return new Response(null, { status: 400 });
	const decoder = new TextDecoder("utf-8", { fatal: true });
	let body = "";
	let bytes = 0;
	try {
		for (;;) {
			const { done, value } = await reader.read();
			if (done) break;
			bytes += value.length;
			if (bytes > maxBodyBytes) {
				await reader.cancel();
				return new Response(null, { status: 413 });
			}
			body += decoder.decode(value, { stream: true });
		}
		body += decoder.decode();
		const match = envelopeObject.exec(body);
		const envelope: unknown = match ? JSON.parse(match[1]) : null;
		if (!validApprovalEnvelope(envelope))
			return new Response(null, { status: 400 });

		url.pathname = target.upstream;
		return forwardCommercialPolicies(
			new Request(url, {
				method: request.method,
				headers: request.headers,
				body: JSON.stringify({ policyId: target.policyId, envelope }),
			}),
		);
	} catch {
		return new Response(null, { status: 400 });
	} finally {
		reader.releaseLock();
	}
}

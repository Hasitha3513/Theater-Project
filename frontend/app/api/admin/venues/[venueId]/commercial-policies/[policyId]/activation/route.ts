import { isPolicyActivationPath } from "../../../../../../../../lib/commercial-policies";
import { forwardCommercialPolicies } from "../../../../../../../../lib/room-backend";

function forwardActivation(
	request: Request,
	method: "GET" | "POST",
): Promise<Response> {
	const url = new URL(request.url);
	if (request.method !== method || !isPolicyActivationPath(url.pathname))
		return Promise.resolve(new Response(null, { status: 404 }));
	return forwardCommercialPolicies(request);
}

export function GET(request: Request) {
	return forwardActivation(request, "GET");
}

export function POST(request: Request) {
	return forwardActivation(request, "POST");
}

import { forwardSession } from "../../../../lib/backend";
export const dynamic = "force-dynamic";
export function POST(request: Request) {
	return forwardSession(request, "/api/session/logout");
}

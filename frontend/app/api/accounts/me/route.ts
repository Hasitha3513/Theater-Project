import { forwardSession } from "../../../../lib/backend";
export const dynamic = "force-dynamic";
export function GET(request: Request) {
	return forwardSession(request, "/api/accounts/me");
}

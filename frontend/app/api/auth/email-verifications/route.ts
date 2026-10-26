import { forwardManualAuth } from "../../../../lib/manual-auth-backend";
export const dynamic = "force-dynamic";
export function POST(request: Request) {
	return forwardManualAuth(request, "email-verifications");
}

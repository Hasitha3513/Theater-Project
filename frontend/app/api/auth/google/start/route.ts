import { forwardGoogleSso } from "../../../../../lib/google-sso-backend";
export const dynamic = "force-dynamic";
export function GET(request: Request) {
	return forwardGoogleSso(request, "start");
}

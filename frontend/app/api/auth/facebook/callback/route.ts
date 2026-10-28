import { forwardFacebookSso } from "../../../../../lib/facebook-sso-backend";
export const dynamic = "force-dynamic";
export function GET(request: Request) {
	return forwardFacebookSso(request, "callback");
}

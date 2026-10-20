// Presentation only. HTTP responses and owning workflows remain authoritative.
export type StateKind =
	| "loading"
	| "empty"
	| "error"
	| "unauthenticated"
	| "denied"
	| "unconfigured"
	| "validation"
	| "not-found"
	| "conflict"
	| "unavailable";
export const statePresentation: Record<
	StateKind,
	{ title: string; message: string; symbol: string; urgent: boolean }
> = {
	loading: {
		title: "Loading",
		message: "Getting the latest information. No result is confirmed yet.",
		symbol: "…",
		urgent: false,
	},
	empty: {
		title: "No results",
		message: "There are no matching records. Check your selection or filters.",
		symbol: "—",
		urgent: false,
	},
	error: {
		title: "Request not completed",
		message:
			"We could not confirm the result. Check the current status before repeating a submission.",
		symbol: "!",
		urgent: true,
	},
	unauthenticated: {
		title: "Sign-in required",
		message:
			"Sign in to continue. Your session may have ended or expired. Protected details are not shown.",
		symbol: "!",
		urgent: true,
	},
	denied: {
		title: "Access denied",
		message:
			"You do not have current permission for this request. Check your account and assigned scope; reloading does not grant access.",
		symbol: "!",
		urgent: true,
	},
	unconfigured: {
		title: "Connection unavailable",
		message:
			"A service connection is unavailable. Contact the responsible operator. No account or successful result is assumed.",
		symbol: "!",
		urgent: true,
	},
	validation: {
		title: "Check your details",
		message:
			"The request was not accepted. Review the entered details and correct any highlighted fields before trying again.",
		symbol: "!",
		urgent: true,
	},
	"not-found": {
		title: "Item unavailable",
		message:
			"This item could not be found or is not accessible. Check the reference or return to the list.",
		symbol: "?",
		urgent: true,
	},
	conflict: {
		title: "Information changed",
		message:
			"This request conflicts with the current state. Reload the latest information and review it before taking another action.",
		symbol: "↻",
		urgent: true,
	},
	unavailable: {
		title: "Service unavailable",
		message:
			"Current information could not be loaded. Reload the status when the service is available. Do not repeat a submission whose outcome is unknown.",
		symbol: "!",
		urgent: true,
	},
};
export function stateForStatus(status: number): StateKind {
	if (status === 401) return "unauthenticated";
	if (status === 403) return "denied";
	if (status === 400 || status === 422) return "validation";
	if (status === 404) return "not-found";
	if (status === 409 || status === 412) return "conflict";
	if (status === 408 || status === 429 || (status >= 500 && status <= 599))
		return "unavailable";
	return "error";
}

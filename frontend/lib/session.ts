export type Account = { accountId: string; eligibility: string };
export type SessionResult =
	| { kind: "authenticated"; account: Account }
	| { kind: "unauthenticated" | "denied" | "unconfigured" | "error" };
export async function readSession(
	request: typeof fetch = fetch,
): Promise<SessionResult> {
	try {
		const response = await request("/api/accounts/me", {
			credentials: "same-origin",
			cache: "no-store",
		});
		if (response.status === 401) return { kind: "unauthenticated" };
		if (response.status === 403) return { kind: "denied" };
		if (response.status === 503) return { kind: "unconfigured" };
		if (!response.ok) return { kind: "error" };
		const data = await response.json();
		if (
			typeof data.accountId !== "string" ||
			!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/.test(data.accountId) ||
			data.eligibility !== "ELIGIBLE"
		)
			return { kind: "denied" };
		return {
			kind: "authenticated",
			account: { accountId: data.accountId, eligibility: data.eligibility },
		};
	} catch {
		return { kind: "error" };
	}
}
export async function endSession(
	request: typeof fetch = fetch,
): Promise<boolean> {
	try {
		const csrf = await request("/api/session/csrf", {
			credentials: "same-origin",
			cache: "no-store",
		});
		if (!csrf.ok) return false;
		const data = await csrf.json();
		if (
			data.headerName !== "X-CSRF-TOKEN" ||
			typeof data.token !== "string" ||
			!data.token
		)
			return false;
		const response = await request("/api/session/logout", {
			method: "POST",
			credentials: "same-origin",
			headers: { "X-CSRF-TOKEN": data.token },
			cache: "no-store",
		});
		return response.status === 204;
	} catch {
		return false;
	}
}

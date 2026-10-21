// Test-only URL admission, not an application authorization boundary.
const reference =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function safeCustomerNavigation(value, origin) {
	try {
		const url = new URL(value, origin);
		if (url.origin !== origin || url.username || url.password || url.hash)
			return false;
		const entries = [...url.searchParams];
		if (entries.length === 0)
			return (
				[
					"/",
					"/account",
					"/rooms",
					"/bookings",
					"/bank",
					"/verification",
				].includes(url.pathname) ||
				(/^\/(rooms|bookings)\/[0-9a-f-]+$/i.test(url.pathname) &&
					reference.test(url.pathname.split("/")[2]))
			);
		if (entries.length !== 1) return false;
		const [key, ref] = entries[0];
		const allowed =
			url.pathname === "/bank"
				? ["quote", "request", "evidence"]
				: url.pathname === "/verification"
					? ["quote"]
					: [];
		return allowed.includes(key) && reference.test(ref);
	} catch {
		return false;
	}
}

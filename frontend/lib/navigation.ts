// Presentation only; route visibility never establishes server authority.
export function isActive(path: string, href: string) {
	return (
		path === href ||
		(href !== "/" && href !== "/admin" && path.startsWith(`${href}/`))
	);
}

export const adminNavigation = [
	["/admin", "Workspace"],
	["/admin/components", "UI examples"],
	["/admin/account", "My account"],
	["/admin/accounts", "Accounts and audit"],
	["/admin/venues", "Venues"],
	["/admin/rooms", "Room configuration"],
	["/admin/inventory", "Inventory inspection"],
	["/admin/bookings", "Bookings"],
	["/admin/verification", "Verification review"],
	["/admin/bank", "Bank review"],
	["/admin/refunds", "Refund review"],
	["/admin/access", "Access simulator"],
	["/admin/iot", "Lighting simulator"],
	["/admin/operations", "Room operations"],
	["/admin/reports/bank", "Bank report"],
	["/admin/notifications", "Notification queue"],
	["/admin/settings/packages", "Package drafts"],
] as const;

export function adminSection(path: string) {
	return adminNavigation.find(([href]) => isActive(path, href));
}
// These route references are navigation context, never grants or verified venue names.
export function adminRouteContext(path: string) {
	const section = adminSection(path);
	if (!section) return undefined;
	const uuid = "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})";
	const booking = new RegExp(
		`^/admin/bookings/venues/${uuid}/${uuid}$`,
		"i",
	).exec(path);
	const review = new RegExp(
		`^/admin/(verification|bank)/venues/${uuid}/requests/${uuid}$`,
		"i",
	).exec(path);
	const venue = booking?.[1] ?? review?.[2];
	return {
		section,
		venue: venue?.toLowerCase(),
		detail: Boolean(booking || review),
		href: venue ? `${section[0]}?venue=${venue.toLowerCase()}` : section[0],
	};
}

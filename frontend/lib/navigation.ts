// Presentation only; route visibility never establishes server authority.
export function isActive(path: string, href: string) {
	return (
		path === href ||
		(href !== "/" && href !== "/admin" && path.startsWith(`${href}/`))
	);
}

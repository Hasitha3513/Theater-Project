"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import tokens from "../../knowledge/tokens/cinenest-design-tokens.json";

export function isActive(path: string, href: string) {
	return path === href;
}
function Brand() {
	return (
		<span className="brand">
			<span className="brand-mark" aria-hidden="true">
				◉
			</span>
			<span>
				CineNest<small>Your private cinema experience</small>
			</span>
		</span>
	);
}
export function Navigation({
	admin,
	path,
	onNavigate,
}: {
	admin: boolean;
	path: string;
	onNavigate?: () => void;
}) {
	const links = admin
		? [
				["/admin", "Workspace"],
				["/admin/components", "UI examples"],
				["/admin/account", "Account session"],
                ["/admin/accounts", "Synthetic accounts and audit"],
                ["/admin/venues", "Venues"],
                ["/admin/rooms", "Room configuration"],
                ["/admin/inventory", "Inventory inspection"],
                ["/admin/verification", "Synthetic verification review"],
                ["/admin/bank", "Synthetic bank review"],
                ["/admin/refunds", "Synthetic bank-only refunds"],
                ["/admin/access", "Synthetic access simulation"],
                ["/admin/notifications", "Synthetic notification queue"],
                ["/admin/settings/packages", "Package drafts"],
			]
		: [
				["/", "Home"],
                ["/rooms", "Explore rooms"],
                ["/verification", "Synthetic verification"],
                ["/bank", "Synthetic bank request"],
                ["/refunds", "Synthetic refund status"],
                ["/access", "Synthetic access status"],
                ["/notifications", "Synthetic notifications"],
				["/account", "Account session"],
				["/admin", "Admin shell preview"],
			];
	return (
		<nav aria-label={admin ? "Administration preview" : "Customer navigation"}>
			<ul>
				{links.map(([href, label]) => (
					<li key={href}>
						<Link
							href={href}
							aria-current={isActive(path, href) ? "page" : undefined}
							onClick={onNavigate}
						>
							{label}
						</Link>
					</li>
				))}
			</ul>
			{admin && (
				<div className="planned-nav">
					<p>Future modules</p>
					<ul>
						{[
							"Bookings",
							"Customers",
							"Payments",
							"Devices",
							"Reports",
							"Promotions",
							"Settings",
							"Audit logs",
						].map((label) => (
							<li key={label}>
								<span>{label}</span>
								<small>Planned</small>
							</li>
						))}
					</ul>
				</div>
			)}
		</nav>
	);
}
export function ShellFrame({
	admin,
	path,
	children,
	menu,
}: {
	admin: boolean;
	path: string;
	children: ReactNode;
	menu?: ReactNode;
}) {
	return (
		<div
			className={admin ? "app-shell admin-shell" : "app-shell customer-shell"}
			data-cn-theme={admin ? "booking-panel" : "cinematic"}
		>
			<a className="skip-link" href="#main-content">
				Skip to content
			</a>
			{admin && (
				<aside className="sidebar">
					<Link href="/" className="brand-link" aria-label="CineNest home">
						<Brand />
					</Link>
					<p className="sidebar-label">Administration · shell preview</p>
					<Navigation admin path={path} />
					<Link className="sidebar-home" href="/">
						← Customer shell
					</Link>
				</aside>
			)}
			<div className="shell-body">
				<header className="topbar">
					{menu}
					{admin ? (
						<span className="context-label">CineNest workspace</span>
					) : (
						<Link href="/" className="brand-link" aria-label="CineNest home">
							<Brand />
						</Link>
					)}
					<div className="desktop-customer-nav">
						{!admin && <Navigation admin={false} path={path} />}
					</div>
					<span className="preview-label">Development preview</span>
				</header>
				<main id="main-content" tabIndex={-1} className="page-content">
					{children}
				</main>
				<footer className="shell-footer">
					CineNest · UI foundation. Booking, payment and room controls are not
					available.
				</footer>
			</div>
		</div>
	);
}
export function ApplicationShell({ children }: { children: ReactNode }) {
	const path = usePathname();
	const admin = path.startsWith("/admin");
	const dialog = useRef<HTMLDialogElement>(null);
	const trigger = useRef<HTMLButtonElement>(null);
	const [open, setOpen] = useState(false);
	useEffect(() => {
		const query = window.matchMedia(`(min-width: ${tokens.breakpoint.lg}px)`);
		const close = () => {
			if (query.matches) {
				dialog.current?.close();
				setOpen(false);
			}
		};
		query.addEventListener("change", close);
		return () => query.removeEventListener("change", close);
	}, []);
	useEffect(() => {
		if (path) {
			dialog.current?.close();
			setOpen(false);
		}
	}, [path]);
	return (
		<>
			<ShellFrame
				admin={admin}
				path={path}
				menu={
					<button
						ref={trigger}
						className="menu-toggle"
						type="button"
						aria-label="Open navigation"
						aria-controls="mobile-navigation"
						aria-expanded={open}
						onClick={() => {
							dialog.current?.showModal();
							setOpen(true);
						}}
					>
						☰ <span>Menu</span>
					</button>
				}
			>
				{children}
			</ShellFrame>
			<dialog
				id="mobile-navigation"
				className="nav-dialog"
				ref={dialog}
				aria-labelledby="navigation-title"
                onKeyDown={(event) => {
                    if (event.key !== "Tab") return;
                    const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>("a[href], button:not([disabled])"));
                    const first = controls[0]; const last = controls.at(-1);
                    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
                    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
                }}
				onClose={() => {
					setOpen(false);
					trigger.current?.focus();
				}}
			>
				<div className="drawer-heading">
					<h2 id="navigation-title">CineNest navigation</h2>
					<button
						type="button"
						className="button button-secondary"
						onClick={() => dialog.current?.close()}
					>
						Close navigation
					</button>
				</div>
				<Navigation
					admin={admin}
					path={path}
					onNavigate={() => dialog.current?.close()}
				/>
			</dialog>
		</>
	);
}

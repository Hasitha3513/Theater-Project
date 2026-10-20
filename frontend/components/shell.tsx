"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import tokens from "../../knowledge/tokens/cinenest-design-tokens.json";

import { isActive } from "../lib/navigation";
export { isActive } from "../lib/navigation";
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
			]
		: [
				["/", "Home"],
				["/rooms", "Rooms"],
				["/verification", "Verify"],
				["/bank", "Bank request"],
				["/bookings", "My bookings"],
				["/refunds", "Refunds"],
				["/access", "Access simulator"],
				["/notifications", "Inbox"],
				["/account", "Account"],
				["/admin", "Staff"],
			];
	return (
		<nav
			aria-label={admin ? "Administration navigation" : "Customer navigation"}
		>
			<ul>
				{links.map(([href, label]) => (
					<li key={href}>
						<Link
							href={href}
							aria-current={
								isActive(path, href)
									? path === href
										? "page"
										: "location"
									: undefined
							}
							onClick={onNavigate}
						>
							{label}
						</Link>
					</li>
				))}
			</ul>
			{admin && (
				<div className="planned-nav">
					<p>Release limits</p>
					<small>
						Online payments and rewards are deferred. Tools remain synthetic.
					</small>
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
					<p className="sidebar-label">Administration</p>
					<Navigation admin path={path} />
					<Link className="sidebar-home" href="/">
						← Customer home
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
					<span
						className="preview-label"
					>
						Synthetic environment
					</span>
				</header>
				<main id="main-content" tabIndex={-1} className="page-content">
					{children}
				</main>
				<footer className="shell-footer">
					CineNest · Synthetic bookings and test identity verification only.
					Real payments, physical access and equipment controls are not enabled.
					Production operations are not authorized.
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
					const controls = Array.from(
						event.currentTarget.querySelectorAll<HTMLElement>(
							"a[href], button:not([disabled])",
						),
					);
					const first = controls[0];
					const last = controls.at(-1);
					if (event.shiftKey && document.activeElement === first) {
						event.preventDefault();
						last?.focus();
					} else if (!event.shiftKey && document.activeElement === last) {
						event.preventDefault();
						first?.focus();
					}
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

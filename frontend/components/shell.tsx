"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import tokens from "../../knowledge/tokens/cinenest-design-tokens.json";

import {
	isActive,
	adminNavigation,
	adminSection,
	adminRouteContext,
} from "../lib/navigation";
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
		? adminNavigation
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

export function AdminBreadcrumb({ path }: { path: string }) {
	const context = adminRouteContext(path);
	if (!context) return null;
	return (
		<nav className="admin-breadcrumb" aria-label="Administration breadcrumbs">
			<ol>
				<li>
					<Link href="/admin" prefetch={false}>
						Administration
					</Link>
				</li>
				{context.section[0] !== "/admin" && (
					<li>
						{context.detail ? (
							<Link href={context.href} prefetch={false}>
								{context.section[1]}
							</Link>
						) : (
							<span aria-current="page">{context.section[1]}</span>
						)}
					</li>
				)}
				{context.detail && (
					<li>
						<span aria-current="page">Details</span>
					</li>
				)}
			</ol>
			{context.venue && (
				<p>
					Route venue: <span>{context.venue}</span> · Selection only; current
					server permission is required.
				</p>
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
						<span className="context-label">
							{adminSection(path)?.[1] ?? "CineNest workspace"}
						</span>
					) : (
						<Link href="/" className="brand-link" aria-label="CineNest home">
							<Brand />
						</Link>
					)}
					<div className="desktop-customer-nav">
						{!admin && <Navigation admin={false} path={path} />}
					</div>
					<span className="preview-label">Synthetic environment</span>
				</header>
				<main id="main-content" tabIndex={-1} className="page-content">
					{admin && <AdminBreadcrumb path={path} />}
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
	const admin = path === "/admin" || path.startsWith("/admin/");
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

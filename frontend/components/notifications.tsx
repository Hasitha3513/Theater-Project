"use client";
import { useEffect, useRef, useState } from "react";
import { roomRequest } from "../lib/rooms";
import { Badge, Button, Card, Field, SelectField } from "./primitives";
export const NOTIFICATION_LABEL =
	"SYNTHETIC NOTIFICATION TEST — NO REAL DELIVERY";
type Notice = {
	synthetic: true;
	liveDelivery: false;
	id: string;
	kind: string;
	revision: number;
	state: string;
	read_at: string | null;
	expires_at: string;
	template_version: number;
	message?: string;
	destination?: string;
	operation_id: string;
	attemptHistory?: {
		attempt_id: string;
		fence: number;
		outcome: string;
		created_at: string;
	}[];
	history?: {
		audit_id: string;
		action: string;
		outcome: string;
		created_at: string;
	}[];
};
type Inbox = {
	items: Notice[];
	unreadCount?: number;
	unassigned?: number;
	unassignedWork?: { event_id: string; kind: string; state: string }[];
	page: number;
	size: number;
	hasNext: boolean;
};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function notificationRecord(value: unknown): Notice {
	if (!value || typeof value !== "object")
		throw new Error("Invalid synthetic notification");
	const r = value as Notice;
	if (
		r.synthetic !== true ||
		r.liveDelivery !== false ||
		!UUID.test(r.id) ||
		!Number.isSafeInteger(r.revision) ||
		r.revision < 0 ||
		r.template_version !== 1 ||
		![
			"READY",
			"LEASED",
			"SIMULATOR_ACCEPTED",
			"DELIVERY_FAILED",
			"DELIVERY_UNKNOWN",
			"SUPPRESSED",
		].includes(r.state) ||
		(r.destination !== undefined &&
			!["/bank", "/access", "/bookings"].includes(r.destination))
	)
		throw new Error("Invalid synthetic notification");
	return r;
}
function notificationInbox(value: Inbox): Inbox {
	if (
		!Array.isArray(value.items) ||
		value.items.length > 50 ||
		!Number.isSafeInteger(value.page) ||
		value.page < 0
	)
		throw new Error("Invalid synthetic inbox");
	return { ...value, items: value.items.map(notificationRecord) };
}

export function NotificationWorkspace({ admin = false }: { admin?: boolean }) {
	const [venue, setVenue] = useState("");
	const [context, setContext] = useState("");
	const [page, setPage] = useState(0);
	const [data, setData] = useState<Inbox | null>(null);
	const [detail, setDetail] = useState<Notice | null>(null);
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const [fixture, setFixture] = useState("ACCEPTED");
	const generation = useRef(0);
	useEffect(() => {
		const clear = () => {
			generation.current++;
			setData(null);
			setDetail(null);
			setError("");
			setContext("");
			setBusy(false);
		};
		window.addEventListener("cinenest-session-change", clear);
		const hidden = () => {
			if (document.visibilityState === "hidden") clear();
		};
		document.addEventListener("visibilitychange", hidden);
		window.addEventListener("pagehide", clear);
		return () => {
			generation.current++;
			window.removeEventListener("cinenest-session-change", clear);
			document.removeEventListener("visibilitychange", hidden);
			window.removeEventListener("pagehide", clear);
		};
	}, []);
	const base = admin
		? `/api/admin/venues/${context}/notifications`
		: "/api/notifications";
	async function load(next = page, selected?: string, command?: string) {
		if (admin && !UUID.test(context)) {
			setError("Enter an authorized venue UUID and open that context.");
			return;
		}
		const epoch = ++generation.current;
		setError("");
		setBusy(true);
		setDetail(null);
		try {
			const path = selected
				? `${base}/${selected}`
				: `${base}?page=${next}&size=10`;
			let result: Notice | Inbox;
			if (command) {
				if (!detail)
					throw new Error("Refresh the selected notification first.");
				result = await roomRequest<Notice>(`${path}/${command}`, {
					method: "POST",
					body: JSON.stringify({
						commandKey: crypto.randomUUID(),
						expectedRevision: detail.revision,
						...(command === "simulations" ? { fixture } : {}),
					}),
				});
			} else result = await roomRequest<Notice | Inbox>(path);
			if (command) {
				const refreshed = await roomRequest<Inbox>(
					`${base}?page=${page}&size=10`,
				);
				if (epoch !== generation.current) return;
				setData(notificationInbox(refreshed));
			}
			if (epoch !== generation.current) return;
			if (selected) setDetail(notificationRecord(result));
			else {
				setData(notificationInbox(result as Inbox));
				setPage(next);
			}
		} catch {
			if (epoch === generation.current) {
				setData(null);
				setDetail(null);
				setError(
					"Notification unavailable, expired, denied or changed. Refresh current context before retrying.",
				);
			}
		} finally {
			if (epoch === generation.current) setBusy(false);
		}
	}
	function changeVenue(value: string) {
		generation.current++;
		setVenue(value);
		setContext("");
		setData(null);
		setDetail(null);
		setError("");
		setBusy(false);
		setPage(0);
	}
	return (
		<div className="notification-workspace">
			<h1>{admin ? "Notification administration" : "Notifications"}</h1>
			<p>
				<Badge tone="warning">{NOTIFICATION_LABEL}</Badge>
			</p>
			<p>
				No email, SMS, live messaging, payment evidence or access credentials
				are delivered.
			</p>
			{admin && (
				<Card title="Venue context">
					<Field
						id="notification-venue"
						label="Authorized venue UUID"
						value={venue}
						onChange={(e) => changeVenue(e.target.value)}
					/>
					<Button
						disabled={!UUID.test(venue) || busy}
						onClick={() => {
							generation.current++;
							setContext(venue);
							setData(null);
							setDetail(null);
							setPage(0);
						}}
					>
						Open venue
					</Button>
				</Card>
			)}
			<Button
				busy={busy}
				disabled={admin && !context}
				onClick={() => void load(0)}
			>
				Refresh notifications
			</Button>
			<div aria-live="polite" role="status">
				{busy ? "Loading notifications…" : error}
			</div>
			{data && (
				<Card title={admin ? "Redacted delivery queue" : "Your inbox"}>
					<p>
						{admin
							? `Unassigned work: ${data.unassigned ?? 0}`
							: `Unread notifications: ${data.unreadCount ?? 0}`}
					</p>
					{admin && data.unassignedWork && (
						<ul aria-label="Unassigned work">
							{data.unassignedWork.map((w) => (
								<li key={w.event_id}>
									{w.kind}: {w.state} — recipient assignment unresolved
								</li>
							))}
						</ul>
					)}
					{data.items.length === 0 ? (
						<p>No current notifications available.</p>
					) : (
						<ul>
							{data.items.map((n) => (
								<li key={n.id}>
									<Button
										variant="secondary"
										disabled={busy}
										onClick={() => void load(page, n.id)}
									>
										{n.kind.replaceAll("_", " ")}
									</Button>
									<span>
										{" "}
										{n.state} ·{" "}
										{n.read_at ? "USER_MARKED_READ" : "Not marked read"}
									</span>
								</li>
							))}
						</ul>
					)}
					<p>Page {page + 1}</p>
					<Button
						variant="secondary"
						disabled={busy || page === 0}
						onClick={() => void load(page - 1)}
					>
						Previous
					</Button>
					<Button
						variant="secondary"
						disabled={busy || !data.hasNext}
						onClick={() => void load(page + 1)}
					>
						Next
					</Button>
				</Card>
			)}
			{detail && (
				<Card title="Notification detail">
					<p>{detail.kind}</p>
					<p>
						Delivery: {detail.state}. Template version:{" "}
						{detail.template_version}. Revision: {detail.revision}.
					</p>
					<p>Expires: {detail.expires_at}</p>
					{!admin && (
						<>
							<p>{detail.message}</p>
							{["/bank", "/access", "/bookings"].includes(
								detail.destination ?? "",
							) && <a href={detail.destination}>Open protected workspace</a>}
							<Button
								disabled={busy || !!detail.read_at}
								onClick={() => void load(page, detail.id, "read")}
							>
								Mark as read
							</Button>
							<p>
								Marking read records your action; it does not establish funds,
								access or comprehension.
							</p>
						</>
					)}
					{admin && (
						<>
							<p>Metadata only. No private message body or credential.</p>
							<SelectField
								id="notification-fixture"
								label="Local capture fixture"
								value={fixture}
								onChange={(e) => setFixture(e.target.value)}
							>
								{[
									"ACCEPTED",
									"FAILED",
									"UNKNOWN_ACCEPTED",
									"UNKNOWN_FAILED",
								].map((f) => (
									<option key={f} value={f}>
										{f}
									</option>
								))}
							</SelectField>
							<Button
								disabled={busy || detail.state !== "READY"}
								onClick={() => void load(page, detail.id, "simulations")}
							>
								Configure local simulation
							</Button>
							<Button
								disabled={busy || detail.state !== "DELIVERY_FAILED"}
								onClick={() => void load(page, detail.id, "retries")}
							>
								Request safe retry
							</Button>
							<Button
								disabled={busy || detail.state !== "DELIVERY_UNKNOWN"}
								onClick={() => void load(page, detail.id, "reconciliations")}
							>
								Reconcile durable evidence
							</Button>
							<h3>Attempt history</h3>
							<ul>
								{detail.attemptHistory?.map((a) => (
									<li key={a.attempt_id}>
										{a.fence}: {a.outcome} · {a.created_at}
									</li>
								))}
							</ul>
							<h3>Audit timeline</h3>
							<ul>
								{detail.history?.map((a) => (
									<li key={a.audit_id}>
										{a.action}: {a.outcome}
									</li>
								))}
							</ul>
						</>
					)}
				</Card>
			)}
		</div>
	);
}

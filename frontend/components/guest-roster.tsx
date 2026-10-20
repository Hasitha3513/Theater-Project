"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { roomRequest, RoomRequestError, stateFor } from "../lib/rooms";
import { Button, Card, Field, StateMessage } from "./primitives";
type Entry = {
	id: string;
	kind: "BOOKER" | "COMPANION";
	label: string;
	status: "UNVERIFIED";
};
type Roster = {
	bookingId: string;
	bookingRevision: number;
	revision: number;
	initialized: boolean;
	synthetic: true;
	status: "UNVERIFIED";
	physicalAdmission: false;
	ageVerified: false;
	accessIssued: false;
	purchasedLimit: number;
	roomCapacity: number;
	count: number;
	remaining: number;
	entries: Entry[];
	permittedActions: ("INITIALIZE" | "ADD")[];
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function rosterRecord(input: unknown, booking: string): Roster {
	const r = input as Roster;
	if (
		!r ||
		!uuid.test(booking) ||
		r.bookingId !== booking ||
		r.synthetic !== true ||
		r.status !== "UNVERIFIED" ||
		r.physicalAdmission !== false ||
		r.ageVerified !== false ||
		r.accessIssued !== false ||
		typeof r.initialized !== "boolean" ||
		![
			r.bookingRevision,
			r.revision,
			r.purchasedLimit,
			r.roomCapacity,
			r.count,
			r.remaining,
		].every((n) => Number.isSafeInteger(n) && n >= 0) ||
		r.bookingRevision < 1 ||
		r.purchasedLimit < 1 ||
		r.roomCapacity < 1 ||
		!Array.isArray(r.entries) ||
		r.entries.length !== r.count ||
		!Array.isArray(r.permittedActions) ||
		r.permittedActions.length > 1 ||
		r.permittedActions.some((a) => !["INITIALIZE", "ADD"].includes(a)) ||
		r.remaining !==
			Math.max(0, Math.min(r.purchasedLimit, r.roomCapacity) - r.count)
	)
		throw Error("Invalid synthetic roster context");
	const ids = new Set(),
		labels = new Set();
	let bookers = 0;
	for (const e of r.entries) {
		if (
			!e ||
			!uuid.test(e.id) ||
			ids.has(e.id) ||
			labels.has(e.label) ||
			e.status !== "UNVERIFIED" ||
			!(
				(e.kind === "BOOKER" && e.label === "BOOKER") ||
				(e.kind === "COMPANION" && /^SYNTHETIC-[1-9][0-9]{0,5}$/.test(e.label))
			)
		)
			throw Error("Invalid synthetic roster entries");
		ids.add(e.id);
		labels.add(e.label);
		if (e.kind === "BOOKER") bookers++;
	}
	if (
		bookers !== (r.initialized ? 1 : 0) ||
		(!r.initialized && r.count !== 0) ||
		(r.permittedActions.includes("ADD") && !r.initialized) ||
		(r.permittedActions.includes("INITIALIZE") && r.initialized)
	)
		throw Error("Invalid booker binding");
	return r;
}
export function GuestRoster({ bookingId }: { bookingId: string }) {
	const [value, setValue] = useState<Roster>();
	const [error, setError] = useState<unknown>();
	const [busy, setBusy] = useState(false);
	const [number, setNumber] = useState("");
	const epoch = useRef(0);
	const pending = useRef<{ path: string; body: object } | undefined>(undefined);
	const base = `/api/bookings/${bookingId}/guest-roster`;
	const load = useCallback(async () => {
		const n = ++epoch.current;
		setValue(undefined);
		setError(undefined);
		setBusy(true);
		try {
			const r = rosterRecord(await roomRequest(base), bookingId);
			if (n === epoch.current) setValue(r);
		} catch (e) {
			if (n === epoch.current) setError(e);
		} finally {
			if (n === epoch.current) setBusy(false);
		}
	}, [base, bookingId]);
	useEffect(() => {
		void load();
		const clear = () => {
			++epoch.current;
			setValue(undefined);
			setError(undefined);
			setBusy(false);
			pending.current = undefined;
		};
		const visibility = () => {
			if (document.hidden) clear();
			else void load();
		};
		const refresh = () => void load();
		window.addEventListener("focus", refresh);
		window.addEventListener("pageshow", refresh);
		window.addEventListener("pagehide", clear);
		window.addEventListener("cinenest-session-change", clear);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			clear();
			window.removeEventListener("focus", refresh);
			window.removeEventListener("pageshow", refresh);
			window.removeEventListener("pagehide", clear);
			window.removeEventListener("cinenest-session-change", clear);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [load]);
	async function submit(add: boolean) {
		if (busy || !value) return;
		const n = ++epoch.current;
		setBusy(true);
		setError(undefined);
		try {
			if (!pending.current) {
				const fresh = rosterRecord(await roomRequest(base), bookingId);
				if (n !== epoch.current) return;
				if (
					fresh.revision !== value.revision ||
					fresh.bookingRevision !== value.bookingRevision ||
					!fresh.permittedActions.includes(add ? "ADD" : "INITIALIZE")
				) {
					setValue(fresh);
					throw Error("Roster changed; review current context");
				}
				pending.current = {
					path: add ? `${base}/companions` : base,
					body: {
						commandKey: crypto.randomUUID(),
						bookingRevision: fresh.bookingRevision,
						revision: fresh.revision,
						...(add ? { label: `SYNTHETIC-${number}` } : {}),
					},
				};
			}
			const r = rosterRecord(
				await roomRequest(pending.current.path, {
					method: "POST",
					body: JSON.stringify(pending.current.body),
				}),
				bookingId,
			);
			if (n !== epoch.current) return;
			pending.current = undefined;
			setValue(r);
			setNumber("");
		} catch (e) {
			if (n !== epoch.current) return;
			if (
				e instanceof RoomRequestError &&
				[400, 401, 403, 404, 409].includes(e.status)
			) {
				pending.current = undefined;
				setValue(undefined);
			}
			setError(e);
		} finally {
			if (n === epoch.current) setBusy(false);
		}
	}
	return (
		<Card title="Synthetic guest roster" className="guest-roster-panel">
			<p>
				UNVERIFIED / SYNTHETIC. No real names, contact details, documents or age
				declarations. Registration grants no admission, identity approval or
				access credentials.
			</p>
			<p>
				Labels use SYNTHETIC-1 to SYNTHETIC-999999 only. Different labels do not
				prove distinct people. The server-bound booker counts as one guest. No
				edits or removals.
			</p>
			<Button variant="secondary" busy={busy} onClick={() => void load()}>
				Reload guest roster
			</Button>
			{busy && <StateMessage kind="loading" />}
			{error !== undefined && (
				<StateMessage kind={stateFor(error)}>
					<p>
						Roster unavailable, denied or changed. Refresh to recover; an
						uncertain command must use its original retry.
					</p>
				</StateMessage>
			)}
			{value && (
				<>
					<p className="guest-roster-capacity">
						Purchased guest limit: {value.purchasedLimit}. Current room
						capacity: {value.roomCapacity}. Roster count: {value.count}.
						Remaining capacity: {value.remaining}.
					</p>
					{value.entries.length === 0 ? (
						<StateMessage kind="empty">
							<p>No roster initialized.</p>
						</StateMessage>
					) : (
						<ul>
							{value.entries.map((e) => (
								<li key={e.id}>
									{e.kind === "BOOKER" ? "Server-bound BOOKER" : e.label} —
									UNVERIFIED / SYNTHETIC
								</li>
							))}
						</ul>
					)}
					{value.permittedActions.includes("INITIALIZE") && (
						<Button disabled={busy} onClick={() => void submit(false)}>
							Create synthetic roster
						</Button>
					)}
					{value.permittedActions.includes("ADD") && (
						<>
							<Field
								id={`companion-${bookingId}`}
								label="Synthetic companion number"
								inputMode="numeric"
								value={number}
								disabled={busy || !!pending.current}
								onChange={(e) => setNumber(e.target.value)}
							/>
							<Button
								disabled={
									busy || !!pending.current || !/^[1-9][0-9]{0,5}$/.test(number)
								}
								onClick={() => void submit(true)}
							>
								Add synthetic companion
							</Button>
						</>
					)}
					{pending.current && !busy && (
						<Button
							onClick={() =>
								void submit(
									pending.current?.path.endsWith("/companions") === true,
								)
							}
						>
							Retry original roster command
						</Button>
					)}
				</>
			)}
		</Card>
	);
}

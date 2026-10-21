"use client";
import { useEffect, useRef, useState } from "react";
import { RoomRequestError, roomRequest } from "../lib/rooms";
import type { BookingAccessContext } from "../lib/staff-booking-access";
import { Button, Card, Field, SelectField, StateMessage } from "./primitives";
import { StaffBookingAccessPanel } from "./staff-booking-access";
export const ACCESS_LABEL = "SYNTHETIC ACCESS TEST — NO DOOR CONTROL";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type AccessRecord = {
	id?: string;
	bookingId: string;
	bookingRevision: number;
	venueId?: string;
	roomId?: string;
	version?: number;
	revision?: number;
	state: string;
	provisioning?: string;
	operationId?: string;
	sessionStart?: string;
	sessionEnd?: string;
	timezone?: string;
	permittedActions: string[];
	history?: {
		id: string;
		actor: string;
		action: string;
		outcome: string;
		at: string;
	}[];
	synthetic: true;
	doorControlled: false;
	token?: string;
};
export function accessRecord(value: unknown): AccessRecord {
	const r = value as AccessRecord;
	if (
		r?.synthetic !== true ||
		r.doorControlled !== false ||
		!uuid.test(r.bookingId) ||
		!Number.isSafeInteger(r.bookingRevision) ||
		!Array.isArray(r.permittedActions) ||
		(r.id && !uuid.test(r.id))
	)
		throw new Error("Invalid synthetic access response");
	return r;
}
export function accessCommand(
	op: string,
	r: AccessRecord,
	venue: string,
	key: string,
	fields: Record<string, unknown> = {},
) {
	if (
		!uuid.test(key) ||
		!uuid.test(r.bookingId) ||
		!Number.isSafeInteger(r.bookingRevision) ||
		(op !== "ISSUE" && (!r.id || !uuid.test(r.id)))
	)
		throw new Error("Refresh current access context");
	const admin = ["REVOKE", "SIMULATE"].includes(op);
	if (admin && !uuid.test(venue)) throw new Error("Applicable venue required");
	const root = `/api/bookings/${r.bookingId}/access`;
	const path =
		op === "ISSUE" || op === "REISSUE"
			? `${root}/credentials`
			: op === "REVEAL"
				? `${root}/credentials/${r.id}/reveal`
				: op === "ATTEMPT"
					? `${root}/simulated-attempts`
					: op === "REVOKE"
						? `/api/admin/venues/${venue}/access/credentials/${r.id}/revocations`
						: op === "SIMULATE"
							? `/api/admin/venues/${venue}/access/credentials/${r.id}/simulations`
							: "";
	if (!path) throw new Error("Unsupported synthetic action");
	return {
		path,
		init: {
			method: "POST",
			body: JSON.stringify({
				...fields,
				expectedRevision: r.revision ?? 0,
				expectedVersion: r.version ?? 0,
				bookingRevision: r.bookingRevision,
				commandKey: key,
				reason: "SYNTHETIC access test",
				...(op === "ISSUE" || op === "REISSUE" ? { mode: op } : {}),
				...(op === "ATTEMPT" ? { credentialId: r.id } : {}),
				...(op === "SIMULATE"
					? { operationId: r.operationId, credentialVersion: r.version }
					: {}),
			}),
		},
	};
}
export function AccessWorkspace({
	admin = false,
	context,
	invalidContext = false,
}: {
	admin?: boolean;
	context?: BookingAccessContext;
	invalidContext?: boolean;
}) {
	if (admin && invalidContext)
		return (
			<StateMessage kind="validation">
				<p>
					A valid booking and venue reference are required. Return to staff
					booking results to select a booking.
				</p>
				<a href="/admin/bookings">Return to booking results</a>
			</StateMessage>
		);
	if (admin && context)
		return (
			<StaffBookingAccessPanel
				key={`${context.venue}/${context.booking}`}
				context={context}
			/>
		);
	return <AccessSimulationWorkspace admin={admin} />;
}
function AccessSimulationWorkspace({ admin = false }: { admin?: boolean }) {
	const [target, setTarget] = useState(""),
		[rows, setRows] = useState<AccessRecord[]>([]),
		[selected, setSelected] = useState<AccessRecord | null>(null),
		[busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Load an authorized synthetic booking or venue.",
		),
		[secret, setSecret] = useState(""),
		[token, setToken] = useState(""),
		[fixture, setFixture] = useState("SYNTHETIC-INSTALLED"),
		[page, setPage] = useState(0);
	const generation = useRef(0),
		pending = useRef<ReturnType<typeof accessCommand> | null>(null);
	useEffect(() => {
		const clear = () => {
			setSecret("");
			setToken("");
		};
		document.addEventListener("visibilitychange", clear);
		window.addEventListener("blur", clear);
		return () => {
			generation.current++;
			document.removeEventListener("visibilitychange", clear);
			window.removeEventListener("blur", clear);
		};
	}, []);
	function reset(value: string) {
		generation.current++;
		setTarget(value);
		setRows([]);
		setSelected(null);
		setSecret("");
		setToken("");
		pending.current = null;
		setBusy(false);
		setMessage("Refresh the new authorized context.");
	}
	async function load(nextPage = 0) {
		if (!uuid.test(target)) {
			setMessage("Enter a valid identifier.");
			return;
		}
		const g = ++generation.current;
		setBusy(true);
		setSecret("");
		setToken("");
		pending.current = null;
		try {
			const result = await roomRequest<unknown>(
				admin
					? `/api/admin/venues/${target}/access/credentials?page=${nextPage}&size=25`
					: `/api/bookings/${target}/access`,
			);
			if (g !== generation.current) return;
			const data = admin
				? (result as { items: unknown[] }).items.map(accessRecord)
				: [accessRecord(result)];
			setPage(nextPage);
			setRows(data);
			setSelected(admin ? null : data[0]);
			setMessage(
				data.length
					? "Current synthetic status loaded."
					: "No synthetic credentials in this venue.",
			);
		} catch (e) {
			if (g === generation.current) {
				setRows([]);
				setSelected(null);
				setMessage(
					e instanceof RoomRequestError
						? `${e.status}: Access unavailable or denied. Refresh before retrying.`
						: "Access unavailable.",
				);
			}
		} finally {
			if (g === generation.current) setBusy(false);
		}
	}
	async function action(op: string) {
		if (!selected) return;
		const g = generation.current;
		setBusy(true);
		setSecret("");
		try {
			const command =
				pending.current ??
				accessCommand(op, selected, target, crypto.randomUUID(), {
					...(op === "ATTEMPT" ? { token } : {}),
					...(op === "SIMULATE"
						? {
								fixture,
								reconcile: ["UNKNOWN", "RECONCILIATION_REQUIRED"].includes(
									selected.provisioning ?? "",
								),
							}
						: {}),
				});
			pending.current = command;
			const result = accessRecord(
				await roomRequest(command.path, command.init),
			);
			if (g !== generation.current) return;
			setSecret(op === "REVEAL" ? (result.token ?? "") : "");
			const safe = { ...result };
			delete safe.token;
			setSelected(safe);
			setRows((old) =>
				old.map((x) => (x.bookingId === safe.bookingId ? safe : x)),
			);
			pending.current = null;
			setToken("");
			setMessage("Synthetic command recorded. No door was controlled.");
		} catch (e) {
			if (g === generation.current) {
				pending.current = null;
				setToken("");
				setSelected(null);
				setMessage(
					e instanceof RoomRequestError
						? `${e.status}: Action denied, changed or outcome uncertain. Refresh; never blindly resend.`
						: "Action unavailable. Refresh before retrying.",
				);
			}
		} finally {
			if (g === generation.current) setBusy(false);
		}
	}
	return (
		<div className="page-stack access-workspace">
			<h1>
				{admin
					? "Access simulation administration"
					: "Booking access simulation"}
			</h1>
			<p role="note">{ACCESS_LABEL}</p>
			<p>
				Simulator evidence is separate from funding, booking confirmation and
				physical admission. No live delivery.
			</p>
			<Card title="Authorized context">
				<Field
					id="access-target"
					label={admin ? "Venue ID" : "Booking ID"}
					value={target}
					onChange={(e) => reset(e.target.value)}
				/>
				<Button busy={busy} onClick={() => load(0)}>
					Refresh status
				</Button>
				<p role="status" aria-live="polite">
					{message}
				</p>
			</Card>
			{admin && rows.length > 0 && (
				<Card title="Venue credential queue">
					<ul>
						{rows.map((r) => (
							<li key={r.id}>
								<Button
									disabled={busy}
									variant="secondary"
									onClick={() => {
										generation.current++;
										setSelected(r);
										setSecret("");
										setToken("");
										pending.current = null;
									}}
								>
									Inspect {r.bookingId}
								</Button>{" "}
								{r.state} / {r.provisioning}
							</li>
						))}
					</ul>
					<Button disabled={busy || page === 0} onClick={() => load(page - 1)}>
						Previous page
					</Button>
					<Button
						disabled={busy || rows.length < 25}
						onClick={() => load(page + 1)}
					>
						Next page
					</Button>
				</Card>
			)}
			{selected && (
				<Card title="Synthetic credential status">
					<dl className="account-summary">
						<dt>Booking</dt>
						<dd>{selected.bookingId}</dd>
						<dt>Credential</dt>
						<dd>{selected.state}</dd>
						<dt>Provisioning</dt>
						<dd>{selected.provisioning ?? "Not requested"}</dd>
						<dt>Purchased window</dt>
						<dd>
							{selected.sessionStart ?? "Not issued"} —{" "}
							{selected.sessionEnd ?? "Not issued"} {selected.timezone}
						</dd>
					</dl>
					{!admin && selected.permittedActions.includes("ATTEMPT") && (
						<Field
							id="access-token"
							type="password"
							autoComplete="off"
							label="Synthetic credential for one simulated entry"
							value={token}
							onChange={(e) => setToken(e.target.value)}
						/>
					)}
					{admin && selected.permittedActions.includes("SIMULATE") && (
						<SelectField
							id="access-fixture"
							label="Controlled simulator outcome"
							value={fixture}
							onChange={(e) => setFixture(e.target.value)}
						>
							<option value="SYNTHETIC-INSTALLED">
								Installed in simulator
							</option>
							<option value="SYNTHETIC-FAILED">
								Confirmed simulator failure
							</option>
							<option value="SYNTHETIC-UNKNOWN">
								Unknown — reconcile before retry
							</option>
						</SelectField>
					)}
					{selected.permittedActions.map((op) => (
						<Button key={op} disabled={busy} onClick={() => action(op)}>
							{op === "SIMULATE" &&
							["UNKNOWN", "RECONCILIATION_REQUIRED"].includes(
								selected.provisioning ?? "",
							)
								? "Reconcile simulation"
								: op}
						</Button>
					))}
					{secret && (
						<section aria-label="Private one-time synthetic credential">
							<p>
								Private one-time disclosure. Lost values require authorized
								reissue.
							</p>
							<output>{secret}</output>
							<Button onClick={() => setSecret("")}>Hide credential</Button>
						</section>
					)}
					<p>
						Unknown provisioning requires reconciliation. Reissue supersedes the
						old version. Access excludes cleaning time.
					</p>
					{selected.history && (
						<ul aria-label="Access audit timeline">
							{selected.history.map((h) => (
								<li key={h.id}>
									{h.at}: {h.action} — {h.outcome}
								</li>
							))}
						</ul>
					)}
				</Card>
			)}
		</div>
	);
}

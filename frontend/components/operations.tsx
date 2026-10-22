"use client";
import { useRef, useState, useEffect } from "react";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField, PageHeading } from "./primitives";
import { MaintenanceImpactPreview } from "./maintenance-impact";
export const OPERATIONS_LABEL = "SYNTHETIC OPERATIONAL TEST";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
type Entry = {
	entry_id: string;
	actor_id: string;
	kind: string;
	case_revision: number;
	created_at: string;
	data: Record<string, unknown>;
};
export type Turnover = {
	case_id: string;
	venue_id: string;
	room_id: string;
	state: string;
	revision: number;
	synthetic: true;
	checklist_version: string;
	checklist: string[];
	assignee_id: string | null;
	assignment: string;
	physicalOccupancy: "UNKNOWN";
	physicalReadiness: "UNVERIFIED";
	inventoryChanged: false;
	entries: Entry[];
	permittedActions: string[];
};
export function turnover(value: unknown, venue: string): Turnover {
	const c = value as Turnover;
	if (
		c?.synthetic !== true ||
		c.venue_id !== venue ||
		!uuid.test(c.case_id) ||
		!uuid.test(c.room_id) ||
		!Number.isSafeInteger(c.revision) ||
		c.revision < 0 ||
		c.physicalOccupancy !== "UNKNOWN" ||
		c.physicalReadiness !== "UNVERIFIED" ||
		c.inventoryChanged !== false ||
		!["OPEN", "ACKNOWLEDGED", "ASSERTED", "RESOLVED"].includes(c.state) ||
		!Array.isArray(c.checklist) ||
		!c.checklist.length ||
		!Array.isArray(c.entries) ||
		!Array.isArray(c.permittedActions)
	)
		throw Error("Invalid synthetic operations response");
	if (
		!c.checklist.every(
			(x) => typeof x === "string" && /^[A-Z0-9_-]{1,64}$/.test(x),
		) ||
		!c.entries.every(
			(e) =>
				uuid.test(e.entry_id) &&
				uuid.test(e.actor_id) &&
				Number.isSafeInteger(e.case_revision) &&
				e.case_revision >= 0 &&
				e.data &&
				typeof e.data === "object" &&
				!Array.isArray(e.data) &&
				Number.isFinite(Date.parse(e.created_at)),
		) ||
		!c.permittedActions.every((x) =>
			["read", "record", "assign", "review"].includes(x),
		)
	)
		throw Error("Invalid synthetic evidence response");
	return c;
}
export function operationsPath(venue: string, id?: string, action?: string) {
	if (!uuid.test(venue) || (id && !uuid.test(id))) throw Error("Invalid scope");
	const root = `/api/admin/venues/${venue}/operations/cases`;
	if (
		action &&
		![
			"assignments",
			"acknowledgements",
			"checklist",
			"completion-assertions",
			"exceptions",
			"exception-resolutions",
			"reviews",
		].includes(action)
	)
		throw Error("Unsupported action");
	return root + (id ? `/${id}` : "") + (action ? `/${action}` : "");
}
export function OperationsWorkspace() {
	const [venue, setVenue] = useState(""),
		[room, setRoom] = useState(""),
		[booking, setBooking] = useState(""),
		[assignee, setAssignee] = useState(""),
		[category, setCategory] = useState("CLEANING"),
		[reference, setReference] = useState(""),
		[cases, setCases] = useState<Turnover[]>([]),
		[current, setCurrent] = useState<Turnover | null>(null),
		[filter, setFilter] = useState("ALL"),
		[page, setPage] = useState(0),
		[busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Enter an authorized synthetic venue. Real operations are disabled.",
		);
	const generation = useRef(0),
		pending = useRef<{ signature: string; key: string } | null>(null),
		status = useRef<HTMLParagraphElement>(null);
	useEffect(
		() => () => {
			generation.current++;
		},
		[],
	);
	function scope(v: string) {
		generation.current++;
		pending.current = null;
		setVenue(v);
		setCases([]);
		setCurrent(null);
		setPage(0);
		setBusy(false);
		setMessage("Load authorized scope.");
	}
	async function request(
		action?: string,
		extra: Record<string, unknown> = {},
		selected?: string,
		nextPage = page,
	) {
		const g = generation.current;
		setBusy(true);
		try {
			let result: unknown;
			if (action) {
				const path = operationsPath(
					venue,
					action === "create" ? undefined : current?.case_id,
					action === "create" ? undefined : action,
				);
				const body = {
					expectedVersion: action === "create" ? 0 : current?.revision,
					...extra,
				};
				const signature = JSON.stringify([path, body]);
				if (pending.current?.signature !== signature)
					pending.current = { signature, key: crypto.randomUUID() };
				result = await roomRequest(path, {
					method: "POST",
					body: JSON.stringify({ ...body, commandKey: pending.current.key }),
				});
			} else
				result = await roomRequest(
					selected
						? operationsPath(venue, selected)
						: `${operationsPath(venue)}?page=${nextPage}`,
				);
			if (g !== generation.current) return;
			if (action || selected) {
				const c = turnover(result, venue);
				setCurrent(c);
				setCases((rows) => [c, ...rows.filter((x) => x.case_id !== c.case_id)]);
				pending.current = null;
			} else {
				const rows = (result as { items: unknown[] }).items;
				if (!Array.isArray(rows)) throw Error("Invalid queue response");
				setCases(rows.map((x) => turnover(x, venue)));
				setCurrent(null);
				setPage(nextPage);
			}
			setMessage("Synthetic evidence loaded. Physical readiness not verified.");
		} catch (e) {
			if (g !== generation.current) return;
			if (e instanceof RoomRequestError && [401, 403, 404].includes(e.status)) {
				setCases([]);
				setCurrent(null);
				pending.current = null;
				setMessage("Denied or session expired. Reload authorized scope.");
			} else if (e instanceof RoomRequestError && e.status === 409) {
				setCurrent(null);
				pending.current = null;
				setMessage(
					"Conflict or stale revision. Reload the queue and compare before retrying.",
				);
			} else
				setMessage(
					"Unavailable or invalid input. Reconcile current state before retrying; no real operation performed.",
				);
			queueMicrotask(() => status.current?.focus());
		} finally {
			if (g === generation.current) setBusy(false);
		}
	}
	const can = (p: string) =>
		current?.permittedActions.includes(p) && current.state !== "RESOLVED";
	const unresolved =
		current?.entries.filter(
			(e) =>
				e.kind === "EXCEPTION" &&
				!current.entries.some(
					(r) =>
						r.kind === "RESOLVE_EXCEPTION" && r.data.entryId === e.entry_id,
				),
		) ?? [];
	return (
		<div className="staff-workspace operations-workspace">
			<PageHeading eyebrow="Synthetic staff workspace" title="Room operations">
				Manage synthetic turnover cases, assignments and recorded attestations.
				Physical room readiness requires separate verification.
			</PageHeading>
			<p className="fixture-notice">{OPERATIONS_LABEL}</p>
			<p>PHYSICAL READINESS NOT VERIFIED</p>
			<p>
				Occupancy UNKNOWN. Case outcomes do not change inventory or authorize
				admission.
			</p>
			<p
				className="workspace-status"
				ref={status}
				tabIndex={-1}
				role="status"
				aria-atomic="true"
				aria-busy={busy || undefined}
			>
				{busy ? "Loading…" : message}
			</p>
			<MaintenanceImpactPreview />
			<Card title="Turnover queue">
				<Field
					id="operations-venue"
					label="Venue ID"
					hint="Enter a venue covered by your current server-authorized grant."
					value={venue}
					onChange={(e) => scope(e.target.value)}
				/>
				<Button
					busy={busy}
					disabled={!uuid.test(venue)}
					onClick={() => request()}
				>
					Load queue
				</Button>
				<SelectField
					id="operations-filter"
					label="Filter current queue page"
					value={filter}
					onChange={(e) => setFilter(e.target.value)}
				>
					<option value="ALL">All</option>
					<option value="UNASSIGNED">Unassigned</option>
					<option value="UNRESOLVED">Unresolved</option>
				</SelectField>
				{!cases.length && <p>No cases loaded.</p>}
				<ul>
					{cases
						.filter(
							(c) =>
								filter === "ALL" ||
								(filter === "UNASSIGNED"
									? c.assignment === "UNASSIGNED"
									: c.state !== "RESOLVED"),
						)
						.map((c) => (
							<li key={c.case_id}>
								<Button
									disabled={busy}
									onClick={() => request(undefined, {}, c.case_id)}
								>
									{c.room_id} — {c.state} — {c.assignment}
								</Button>
							</li>
						))}
				</ul>
				<Button
					disabled={busy || page === 0}
					onClick={() => request(undefined, {}, undefined, page - 1)}
				>
					Previous
				</Button>
				<Button
					disabled={busy || cases.length < 25}
					onClick={() => request(undefined, {}, undefined, page + 1)}
				>
					Next
				</Button>
			</Card>
			<Card title="Create synthetic turnover case">
				<Field
					id="operations-room"
					label="Room ID"
					value={room}
					onChange={(e) => setRoom(e.target.value)}
				/>
				<Field
					id="operations-booking"
					label="Optional synthetic booking ID"
					value={booking}
					onChange={(e) => setBooking(e.target.value)}
				/>
				<Button
					disabled={
						busy ||
						!uuid.test(venue) ||
						!uuid.test(room) ||
						(!!booking && !uuid.test(booking))
					}
					onClick={() =>
						request("create", {
							roomId: room,
							...(booking ? { bookingId: booking } : {}),
						})
					}
				>
					Create synthetic case
				</Button>
				<p>
					Backend permission is required; no staff appointment or physical task
					is created.
				</p>
			</Card>
			{current && (
				<Card title="Synthetic case detail">
					<p>
						Case {current.case_id}; room {current.room_id}; revision{" "}
						{current.revision}; {current.state}; {current.assignment}
					</p>
					<p>
						Checklist {current.checklist_version}; assigned actor{" "}
						{current.assignee_id ?? "UNASSIGNED"}
					</p>
					<Field
						id="operations-assignee"
						label="Eligible synthetic staff account ID (empty to unassign)"
						value={assignee}
						onChange={(e) => setAssignee(e.target.value)}
					/>
					<Button
						disabled={
							busy ||
							!can("assign") ||
							current.state !== "OPEN" ||
							(!!assignee && !uuid.test(assignee))
						}
						onClick={() =>
							request("assignments", { assigneeId: assignee || null })
						}
					>
						Assign staff
					</Button>
					<Button
						disabled={busy || !can("record") || current.state !== "OPEN"}
						onClick={() => request("acknowledgements")}
					>
						Acknowledge assigned work
					</Button>
					<ul>
						{current.checklist.map((item) => (
							<li key={item}>
								{item}{" "}
								<Button
									disabled={
										busy || !can("record") || current.state !== "ACKNOWLEDGED"
									}
									onClick={() => request("checklist", { item, asserted: true })}
								>
									Record {item} assertion
								</Button>
							</li>
						))}
					</ul>
					<Button
						disabled={
							busy || !can("record") || current.state !== "ACKNOWLEDGED"
						}
						onClick={() => request("completion-assertions")}
					>
						Assert synthetic cleaning completion
					</Button>
					<SelectField
						id="operations-category"
						label="Exception category"
						value={category}
						onChange={(e) => setCategory(e.target.value)}
					>
						{["CLEANING", "MAINTENANCE", "UNKNOWN_DEVICE"].map((x) => (
							<option key={x}>{x}</option>
						))}
					</SelectField>
					<Field
						id="operations-reference"
						label="Optional IoT command reference"
						value={reference}
						onChange={(e) => setReference(e.target.value)}
					/>
					<Button
						disabled={
							busy || !can("record") || (!!reference && !uuid.test(reference))
						}
						onClick={() =>
							request("exceptions", {
								category,
								...(reference ? { source: "IOT", referenceId: reference } : {}),
							})
						}
					>
						Record exception
					</Button>
					<h3>Unresolved exceptions</h3>
					{!unresolved.length && <p>No unresolved exceptions recorded.</p>}
					{unresolved.map((e) => (
						<p key={e.entry_id}>
							{String(e.data.category)} — {e.entry_id}{" "}
							<Button
								disabled={busy || !can("review")}
								onClick={() =>
									request("exception-resolutions", { entryId: e.entry_id })
								}
							>
								Record synthetic exception resolution
							</Button>
						</p>
					))}
					<Button
						disabled={
							busy ||
							!can("review") ||
							current.state !== "ASSERTED" ||
							unresolved.length > 0
						}
						onClick={() => request("reviews")}
					>
						Review and resolve synthetic case
					</Button>
					<h3>Evidence and audit timeline</h3>
					<ol>
						{current.entries.map((e) => (
							<li key={e.entry_id}>
								{e.kind} — {e.actor_id} — revision {e.case_revision} —{" "}
								{e.created_at} — {JSON.stringify(e.data)}
							</li>
						))}
					</ol>
				</Card>
			)}
		</div>
	);
}

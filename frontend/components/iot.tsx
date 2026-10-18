"use client";
import { useState, useRef, useEffect } from "react";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";
export const IOT_LABEL = "SYNTHETIC IOT TEST — NO REAL EQUIPMENT CONTROL";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
type Command = {
	id: string;
	booking_id: string;
	booking_revision: number;
	state: string;
	revision: number;
	correlation_id: string;
	desired: string;
	expires_at: string;
	starts_at: string;
	ends_at: string;
	timezone: string;
};
export type Device = {
	device_id: string;
	venue_id: string;
	room_id: string;
	revision: number;
	online: boolean;
	enabled: boolean;
	desired: string | null;
	observed: string;
	observationFresh: boolean;
	observed_at: string | null;
	observation_sequence: number;
	commands: Command[];
	audit: { id: string; action: string; outcome: string; created_at: string }[];
	permittedActions: string[];
	synthetic: true;
	equipmentControlled: false;
	physicalOccupancy: "UNKNOWN";
	physicalReadiness: "UNVERIFIED";
};
export function iotDevice(x: unknown, venue: string): Device {
	const d = x as Device;
	if (
		d?.synthetic !== true ||
		d.equipmentControlled !== false ||
		d.physicalOccupancy !== "UNKNOWN" ||
		d.physicalReadiness !== "UNVERIFIED" ||
		d.venue_id !== venue ||
		!uuid.test(d.device_id) ||
		!uuid.test(d.room_id) ||
		!Number.isSafeInteger(d.revision) ||
		d.revision < 1 ||
		!Array.isArray(d.commands) ||
		!Array.isArray(d.audit) ||
		!Array.isArray(d.permittedActions) ||
		!["ON", "OFF", "UNKNOWN"].includes(d.observed) ||
		typeof d.online !== "boolean" ||
		typeof d.enabled !== "boolean"
	)
		throw Error("Invalid synthetic device response");
	for (const c of d.commands)
		if (
			!uuid.test(c.id) ||
			!uuid.test(c.booking_id) ||
			!uuid.test(c.correlation_id) ||
			!Number.isSafeInteger(c.revision) ||
			!Number.isSafeInteger(c.booking_revision) ||
			!["PENDING", "ACKNOWLEDGED", "FAILED", "EXPIRED", "UNKNOWN"].includes(
				c.state,
			)
		)
			throw Error("Invalid command response");
	return d;
}
export function iotPath(
	venue: string,
	device?: string,
	command?: string,
	action?: string,
) {
	if (
		!uuid.test(venue) ||
		(device && !uuid.test(device)) ||
		(command && !uuid.test(command))
	)
		throw Error("Invalid scope");
	const root = `/api/admin/venues/${venue}/iot/devices`;
	if (!device) return root;
	if (!action) return `${root}/${device}`;
	if (action === "command" && !command) return `${root}/${device}/commands`;
	if (command && ["simulations", "reconciliations"].includes(action))
		return `${root}/${device}/commands/${command}/${action}`;
	throw Error("Unsupported simulator action");
}
export function IotWorkspace() {
	const [venue, setVenue] = useState(""),
		[devices, setDevices] = useState<Device[]>([]),
		[device, setDevice] = useState<Device | null>(null),
		[booking, setBooking] = useState(""),
		[revision, setRevision] = useState("1"),
		[desired, setDesired] = useState("ON"),
		[outcome, setOutcome] = useState("ACKNOWLEDGED"),
		[observation, setObservation] = useState("NONE"),
		[busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Not configured: enter an authorized synthetic venue.",
		),
		[page, setPage] = useState(0),
		[filter, setFilter] = useState("ALL");
	const status = useRef<HTMLParagraphElement>(null);
	const generation = useRef(0),
		pending = useRef<{ signature: string; key: string } | null>(null);
	useEffect(
		() => () => {
			generation.current++;
		},
		[],
	);
	function clear(value: string) {
		generation.current++;
		setVenue(value);
		setDevices([]);
		setDevice(null);
		setMessage("Not configured: load the selected authorized venue.");
		setBusy(false);
		setPage(0);
		pending.current = null;
	}
	function fail(e: unknown) {
		queueMicrotask(() => status.current?.focus());
		if (e instanceof RoomRequestError) {
			if ([401, 403, 404].includes(e.status)) {
				setDevice(null);
				setDevices([]);
				setMessage("Denied or session expired. Reload authorized scope.");
			} else
				setMessage(
					e.status === 503
						? "Unconfigured or unavailable. Retry after configuration is verified."
						: e.status === 409
							? "Conflict or stale context. Refresh and review before retrying."
							: e.status === 429
								? "Rate limit reached. Wait before retrying."
								: "Request failed. Refresh command history before retrying.",
				);
		} else setMessage("Invalid response or scope. Refresh before retrying.");
	}
	async function load(next = 0) {
		const token = ++generation.current;
		setBusy(true);
		setDevice(null);
		try {
			const data = await roomRequest<{ items: unknown[] }>(
				`${iotPath(venue)}?page=${next}&size=25`,
			);
			if (token !== generation.current) return;
			const rows = data.items.map((x) => iotDevice(x, venue));
			setDevices(rows);
			setPage(next);
			setMessage(
				rows.length
					? "Synthetic inventory loaded."
					: next
						? "No more records on this page."
						: "No synthetic devices registered.",
			);
		} catch (e) {
			if (token === generation.current) {
				setDevices([]);
				fail(e);
			}
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}
	async function open(id: string) {
		const token = ++generation.current;
		setBusy(true);
		setDevice(null);
		try {
			const d = iotDevice(await roomRequest(iotPath(venue, id)), venue);
			if (token === generation.current) {
				setDevice(d);
				setMessage(
					"Synthetic status refreshed; physical readiness remains unverified.",
				);
			}
		} catch (e) {
			if (token === generation.current) fail(e);
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}
	async function submit(c?: Command) {
		if (!device) return;
		const token = ++generation.current;
		setBusy(true);
		const action = c
			? c.state === "UNKNOWN"
				? "reconciliations"
				: "simulations"
			: "command";
		try {
			const fields: Record<string, unknown> = c
				? {
						bookingId: c.booking_id,
						bookingRevision: c.booking_revision,
						expectedRevision: c.revision,
						correlationId: c.correlation_id,
						outcome,
					}
				: {
						bookingId: booking,
						bookingRevision: Number(revision),
						expectedRevision: device.revision,
						desired,
					};
			if (
				!c &&
				(!uuid.test(booking) ||
					!Number.isSafeInteger(Number(revision)) ||
					Number(revision) < 1)
			)
				throw Error("Invalid booking context");
			if (c && observation !== "NONE")
				Object.assign(fields, {
					observed: observation,
					observationSequence: device.observation_sequence + 1,
					observedAt: new Date().toISOString(),
				});
			const path = iotPath(venue, device.device_id, c?.id, action),
				signature = JSON.stringify([path, fields]);
			if (pending.current?.signature !== signature)
				pending.current = { signature, key: crypto.randomUUID() };
			const d = iotDevice(
				await roomRequest(path, {
					method: "POST",
					body: JSON.stringify({ ...fields, commandKey: pending.current.key }),
				}),
				venue,
			);
			if (token !== generation.current) return;
			pending.current = null;
			setDevice(d);
			setMessage(
				"Simulator record saved. This is not physical equipment evidence.",
			);
			queueMicrotask(() => status.current?.focus());
		} catch (e) {
			if (token === generation.current) fail(e);
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}
	const shown = devices.filter((d) =>
		filter === "ALL" || filter === "UNKNOWN"
			? filter === "ALL" ||
				d.observed === "UNKNOWN" ||
				d.commands.some((c) => c.state === "UNKNOWN")
			: d.commands.some((c) => c.state === "FAILED"),
	);
	return (
		<section>
			<h1>Synthetic lighting simulator</h1>
			<p role="note">{IOT_LABEL}</p>
			<p>
				Physical occupancy UNKNOWN. Room readiness UNVERIFIED. Emergency egress
				is independent of this interface.
			</p>
			<Field
				id="iot-authorized-venue-uuid"
				label="Authorized venue UUID"
				value={venue}
				onChange={(e) => clear(e.target.value)}
			/>
			<Button disabled={busy || !uuid.test(venue)} onClick={() => load(0)}>
				Load devices
			</Button>
			<p ref={status} tabIndex={-1} role="status" aria-live="polite">
				{busy ? "Loading synthetic records…" : message}
			</p>
			<SelectField
				id="iot-device-exception-filter"
				label="Device exception filter"
				value={filter}
				onChange={(e) => setFilter(e.target.value)}
			>
				<option value="ALL">All devices</option>
				<option value="UNKNOWN">Unknown observations or commands</option>
				<option value="FAILED">Failed commands</option>
			</SelectField>
			{!busy && !shown.length && devices.length > 0 && (
				<p>No matching devices.</p>
			)}
			<div>
				{shown.map((d) => (
					<Card title="Synthetic lighting device" key={d.device_id}>
						<p>Device {d.device_id}</p>
						<p>Room {d.room_id}</p>
						<p>
							{d.enabled ? "Enabled" : "Disabled"} ·{" "}
							{d.online ? "Simulated online" : "Simulated offline"} · Observed{" "}
							{d.observed}
						</p>
						<Button disabled={busy} onClick={() => open(d.device_id)}>
							View device
						</Button>
					</Card>
				))}
			</div>
			{devices.length > 0 && (
				<nav aria-label="Device pages">
					<Button disabled={busy || page === 0} onClick={() => load(page - 1)}>
						Previous page
					</Button>
					<span> Page {page + 1} </span>
					<Button
						disabled={busy || devices.length < 25}
						onClick={() => load(page + 1)}
					>
						Next page
					</Button>
				</nav>
			)}
			{device && (
				<Card title="Device details">
					<p>
						Desired {device.desired ?? "Not requested"} · Observed{" "}
						{device.observed} ·{" "}
						{device.observationFresh
							? "Fresh simulated observation"
							: "Observation missing or stale"}
					</p>
					<p>
						Last observation {device.observed_at ?? "None"}; sequence{" "}
						{device.observation_sequence}
					</p>
					<Button disabled={busy} onClick={() => open(device.device_id)}>
						Refresh status
					</Button>
					{device.permittedActions.includes("command") && (
						<fieldset disabled={busy || !device.enabled}>
							<legend>Request simulated lighting state</legend>
							<Field
								id="iot-confirmed-synthetic-booking-uuid"
								label="Confirmed synthetic booking UUID"
								value={booking}
								onChange={(e) => setBooking(e.target.value)}
							/>
							<Field
								id="iot-current-booking-revision"
								label="Current booking revision"
								type="number"
								min="1"
								value={revision}
								onChange={(e) => setRevision(e.target.value)}
							/>
							<SelectField
								id="iot-desired-lighting-state"
								label="Desired lighting state"
								value={desired}
								onChange={(e) => setDesired(e.target.value)}
							>
								<option>ON</option>
								<option>OFF</option>
							</SelectField>
							<Button onClick={() => submit()}>Request simulated state</Button>
						</fieldset>
					)}
					<SelectField
						id="iot-controlled-simulator-outcome"
						label="Controlled simulator outcome"
						value={outcome}
						onChange={(e) => setOutcome(e.target.value)}
					>
						<option>ACKNOWLEDGED</option>
						<option>FAILED</option>
						<option>UNKNOWN</option>
					</SelectField>
					<SelectField
						id="iot-independent-simulated-observation"
						label="Independent simulated observation"
						value={observation}
						onChange={(e) => setObservation(e.target.value)}
					>
						<option value="NONE">No observation</option>
						<option>ON</option>
						<option>OFF</option>
					</SelectField>
					<h3>Command history</h3>
					{!device.commands.length && <p>No command history.</p>}
					{device.commands.map((c) => (
						<article key={c.id}>
							<h4>{c.state}</h4>
							<p>
								Command {c.id} · desired {c.desired}; acknowledgement{" "}
								{c.state === "ACKNOWLEDGED" ? "Recorded" : "Not established"}
							</p>
							<p>
								Booking {c.booking_id} revision {c.booking_revision}; purchased{" "}
								{c.starts_at} – {c.ends_at} ({c.timezone}); expires{" "}
								{c.expires_at}
							</p>
							{c.state === "PENDING" &&
								device.permittedActions.includes("simulate") && (
									<Button
										disabled={busy || !device.online || !device.enabled}
										onClick={() => submit(c)}
									>
										Record simulator outcome
									</Button>
								)}
							{c.state === "UNKNOWN" &&
								device.permittedActions.includes("reconcile") && (
									<Button
										disabled={busy || !device.online || !device.enabled}
										onClick={() => submit(c)}
									>
										Reconcile unknown outcome
									</Button>
								)}
						</article>
					))}
					<h3>Audit history</h3>
					{device.audit.map((a) => (
						<p key={a.id}>
							{a.action} · {a.outcome} · {a.created_at}
						</p>
					))}
				</Card>
			)}
		</section>
	);
}

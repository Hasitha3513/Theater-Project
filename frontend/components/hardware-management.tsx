"use client";
import { useEffect, useRef, useState } from "react";
import {
	HardwareCommand,
	assignmentPath,
	hardwareCategories,
	hardwareContext,
	hardwareId,
	lockCapabilities,
	parseAssignment,
	parseDevice,
	parseHardwarePage,
	parseRegistration,
	registrationValues,
	registryPath,
	type Assignment,
	type HardwarePage,
	type RegistrationDraft,
	type RegistryDevice,
} from "../lib/hardware";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, PageHeading, SelectField } from "./primitives";

const wrap = { overflowWrap: "anywhere" as const };
const grid = {
	display: "grid",
	gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 16rem), 1fr))",
	gap: "1rem",
};
const emptyDraft = (): RegistrationDraft => ({
	manufacturer: "",
	model: "",
	sku: "",
	hardware: "",
	firmware: "",
	reference: "",
	referenceVersion: "",
	capabilities: [],
});
export function HardwareRegistryTable({
	items,
	inspect,
	disabled = false,
}: {
	items: RegistryDevice[];
	inspect: (id: string) => void;
	disabled?: boolean;
}) {
	return (
		<div style={{ overflowX: "auto", maxWidth: "100%" }}>
			<table>
				<caption>Protected Smart Door Lock registry</caption>
				<thead>
					<tr>
						<th scope="col">Device / venue</th>
						<th scope="col">Declared metadata</th>
						<th scope="col">Qualification / revision</th>
						<th scope="col">Recorded</th>
						<th scope="col">Details</th>
					</tr>
				</thead>
				<tbody>
					{items.map((d) => (
						<tr key={d.deviceId}>
							<td style={wrap}>
								{d.deviceId}
								<br />
								Smart Door Locks
								<br />
								Venue: {d.venueId}
							</td>
							<td style={wrap}>
								{d.manufacturer} / {d.model}
								<br />
								Declared capabilities:{" "}
								{d.declaredCapabilities.join(", ") || "None declared"}
							</td>
							<td>
								UNQUALIFIED
								<br />
								Revision {d.revision}
								<br />
								Customer access disabled
							</td>
							<td style={wrap}>
								Registered: {d.registeredAt}
								<br />
								Recorded: {d.recordedAt}
							</td>
							<td>
								<Button disabled={disabled} onClick={() => inspect(d.deviceId)}>
									Inspect device
								</Button>
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}
export function HardwareManagement() {
	const [category, setCategory] = useState<string>(hardwareCategories[0]);
	const [venue, setVenue] = useState(""),
		[room, setRoom] = useState("");
	const [devices, setDevices] = useState<HardwarePage<RegistryDevice>>(),
		[detail, setDetail] = useState<RegistryDevice>();
	const [history, setHistory] = useState<HardwarePage<RegistryDevice>>(),
		[historical, setHistorical] = useState<RegistryDevice>();
	const [assignments, setAssignments] = useState<HardwarePage<Assignment>>(),
		[assignment, setAssignment] = useState<Assignment>();
	const [draft, setDraft] = useState<RegistrationDraft>(emptyDraft),
		[registered, setRegistered] = useState<string>();
	const [busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Select an exact venue. Only synthetic Smart Door Lock metadata and qualification assignments are supported.",
		);
	const turn = useRef(0),
		locked = useRef(false),
		mounted = useRef(true);
	const registration = useRef(new HardwareCommand()),
		assignCommand = useRef(new HardwareCommand());
	const supported = category === "Smart Door Locks";
	function clearProtected() {
		setDevices(undefined);
		setDetail(undefined);
		setHistory(undefined);
		setHistorical(undefined);
		setAssignments(undefined);
		setAssignment(undefined);
		setRegistered(undefined);
	}
	function clearCommands() {
		registration.current.clear();
		assignCommand.current.clear();
	}
	useEffect(() => {
		mounted.current = true;
		const clear = () => {
			turn.current++;
			setDevices(undefined);
			setDetail(undefined);
			setHistory(undefined);
			setHistorical(undefined);
			setAssignments(undefined);
			setAssignment(undefined);
			setRegistered(undefined);
			registration.current.clear();
			assignCommand.current.clear();
			setDraft(emptyDraft());
			setMessage(
				"Session or page context changed. Reload protected hardware context.",
			);
		};
		const events = ["cinenest-session-change", "pagehide", "popstate"];
		for (const e of events) window.addEventListener(e, clear);
		document.addEventListener("visibilitychange", clear);
		return () => {
			mounted.current = false;
			turn.current++;
			for (const e of events) window.removeEventListener(e, clear);
			document.removeEventListener("visibilitychange", clear);
		};
	}, []);
	async function run(action: (current: () => boolean) => Promise<void>) {
		if (locked.current) return;
		locked.current = true;
		setBusy(true);
		const generation = turn.current;
		const current = () => mounted.current && generation === turn.current;
		try {
			await action(current);
		} catch (error) {
			if (current()) {
				if (
					error instanceof RoomRequestError &&
					[401, 403].includes(error.status)
				) {
					clearProtected();
					clearCommands();
					setDraft(emptyDraft());
				}
				if (error instanceof RoomRequestError && error.status === 409) {
					setAssignments(undefined);
					setAssignment(undefined);
				}
				setMessage(
					error instanceof RoomRequestError
						? `Hardware request denied or unavailable (${error.status}). Reload authoritative context; retry unchanged commands only.`
						: error instanceof Error
							? error.message
							: "Hardware request unavailable.",
				);
			}
		} finally {
			locked.current = false;
			if (mounted.current) setBusy(false);
		}
	}
	function changeVenue(value: string) {
		turn.current++;
		clearProtected();
		clearCommands();
		setDraft(emptyDraft());
		setVenue(value);
		setMessage("Venue changed. Reload protected registry and room context.");
	}
	function changeRoom(value: string) {
		turn.current++;
		setRoom(value);
		setAssignments(undefined);
		setAssignment(undefined);
		assignCommand.current.clear();
		setMessage(
			"Room changed. Load exact-room assignments before creating a binding.",
		);
	}
	function changeCategory(value: string) {
		turn.current++;
		clearProtected();
		clearCommands();
		setDraft(emptyDraft());
		setCategory(value);
		setMessage(
			value === "Smart Door Locks"
				? "Load the protected synthetic registry."
				: "UNSUPPORTED: category-specific onboarding and control are not implemented.",
		);
	}
	function loadRegistry(page = 0) {
		void run(async (current) => {
			const v = hardwareId(venue),
				path = registryPath(v);
			setDetail(undefined);
			setHistory(undefined);
			setHistorical(undefined);
			assignCommand.current.clear();
			const parsed = parseHardwarePage(
				await roomRequest(`${path}?page=${page}`),
				hardwareContext(path, "GET"),
			) as HardwarePage<RegistryDevice>;
			if (parsed.page !== page) throw Error("Unexpected registry page.");
			if (current()) {
				setDevices(parsed);
				setMessage(
					"Protected registry loaded. Registration does not establish device ownership or physical readiness.",
				);
			}
		});
	}
	function inspect(id: string) {
		void run(async (current) => {
			const parsed = parseDevice(
				await roomRequest(registryPath(venue, id)),
				venue,
				id,
			);
			if (current()) {
				setDetail(parsed);
				setHistory(undefined);
				setHistorical(undefined);
				setAssignment(undefined);
				assignCommand.current.clear();
				setMessage(
					"Device detail loaded. Room assignments must be read separately from the authoritative assignment API.",
				);
			}
		});
	}
	function loadHistory(page = 0) {
		if (!detail) return;
		void run(async (current) => {
			const path = registryPath(venue, detail.deviceId, "history");
			const parsed = parseHardwarePage(
				await roomRequest(`${path}?page=${page}`),
				hardwareContext(path, "GET"),
			) as HardwarePage<RegistryDevice>;
			if (parsed.page !== page) throw Error("Unexpected revision page.");
			if (current()) {
				setHistory(parsed);
				setHistorical(undefined);
				setMessage("Immutable revision history loaded.");
			}
		});
	}
	function inspectRevision(revision: number) {
		if (!detail) return;
		void run(async (current) => {
			const parsed = parseDevice(
				await roomRequest(registryPath(venue, detail.deviceId, revision)),
				venue,
				detail.deviceId,
				revision,
			);
			if (current()) {
				setHistorical(parsed);
				setMessage(
					"Historical metadata loaded; assignment still uses the separately loaded current device revision.",
				);
			}
		});
	}
	function loadAssignments(page = 0) {
		void run(async (current) => {
			const path = assignmentPath(venue, room);
			const parsed = parseHardwarePage(
				await roomRequest(`${path}?page=${page}`),
				hardwareContext(path, "GET"),
			) as HardwarePage<Assignment>;
			if (parsed.page !== page) throw Error("Unexpected assignment page.");
			if (current()) {
				setAssignments(parsed);
				setAssignment(undefined);
				setMessage(
					"Authoritative exact-room assignment context loaded. This is not a venue-wide assignment search.",
				);
			}
		});
	}
	function inspectAssignment(id: string) {
		void run(async (current) => {
			const parsed = parseAssignment(
				await roomRequest(assignmentPath(venue, room, id)),
				venue,
				room,
				id,
			);
			if (current()) {
				setAssignment(parsed);
				setMessage("Protected room assignment detail loaded.");
			}
		});
	}
	function register() {
		void run(async (current) => {
			const path = registryPath(venue),
				values = registrationValues(draft);
			const raw = await registration.current.send(path, values, (p, body) =>
				roomRequest(p, { method: "POST", body }),
			);
			const receipt = parseRegistration(raw, venue);
			if (current()) {
				setRegistered(receipt.deviceId);
				setMessage(
					receipt.replayed
						? "Original synthetic registration replayed. No activation performed."
						: "Synthetic metadata registered. No activation performed. Load registry with independent read permission to inspect it.",
				);
			}
		});
	}
	function createAssignment() {
		if (!detail || !assignments?.roomConfiguration) return;
		void run(async (current) => {
			if (detail.venueId !== hardwareId(venue))
				throw Error("Reload exact-venue device context.");
			const raw = await assignCommand.current.send(
				assignmentPath(venue, room),
				{
					deviceId: detail.deviceId,
					expectedAssignmentRevision: 0,
					expectedDeviceMetadataRevision: detail.revision,
					expectedRoomConfiguration: assignments.roomConfiguration,
				},
				(p, body) => roomRequest(p, { method: "POST", body }),
			);
			const saved = parseAssignment(raw, venue, room);
			if (saved.deviceId !== detail.deviceId)
				throw Error("Unexpected assigned device.");
			if (current()) {
				setAssignment(saved);
				setAssignments(undefined);
				setMessage(
					saved.replayed
						? "Original qualification assignment replayed. Customer access remains disabled."
						: "Qualification assignment saved. Customer access remains disabled; reload room context.",
				);
			}
		});
	}
	const selectedBinding = assignments?.items.find(
		(a) => a.deviceId === detail?.deviceId,
	);
	return (
		<div style={{ minWidth: 0, maxWidth: "100%" }}>
			<PageHeading eyebrow="Administration" title="Hardware Management">
				Hardware-independent preparation. Synthetic registration and
				qualification assignment only; no real equipment control.
			</PageHeading>
			<p role="status" aria-live="polite" style={wrap}>
				{message}
			</p>
			<Card title="Device category and venue">
				<div style={grid}>
					<SelectField
						id="hardware-category"
						label="Device category"
						value={category}
						disabled={busy}
						onChange={(e) => changeCategory(e.target.value)}
					>
						{hardwareCategories.map((c) => (
							<option key={c}>{c}</option>
						))}
					</SelectField>
					<Field
						id="hardware-venue"
						label="Venue reference"
						value={venue}
						disabled={busy}
						autoComplete="off"
						onChange={(e) => changeVenue(e.target.value)}
					/>
				</div>
				<p>
					Read, register and assignment permissions are independent. The backend
					authorizes every request.
				</p>
				{!supported && (
					<p>
						UNSUPPORTED — onboarding, compatibility and control are unavailable
						for this category.
					</p>
				)}
				{category === "Lighting Controllers" && (
					<a href="/admin/iot">Open existing Lighting Simulator</a>
				)}
			</Card>
			<Card title="Device Registry">
				<Button
					busy={busy}
					disabled={!supported}
					onClick={() => loadRegistry()}
				>
					Load device registry
				</Button>
				{!devices ? (
					<p>Protected registry not loaded.</p>
				) : devices.items.length === 0 ? (
					<p>No synthetic devices returned for this venue.</p>
				) : (
					<HardwareRegistryTable
						items={devices.items}
						inspect={inspect}
						disabled={busy}
					/>
				)}
				{devices && (
					<>
						<p>
							Page {devices.page + 1}; server observation: {devices.observedAt}
						</p>
						<Button
							disabled={busy || devices.page === 0}
							variant="secondary"
							onClick={() => loadRegistry(devices.page - 1)}
						>
							Previous devices
						</Button>
						<Button
							disabled={busy || !devices.hasNext}
							variant="secondary"
							onClick={() => loadRegistry(devices.page + 1)}
						>
							Next devices
						</Button>
					</>
				)}
			</Card>
			<Card title="Synthetic Smart Lock Registration">
				<p>
					Only approved synthetic TUYA qualification references are accepted.
					Declared manufacturer/model values do not verify hardware. No provider
					passwords, API keys or access credentials are collected.
				</p>
				<form
					onSubmit={(e) => {
						e.preventDefault();
						register();
					}}
				>
					<fieldset disabled={busy || !supported} style={{ minWidth: 0 }}>
						<legend>Synthetic metadata</legend>
						<div style={grid}>
							<Field
								id="hardware-provider"
								label="Synthetic provider"
								value="TUYA"
								readOnly
							/>
							{(
								[
									["manufacturer", "Declared manufacturer"],
									["model", "Declared model"],
									["sku", "SKU (optional)"],
									["hardware", "Hardware revision (optional)"],
									["firmware", "Firmware revision (optional)"],
									["reference", "Approved synthetic reference ID"],
									["referenceVersion", "Approved synthetic reference version"],
								] as const
							).map(([key, label]) => (
								<Field
									key={key}
									id={`hardware-${key}`}
									label={label}
									value={draft[key]}
									required={[
										"manufacturer",
										"model",
										"reference",
										"referenceVersion",
									].includes(key)}
									maxLength={128}
									autoComplete="off"
									onChange={(e) =>
										setDraft((d) => ({ ...d, [key]: e.target.value }))
									}
								/>
							))}
						</div>
						<fieldset style={{ minWidth: 0 }}>
							<legend>
								Declared capabilities — support remains unverified
							</legend>
							{lockCapabilities.map((cap) => (
								<Field
									key={cap}
									id={`hardware-cap-${cap}`}
									type="checkbox"
									label={`Declare ${cap}`}
									checked={draft.capabilities.includes(cap)}
									onChange={(e) =>
										setDraft((d) => ({
											...d,
											capabilities: e.target.checked
												? [...d.capabilities, cap]
												: d.capabilities.filter((c) => c !== cap),
										}))
									}
								/>
							))}
						</fieldset>
						<Button type="submit" busy={busy}>
							Register synthetic device
						</Button>
						<Button
							variant="secondary"
							disabled={busy}
							onClick={() => {
								registration.current.clear();
								setRegistered(undefined);
								setMessage(
									"New registration command started. Ensure any previous outcome has been checked before submitting.",
								);
							}}
						>
							Start new registration command
						</Button>
					</fieldset>
				</form>
				{registered && (
					<p style={wrap}>
						Registered device reference: <output>{registered}</output>
					</p>
				)}
			</Card>
			{detail && (
				<Card title="Device details">
					<dl style={wrap}>
						<dt>Device</dt>
						<dd>{detail.deviceId}</dd>
						<dt>Manufacturer / model</dt>
						<dd>
							{detail.manufacturer} / {detail.model}
						</dd>
						<dt>Current loaded revision</dt>
						<dd>{detail.revision}</dd>
						<dt>Firmware</dt>
						<dd>{detail.firmwareRevision ?? "UNKNOWN"}</dd>
						<dt>Declaration source</dt>
						<dd>{detail.declarationSource}</dd>
						<dt>Declared capabilities</dt>
						<dd>
							{`${detail.declaredCapabilities.join(", ") || "None declared"} — support not evaluated`}
						</dd>
						<dt>Qualification</dt>
						<dd>UNQUALIFIED; ownership UNVERIFIED; health UNKNOWN</dd>
						<dt>Selected-room binding</dt>
						<dd>
							{selectedBinding
								? selectedBinding.assignmentId
								: "Not established by the loaded selected-room results. Load room assignments; registry metadata cannot establish whether this device is assigned elsewhere."}
						</dd>
					</dl>
					<Button disabled={busy} onClick={() => loadHistory()}>
						Load revision history
					</Button>
					{history && (
						<>
							<ul>
								{history.items.map((h) => (
									<li key={h.revision}>
										<Button
											variant="secondary"
											disabled={busy}
											onClick={() => inspectRevision(h.revision)}
										>
											View revision {h.revision}
										</Button>{" "}
										— {h.recordedAt}
									</li>
								))}
							</ul>
							<Button
								disabled={busy || history.page === 0}
								variant="secondary"
								onClick={() => loadHistory(history.page - 1)}
							>
								Previous revisions
							</Button>
							<Button
								disabled={busy || !history.hasNext}
								variant="secondary"
								onClick={() => loadHistory(history.page + 1)}
							>
								Next revisions
							</Button>
						</>
					)}
					{historical && (
						<p style={wrap}>
							Historical revision {historical.revision}:{" "}
							{historical.manufacturer} / {historical.model}; firmware{" "}
							{historical.firmwareRevision ?? "UNKNOWN"}; recorded{" "}
							{historical.recordedAt}. UNQUALIFIED.
						</p>
					)}
				</Card>
			)}
			<Card title="Room Assignment">
				<p>
					Initial immutable qualification binding only. No reassignment,
					deactivation or room-availability change.
				</p>
				<Field
					id="hardware-room"
					label="Room reference"
					value={room}
					disabled={busy || !supported}
					autoComplete="off"
					onChange={(e) => changeRoom(e.target.value)}
				/>
				<Button disabled={busy || !supported} onClick={() => loadAssignments()}>
					Load room assignments
				</Button>
				{assignments && (
					<>
						<p>
							Room context loaded; page {assignments.page + 1}.{" "}
							{assignments.items.length === 0
								? "No assignments returned for this room on this page."
								: "Existing qualification assignments:"}
						</p>
						<ul>
							{assignments.items.map((a) => (
								<li key={a.assignmentId} style={wrap}>
									{a.deviceId} — UNQUALIFIED{" "}
									<Button
										variant="secondary"
										disabled={busy}
										onClick={() => inspectAssignment(a.assignmentId)}
									>
										Inspect assignment
									</Button>
								</li>
							))}
						</ul>
						<Button
							disabled={busy || assignments.page === 0}
							variant="secondary"
							onClick={() => loadAssignments(assignments.page - 1)}
						>
							Previous assignments
						</Button>
						<Button
							disabled={busy || !assignments.hasNext}
							variant="secondary"
							onClick={() => loadAssignments(assignments.page + 1)}
						>
							Next assignments
						</Button>
					</>
				)}
				<p style={wrap}>
					Selected device: {detail?.deviceId ?? "Inspect a device first"}.
					Expected metadata revision: {detail?.revision ?? "not loaded"}.
				</p>
				<Button
					disabled={busy || !supported || !detail || !assignments}
					onClick={createAssignment}
				>
					Create qualification assignment
				</Button>
				<Button
					disabled={busy || !supported}
					variant="secondary"
					onClick={() => {
						assignCommand.current.clear();
						setAssignments(undefined);
						setAssignment(undefined);
						setMessage(
							"New assignment command started. Reload authoritative room context first.",
						);
					}}
				>
					Start new assignment command
				</Button>
				{assignment && (
					<p style={wrap}>
						{`Assignment ${assignment.assignmentId}; device ${assignment.deviceId}; room ${assignment.roomId}; revision ${assignment.revision}; created ${assignment.createdAt}. UNQUALIFIED — customer access disabled.`}
					</p>
				)}
			</Card>
			<Card title="Operational qualification">
				<dl>
					<dt>Protocol</dt>
					<dd>NOT_CONFIGURED</dd>
					<dt>Compatibility</dt>
					<dd>NOT_EVALUATED</dd>
					<dt>Connection test</dt>
					<dd>UNSUPPORTED — no approved runtime adapter</dd>
					<dt>Physical qualification</dt>
					<dd>REQUIRES_PHYSICAL_VERIFICATION</dd>
					<dt>Customer access</dt>
					<dd>Disabled</dd>
				</dl>
				<Button disabled>Validate compatibility</Button>{" "}
				<Button disabled>Test connection</Button>{" "}
				<Button disabled>Activate approved device</Button>
				<p>
					Connection or software declarations cannot establish physical safety.
					Emergency egress must remain independent of network, payment and
					remote commands.
				</p>
			</Card>
		</div>
	);
}

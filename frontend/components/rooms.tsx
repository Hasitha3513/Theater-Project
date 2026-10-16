"use client";
import Link from "next/link";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
	Badge,
	Button,
	Card,
	Field,
	PageHeading,
	SelectField,
	StateMessage,
} from "./primitives";
import {
	roomRequest,
	RoomRequestError,
	queryRooms,
	stateFor,
} from "../lib/rooms";
import type { Venue, Room, Page, WindowConfig, Facility } from "../lib/rooms";
const boundary =
	"Catalog configuration does not confirm availability, cleanliness, device health, safe occupancy or entry readiness. Staff-supported operations apply.";
export function RoomPhoto({ room }: { room: Room }) {
	const [failed, setFailed] = useState(false);
	const image = room.images[0];
	return image && !failed ? (
		<Image
			width={960}
			height={540}
			className="room-photo"
			src={`/${image.assetPath}`}
			alt={image.altText}
			onError={() => setFailed(true)}
		/>
	) : (
		<p className="photo-fallback">Room photograph unavailable</p>
	);
}
export function RoomSummary({
	room,
	admin,
	edit,
}: {
	room: Room;
	admin: boolean;
	edit?: () => void;
}) {
	return (
		<Card title={room.name}>
			<RoomPhoto room={room} />
			<p>
				{room.code} · Capacity {room.capacity}
			</p>
			<p>{room.description}</p>
			<p>
				{room.facilities.map((f) => f.label).join(" · ") ||
					"No facilities recorded"}
			</p>
			{admin ? (
				<>
					<Badge>{room.enabled ? "Catalog enabled" : "Catalog disabled"}</Badge>
					<p>
						{room.configured
							? "Venue configured"
							: "Venue configuration incomplete"}
					</p>
					{room.permittedActions?.includes("room.write") && (
						<Button onClick={edit}>Edit room</Button>
					)}
				</>
			) : (
				<Link href={`/rooms/${room.roomId}`}>Room details</Link>
			)}
		</Card>
	);
}
export function RoomCatalog({ admin = false }: { admin?: boolean }) {
	const [venues, setVenues] = useState<Venue[]>([]);
	const [venue, setVenue] = useState("");
	const [q, setQ] = useState("");
	const [minimum, setMinimum] = useState("");
	const [facility, setFacility] = useState("");
	const [enabled, setEnabled] = useState("");
	const [page, setPage] = useState(0);
	const [data, setData] = useState<Page>();
	const [error, setError] = useState<unknown>();
	const [loading, setLoading] = useState(false);
	const [reload, setReload] = useState(() => new AbortController());
	const [view, setView] = useState("cards");
	const [editing, setEditing] = useState<Room | null | undefined>();
	useEffect(() => {
		if (admin)
			setView(
				localStorage.getItem("cn-room-view") === "table" ? "table" : "cards",
			);
		roomRequest<Venue[]>(admin ? "/api/admin/venues" : "/api/catalog/venues")
			.then(setVenues)
			.catch(setError);
	}, [admin]);
	useEffect(() => {
		if (admin && !venue) {
			setData(undefined);
			return;
		}
		let current = true;
		setLoading(true);
		setError(undefined);
		setData(undefined);
		const path = admin
			? `/api/admin/venues/${encodeURIComponent(venue)}/rooms`
			: "/api/catalog/rooms";
		roomRequest<Page>(
			`${path}?${queryRooms(admin ? "" : venue, q, minimum, facility, page, enabled)}`,
			{ signal: reload.signal },
		)
			.then((v) => {
				if (current) setData(v);
			})
			.catch((e) => {
				if (current) setError(e);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, [admin, venue, q, minimum, facility, page, enabled, reload]);
	return (
		<>
			<PageHeading
				eyebrow={admin ? "Administration" : "Discover CineNest"}
				title={admin ? "Room configuration" : "Explore our rooms"}
			>
				{boundary}
			</PageHeading>
			<div className="filter-grid">
				<SelectField
					id="room-venue"
					label="Venue"
					value={venue}
					onChange={(e) => {
						setVenue(e.target.value);
						setPage(0);
					}}
				>
					<option value="">
						{admin ? "Select a permitted venue" : "All venues"}
					</option>
					{venues.map((v) => (
						<option key={v.venueId} value={v.venueId}>
							{v.displayName}
						</option>
					))}
				</SelectField>
				{admin && (
					<Field
						id="scope-venue"
						label="Assigned venue ID"
						hint="For an existing room grant not listed above. The server verifies scope."
						value={venue}
						onChange={(e) => {
							setVenue(e.target.value);
							setPage(0);
						}}
					/>
				)}
				<Field
					id="room-search"
					label="Name or code"
					value={q}
					onChange={(e) => {
						setQ(e.target.value);
						setPage(0);
					}}
				/>
				<Field
					id="room-capacity"
					label="Minimum capacity"
					type="number"
					min="0"
					value={minimum}
					onChange={(e) => {
						setMinimum(e.target.value);
						setPage(0);
					}}
				/>
				<Field
					id="room-facility"
					label="Facility code"
					value={facility}
					onChange={(e) => {
						setFacility(e.target.value);
						setPage(0);
					}}
				/>
				{admin && (
					<SelectField
						id="room-enabled"
						label="Catalog state"
						value={enabled}
						onChange={(e) => {
							setEnabled(e.target.value);
							setPage(0);
						}}
					>
						<option value="">All</option>
						<option value="true">Enabled</option>
						<option value="false">Disabled</option>
					</SelectField>
				)}
			</div>
			<div className="toolbar">
				<Button
					variant="secondary"
					onClick={() => {
						setQ("");
						setMinimum("");
						setFacility("");
						setEnabled("");
						setPage(0);
					}}
				>
					Reset filters
				</Button>
				{admin && (
					<>
						<Button
							variant="secondary"
							onClick={() => {
								const v = view === "cards" ? "table" : "cards";
								setView(v);
								localStorage.setItem("cn-room-view", v);
							}}
						>
							Show {view === "cards" ? "table" : "cards"}
						</Button>
						{venue && data?.permittedActions?.includes("room.write") && !error && (
							<Button onClick={() => setEditing(null)}>
								Add room
							</Button>
						)}
					</>
				)}
			</div>
			{admin && (
				<p className="muted">
					Creation requires room.write. Reservation-impact checking is not
					implemented. No booking or device actions are provided.
				</p>
			)}
			{loading && <StateMessage kind="loading" />}
			{error !== undefined && (
				<StateMessage kind={stateFor(error)}>
					<Button onClick={() => setReload(new AbortController())}>
						Retry
					</Button>
				</StateMessage>
			)}
			{admin && !venue && !error && (
				<p role="status">Select an assigned venue to view its configuration.</p>
			)}
			{data && !loading && !error && (
				<>
					{data.items.length === 0 ? (
						<section className="state" role="status">
							<h2>No matching rooms</h2>
							<p>Change the filters, or no rooms are recorded yet.</p>
						</section>
					) : view === "table" && admin ? (
						<div className="table-scroll">
							<table>
								<caption>Rooms · same filtered records as card view</caption>
								<thead>
									<tr>
										<th>Code</th>
										<th>Name</th>
										<th>Capacity</th>
										<th>Facilities</th>
										<th>Catalog state</th>
										<th>Actions</th>
									</tr>
								</thead>
								<tbody>
									{data.items.map((r) => (
										<tr key={r.roomId} data-room-id={r.roomId}>
											<td>{r.code}</td>
											<td>{r.name}</td>
											<td>{r.capacity}</td>
											<td>{r.facilities.map((f) => f.label).join(", ")}</td>
											<td>{r.enabled ? "Enabled" : "Disabled"}</td>
											<td>
												{r.permittedActions?.includes("room.write") ? (
													<Button onClick={() => setEditing(r)}>
														Edit room
													</Button>
												) : (
													"Read only"
												)}
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					) : (
						<div className="room-grid">
							{data.items.map((r) => (
								<div key={r.roomId} data-room-id={r.roomId}>
									<RoomSummary
										room={r}
										admin={admin}
										edit={() => setEditing(r)}
									/>
								</div>
							))}
						</div>
					)}
					<div className="toolbar">
						<Button
							variant="secondary"
							disabled={page === 0}
							onClick={() => setPage(page - 1)}
						>
							Previous page
						</Button>
						<span role="status">
							{data.total} rooms · Page {page + 1}
						</span>
						<Button
							variant="secondary"
							disabled={(page + 1) * data.size >= data.total}
							onClick={() => setPage(page + 1)}
						>
							Next page
						</Button>
					</div>
				</>
			)}
			{editing !== undefined && (
				<ConfigurationEditor
					kind="room"
					venueId={venue}
					initial={editing ?? undefined}
					onClose={() => setEditing(undefined)}
					onSaved={() => {
						setEditing(undefined);
						setReload(new AbortController());
					}}
				/>
			)}
		</>
	);
}
export function RoomDetail({ id }: { id: string }) {
	const [room, setRoom] = useState<Room>();
	const [error, setError] = useState<unknown>();
	const [reload, setReload] = useState(() => new AbortController());
	useEffect(() => {
		let current = true;
		setError(undefined);
		setRoom(undefined);
		roomRequest<Room>(`/api/catalog/rooms/${encodeURIComponent(id)}`, {
			signal: reload.signal,
		})
			.then((v) => {
				if (current) setRoom(v);
			})
			.catch((e) => {
				if (current) setError(e);
			});
		return () => {
			current = false;
		};
	}, [id, reload]);
	return (
		<>
			<PageHeading
				eyebrow="Room discovery"
				title={room?.name ?? "Room details"}
			>
				{boundary}
			</PageHeading>
			{error ? (
				<StateMessage kind={stateFor(error)}>
					<p>
						{error instanceof RoomRequestError && error.status === 404
							? "This room is not in the public catalog."
							: "Retry when the service is available."}
					</p>
					<Button onClick={() => setReload(new AbortController())}>
						Retry
					</Button>
				</StateMessage>
			) : !room ? (
				<StateMessage kind="loading" />
			) : (
				<>
					<RoomSummary room={room} admin={false} />
					<Card title="Customer operating windows">
						<p>
							{room.venue?.displayName} · {room.venue?.timezone}
						</p>
						{room.venue?.windows.map((w) => (
							<p key={w.weekday}>
								{days[w.weekday]} {clock(w.openingMinute)}–
								{clock(w.closingMinute)}
							</p>
						))}
						<p>
							Missing days are closed. Special-day policies are not available.
							Hours do not establish live availability.
						</p>
					</Card>
				</>
			)}
			<Link href="/rooms">Back to rooms</Link>
		</>
	);
}
export function VenueConfiguration() {
	const [venues, setVenues] = useState<Venue[]>([]);
	const [error, setError] = useState<unknown>();
	const [loading, setLoading] = useState(true);
	const [reload, setReload] = useState(() => new AbortController());
	const [editing, setEditing] = useState<Venue | null | undefined>();
	useEffect(() => {
		let current = true;
		setLoading(true);
		setError(undefined);
		roomRequest<Venue[]>("/api/admin/venues", { signal: reload.signal })
			.then((v) => {
				if (current) setVenues(v);
			})
			.catch((e) => {
				if (current) setError(e);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, [reload]);
	return (
		<>
			<PageHeading eyebrow="Scoped administration" title="Venue configuration">
				{boundary}
			</PageHeading>
			<p>
				New venues require venue.write for an already assigned ID. This page
				cannot grant access or approve staffing.
			</p>
			<Button onClick={() => setEditing(null)}>
				Configure assigned new venue
			</Button>
			{loading && <StateMessage kind="loading" />}
			{error ? (
				<StateMessage kind={stateFor(error)}>
					<Button onClick={() => setReload(new AbortController())}>
						Retry
					</Button>
				</StateMessage>
			) : (
				!loading && (
					<>
						{venues.length === 0 && (
							<p role="status">No permitted venues are configured.</p>
						)}
						<div className="room-grid">
							{venues.map((v) => (
								<Card key={v.venueId} title={v.displayName}>
									<p>
										{v.code} · {v.timezone}
									</p>
									<Badge>{v.configured ? "Configured" : "Incomplete"}</Badge>
									<p>Version {v.version}</p>
									{v.permittedActions?.includes("venue.write") && (
										<Button onClick={() => setEditing(v)}>Edit venue</Button>
									)}
								</Card>
							))}
						</div>
					</>
				)
			)}
			{editing !== undefined && (
				<ConfigurationEditor
					kind="venue"
					initial={editing ?? undefined}
					onClose={() => setEditing(undefined)}
					onSaved={() => {
						setEditing(undefined);
						setReload(new AbortController());
					}}
				/>
			)}
		</>
	);
}
const days = [
	"",
	"Monday",
	"Tuesday",
	"Wednesday",
	"Thursday",
	"Friday",
	"Saturday",
	"Sunday",
];
export function clock(minute: number) {
	return `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
}
function minutes(value: string) {
	if (!/^\d{2}:\d{2}$/.test(value)) return -1;
	const [h, m] = value.split(":").map(Number);
	return m > 59 ? -1 : h * 60 + m;
}
function ConfigurationEditor({
	kind,
	venueId,
	initial,
	onClose,
	onSaved,
}: {
	kind: "room" | "venue";
	venueId?: string;
	initial?: Room | Venue;
	onClose: () => void;
	onSaved: () => void;
}) {
	const dialog = useRef<HTMLDialogElement>(null);
	const [error, setError] = useState<unknown>();
	const [busy, setBusy] = useState(false);
	const [dirty, setDirty] = useState(false);
	const [version, setVersion] = useState(initial?.version);
	const [comparison, setComparison] = useState<Room | Venue>();
	const venue = kind === "venue" ? (initial as Venue | undefined) : undefined;
	const room = kind === "room" ? (initial as Room | undefined) : undefined;
	const [windows, setWindows] = useState<WindowConfig[]>(venue?.windows ?? []);
	const [facilities, setFacilities] = useState<Facility[]>(
		room?.facilities ?? [],
	);
	useEffect(() => {
		const previous = document.activeElement as HTMLElement;
		dialog.current?.showModal();
		return () => previous?.focus();
	}, []);
	const close = () => {
		if (!busy && (!dirty || window.confirm("Discard unsaved configuration?")))
			onClose();
	};
	const path =
		kind === "venue"
			? `/api/admin/venues${venue ? `/${venue.venueId}` : ""}`
			: `/api/admin/venues/${venueId}/rooms${room ? `/${room.roomId}` : ""}`;
	async function save(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		setError(undefined);
		const f = new FormData(event.currentTarget);
		const body: Record<string, unknown> = {
			code: f.get("code"),
			enabled: f.get("enabled") === "on",
		};
		if (initial) body.expectedVersion = version;
		if (kind === "venue") {
			Object.assign(body, {
				displayName: f.get("name"),
				timezone: f.get("timezone"),
				cleaningBufferMinutes:
					f.get("buffer") === "" ? null : Number(f.get("buffer")),
				postCloseCleaningApproved: f.get("postClose") === "on",
				operationalApprovalReference: f.get("reference"),
				windows,
			});
			if (!initial) body.venueId = f.get("venueId");
		} else
			Object.assign(body, {
				name: f.get("name"),
				description: f.get("description"),
				capacity: Number(f.get("capacity")),
				facilities,
				images:
					room?.images.map(({ assetPath, altText, sortOrder }) => ({
						assetPath,
						altText,
						sortOrder,
					})) ?? [],
			});
		try {
			await roomRequest(path, {
				method: initial ? "PUT" : "POST",
				body: JSON.stringify(body),
			});
			onSaved();
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<dialog
			ref={dialog}
			className="configuration-dialog"
			aria-labelledby="configuration-title"
			onCancel={(e) => {
				e.preventDefault();
				close();
			}}
			onKeyDown={(e) => {
				if (e.key !== "Tab") return;
				const controls = Array.from(
					e.currentTarget.querySelectorAll<HTMLElement>(
						"button:not([disabled]),input:not([disabled]),select,textarea,a[href]",
					),
				);
				if (e.shiftKey && document.activeElement === controls[0]) {
					e.preventDefault();
					controls.at(-1)?.focus();
				} else if (!e.shiftKey && document.activeElement === controls.at(-1)) {
					e.preventDefault();
					controls[0]?.focus();
				}
			}}
		>
			<div className="drawer-heading">
				<h2 id="configuration-title">
					{initial ? "Edit" : "Create"} {kind}
				</h2>
				<Button variant="secondary" disabled={busy} onClick={close}>
					Close editor
				</Button>
			</div>
			<p>{boundary}</p>
			<p>Reservation-impact checking is not implemented.</p>
			<form onSubmit={save} onChange={() => setDirty(true)}>
				<div className="filter-grid">
					{kind === "venue" && !initial && (
						<Field
							id="new-venue-id"
							label="Assigned venue ID"
							name="venueId"
							required
						/>
					)}
					<Field
						id="config-code"
						label="Code"
						name="code"
						defaultValue={initial?.code ?? ""}
						required
						maxLength={64}
					/>
					<Field
						id="config-name"
						label="Name"
						name="name"
						defaultValue={venue?.displayName ?? room?.name ?? ""}
						required
						maxLength={200}
					/>
					{kind === "room" ? (
						<>
							<Field
								id="config-capacity"
								label="Capacity"
								type="number"
								name="capacity"
								defaultValue={room?.capacity}
								required
								min="1"
							/>
							<Field
								id="config-description"
								label="Neutral physical description"
								name="description"
								defaultValue={room?.description ?? ""}
								maxLength={2000}
							/>
						</>
					) : (
						<>
							<Field
								id="config-timezone"
								label="IANA venue timezone"
								name="timezone"
								defaultValue={venue?.timezone ?? ""}
								required
								hint="Enter the approved timezone; no default is assumed."
							/>
							<Field
								id="config-buffer"
								label="Cleaning buffer (minutes)"
								type="number"
								name="buffer"
								min="0"
								defaultValue={venue?.cleaningBufferMinutes ?? ""}
								hint="Blank means unconfigured; cleaning is not customer viewing time."
							/>
							<Field
								id="config-reference"
								label="Approved operational reference"
								name="reference"
								defaultValue={venue?.operationalApprovalReference ?? ""}
								hint="Must match independent server-side owner approval."
							/>
							<label>
								<input
									type="checkbox"
									name="postClose"
									defaultChecked={venue?.postCloseCleaningApproved}
								/>
								Permit approved staffed post-close cleaning
							</label>
						</>
					)}
					<label>
						<input
							type="checkbox"
							name="enabled"
							defaultChecked={initial?.enabled ?? false}
						/>
						Enable catalog configuration (not physical readiness)
					</label>
				</div>
				{kind === "venue" ? (
					<fieldset>
						<legend>Weekly customer operating windows</legend>
						<p>
							Missing days are closed. Use HH:mm; 24:00 means next-day midnight.
							Special-day exceptions are not configured.
						</p>
						{days.slice(1).map((day, i) => {
							const index = i + 1;
							const w = windows.find((x) => x.weekday === index);
							return (
								<div className="window-row" key={day}>
									<label>
										<input
											type="checkbox"
											checked={!!w}
											onChange={(e) => {
												setDirty(true);
												setWindows(
													e.target.checked
														? [
																...windows,
																{
																	weekday: index,
																	openingMinute: 0,
																	closingMinute: 0,
																},
															]
														: windows.filter((x) => x.weekday !== index),
												);
											}}
										/>
										{day}
									</label>
									{w && (
										<>
											<Field
												id={`open-${index}`}
												label="Open HH:mm"
												required
												defaultValue={clock(w.openingMinute)}
												onChange={(e) =>
													setWindows(
														windows.map((x) =>
															x.weekday === index
																? {
																		...x,
																		openingMinute: minutes(e.target.value),
																	}
																: x,
														),
													)
												}
											/>
											<Field
												id={`close-${index}`}
												label="Close HH:mm"
												required
												defaultValue={clock(w.closingMinute)}
												onChange={(e) =>
													setWindows(
														windows.map((x) =>
															x.weekday === index
																? {
																		...x,
																		closingMinute: minutes(e.target.value),
																	}
																: x,
														),
													)
												}
											/>
										</>
									)}
								</div>
							);
						})}
					</fieldset>
				) : (
					<fieldset>
						<legend>Neutral facilities</legend>
						{facilities.map((f, i) => (
							<div className="window-row" key={f.sortOrder}>
								<Field
									id={`facility-code-${i}`}
									label="Capability code"
									value={f.code}
									required
									onChange={(e) =>
										setFacilities(
											facilities.map((v, n) =>
												n === i ? { ...v, code: e.target.value } : v,
											),
										)
									}
								/>
								<Field
									id={`facility-label-${i}`}
									label="Physical capability label"
									value={f.label}
									required
									onChange={(e) =>
										setFacilities(
											facilities.map((v, n) =>
												n === i ? { ...v, label: e.target.value } : v,
											),
										)
									}
								/>
								<Button
									variant="secondary"
									onClick={() => {
										setDirty(true);
										setFacilities(facilities.filter((_, n) => n !== i));
									}}
								>
									Remove facility {i + 1}
								</Button>
							</div>
						))}
						<Button
							variant="secondary"
							onClick={() => {
								setDirty(true);
								setFacilities([
									...facilities,
									{
										code: "",
										label: "",
										sortOrder:
											Math.max(-1, ...facilities.map((f) => f.sortOrder)) + 1,
									},
								]);
							}}
						>
							Add facility
						</Button>
						<p>
							No approved room photographs are available. Upload and remote
							images are not supported; a labelled fallback is displayed.
						</p>
					</fieldset>
				)}
				{error !== undefined && (
					<section role="alert" className="state">
						<h3>
							{error instanceof RoomRequestError && error.status === 409
								? "Configuration conflict"
								: "Save failed"}
						</h3>
						<p>
							{error instanceof Error ? error.message : "Please retry"} Your
							draft is retained.
						</p>
						{error instanceof RoomRequestError &&
							error.status === 409 &&
							initial && (
								<Button
									variant="secondary"
									onClick={async () => {
										try {
											setComparison(await roomRequest<Room | Venue>(path));
										} catch (e) {
											setError(e);
										}
									}}
								>
									Load latest for comparison
								</Button>
							)}
					</section>
				)}
				{comparison && (
					<section className="state">
						<h3>Latest saved configuration · version {comparison.version}</h3>
						<pre>{JSON.stringify(comparison, null, 2)}</pre>
						<Button
							variant="secondary"
							onClick={() => {
								setVersion(comparison.version);
								setComparison(undefined);
								setError(undefined);
							}}
						>
							Use latest version with my reviewed draft
						</Button>
					</section>
				)}
				<div className="toolbar">
					<Button type="submit" busy={busy}>
						Save {kind}
					</Button>
					<Button variant="secondary" disabled={busy} onClick={close}>
						Cancel
					</Button>
					<span>Configuration version {version ?? "new"}</span>
				</div>
			</form>
		</dialog>
	);
}

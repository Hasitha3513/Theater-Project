"use client";
import { useEffect, useRef, useState } from "react";
import {
	blockPath,
	blockUuid,
	parseBlock,
	parseBlockList,
	type RoomBlock,
	type RoomBlockList,
} from "../lib/room-blocks";
import { queryRooms, roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";

type Inputs = {
	venue: string;
	room: string;
	startDate: string;
	startTime: string;
	endDate: string;
	endTime: string;
};
type Errors = Partial<Record<keyof Inputs | "context", string>>;
type Command = { path: string; body: string; timezone?: string };
type VenueChoice = { venueId: string; displayName: string };
type RoomChoice = {
	roomId: string;
	venueId: string;
	name: string;
	capacity: number;
};

/** Resolve wall-clock input in the server-provided venue zone, never the browser zone.
 * Round-trip all candidates: reject DST gaps and folds rather than picking an offset.
 */
export function blockDateTime(date: string, time: string, timezone: string) {
	if (
		!/^\d{4}-\d{2}-\d{2}$/.test(date) ||
		date.startsWith("0000") ||
		!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)
	)
		throw Error("Use a valid date and a 24-hour time (HH:mm).");
	const desired = `${date}T${time}:00`,
		nominal = Date.parse(`${desired}Z`);
	if (
		!Number.isFinite(nominal) ||
		new Date(nominal).toISOString().slice(0, 19) !== desired
	)
		throw Error("Choose a real calendar date.");
	const formatter = new Intl.DateTimeFormat("en-GB-u-ca-iso8601-nu-latn", {
		timeZone: timezone,
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hourCycle: "h23",
	});
	const local = (epoch: number) => {
		const p = Object.fromEntries(
			formatter.formatToParts(epoch).map((x) => [x.type, x.value]),
		);
		return `${p.year.padStart(4, "0")}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}`;
	};
	const offsets = new Set<number>();
	for (let hours = -36; hours <= 36; hours += 6) {
		const sample = nominal + hours * 3600000;
		offsets.add(Date.parse(`${local(sample)}Z`) - sample);
	}
	const candidates = [...offsets]
		.map((offset) => nominal - offset)
		.filter((epoch) => local(epoch) === desired);
	if (candidates.length !== 1)
		throw Error(
			candidates.length
				? "This time occurs twice in the venue timezone. Choose an unambiguous time."
				: "This time does not exist in the venue timezone. Choose another time.",
		);
	return new Date(candidates[0]).toISOString();
}
export function validateBlockInputs(input: Inputs, timezone?: string) {
	const errors: Errors = {};
	if (!input.venue)
		errors.venue = "Choose a venue or enter its approved reference.";
	else if (!blockUuid.test(input.venue))
		errors.venue = "Enter a valid venue UUID reference.";
	if (!input.room)
		errors.room = "Choose a room or enter its approved reference.";
	else if (!blockUuid.test(input.room))
		errors.room = "Enter a valid room UUID reference.";
	if (!timezone)
		errors.context =
			"Load room blocks first to confirm permissions and the venue timezone.";
	let startAt: string | undefined, endAt: string | undefined;
	for (const side of ["start", "end"] as const) {
		const date = `${side}Date` as const,
			time = `${side}Time` as const;
		if (!input[date]) errors[date] = `Choose the ${side} date.`;
		else if (
			!/^\d{4}-\d{2}-\d{2}$/.test(input[date]) ||
			input[date].startsWith("0000")
		)
			errors[date] = "Use a valid calendar date (YYYY-MM-DD).";
		if (!input[time]) errors[time] = `Choose the ${side} time.`;
		else if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(input[time]))
			errors[time] = "Use a 24-hour time (HH:mm).";
		if (!errors[date] && !errors[time] && timezone) {
			try {
				const instant = blockDateTime(input[date], input[time], timezone);
				if (side === "start") startAt = instant;
				else endAt = instant;
			} catch (error) {
				if (error instanceof RangeError)
					errors.context =
						"The venue timezone is unavailable. Reload current room context.";
				else if (
					error instanceof Error &&
					error.message === "Choose a real calendar date."
				)
					errors[date] = error.message;
				else
					errors[time] =
						error instanceof Error ? error.message : "Check this time.";
			}
		}
	}
	if (startAt && endAt && Date.parse(endAt) <= Date.parse(startAt))
		errors.endTime = "End date and time must be after the start.";
	return { errors, startAt, endAt };
}
export function blockLocalTime(instant: string, timezone: string) {
	return new Intl.DateTimeFormat("en-GB", {
		timeZone: timezone,
		day: "numeric",
		month: "long",
		year: "numeric",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hourCycle: "h23",
		timeZoneName: "longOffset",
	}).format(new Date(instant));
}
export function blockFeedback(error: unknown, uncertainWrite = false) {
	if (uncertainWrite)
		return "Save not confirmed. Inspect existing blocks before retrying. Retry original block command sends the exact same request; no automatic retry occurs.";
	if (!(error instanceof RoomRequestError))
		return "Connection or response unavailable. No success is assumed. Reload the current room context.";
	switch (error.status) {
		case 401:
			return "Sign-in required or session expired. Log in again, then reload this room.";
		case 403:
			return "Access denied. Check exact-venue read/create permissions and session/CSRF protection.";
		case 409:
			if (
				error.message ===
				"Room configuration changed; reload before creating a block"
			)
				return "Room configuration changed. Reload room blocks and review the interval before creating a new command.";
			if (error.message === "Inventory conflict; no booking was changed")
				return "This interval overlaps existing inventory. Choose another interval. No booking was changed.";
			if (error.message === "Command already used with another request")
				return "This command identity was already used for different details. Inspect the original result before proceeding.";
			return "Inventory conflicts or its configuration changed. Reload room blocks and review the interval. No booking change is confirmed.";
		case 400:
			return "The server did not accept these details. Check the room, dates and times; reload current context.";
		case 404:
			return "Room, block or local feature unavailable. Check the exact references and current environment.";
		case 413:
			return "Request too large. Reload the room context before trying a new command.";
		default:
			return "The service could not complete this request. No success is assumed.";
	}
}
export function blockWriteFailure(error: unknown, command?: Command) {
	const definite =
		error instanceof RoomRequestError &&
		[400, 401, 403, 404, 409, 413].includes(error.status);
	return {
		pending: definite ? undefined : command,
		message: blockFeedback(error, !!command && !definite),
	};
}
export function prepareBlockCommand(
	venue: string,
	room: string,
	startAt: string,
	endAt: string,
	configuration: string,
	key: string,
): Command {
	if (
		!blockUuid.test(key) ||
		!configuration ||
		configuration.length > 16384 ||
		!Number.isFinite(Date.parse(startAt)) ||
		!Number.isFinite(Date.parse(endAt)) ||
		!/(Z|[+-]\d\d:\d\d)$/.test(startAt) ||
		!/(Z|[+-]\d\d:\d\d)$/.test(endAt) ||
		Date.parse(startAt) >= Date.parse(endAt)
	)
		throw Error("Invalid block command");
	return {
		path: blockPath(venue, room),
		body: JSON.stringify({
			idempotencyKey: key,
			startAt,
			endAt,
			expectedConfiguration: configuration,
		}),
	};
}
export async function readBlockContext(venue: string, room: string, page = 0) {
	return parseBlockList(
		await roomRequest(`${blockPath(venue, room)}?page=${page}&size=24`),
		venue,
		room,
	);
}
export async function sendBlockCommand(
	command: Command,
	venue: string,
	room: string,
) {
	return parseBlock(
		await roomRequest(command.path, { method: "POST", body: command.body }),
		venue,
		room,
	);
}
export function findRoomChoice(
	rooms: readonly RoomChoice[],
	venueId: string,
	roomId: string,
) {
	return rooms.find(
		(room) => room.venueId === venueId && room.roomId === roomId,
	);
}

export function SavedBlockDetail({
	block,
	timezone,
	rooms = [],
}: {
	block: RoomBlock;
	timezone: string;
	rooms?: readonly RoomChoice[];
}) {
	const room = findRoomChoice(rooms, block.venueId, block.roomId);
	return (
		<Card title="Saved block detail">
			<p>
				{room ? (
					<>Room: {room.name}</>
				) : (
					<>
						Confirmed room reference: <code>{block.roomId}</code>
					</>
				)}
			</p>
			<p>
				{blockLocalTime(block.startAt, timezone)} –{" "}
				{blockLocalTime(block.endAt, timezone)}
			</p>
			<p>
				Venue timezone: {timezone}. Revision {block.revision}; synthetic
				inventory evidence only.
			</p>
			<p>
				Block ID: <code>{block.blockId}</code>
			</p>
			<p>
				Created: {blockLocalTime(block.createdAt, timezone)}. Creator identity
				is not supplied in this view.
			</p>
			<details>
				<summary>Exact original timestamps</summary>
				<p>
					Start: <code>{block.startAt}</code>
				</p>
				<p>
					End: <code>{block.endAt}</code>
				</p>
				<p>
					Created: <code>{block.createdAt}</code>
				</p>
			</details>
		</Card>
	);
}
export function RoomBlocking() {
	const [venue, setVenue] = useState(""),
		[room, setRoom] = useState("");
	const [startDate, setStartDate] = useState(""),
		[startTime, setStartTime] = useState("");
	const [endDate, setEndDate] = useState(""),
		[endTime, setEndTime] = useState("");
	const [venues, setVenues] = useState<VenueChoice[]>([]),
		[rooms, setRooms] = useState<RoomChoice[]>([]);
	const [list, setList] = useState<RoomBlockList>(),
		[detail, setDetail] = useState<{ block: RoomBlock; timezone: string }>();
	const [errors, setErrors] = useState<Errors>({}),
		[busy, setBusy] = useState(false);
	const [message, setMessage] = useState(
		"Local synthetic only. Choose a room, then load current room blocks.",
	);
	const generation = useRef(0),
		pending = useRef<Command | undefined>(undefined);
	const resetDates = () => {
		setStartDate("");
		setStartTime("");
		setEndDate("");
		setEndTime("");
	};
	useEffect(() => {
		const clear = () => {
			generation.current++;
			setList(undefined);
			setDetail(undefined);
			pending.current = undefined;
			setBusy(false);
			setErrors({});
			setStartDate("");
			setStartTime("");
			setEndDate("");
			setEndTime("");
			setMessage("Session/context changed. Reload authoritative room context.");
		};
		const events = ["cinenest-session-change", "pagehide", "popstate"];
		for (const e of events) window.addEventListener(e, clear);
		document.addEventListener("visibilitychange", clear);
		return () => {
			generation.current++;
			for (const e of events) window.removeEventListener(e, clear);
			document.removeEventListener("visibilitychange", clear);
		};
	}, []);
	function scope(change: () => void) {
		generation.current++;
		setList(undefined);
		setDetail(undefined);
		pending.current = undefined;
		setErrors({});
		resetDates();
		change();
		setMessage(
			"Room selection changed. Load current room blocks before choosing dates.",
		);
	}
	async function findVenues() {
		const turn = ++generation.current;
		setBusy(true);
		try {
			const result = await roomRequest<unknown>("/api/catalog/venues");
			if (
				!Array.isArray(result) ||
				!result.every(
					(x) =>
						x && blockUuid.test(x.venueId) && typeof x.displayName === "string",
				)
			)
				throw Error();
			if (turn === generation.current) {
				setVenues(result);
				setMessage(
					"Choose a venue by name, then find its rooms. Catalogue visibility does not grant block permissions.",
				);
			}
		} catch {
			if (turn === generation.current)
				setMessage(
					"Name lookup unavailable. Use approved exact references below; protected room permissions still apply.",
				);
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function findRooms() {
		if (!blockUuid.test(venue)) {
			setErrors({
				venue: "Choose a venue or enter a valid venue reference first.",
			});
			return;
		}
		const turn = ++generation.current;
		setBusy(true);
		setRooms([]);
		try {
			const result = await roomRequest<{ items?: unknown }>(
				`/api/catalog/rooms?${queryRooms(venue, "", "", "", 0, "")}`,
			);
			if (
				!Array.isArray(result.items) ||
				!result.items.every(
					(x) =>
						x &&
						blockUuid.test(x.roomId) &&
						x.venueId === venue &&
						typeof x.name === "string" &&
						Number.isSafeInteger(x.capacity) &&
						x.capacity > 0,
				)
			)
				throw Error();
			if (turn === generation.current) {
				setRooms(result.items);
				setMessage(
					"Choose a room by name. If it is not on this catalogue page, use its approved exact reference below.",
				);
			}
		} catch {
			if (turn === generation.current)
				setMessage(
					"Room name lookup unavailable. Use the approved exact room reference below.",
				);
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function load(page = 0) {
		const e: Errors = {};
		if (!venue) e.venue = "Choose a venue or enter its approved reference.";
		else if (!blockUuid.test(venue))
			e.venue = "Enter a valid venue UUID reference.";
		if (!room) e.room = "Choose a room or enter its approved reference.";
		else if (!blockUuid.test(room))
			e.room = "Enter a valid room UUID reference.";
		setErrors(e);
		if (Object.keys(e).length) {
			setMessage("Choose a valid venue and room before loading blocks.");
			return;
		}
		const turn = ++generation.current;
		setBusy(true);
		setDetail(undefined);
		try {
			const data = await readBlockContext(venue, room, page);
			if (turn !== generation.current) return;
			if (list && list.timezone !== data.timezone && !pending.current)
				resetDates();
			setList(data);
			setMessage(
				pending.current
					? "Current blocks loaded. The unconfirmed write is still bound to its original command; inspect history before an explicit retry."
					: "Current inventory context loaded. Choose the start and end in the venue timezone below.",
			);
		} catch (error) {
			if (turn === generation.current) {
				setList(undefined);
				setMessage(blockFeedback(error));
			}
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function create() {
		if (!pending.current) {
			const validation = validateBlockInputs(
				{ venue, room, startDate, startTime, endDate, endTime },
				list?.timezone,
			);
			setErrors(validation.errors);
			if (
				Object.keys(validation.errors).length ||
				!list ||
				!validation.startAt ||
				!validation.endAt
			) {
				setMessage("Check the highlighted fields. No block request was sent.");
				return;
			}
			pending.current = {
				...prepareBlockCommand(
					venue,
					room,
					validation.startAt,
					validation.endAt,
					list.configuration,
					crypto.randomUUID(),
				),
				timezone: list.timezone,
			};
		}
		const turn = ++generation.current;
		setBusy(true);
		const timezone =
			pending.current?.timezone ?? list?.timezone ?? detail?.timezone;
		try {
			const data = await sendBlockCommand(pending.current, venue, room);
			if (turn !== generation.current) return;
			pending.current = undefined;
			setList(undefined);
			if (timezone) setDetail({ block: data, timezone });
			setMessage(
				"Room block saved. No existing booking was changed. Reload blocks to inspect current state.",
			);
		} catch (error) {
			if (turn === generation.current) {
				const failure = blockWriteFailure(error, pending.current);
				pending.current = failure.pending;
				if (!failure.pending) {
					setList(undefined);
					setDetail(undefined);
				}
				setMessage(failure.message);
			}
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function inspect(id: string) {
		if (!list) return;
		const timezone = list.timezone,
			turn = ++generation.current;
		setBusy(true);
		setDetail(undefined);
		try {
			const data = parseBlock(
				await roomRequest(`${blockPath(venue, room)}/${id}`),
				venue,
				room,
			);
			if (turn === generation.current) setDetail({ block: data, timezone });
		} catch (error) {
			if (turn === generation.current) {
				if (
					error instanceof RoomRequestError &&
					[401, 403].includes(error.status)
				)
					setList(undefined);
				setMessage(blockFeedback(error));
			}
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	const selectedVenue = venues.find((v) => v.venueId === venue),
		selectedRoom = findRoomChoice(rooms, venue, room);
	const confirmedReference = list && !selectedRoom ? room : "";
	const formTimezone = pending.current?.timezone ?? list?.timezone;
	const validation = validateBlockInputs(
		{ venue, room, startDate, startTime, endDate, endTime },
		formTimezone,
	);
	const frozen = busy || !!pending.current;
	return (
		<>
			<h1>Room Blocking</h1>
			<p>
				Disabled by default outside the isolated synthetic environment. Create
				and read authority are independent. This tool cannot reopen a room or
				cancel a booking.
			</p>
			<Card title="Choose a venue and room">
				<Button disabled={frozen} onClick={findVenues}>
					Find venues by name
				</Button>
				<SelectField
					id="block-venue-name"
					label="Venue"
					value={selectedVenue ? venue : ""}
					disabled={frozen}
					onChange={(e) =>
						scope(() => {
							setVenue(e.target.value);
							setRoom("");
							setRooms([]);
						})
					}
				>
					<option value="">Choose a venue</option>
					{venues.map((v) => (
						<option key={v.venueId} value={v.venueId}>
							{v.displayName}
						</option>
					))}
				</SelectField>
				<Button disabled={frozen || !blockUuid.test(venue)} onClick={findRooms}>
					Find rooms
				</Button>
				<SelectField
					id="block-room-name"
					label="Room"
					value={selectedRoom ? room : confirmedReference}
					disabled={frozen}
					onChange={(e) => scope(() => setRoom(e.target.value))}
				>
					<option value="">Choose a room</option>
					{confirmedReference && (
						<option value={confirmedReference}>
							Confirmed room reference: {confirmedReference}
						</option>
					)}
					{rooms.map((r) => (
						<option key={r.roomId} value={r.roomId}>
							{r.name} · up to {r.capacity} guests
						</option>
					))}
				</SelectField>
				{selectedRoom && (
					<p>
						Selected: {selectedVenue?.displayName ?? "Exact venue reference"} /{" "}
						{selectedRoom.name}. Catalogue capacity: {selectedRoom.capacity}.
						not a physical safety certification.
					</p>
				)}
				<p>
					Names come from the existing public catalogue. Server-side exact-venue
					permissions remain required.
				</p>
				<details open={!!errors.venue || !!errors.room}>
					<summary>Use exact venue and room references instead</summary>
					<Field
						id="block-venue"
						label="Venue reference (UUID)"
						hint="Use an approved venue reference when name lookup is unavailable."
						value={venue}
						error={errors.venue}
						disabled={frozen}
						onChange={(e) =>
							scope(() => {
								setVenue(e.target.value);
								setRoom("");
								setRooms([]);
							})
						}
					/>
					<Field
						id="block-room"
						label="Room reference (UUID)"
						hint="The room must belong to this venue; the server verifies access."
						value={room}
						error={errors.room}
						disabled={frozen}
						onChange={(e) => scope(() => setRoom(e.target.value))}
					/>
				</details>
				<Button disabled={busy} onClick={() => load()}>
					Load room blocks
				</Button>
			</Card>
			<p role="status" aria-live="polite">
				{message}
			</p>
			<Card title="Choose when the room is unavailable">
				<p>
					{formTimezone
						? `Venue timezone: ${formTimezone}${formTimezone === "Asia/Colombo" ? " — Sri Lanka time" : ""}. These are venue-local dates and times, not your browser timezone.`
						: "Load room blocks first to confirm the venue timezone. No default timezone is assumed."}
				</p>
				{errors.context && <p role="alert">{errors.context}</p>}
				<div className="filter-grid">
					<Field
						id="block-start-date"
						label="Start date"
						type="date"
						value={startDate}
						error={errors.startDate}
						disabled={frozen || !list}
						onChange={(e) => setStartDate(e.target.value)}
					/>
					<Field
						id="block-start-time"
						label="Start time (24-hour)"
						type="time"
						step="60"
						value={startTime}
						error={errors.startTime}
						disabled={frozen || !list}
						onChange={(e) => setStartTime(e.target.value)}
					/>
					<Field
						id="block-end-date"
						label="End date"
						type="date"
						value={endDate}
						error={errors.endDate}
						disabled={frozen || !list}
						onChange={(e) => setEndDate(e.target.value)}
					/>
					<Field
						id="block-end-time"
						label="End time (24-hour)"
						type="time"
						step="60"
						value={endTime}
						error={errors.endTime}
						disabled={frozen || !list}
						onChange={(e) => setEndTime(e.target.value)}
					/>
				</div>
				{formTimezone &&
					validation.startAt &&
					validation.endAt &&
					!Object.keys(validation.errors).length && (
						<p>
							Review before saving:{" "}
							{blockLocalTime(validation.startAt, formTimezone)} –{" "}
							{blockLocalTime(validation.endAt, formTimezone)} ({formTimezone}).
						</p>
					)}
				<Button disabled={busy || (!list && !pending.current)} onClick={create}>
					{pending.current
						? "Retry original block command"
						: "Create room block"}
				</Button>
				<p>
					The exact interval is blocked. No cleaning extension or automatic
					safety clearance is added. Server validation remains authoritative.
				</p>
			</Card>
			{list && (
				<Card title="Saved room blocks">
					<p>
						Observed at {blockLocalTime(list.observedAt, list.timezone)}. Page{" "}
						{list.page + 1}.
					</p>
					{list.items.length === 0 && <p>No saved blocks on this page.</p>}
					<ul>
						{list.items.map((b) => (
							<li key={b.blockId}>
								{blockLocalTime(b.startAt, list.timezone)} –{" "}
								{blockLocalTime(b.endAt, list.timezone)}{" "}
								<Button disabled={busy} onClick={() => inspect(b.blockId)}>
									Inspect block
								</Button>
							</li>
						))}
					</ul>
					<Button
						disabled={busy || list.page === 0}
						onClick={() => load(list.page - 1)}
					>
						Previous page
					</Button>
					<Button
						disabled={busy || list.items.length < list.size}
						onClick={() => load(list.page + 1)}
					>
						Next page
					</Button>
				</Card>
			)}
			{detail && (
				<SavedBlockDetail
					block={detail.block}
					timezone={detail.timezone}
					rooms={rooms}
				/>
			)}
		</>
	);
}

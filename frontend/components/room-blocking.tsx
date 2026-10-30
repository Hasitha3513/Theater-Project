"use client";
import { useEffect, useRef, useState } from "react";
import {
	blockPath,
	parseBlock,
	parseBlockList,
	type RoomBlock,
	type RoomBlockList,
} from "../lib/room-blocks";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field } from "./primitives";
export function RoomBlocking() {
	const [venue, setVenue] = useState(""),
		[room, setRoom] = useState(""),
		[start, setStart] = useState(""),
		[end, setEnd] = useState("");
	const [list, setList] = useState<RoomBlockList>(),
		[detail, setDetail] = useState<RoomBlock>(),
		[busy, setBusy] = useState(false),
		[message, setMessage] = useState(
			"Local synthetic only. Load current room context before creating a block.",
		);
	const generation = useRef(0),
		pending = useRef<{ path: string; body: string } | undefined>(undefined);
	useEffect(() => {
		const clear = () => {
			generation.current++;
			setList(undefined);
			setDetail(undefined);
			pending.current = undefined;
			setBusy(false);
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
		change();
	}
	async function load(page = 0) {
		const turn = ++generation.current;
		setBusy(true);
		setDetail(undefined);
		try {
			const data = parseBlockList(
				await roomRequest(`${blockPath(venue, room)}?page=${page}&size=24`),
				venue,
				room,
			);
			if (turn !== generation.current) return;
			setList(data);
			setMessage(
				"Current inventory context loaded. A block is not physical readiness evidence.",
			);
		} catch {
			if (turn === generation.current) {
				setList(undefined);
				setMessage(
					"Room Blocking unavailable or access denied. Check current session, exact scope and enabled test configuration.",
				);
			}
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function create() {
		const turn = ++generation.current;
		setBusy(true);
		try {
			if (!pending.current) {
				if (!list) throw Error();
				if (
					!/(Z|[+-]\d\d:\d\d)$/.test(start) ||
					!/(Z|[+-]\d\d:\d\d)$/.test(end) ||
					!Number.isFinite(Date.parse(start)) ||
					!Number.isFinite(Date.parse(end)) ||
					Date.parse(start) >= Date.parse(end)
				)
					throw Error();
				pending.current = {
					path: blockPath(venue, room),
					body: JSON.stringify({
						idempotencyKey: crypto.randomUUID(),
						startAt: start,
						endAt: end,
						expectedConfiguration: list.configuration,
					}),
				};
			}
			const data = parseBlock(
				await roomRequest(pending.current.path, {
					method: "POST",
					body: pending.current.body,
				}),
				venue,
				room,
			);
			if (turn !== generation.current) return;
			pending.current = undefined;
			setDetail(data);
			setList(undefined);
			setMessage(
				"Room block saved. No existing booking was changed. Reload blocks to inspect current state.",
			);
		} catch (error) {
			if (turn === generation.current) {
				if (
					error instanceof RoomRequestError &&
					[400, 401, 403, 404, 409, 413].includes(error.status)
				) {
					pending.current = undefined;
					setList(undefined);
				}
				setMessage(
					pending.current
						? "Not confirmed: denied, conflict or interrupted response. Retry uses the exact original command; reload blocks to inspect authoritative state."
						: "Request rejected or invalid. Check scope, interval and current configuration; reload before creating a new command.",
				);
			}
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	async function inspect(id: string) {
		const turn = ++generation.current;
		setBusy(true);
		setDetail(undefined);
		try {
			const data = parseBlock(
				await roomRequest(`${blockPath(venue, room)}/${id}`),
				venue,
				room,
			);
			if (turn === generation.current) setDetail(data);
		} catch {
			if (turn === generation.current)
				setMessage("Block detail unavailable or access denied.");
		} finally {
			if (turn === generation.current) setBusy(false);
		}
	}
	return (
		<>
			<h1>Room Blocking</h1>
			<p>
				Disabled by default outside the isolated synthetic environment. Create
				and read authority are independent. This tool cannot reopen a room or
				cancel a booking.
			</p>
			<Card title="Block an exact room">
				<Field
					id="block-venue"
					label="Venue reference"
					value={venue}
					disabled={busy || !!pending.current}
					onChange={(e) => scope(() => setVenue(e.target.value))}
				/>
				<Field
					id="block-room"
					label="Room reference"
					value={room}
					disabled={busy || !!pending.current}
					onChange={(e) => scope(() => setRoom(e.target.value))}
				/>
				<Button disabled={busy} onClick={() => load()}>
					Load room blocks
				</Button>
				<p role="status" aria-live="polite">
					{message}
				</p>
				{list && (
					<p>
						Venue timezone: {list.timezone}. Observed at {list.observedAt}. Page{" "}
						{list.page + 1}.
					</p>
				)}
				<Field
					id="block-start"
					label="Block start (ISO timestamp with offset)"
					placeholder="2030-01-01T10:00:00+05:30"
					value={start}
					disabled={busy || !!pending.current}
					onChange={(e) => setStart(e.target.value)}
				/>
				<Field
					id="block-end"
					label="Block end (ISO timestamp with offset)"
					placeholder="2030-01-01T12:00:00+05:30"
					value={end}
					disabled={busy || !!pending.current}
					onChange={(e) => setEnd(e.target.value)}
				/>
				<Button disabled={busy || (!list && !pending.current)} onClick={create}>
					{pending.current
						? "Retry original block command"
						: "Create room block"}
				</Button>
				<p>
					The exact interval is blocked. No cleaning extension or automatic
					safety clearance is added.
				</p>
			</Card>
			{list && (
				<Card title="Saved room blocks">
					{list.items.length === 0 && <p>No saved blocks on this page.</p>}
					<ul>
						{list.items.map((b) => (
							<li key={b.blockId}>
								{b.startAt} – {b.endAt}{" "}
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
				<Card title="Saved block detail">
					<p>
						{detail.startAt} – {detail.endAt}
					</p>
					<p>Revision {detail.revision}; synthetic inventory evidence only.</p>
				</Card>
			)}
		</>
	);
}

"use client";
import { useEffect, useRef, useState } from "react";
import {
	calendarReasons,
	loadCalendar,
	shiftCalendarDate,
	type CustomerCalendar,
	type CalendarItem,
} from "../lib/customer-calendar";
import { venueTime } from "../lib/inventory";
import { stateFor } from "../lib/rooms";
import { Button, Field, SelectField, StateMessage } from "./primitives";
export function CustomerAvailabilityCalendar({
	roomId,
	venueId,
	timezone,
	disabled,
	onChoose,
}: {
	roomId: string;
	venueId: string;
	timezone: string;
	disabled: boolean;
	onChoose: (
		value: { local: string; duration: string; guests: string } | undefined,
	) => void;
}) {
	const [date, setDate] = useState(""),
		[duration, setDuration] = useState(""),
		[guests, setGuests] = useState(""),
		[data, setData] = useState<CustomerCalendar>(),
		[selected, setSelected] = useState(""),
		[busy, setBusy] = useState(false),
		[error, setError] = useState<unknown>();
	const epoch = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		choose = useRef(onChoose);
	choose.current = onChoose;
	function clear() {
		epoch.current++;
		controller.current?.abort();
		setData(undefined);
		setSelected("");
		setBusy(false);
		setError(undefined);
		choose.current(undefined);
	}
	useEffect(() => {
		const hide = () => {
			epoch.current++;
			controller.current?.abort();
			setData(undefined);
			setSelected("");
			setBusy(false);
			setError(undefined);
			choose.current(undefined);
		};
		const visibility = () => {
			if (document.visibilityState === "hidden") hide();
		};
		window.addEventListener("cinenest-session-change", hide);
		window.addEventListener("pagehide", hide);
		document.addEventListener("visibilitychange", visibility);
		return () => {
			epoch.current++;
			controller.current?.abort();
			window.removeEventListener("cinenest-session-change", hide);
			window.removeEventListener("pagehide", hide);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, []);
	async function load() {
		clear();
		const token = epoch.current,
			c = new AbortController();
		controller.current = c;
		setBusy(true);
		try {
			const next = await loadCalendar(
				{
					roomId,
					venueId,
					date,
					durationMinutes: Number(duration),
					guests: Number(guests),
				},
				AbortSignal.any([c.signal, AbortSignal.timeout(15000)]),
			);
			if (token === epoch.current) setData(next);
		} catch (e) {
			if (token === epoch.current) setError(e);
		} finally {
			if (token === epoch.current) setBusy(false);
		}
	}
	const item = data?.items.find((i) => i.startLocal === selected),
		available = data?.items.filter((i) => i.available === true).length ?? 0,
		unknown = data?.items.filter((i) => i.available === null).length ?? 0;
	return (
		<section
			className="customer-calendar"
			aria-label="Customer availability calendar"
		>
			<div className="calendar-heading">
				<div>
					<p className="eyebrow">Plan your visit</p>
					<h3>Find a session time</h3>
				</div>
				<p>Venue local time · {data?.timezone ?? timezone}</p>
			</div>
			<p>
				Read-only inventory view for this room. Available times are advisory,
				not reservations or physical readiness. Synthetic booking restrictions
				remain in effect.
			</p>
			<form
				onSubmit={(e) => {
					e.preventDefault();
					void load();
				}}
			>
				<fieldset disabled={disabled}>
					<legend>Choose a date, duration and party size</legend>
					<div className="filter-grid">
						<Field
							id={`calendar-date-${roomId}`}
							label="Calendar date (venue local)"
							type="date"
							required
							value={date}
							onChange={(e) => {
								clear();
								setDate(e.target.value);
							}}
						/>
						<Field
							id={`calendar-duration-${roomId}`}
							label="Calendar duration (minutes)"
							hint="Enter the requested elapsed minutes; the server checks the applicable minimum."
							type="number"
							min="1"
							max="1440"
							required
							value={duration}
							onChange={(e) => {
								clear();
								setDuration(e.target.value);
							}}
						/>
						<Field
							id={`calendar-guests-${roomId}`}
							label="Calendar guests"
							type="number"
							min="1"
							required
							value={guests}
							onChange={(e) => {
								clear();
								setGuests(e.target.value);
							}}
						/>
					</div>
					<div className="toolbar">
						<Button
							variant="secondary"
							disabled={!date}
							onClick={() => {
								clear();
								setDate(shiftCalendarDate(date, -1));
							}}
						>
							Previous calendar day
						</Button>
						<Button
							variant="secondary"
							disabled={!date}
							onClick={() => {
								clear();
								setDate(shiftCalendarDate(date, 1));
							}}
						>
							Next calendar day
						</Button>
						<Button
							type="submit"
							aria-disabled={busy}
							onClick={(e) => {
								if (busy) e.preventDefault();
							}}
						>
							Load availability calendar
						</Button>
					</div>
				</fieldset>
			</form>
			<p className="calendar-legend">
				Available: server-observed free inventory · Unavailable: blocked or
				outside rules · Uncertain: cannot verify availability
			</p>
			{busy && (
				<StateMessage kind="loading">
					<p>Checking this date in the venue timezone.</p>
				</StateMessage>
			)}
			{error !== undefined && (
				<StateMessage
					kind={stateFor(error)}
					recovery={
						<Button disabled={disabled} onClick={() => void load()}>
							Retry calendar
						</Button>
					}
				>
					<p>
						Availability was cleared. Check your selections or sign in again,
						then reload.
					</p>
				</StateMessage>
			)}
			{data && (
				<div className="calendar-results">
					<p role="status">
						{available} available · {data.items.length - available - unknown}{" "}
						unavailable · {unknown} uncertain
					</p>
					<p className="muted">
						Observed {venueTime(data.observedAt, data.timezone)} ·{" "}
						{data.timezone}. Refresh before continuing.
					</p>
					{!available && <p>No available starts for this request.</p>}
					<SelectField
						id={`calendar-time-${roomId}`}
						label={`Session start (${data.timezone})`}
						disabled={disabled || !available}
						value={selected}
						onChange={(e) => {
							setSelected(e.target.value);
							choose.current(undefined);
						}}
					>
						<option value="">Choose an available start</option>
						{data.items.map((i) => (
							<option
								key={i.startLocal}
								value={i.startLocal}
								disabled={i.available !== true}
							>
								{i.startLocal.slice(11)} ·{" "}
								{i.available === true
									? "Available"
									: i.available === null
										? "Uncertain"
										: "Unavailable"}
							</option>
						))}
					</SelectField>
					{item && <CalendarSelection item={item} data={data} />}
					<Button
						disabled={disabled || !item || item.available !== true}
						onClick={() => {
							if (item?.available === true)
								choose.current({
									local: item.startLocal,
									duration: String(data.durationMinutes),
									guests: String(data.guests),
								});
						}}
					>
						Use selected time
					</Button>
					<p>
						This only prepares the existing inventory-check form below. Explicit
						inventory checking and hold creation revalidate current rules; no
						hold or quotation is created here.
					</p>
					<details>
						<summary>Unavailable and uncertain time details</summary>
						<ul className="calendar-reasons">
							{Object.entries(calendarReasons).map(([reason, label]) => {
								const count = data.items.filter((i) =>
									i.reasons.includes(reason as keyof typeof calendarReasons),
								).length;
								return count ? (
									<li key={reason}>
										{label}: {count} starts
									</li>
								) : null;
							})}
						</ul>
						<p>
							No other customers’ reservations are disclosed. An empty or
							unavailable result does not establish room cleanliness, safety or
							admission.
						</p>
					</details>
				</div>
			)}
		</section>
	);
}
function CalendarSelection({
	item,
	data,
}: {
	item: CalendarItem;
	data: CustomerCalendar;
}) {
	return (
		<dl className="account-summary calendar-selection">
			<div>
				<dt>Selected session</dt>
				<dd>
					{item.sessionStart && venueTime(item.sessionStart, data.timezone)} –{" "}
					{item.sessionEnd && venueTime(item.sessionEnd, data.timezone)}
				</dd>
			</div>
			<div>
				<dt>Party & duration</dt>
				<dd>
					{data.guests} guests · {data.durationMinutes} minutes
				</dd>
			</div>
			<div>
				<dt>Inventory blocked until</dt>
				<dd>
					{item.blockedUntil && venueTime(item.blockedUntil, data.timezone)} (
					{data.timezone})
				</dd>
			</div>
		</dl>
	);
}

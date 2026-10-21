import { roomRequest } from "./rooms";
export type CalendarContext = {
	roomId: string;
	venueId: string;
	date: string;
	durationMinutes: number;
	guests: number;
};
export const calendarReasons = {
	INVALID_LOCAL_TIME: "Local time unavailable (clock change)",
	OUTSIDE_OPERATING_HOURS: "Outside customer hours",
	MINIMUM_DURATION: "Below the applicable minimum duration",
	PAST_START: "Start time has passed",
	PENDING_RECONCILIATION: "Availability awaiting reconciliation",
	MAINTENANCE_BLOCK: "Inventory blocked for maintenance",
	UNAVAILABLE: "Unavailable inventory",
	TEMPORARILY_HELD: "Temporarily held inventory",
} as const;
export type CalendarItem = {
	startLocal: string;
	sessionStart: string | null;
	sessionEnd: string | null;
	blockedUntil: string | null;
	available: boolean | null;
	reasons: (keyof typeof calendarReasons)[];
};
export type CustomerCalendar = CalendarContext & {
	timezone: string;
	configuration: string;
	observedAt: string;
	evidenceLevel: "INVENTORY_ONLY";
	complete: true;
	items: CalendarItem[];
};
export function calendarDate(value: string) {
	return (
		/^\d{4}-\d{2}-\d{2}$/.test(value) &&
		Number.isFinite(Date.parse(`${value}T00:00:00Z`)) &&
		new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
	);
}
export function calendarPath(c: CalendarContext) {
	if (
		!/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(
			c.roomId,
		) ||
		!calendarDate(c.date) ||
		!Number.isSafeInteger(c.durationMinutes) ||
		c.durationMinutes < 1 ||
		c.durationMinutes > 1440 ||
		!Number.isSafeInteger(c.guests) ||
		c.guests < 1
	)
		throw Error("Check the date, duration and guest count.");
	return `/api/rooms/${c.roomId}/availability/calendar?${new URLSearchParams({ date: c.date, durationMinutes: String(c.durationMinutes), guests: String(c.guests) })}`;
}
export function validateCalendar(
	value: unknown,
	c: CalendarContext,
): CustomerCalendar {
	const bad = () => {
		throw Error("Availability could not be verified. Reload the calendar.");
	};
	if (!value || typeof value !== "object") return bad();
	const d = value as CustomerCalendar;
	if (
		d.roomId !== c.roomId ||
		d.venueId !== c.venueId ||
		d.date !== c.date ||
		d.durationMinutes !== c.durationMinutes ||
		d.guests !== c.guests ||
		d.evidenceLevel !== "INVENTORY_ONLY" ||
		d.complete !== true ||
		typeof d.configuration !== "string" ||
		!d.configuration ||
		typeof d.timezone !== "string" ||
		!Number.isFinite(Date.parse(d.observedAt)) ||
		!Array.isArray(d.items) ||
		(d.items.length !== 1440 && d.items.length !== 0)
	)
		return bad();
	try {
		new Intl.DateTimeFormat("en-GB", { timeZone: d.timezone }).format();
	} catch {
		return bad();
	}
	for (const [minute, i] of d.items.entries()) {
		const local = `${c.date}T${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
		if (
			!i ||
			i.startLocal !== local ||
			![true, false, null].includes(i.available) ||
			!Array.isArray(i.reasons) ||
			i.reasons.some((r) => !Object.hasOwn(calendarReasons, r)) ||
			i.reasons.length > 8
		)
			return bad();
		for (const k of ["sessionStart", "sessionEnd", "blockedUntil"] as const)
			if (
				i[k] !== null &&
				(typeof i[k] !== "string" ||
					!Number.isFinite(Date.parse(i[k] as string)))
			)
				return bad();
		if (
			i.available === true &&
			(i.reasons.length || !i.sessionStart || !i.sessionEnd || !i.blockedUntil)
		)
			return bad();
		if (i.available !== true && !i.reasons.length) return bad();
		if (
			i.sessionStart &&
			i.sessionEnd &&
			i.blockedUntil &&
			(Date.parse(i.sessionEnd) <= Date.parse(i.sessionStart) ||
				Date.parse(i.blockedUntil) < Date.parse(i.sessionEnd))
		)
			return bad();
	}
	return d;
}
export async function loadCalendar(c: CalendarContext, signal: AbortSignal) {
	return validateCalendar(await roomRequest(calendarPath(c), { signal }), c);
}
export function shiftCalendarDate(date: string, days: number) {
	if (!calendarDate(date)) return date;
	const d = new Date(`${date}T00:00:00Z`);
	d.setUTCDate(d.getUTCDate() + days);
	const next = d.toISOString().slice(0, 10);
	return calendarDate(next) ? next : date;
}

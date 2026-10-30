export const blockUuid =
	/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type RoomBlock = {
	blockId: string;
	venueId: string;
	roomId: string;
	startAt: string;
	endAt: string;
	revision: number;
	createdAt: string;
	synthetic: true;
};
export type RoomBlockList = {
	items: RoomBlock[];
	page: number;
	size: number;
	configuration: string;
	timezone: string;
	observedAt: string;
	synthetic: true;
};
export function blockPath(venue: string, room: string) {
	if (!blockUuid.test(venue) || !blockUuid.test(room))
		throw Error("Enter exact venue and room references");
	return `/api/admin/venues/${venue}/rooms/${room}/blocks`;
}
function object(value: unknown): Record<string, unknown> {
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Invalid Room Blocking response");
	return value as Record<string, unknown>;
}
function time(value: unknown): string {
	if (
		typeof value !== "string" ||
		value.length > 40 ||
		!/(Z|[+-]\d\d:\d\d)$/.test(value) ||
		!Number.isFinite(Date.parse(value))
	)
		throw Error("Invalid timestamp");
	return value;
}
export function parseBlock(
	value: unknown,
	venue: string,
	room: string,
): RoomBlock {
	const b = object(value);
	if (
		typeof b.blockId !== "string" ||
		!blockUuid.test(b.blockId) ||
		b.venueId !== venue ||
		b.roomId !== room ||
		b.revision !== 0 ||
		b.synthetic !== true
	)
		throw Error("Invalid block scope");
	const startAt = time(b.startAt),
		endAt = time(b.endAt);
	if (Date.parse(startAt) >= Date.parse(endAt))
		throw Error("Invalid block interval");
	return {
		blockId: b.blockId,
		venueId: venue,
		roomId: room,
		startAt,
		endAt,
		revision: 0,
		createdAt: time(b.createdAt),
		synthetic: true,
	};
}
export function parseBlockList(
	value: unknown,
	venue: string,
	room: string,
): RoomBlockList {
	const b = object(value);
	if (
		!Array.isArray(b.items) ||
		b.items.length > 100 ||
		!Number.isSafeInteger(b.page) ||
		Number(b.page) < 0 ||
		!Number.isSafeInteger(b.size) ||
		Number(b.size) < 1 ||
		Number(b.size) > 100 ||
		typeof b.configuration !== "string" ||
		!b.configuration ||
		b.configuration.length > 16384 ||
		typeof b.timezone !== "string" ||
		b.timezone.length > 100 ||
		b.synthetic !== true
	)
		throw Error("Invalid block collection");
	new Intl.DateTimeFormat("en", { timeZone: b.timezone });
	return {
		items: b.items.map((x) => parseBlock(x, venue, room)),
		page: Number(b.page),
		size: Number(b.size),
		configuration: b.configuration,
		timezone: b.timezone,
		observedAt: time(b.observedAt),
		synthetic: true,
	};
}

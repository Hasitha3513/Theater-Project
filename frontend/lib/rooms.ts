import { stateForStatus } from "./state-presentation";
export type WindowConfig = {
	weekday: number;
	openingMinute: number;
	closingMinute: number;
};
export type Facility = { code: string; label: string; sortOrder: number };
export type Venue = {
	venueId: string;
	code: string;
	displayName: string;
	timezone: string;
	windows: WindowConfig[];
	cleaningBufferMinutes?: number | null;
	postCloseCleaningApproved?: boolean;
	operationalApprovalReference?: string | null;
	enabled?: boolean;
	version: number;
	configured?: boolean;
	permittedActions?: string[];
};
export type Room = {
	roomId: string;
	venueId: string;
	code: string;
	name: string;
	description: string;
	capacity: number;
	enabled?: boolean;
	version?: number;
	configured?: boolean;
	facilities: Facility[];
	images: {
		imageId: string;
		assetPath: string;
		altText: string;
		sortOrder: number;
	}[];
	permittedActions?: string[];
	venue?: Venue;
};
export type Page = {
	permittedActions?: string[];
	items: Room[];
	total: number;
	page: number;
	size: number;
};
export class RoomRequestError extends Error {
	constructor(
		public status: number,
		message: string,
	) {
		super(message);
	}
}
export async function roomRequest<T>(
	path: string,
	init?: RequestInit,
): Promise<T> {
	const headers = new Headers(init?.headers);
	if (init?.method && init.method !== "GET") {
		const csrf = await fetch("/api/session/csrf", { cache: "no-store" });
		if (!csrf.ok)
			throw new RoomRequestError(
				csrf.status,
				"Session or CSRF token unavailable",
			);
		const token = await csrf.json();
		headers.set("X-CSRF-TOKEN", token.token);
		headers.set("Content-Type", "application/json");
	}
	const response = await fetch(path, { ...init, headers, cache: "no-store" });
	if (!response.ok) {
		let message = "Request failed; please retry";
		try {
			const body = await response.json();
			if (typeof body.message === "string") message = body.message;
		} catch {}
		throw new RoomRequestError(response.status, message);
	}
	return response.json();
}
export function queryRooms(
	venueId: string,
	q: string,
	minimum: string,
	facility: string,
	page: number,
	enabled: string,
) {
	const query = new URLSearchParams({
		q,
		minimumCapacity: minimum || "0",
		facility,
		page: String(page),
		size: "24",
	});
	if (venueId) query.set("venueId", venueId);
	if (enabled) query.set("enabled", enabled);
	return query.toString();
}
export function stateFor(error: unknown) {
	if (error instanceof RoomRequestError) return stateForStatus(error.status);
	if (
		error instanceof TypeError ||
		(error instanceof DOMException && error.name === "TimeoutError")
	)
		return "unavailable";
	return "error";
}

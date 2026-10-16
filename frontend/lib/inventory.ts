// Presentation only: PostgreSQL owns expiry, eligibility and interval decisions.
export type Candidate = {
	roomId: string;
	timezone: string;
	sessionStart: string;
	sessionEnd: string;
	bufferMinutes: number;
	blockedUntil: string;
	configuration: string;
	serverNow: string;
	available: boolean;
	evidenceLevel: "INVENTORY_ONLY";
};
export type Hold = Omit<Candidate, "configuration" | "available"> & {
	holdId: string;
	createdAt: string;
	expiresAt: string;
	state: "ACTIVE" | "EXPIRED" | "RELEASED" | "INVALIDATED";
	revision: number;
};
export function remainingSeconds(hold: Hold, elapsedMs: number) {
	if (hold.state !== "ACTIVE") return 0;
	return Math.max(
		0,
		Math.ceil(
			(Date.parse(hold.expiresAt) - Date.parse(hold.serverNow) - elapsedMs) /
				1000,
		),
	);
}
export function validateHold(value: unknown): Hold {
	if (!value || typeof value !== "object")
		throw new Error("Invalid hold response");
	const h = value as Hold;
	if (
		typeof h.holdId !== "string" ||
		typeof h.timezone !== "string" ||
		![h.sessionStart, h.sessionEnd, h.blockedUntil, h.createdAt].every(
			(v) => typeof v === "string" && Number.isFinite(Date.parse(v)),
		) ||
		!["ACTIVE", "EXPIRED", "RELEASED", "INVALIDATED"].includes(h.state) ||
		!Number.isSafeInteger(h.revision) ||
		h.revision < 0 ||
		!Number.isFinite(Date.parse(h.expiresAt)) ||
		!Number.isFinite(Date.parse(h.serverNow)) ||
		h.evidenceLevel !== "INVENTORY_ONLY"
	)
		throw new Error("Invalid hold response");
	try {
		venueTime(h.sessionStart, h.timezone);
	} catch {
		throw new Error("Invalid hold timezone");
	}
	return h;
}

export function venueTime(value: string, timezone: string) {
	return new Intl.DateTimeFormat("en-GB", {
		timeZone: timezone,
		dateStyle: "medium",
		timeStyle: "short",
		hourCycle: "h23",
	}).format(new Date(value));
}

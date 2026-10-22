export { context, account } from "./booking-confirmations-fixtures.mjs";
import { context } from "./booking-confirmations-fixtures.mjs";
export const fixture = () => ({
	metricId: "CN-M-POPULAR-TIME-SLOTS",
	metricVersion: "SYNTHETIC_POPULAR_SESSION_STARTS_V1",
	bandDefinitionVersion: "LOCAL_HOURLY_24_V1",
	venueId: context.venue,
	roomId: null,
	from: context.from,
	to: context.to,
	timezone: "Asia/Colombo",
	observedAt: "2026-10-22T00:00:00Z",
	revisionBasis: "LATEST_COMMITTED_PURCHASED_REVISION",
	cohortBasis: "sessionStart",
	synthetic: true,
	complete: true,
	total: "3",
	buckets: Array.from({ length: 24 }, (_, i) => ({
		bandId: `H${String(i).padStart(2, "0")}`,
		startHour: i,
		endHour: i + 1,
		count: i === 18 ? "2" : i === 20 ? "1" : "0",
	})),
});

import { displayMoney } from "../lib/packages";

type Kind = "package" | "room" | "venue";
type RecordValue = Record<string, unknown>;
type FieldSpec = readonly [
	string,
	string,
	(
		| "text"
		| "number"
		| "money"
		| "boolean"
		| "references"
		| "options"
		| "facilities"
		| "images"
		| "windows"
	),
];
// Deliberately closed presentation vocabulary. Unknown keys never become UI or inputs.
const common: FieldSpec[] = [
	["code", "Code", "text"],
	["name", "Name", "text"],
	["description", "Description", "text"],
];
export const configurationFields: Record<Kind, readonly FieldSpec[]> = {
	package: [
		...common,
		["revision", "Revision", "number"],
		["status", "State", "text"],
		["currency", "Currency", "text"],
		["basePriceMinor", "Base price (LKR)", "money"],
		["includedMinutes", "Included customer minutes", "number"],
		["minimumMinutes", "Minimum minutes", "number"],
		["maximumMinutes", "Maximum minutes", "number"],
		["includedGuests", "Included guests", "number"],
		["maximumGuests", "Maximum guests", "number"],
		["extraGuestPriceMinor", "Additional guest charge (LKR)", "money"],
		["extensionsEnabled", "Whole-unit extensions", "boolean"],
		["extensionStepMinutes", "Extension unit (minutes)", "number"],
		["extensionPriceMinor", "Extension unit price (LKR)", "money"],
		["venueWide", "All configured venue rooms", "boolean"],
		["roomIds", "Room references", "references"],
		["options", "Versioned service references", "options"],
	],
	room: [
		...common,
		["version", "Configuration version", "number"],
		["capacity", "Capacity", "number"],
		["enabled", "Catalog enabled", "boolean"],
		["configured", "Venue configured", "boolean"],
		["facilities", "Recorded facilities", "facilities"],
		["images", "Recorded images", "images"],
	],
	venue: [
		["code", "Code", "text"],
		["displayName", "Venue name", "text"],
		["version", "Configuration version", "number"],
		["timezone", "Venue timezone", "text"],
		["enabled", "Catalog enabled", "boolean"],
		["cleaningBufferMinutes", "Cleaning/reset buffer (minutes)", "number"],
		[
			"postCloseCleaningApproved",
			"Post-close cleaning approval recorded",
			"boolean",
		],
		["operationalApprovalReference", "Operational approval reference", "text"],
		["windows", "Customer operating windows", "windows"],
	],
};
const record = (value: unknown): RecordValue =>
	value && typeof value === "object" && !Array.isArray(value)
		? (value as RecordValue)
		: {};
const text = (value: unknown) =>
	typeof value === "string" ? value : "Not supplied";
function clock(value: unknown) {
	return typeof value === "number" &&
		Number.isInteger(value) &&
		value >= 0 &&
		value <= 1440
		? `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`
		: "Not supplied";
}
export function configurationValue(value: unknown, type: FieldSpec[2]): string {
	if (value === null) return "Not configured";
	if (type === "text") return text(value);
	if (type === "number")
		return typeof value === "number" &&
			Number.isSafeInteger(value) &&
			value >= 0
			? String(value)
			: "Not supplied";
	if (type === "boolean")
		return value === true ? "Yes" : value === false ? "No" : "Not supplied";
	if (type === "money")
		return typeof value === "string" && /^(0|[1-9]\d*)$/.test(value)
			? displayMoney(value)
			: "Not supplied";
	if (!Array.isArray(value)) return "Not supplied";
	if (!value.length) return "None recorded";
	return value
		.map((item) => {
			if (type === "references") return text(item);
			const r = record(item);
			if (type === "options")
				return `Service ${text(r.serviceId)} · rate ${text(r.rateVersionId)} · ${text(r.mode)}`;
			if (type === "facilities") return `${text(r.label)} (${text(r.code)})`;
			if (type === "images") return `${text(r.altText)} · ${text(r.assetPath)}`;
			const days = [
				"Monday",
				"Tuesday",
				"Wednesday",
				"Thursday",
				"Friday",
				"Saturday",
				"Sunday",
			];
			return `${typeof r.weekday === "number" ? (days[r.weekday - 1] ?? "Unknown day") : "Unknown day"}: ${clock(r.openingMinute)}–${clock(r.closingMinute)} (venue local)`;
		})
		.join("; ");
}
export function ConfigurationSummary({
	kind,
	value,
	previous,
	historical = false,
}: {
	kind: Kind;
	value: unknown;
	previous?: unknown;
	historical?: boolean;
}) {
	const current = record(value),
		before = record(previous);
	return (
		<section
			className="configuration-summary"
			aria-label={
				previous === undefined
					? "Saved configuration fields"
					: "Saved field comparison"
			}
		>
			{previous !== undefined && (
				<p>
					{historical
						? "Comparison between these loaded historical snapshots; neither is asserted to be the current live version. "
						: "Previously loaded snapshot compared with the latest saved snapshot. "}
					Unsaved form inputs are excluded. Fields marked Changed require
					review; no values are applied automatically.
				</p>
			)}
			<dl>
				{configurationFields[kind].map(([key, label, type]) => {
					const now = configurationValue(current[key], type),
						old = configurationValue(before[key], type),
						changed = previous !== undefined && old !== now;
					return (
						<div
							key={key}
							className={
								changed
									? "configuration-field is-changed"
									: "configuration-field"
							}
						>
							<dt>
								{label}
								{changed && (
									<strong className="configuration-change">Changed</strong>
								)}
							</dt>
							<dd>
								{previous !== undefined && (
									<span className="configuration-before">
										{historical
											? "Older loaded snapshot: "
											: "Previously loaded: "}
										{old}
									</span>
								)}
								<span>
									{previous !== undefined
										? historical
											? "This saved snapshot: "
											: "Latest saved: "
										: ""}
									{now}
								</span>
							</dd>
						</div>
					);
				})}
			</dl>
			<p className="muted">
				Read-only supported fields. Other response fields are not displayed or
				editable here. Configuration does not establish bookable availability,
				commercial entitlement or physical readiness.
			</p>
		</section>
	);
}

export const hardwareCategories = [
	"Smart Door Locks",
	"IoT Gateways",
	"Lighting Controllers",
	"Projectors / Smart TVs",
	"Audio Systems",
	"Air Conditioning Controllers",
	"Environmental Sensors",
	"Future Compatible Devices",
] as const;
export const lockCapabilities = [
	"SCHEDULED_PIN",
	"STATUS_READBACK",
	"REVOKE",
	"RECONCILE",
	"REENTRY",
] as const;
export type LockCapability = (typeof lockCapabilities)[number];
const idPattern = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
export function hardwareId(value: unknown): string {
	if (
		typeof value !== "string" ||
		!new RegExp(`^${idPattern}$`, "i").test(value)
	)
		throw Error("Enter an exact UUID reference.");
	return value.toLowerCase();
}
function object(value: unknown): Record<string, unknown> {
	if (!value || typeof value !== "object" || Array.isArray(value))
		throw Error("Invalid hardware response.");
	return value as Record<string, unknown>;
}
function integer(
	value: unknown,
	minimum = 0,
	maximum = Number.MAX_SAFE_INTEGER,
): number {
	if (
		typeof value !== "number" ||
		!Number.isSafeInteger(value) ||
		value < minimum ||
		value > maximum
	)
		throw Error("Invalid revision or page.");
	return value;
}
function text(value: unknown, maximum = 128): string {
	if (
		typeof value !== "string" ||
		!value.trim() ||
		[...value].length > maximum ||
		[...value].some((c) => {
			const n = c.codePointAt(0) ?? 0;
			return n < 32 || (n >= 127 && n <= 159);
		}) ||
		!value.isWellFormed()
	)
		throw Error("Invalid metadata text.");
	return value;
}
function optional(value: unknown): string | null {
	return value === null ? null : text(value);
}
function stamp(value: unknown): string {
	const result = text(value, 64);
	if (
		!/T.*(?:Z|[+-]\d{2}:\d{2})$/.test(result) ||
		!Number.isFinite(Date.parse(result))
	)
		throw Error("Invalid server timestamp.");
	return result;
}
function keys(value: Record<string, unknown>, expected: string[]) {
	if (Object.keys(value).sort().join("|") !== [...expected].sort().join("|"))
		throw Error("Unsupported hardware fields.");
}
function capabilities(value: unknown): LockCapability[] {
	if (
		!Array.isArray(value) ||
		value.length > 5 ||
		new Set(value).size !== value.length ||
		value.some((v) => !lockCapabilities.includes(v))
	)
		throw Error("Unsupported capability declaration.");
	return [...value].sort() as LockCapability[];
}
export function registryPath(
	venue: string,
	device?: string,
	revision?: number | "history",
): string {
	const root = `/api/admin/venues/${hardwareId(venue)}/iot/lock-devices`;
	if (device === undefined) {
		if (revision !== undefined) throw Error("Device required.");
		return root;
	}
	const path = `${root}/${hardwareId(device)}`;
	return revision === undefined
		? path
		: `${path}/revisions${revision === "history" ? "" : `/${integer(revision, 1)}`}`;
}
export function assignmentPath(
	venue: string,
	room: string,
	assignment?: string,
): string {
	return `/api/admin/venues/${hardwareId(venue)}/rooms/${hardwareId(room)}/lock-assignments${assignment !== undefined ? `/${hardwareId(assignment)}` : ""}`;
}
export type HardwareContext = {
	venue: string;
	room?: string;
	device?: string;
	revision?: number;
	collection: boolean;
	kind: "registry" | "history" | "assignment";
};
export function hardwareContext(path: string, method: string): HardwareContext {
	const registry = new RegExp(
		`^/api/admin/venues/(${idPattern})/iot/lock-devices(?:/(${idPattern})(?:/revisions(?:/([1-9][0-9]{0,15}))?)?)?$`,
	).exec(path);
	const assignment = new RegExp(
		`^/api/admin/venues/(${idPattern})/rooms/(${idPattern})/lock-assignments(?:/(${idPattern}))?$`,
	).exec(path);
	if (registry) {
		const collection = !registry[2],
			history = path.endsWith("/revisions");
		if (method !== "GET" && !(method === "POST" && collection))
			throw Error("Unsupported hardware method.");
		return {
			venue: registry[1],
			device: registry[2],
			revision: registry[3] ? integer(Number(registry[3]), 1) : undefined,
			collection: collection || history,
			kind: history ? "history" : "registry",
		};
	}
	if (assignment) {
		if (method !== "GET" && !(method === "POST" && !assignment[3]))
			throw Error("Unsupported hardware method.");
		return {
			venue: assignment[1],
			room: assignment[2],
			device: assignment[3],
			collection: !assignment[3],
			kind: "assignment",
		};
	}
	throw Error("Unsupported hardware route.");
}
export function hardwareRoute(path: string, method: string): boolean {
	try {
		hardwareContext(path, method);
		return true;
	} catch {
		return false;
	}
}
export type RegistryDevice = {
	deviceId: string;
	venueId: string;
	provider: "TUYA";
	revision: number;
	manufacturer: string;
	model: string;
	sku: string | null;
	hardwareRevision: string | null;
	firmwareRevision: string | null;
	declaredCapabilities: LockCapability[];
	declarationSource: string;
	registeredAt: string;
	recordedAt: string;
	qualification: "UNQUALIFIED";
	customerAccessEnabled: false;
	health: "UNKNOWN";
	registrationStatus: "REGISTERED";
	physicalAcceptance: "NOT_VERIFIED";
	providerOwnership: "UNVERIFIED";
	lastDeviceObservationAt: null;
	mode: "QUALIFICATION_ONLY";
};
export function parseDevice(
	value: unknown,
	venue: string,
	device?: string,
	revision?: number,
): RegistryDevice {
	const d = object(value);
	if (
		d.venueId !== hardwareId(venue) ||
		(device && d.deviceId !== hardwareId(device)) ||
		(revision !== undefined && d.revision !== revision) ||
		d.provider !== "TUYA" ||
		d.registrationStatus !== "REGISTERED" ||
		d.qualification !== "UNQUALIFIED" ||
		d.customerAccessEnabled !== false ||
		d.physicalAcceptance !== "NOT_VERIFIED" ||
		d.providerOwnership !== "UNVERIFIED" ||
		d.health !== "UNKNOWN" ||
		d.lastDeviceObservationAt !== null ||
		d.mode !== "QUALIFICATION_ONLY"
	)
		throw Error("Unqualified exact-venue device response required.");
	if (
		!["OPERATOR_DECLARATION", "VENDOR_DOCUMENT", "CONTRACT_FIXTURE"].includes(
			String(d.declarationSource),
		)
	)
		throw Error("Invalid declaration source.");
	return {
		deviceId: hardwareId(d.deviceId),
		venueId: hardwareId(d.venueId),
		provider: "TUYA",
		revision: integer(d.revision, 1),
		manufacturer: text(d.manufacturer),
		model: text(d.model),
		sku: optional(d.sku),
		hardwareRevision: optional(d.hardwareRevision),
		firmwareRevision: optional(d.firmwareRevision),
		declaredCapabilities: capabilities(d.declaredCapabilities),
		declarationSource: String(d.declarationSource),
		registeredAt: stamp(d.registeredAt),
		recordedAt: stamp(d.recordedAt),
		qualification: "UNQUALIFIED",
		customerAccessEnabled: false,
		health: "UNKNOWN",
		registrationStatus: "REGISTERED",
		physicalAcceptance: "NOT_VERIFIED",
		providerOwnership: "UNVERIFIED",
		lastDeviceObservationAt: null,
		mode: "QUALIFICATION_ONLY",
	};
}
export type Assignment = {
	assignmentId: string;
	venueId: string;
	roomId: string;
	deviceId: string;
	revision: 1;
	expectedRoomConfiguration: string;
	expectedDeviceMetadataRevision: number;
	createdAt: string;
	replayed: boolean;
	state: "UNQUALIFIED";
	customerAccessEnabled: false;
	physicalAcceptance: "NOT_VERIFIED";
	mode: "QUALIFICATION_ONLY";
};
export function parseAssignment(
	value: unknown,
	venue: string,
	room: string,
	assignment?: string,
): Assignment {
	const a = object(value);
	if (
		a.venueId !== hardwareId(venue) ||
		a.roomId !== hardwareId(room) ||
		(assignment && a.assignmentId !== hardwareId(assignment)) ||
		a.revision !== 1 ||
		a.state !== "UNQUALIFIED" ||
		a.customerAccessEnabled !== false ||
		a.physicalAcceptance !== "NOT_VERIFIED" ||
		a.mode !== "QUALIFICATION_ONLY" ||
		typeof a.replayed !== "boolean"
	)
		throw Error("Unqualified exact-room assignment required.");
	return {
		assignmentId: hardwareId(a.assignmentId),
		venueId: hardwareId(a.venueId),
		roomId: hardwareId(a.roomId),
		deviceId: hardwareId(a.deviceId),
		revision: 1,
		expectedRoomConfiguration: text(a.expectedRoomConfiguration, 16384),
		expectedDeviceMetadataRevision: integer(
			a.expectedDeviceMetadataRevision,
			1,
		),
		createdAt: stamp(a.createdAt),
		replayed: a.replayed,
		state: "UNQUALIFIED",
		customerAccessEnabled: false,
		physicalAcceptance: "NOT_VERIFIED",
		mode: "QUALIFICATION_ONLY",
	};
}
export type HardwarePage<T> = {
	items: T[];
	page: number;
	hasNext: boolean;
	observedAt: string;
	size: 25;
	mode: "QUALIFICATION_ONLY";
	customerAccessEnabled?: false;
	roomConfiguration?: string;
};
export function parseHardwarePage(
	value: unknown,
	context: HardwareContext,
): HardwarePage<RegistryDevice | Assignment> {
	const p = object(value);
	if (
		!Array.isArray(p.items) ||
		p.items.length > 25 ||
		p.size !== 25 ||
		typeof p.hasNext !== "boolean" ||
		p.mode !== "QUALIFICATION_ONLY"
	)
		throw Error("Invalid hardware page.");
	const items = p.items.map((v) =>
		context.kind === "assignment"
			? parseAssignment(v, context.venue, context.room as string)
			: parseDevice(v, context.venue, context.device),
	);
	const identities = items.map((v) =>
		"assignmentId" in v ? v.assignmentId : `${v.deviceId}:${v.revision}`,
	);
	if (new Set(identities).size !== identities.length)
		throw Error("Duplicate hardware records.");
	if (context.kind === "assignment" && p.customerAccessEnabled !== false)
		throw Error("Access must remain disabled.");
	return {
		items,
		page: integer(p.page, 0, 10000),
		hasNext: p.hasNext,
		observedAt: stamp(p.observedAt),
		size: 25,
		mode: "QUALIFICATION_ONLY",
		...(context.kind === "assignment"
			? {
					roomConfiguration: text(p.roomConfiguration, 16384),
					customerAccessEnabled: false as const,
				}
			: {}),
	};
}
export function parseRegistration(value: unknown, venue: string) {
	const r = object(value);
	if (
		r.venueId !== hardwareId(venue) ||
		r.registeredMetadataRevision !== 1 ||
		r.registrationStatus !== "REGISTERED" ||
		r.activationPerformed !== false ||
		typeof r.replayed !== "boolean"
	)
		throw Error("Invalid registration receipt.");
	return {
		deviceId: hardwareId(r.deviceId),
		venueId: hardwareId(r.venueId),
		registeredMetadataRevision: 1,
		registeredAt: stamp(r.registeredAt),
		registrationStatus: "REGISTERED",
		activationPerformed: false,
		replayed: r.replayed,
	};
}
export function validateRegistration(value: unknown): Record<string, unknown> {
	const r = object(value);
	keys(r, [
		"commandKey",
		"expectedRevision",
		"provider",
		"providerDeviceReference",
		"manufacturer",
		"model",
		"sku",
		"hardwareRevision",
		"firmwareRevision",
		"gatewayReference",
		"capabilityDeclaration",
	]);
	if (r.provider !== "TUYA" || r.expectedRevision !== 0)
		throw Error("Only initial synthetic TUYA registration is supported.");
	hardwareId(r.commandKey);
	const reference = (value: unknown) => {
		const x = object(value);
		keys(x, ["id", "version"]);
		hardwareId(x.id);
		hardwareId(x.version);
	};
	reference(r.providerDeviceReference);
	if (r.gatewayReference !== null) reference(r.gatewayReference);
	text(r.manufacturer);
	text(r.model);
	optional(r.sku);
	optional(r.hardwareRevision);
	optional(r.firmwareRevision);
	const d = object(r.capabilityDeclaration);
	keys(d, ["source", "sourceReference", "capabilities"]);
	if (
		!["OPERATOR_DECLARATION", "VENDOR_DOCUMENT", "CONTRACT_FIXTURE"].includes(
			String(d.source),
		)
	)
		throw Error("Invalid declaration source.");
	if (d.sourceReference !== null) hardwareId(d.sourceReference);
	else if (d.source === "VENDOR_DOCUMENT")
		throw Error("Document reference required.");
	capabilities(d.capabilities);
	return r;
}
export function validateAssignment(value: unknown): Record<string, unknown> {
	const a = object(value);
	keys(a, [
		"commandKey",
		"deviceId",
		"expectedAssignmentRevision",
		"expectedRoomConfiguration",
		"expectedDeviceMetadataRevision",
	]);
	hardwareId(a.commandKey);
	hardwareId(a.deviceId);
	if (a.expectedAssignmentRevision !== 0)
		throw Error("Only initial assignment is supported.");
	text(a.expectedRoomConfiguration, 16384);
	integer(a.expectedDeviceMetadataRevision, 1);
	return a;
}
export type RegistrationDraft = {
	manufacturer: string;
	model: string;
	sku: string;
	hardware: string;
	firmware: string;
	reference: string;
	referenceVersion: string;
	capabilities: LockCapability[];
};
export function registrationValues(draft: RegistrationDraft) {
	return {
		expectedRevision: 0,
		provider: "TUYA",
		providerDeviceReference: {
			id: hardwareId(draft.reference),
			version: hardwareId(draft.referenceVersion),
		},
		manufacturer: text(draft.manufacturer.trim()),
		model: text(draft.model.trim()),
		sku: draft.sku.trim() || null,
		hardwareRevision: draft.hardware.trim() || null,
		firmwareRevision: draft.firmware.trim() || null,
		gatewayReference: null,
		capabilityDeclaration: {
			source: "OPERATOR_DECLARATION",
			sourceReference: null,
			capabilities: capabilities(draft.capabilities),
		},
	};
}
function canonical(value: unknown): string {
	if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
	if (value && typeof value === "object")
		return `{${Object.entries(value)
			.sort(([a], [b]) => a.localeCompare(b))
			.map(([k, v]) => `${JSON.stringify(k)}:${canonical(v)}`)
			.join(",")}}`;
	return JSON.stringify(value);
}
/** A retry keeps the same path, identity and immutable payload; an explicit reset starts a new intent. */
export class HardwareCommand {
	private pending?: { path: string; fingerprint: string; body: string };
	private running = false;
	constructor(private id: () => string = () => crypto.randomUUID()) {}
	clear() {
		this.pending = undefined;
	}
	async send<T>(
		path: string,
		values: Record<string, unknown>,
		sender: (path: string, body: string) => Promise<T>,
	): Promise<T> {
		if (this.running) throw Error("A hardware command is already pending.");
		const c = hardwareContext(path, "POST"),
			fingerprint = canonical(values);
		if (
			this.pending &&
			(this.pending.path !== path || this.pending.fingerprint !== fingerprint)
		)
			throw Error(
				"Retry the unchanged command or explicitly start a new command.",
			);
		if (!this.pending) {
			const command = { ...values, commandKey: hardwareId(this.id()) };
			if (c.kind === "assignment") validateAssignment(command);
			else validateRegistration(command);
			this.pending = { path, fingerprint, body: JSON.stringify(command) };
		}
		this.running = true;
		try {
			return await sender(this.pending.path, this.pending.body);
		} finally {
			this.running = false;
		}
	}
}

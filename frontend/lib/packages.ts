import { roomRequest } from "./rooms";
export { RoomRequestError, stateFor } from "./rooms";
export const packageRequest = roomRequest;
export type Option = {
	serviceId: string;
	rateVersionId: string;
	mode: "INCLUDED" | "OPTIONAL";
};
export type Draft = {
	packageId: string;
	venueId: string;
	code: string;
	name: string;
	description: string;
	currency: "LKR";
	basePriceMinor: string;
	includedMinutes: number;
	minimumMinutes: number;
	maximumMinutes: number;
	extensionsEnabled: boolean;
	extensionStepMinutes: number | null;
	extensionPriceMinor: string | null;
	includedGuests: number;
	maximumGuests: number;
	extraGuestPriceMinor: string;
	venueWide: boolean;
	roomIds: string[];
	options: Option[];
	revision: number;
	status: "DRAFT";
	permittedActions?: string[];
};
export type OptionCatalog = {
	constraintStatus: string;
	constraints: null | {
		venueId: string;
		minimumMinutes: number;
		policyRevision: number;
		rooms: { roomId: string; capacity: number; facilities: string[] }[];
	};
	services: {
		serviceId: string;
		rateVersionId: string;
		code: string;
		label: string;
		revision: number;
		unitPriceMinor: string;
		requiredFacilityCode: string;
	}[];
};
export type Preview = {
	label: string;
	knownPackageComponentsMinor: string;
	taxStatus: string;
	feeStatus: string;
	options: {
		serviceId: string;
		label: string;
		mode: string;
		additionalChargeMinor: string;
		selected: boolean;
	}[];
};
export function parseMoney(raw: string) {
	if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(raw))
		throw new Error(
			"Use a nonnegative amount with at most two decimal places; no rounding is applied.",
		);
	const [whole, fraction = ""] = raw.split(".");
	const value = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
	if (value > 9223372036854775807n)
		throw new Error("Amount exceeds the supported exact range.");
	return value.toString();
}
export function displayMoney(raw: string) {
	const value = BigInt(raw);
	return `${value / 100n}.${String(value % 100n).padStart(2, "0")}`;
}
export function draftBody(d: Draft) {
	const {
		packageId: _id,
		venueId: _venue,
		revision: _revision,
		status: _status,
		permittedActions: _actions,
		...body
	} = d;
	return body;
}

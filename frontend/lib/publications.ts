// Presentation contract validation only; the backend owns pricing and eligibility rules.
export type PublishedSnapshot = {
	name: string;
	description: string;
	basePriceMinor: string;
	includedMinutes: number;
	includedGuests: number;
	options: { label: string; mode: string; additionalChargeMinor: string }[];
};
export type PublicPackage = PublishedSnapshot & {
	packageId: string;
	versionId: string;
};
export function catalogItems(value: unknown): PublicPackage[] {
	if (
		!value ||
		typeof value !== "object" ||
		!("items" in value) ||
		!Array.isArray(value.items) ||
		!("purchasable" in value) ||
		value.purchasable !== false
	)
		throw new Error("Invalid indicative catalog response");
	const minor = (v: unknown) =>
		typeof v === "string" &&
		/^\d+$/.test(v) &&
		v.length <= 19 &&
		BigInt(v) <= 9223372036854775807n;
	for (const p of value.items) {
		if (
			!p ||
			typeof p !== "object" ||
			typeof p.packageId !== "string" ||
			typeof p.versionId !== "string" ||
			typeof p.name !== "string" ||
			typeof p.description !== "string" ||
			!minor(p.basePriceMinor) ||
			!Number.isInteger(p.includedMinutes) ||
			!Number.isInteger(p.includedGuests) ||
			!Array.isArray(p.options)
		)
			throw new Error("Invalid indicative catalog item");
		for (const o of p.options)
			if (
				!o ||
				typeof o.label !== "string" ||
				!["INCLUDED", "OPTIONAL"].includes(o.mode) ||
				!minor(o.additionalChargeMinor)
			)
				throw new Error("Invalid indicative option");
	}
	return value.items;
}

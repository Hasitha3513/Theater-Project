export type CustomerProfile = {
	customerId: string;
	fullName: string;
	phone: string | null;
	verifiedEmail: string | null;
	emailVerificationStatus: "VERIFIED" | "PENDING" | "NOT_AVAILABLE";
	accountStatus: "ELIGIBLE";
	linkedProviderTypes: string[];
	revision: number;
	updatedAt: string | null;
};
export function profileRecord(value: unknown): CustomerProfile {
	const p = value as CustomerProfile;
	if (
		!p ||
		typeof p !== "object" ||
		Array.isArray(p) ||
		Object.keys(p).sort().join(",") !==
			"accountStatus,customerId,emailVerificationStatus,fullName,linkedProviderTypes,phone,revision,updatedAt,verifiedEmail" ||
		!/^([0-9a-f]{8}-)([0-9a-f]{4}-){3}[0-9a-f]{12}$/.test(p.customerId) ||
		p.accountStatus !== "ELIGIBLE" ||
		typeof p.fullName !== "string" ||
		[...p.fullName].length > 120 ||
		!(
			p.phone === null ||
			(typeof p.phone === "string" && p.phone.length <= 32)
		) ||
		!Number.isSafeInteger(p.revision) ||
		p.revision < 0 ||
		!(
			(p.updatedAt === null && p.revision === 0) ||
			(typeof p.updatedAt === "string" &&
				p.revision > 0 &&
				Number.isFinite(Date.parse(p.updatedAt)))
		) ||
		!["VERIFIED", "PENDING", "NOT_AVAILABLE"].includes(
			p.emailVerificationStatus,
		) ||
		!(
			(p.verifiedEmail === null && p.emailVerificationStatus !== "VERIFIED") ||
			(typeof p.verifiedEmail === "string" &&
				p.emailVerificationStatus === "VERIFIED" &&
				/^[a-z0-9][a-z0-9._+-]{0,63}@example\.test$/.test(p.verifiedEmail))
		) ||
		!Array.isArray(p.linkedProviderTypes) ||
		p.linkedProviderTypes.length > 4 ||
		new Set(p.linkedProviderTypes).size !== p.linkedProviderTypes.length ||
		p.linkedProviderTypes.some(
			(x) => !["MANUAL", "GOOGLE", "FACEBOOK", "OIDC"].includes(x),
		)
	)
		throw Error("Invalid own profile");
	return p;
}
export function profileEdit(
	value: unknown,
): value is {
	fullName: string;
	phone: string | null;
	expectedRevision: number;
} {
	if (!value || typeof value !== "object" || Array.isArray(value)) return false;
	const v = value as Record<string, unknown>;
	return (
		Object.keys(v).sort().join(",") === "expectedRevision,fullName,phone" &&
		typeof v.fullName === "string" &&
		[...v.fullName].length <= 120 &&
		(v.phone === null ||
			(typeof v.phone === "string" && v.phone.length <= 32)) &&
		Number.isSafeInteger(v.expectedRevision) &&
		(v.expectedRevision as number) >= 0 &&
		(v.expectedRevision as number) < Number.MAX_SAFE_INTEGER
	);
}

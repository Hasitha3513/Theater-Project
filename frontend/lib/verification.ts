import { RoomRequestError } from "./rooms";
export const verificationLabels = [
	"TEST / SYNTHETIC IDENTITY VERIFICATION",
	"NO REAL IDENTITY DOCUMENTS",
	"NOT VALID FOR PHYSICAL ADMISSION",
];
export type Verification = {
	id: string;
	venueId: string;
	quoteId: string;
	revision: number;
	generation: number;
	superseded: boolean;
	previousId: string | null;
	state: string;
	validUntil: string | null;
	synthetic: true;
	physicalAdmission: false;
	labels: string[];
	evidence: { id: string; type: string; state: string; expiresAt: string }[];
	history: {
		id: string;
		revision: number;
		verdict: string;
		reason: string;
		at: string;
		validUntil: string;
	}[];
	uploadId?: string;
};
export function validateVerification(value: unknown): Verification {
	const v = value as Verification;
	if (
		v?.synthetic !== true ||
		v.physicalAdmission !== false ||
		typeof v.id !== "string" ||
		typeof v.venueId !== "string" ||
		!Number.isSafeInteger(v.revision) ||
		v.revision < 0 ||
		!Number.isSafeInteger(v.generation) ||
		v.generation < 1 ||
		typeof v.superseded !== "boolean" ||
		!verificationLabels.every((l) => v.labels?.includes(l)) ||
		!Array.isArray(v.evidence) ||
		v.evidence.length > 2 ||
		!Array.isArray(v.history) ||
		![
			"NOT_SUBMITTED",
			"SUBMITTED",
			"PROCESSING",
			"REVIEW_REQUIRED",
			"VERIFIED",
			"REJECTED",
			"EXPIRED",
			"REVOKED",
		].includes(v.state)
	)
		throw new Error("Invalid synthetic verification response");
	return v;
}
export async function uploadSynthetic(
	id: string,
	file: File,
	progress: (percent: number) => void,
) {
	if (
		!["image/jpeg", "image/png"].includes(file.type) ||
		file.size > 5 * 1024 * 1024 ||
		file.size === 0
	)
		throw new Error(
			"Choose an approved synthetic JPEG/PNG fixture up to 5 MiB",
		);
	const csrf = await fetch("/api/session/csrf", { cache: "no-store" });
	if (!csrf.ok)
		throw new RoomRequestError(csrf.status, "Current session required");
	const token = await csrf.json();
	await new Promise<void>((resolve, reject) => {
		const xhr = new XMLHttpRequest();
		xhr.open("PUT", `/api/verification/uploads/${id}/content`);
		xhr.setRequestHeader("X-CSRF-TOKEN", token.token);
		xhr.setRequestHeader("Content-Type", file.type);
		xhr.upload.onprogress = (e) => {
			if (e.lengthComputable) progress(Math.round((e.loaded / e.total) * 100));
		};
		xhr.timeout = 15000;
		xhr.onload = () => {
			if (xhr.status === 200) resolve();
			else
				reject(
					new RoomRequestError(
						xhr.status,
						"Synthetic upload rejected; refresh current state",
					),
				);
		};
		xhr.onerror = xhr.ontimeout = () =>
			reject(new Error("Upload outcome unknown; refresh before resubmitting"));
		xhr.send(file);
	});
}

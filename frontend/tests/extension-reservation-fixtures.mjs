import {
	bookingId,
	venueId,
	requestId,
	customerBooking,
} from "./booking-cancellation-fixtures.mjs";
import {
	reservationTerms,
	reservationWarning,
} from "../lib/extension-reservation.ts";
export const reserveId = "00000000-0000-4000-8000-000000000010";
export const booking = () => ({
	...customerBooking(),
	amendmentPlanning: {
		guests: 4,
		packageId: venueId,
		optionalServiceIds: [],
		paymentMethod: "BANK_DEPOSIT",
	},
});
export const reservation = () => ({
	bookingId,
	reservationId: reserveId,
	purchasedRevision: 1,
	proposalRevision: 0,
	state: "ACTIVE",
	originalSession: {
		start: "2026-11-01T04:30:00Z",
		end: "2026-11-01T07:30:00Z",
		blockedUntil: "2026-11-01T07:45:00Z",
	},
	proposedSession: {
		start: "2026-11-01T04:30:00Z",
		end: "2026-11-01T08:00:00Z",
		blockedUntil: "2026-11-01T08:15:00Z",
	},
	additionalCoverage: {
		start: "2026-11-01T07:45:00Z",
		end: "2026-11-01T08:15:00Z",
	},
	additionalMinor: "12345",
	currency: "LKR",
	packageVersionId: venueId,
	pricingBasis: "FROZEN_PURCHASED_TERMS",
	additionalMinutes: 30,
	termsVersion: reservationTerms,
	createdAt: "2026-10-24T04:00:00Z",
	expiresAt: "2026-10-24T04:10:00Z",
	observedAt: "2026-10-24T04:01:00Z",
	effective: true,
	pendingReconciliation: false,
	reasons: [],
	bookingChanged: false,
	paymentRequested: false,
	occupancy: "UNKNOWN",
	readiness: "UNVERIFIED",
	synthetic: true,
	warning: reservationWarning,
});
export const create = () => ({
	additionalMinutes: 30,
	expectedPurchasedRevision: 1,
	expectedLifecycleVersion: 0,
	expectedPackageVersionId: venueId,
	expectedAdditionalMinor: "12345",
	expectedCurrency: "LKR",
	expectedConfiguration: "room-v1:venue-v1",
	commandKey: requestId,
	termsVersion: reservationTerms,
	acknowledged: true,
});
export const recovery = (key) => ({
	bookingId,
	reservationId: reserveId,
	commandKey: key,
	commandType: "CREATE",
	recoveryStatus: "COMMITTED_REFERENCE",
	statusReadRequired: true,
});

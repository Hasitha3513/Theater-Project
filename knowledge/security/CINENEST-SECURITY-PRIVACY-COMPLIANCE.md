# CineNest Security Privacy and Compliance

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.


## Scope and status
Desired controls DOCUMENTED_DRAFT, implementation NOT_VERIFIED. No legal-compliance, merchant approval, screening licence or hardware-safety certification asserted. Source PDFs (if later supplied) are draft requirements, not proof of any approval. This document defines review topics and acceptance evidence; specific legal obligations need qualified local review, not inferred rules.

## Identity and authorization
Google OIDC proves a provider login, not age, physical identity or policy consent. Verify OIDC protocol/session securely; secure HttpOnly application cookies, appropriate SameSite/CSRF and session revocation; exact deployment strategy requires stack approval. Enforce least-privilege capabilities, owner/authorized venue scope, stronger administrative authentication and restricted emergency overrides. Separate booking management, finance refund/export, document review/evidence view, access secret reveal and device command permissions. Every protected action checked backend-side; UI hiding is insufficient. Do not presume multi-tenant SaaS isolation.

## Evidence processing and privacy
NIC/passport extraction primarily covers booker; OCR only offers candidate text. It does not prove document authenticity, identity or liveness. Age/date interpretation, rejection/appeal, manual review, all-guest original-document checks, guardian/admission policy and exception handling need final decisions (OD-03). Collect minimum required data, restrict evidence preview/export, mask IDs, log evidence access without raw contents, segregate contact/verification/access data. Private encrypted storage and controlled encryption keys; bounded signed upload/read links, file type/size validation, malware handling, audit and deletion workflows need provider/design review.

Retention periods, processing purposes, consent/legal basis, regional transfers/subprocessors, backups/deletion lag, incident obligations and account deletion conflicts are OPEN_DECISION OD-05. No invented retention days. Deletion workflow authenticates requester, checks approved legal holds/ledger obligations, removes active evidence and tracks pending copies/backups per approved lifecycle; deletion success never asserted solely from UI click. Raw documents/OCR text are excluded from ordinary events, logs and analytics.

## Consent and purchases
Explicit versioned terms/privacy/admission acceptance records actor, policy versions/hash, timestamp and context. Google sign-in and a prechecked box do not substitute for consent. Preserve booking-time quote, refund terms and consent snapshot; new policy settings cannot silently rewrite purchases. Dark patterns, hidden fees and blanket automatic card re-billing are outside approved behavior. Refund/charge consent/provider eligibility remains OD-07.

## Payment and secret boundaries
Hosted/tokenized checkout avoids application collection/storage of raw card credentials. Official provider merchant/country/product support, webhook authenticity, refund rules and responsibilities must be verified (OD-06). Do not claim hosted checkout eliminates every security/compliance responsibility. Verify events and dedupe financial facts; no client-redirect success trust.

PIN default delivery should be a protected link/in-app reveal in SMS/email, not raw secrets in templates or normal notification events. If raw PIN delivery is later required, it needs an explicit risk/consent/retention decision and narrow secure delivery channel. Mask on lists and shared receipts; exclude from URLs/query logs/analytics/exports. Credential storage/installation uses least-privilege secured adapters, secret encryption, rotation/revocation and access audit. Delivery receipt does not prove customer received or could enter.

## Safety and commercial review
Follow [IoT requirements](../architecture/CINENEST-IOT-ACCESS-AND-FAILURE-MODES.md): independent egress, no occupant trapping or safety power interruption, unknown/offline never treated as OFF/empty. Product claims “fully automated/staff-less” cannot override required emergency, verification or physical-cleaning staffing.

Commercial screening/streaming subscriptions, public exhibition/content rights, gaming licences, photography/logo rights and venue/admission conditions remain OD-14. Consumer pricing/refund disclosures and final 18+ policy require review before launch. No guaranteed legal compliance or approved licensing is claimed.

## Future security evidence
Threat model customer/admin/device/provider trust boundaries; test object-level scope, privileged MFA/recovery, CSRF/session fixation, signed URLs/upload validation, secret/log leakage, webhook replay, broker ACLs, device credential rotation, dependency/supply-chain scanning, restore and incident response. These are continuous delivery gates, not tests run by this documentation task.

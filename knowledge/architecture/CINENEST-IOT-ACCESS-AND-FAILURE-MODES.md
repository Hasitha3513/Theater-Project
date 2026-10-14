# CineNest IoT Access and Failure Modes

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.


## Hard design constraints and proof status
Decision DOCUMENTED_DRAFT safety requirements from S1; Implementation NOT_VERIFIED. Emergency egress must not depend on cloud, payment, PIN or administrator intervention. Access expiry/revocation denies new entry and must not trap occupants. Remote lighting/AC/projector actions must not disable safety circuits, alarms, egress lighting or emergency mechanisms. Actual hardware, wiring, fail modes, physical inspection and accountable staffing are launch blockers OD-02/04, not verified by this document.

Entry valid only [session_start,session_end); requested credential installation is not installed until authoritative acknowledgement/lookup. Session/cleaning schedules follow [lifecycles](../product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md). Exit/checkout event is evidence of an event, never proof every person left. Physical occupancy status is unknown unless a validated detection/operational process supports a qualified claim.

## Capability and hardware acceptance matrix
For each lock/controller/model/firmware record local PIN capacity, scheduled validity, clock sync/drift behavior, provisioning acknowledgement, offline entry, power failure, revoke while offline, re-entry, manual/emergency egress and vendor API/country/account compatibility. Every field is currently NOT_VERIFIED. TTLock/Tuya-compatible is a candidate description, not tested compatibility. Cloud remote unlock cannot establish offline capability. Record test evidence and responsible reviewer before enabling any supported badge/control.

| Failure | Desired UI/state | Safe behavior/recovery | Evidence/launch gate |
|---|---|---|---|
| Internet/cloud/vendor outage | Unknown/degraded, last observation; no false available/unlocked | Only tested preinstalled local credentials may work; support/intervention; reconcile after reconnect | Model-specific outage and clock tests |
| Broker/ESP32 disconnected | Stale/unknown telemetry; pending command may expire | Bounded retry/TTL; no blind repeated unlock; refresh actual state | Disconnect/reconnect, ACL and retained-message tests |
| Lock power failure | Access unknown, independent egress instructions/support | Hardware-tested local/manual intervention; never promise entry or egress from cloud | Wiring/power/egress inspection |
| PIN install missing/failed | Paid/confirmed may coexist with access pending/failed | Reconcile credential version/window; bounded retry; accountable incident before arrival | Installation lookup/ack and support drill |
| Late cancel/revoke while offline | Revocation requested/unknown until evidence | Escalate tested local intervention; preserve egress; do not claim credential disabled | Offline revoke/re-entry tests |
| Command ack lost/out-of-order | Unknown original operation; requested vs observed distinct | Query command/device state; dedupe by command ID; do not replay expired command | Ack/telemetry ordering and idempotency tests |
| Sensor quiet/exit event | Physical occupancy unknown unless qualified | Do not mark room vacant or ready from signal alone | OD-13 validated detection/operational process |
| Session expired/possible overstay | Ending/ended plus separate suspected incident | Five-minute warning, safe entry expiry, staff escalation; no unsafe lock/power punishment | Staffing/response plan OD-04 |
| Buffer elapsed, cleaning unconfirmed | Cleaning_required, not ready | Physical checklist/clearance, block unsafe next admission | Staffing/readiness policy OD-04/10 |
| Clock drift or stale scheduled command | Unknown validity; expiry visible | Server/local schedule reconciliation, expire stale work; safe intervention | Time-sync/offline window boundary tests |

## Secure command contract
Authenticated device identities and MQTT over TLS with topic/resource ACLs; credentials unique/rotatable/revocable. Capability-scoped commands include command ID, desired action/units, actor/correlation, schema version, issuedAt and expiresAt. Reject stale/unauthorized/unsupported values at server and edge. Do not retain actionable unlock/power commands for replay after reboot; retained telemetry still needs freshness checks. Ack confirms receipt/processing per capability, not physical state unless explicit observed evidence. UI labels requested, pending, acknowledged, failed, expired and unknown; actual observation has observedAt/quality.

## Operations and continuous acceptance
Run simulated and physical safety-reviewed tests before controlled launch; documentation makes no hardware result claim. Maintain incident ownership/contacts, manual recovery runbooks, safe maintenance and emergency staffing. Five-minute notifications are scheduled durably and deduplicated per session revision; missed jobs do not send misleading warnings after expiry. Separate notification delivery from session end and physical cleaning. No physical commands are sent by this task.

## Bank payment — CN-KB-BANK-PAYMENT-001
[Bank verification](../product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) never unlocks directly. Receipt submission/reviewer alert is not verified funds; verified funds alone are not confirmation or installed access. Only sufficient authorized allocation, valid inventory and pre-confirmation eligibility allow Booking to queue existing provisioning. No PIN release while payment unverified; keep purchased session window and actual device acknowledgement. Access failure remains access pending/incident while bank payment stays verified; no reversal fiction. Original-ID entry checks and independent egress remain unchanged. Bank payment implies no card token or damage card re-billing; alternate policy requires separate approval. No real lock/controller commands sent here.

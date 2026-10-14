# CineNest Charts and Metrics

> CN-KB-FEATURES-TECH-001 · 2026-10-14. Decision: DOCUMENTED_DRAFT. Implementation: NOT_VERIFIED. Source S1 with S9 bank extension (supporting bank metrics RECOMMENDED). Contract version 1.2.0 extends observed chart-spec 1.1.0 for bank payments; historical 1.0.0→1.1.0 corrections remain in ADR-002; no backend or chart library is implemented.

## Canonical semantics and boundaries
[Chart JSON](tokens/cinenest-chart-specs.json) is machine-readable contract guidance; [active tokens](tokens/cinenest-design-tokens.json) remain byte-for-byte unchanged. [ADR-002](CINENEST-DESIGN-DECISION-ADR-002.md) records corrections from the initial cash-as-revenue/ambiguous donut definition. [Business rules](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) and [API contracts](architecture/CINENEST-DATA-AND-API-CONTRACTS.md) define authoritative proposed sources.

Gateway captures, independently verified bank credit collections, net collections (both incoming channels less evidenced outgoing refunds/reversals), and recognized revenue (approved recognition ledger/policy) are distinct. Uploaded/declared receipt values are never collections; allocations and verification processing are not second incoming cash events. No approved recognition policy exists; recognized revenue is Unavailable. These are not net profit. Provider fees, taxes, chargebacks and recognition allocation require OD-16/07 approval. Deduplicate unique ledger events; refund events use refund date and original purchase scope. Do not infer booking count from capture count.

Today's confirmed bookings use confirmedAt; today's sessions use scheduled session_start; creation-cohort statistics use createdAt. Live booking-based status and physical occupancy are distinct. Unknown sensors/exit events do not imply unoccupied. Donut center and slices share all scoped enabled rooms including unknown; a bookable-only rate must be another explicitly labelled metric. Utilization uses customer session minutes, not cleaning or motion minutes, and a versioned sellable-minute denominator.

Storage: UTC RFC3339 instants; reporting: explicit Asia/Colombo half-open [from,to), local midnight to next local midnight. End-date UI converts to next local date exclusive. Other supported zones require explicit configuration and offset-disambiguated DST labels; do not use browser timezone. Week start Monday remains proposed. Currency default LKR from S1, integer minor units, locale/ISO label; mixed currency never summed without approved FX. Round only display, not source aggregates.

Prior comparison returns exact preceding calendar boundaries in same timezone/granularity, compares equivalent complete portions and labels partial periods. Zero/missing denominator or previous baseline → N/A; unavailable/partial data never zero. Complete empty counts can be zero. DEMO examples never serve as production fallback. Missing buckets show gaps. Freshness includes asOf, completeness and observedAt; actual thresholds/refresh are open. Authorized exports use exactly visible filters, units, metric IDs/date bases and scope, not a paginated subset. No secret or identity evidence in reports/tooltips.

Grid/axis use chart.grid/chart.axis. Series use stable tokens and non-color differentiation; equivalent tables, focus/tap tooltips and full labels required. Responsive plots minimum 240px with reduced ticks/wrapped legends; errors/no records/no scope/stale/partial are explicit. Chart library selection remains open (Recharts OR Apache ECharts).

## DEMO calculation fixtures
- Capture 100000 and successful refund 20000 minor units → net collections 80000; recognized revenue unavailable without recognition policy. Two confirmed booking IDs remain count 2 even if three captures.
- Room states 2 occupied + 2 available + 1 cleaning + 1 maintenance + 1 unknown → 7 slices; center booking-based occupancy 2/7 = 28.571…%. Physical occupancy remains unknown.
- Operating interval 09:00–17:00 minus 12:00–13:00 blackout → 420 sellable minutes; session union 10:00–14:00 clipped excluding blackout → 180 minutes, 42.857…%. Any buffer exclusions require the reported policy version.
- Cohort 5 / cancelled 1 → 20%; zero denominator → N/A. Captures today and scheduled sessions today are intentionally different cohorts.

## Revenue and Bookings Chart

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-REVENUE-BOOKINGS |
| Chart type | bar-line |
| Formula | Gateway captured collections = deduped authenticated capture amounts by capturedAt. Bank collections = independently verified unique credits by bank_credited_at, verified by asOf. Total incoming collections = gateway captures + bank collections; net collections = total incoming - confirmed gateway/bank outgoing refunds and separately verified bank reversal movements (never duplicate the same reversal/refund). Claims and allocation events add no collections. Recognized revenue remains separate approved recognition ledger/service policy only, null if unavailable. Booking count = distinct confirmedAt booking IDs, source-independent (not receipt/credit count). Incoming excess/unmatched funds are real received money, never automatic booking or recognized revenue; disclose their balances separately. |
| Population | Unique independently verified incoming money ledger events in financial range and authorized scope; bank unallocated/excess scope is authorized receiving account, room attribution only when reliable; disclosed unmatched scope, no fabricated room assignment. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; Asia/Colombo reporting boundaries; gateway capturedAt, bank_credited_at, confirmed outgoing refund/reversal event times; verified_at cutoff asOf. Booking confirmedAt and service session_start_at independent. |
| Axes/labels | X local bucket label; left money/currency; right integer booking count. Both start at zero; signed financial axis may extend below zero. |
| Filters | range, granularity, room, location, currency, timezone |
| Tooltip | Exact range/asOf/timezone/currency; gateway captures, independently verified bank credits, confirmed outgoing refunds/reversals, total/net collections; distinct verified_at/creditedAt/allocatedAt bases, completeness. Recognition only with approved policy; confirmed bookings separate scale. |
| Contract | financialBuckets/financialSummary fields gatewayCapturedMinor, verifiedBankCreditMinor, confirmedRefundMinor, verifiedReversalMinor, netCollectionsMinor; event time + asOf evidence cutoff; distinct bookingBuckets/recognitionBuckets. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Net collections; confirmed bookings (combo only); explicit selected basis. Recognized revenue never an alias of net collections. |
| Responsive | Full width; minimum 240px plot; reduce ticks, stack legend; dual-axis labels retained, table available. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Net collections = `chart.revenue`; Confirmed bookings = `chart.bookings` |

## Room Status Donut Chart

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-ROOM-OCCUPANCY |
| Chart type | donut |
| Formula | Population = all enabled rooms in authorized selected venue/room scope at snapshotAt, each exactly once in occupied/available/cleaning/maintenance/unknown. Donut center = booking-based occupied count / sum all five slice counts × 100. Zero rooms → N/A. Unknown stays in population and is labelled; center is not physical occupancy. Slice percent and center share scope/asOf/denominator. State mapping uses versioned maintenance > unknown/stale readiness > cleaning > scheduled occupied > cleared available precedence; conflicting facts raise an incident and never prove physical presence. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | integer counts / defined percent / state labels |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | No axes; center label booking-based occupied / all scoped rooms and denominator. Physical occupancy unknown unless separate validated observation contract. |
| Filters | location, room, snapshotAt |
| Tooltip | State, count, percentage of all scoped status rooms, observedAt and freshness. Unknown is not unoccupied. |
| Contract | roomSnapshot with mutually exclusive states, stateObservedAt and snapshotAt; explicit unknown/stale handling. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | All five states with counts; occupied violet, available green, cleaning amber, maintenance red, unknown gray. |
| Responsive | Minimum 240px graphic; legend below <lg; counts and table readable without hovering. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Occupied = `chart.occupied`; Available = `chart.available`; Cleaning = `chart.cleaning`; Maintenance = `chart.maintenance`; Unknown = `chart.unknown` |

## Revenue Trend Chart

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-REVENUE-TREND |
| Chart type | line-area |
| Formula | Gateway captured collections = deduped authenticated capture amounts by capturedAt. Bank collections = independently verified unique credits by bank_credited_at, verified by asOf. Total incoming collections = gateway captures + bank collections; net collections = total incoming - confirmed gateway/bank outgoing refunds and separately verified bank reversal movements (never duplicate the same reversal/refund). Claims and allocation events add no collections. Recognized revenue remains separate approved recognition ledger/service policy only, null if unavailable. Incoming excess/unmatched funds are real received money, never automatic booking or recognized revenue; disclose their balances separately. |
| Population | Unique independently verified incoming money ledger events in financial range and authorized scope; bank unallocated/excess scope is authorized receiving account, room attribution only when reliable; disclosed unmatched scope, no fabricated room assignment. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; Asia/Colombo reporting boundaries; gateway capturedAt, bank_credited_at, confirmed outgoing refund/reversal event times; verified_at cutoff asOf. Booking confirmedAt and service session_start_at independent. |
| Axes/labels | X bucket/date; Y money/currency; zero baseline and negative values preserved. |
| Filters | range, granularity, room, location, currency, timezone |
| Tooltip | Exact range/asOf/timezone/currency; gateway captures, independently verified bank credits, confirmed outgoing refunds/reversals, total/net collections; distinct verified_at/creditedAt/allocatedAt bases, completeness. Recognition only with approved policy; confirmed bookings separate scale. |
| Contract | financialBuckets/financialSummary fields gatewayCapturedMinor, verifiedBankCreditMinor, confirmedRefundMinor, verifiedReversalMinor, netCollectionsMinor; event time + asOf evidence cutoff; distinct bookingBuckets/recognitionBuckets. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Net collections; confirmed bookings (combo only); explicit selected basis. Recognized revenue never an alias of net collections. |
| Responsive | Full width; fewer ticks; area may be removed at small widths without removing values. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Net collections = `chart.revenue` |

## Popular Time Slots Chart

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-POPULAR-TIME-SLOTS |
| Chart type | bar |
| Formula | Distinct confirmed noncancelled booking IDs whose scheduled start falls within selected range, grouped by configured local hour band. Explicit business bands [startHour,endHour); cross-midnight bands split or carry dayOffset. Popularity is count, not occupied minutes. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | integer counts / defined percent / state labels |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | X labelled local time bands (24-hour or locale time); Y integer booking count from zero. |
| Filters | range, room, location, timezone, slotBandDefinition |
| Tooltip | Band including AM/PM or 24-hour clarity, booking count, range and timezone. |
| Contract | slotBuckets with bandId, label, startHour/endHour/dayOffset and bookingCount; band definitions from configured operating hours. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Bookings started; label rankings as tied when counts equal. |
| Responsive | Horizontal bars at <sm; preserve complete time-band labels; no ambiguous duplicate 9–12. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Bookings started = `chart.bookings` |

## Room Utilization Chart

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-ROOM-UTILIZATION |
| Chart type | horizontal-bar |
| Formula | Per room: union noncancelled confirmed session intervals (customer minutes only, never cleaning) clipped to range and sellable intervals / union defined sellable intervals in range × 100. Sellable intervals = operating windows minus versioned maintenance/blackouts and non-sellable cleaning buffers under approved denominator policy. Avoid double subtraction; report sellablePolicyVersion. Zero denominator → N/A; unknown/incomplete inputs → unavailable. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | percent and minutes |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | Y room label; X 0–100 percent. Never cap invalid >100 data silently; flag contract error. |
| Filters | range, room, location, timezone |
| Tooltip | Room, booked/sellable minutes, percent, excluded intervals and range/timezone. |
| Contract | roomUtilization with roomId/name, bookedMinutes, sellableMinutes, utilizationPercent and completeness; server resolves blackout/buffer policy. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Time utilization; optional comparison dashed, not another occupancy definition. |
| Responsive | Vertical list of horizontal bars; long names wrap and table remains available. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Time utilization = `chart.occupied` |

## Booking Statistics

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-BOOKING-STATISTICS |
| Chart type | stacked-bar-and-summary |
| Formula | Cohort = distinct booking IDs with createdAt within range; snapshot state at asOf partitions cohort once. Total = sum all mutually exclusive state counts including other. Cancellation rate = cancelled cohort / total cohort × 100; zero cohort → N/A. Separate confirmedAt KPI is not interchangeable with this creation cohort. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | integer counts / defined percent / state labels |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | X creation bucket; Y integer booking count, zero baseline. |
| Filters | range, granularity, room, location, timezone |
| Tooltip | Bucket, each state count, cohort total, cancellation rate, asOf. |
| Contract | bookingStateBuckets with cohort basis createdAt, asOf and all backend states mapped to documented display buckets; totals from same dataset. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | All included states, with text and stable color; Other states expands in data table. |
| Responsive | Stacked chart stacks above summary cards <lg; table retains unmapped states. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Confirmed = `chart.available`; Ongoing = `chart.bookings`; Completed = `chart.occupied`; Cancelled = `chart.maintenance`; Other states = `chart.unknown` |

## Financial KPI Cards

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-FINANCIAL-KPIS |
| Chart type | kpi-cards |
| Formula | Gateway captured collections = deduped authenticated capture amounts by capturedAt. Bank collections = independently verified unique credits by bank_credited_at, verified by asOf. Total incoming collections = gateway captures + bank collections; net collections = total incoming - confirmed gateway/bank outgoing refunds and separately verified bank reversal movements (never duplicate the same reversal/refund). Claims and allocation events add no collections. Recognized revenue remains separate approved recognition ledger/service policy only, null if unavailable. Average booking value = finalized purchased quote total / distinct eligible confirmedAt bookings at asOf, unchanged purchased snapshots; zero denominator → N/A; prior zero/missing → N/A. Incoming excess/unmatched funds are real received money, never automatic booking or recognized revenue; disclose their balances separately. |
| Population | Unique independently verified incoming money ledger events in financial range and authorized scope; bank unallocated/excess scope is authorized receiving account, room attribution only when reliable; disclosed unmatched scope, no fabricated room assignment. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; Asia/Colombo reporting boundaries; gateway capturedAt, bank_credited_at, confirmed outgoing refund/reversal event times; verified_at cutoff asOf. Booking confirmedAt and service session_start_at independent. |
| Axes/labels | No axes; each card has label, value/currency and named comparison basis. |
| Filters | range, room, location, currency, timezone |
| Tooltip | Exact range/asOf/timezone/currency; gateway captures, independently verified bank credits, confirmed outgoing refunds/reversals, total/net collections; distinct verified_at/creditedAt/allocatedAt bases, completeness. Recognition only with approved policy; confirmed bookings separate scale. |
| Contract | financialBuckets/financialSummary fields gatewayCapturedMinor, verifiedBankCreditMinor, confirmedRefundMinor, verifiedReversalMinor, netCollectionsMinor; event time + asOf evidence cutoff; distinct bookingBuckets/recognitionBuckets. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Not required; label every card. Up/down is descriptive, not always good/bad. |
| Responsive | 1/2/4 columns base/sm/lg; values wrap without truncation. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Net collections = `chart.revenue`; Gateway captured collections = `chart.available`; Successful refunds = `chart.maintenance`; Average booking value = `chart.bookings`; Recognized revenue (only with approved recognition policy) = `chart.revenue` |

## System and IoT Status Indicators

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-SYSTEM-IOT-STATUS |
| Chart type | status-list-and-timeline |
| Formula | Device state from authoritative health policy and lastSeen: stale after per-type threshold, unknown when never observed; connectivity alone does not imply command success. Fleet online rate = online / registered enabled scoped devices ×100, unknown included; 0 devices → N/A. System operational only when all required checks explicitly healthy and fresh; missing/degraded checks forbid operational claim. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | integer counts / defined percent / state labels |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | Status list has no axes; optional timeline X instant/date, Y state per service/device lane. |
| Filters | location, room, deviceType, service, range |
| Tooltip | Entity, status, observedAt/timezone, age, freshness threshold and safe diagnostic. |
| Contract | serviceHealth and deviceHealth with status, observedAt, staleAfterSeconds, required, safeReason and telemetry schema version. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Online/degraded/offline/unknown with icons and words; no all-green default. |
| Responsive | List wraps to cards; timeline scrolls in labelled region; equivalent event table. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Online = `chart.available`; Degraded = `chart.cleaning`; Offline = `chart.maintenance`; Unknown = `chart.unknown` |

## Payment and Add-on Breakdown Charts

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-PAYMENT-ADDON-BREAKDOWN |
| Chart type | separate-bars |
| Formula | Payment panel groups deduped independently verified gateway capture and bank credit incoming money by channel/method, respective financial event time and asOf evidence cutoff; uploaded/declared or allocated amounts not extra collections. Add-on panel retains sold immutable quote-item values/quantities by eligible confirmedAt cohort, not cash/recognition; include purchased package inclusion flags so included free extras are not a paid sale. Unmatched/excess bank balance displayed separately, never add-on revenue. |
| Population | Verified incoming money by channel for payment panel; finalized purchased quote item lines for add-ons; scope/date basis separately labelled. |
| Units | Single ISO currency minor-unit amounts, quantities and distinct counts separately labelled. |
| Time basis | Gateway capturedAt versus bank_credited_at with verified_at asOf cutoff; sold add-on confirmedAt cohort/session date distinct. |
| Axes/labels | Separate panels: category vs money/quantity with explicitly selected unit; zero baseline; retain negative refund-adjusted views when supplied. |
| Filters | range, room, location, currency, timezone, paymentMethod, addonType |
| Tooltip | Channel, verified incoming amount/currency/event-time/asOf and allocation/unmatched qualification; add-on purchased item count/amount/cohort distinct; no receipt amount as collections. |
| Contract | paymentBreakdown {methodId, gatewayCapturedMinor, verifiedBankCreditMinor, collectionsMinor, externalEventCount}; addonBreakdown remains sold quote-item cohort; unmatchedBankBalance separate. |
| Authorized data source | Payments ledger and Pricing/Booking immutable purchase-item projection; payments.read/analytics.read scope. |
| Freshness | Server asOf/complete; stale or missing allocation labelled. |
| Legend | Verified incoming collections by channel (gateway captures and bank credits) and sold add-on values are separate populations; never a combined pie denominator. |
| Responsive | Horizontal bars on mobile; full labels and supporting tables remain. |
| Accessibility | Equivalent data table, non-color labels, focus/tap tooltips and keyboard filters. |
| Empty/error states | Valid complete empty counts zero; unknown allocations unavailable; error/partial dataset never invented zero. |
| Colors | Verified incoming collections by payment channel = `chart.revenue`; Sold add-ons by type = `chart.bookings` |

## Bank Payment Review and Finance Metrics

> CN-KB-BANK-PAYMENT-001 · Supporting metrics RECOMMENDED; Implementation NOT_VERIFIED. [Canonical bank workflow](product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) owns fund/review states. [Chart JSON](tokens/cinenest-chart-specs.json) supplies contracts; no live bank/provider/API is verified.

| Aspect | Specification |
|---|---|
| Metric ID | CN-M-BANK-PAYMENT-REVIEW-FINANCES |
| Chart type | kpi-cards-and-review-age-bars |
| Formula | BN-M01–08 defined in bankMetrics; request count/elapsed age/money flows and snapshot balances have distinct populations/time bases. Never count receipt claims, verification and allocation as duplicate incoming money; N/A zero denominator and unavailable not zero. |
| Population | Open bank review requests, unique bank credit ledger, allocation ledger and outgoing refunds; each named metric uses explicit population. |
| Units | Integer counts, elapsed minutes, exact single-currency minor units; no shared axis across unlike units. |
| Time basis | Snapshot asOf vs evidence_submitted_at/bank_credited_at/verified_at/allocated_at/bank_refund_confirmed_at/session_start_at; UTC storage and Asia/Colombo boundaries. |
| Axes/labels | Queue age bars: X elapsed minutes, Y request/age band; finance cards no axes, labelled currency/time basis. No receipt amount collections axis. |
| Filters | range, venue, roomWhenAttributed, receivingAccount, reviewState, assignee, paymentChannel, currency, timezone, explicitTimeBasis |
| Tooltip | Metric ID/definition/population, exact date basis/asOf/currency, aging SLA configuration, scope/attribution/completeness; no raw receipts or bank details. |
| Contract | bankReviewMetrics with asOf/openRequestCount/ageMinutes/reviewDueAt; bankCredit/Allocation/Refund ledger aggregates keyed by external credit/operation ID; bankMetrics definitions authoritative. |
| Authorized data source | Backend Payments authorized bank.review/payments.read/report.export and venue/receiving-account scope; Booking purchased refs; no actual API/bank feed verified. |
| Freshness | asOf/complete/warnings and individual submission/credit/verify/allocation times; review updates/delayed bank matching labelled; configured stale thresholds open. |
| Legend | Each named metric/date basis explicit; colors reuse chart.bookings/available/revenue/cleaning/maintenance; no new palette, non-color status labels. |
| Responsive | Finance cards 1/2/4 columns; queue age horizontal bars with labelled table and wrapped legends on mobile. |
| Accessibility | Equivalent scoped data table, textual units/state/date basis, focus/tap tooltips, keyboard filters; no color-only or inaccessible receipt preview. |
| Empty/error states | Empty reviewed population = zero valid count; age with no requests → N/A; unavailable/partial/stale permission/error distinct with retry; never DEMO or declared values fallback. |

### Bank metric populations and clocks

| ID / metric | Population | Formula | Units | Time basis | Color |
|---|---|---|---|---|---|
| BN-M01 / pendingReviewCount | Distinct bank request IDs with unresolved submitted/review_required/reviewing/awaiting_credit/information_requested work, one per request; repeated receipts do not add requests | count distinct requestId in open review states at asOf | integer request count | snapshot asOf; submitted date filter explicitly independent | `chart.bookings` |
| BN-M02 / queueAgeMinutes | Same open request population as BN-M01; first outstanding submission since unresolved cycle, corrections do not reset | asOf - oldest unresolved evidence_submitted_at per request in elapsed minutes; distribution/buckets from configured service; overdue only if approved review_due_at passed | elapsed minutes; separate approved service-hours SLA if selected | snapshot asOf and evidence_submitted_at, never approvedAt; elapsed vs service hours labelled | `chart.cleaning` |
| BN-M03 / verifiedBankFundsMinor | Unique independently verified receiving-account credit facts, including unallocated/excess; declared/uploaded amounts excluded | sum unique positive verified credit amountMinor first verified_at in range; verification correction/bank reversal separate evidence, no double verification entry | integer minor units / ISO currency | verified_at operational processing range, credited_at shown separately; not total collections-by-credit-date | `chart.available` |
| BN-M04 / bankCollectionsMinor | Unique actual verified receiving-account credits with bank_credited_at in range and verified by asOf; all actual credits including excess but not booking revenue | sum deduped bank credit amountMinor; bank-verified reversals/refunds separately deducted for net money measure, allocation never adds another credit | integer minor units / ISO currency | bank_credited_at financial event time; verified_at cutoff asOf; late review can revise historical projection with clear asOf | `chart.revenue` |
| BN-M05 / allocatedBookingPaymentsMinor | Scoped accepted quote-linked bank/gateway allocation ledger deltas at asOf; only authenticated gateway/bank-reconciled funds | sum signed unique allocation amount deltas (including separately audited allocation reversals), capped by quote due and available verified funds; source channels separate | integer minor units / ISO currency | allocated_at ledger event range, booking session_start_at is separate service filter; allocation is not cash recognition | `chart.bookings` |
| BN-M06 / unmatchedExcessMinor | Unique verified bank credits asOf minus active non-refunded allocations (after linked reversals/refund adjustments) and confirmed outgoing refunds/actual bank reversals; refund-reserved portion explicitly identified | per-credit remaining amount; split unmatched vs accepted-quote overpayment/excess only using linked facts; never derive from receipt claim; nonnegative invariant failures flagged; active allocations released/refined through refund adjustments so outgoing refunds not deducted twice; reserved/available balances separate | integer minor units / ISO currency | balance snapshot asOf, optional credited-date cohort explicit; not a movement summed again with collections | `chart.cleaning` |
| BN-M07 / confirmedBankRefundsMinor | Unique actual outgoing bank refunds with approved destination and independently supported completion evidence | sum confirmed outgoing refund amountMinor once; requested/approved/initiated/sent/failed/unknown excluded from completed money movements | integer minor units / ISO currency | bank_refund_confirmed_at/outgoing settlement instant selected by evidenced bank contract; not approval time | `chart.maintenance` |
| BN-M08 / paymentChannelCollectionsMinor | Deduped gateway captures + actual verified bank credits; matched internal own-account transfers/duplicates excluded through reconciliation; allocation excluded | group verified incoming collections by gateway vs bank, sum once per external money event. Source bank credit is never card capture; refunds shown as separate channel movements | integer minor units / ISO currency | gateway capturedAt and bank_credited_at, verified/asOf cutoff labelled; recognition/service revenue remains separate | `chart.revenue` |

Room-filtered booking allocation and global receiving-account collections can have different attributable scope; label unmatched/unknown room attribution rather than invent it. Exports use identical authorized scope, asOf/clock/currency and metric IDs as view. Receipt-uploaded amounts excluded even when review count is positive. AsOf late verification can legitimately revise past credit-date collections; display processing time separately. No recognized service revenue without approved recognition policy; excess is not sold package/add-on revenue. Existing DEMO fixtures are gateway-only unless labelled otherwise, never production fallback.

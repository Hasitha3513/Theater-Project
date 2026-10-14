# CineNest Charts and Metrics

> CN-KB-FEATURES-TECH-001 · 2026-10-14. Decision: DOCUMENTED_DRAFT. Implementation: NOT_VERIFIED. Source S1. Contract version 1.1.0 updates verified chart-spec version 1.0.0; no backend or chart library is implemented.

## Canonical semantics and boundaries
[Chart JSON](tokens/cinenest-chart-specs.json) is machine-readable contract guidance; [active tokens](tokens/cinenest-design-tokens.json) remain byte-for-byte unchanged. [ADR-002](CINENEST-DESIGN-DECISION-ADR-002.md) records corrections from the initial cash-as-revenue/ambiguous donut definition. [Business rules](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) and [API contracts](architecture/CINENEST-DATA-AND-API-CONTRACTS.md) define authoritative proposed sources.

Captured collections, net collections (captures less successful refunds), and recognized revenue (approved recognition ledger/policy) are distinct. No approved recognition policy exists; recognized revenue is Unavailable. These are not net profit. Provider fees, taxes, chargebacks and recognition allocation require OD-16/07 approval. Deduplicate unique ledger events; refund events use refund date and original purchase scope. Do not infer booking count from capture count.

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
| Formula | Captured collections = sum unique successful capture ledger event amounts in financial event range; refunds = sum unique successful refund event amounts in refund event range; net collections = captures - refunds. Recognized revenue is separate, only from finance-approved recognition entries/policy and never inferred from cash. Booking count = distinct booking IDs confirmedAt in bucket (not sessions scheduled today). |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | X local bucket label; left money/currency; right integer booking count. Both start at zero; signed financial axis may extend below zero. |
| Filters | range, granularity, room, location, currency, timezone |
| Tooltip | Exact bucket/timezone, captured collections, successful refunds, net collections, currency/date basis/completeness; confirmedAt bookings on combo. Recognized revenue only if approved recognition data explicitly selected. |
| Contract | financialBuckets + bookingBuckets (combo only), with separate capture/refund event basis and confirmedAt count; recognitionBuckets separate optional contract. |
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
| Formula | Captured collections = sum unique successful capture ledger event amounts in financial event range; refunds = sum unique successful refund event amounts in refund event range; net collections = captures - refunds. Recognized revenue is separate, only from finance-approved recognition entries/policy and never inferred from cash. Area is decorative, not cumulative. Negative net collections preserved. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | X bucket/date; Y money/currency; zero baseline and negative values preserved. |
| Filters | range, granularity, room, location, currency, timezone |
| Tooltip | Exact bucket/timezone, captured collections, successful refunds, net collections, currency/date basis/completeness; confirmedAt bookings on combo. Recognized revenue only if approved recognition data explicitly selected. |
| Contract | financialBuckets + bookingBuckets (combo only), with separate capture/refund event basis and confirmedAt count; recognitionBuckets separate optional contract. |
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
| Formula | Captured collections and successful refunds sum unique ledger events in event range; net collections = captures - refunds. Recognized revenue is separate finance-approved recognition entries by recognition/service policy date, null if no policy/entries. Average booking value = finalized quote totals / distinct eligible noncancelled-at-asOf bookings confirmed in range. Zero denominator → N/A. Delta = (current - previous)/abs(previous) ×100; zero/missing previous → N/A. |
| Population | Authorized venue/room dataset; finance ledger events, booking cohort or room/device snapshot as specified in formula; no client-page-only aggregates. |
| Units | LKR minor units with ISO currency and integer counts (separate scales) |
| Time basis | UTC instants; explicit Asia/Colombo [from,to) local reporting boundaries; named financial event / confirmedAt / createdAt / scheduled service / snapshot basis as formula states. |
| Axes/labels | No axes; each card has label, value/currency and named comparison basis. |
| Filters | range, room, location, currency, timezone |
| Tooltip | Each named metric, currency, distinct date basis, completeness, asOf and exact comparable period; missing recognition policy displays Unavailable. |
| Contract | financialSummary including sums, confirmedQuoteTotalMinor, eligibleConfirmedBookingCount, previous comparable range and asOf; all amounts per currency. |
| Authorized data source | Backend-authorized module read projections under analytics.read, room/device.read or payments.read where applicable; proposed contract, no actual endpoint verified. |
| Freshness | Response asOf/complete/warnings plus observedAt for snapshots; per-service refresh/stale threshold policy TBD, stale values labelled and retained. |
| Legend | Not required; label every card. Up/down is descriptive, not always good/bad. |
| Responsive | 1/2/4 columns base/sm/lg; values wrap without truncation. |
| Accessibility | Text legend/labels, focus/tap tooltip, non-color cues and equivalent authorized data table; keyboard controls and reduced motion. |
| Empty/error states | Complete empty dataset = valid zero counts; denominator zero = N/A; missing/partial/error = unavailable/partial with retry and freshness, never zero or DEMO fallback. |
| Colors | Net collections = `chart.revenue`; Captured payments = `chart.available`; Successful refunds = `chart.maintenance`; Average booking value = `chart.bookings`; Recognized revenue (only with approved recognition policy) = `chart.revenue` |

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
| Formula | Payment panel sums unique successful capture event amounts by verified provider/method, financial event range. Add-on panel sums finalized itemized sold add-on amounts and quantities for noncancelled confirmedAt cohort at asOf; PS4/extra-hour/fifth-guest lines use versioned quote types. Do not treat sold line items as cash or recognized revenue. Refund allocation shown only if authoritative line allocations exist; otherwise explicitly unavailable. |
| Population | Successful unique capture ledger events for payments; finalized quote item lines of eligible confirmedAt booking cohort for add-ons. |
| Units | Single ISO currency minor-unit amounts, quantities and distinct counts separately labelled. |
| Time basis | Payment event time versus booking confirmedAt cohort; Asia/Colombo boundaries and UTC instant storage. |
| Axes/labels | Separate panels: category vs money/quantity with explicitly selected unit; zero baseline; retain negative refund-adjusted views when supplied. |
| Filters | range, room, location, currency, timezone, paymentMethod, addonType |
| Tooltip | Method/type, amount/currency, quantity/count, exact cohort/date basis, refund allocation availability and asOf. |
| Contract | paymentBreakdown {methodId, capturedMinor, captureCount}; addonBreakdown {itemType, soldAddonMinor, quantity, bookingCount, cohortBasis, asOf}; per currency/scope. |
| Authorized data source | Payments ledger and Pricing/Booking immutable purchase-item projection; payments.read/analytics.read scope. |
| Freshness | Server asOf/complete; stale or missing allocation labelled. |
| Legend | Captured collections and sold add-on values are separate panels/populations; never a combined pie denominator. |
| Responsive | Horizontal bars on mobile; full labels and supporting tables remain. |
| Accessibility | Equivalent data table, non-color labels, focus/tap tooltips and keyboard filters. |
| Empty/error states | Valid complete empty counts zero; unknown allocations unavailable; error/partial dataset never invented zero. |
| Colors | Captured by provider/payment method = `chart.revenue`; Sold add-ons by type = `chart.bookings` |

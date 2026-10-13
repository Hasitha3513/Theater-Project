# CineNest Charts and Metrics

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Recorded: 2026-10-13. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Data authority and reusable configuration
[tokens/cinenest-chart-specs.json](tokens/cinenest-chart-specs.json) contains the shared machine-readable specification and proposed typed contracts; chart colors reference [design tokens](tokens/cinenest-design-tokens.json). No chart package, endpoints, database schema or existing chart components were found. Do not select a library or invent endpoint compatibility here. Backend adapters must map actual domain contracts and lifecycle states before rendering. All screenshots are illustrative; never seed production with screenshot counts, names, prices or percentages.

## Calculation and time conventions
Use server aggregates at the authorized room/location scope with matching data versions/asOf. Range is half-open [from,to); timestamps are RFC3339 instants stored in UTC, local buckets are calculated server-side using a validated IANA timezone. Proposed venue presentation is Asia/Colombo and LKR, inferred from references; both require configuration confirmation. Currency values are integer minor units and formatted with locale plus explicit ISO currency where ambiguous (Rs. alone is insufficient). Do not assume every currency has two decimals. No mixed-currency totals without approved conversion source, effective time and display disclosure.

Calendar “today” is local midnight to next local midnight in venue timezone, never browser time. Week starts Monday as a proposal pending business rules. Inclusive end-date UI converts to next local day exclusive. DST zones may produce 23/25-hour days and duplicate wall-clock hours; retain offset in ambiguous labels and unique instant bucket keys. Bucket boundaries and clipping come from server, not client timezone arithmetic. Future incomplete buckets remain gaps, not invented zeros.

Previous period: immediately preceding calendar period of equal selected calendar length in same timezone and granularity, excluding incomplete current bucket for a comparable comparison; server returns exact boundaries and completeness. Labels disclose comparison range; do not compare partial today with full yesterday silently. Missing comparison → N/A. Delta denominators use absolute previous value; zero baseline → N/A. Every dataset reports asOf/freshness. Async filters must not allow an older response to overwrite newer query data.

Revenue here is a provisional operational cash-event measure, **not certified accounting revenue**: successful captures minus successful refunds by event date. Confirm treatment of taxes, provider fees, settlement, failed chargebacks and revenue recognition with finance requirements before calling it net profit or accounting revenue. Refunds link to original booking/room for scope attribution but appear at refund event date. A transaction can have multiple captures/refunds: sum successful unique ledger events once by event ID. Prevent duplicate webhook counting. Booking counts use distinct booking IDs and a named date basis. Actual domain lifecycle maps must be reconciled; financial event counts cannot stand in for bookings.

Time utilization measures booked sellable interval minutes, not motion-detected attendance. Interval union/clipping, blackout exclusion and turnover rules belong to the backend. Operational snapshot occupancy and time utilization intentionally use different denominators and must be labelled. For all divisions, denominator zero → null/N/A. Complete empty financial/count bucket → zero; unavailable/partial bucket → null plus warning. Never connect chart lines across missing data by default. All rounded numbers retain unrounded source precision for aggregate computation.

## Shared presentation and contracts
Grid uses chart.grid; labels/axes use chart.axis; semantic series tokens below. Legends are visible, textual, stable across pages and keyboard-operable when toggled. Distinguish series through marks, patterns/dashes and accessible table equivalents. Tooltips work on focus/tap and hover, show original units/date basis; they never expose identity documents, PINs, secrets or unnecessary PII. Do not truncate amounts or omit currency. Plot titles include metric, scope and time basis. At small widths retain labels using horizontal bars or reduced tick density. No-data/error/partial-data states differ from valid zero.

All contract field descriptions and query/envelope fields appear in the JSON. Endpoints are intentionally unspecified. Require schema version, exact range, timezone, currency, asOf, completeness/warnings, and typed data. Authentication/authorization applies before aggregation and export. Date presets, room/location filters and currency are sent to backend; do not filter a paginated subset and call it a complete total. Refresh cadence is configured per service; pause background polling when hidden and label stale snapshots. Accessible equivalent table/export uses the same data and scope.

## Calculation examples (synthetic validation fixtures only)
- Captured 100000 minor units and successful refunds 20000 → recognized 80000; pending refund excluded. Two distinct confirmed bookings → count 2 regardless of capture count.
- Snapshot: 2 occupied, 2 available, 1 cleaning, 1 maintenance, 1 unknown → occupancy 50% (2/4), donut 7 rooms; excluded denominator count 3.
- Sellable [09:00,17:00), blackout [12:00,13:00) → 420 minutes; bookings [10:00,12:00) and [11:00,14:00) union then clipped → 180 booked minutes; utilization 42.857…%.
- Creation cohort 5, cancelled 1 → cancellation 20%; zero cohort → N/A. Prior revenue 0 → delta N/A, not infinite growth.

## Revenue and Bookings Chart

| Aspect | Specification |
|---|---|
| Chart type | bar-line |
| Colors | Recognized revenue = `chart.revenue`; Confirmed bookings = `chart.bookings` |
| Axes and labels | X local bucket label; left money/currency; right integer booking count. Both start at zero; signed financial axis may extend below zero. |
| Legends | Revenue bar and bookings line, visible with series toggle and equivalent table. |
| Tooltips | Bucket start/end/timezone, recognized revenue with currency, confirmed bookings, financial event basis and completeness. |
| Filters | range, granularity, room, location, currency, timezone |
| Metric calculations | Revenue = sum successful captured payment minor amounts - successful refund minor amounts, allocated to their respective financial event bucket; bookingCount = distinct booking IDs with confirmedAt inside bucket. Counts and money have separate event/date bases. |
| Data source contract | financialBuckets + bookingBuckets joined on exact start/end; never infer bookings from transaction counts. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Full width; minimum 240px plot; reduce ticks, stack legend; dual-axis labels retained, table available. |

## Room Occupancy Donut Chart

| Aspect | Specification |
|---|---|
| Chart type | donut |
| Colors | Occupied = `chart.occupied`; Available = `chart.available`; Cleaning = `chart.cleaning`; Maintenance = `chart.maintenance`; Unknown = `chart.unknown` |
| Axes and labels | No axes. Center labels operational snapshot occupancy and eligible denominator; never confuse with time utilization. |
| Legends | All five states with counts; occupied violet, available green, cleaning amber, maintenance red, unknown gray. |
| Tooltips | State, room count, share of all snapshot rooms, snapshotAt; unknown excluded from eligible rate. |
| Filters | location, room, snapshotAt |
| Metric calculations | Snapshot occupancy = occupied bookable rooms / (occupied + available bookable rooms) × 100. Denominator excludes cleaning, maintenance and unknown; show excluded count. Donut slices show all rooms in scope, not just denominator. No eligible rooms yields N/A. |
| Data source contract | roomSnapshot with mutually exclusive states, stateObservedAt and snapshotAt; explicit unknown/stale handling. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Minimum 240px graphic; legend below <lg; counts and table readable without hovering. |

## Revenue Trend Chart

| Aspect | Specification |
|---|---|
| Chart type | line-area |
| Colors | Recognized revenue = `chart.revenue` |
| Axes and labels | X bucket/date; Y money/currency; zero baseline and negative values preserved. |
| Legends | Recognized revenue; previous period dashed only when data supplied. |
| Tooltips | Bucket, captured, refunds, recognized revenue, currency, completeness and optional comparison. |
| Filters | range, granularity, room, location, currency, timezone |
| Metric calculations | Same financial event revenue definition as revenue-bookings; area is decorative transparent series color, not cumulative revenue. |
| Data source contract | financialBuckets with capturedMinor/refundedMinor/recognizedRevenueMinor and optional comparable previous buckets. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Full width; fewer ticks; area may be removed at small widths without removing values. |

## Popular Time Slots Chart

| Aspect | Specification |
|---|---|
| Chart type | bar |
| Colors | Bookings started = `chart.bookings` |
| Axes and labels | X labelled local time bands (24-hour or locale time); Y integer booking count from zero. |
| Legends | Bookings started; label rankings as tied when counts equal. |
| Tooltips | Band including AM/PM or 24-hour clarity, booking count, range and timezone. |
| Filters | range, room, location, timezone, slotBandDefinition |
| Metric calculations | Distinct confirmed noncancelled booking IDs whose scheduled start falls within selected range, grouped by configured local hour band. Explicit business bands [startHour,endHour); cross-midnight bands split or carry dayOffset. Popularity is count, not occupied minutes. |
| Data source contract | slotBuckets with bandId, label, startHour/endHour/dayOffset and bookingCount; band definitions from configured operating hours. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Horizontal bars at <sm; preserve complete time-band labels; no ambiguous duplicate 9–12. |

## Room Utilization Chart

| Aspect | Specification |
|---|---|
| Chart type | horizontal-bar |
| Colors | Time utilization = `chart.occupied` |
| Axes and labels | Y room label; X 0–100 percent. Never cap invalid >100 data silently; flag contract error. |
| Legends | Time utilization; optional comparison dashed, not another occupancy definition. |
| Tooltips | Room, booked/sellable minutes, percent, excluded intervals and range/timezone. |
| Filters | range, room, location, timezone |
| Metric calculations | For each room: union of noncancelled confirmed booking intervals intersected with sellable intervals and range, divided by union of sellable intervals within range × 100. Sellable intervals = configured operating intervals minus maintenance/blackouts and policy-defined turnaround. Count interval overlaps once. Zero sellable minutes → null/N/A. |
| Data source contract | roomUtilization with roomId/name, bookedMinutes, sellableMinutes, utilizationPercent and completeness; server resolves blackout/buffer policy. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Vertical list of horizontal bars; long names wrap and table remains available. |

## Booking Statistics

| Aspect | Specification |
|---|---|
| Chart type | stacked-bar-and-summary |
| Colors | Confirmed = `chart.available`; Ongoing = `chart.bookings`; Completed = `chart.occupied`; Cancelled = `chart.maintenance`; Other states = `chart.unknown` |
| Axes and labels | X creation bucket; Y integer booking count, zero baseline. |
| Legends | All included states, with text and stable color; Other states expands in data table. |
| Tooltips | Bucket, each state count, cohort total, cancellation rate, asOf. |
| Filters | range, granularity, room, location, timezone |
| Metric calculations | Cohort = distinct booking IDs with createdAt within range; snapshot state at asOf partitions cohort once. Total = sum all mutually exclusive state counts including other. Cancellation rate = cancelled cohort / total cohort × 100; zero cohort → N/A. Separate confirmedAt KPI is not interchangeable with this creation cohort. |
| Data source contract | bookingStateBuckets with cohort basis createdAt, asOf and all backend states mapped to documented display buckets; totals from same dataset. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | Stacked chart stacks above summary cards <lg; table retains unmapped states. |

## Financial KPI Cards

| Aspect | Specification |
|---|---|
| Chart type | kpi-cards |
| Colors | Recognized revenue = `chart.revenue`; Captured payments = `chart.available`; Successful refunds = `chart.maintenance`; Average booking value = `chart.bookings` |
| Axes and labels | No axes; each card has label, value/currency and named comparison basis. |
| Legends | Not required; label every card. Up/down is descriptive, not always good/bad. |
| Tooltips | Metric definition, date basis, previous exact period, completeness, asOf. |
| Filters | range, room, location, currency, timezone |
| Metric calculations | Captured = sum successful captures in event range; refunded = sum successful refunds in event range; recognized = captured - refunded. Average booking value = sum finalized quote totals of bookings confirmed in range / distinct such bookings, rounded for display only; excludes cancelled-at-asOf cohort and labelled asOf. Denominator 0 → N/A. Period delta = (current - previous) / abs(previous) ×100; previous 0 or missing → N/A. Revenue may be negative. |
| Data source contract | financialSummary including sums, confirmedQuoteTotalMinor, eligibleConfirmedBookingCount, previous comparable range and asOf; all amounts per currency. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | 1/2/4 columns base/sm/lg; values wrap without truncation. |

## System and IoT Status Indicators

| Aspect | Specification |
|---|---|
| Chart type | status-list-and-timeline |
| Colors | Online = `chart.available`; Degraded = `chart.cleaning`; Offline = `chart.maintenance`; Unknown = `chart.unknown` |
| Axes and labels | Status list has no axes; optional timeline X instant/date, Y state per service/device lane. |
| Legends | Online/degraded/offline/unknown with icons and words; no all-green default. |
| Tooltips | Entity, status, observedAt/timezone, age, freshness threshold and safe diagnostic. |
| Filters | location, room, deviceType, service, range |
| Metric calculations | Device state from authoritative health policy and lastSeen: stale after per-type threshold, unknown when never observed; connectivity alone does not imply command success. Fleet online rate = online / registered enabled scoped devices ×100, unknown included; 0 devices → N/A. System operational only when all required checks explicitly healthy and fresh; missing/degraded checks forbid operational claim. |
| Data source contract | serviceHealth and deviceHealth with status, observedAt, staleAfterSeconds, required, safeReason and telemetry schema version. |
| Date/timezone | Shared half-open range and server-local buckets above; snapshot/health uses observedAt/asOf instants. |
| Responsive layout | List wraps to cards; timeline scrolls in labelled region; equivalent event table. |

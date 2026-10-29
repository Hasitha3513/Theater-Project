# CineNest User Guide — පරිශීලක මාර්ගෝපදේශය

Version: source `24b73caf16eebda1490d0f3552c268d15e024229`, 2026-10-22. [CI 39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759). [Feature catalogue](../product/FEATURE_CATALOG.md). මෙම guide එක software instructions පමණි; production හෝ formal acceptance නොවේ.

## භාවිතයට පෙර

මෙය **SYNTHETIC DEMONSTRATION ONLY** පරිසරය සඳහාය. Operator සකසා දුන් isolated test පරිසරයක්, test account/session හා අවශ්‍ය venue-scoped grants තිබිය යුතුය. සැබෑ NIC/passport, bank receipt, මුදල්, credentials හෝ device commands භාවිත නොකරන්න. Role headings රැකියා වගකීම් අනුව navigation පහසු කිරීමට පමණි; Administrator යන නමෙන් bypass නොලැබේ. මේ ලේඛනයෙන් account, grant, appointment හෝ bootstrap සාදන්නේ නැත.

## පොදු errors සහ ආරක්ෂාව

401 / Session expired: protected තොරතුරු තවදුරටත් විශ්වාස නොකර operator අනුමත login මගින් session නැවත තහවුරු කරන්න. 403: නිවැරදි venue සහ වෙනම permission අවශ්‍යයි; නැවත නැවත submit නොකරන්න. 404: reference සහ ownership තහවුරු කරන්න; වෙනත් customer reference සොයන්න එපා. 409 / Stale: context/revision නැවත load කර වෙනස්කම් කියවා explicit consent/action නැවත දෙන්න. 429/503/network error: success හෝ zero අනුමාන නොකරන්න; UI ලබාදෙන explicit retry පමණක් භාවිත කරන්න. Mutation outcome unknown නම් අලුත් command එකක් නොසාදා පවතින command recovery අනුගමනය කරන්න.

Keyboard සඳහා Tab / Shift+Tab, Enter සහ checkbox සඳහා Space භාවිත කරන්න. Focus සලකුණ බලන්න. Disabled controls permissions/eligibility ඉක්මවා භාවිත නොකරන්න. Screenshot සඳහා private evidence, PIN, token හෝ sensitive payment details ගන්න එපා. Calendar/quote/payment request එකක් physical entry හෝ booking confirmation නොවේ.

## Customer

### UG-001 — Check session

- **Purpose / අරමුණ:** Check session; [DOC-F001](../product/FEATURE_CATALOG.md#doc-f001) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `authenticated eligible account`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/account`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/account` විවෘත කරන්න. → `Check session` තෝරන්න. → ඉවත් වීමට `Log out of current session` තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** සේවාදායකයෙන් ලැබුණු session තත්ත්වය පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** මෙම පිටුවෙන් production account එකක් හෝ staff අවසරයක් සාදන්නේ නැත.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/session-panel.tsx), [IdentityIntegrationTest](../../backend/src/test/java/com/cinenest/IdentityIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-002 — Explore rooms

- **Purpose / අරමුණ:** Explore rooms; [DOC-F002](../product/FEATURE_CATALOG.md#doc-f002) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `eligible customer; server discovery policy`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/rooms`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/rooms` විවෘත කරන්න. → `Venue`, `Name or code`, `Minimum capacity`, `Facility` පෙරහන් අවශ්‍ය නම් භාවිත කරන්න. → `Room details` තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** තෝරාගත් කාමරයේ තොරතුරු පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** රූප illustrative විය හැක; කාමරය හිස් හෝ ආරක්ෂිත බව මෙයින් තහවුරු නොවේ.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/catalog.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/rooms.tsx), [RoomsIntegrationTest](../../backend/src/test/java/com/cinenest/RoomsIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-003 — Calendar selection

- **Purpose / අරමුණ:** Calendar selection; [DOC-F004](../product/FEATURE_CATALOG.md#doc-f004) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `eligible customer; room visibility`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/rooms/[roomId]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** කාමර විස්තර පිටුවේ calendar පෝරමයේ දිනය, කාලය සහ අමුත්තන් ගණන පුරවන්න. → calendar කියවීම පැහැදිලිව ඉල්ලන්න. → ලබාගත හැකි ආරම්භයක් තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** අදාළ venue timezone අනුව candidate වේලාව තෝරාගැනීමට ලැබේ.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Calendar එක බැලීම හෝ වේලාවක් තේරීම hold එකක් සාදන්නේ නැත. UNKNOWN සහ PENDING_RECONCILIATION ලබාගත හැකි බවක් නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/customer-calendar.tsx), [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-004 — Create temporary hold

- **Purpose / අරමුණ:** Create temporary hold; [DOC-F005](../product/FEATURE_CATALOG.md#doc-f005) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `eligible customer; owned hold`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/rooms/[roomId]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `Customer start in venue local time`, `Customer duration (minutes)`, `Guests` පුරවන්න. → `Check inventory (no hold)` තෝරන්න. → අවසර ලැබුණොත් `Create temporary inventory hold` තෝරන්න. → `Refresh hold state` මගින් යාවත්කාලීන කරන්න.
- **Expected result / ප්‍රතිඵලය:** සේවාදායකයෙන් hold reference, state සහ expiry ලැබේ.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** කල් ඉකුත් වූ hold භාවිත නොකරන්න. Outcome unresolved නම් `Retry original command` භාවිත කරන්න; අලුත් command එකක් නොසාදන්න.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/inventory.tsx), [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-005 — Review and accept quotation

- **Purpose / අරමුණ:** Review and accept quotation; [DOC-F006](../product/FEATURE_CATALOG.md#doc-f006) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `eligible customer; owned quote and hold`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/rooms/[roomId]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** එම කාමරයේ supported package තෝරන්න. → quotation පෝරමය හා itemized ගණනය කියවන්න. → `Accept this revision` කොටසේ disclosures තුන කියවන්න. → `I reviewed this exact synthetic revision and all three disclosures` පැහැදිලිව තෝරා එම revision එක පිළිගන්න.
- **Expected result / ප්‍රතිඵලය:** එම revision සඳහා authoritative acceptance තත්ත්වය ලැබේ.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** මිල හෝ revision වෙනස් නම් නැවත කියවන්න; quote acceptance ගෙවීමක් හෝ booking confirmation එකක් නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/quotes.tsx), [QuoteIntegrationTest](../../backend/src/test/java/com/cinenest/QuoteIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-006 — Synthetic verification

- **Purpose / අරමුණ:** Synthetic verification; [DOC-F007](../product/FEATURE_CATALOG.md#doc-f007) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `ownership; staff verification.review; separate verification.evidence.read`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/verification`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** Eligible quote එකෙන් `Continue to Verification` භාවිත කරන්න. → ආරක්ෂිත quotation summary කියවන්න. → operator ලබාදුන් `Operator-provided synthetic policy version` පමණක් ඇතුළත් කරන්න. → unchecked consent තෝරන්න. → `Start synthetic request` තෝරන්න. → UI ඉඩදෙන test evidence පමණක් පැහැදිලි ක්‍රියාවකින් යවන්න.
- **Expected result / ප්‍රතිඵලය:** PENDING හෝ reviewer ලබාදුන් authoritative තත්ත්වය පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** සැබෑ NIC/passport භාවිත නොකරන්න. Quote/policy වෙනස් වුවහොත් නැවත consent අවශ්‍යයි; rejected තත්ත්වය approval නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/verification.tsx), [VerificationIntegrationTest](../../backend/src/test/java/com/cinenest/VerificationIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-007 — Bank request and receipt

- **Purpose / අරමුණ:** Bank request and receipt; [DOC-F008](../product/FEATURE_CATALOG.md#doc-f008) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `authenticated owner; protected readiness`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/bank`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** Verification හි server-approved continuation භාවිත කරන්න. → `/bank` හි loaded quotation/readiness කියවන්න. → අවසර ඇති විට පමණක් request creation පැහැදිලිව ඉල්ලන්න. → තම request context තුළ claim සහ synthetic receipt පෝරමයේ අනිවාර්ය fields පුරවා වෙන වෙනම submit කරන්න.
- **Expected result / ප්‍රතිඵලය:** තම request, claim generation සහ evidence state reload කර පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** සැබෑ මුදල් හෝ receipts නොයවන්න. Upload uncertain නම් READY ලෙස නොසලකන්න; UI දෙන recovery එකෙන් පමණක් ඉදිරියට යන්න.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/bank.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

**Verified pending/recovery clarification—CN-R1-BOOKING-JOURNEY-E2E-001:** Create response timeout වුවහොත් payment/booking සාර්ථක බව හෝ අසාර්ථක බව අනුමාන නොකරන්න. UI පෙන්වන **Retry original bank request** ක්‍රියාව explicit ලෙස භාවිත කරන්න;existing implementation එක same command/payload යළි භාවිත කරයි. තම request reload කිරීමේදී OPEN/PENDING සහ UNFUNDED/NOT_CONFIRMED යනු verified funds හෝ confirmed booking නොවේ. අලුත් payment/hold එකක් ස්වයංක්‍රීයව නොයවන්න. Real-stack37PASS evidence සහ private local375/768/1280screenshots [PROGRESS](../PROGRESS.md#release1-booking-journey) හි සඳහන්ය;ඉහත historical source-inspection receipt සහ portable screenshot limitation retained. සැබෑ මුදල්/IDs/door operations සඳහා අවසරයක් නැත.

**Recovery—CN-R1-CUSTOMER-BOOKING-RECOVERY-001:** Refresh/revisit පසු **My bookings → Refresh booking history → View Bank Request** භාවිත කරන්න. Pending request එක purchased booking එකක් නොවේ. Unknown result සඳහා existing original-command retry හෝ owned history පරීක්ෂා කරන්න;අලුත් payment/hold එකක් නොයවන්න. Session unavailable නම් නැවත sign in කර history explicit ලෙස refresh කරන්න. [Real-stack evidence](../PROGRESS.md#release1-customer-recovery).

### UG-008 — Booking history and details

- **Purpose / අරමුණ:** Booking history and details; [DOC-F009](../product/FEATURE_CATALOG.md#doc-f009) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `authenticated owner`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/bookings`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/bookings` විවෘත කරන්න. → server ලැයිස්තුවෙන් නිවැරදි booking තෝරන්න. → detail reload කළ පසු purchased room/package/session සහ status කියවන්න.
- **Expected result / ප්‍රතිඵලය:** තම booking එකේ authoritative purchased summary පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** SIMULATED_CONFIRMED පමණක් synthetic confirmation; claim, partial allocation හෝ unknown outcome confirmation නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/customer-bookings.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-009 — Guest roster

- **Purpose / අරමුණ:** Guest roster; [DOC-F010](../product/FEATURE_CATALOG.md#doc-f010) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `ownership; staff guest.synthetic.read independently`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/bookings/[id]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** තම `/bookings/[id]` විවෘත කරන්න. → roster හි purchased limit කියවන්න. → enabled synthetic guest form හි වලංගු test entry එකක් පුරවන්න. → එක් කිරීම පැහැදිලිව submit කර reload කරන්න.
- **Expected result / ප්‍රතිඵලය:** Capacity සහ duplicate checks අනුව server roster එක යාවත්කාලීන වේ.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** අමුත්තෙකුගේ නම ඇතුළත් කිරීම වයස හෝ admission තහවුරු නොකරයි; automatic guest addition නැත.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/guest-roster.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

Current synthetic roster input: use only `SYNTHETIC-1` through `SYNTHETIC-999999` companion labels; never real guest names or identity documents. Use the existing explicit roster initialization/add actions; self-declaration is not age or admission evidence.

### UG-010 — Amendment review — PARTIAL

- **Purpose / අරමුණ:** Amendment review — PARTIAL; [DOC-F011](../product/FEATURE_CATALOG.md#doc-f011) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `owned booking; current server eligibility`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/bookings/[id]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** තම booking detail හි server ලබාදුන් amendment actions පමණක් භාවිත කරන්න. → proposal සහ purchased terms අතර වෙනස කියවන්න. → server ඉඩ දෙන explicit acceptance තෝරා reload කරන්න.
- **Expected result / ප්‍රතිඵලය:** සාර්ථක නම් නව committed purchased revision පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** සියලු cancellation/extension ක්‍රියා මෙහි ඇතැයි නොසලකන්න; action නොපෙන්වයි නම් URL වෙනස් කර බලය ලබාගත නොහැක.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/booking-amendment.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-011 — Notification inbox

- **Purpose / අරමුණ:** Notification inbox; [DOC-F024](../product/FEATURE_CATALOG.md#doc-f024) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Customer; permissions: `own notification.read; notification.admin.read and separate queue-command grants`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/notifications`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/notifications` විවෘත කරන්න. → තම පණිවිඩය තෝරා කියවන්න. → තිබෙන mark-read ක්‍රියාව පැහැදිලිව තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** Inbox record හි read state පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** මෙය live SMS/email ලැබුණු බවට සාක්ෂියක් නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/notifications.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

## Reception / Front Desk Staff

### UG-012 — Staff booking lookup

- **Purpose / අරමුණ:** Staff booking lookup; [DOC-F014](../product/FEATURE_CATALOG.md#doc-f014) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Reception / Front Desk Staff; permissions: `booking.synthetic.read at exact venue`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/bookings`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/bookings` විවෘත කරන්න. → තම grant එකට අදාළ venue හා filters පුරවා කියවීම ඉල්ලන්න. → ලැයිස්තුවෙන් exact booking detail තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** එම venue හි අවසර ලත් booking summary පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Front Desk යන රැකියා නාමයෙන් permission නොලැබේ; booking.synthetic.read අවශ්‍යයි.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/staff-bookings.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-013 — Staff guest inspection

- **Purpose / අරමුණ:** Staff guest inspection; [DOC-F010](../product/FEATURE_CATALOG.md#doc-f010) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Reception / Front Desk Staff; permissions: `booking.synthetic.read for staff detail; independent same-venue guest.synthetic.read for roster`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/bookings/venues/[venueId]/[booking]`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** Staff booking detail තුළ guest roster section භාවිත කරන්න. → exact booking/venue තහවුරු කරන්න. → read action පැහැදිලිව ඉල්ලන්න.
- **Expected result / ප්‍රතිඵලය:** guest.synthetic.read තිබේ නම් synthetic roster පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Booking-read පමණක් guest-read නොවේ; physical admission නොදෙන්න.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/staff-guest-roster.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

Current route/permission/source guidance rechecked at `ebef814649893926f49023e51e27d7efbbc9af15` (CI39929478983 SUCCESS); this is source inspection, not a fresh browser test.

## Finance Staff

### UG-014 — Synthetic bank review — PARTIAL

- **Purpose / අරමුණ:** Synthetic bank review — PARTIAL; [DOC-F016](../product/FEATURE_CATALOG.md#doc-f016) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Finance Staff; permissions: `bank.claim.review; separate evidence/credit/allocate grants`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/bank`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/bank` හි තම venue queue load කරන්න. → exact request තෝරන්න. → claim/evidence review බලය තිබේ නම් පමණක් කියවන්න. → තමන්ට server ලබාදුන් explicit review action පමණක් භාවිත කරන්න.
- **Expected result / ප්‍රතිඵලය:** Review state සහ history reload වේ.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Evidence review, independent credit verification සහ allocation වෙනම බලතලයි; මෙම මාර්ගෝපදේශය real banking ක්‍රියා සඳහා අවසරයක් නොවේ.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/bank.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-015 — Refund review — PARTIAL

- **Purpose / අරමුණ:** Refund review — PARTIAL; [DOC-F017](../product/FEATURE_CATALOG.md#doc-f017) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Finance Staff; permissions: `refund.synthetic.read/review/approve/destination.verify/execute/reconcile separately`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/refunds`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/refunds` වෙත යන්න හෝ staff booking හි protected refund context විවෘත කරන්න. → exact obligation හා attempt state කියවන්න. → දැනට අවසර ඇති explicit action පමණක් තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** Authoritative refund obligation/attempt තත්ත්වය පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Approval සහ repayment වෙනස්ය; sensitive destination details නොබෙදාහරින්න. සැබෑ refund නොකරන්න.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/refunds.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-016 — Read synthetic bank report

- **Purpose / අරමුණ:** Read synthetic bank report; [DOC-F018](../product/FEATURE_CATALOG.md#doc-f018) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Finance Staff; permissions: `report.bank.synthetic.read`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/reports/bank`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin` හි `Synthetic Bank Summary` පෝරමයට venue, currency සහ venue-local date range ඇතුළත් කරන්න. → `Load Bank Summary` තෝරන්න. → අවශ්‍ය නම් detailed report link භාවිත කරන්න.
- **Expected result / ප්‍රතිඵලය:** Server විසින් ලැබුණු synthetic credit/allocation/refund summary පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** report.bank.synthetic.read ස්වාධීනව අවශ්‍යයි; නොලැබුණු data සඳහා zero නොඅනුමාන කරන්න.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/analytics.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/bank-report.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

## Venue Manager

### UG-017 — Booking statistics

- **Purpose / අරමුණ:** Booking statistics; [DOC-F019](../product/FEATURE_CATALOG.md#doc-f019) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Venue Manager; permissions: `report.booking.synthetic.read`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin` හි `Synthetic Booking Confirmations` වෙත යන්න. → `Statistics venue reference`, optional room, `From date (inclusive)`, `To date (exclusive)` පුරවන්න. → `Load statistics` තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** Total, daily confirmations, room distribution, timezone සහ observed time පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** දින 31 සීමාවයි; confirmed_at cohort එකයි. Popular session starts, revenue හෝ cancellation rate නොවේ.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/analytics.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/booking-confirmations.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-018 — Package publication — PARTIAL

- **Purpose / අරමුණ:** Package publication — PARTIAL; [DOC-F026](../product/FEATURE_CATALOG.md#doc-f026) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Venue Manager; permissions: `package.read/edit/publish/deactivate separately`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/settings/packages`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/settings/packages` විවෘත කරන්න. → authorized venue/package තෝරන්න. → current revision සහ structured comparison කියවන්න. → backend දක්වන edit/preview/publication controls පමණක් පැහැදිලිව භාවිත කරන්න.
- **Expected result / ප්‍රතිඵලය:** සාර්ථක ක්‍රියාවෙන් නව draft/publication state සහ history පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Editor වීම publisher වීම නොවේ; purchased terms නැවත ලියන්නේ නැත. UI confirmation සහ independent rules සුරකින්න.
- **Screenshot:** NOT_CAPTURED — මෙම workflow සඳහා portable screenshot නැත; design mockup එක implementation evidence නොවේ.
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/publications.tsx), [PublicationIntegrationTest](../../backend/src/test/java/com/cinenest/PublicationIntegrationTest.java). Documentation review is not a fresh browser execution.

## System Administrator

### UG-019 — Managed grant inspection

- **Purpose / අරමුණ:** Managed grant inspection; [DOC-F023](../product/FEATURE_CATALOG.md#doc-f023) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** System Administrator; permissions: `identity.grant.read at exact venue`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/accounts`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/accounts` හි `Managed Grant Inspection` වෙත යන්න. → `Venue reference`, `Account reference`, `Rows per page` පුරවන්න. → `Load managed grants` තෝරන්න. → server ඉඩ දෙන `Next page` පමණක් භාවිත කරන්න.
- **Expected result / ප්‍රතිඵලය:** booking/guest managed subset එක හා observation metadata පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** identity.grant.read අවශ්‍යයි. Actual assignments UNASSIGNED; bootstrap/delegation DISABLED. Grant assign/approve/revoke නැත.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/grants.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/managed-grant-inspection.tsx), [ManagedGrantReadIntegrationTest](../../backend/src/test/java/com/cinenest/ManagedGrantReadIntegrationTest.java). Documentation review is not a fresh browser execution.

## Operations / Maintenance Staff

### UG-020 — Turnover queue — PARTIAL

- **Purpose / අරමුණ:** Turnover queue — PARTIAL; [DOC-F020](../product/FEATURE_CATALOG.md#doc-f020) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Operations / Maintenance Staff; permissions: `operations.synthetic.read; assign/record/review independently`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/operations`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/operations` හි `Venue ID` පුරවා `Load queue` තෝරන්න. → current page filter හා `Previous` / `Next` භාවිත කරන්න. → case තෝරා server ඉඩ දෙන explicit synthetic record/review ක්‍රියා පමණක් භාවිත කරන්න.
- **Expected result / ප්‍රතිඵලය:** Recorded lifecycle, assignment හා exceptions පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** Case resolved වූවත් occupancy UNKNOWN, readiness UNVERIFIED; cleaning හෝ physical safety තහවුරු නොවේ.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/operations.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/operations.tsx), [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java). Documentation review is not a fresh browser execution.

### UG-021 — Maintenance Impact Preview

- **Purpose / අරමුණ:** Maintenance Impact Preview; [DOC-F021](../product/FEATURE_CATALOG.md#doc-f021) යටතේ විස්තර කළ bounded software භාවිතය.
- **Who / කවුද:** Operations / Maintenance Staff; permissions: `inventory.read independently`. Exact server authorization අවශ්‍යයි.
- **Prerequisites / පෙර අවශ්‍යතා:** සුදුසු test session, නිවැරදි venue/owned context, operator-approved synthetic fixtures; ඉහත පොදු ආරක්ෂක සීමා අදාළයි.
- **Navigation:** `/admin/operations`. `[id]` ආදී placeholders වෙනුවට UI ලබාදෙන exact opaque reference භාවිත කරන්න.
- **Steps / පියවර:** `/admin/operations` හි `Maintenance Impact Preview` පෝරමය තෝරන්න. → `Venue reference`, `Room reference`, venue-local start/end පුරවන්න. → `Load Preview` තෝරන්න. → අසාර්ථක නම් context තහවුරු කර `Retry` තෝරන්න.
- **Expected result / ප්‍රතිඵලය:** Interval, server observation හා authoritative conflict/completeness පෙන්වයි.
- **Errors / දෝෂ:** ඉහත පොදු error ක්‍රමය අනුගමනය කරන්න; backend response නැතිනම් සාර්ථකයි කියා නොසලකන්න.
- **Security and limitations / සීමා:** inventory.read ස්වාධීනව අවශ්‍යයි. Empty conflicts closure/reopening/admission සඳහා අවසරයක් නොවේ.
- **Screenshot:** [Synthetic browser-test capture](../product/screenshots/operations.png)
- **Verification:** PARTIAL full workflow/operational qualification; source-inspected `24b73caf16eebda1490d0f3552c268d15e024229`; CI above. [Source](../../frontend/components/maintenance-impact.tsx), [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java). Documentation review is not a fresh browser execution.

## අනෙකුත් සීමා සහ maintenance

Access සහ Lighting simulator තිර `/access`, `/admin/access`, `/admin/iot` වල තිබේ. ඒවා physical lock/light ක්‍රියාකරන බවට සාක්ෂි නොවේ; මෙහි live-operation steps සපයා නැත. Customer `/refunds` සහ staff venue/room configuration capabilities catalogue තුළ සටහන් වේ; සියලු command variants සඳහා සම්පූර්ණ training coverage මෙහි නොමැත. UI examples යනු training/product workflow නොවේ.

Loyalty/referral/promotion ක්‍රියා DEFERRED; PayHere DEFERRED/BLOCKED. Popular Time Slots හි fixed-hour synthetic report ඇත;configured business bands තවම outstanding. Production recovery, physical admission සහ real banking qualified නැත. D0–D6 NOT_HELD / NOT_ACCEPTED. මෙම guide එකෙන් roadmap හෝ requirement acceptance වෙනස් නොවේ.

UI workflow වෙනස් කරන අනාගත authorized task එකේ අදාළ UG section සහ catalogue evidence එකම commit තුළ යාවත්කාලීන කරන්න. Screenshot provenance [catalogue](../product/FEATURE_CATALOG.md#screenshot-provenance-and-coverage) තුළ ඇත; mockups සහ browser fixtures වෙන්කර හඳුන්වන්න. අලුත් feature අදහස් [FUTURE_FEATURES](../backlog/FUTURE_FEATURES.md) තුළ පමණක් සටහන් කරන්න; ක්‍රියාත්මක කිරීම සඳහා වෙනම අවසර අවශ්‍යයි.

<a id="ug-022"></a>
## Venue reporting staff

### UG-022 — Popular Reserved Session Start Times

- **Purpose / අරමුණ:** Synthetic reserved session ආරම්භක පැය බෙදාහැරීම කියවීම;[DOC-F027](../product/FEATURE_CATALOG.md#doc-f027).
- **Who / කවුද:** Current eligible venue member සහ එම venue සඳහා report.booking.synthetic.read අවසරය ඇති staff පමණි. Administrator නම හෝ bank/booking read අවසරය ප්‍රමාණවත් නොවේ.
- **Prerequisites / පෙර අවශ්‍යතා:** Operator-approved synthetic test environment සහ session;නිවැරදි venue reference;දින31ට නොවැඩි venue-local interval. මේ guide එක grant හෝ bootstrap නොසාදයි.
- **Navigation:** /admin → Popular Reserved Session Start Times.
- **Steps / පියවර:** 1. Popular slots venue reference ඇතුළත් කරන්න. 2. අවශ්‍ය නම් එම venue හි Popular slots room reference (optional) ඇතුළත් කරන්න. 3. Popular slots from date (inclusive) සහ Popular slots to date (exclusive) පුරවන්න;අවසන් දිනය ඇතුළත් නොවේ. 4. Load Report තෝරන්න. 5. Venue timezone,Observed at,Reporting interval සහ Total synthetic reservations තහවුරු කරන්න. 6. එක් පැයක bar එකේ දිග/පෙන්වන count කියවන්න;පහළ Equivalent hourly counts table හි එම නිවැරදි ගණනම ඇත. 7. අවශ්‍ය නම් Retry මගින් අලුතින් කියවන්න;Clear මගින් protected result ඉවත් කරන්න.
- **Expected result / ප්‍රතිඵලය:** Server එකෙන් ලැබුණු සම්පූර්ණ24 hourly buckets;complete empty interval නම් සියලු counts zero සහ empty message. Date/room වෙනස් කිරීමෙන් old result ඉවත් වෙයි;නැවත explicit Load Report අවශ්‍යයි.
- **Errors / දෝෂ:** Session expired නම් අනුමත login නැවත තහවුරු කරන්න. Access denied නම් exact venue permission සොයාබලන්න;වෙනත් venue grant එකක් එකතුකර බලය ලබාගත නොහැක. Dates/reference වැරදි නම් නිවැරදි කර Retry තෝරන්න. Backend/audit unavailable නම් values නොපෙන්වයි;zero අනුමාන නොකරන්න.
- **Security and limitations / සීමා:** Synthetic reserved session starts only. Not actual attendance,occupancy,revenue or physical readiness. confirmed_at භාවිත නොකර latest purchased sessionStart ගනී. Venue timezone සහ committed amendments වෙනස් වුවහොත් පෙර දැකපු counts වෙනස් විය හැක. Fixed hourly bands පමණි;configured business bands/full requirement acceptance outstanding. No booking/financial/device mutation.
- **Screenshot:** [375px](../product/screenshots/popular-375.png),[768px](../product/screenshots/popular-768.png),[1280px](../product/screenshots/popular-1280.png) — actual intercepted synthetic browser captures;real-backend E2E හෝ production qualification නොවේ.
- **Verification:** PARTIAL full requirement;locally tested feature checkpoint based on338db745371a64c10845eee334d2d6a7104b1e6a. Publication commit=this record's Git history;new exact-source CI PENDING. [Source](../../frontend/components/popular-slots.tsx),[tests](../../frontend/tests/popular-slots.test.mjs),[actual evidence](../PROGRESS.md#popular-session-starts).

<a id="ug-023"></a>
## Customer and independently authorized staff

### UG-023 — Synthetic pre-start cancellation

- **Purpose / අරමුණ:** [DOC-F028](../product/FEATURE_CATALOG.md#doc-f028) හි bounded synthetic cancellation භාවිත කිරීම. මෙය සැබෑ commercial cancellation හෝ refund repayment එකක් නොවේ.
- **Who / කවුද:** Eligible booking owner හෝ එම venue හි current eligible staff member සහ independent booking.synthetic.cancel අවසරය. Staff detail කියවීමේ අවසරය cancellation බලය ලබා නොදෙයි. Grants/bootstrap UNASSIGNED/DISABLED ලෙසම පවතී.
- **Prerequisites / පෙර අවශ්‍යතා:** Session ආරම්භයට පෙර;කිසිදු credential එකක් පෙර issue නොවී තිබීම;pending amendment නොමැතිවීම;financial funding reconciled වීම. Revoked/expired credential එකක් තිබුණත් මෙම cancellation path ප්‍රතික්ෂේප වේ.
- **Navigation:** Customer /bookings → exact booking detail → Booking cancellation. Staff /admin/bookings → exact venue booking detail → Staff booking cancellation.
- **Steps / පියවර:** 1. Check Cancellation Eligibility තෝරන්න. 2. Server දක්වන reason සහ purchased context කියවන්න;permission/session error නම් ඉදිරියට නොයන්න. 3. Eligible නම් consequences කියවන්න:current inventory release සහ100% eligible principal එක financial review සඳහා reserve වීම. 4. Cancellation reason පුරවන්න;identity/bank/access secrets ඇතුළත් නොකරන්න. 5. මුලින් unchecked acknowledgement එක තෝරන්න. 6. Confirm synthetic cancellation පැහැදිලිව තෝරන්න. Command එක auto-submit නොවේ. 7. Backend committed SIMULATED_CANCELLED සහ separate refund-obligation summary තහවුරු කරන්න.
- **Expected result / ප්‍රතිඵලය:** RESERVED_REVIEW_REQUIRED යනු refund approval,ගෙවීම හෝsettlement නොවේ. Purchased history සහ original allocation රඳාපවතී. Physical readiness UNVERIFIED ය. Technical references පහළ Cancellation status references තුළ ඇත;private evidence/actor identities නොපෙන්වයි.
- **Errors / දෝෂ:** Session/permission/missing/conflict/service errors මත protected result ඉවත් වේ. Check Cancellation Eligibility මගින් authoritative status නැවත කියවන්න. Outcome uncertain නම් success හෝfailure අනුමාන නොකරන්න. Backend තවම eligible කියයි නම් Retry same cancellation command පමණක් explicit acknowledgement පසු භාවිත කළ හැක;original reason/key/versions වෙනස් නොවේ. Automatic duplicate submission නොවේ. Booking/venue/session/navigation වෙනස් වූ විට protected state clear වේ.
- **Security and limitations / සීමා:** Started,ever-issued,pending amendment හෝuncertain funding මෙම UI එකෙන් bypass කළ නොහැක. No real money,credential revocation,physical safety,emergency bypass හෝproduction authority. Existing full MVP obligations/acceptance unchanged.
- **Screenshot:** [Customer375](../product/screenshots/cancellation-after-customer-375.png),[768](../product/screenshots/cancellation-after-customer-768.png),[1280](../product/screenshots/cancellation-after-customer-1280.png);[Staff375](../product/screenshots/cancellation-after-staff-375.png),[768](../product/screenshots/cancellation-after-staff-768.png),[1280](../product/screenshots/cancellation-after-staff-1280.png) — intercepted synthetic presentation fixtures,not real-backend E2E evidence.
- **Verification:** [Source/tests/results](../PROGRESS.md#synthetic-cancellation-frontend);frontend local checkpoint based on4b3ac401f510e97b4a21821530938e48fa8f0a20;new exact-source CI PENDING. Full requirements NOT_ACCEPTED.

<a id="ug-024"></a>
Current cancellation guard clarification (CN-KNOWLEDGE-SYNC-001): an unresolved persisted ACTIVE extension reservation blocks cancellation. Resolve it only through the existing authorized lifecycle; elapsed time alone is not proof of committed release. Backend eligibility remains authoritative. No cancellation/refund action is automatic.

### UG-024 — Extension preview / අමතර කාලය සඳහා මිල පෙරදසුන

Open your existing customer booking detail and find **Explore extra cinema time** below the purchased snapshot. Review booking/package/session context, then explicitly select **Check Extension Options**. The server determines ownership, current eligibility and purchased duration increments. Enter a permitted **Additional duration (minutes)** and choose **Preview Additional Price**. No request is sent merely by opening the panel or changing duration.

The preview separates purchased price from additional LKR price. Proposed end is a labelled calculated display in the purchased timezone,not a backend-confirmed change. No total price or preview expiry is supplied. Competing blocking inventory is not checked by this preview;no extra time is guaranteed. **This is a price and availability preview only. Your booking has not been extended, and no payment has been requested.** පවතින booking එක වෙනස් නොවේ;ගෙවීමක් හෝ අමතර කාල වෙන්කිරීමක් සිදු නොවේ.

Only current eligible owners with pre-start,never-issued,revision1/lifecycle0 SIMULATED_CONFIRMED context may preview. Pending amendment/extension,changed room,started/cancelled/unsupported/unauthorized context is rejected. On a conflict reload the booking;on a service failure use Check Extension Options explicitly to retry;on expired session sign in and reload. Protected results clear on context/session/navigation changes. The preview itself reserves nothing;the separate temporary-reservation stage is described in UG-025. There is no funding,commit or PIN control. [Feature](../product/FEATURE_CATALOG.md#doc-f029),[evidence](../PROGRESS.md#extension-preview-frontend).

### UG-025 — Temporary extension reservation / තාවකාලික අමතර කාල වෙන්කිරීම

The customer workflow is available in existing booking details below **Explore extra cinema time**. After **Check Extension Options** and **Preview Additional Price**,choose **Review Extension Reservation**. This reads current room configuration,not a reservation guarantee. Review additional minutes/price/proposed end and the maximum ten-minute deadline clipped to original start. Explicitly tick the unchecked acknowledgement,then choose **Reserve Extension Time**. The server rechecks eligibility,price,configuration and additional inventory. The original purchased booking remains unchanged;ගෙවීමක් හෝ purchased extension එකක් නොවේ.

Use **Reload Extension Reservation** for authoritative state,expiry,revision and observation. Times are shown in the purchased venue timezone. To withdraw,tick the separate unchecked withdrawal acknowledgement and choose **Withdraw Extension Reservation**. Current status is reloaded;request transmission alone is not success. There are no staff reservation controls.

A created reservation protects additional blocking coverage for600 seconds at most,clipped to original start. Five new/rejected creation attempts per rolling60 seconds per eligible owner across all bookings/venues;verified identical committed command replay is free and never renews expiry. Reads and explicit withdrawal do not consume creation budget. Use a fresh status read for current validity:replayed command results describe their original observation time. EXPIRED/INVALIDATED/WITHDRAWN is not an extended booking;PENDING_RECONCILIATION remains unavailable until cleanup commits. Original purchase persists on withdrawal/expiry. Pending reservations temporarily prevent conflicting cancellation/rescheduling/credential issuance;use only the explicit recovery controls below. [Feature](../product/FEATURE_CATALOG.md#doc-f030),[actual evidence](../PROGRESS.md#extension-reservation).

Current status guidance: use the existing protected status read for an authoritative observation. ACTIVE may still be ineffective/PENDING_RECONCILIATION after expiry or configuration change. Customer reservation/reload/withdrawal controls now exist;there is no funding button or automatic mutation. This is not payment or purchased-extension approval.


UG-025 backend recovery clarification (CN-EXTENSION-RESERVATION-RECOVERY-READ-001): if a CREATE response was lost,the current eligible booking owner can resolve a previously committed CREATE command through the protected command-key recovery GET,then load the returned reservationId through the existing status GET. At that historical backend-only checkpoint,no customer recovery/reservation UI or proxy was implemented. Command keys do not grant authority;other accounts/bookings and WITHDRAW receipts are unavailable. Missing receipt means original outcome remains unknown;do not automatically create a replacement or infer failure. Recovered reference is not a paid/purchased extension. [Evidence](../PROGRESS.md#extension-reservation-recovery).


UG-025 current recovery (CN-EXTENSION-RESERVATION-FE-001):refresh or revisit in the same browser session restores only opaque account/booking-scoped references,then uses protected recovery/status reads. No price,evidence,token or consent is stored. An unknown/missing receipt stays unresolved;never create a replacement to resolve uncertainty. Reload explicitly. A lost withdrawal result reloads status and retains the original withdrawal command;retry requires a new explicit acknowledgement and uses that same command. Session/owner changes clear protected presentation. A different browser session without a saved reference cannot discover an existing active reservation by an unsupported list endpoint;server conflict is shown without inventing a reference. On403/404/409/429/503 follow the sanitized message and explicit reload;do not treat elapsed expiry or empty results as committed release,financial approval or physical readiness. [Evidence](../PROGRESS.md#extension-reservation-frontend).

UG-025 verified behavior (CN-E2E-EXTENSION-RESERVATION-001):the real synthetic application now verifies explicit preview,unchecked acknowledgement,temporary reservation,status refresh,recovery and withdrawal. After an uncertain creation response,use Reload Extension Reservation and keep the original browser-session reference. A missing receipt remains unknown;no replacement is sent automatically. Server expiry may require reconciliation before inventory is released;neither expiry nor temporary ACTIVE coverage changes your purchased session or PIN. Synthetic banking,physical readiness and funding restrictions remain. [Evidence](../PROGRESS.md#extension-reservation-e2e).

### UG-026 — Staging bank instruction inspection / ගෙවිය නොහැකි පෙරදසුන

Existing Admin Bank workspace එකේ Staging Bank Instructions panel භාවිත කරන්න.1.Exact venue reference දෙන්න.2.Instruction revision හෝ bank request lookup තෝරන්න.3.Exact target UUID දෙන්න.4.Load Instructions explicitව තෝරන්න. Version,effective interval,observation time සහ staging approval metadata පමණක් පෙන්වයි. NO REAL RECEIVING ACCOUNT / DO NOT TRANSFER MONEY යනු සැබෑ ගෙවීම් උපදෙස් නොවේ. Fixture approval actual bank ownership verification නොවේ.

403 නම් exact-venue bank.instructions.read සහ current membership අවශ්‍යයි;request lookup සඳහා existing bank review/evidence permissions සහ no-own-review restriction ද පවතී.401නම් sign in කර නැවත load කරන්න.409නම් legacy request එකට binding නැහැ;නව version එකක් අනුමාන නොකරන්න.503නම් private data නොපෙන්වයි;Retry Instructions explicitව භාවිත කරන්න. Context/session/navigation වෙනස්වීමෙන් protected result clear වෙයි;stale response restore නොවේ. Clear Instructions data ඉවත් කරයි. No automatic writes,no grants/account activation,real payment,receipt upload,allocation,booking/PIN confirmation. Default-disabled staging feature එකට permissions මෙම guide එකෙන් ලබා නොදෙයි. [Evidence](../PROGRESS.md#staging-bank-instructions).

UG-026 verification update: explicit instruction load/reload,refresh/revisit,venue/session clearing and synthetic warnings tested through actual Next/Boot/PostgreSQL. This changes evidence only,not user actions or production availability. [Evidence](../PROGRESS.md#bank-instructions-real-stack-e2e).


## Local receipt qualification (developers only)
Customer/staff banking workflows are unchanged;do not upload real receipts. Authorized developers may run `DOCKER_HOST=unix:///var/run/docker.sock JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 python3 -B scripts/verify-local-receipts.py --execute` after reviewing the pinned Compose file and cached images. The opt-in run creates a unique project,downloads official signatures through a separate updater,executes actual container tests,and deletes its owned volumes/containers/networks. It never calls AWS. Evidence and failed logs remain in ignored `.cache/local-receipts/`;success is recorded only after cleanup verification. [Scope and restrictions](../PROGRESS.md#local-receipt-qualification).


## Local integrated receipt verification (developers only)
Run `DOCKER_HOST=unix:///var/run/docker.sock JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 python3 -B scripts/verify-local-receipts.py --integration --execute` only for isolated synthetic tests. The launcher supplies temporary secrets,an outside-repository journal and private socket,uses actual authenticated APIs,and repeats protected reads in a fresh application JVM before cleanup. Application code never receives Docker access. Financial/access/device features are disabled in the fixture. Existing default customer/staff workflows and synthetic warnings are unchanged;never upload real receipts. [Scope,rollback and evidence](../PROGRESS.md#local-receipt-integration). Disable new local submissions before changing mode;retain journal,key and matching objects for controlled reconciliation. Never silently fall back to another store for a missing/corrupt binding.


## Receipt admission qualification — developer boundary
Customer/staff upload steps do not change. Never upload real receipts. New validation utilities have no customer wiring and the admission policy always denies. The existing local integration command additionally selects candidate image/storage/scanner tests;it verifies actual signature age before running and never bypasses stale metadata. Final controlled qualification with official28165 signatures passes;this does not qualify production receipt processing. [Actual status](../PROGRESS.md#receipt-admission-qualification).


<a id="local-customer-admin-preview"></a>
## Local Customer Website and Admin inspection
Current owner-requested local preview is http://127.0.0.1:3000 for Customer and http://127.0.0.1:3000/admin for Admin;it uses the existing Run.sh all launcher and a separate owned local database. The launcher still requires explicitly supplied local database/toolchain configuration;no credentials are stored in Git. This preview session is not a production service or a deployment promise.
Use Explore Cinema Rooms to open /rooms. The fresh preview has no configured rooms:No matching rooms is an authoritative empty catalog,not a room-ready statement. The three homepage booking/package links intentionally begin at room selection. A full booking cannot be demonstrated from an empty catalog without separately admitted fixtures/configuration and an eligible account.
/account is Current account session,not a sign-up or editable profile. There is no qualified manual/Google/Facebook customer-login flow in this preview. Opening /admin exposes the shell/navigation only;Room configuration and Package drafts still require current server-side venue permissions. Do not interpret visible links,form fields or HTTP200HTML as permission or a successful data mutation. No account/grant was provisioned for this inspection.
Current requested follow-ups:bounded customer authentication,own-profile management,provider qualification and non-financial homepage content. They remain separately gated;email-based auto-linking,financial promotions and real receipt asset reuse are not permitted by this guide. Screenshots/evidence and exact limits:[current preview](../PROGRESS.md#customer-website-preview).


<a id="local-customer-auth"></a>
## Local synthetic account registration/login — CN-R1-CUSTOMER-AUTH-001
මෙය local Ubuntu test workflow එකක් පමණයි. Real customers,real email,Google/Facebook,password recovery සහ production admission unavailable.
1. Existing isolated PostgreSQL සහ qualified Java25/Node24 toolchains භාවිත කරන්න. Operator:SPRING_PROFILES_ACTIVE=local-synthetic,CINENEST_LOCAL_CUSTOMER_AUTH=true,CINENEST_LOCAL_SESSION_SECONDS=1800 වැනි explicit positive local value සහ CINENEST_LOCAL_TEST_OUTBOX absolute private directory configure කර qualified packaged backend JAR එක --spring.profiles.active=local-synthetic සමඟ run කරන්න. Frontend සඳහා existing Run-frontend.sh සහ exact loopback CINENEST_AUTH_ORIGIN භාවිත කරන්න. Existing launcher SPRING_* override restrictions නොවෙනස්ය;SPRING_PROFILES_ACTIVE launcher වෙත export නොකරන්න. Outbox0700/files0600;shared/public directory භාවිත නොකරන්න. Default profile manual auth enable නොකරයි.
2. /register වෙත ගොස් unique example.test email සහ15–128character password ලබාදී Create test account තෝරන්න. Pending message එක account existence හෙළි නොකරයි;real email යවන්නේ නැහැ. Duplicate registration existing password/account replace හෝ expired token renew නොකරයි.
3. Authorized local test operator පමණක් private outbox message එකෙන් token ලබාදෙයි. Verification field එකට paste කර Verify test email තෝරන්න. Tokens URLs,logs,Git,screenshots හෝ public channels වෙත යවන්න එපා. Used/expired token denied;recovery/resend මෙම slice එකේ නැහැ. Test verification commercial/identity/age approval නොවේ.
4. /login වෙත ගොස් same credentials භාවිත කරන්න. Successful server response පසු /account → Check session. Refresh/revisit පසු නැවත authoritative server check කරන්න. Log out of current session මඟින් එම JDBC session එක revoke කරයි;other sessions ගැන claim නොකරයි.
5. Invalid credentials/unverified/ineligible account සඳහා generic denial ලැබේ.429දී configured local test window අවසන් වනතුරු රැඳී සිටින්න.503/unknown resultදී success assume නොකර Account check කරන්න. Registration retry duplicate account නොසාදයි. Password/token form values navigationදී clear වන අතර browser storage භාවිත නොකරයි.
Google/Facebook buttons unavailable ලෙස disabled. No real mail/financial/hardware acceptance. [Evidence](../PROGRESS.md#local-customer-auth),[technical configuration](../ARCHITECTURE.md#local-customer-auth).


<a id="google-sso-s1"></a>
## Google SSO S1 qualification — real Google login නොවේ
Default /login සහ /register pages manual authentication preserve කරන අතර Google unavailable ලෙස දිස්වේ. Isolated authorized contract test එකේ Google warning එක simulated provider බව පැහැදිලිව පෙන්වයි. Continue with Google — synthetic qualification native button එක keyboard/mouse මඟින් local fixture authorize/code/callback flow ආරම්භ කරයි. Successful trusted subject එක same CineNest UUID සමඟ /account වෙත පැමිණේ;Check session authoritative server state පෙන්වයි. Cancelled/rejected/unavailable callback එක fixed error notice එකක් පමණක් පෙන්වයි;private provider errors හෝ tokens නොපෙන්වයි. Existing logout/CSRF ක්‍රියාත්මකයි.
Real Google credentials,account linking හෝ customer admission මෙයින් ලැබෙන්නේ නැහැ. Unknown subject එක signed/verified email තිබුණත් deny වේ. Manual password සහ existing history වෙනස් නොවේ. Missing/unverified email කියන්නේ already-associated subject එකකට email-based admission ලබාදීමක් නොවේ. Facebook සහ runtime linking/unlinking unavailable.
Reproduce only with existing qualified toolchains:frontend production build පසු JAVA_HOME=qualifiedJava25,DOCKER_HOST=unix:///var/run/docker.sock,CINENEST_GOOGLE_E2E=true,CINENEST_MANUAL_E2E=true,PLAYWRIGHT_MODULE_PATH=qualifiedPlaywright සහ python3 scripts/verify-foundation.py. Harness owns isolated PostgreSQL and simulated issuer;never supply real customer data/secrets. Do not configure live mode from this S1 authority. [Technical limits](../ARCHITECTURE.md#google-sso-s1),[actual evidence](../PROGRESS.md#google-sso-s1).


<a id="google-sso-s2a"></a>
### Google S2-A — configured but disabled
Google adapter configured;live execution remains disabled යන notice එක provider qualification PASS බව නොකියයි. Google button disabled තබන අතර manual test email/password login තවම භාවිත කළ හැක. Facebook තවම unavailable. S2-A තුළ real Google credentials ලබාගෙන හෝ account login එකක් කර නැහැ;Google button බලෙන් enable කිරීම හෝ flags වෙනස් කිරීම owner approval නොවේ.
Operator requirements:[exact backend/private-secret/frontend configuration](../ARCHITECTURE.md#google-sso-s2a). Fixed local callback http://127.0.0.1:42137/api/auth/google/callback offline-registration real-stack harness එකෙන් පරීක්ෂා කළා;test server පසුව නවත්වන නිසා running preview එකක් හෝ Cloud Console registration එකක් ලෙස නොසලකන්න. Real Web client/secret private backend file එකක තබා separate S2-B authorization ලැබෙන තුරු execution gates false තබන්න. Secret,ID/access token හෝ ClientRegistration object logs/screenshots/Git වලට ඇතුළත් නොකරන්න. [Evidence and limits](../PROGRESS.md#google-sso-s2a).


<a id="facebook-sso-s1"></a>
### Facebook S1 local fixture qualification

Default /login හි Facebook Login unavailable disabled වේ. Authorized isolated fixture configured නම් පමණක් Continue with Facebook — synthetic qualification දිස්වේ. මෙය real Facebook Login නොවේ. Existing eligible pre-associated test identity එක පමණක් /account වෙත යයි;unknown/suspended identity deny වේ. Account linking,new registration හෝ email matching සිදු නොවේ. Keyboard මඟින් button activate කළ හැක;failure එක sanitized alert එකක දැක්වේ. Existing Check session සහ Log out controls භාවිත කරන්න;manual/Google flows තවම පවතී. Real Meta credentials හෝ customer identifiers මේ fixture එකට දෙන්න එපා. [Exact configuration](../ARCHITECTURE.md#facebook-sso-s1),[verification](../PROGRESS.md#facebook-sso-s1).


<a id="facebook-sso-s2a"></a>
### Facebook S2-A configured boundary

Facebook adapter configured; live execution remains disabled notice එක actual Facebook Login PASS බවක් නොවේ. Disabled button බලෙන් enable කිරීම owner authority නොවේ. Manual සහ separately configured Google login තවම පවතී. Callback origin independently qualified වී නැති බැවින් real Meta login නොකරන්න. [Exact private configuration](../ARCHITECTURE.md#facebook-sso-s2a);secrets,tokens,provider identifiers හෝ cookies chat/screenshots/Git වෙත නොදෙන්න. S1 synthetic mode භාවිත කළ විට එහි synthetic-only label රැකේ.


### Local HTTPS qualification operator workflow
This is a local synthetic/test operator workflow,not customer onboarding. Prepare a browser-trusted localhost certificate with approved Ubuntu mkcert/NSS tooling outside Git. Keep the private directory0700/key0600;never send keys,secrets or tokens to chat. Start the existing isolated PostgreSQL/Spring environment and the production Next build bound127.0.0.1:42137 using existing authentication configuration. Then run `CINENEST_LOCAL_HTTPS=true bash Run-https.sh` with qualified Node on PATH. The default certificate paths are `$XDG_CONFIG_HOME/cinenest/local-https/localhost.pem` and `localhost-key.pem` (fallback `~/.config`);explicit CINENEST_LOCAL_HTTPS_CERT/KEY may select approved private files. Open https://localhost:4443 only after trust verification;never click through a certificate warning.
Keep Google/Facebook backend and frontend live-execution flagsfalse. Use synthetic provider fixtures for compatibility tests;do not alter Google/Meta Console configuration. The candidate Facebook callback is https://localhost:4443/api/auth/facebook/callback,NOT provider-qualified or registered by this task. Stop ingress to roll back;existing HTTP qualification remains independent. [Security/configuration](../ARCHITECTURE.md#local-https-ingress),[verification status](../PROGRESS.md#local-https-ingress).


### Local synthetic profile viewing and editing

With the isolated profile feature enabled, sign in using the existing test authentication workflow and open `/account`. My profile loads your own details. Choose Edit profile, enter a full name and optional phone, then Save profile or Cancel profile edit. Phone is unverified. Verified email, customer ID, account eligibility and linked login methods cannot be edited here.

If another session changed the profile, use Reload profile before editing again. If a save result is uncertain, reload first; do not assume it failed. Logout clears protected profile/history. Existing booking history remains subject to its own authorization and backend availability. Production processing is not enabled. [Current evidence and limitations](../PROGRESS.md#local-customer-profile).


<a id="admin-advertising-s1"></a>

### Local synthetic venue advertising

This is an authorized local qualification workflow, not production content administration. The backend requires `local-synthetic` and explicit `cinenest.advertising.enabled=true`; the property defaults false and startup rejects enabling it outside that profile. Keep normal Google/Facebook live-execution gates false. Use only disposable content-only fixtures with exact-venue read/edit/publish authority; no production assignments. Build frontend using the existing qualified Node/npm commands. The existing foundation runner can reproduce the isolated journey with `CINENEST_ADVERTISING_E2E=true`, the already installed qualified `PLAYWRIGHT_MODULE_PATH`, Java25 and Docker; it owns/cleans its database, and the browser harness owns/stops Next/Chromium. It does not download browsers or contact providers.

Open Admin → Venue advertising (`/admin/advertising`) in the approved local stack. Choose a venue and Load banners. New draft opens the form. Enter title/description/image description, select an approved concept illustration, give explicit-offset ISO start/end instants and order, then Save draft. Venue timezone is shown; start is inclusive/end exclusive. Saving an edit leaves the live snapshot unchanged. Preview saved revision shows the exact saved content; publish with a different, currently authorized publisher. Author self-publication is denied. Unpublish removes current homepage visibility and retains immutable history. Revision history shows prior content. Reload and compare after a conflict; do not assume an uncertain command failed or create a new command blindly. Existing stable command replay protects retries; lost protected authority clears the view.

Homepage shows only published snapshots admitted by server time and complete enabled-venue state. Allow up to the documented15-second connected-client refresh interval for removal; disconnected instant removal is not promised. AI-generated concepts are not venue photographs. No arbitrary uploads/external links, discounts, price changes or customer notifications are available. Disabled/unconfigured or unauthorized operations are unavailable; menu visibility grants no permission. [Policy/rollback](../ARCHITECTURE.md#admin-advertising-s1), [actual results](../PROGRESS.md#admin-advertising-s1).

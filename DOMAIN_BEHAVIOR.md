# Habit Companion historical domain contract

Chunk 01 contract, 6 October 2026, extended by chunks 02–08 persistence, shared statistics, forms, Home/detail and Progress/Profile/reminder/data-control integration. The owner approved the Weekly proposal during implementation. This document records that decision for persistence and screen integration; it does not replace the supplied design. The new pure domain types have v2 storage; shared screen calculations are integrated in chunk 03.

## Dates and historical settings

The calculator takes an explicit `today`. `HabitDateSource` accepts a `Clock` with the device zone; there are no ambient date reads in replay. Creation includes its local date. Archive stops new logging/expectations on the archive date, but retains facts already logged before archiving, including partial amounts on that date. Achieved archive-day logs still contribute; an unfinished archive-day log does not create a miss. Weekly unfilled slots in the interrupted period are cancelled even if an archive-day log exists. This chunk 02 clarification is necessary to meet the approved requirement that archive preserves history. Delete removes records through persistence; it is not an archive outcome.

`HabitHistory` has ascending, unique effective settings beginning on creation, and at most one log per date. Every log must be active and eligible under that date's settings (or a retained archive-day fact), with the matching tracking type and unit. Future logs are rejected at evaluation/correction. Rest days are absent from the occurrence timeline. A historical correction replaces only its date's value and replays all derived metrics. Clearing a log is represented by its removal; a false binary/zero quantity value is also an uncompleted result.

Binary is done/not done. Quantity uses `BigDecimal`, a positive target and explicit nonblank unit; partial amounts are retained but do not count as achieved completion. Units match exactly: no guessed conversions. Mode/target/unit edits preserve older records and their original interpretation.

Schedule, mode and target edits normally become effective tomorrow. An edit to or from Weekly, including a quota change, begins the next Monday, even when edited on Monday. Thus a whole open quota period keeps its original schedule. Tracking/target changes within a Weekly period can begin tomorrow, and each date uses its own threshold. Typed `HabitSettingChange` defines effective dates and field changes; transactional storage and merging pending edits belong to chunk 02.

## Weekly policy approved by the owner

- Quota is 1–7 achieved completions per fixed Monday–Sunday period, one per distinct eligible date. It is different from Custom weekday selection.
- Display week-start preferences cannot move these quota boundaries.
- A period's first quota-sized set of achieved dates contributes successful required occurrences in date order. Additional achieved logs stay in history but do not inflate streaks or quota consistency. Corrections can cause another retained date to fill a slot.
- Unfilled slots are pending through Sunday. They become missed occurrences only when the period closes, ordered after its achieved dates and attributed to Sunday. Multiple shortfalls have distinct slot numbers.
- Creation partial weeks require `ceil(quota × remaining inclusive days / 7)`. Archive cancels still-pending slots in the interrupted period; completed slots and earlier closed shortfalls remain. Archive on Monday does not cancel the previous closed week.
- Streaks count consecutive completed required occurrences, not calendar days. Daily/Custom occurrences are scheduled days; Weekly occurrences are required quota slots. Later screens must label these units accurately rather than call Weekly streaks consecutive days.

| Scenario | Outcomes |
|---|---|
| Quota 3, Tue/Thu/Sat achieved | Three successes; no shortfall |
| Quota 3, only Tue/Thu achieved | Two successes and one pending slot through Sunday; one miss at closure |
| Quota 3, five achieved dates | Five logs retained, first three successful required occurrences |
| Quota 3, created Friday | Two required slots for Friday–Sunday |
| Quota 3, archived Friday after one success | One success retained; interrupted week's remaining slots cancelled |
| Quota 3 changed to 1 on Thursday | Old quota through Sunday; new quota next Monday |

## Streak, risk and consistency replay

There is no 400-day or other artificial history cap. Pending slots do not break current streaks or change risk. A miss resets current streak, while best is the all-time maximum. Correction can legitimately recompute best downward if earlier completion facts change.

On each settled occurrence, retain the last seven settled outcomes. A miss triggers attention when that window contains at least two misses. Two consecutive successful scheduled occurrences clear attention; rest dates/pending slots do not count. Old misses cannot retrigger risk merely on another success; a later miss evaluates entry again. No settled data is neutral. Attention and current streak are independent.

Consistency is completed required occurrences divided by all eligible required occurrences, including pending expectations for the open day/week. This preserves the review's progress interpretation without treating pending as missed. Zero eligible occurrences yields no percentage. New scheduled habits can have zero progress while their risk state remains neutral. Aggregate statistics in chunk 03 must sum numerators/denominators rather than average habit percentages. Weekly successes use their actual dates, closed misses Sunday, and pending slots the current evaluated date for period-aware aggregation; quota periods must not be repartitioned by a display setting.

## Integration limits

Chunk 03 removes the legacy `StreakCalculator` and its 400-day repository window. Home and future detail/Progress/Profile consumers use `HabitSnapshot`/`StatsAggregator` over atomic complete Room history. New/Edit and completion writes use the transactional history repository, preventing habits without initial dated settings. New schedules use this approved contract.

Chunk 02 retains the database name `habitflow.db`, original v1 columns and original timestamps. Migration freezes creation/archive local dates using the device zone in which v1 would have resolved them. v1 has no original creation-zone metadata, so earlier travel/time-zone context cannot be reconstructed. v1 Weekly masks become historical Custom eligibility, with original frequency/mask metadata retained. v1 Daily masks that differed from all seven days also retain their actual old mask semantics. Legacy goal/count are not quantities: row presence remains binary completion. Invalid legacy masks or impossible dates are preserved as raw records and produce validation errors rather than being guessed, deleted or silently treated as an empty database.

Schedule and tracking effective histories are stored separately, then merged by date into domain snapshots. A pending schedule change cannot overwrite a later target change. Repeated edits to the same field/effective date replace that pending field value; combined edits validate and commit atomically. Metadata, cues/plans and per-habit reminder overrides remain separate from expectation history. Archive Undo is limited to five seconds and updates only archive fields from the latest stored habit, preserving other edits and completion records.


## Shared statistics and date refresh (chunk 03)

One snapshot evaluates each habit's dated occurrences and produces active rows, current/all-time best, attention, lifetime totals, week daily buckets and actual 4–6 clipped month-week buckets. Ranged detail consistency sums the same required occurrences inside the selected month. Aggregate consistency sums completed and eligible counts before division. No eligible data has no percentage; future daily dates contribute nothing. Historical archived occurrences remain in totals/all-time best; active/top-streak rows exclude archived habits. Deleting removes their contribution on the next atomic observation. Quantity partial progress is amount/that date's target, clamped to 0–1; achievement remains a separate threshold result.

Weekly successes appear on their achieved dates, closed shortfalls on Sunday, and open pending slots on the evaluated date. Month boundaries and the display week-start can move chart buckets, never the quota's Monday–Sunday definition. Once this week's quota is met, an unfinished extra date is not due on Home; retained extra logs can still fill quota slots after corrections. A completed eligible today remains visible as completed, with no inflation of aggregate quota metrics.

`DeviceClock` reads the current device zone. `DateMonitor` publishes each local midnight (including 23/25-hour daylight-saving days), and Activity resume/date/time/time-zone broadcasts refresh and rearm it. Today's write rechecks the date transactionally, so a midnight race cannot silently become a past correction. Intentional past corrections use a separate operation. If the clock/zone moves backward, statistics query facts as of the displayed date, preserving later already-stored records for when that date returns; new future writes remain forbidden. This is a display/evaluation accommodation, not a rewrite of history.

Home retains prior successful data after a read failure, disables stale completion writes, and exposes Retry in place. Loading, genuine empty, no-due, all-done, read failure and write failure remain distinct. A read failure never manufactures empty habits, and a write failure never claims successful completion. Later chunks reuse these facts and build their own screen-specific loading/error presentation.


## New/Edit and draft contract (chunk 04)

New/Edit shares one form. Entering it, opening Coach, ordinary Back or discarding a draft never creates a habit. Names are trimmed. A trimmed, case-insensitive match against another active habit produces a warning and explicit duplicate-name confirmation rather than silently renaming or forbidding the habit. Archived names do not block creation. The final duplicate check runs inside the save transaction so a concurrent insertion cannot bypass confirmation.

The form supports Daily, Weekly quota 1–7 and Custom with at least one selected weekday. Binary is the recommended default. Quantity requires a positive plain decimal target and a nonblank unit up to 40 characters; comma decimal input is normalised, and comparison of equivalent targets such as 5 and 5.0 does not create a new expectation revision. Existing unknown/legacy appearance keys are preserved unless the user chooses a supplied appearance.

Edit loads the latest independently stored schedule and tracking settings, including pending future changes. Saving retains the habit ID and all completion history. Metadata changes immediately; expectation changes use the approved effective-date rules above. All writes, including metadata and expectation changes, commit or roll back together. Fields unchanged in the form merge from the latest stored values, preserving unrelated cue/reminder/completion edits. A competing change to a field edited in the form produces an explicit conflict; Reload requires confirmation before replacing the draft. Archived or deleted habits cannot be resurrected by a stale form.

SavedStateHandle stores primitive draft fields, the initial edit snapshot, a draft token and successful save ID. Repeated submits are guarded before launching the write; recreation after reported success cannot insert a second habit. This is saved UI state, not a permanent draft database. Bundle/Parcel restoration is verified; arbitrary OS termination during a database commit has not been simulated and is not claimed to provide an exactly-once cross-process transaction guarantee.

The local planning port carries the unsaved draft plus aggregate active-habit count, or an existing-habit local ID for Edit. These are local navigation inputs, not an outbound AI payload. Validated typed results use a primitive SavedStateHandle navigation envelope and matching draft token. Wrong-draft, disabled-Coach and malformed results are rejected; no model prose becomes a database write. The actual Coach detour, network payload builders, strategy/action validation and Apply/Undo remain chunks 09–12. Until connected, the form accurately reports that Coach is unavailable and retains the draft.


## Home logging and launch integration (chunk 05)

Home quantity entry captures today's date and historical target/unit in an unsaved primitive draft. Save records the exact non-negative decimal amount; Clear removes only that day's record through the same transactional current-date/eligibility checks. Partial progress is not achievement. Failed writes retain the amount for retry, and read failure disables writes while allowing an in-dialog Retry. A changed date, unavailable habit or mismatched expectation blocks the stale draft instead of converting it to a past correction. Closing/Back saves nothing. Repeated in-flight submissions are ignored. No schema/history interpretation changed.

Home's summary counts due active habits for today, with zero-due and all-done separate from empty/loading/error. Best remains all-time, and Weekly streak labels remain occurrence-based. Completion controls are separate from caller-aware detail navigation. The retained main graph anchors tabs to Home after removing Splash/setup; onboarding exits only after the local completion flag succeeds. Unsaved form/planning result contracts remain unchanged. Actual detail content and past corrections are chunk 06; this integration does not enable Coach requests.


## Detail corrections and management (chunk 06)

Detail uses the same atomic complete-history evaluation as Home. Current/all-time best stay independent of attention; the positive callout requires current = best >= 7. The attention sentence reports misses among the last seven settled scheduled occurrences and any one-occurrence recovery, without inventing life context. The selected month's rate and ratio count required occurrences under the existing Weekly weighting.

Calendar marks show achieved logs, partial quantities, scheduled Daily/Custom misses, pending today, flexible Weekly dates, rest and unavailable dates separately. Flexible Weekly dates never become invented daily misses; actual week-end shortfalls remain in occurrence metrics. Every date uses its historical schedule/mode/target/unit. Display week start only rearranges calendar columns.

A retained correction draft captures its date, opening date, historical expectation and original stored value as primitives. Saving compares these facts inside the Room transaction before changing that date alone. Changed records/settings, archive/delete, ineligible dates or a midnight race on an entry opened for today are rejected; failure retains input. Intentional earlier corrections remain valid after a later midnight. Repeated in-flight saves are guarded. The existing unguarded `correct` operation remains available to internal fixtures; the detail UI uses `correctChecked`.

Management operates on the local habit ID from Home long press or detail overflow. Edit returns through the existing form and its actual caller. Archive immediately removes the active row/detail and leaves a five-second, timestamp-scoped Undo above destinations; Undo changes only archive fields and preserves other records. Delete requires a named, irreversible-history confirmation and cascades through Room. Removal navigation checks the current entry so simultaneous snapshot/removal notifications cannot pop the caller twice. Reminder and Coach entries explain their current availability; notification delivery and Coach integration keep their planned later scope.

## Progress/Profile presentation and preferences (chunk 07)

Progress Week and Month are one retained root and one saved range choice. Week uses the snapshot's seven display-day buckets and counts completed required occurrences; Month uses its actual 4–6 clipped calendar-week buckets and weighted required-occurrence consistency. The period totals sum those shared buckets; Best remains all-time, including retained archived history. Future buckets have no eligible data and zero denominators display an em dash, not an invented percentage. Top rails rank positive current streaks of active habits and open the existing caller-aware detail route. Units describe required completions and occurrence streaks, including Weekly slots.

Profile uses the same snapshot's lifetime completed/eligible totals and all-time best, plus the active habit count. There is no stored account/join date: the identity summary honestly says “tracking since” the earliest retained habit creation month, or “no history yet”. Deleting the earliest habit can change that summary. The local name is trimmed, may be empty (the UI then uses “Your profile”/HC), permits at most 80 characters and rejects control characters. Inline editing retains primitives through recreation/detours; Done/IME Done persists immediately without a page-level Save. Cancel/Back discards only the unsaved name input. Failed writes keep the input and confirmed preferences.

Light/Dark/Follow system applies to tokens and system-bar appearance without a restart. Week-start selection offers Monday/Sunday and changes calendar columns and display buckets, never closed Monday–Sunday quota periods. Coach enablement persists through the existing setting and is observed by existing entry guards; no external request or new payload is introduced. Settings writes guard repeated taps and update only their field after DataStore confirms success. Statistics/read errors retain the last successful values and offer Retry; a statistics failure does not block available preferences. The theme observer keeps its last confirmed mode and retries local read failures, while Profile exposes those failures.

Reminder, export, destructive-clear and clear-Coach-history entries explicitly report their pending later-chunk integrations and do not perform those operations. Restore remains visibly disabled in v1. Existing database/preference identities, history, New/Edit draft tokens and result envelope remain unchanged.


## Reminders and local data controls (chunk 08)

The global daily reminder is the master switch. A habit inherits the global time when its override fields are null, may explicitly opt out, or may choose a custom time. Custom times still respect the global switch. Changes apply immediately to reminder metadata only, preserving dated schedule/target histories, completions, cues and unrelated form edits. A stale competing reminder edit is rejected rather than overwritten.

Local reminders use one nearest inexact AlarmManager alarm, with no exact-alarm permission or network dependency. Delivery rechecks the current generation, local date/time/zone, notification permission/channel, active record, historical tracking target and Daily/Custom/Weekly eligibility. Partial quantities remain pending; a completed day, met weekly quota, rest day, archive or deletion is not reminded. If today's chosen time has passed and an eligible reminder has not been attempted, it may be delivered when the app/alarm resumes that day. Android can delay it; yesterday's queued alarm never becomes today's reminder. Once attempted, a habit is not reminded again on the same local date, including after completion is cleared or its reminder is edited. The local delivered-attempt ledger survives recreation/reboot and is excluded from export. It is written before posting to prevent duplicates; an abrupt process kill between those steps can lose that day's notification. Notification enablement is checked on resume and dispatch; choices remain saved when permission/channel is blocked. Device force-stop/OEM battery restrictions can suppress delivery until the app resumes.

Export format version 1 is explicit JSON selected through the system document picker. It contains Room schema version 2 raw records, legacy metadata, archives, exact decimal amounts, dated schedules/targets, pending revisions, cues/plans and user-facing settings including profile/habit names. It excludes unsaved drafts and operational reset/reminder metadata. Coach storage is explicitly marked version 0/unimplemented until chunk 10 attaches threads/caches. The picker may offer cloud providers; the disclosure identifies that deliberate transfer. Restore stays disabled. No file is written on picker cancellation; failed I/O may leave an incomplete selected file and never reports success.

Typing exactly CLEAR confirms full logical local clearing: habits, dependent history, preferences (including identity/theme/onboarding/Coach/reminder choices), pending draft/navigation state and reminders. It returns to onboarding. Exported files and Android permission/channel choices remain. Room and DataStore cannot share one transaction, so a durable DataStore reset-intent/generation marker is written before cancellation/deletion. Interrupted/failed clears block local writes/UI/alarm dispatch and finish idempotently on startup or Retry. Completion of reset removes the marker and restores default preferences while retaining only the internal generation. A shared gate serializes writes/export/reset and rejects old queued writes; a new generation replaces navigation/root/draft state. Room IDs are not reset/reused. This is logical data removal, not a forensic secure-erasure claim. Clear Coach history stays a separate chunk 10 operation.

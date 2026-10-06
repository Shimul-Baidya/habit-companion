# Habit Companion historical domain contract

Chunk 01 contract, 6 October 2026, extended by chunks 02–03 persistence and shared statistics. The owner approved the Weekly proposal during implementation. This document records that decision for persistence and screen integration; it does not replace the supplied design. The new pure domain types have v2 storage; shared screen calculations are integrated in chunk 03.

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

Chunk 03 removes the legacy `StreakCalculator` and its 400-day repository window. Home and future detail/Progress/Profile consumers use `HabitSnapshot`/`StatsAggregator` over atomic complete Room history. The old form/toggle path now writes through the transactional history repository, preventing habits without initial dated settings. New schedules use this approved contract.

Chunk 02 retains the database name `habitflow.db`, original v1 columns and original timestamps. Migration freezes creation/archive local dates using the device zone in which v1 would have resolved them. v1 has no original creation-zone metadata, so earlier travel/time-zone context cannot be reconstructed. v1 Weekly masks become historical Custom eligibility, with original frequency/mask metadata retained. v1 Daily masks that differed from all seven days also retain their actual old mask semantics. Legacy goal/count are not quantities: row presence remains binary completion. Invalid legacy masks or impossible dates are preserved as raw records and produce validation errors rather than being guessed, deleted or silently treated as an empty database.

Schedule and tracking effective histories are stored separately, then merged by date into domain snapshots. A pending schedule change cannot overwrite a later target change. Repeated edits to the same field/effective date replace that pending field value; combined edits validate and commit atomically. Metadata, cues/plans and per-habit reminder overrides remain separate from expectation history. Archive Undo is limited to five seconds and updates only archive fields from the latest stored habit, preserving other edits and completion records.


## Shared statistics and date refresh (chunk 03)

One snapshot evaluates each habit's dated occurrences and produces active rows, current/all-time best, attention, lifetime totals, week daily buckets and actual 4–6 clipped month-week buckets. Ranged detail consistency sums the same required occurrences inside the selected month. Aggregate consistency sums completed and eligible counts before division. No eligible data has no percentage; future daily dates contribute nothing. Historical archived occurrences remain in totals/all-time best; active/top-streak rows exclude archived habits. Deleting removes their contribution on the next atomic observation. Quantity partial progress is amount/that date's target, clamped to 0–1; achievement remains a separate threshold result.

Weekly successes appear on their achieved dates, closed shortfalls on Sunday, and open pending slots on the evaluated date. Month boundaries and the display week-start can move chart buckets, never the quota's Monday–Sunday definition. Once this week's quota is met, an unfinished extra date is not due on Home; retained extra logs can still fill quota slots after corrections. A completed eligible today remains visible as completed, with no inflation of aggregate quota metrics.

`DeviceClock` reads the current device zone. `DateMonitor` publishes each local midnight (including 23/25-hour daylight-saving days), and Activity resume/date/time/time-zone broadcasts refresh and rearm it. Today's write rechecks the date transactionally, so a midnight race cannot silently become a past correction. Intentional past corrections use a separate operation. If the clock/zone moves backward, statistics query facts as of the displayed date, preserving later already-stored records for when that date returns; new future writes remain forbidden. This is a display/evaluation accommodation, not a rewrite of history.

Home retains prior successful data after a read failure, disables stale completion writes, and exposes Retry in place. Loading, genuine empty, no-due, all-done, read failure and write failure remain distinct. A read failure never manufactures empty habits, and a write failure never claims successful completion. Later chunks reuse these facts and build their own screen-specific loading/error presentation.

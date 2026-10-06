# Habit Companion implementation handoff

Updated 6 October 2026 after chunk 06. Workspace: `/home/shimul/AndroidStudioProjects/Habit`.

## Start here

1. Read `AGENTS.md` for standing owner decisions and permissions.
2. Read the status and requested chunk in `IMPLEMENTATION_PLAN.md`, then `DOMAIN_BEHAVIOR.md` before changing persistence/calculations.
3. Inspect current code and `git status --short`; the files, not this handoff alone, establish the current implementation.
4. Read relevant supplied screen/flow specifications and mockups before UI changes. `HABIT_COMPANION_REQUIREMENTS_REVIEW.md` maps sources; old proposals yield to the approved owner decisions.

**Chunks 01–06 are complete and verified. Chunk 07 is proposed, awaiting owner approval.** Do not automatically implement all remaining chunks. Complete one authorised chunk, record verification, provide a one-line commit message and ask permission for the next. The owner stages/commits/pushes. Do not perform Git mutations or disturb pre-existing changes/staging. If command execution requires permission, give its purpose and exact command.

## What exists

- Pure domain: `domain/HabitHistory.kt`, `HabitSettingChange.kt`, `HistoryCalculator.kt`, `StatsAggregator.kt`. Daily/Custom scheduled days and Weekly quota slots, dated binary/decimal quantity expectations, eligibility, risk/recovery and unlimited historical streaks are shared rules. Read the approved Weekly contract, not the old review proposal.
- Persistence: Room v2 in `data/local/`, exported schemas under `app/schemas/`, registered data-preserving v1→v2 migration. Keep the database filename **habitflow.db** and old preference keys despite public Habit Companion branding. Schedule/tracking histories are separate. No destructive migration fallback, cloud database or PostgreSQL server.
- `HabitHistoryRepository` atomically creates/edits/corrects/logs/archives/deletes. Archive has field-scoped five-second Undo; deletion cascades. `HabitRepository` supplies atomic complete histories to `HabitSnapshot`/shared aggregation. DataStore handles existing identity/onboarding/theme plus week start, Coach enablement and reminder configuration. Theme/reminder screen/service wiring remains later work.
- `DeviceClock`/`DateMonitor`: midnight, resume, time/date/zone changes and DST-aware refresh. Today's logging rechecks date transactionally. A clock rollback evaluates as-of without deleting later recorded facts. Home separates loading, empty, no-due, all-done, read error/retry and write error; cached data is retained after read failure with stale writes disabled.
- Branding/backup: public name Habit Companion, local-only Room/DataStore, backup disabled and storage domains excluded by legacy/modern backup/extraction XML. No network Coach yet. Actual OEM backup/restore transports have not been exercised.

Paths above are relative to `app/src/main/java/com/example/habit/` unless stated otherwise.

## Chunk 06 integration details

- `ui/screens/detail/`: shared real healthy/attention/neutral detail at `habit/{habitId}`. Current/all-time best/selected-month rate and ratio use `EvaluatedRecord`/shared history. Positive callout is exactly current = best >= 7; attention reading reports measured recent misses and one-occurrence recovery without changing the real streak. Title scrolls into a fading app-bar label; stats/calendar/legend scroll above the docked outlined Ask the Coach or filled Get a plan action. Coach enablement is observed; the current availability message makes no network request.
- `DetailFacts.kt` supplies dated calendar presentation, not a second statistics calculation. Daily/Custom misses differ from flexible Weekly dates; achieved/partial/pending/rest/unavailable dates are explicit. Weekly quota shortfalls stay in weighted occurrence totals. Selected month persists; header arrows and reference-width grid swipes change it. Display week-start rearranges columns only.
- `HabitDetailViewModel`: retained primitive correction date/opening date, historical schedule/target/unit, original value and amount; save/clear/mark/unmark, repeated-submit guards, failure retention and Retry. `data/HabitOperations.kt` is the local mutation port. `HabitHistoryRepository.correctChecked` compares displayed settings/original log inside the Room transaction, rejects changed/archived/deleted/ineligible facts and current-day midnight races, then changes only that date. Intentional past corrections continue to use past expectations. Date rollback before creation has an unavailable interpretation instead of crashing.
- `ui/management/`: one saved manager above destinations observes real records, keeps sheet/delete/reminder states and preserves archive Undo after detail returns to its caller. Home row long press and detail overflow open the same SCR-14 sheet. Edit opens `Routes.editHabit(id)` and returns through the existing form. Change reminder explains the pending chunk 08 interaction without claiming a configuration change. Archive retains historical records and exposes five-second scoped Undo; delete names the habit/best streak, confirms irreversibility and cascades. Failures retain data and allow retry. Expiry/recreation use primitive tokens; repository conflict checks protect unrelated edits/completions.
- `HabitNavHost` now wraps the production graph in `SharedTransitionLayout`; Home/detail name bounds share a real 300ms transition through `HabitSharedTransition.kt`. `PendingHabitDetail` was removed. Detail Back/removal checks its exact current entry, so simultaneous missing/archive and manager notifications cannot pop Home/Progress twice. The main/tab/New/Edit/planning envelope contracts below are retained.
- Adaptations: 28dp calendar dots inside non-overlapping >=48dp targets; narrow content (<336dp) scrolls the seven-column grid horizontally, with header swipes/arrows for months. Large text grows/scrolls; the docked action remains available. Tables' shared dots/filled attention action supersede the square/outlined alternate picture. Material3 1.4.0's sheet `MotionScheme` and motion-spec setters are internal; native sheet/gesture/predictive-Back motion remains a narrow fallback to an exact 250ms duration. No dependency upgrade or replacement sheet.
- No schema, production dependency, database filename, preference key, provided source resource or New/Edit draft contract change. No external Coach request, secret, fake suggestion or real-data reset.

## Retained chunk 05 integration details

- Home now uses the supplied header/date, outlined summary/ring, honest remaining/no-due/all-done states, all-time Best and coloured icon/name/helper/streak/right-hand control rows. `HomeLoading` uses a skeleton without invented habits. Quantity rows show the dated target, partial amount and Weekly quota where applicable; non-due rows say Not due today or Weekly quota met. Attention retains its actual current streak.
- `HomeViewModel` now receives a mandatory SavedStateHandle through `createSavedStateHandle()`. `QuantityEntry` captures habit ID/date/target/unit plus amount as primitives. Open, change, save, clear, dismiss and retry are local; failed saves keep input. Current snapshot/date/eligibility/mode/unit/target checks and in-flight guards block stale or repeated submissions. `HabitDataSource.quantityToday` delegates to `HabitHistoryRepository.logToday`; its nullable value clears today's record under the same transaction/date checks. No schema change or new past-correction path.
- `QuantityDialog` uses one decimal field and explicit Clear/Save/Cancel; read failure provides Retry without losing input. Binary controls keep checked/unmark semantics; quantity controls announce partial/achieved amount. Rows and date/Coach cards can grow with text. Onboarding privacy copy scrolls, and the summary ring adapts to larger text/counts. The Add button occupies a reserved strip above the tabs; the list viewport applies Scaffold insets so rows cannot draw or respond behind that strip.
- `Routes.MAIN = "main"` is a nested graph starting at existing `Routes.HOME`. Tab switching pops to Home with save/restore and single-top; non-Home root Back returns to Home. Splash/setup are removed. Launch fades 200ms, tabs 150ms, form entry/pop 250ms. `Routes.HABIT_DETAIL = "habit/{habitId}"`, `Routes.detail(id)` requires a positive Long; Back uses the actual caller stack. Chunk 06 replaced the temporary detail with real content and shared name bounds (see above).
- `OnboardingViewModel` observes write success, error and saving state, retains completed state, guards repeated finish, and does not navigate after failure. Continue advances/finishes all three panes; Skip writes the same preference. `SplashViewModel` has controlled read/timing inputs and propagates cancellation. The actual brand remains Habit Companion.
- Disabled Coach hides the empty-Home shortcut, while `CoachRoot` explains how to enable it and responds to preference changes. It is still unavailable for real suggestions; no external service, payload, credential or fake response was introduced.
- Production graph screen ports are internal, allowing isolated Android tests to exercise the actual routes/Back/state restoration without touching real preferences or habits. Existing New/Edit planning routes, flags, primitive results, draft token and preservation contract below remain unchanged.

## Retained chunk 04 form contracts

- `data/HabitFormDataSource.kt`: form interface, conflict/duplicate errors, `HabitRecord.editableDraft()` loads latest schedule/tracking independently, including pending changes.
- `HabitHistoryRepository.saveForm`: atomic duplicate check and metadata/expectation save. Trimmed case-insensitive active-name duplicates require confirmation; archived names do not block. Edits preserve ID/history, merge unchanged fields from the latest row and reject conflicting edited fields. Preserve unrelated reminder/cue/completion changes. Numerically equivalent decimal targets do not create a revision.
- `ui/screens/newhabit/HabitFormDraft.kt`: editable primitive fields and domain conversion; Daily/Weekly quota/Custom, binary default/quantity positive decimal plus unit, retained cues/plans. `NewHabitViewModel`: retained primitive SavedStateHandle draft, initial edit snapshot, token, successful save ID, read/write retry, duplicate/discard/reload confirmation and repeated-submit guard. Manual factory uses container history/settings and `createSavedStateHandle()`.
- `NewHabitScreen`: shared New/Edit SCR-05 composition, five reference appearance choices (`HabitAppearance.kt`), legacy choice retained, frequency/quantity controls, scrolling body, Coach card before docked Save/Create, keyboard/navigation insets and larger-text accommodation. Weekday visuals 40dp within distinct 48dp checkbox targets. `PrimaryButton` now has minimum rather than fixed 56dp height.
- Routes: `Routes.NEW_HABIT = "new-habit?habitId={habitId}&planning={planning}"`; helpers `newHabit(planning=false)` and `editHabit(id)`; args Long default 0 and Boolean default false. Empty-Home Coach shortcut enters unsaved creation with planning flag. Edit is now opened from the chunk 06 management sheet.
- `FormCoachEntry.Planning(DraftPlanningRequest)` holds unsaved draft/token/aggregate active count; `.Existing(id)` is a local existing-habit entry. **Neither is an external payload.** Later strict payload builders must exclude prohibited fields.
- `PlanningDraftContract`: validated typed result ↔ primitive `ArrayList<String>` navigation envelope, `RESULT_KEY = "planningDraftResult"`. NavHost observes the form entry's SavedStateHandle; the form validates the token and Coach setting, applies to its draft and consumes the result. A test detour exercises this. Actual Coach destination is not wired yet; card accurately says it is not connected and retains the draft. No fake production response or database write from prose.
- Restoration tests cover saved primitive fields, Bundle/Parcel and Room-backed ViewModel save. Actual OS process kill during a commit is not tested and exactly-once persistence across arbitrary process death is not promised.

## Verified state

Final functional command:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Passed: **104 JVM tests and 73 Pixel 7 Android 17 device tests; zero failures/errors/skips. Lint zero errors, 26 advisories.** Advisory locations concern existing dependency/API checks, redundant label, plural candidates and unused resources; none are in the new detail/management files. Reports: `app/build/test-results/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.xml`. `git diff --check` passed. The debug app remains installed, and final cold launch returned `Status: ok`, `LaunchState: COLD`.

New coverage: calendar eligibility/date coverage/leap day, Weekly flexibility and quota outcomes, positive/neutral/attention facts and retained risk streak, binary/quantity dated corrections, partial/achieved/clear, concurrent corrections and rollback, stale expectation/log/date/archive/delete rejection, failed/read retry/repeated-save paths, primitive and Bundle/Parcel detail restoration with Room, month arrows/swipe, Home long press/Edit and detail Edit caller return, nested native dialog/sheet Back, archive retention/scoped Undo/expiry/restoration, delete cascade and safe Home/Progress removal. Existing migration/DataStore/privacy/domain/date/Home/form/launch/navigation tests passed.

Inspected SCR-07/08/14 and FLOW-06/07/11 plus system-design p.3 against light healthy/dark attention 393 × 832dp, dark 360 × 640dp/font 1.5, scrolled calendar/legend, management sheet and historical quantity dialog. Calendar targets have explicit >=48dp/non-overlap assertions; the large-text dialog asserted native IME visibility and visible Save before persistence. Final QA captures live only in target `cache/chunk06-qa`, with inspection copies `/tmp/habit-chunk06-qa`; dialog PNGs omit the native keyboard itself. Previous Home/onboarding/form visual evidence remains in the plan.

Intermediate failures are recorded in the plan: unbounded weighted calendar measurement and reference-width swipe interception were fixed; visibility tests now scroll the outer list explicitly. Two earlier test activities lost foreground during physical navigation; logs showed navigation gestures and no Habit Companion crash. The owner authorised uninterrupted testing, and the final full suite passed. Forced viewport/font and saved-state reconstruction do not prove every OEM configuration or arbitrary OS kill during a write. The Progress caller test uses an isolated root port through the actual graph; actual Progress content is still chunk 07.

Device tests keep `android.injected.androidTest.leaveApksInstalledAfterRun=true` and use isolated Room/preferences. Never uninstall or clear the real app to solve a test issue. Prior chunk 02 records an earlier uninstall correction; do not claim historical real data survived that earlier run. No Git mutation, source resource edit, schema/dependency change or outbound Coach traffic occurred in chunk 06.

Gradle needs permission for the existing `~/.gradle` cache; SDK/ADB is `/media/shimul/New Volume1/Android/sdk/platform-tools/adb` (quote the path). Give exact commands/purposes when escalation is required. The workspace was clean at startup; inspect current owner staging before later work.

Suggested chunk 06 commit: `feat: add historical habit detail corrections and safe management actions`

## Next chunk 07 (only after approval)

Read SCR-12/13/15, FLOW-12–14 and corresponding mockups. Reuse complete snapshots/aggregation and the working detail route/management operations.

- Complete Progress Week/Month: correct weighted consistency/done/all-time best, seven daily bars and actual 4–6 monthly buckets, top-streak rails and detail entry, zero/partial states and specified chart motions.
- Complete Profile: local identity/name/initials, lifetime values, Preferences/Coach/Data groups and scrollable lower controls. Observe Light/Dark/Follow system at the app theme; persist/reflect week start and Coach enablement.
- Retain root/range/scroll state, four-tab navigation, Progress→detail→Back and immediate reactive changes. Reminder/export/wipe and Coach history entries stay accurately assigned to chunks 08/10; Restore stays disabled.
- Verify shared-value fixtures, month/week-start boundaries, theme persistence without restart, name/settings errors and visual/keyboard/touch behavior. Update handoff/plan and ask before chunk 08.

## Remaining scope

07 Progress/Profile and reactive settings/theme; 08 reminders/export/wipe; 09 actual 60-card strategy retrieval and strict Coach payload/action contract; 10 typed Apply + immediate local update/10-second scoped Undo/local history; 11 Coach screens/draft detours/root selection; 12 real external service; 13 end-to-end/design verification.

Owner prefers **Gemini Flash free API tier**. Credentials, exact model/version and applicable unpaid-service data-use terms require deliberate integration in chunk 12. Never silently configure external traffic or send full habits, local IDs, prohibited names, unrelated data or whole conversations. No new product decision is required for chunk 07.

## Prompt for a new chat

> Continue Habit Companion in this project. Read AGENTS.md, HANDOFF.md, DOMAIN_BEHAVIOR.md and IMPLEMENTATION_PLAN.md, inspect current changes, and implement only chunk 07 faithfully. Verify each logical step and relevant device/visual behavior; preserve existing data, source resources and draft contracts. I handle Git operations. Finish with an updated handoff, a one-line commit message and a brief chunk 08 proposal, then ask permission. For permission-required commands, show the exact command and purpose.

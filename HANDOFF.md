# Habit Companion implementation handoff

Updated 6 October 2026 after chunk 05. Workspace: `/home/shimul/AndroidStudioProjects/Habit`.

## Start here

1. Read `AGENTS.md` for standing owner decisions and permissions.
2. Read the status and requested chunk in `IMPLEMENTATION_PLAN.md`, then `DOMAIN_BEHAVIOR.md` before changing persistence/calculations.
3. Inspect current code and `git status --short`; the files, not this handoff alone, establish the current implementation.
4. Read relevant supplied screen/flow specifications and mockups before UI changes. `HABIT_COMPANION_REQUIREMENTS_REVIEW.md` maps sources; old proposals yield to the approved owner decisions.

**Chunks 01–05 are complete and verified. Chunk 06 is proposed, awaiting owner approval.** Do not automatically implement all remaining chunks. Complete one authorised chunk, record verification, provide a one-line commit message and ask permission for the next. The owner stages/commits/pushes. Do not perform Git mutations or disturb pre-existing changes/staging. If command execution requires permission, give its purpose and exact command.

## What exists

- Pure domain: `domain/HabitHistory.kt`, `HabitSettingChange.kt`, `HistoryCalculator.kt`, `StatsAggregator.kt`. Daily/Custom scheduled days and Weekly quota slots, dated binary/decimal quantity expectations, eligibility, risk/recovery and unlimited historical streaks are shared rules. Read the approved Weekly contract, not the old review proposal.
- Persistence: Room v2 in `data/local/`, exported schemas under `app/schemas/`, registered data-preserving v1→v2 migration. Keep the database filename **habitflow.db** and old preference keys despite public Habit Companion branding. Schedule/tracking histories are separate. No destructive migration fallback, cloud database or PostgreSQL server.
- `HabitHistoryRepository` atomically creates/edits/corrects/logs/archives/deletes. Archive has field-scoped five-second Undo; deletion cascades. `HabitRepository` supplies atomic complete histories to `HabitSnapshot`/shared aggregation. DataStore handles existing identity/onboarding/theme plus week start, Coach enablement and reminder configuration. Theme/reminder screen/service wiring remains later work.
- `DeviceClock`/`DateMonitor`: midnight, resume, time/date/zone changes and DST-aware refresh. Today's logging rechecks date transactionally. A clock rollback evaluates as-of without deleting later recorded facts. Home separates loading, empty, no-due, all-done, read error/retry and write error; cached data is retained after read failure with stale writes disabled.
- Branding/backup: public name Habit Companion, local-only Room/DataStore, backup disabled and storage domains excluded by legacy/modern backup/extraction XML. No network Coach yet. Actual OEM backup/restore transports have not been exercised.

Paths above are relative to `app/src/main/java/com/example/habit/` unless stated otherwise.

## Chunk 05 integration details

- Home now uses the supplied header/date, outlined summary/ring, honest remaining/no-due/all-done states, all-time Best and coloured icon/name/helper/streak/right-hand control rows. `HomeLoading` uses a skeleton without invented habits. Quantity rows show the dated target, partial amount and Weekly quota where applicable; non-due rows say Not due today or Weekly quota met. Attention retains its actual current streak.
- `HomeViewModel` now receives a mandatory SavedStateHandle through `createSavedStateHandle()`. `QuantityEntry` captures habit ID/date/target/unit plus amount as primitives. Open, change, save, clear, dismiss and retry are local; failed saves keep input. Current snapshot/date/eligibility/mode/unit/target checks and in-flight guards block stale or repeated submissions. `HabitDataSource.quantityToday` delegates to `HabitHistoryRepository.logToday`; its nullable value clears today's record under the same transaction/date checks. No schema change or new past-correction path.
- `QuantityDialog` uses one decimal field and explicit Clear/Save/Cancel; read failure provides Retry without losing input. Binary controls keep checked/unmark semantics; quantity controls announce partial/achieved amount. Rows and date/Coach cards can grow with text. Onboarding privacy copy scrolls, and the summary ring adapts to larger text/counts. The Add button occupies a reserved strip above the tabs; the list viewport applies Scaffold insets so rows cannot draw or respond behind that strip.
- `Routes.MAIN = "main"` is a nested graph starting at existing `Routes.HOME`. Tab switching pops to Home with save/restore and single-top; non-Home root Back returns to Home. Splash/setup are removed. Launch fades 200ms, tabs 150ms, form entry/pop 250ms. `Routes.HABIT_DETAIL = "habit/{habitId}"`, `Routes.detail(id)` requires a positive Long; Back uses the actual caller stack. `PendingHabitDetail` is explicitly unfinished until chunk 06. Its 300ms fade/scale is temporary; implement the actual detail container/shared-element transition with its content.
- `OnboardingViewModel` observes write success, error and saving state, retains completed state, guards repeated finish, and does not navigate after failure. Continue advances/finishes all three panes; Skip writes the same preference. `SplashViewModel` has controlled read/timing inputs and propagates cancellation. The actual brand remains Habit Companion.
- Disabled Coach hides the empty-Home shortcut, while `CoachRoot` explains how to enable it and responds to preference changes. It is still unavailable for real suggestions; no external service, payload, credential or fake response was introduced.
- Production graph screen ports are internal, allowing isolated Android tests to exercise the actual routes/Back/state restoration without touching real preferences or habits. Existing New/Edit planning routes, flags, primitive results, draft token and preservation contract below remain unchanged.

## Retained chunk 04 form contracts

- `data/HabitFormDataSource.kt`: form interface, conflict/duplicate errors, `HabitRecord.editableDraft()` loads latest schedule/tracking independently, including pending changes.
- `HabitHistoryRepository.saveForm`: atomic duplicate check and metadata/expectation save. Trimmed case-insensitive active-name duplicates require confirmation; archived names do not block. Edits preserve ID/history, merge unchanged fields from the latest row and reject conflicting edited fields. Preserve unrelated reminder/cue/completion changes. Numerically equivalent decimal targets do not create a revision.
- `ui/screens/newhabit/HabitFormDraft.kt`: editable primitive fields and domain conversion; Daily/Weekly quota/Custom, binary default/quantity positive decimal plus unit, retained cues/plans. `NewHabitViewModel`: retained primitive SavedStateHandle draft, initial edit snapshot, token, successful save ID, read/write retry, duplicate/discard/reload confirmation and repeated-submit guard. Manual factory uses container history/settings and `createSavedStateHandle()`.
- `NewHabitScreen`: shared New/Edit SCR-05 composition, five reference appearance choices (`HabitAppearance.kt`), legacy choice retained, frequency/quantity controls, scrolling body, Coach card before docked Save/Create, keyboard/navigation insets and larger-text accommodation. Weekday visuals 40dp within distinct 48dp checkbox targets. `PrimaryButton` now has minimum rather than fixed 56dp height.
- Routes: `Routes.NEW_HABIT = "new-habit?habitId={habitId}&planning={planning}"`; helpers `newHabit(planning=false)` and `editHabit(id)`; args Long default 0 and Boolean default false. Empty-Home Coach shortcut enters unsaved creation with planning flag. Edit's management entry is still chunk 06.
- `FormCoachEntry.Planning(DraftPlanningRequest)` holds unsaved draft/token/aggregate active count; `.Existing(id)` is a local existing-habit entry. **Neither is an external payload.** Later strict payload builders must exclude prohibited fields.
- `PlanningDraftContract`: validated typed result ↔ primitive `ArrayList<String>` navigation envelope, `RESULT_KEY = "planningDraftResult"`. NavHost observes the form entry's SavedStateHandle; the form validates the token and Coach setting, applies to its draft and consumes the result. A test detour exercises this. Actual Coach destination is not wired yet; card accurately says it is not connected and retains the draft. No fake production response or database write from prose.
- Restoration tests cover saved primitive fields, Bundle/Parcel and Room-backed ViewModel save. Actual OS process kill during a commit is not tested and exactly-once persistence across arbitrary process death is not promised.

## Verified state

Final command after functional changes:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Passed: **92 JVM tests, 59 Pixel 7 Android 17 device tests; zero failures/errors/skips. Lint zero errors, 23 existing advisories.** Tests cover domain/statistics/date, migration/persistence/DataStore, Home state and real binary/quantity logging, form atomic edits/restoration/planning contracts, launch/setup timing and failures, tab/root restoration, caller-aware detail routes, Coach availability and screen accessibility. Reports: `app/build/test-results/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.xml`. `git diff --check` passed. Build reports are ignored and should be refreshed after relevant edits.

Viewed Home/onboarding light and dark 393 × 832dp fixtures plus dark 360 × 640dp/font scale 1.5, scrolled content and quantity dialog with native IME visibility asserted. Home/control non-overlap is tested after scrolling, and the final list viewport keeps partially visible rows outside the Add strip. QA PNGs use only target `cache/chunk05-qa`, with inspection copies in `/tmp/habit-chunk05-qa`; they omit the native keyboard itself. The earlier chunk 04 form captures/checks remain recorded in the plan.

First/subsequent launch branches run through the production graph/screens with isolated read inputs; no real preference reset or uninstall was used. Room-backed Home tests use in-memory databases. Saved-state checks do not claim every arbitrary process kill during a write is verified. Installed-app startup returned `Status: ok` on a cold check; the final post-test check also returned `Status: ok` (`LaunchState: WARM`). The app remains installed. No new dependency/schema, supplied resource change, external Coach traffic or Git mutation.

Device tests must keep `android.injected.androidTest.leaveApksInstalledAfterRun=true`; they use isolated databases/preferences. Never uninstall or clear the real app to solve a test issue. Prior chunk 02 records an earlier uninstall correction; don't claim historical user data survived that earlier run.

Gradle may require permission to access the existing `~/.gradle` cache. SDK/ADB is `/media/shimul/New Volume1/Android/sdk/platform-tools/adb` (quote the path). Ask with the exact command and purpose if escalation is needed; don't print secrets. Use existing installed dependencies; no production dependency/schema changes were needed in chunks 04–05. No Git mutations were performed. Inspect current staging instead of assuming it is unchanged since this handoff.

Suggested chunk 05 commit: `feat: complete Home logging and retain launch and tab navigation state`

## Next chunk 06 (only after approval)

Read SCR-07/08/14 and FLOW-06/07/11 plus relevant calendar/components/mockups. Reuse the complete historical snapshots and repository operations; do not recalculate a limited-history copy.

- Replace `PendingHabitDetail` at `habit/{habitId}` with shared healthy/attention detail: header, real current/all-time best/month rate, conditional local pattern/positive callouts, month ratio, calendar/legend and docked Coach action contract. Preserve positive current streak in attention state.
- Support month changes and eligible past-date corrections for binary and quantity using the selected date's schedule/target/unit. No future/pre-creation/rest-date actions; dependent metrics update together.
- Add overflow and Home long-press management: shared Edit form, reminder entry (delivery remains chunk 08), archive/five-second scoped Undo and delete confirmation/cascade. Dialog/sheet Back consumes first; removal leaves a valid caller destination.
- Retain caller-aware Back from Home and later Progress, form drafts/effective settings and the local Coach contracts. Implement the real 300ms detail/container transition with its content; the temporary port's fade/scale is not final fidelity evidence.
- Verify corrections/history/quantity targets, archive/Undo/delete/conflicts, caller/modal Back, calendar touch targets, dark/light/small/large-text layouts and actual device behavior. Update the plan/handoff; ask before chunk 07.

## Remaining scope

06 detail/calendar corrections/management; 07 Progress/Profile and reactive settings/theme; 08 reminders/export/wipe; 09 actual 60-card strategy retrieval and strict Coach payload/action contract; 10 typed Apply + immediate local update/10-second scoped Undo/local history; 11 Coach screens/draft detours/root selection; 12 real external service; 13 end-to-end/design verification.

Owner prefers **Gemini Flash free API tier**. Credentials, exact model/version and applicable unpaid-service data-use terms require deliberate integration in chunk 12. Never silently configure external traffic or send full habits, local IDs, prohibited names, unrelated data or whole conversations. No new product decision is required for chunk 06.

## Prompt for a new chat

> Continue Habit Companion in this project. Read AGENTS.md, HANDOFF.md, DOMAIN_BEHAVIOR.md and IMPLEMENTATION_PLAN.md, inspect current changes, and implement only chunk 06 faithfully. Verify each logical step and relevant device/visual behavior; preserve existing data, source resources and draft contracts. I handle Git operations. Finish with an updated handoff, a one-line commit message and a brief chunk 07 proposal, then ask permission. For permission-required commands, show the exact command and purpose.

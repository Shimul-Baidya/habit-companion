# Habit Companion implementation handoff

Updated 6 October 2026 after chunk 04. Workspace: `/home/shimul/AndroidStudioProjects/Habit`.

## Start here

1. Read `AGENTS.md` for standing owner decisions and permissions.
2. Read the status and requested chunk in `IMPLEMENTATION_PLAN.md`, then `DOMAIN_BEHAVIOR.md` before changing persistence/calculations.
3. Inspect current code and `git status --short`; the files, not this handoff alone, establish the current implementation.
4. Read relevant supplied screen/flow specifications and mockups before UI changes. `HABIT_COMPANION_REQUIREMENTS_REVIEW.md` maps sources; old proposals yield to the approved owner decisions.

**Chunks 01–04 are complete and verified. Chunk 05 is proposed, awaiting owner approval.** Do not automatically implement all remaining chunks. Complete one authorised chunk, record verification, provide a one-line commit message and ask permission for the next. The owner stages/commits/pushes. Do not perform Git mutations or disturb pre-existing changes/staging. If command execution requires permission, give its purpose and exact command.

## What exists

- Pure domain: `domain/HabitHistory.kt`, `HabitSettingChange.kt`, `HistoryCalculator.kt`, `StatsAggregator.kt`. Daily/Custom scheduled days and Weekly quota slots, dated binary/decimal quantity expectations, eligibility, risk/recovery and unlimited historical streaks are shared rules. Read the approved Weekly contract, not the old review proposal.
- Persistence: Room v2 in `data/local/`, exported schemas under `app/schemas/`, registered data-preserving v1→v2 migration. Keep the database filename **habitflow.db** and old preference keys despite public Habit Companion branding. Schedule/tracking histories are separate. No destructive migration fallback, cloud database or PostgreSQL server.
- `HabitHistoryRepository` atomically creates/edits/corrects/logs/archives/deletes. Archive has field-scoped five-second Undo; deletion cascades. `HabitRepository` supplies atomic complete histories to `HabitSnapshot`/shared aggregation. DataStore handles existing identity/onboarding/theme plus week start, Coach enablement and reminder configuration. Theme/reminder screen/service wiring remains later work.
- `DeviceClock`/`DateMonitor`: midnight, resume, time/date/zone changes and DST-aware refresh. Today's logging rechecks date transactionally. A clock rollback evaluates as-of without deleting later recorded facts. Home separates loading, empty, no-due, all-done, read error/retry and write error; cached data is retained after read failure with stale writes disabled.
- Branding/backup: public name Habit Companion, local-only Room/DataStore, backup disabled and storage domains excluded by legacy/modern backup/extraction XML. No network Coach yet. Actual OEM backup/restore transports have not been exercised.

Paths above are relative to `app/src/main/java/com/example/habit/` unless stated otherwise.

## Chunk 04 integration details

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

Passed: **81 JVM tests, 44 Pixel 7 Android 17 device tests; zero failures/errors/skips. Lint zero errors, 24 existing advisories.** Tests cover domain/statistics/date, migration/persistence/DataStore, Home state/semantics, form/domain/ViewModel, atomic edits/conflicts and form navigation/validation/restoration. Reports: `app/build/test-results/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.xml`. Build output is ignored; future checks should generate fresh evidence.

Viewed rendered form fixtures: light 393 × 832dp, dark 360 × 640dp/font scale 1.5 with IME visible, and scrolled Coach card. Root screenshots exclude native keyboard, while tests explicitly assert IME visibility/insets. QA test captures use only `cache/chunk04-qa` in the target app. This is not complete visual verification of unfinished screens. Final installed app cold launch returned `Status: ok`.

Device tests must keep `android.injected.androidTest.leaveApksInstalledAfterRun=true`; they use isolated databases/preferences. Never uninstall or clear the real app to solve a test issue. Prior chunk 02 records an earlier uninstall correction; don't claim historical user data survived that earlier run.

Gradle may require permission to access the existing `~/.gradle` cache. SDK/ADB is `/media/shimul/New Volume1/Android/sdk/platform-tools/adb` (quote the path). Ask with the exact command and purpose if escalation is needed; don't print secrets. Use existing installed dependencies; no production dependency/schema changes were needed in chunk 04. No Git operations were performed. Inspect current staging instead of assuming it is unchanged since this handoff.

Suggested chunk 04 commit: `feat: complete habit forms with retained drafts and historical edits`

## Next chunk 05 (only after approval)

Read SCR-01/02/03/04 and FLOW-01–05/12/14 plus relevant foundation/mockup sections. Inspect Home, Splash/Onboarding, navigation and shared components first.

- Finish faithful Home greeting/date, summary/ring, all-time Best, coloured habit icon/name/helper/streak/separate completion control and attention grouping. Keep loading/empty/no-due/all-done/error distinct.
- Binary toggle and compact quantity amount entry/clear with today's historical target/unit and partial progress; no rest-date/future/pre-creation writes or quota inflation. Preserve shared calculations/date refresh and stale/repeated-write guards.
- Fix retained Home/main graph anchor instead of removed Splash; onboarding persistence/removal, four-tab save/restore without duplicate roots, Back-to-Home and destination-specific transitions. Preserve chunk 04 route/draft/result contract.
- Introduce the correct habit-detail route/caller contract. Detail itself is chunk 06; do not route habit rows to a Coach placeholder or claim detail completion prematurely. Progress/Profile/Coach screens remain later chunks.
- Verify first/subsequent launch, Skip/Continue/Back, tabs/roots/state, all Home states/logging/date guards, accessibility and light/dark reference/smaller/large-text Home/onboarding visuals. Record checks actually run and remaining limits.

## Remaining scope

06 detail/calendar corrections/management; 07 Progress/Profile and reactive settings/theme; 08 reminders/export/wipe; 09 actual 60-card strategy retrieval and strict Coach payload/action contract; 10 typed Apply + immediate local update/10-second scoped Undo/local history; 11 Coach screens/draft detours/root selection; 12 real external service; 13 end-to-end/design verification.

Owner prefers **Gemini Flash free API tier**. Credentials, exact model/version and applicable unpaid-service data-use terms require deliberate integration in chunk 12. Never silently configure external traffic or send full habits, local IDs, prohibited names, unrelated data or whole conversations. No new product decision is required for chunk 05.

## Prompt for a new chat

> Continue Habit Companion in this project. Read AGENTS.md, HANDOFF.md, DOMAIN_BEHAVIOR.md and IMPLEMENTATION_PLAN.md, inspect current changes, and implement only chunk 05 faithfully. Verify each logical step and relevant device/visual behavior; preserve existing data, source resources and draft contracts. I handle Git operations. Finish with an updated handoff, a one-line commit message and a brief chunk 06 proposal, then ask permission. For permission-required commands, show the exact command and purpose.

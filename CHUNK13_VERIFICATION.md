# Habit Companion — chunk 13 verification

7 October 2026. **Chunk 13 complete, including the remaining chunk 12 Coach follow-up checks.** Pixel 7 was reconnected and the authorised local, visual and live verification finished. Bounded platform limitations are recorded below; no implementation feature or required changed-path live check remains pending.

## Integration fixes

- An archived-only collection now retains access to Progress from Home. Home says “No active habits” within the same empty-card/planning composition. Existing-habit Coach selection requires an active habit, with an explanation; planning remains available. Deleting the final record returns to the genuinely empty state. Navigation changes do not alter historical rows/totals.
- Edit's Coach card accurately describes the existing-habit measured summary; New retains its draft disclosure. Onboarding explains quantity targets and Weekly required completions rather than implying any daily check-in extends every streak.
- Singular completion/weekday/quota labels use Android plurals. Removed the unreachable placeholder, obsolete stub strings/comments and redundant activity label. Screen structures, four tabs, tokens and destinations remain intact.
- Added one JVM archived-history/deletion regression, one compiled Android archived-only navigation regression and `scripts/verify_private_demo.py` for reproducible offline APK/native SQLite checks.

Room v3, `habitflow.db`, preference keys, draft/result envelopes, typed Apply/Undo, provider configuration and supplied resources are preserved. No dependency upgrade, Git mutation, real-data clear, device uninstall or Gemini request occurred.

## Executed checks

| Check | Current result |
|---|---|
| `./gradlew :app:build :app:assembleDebugAndroidTest --max-workers=2` | Successful in 2m 36s: debug/unsigned release assembly, JVM tests, lint and instrumentation APK compilation |
| JVM | **164 tests / 20 suites**, zero failures/errors/skips. This configured build ran the debug unit suite. |
| Lint | Zero errors, **17 warnings**: 11 dependency updates, 3 newer versions, 2 Gradle/AGP versions, 1 target API advisory. No stack upgrade to silence these. |
| Pixel local suite | **180/180 passed**, zero failures/errors/skips, Android 17; actual Room v1→v3/v2→v3 migrations and reopen/persistence/navigation/data-control checks |
| Focused visual/live suite | Initial five-case run: four passed, one visual-fixture failure. Coach was disabled in its synthetic state; enabling it repaired the fixture, and its one-case rerun passed. No production defect or additional live retry. |
| Dark reference suite | Four additional 393×832dp form/Coach/Progress/Profile cases passed, zero failures/errors/skips |
| Current device coverage | **189 unique selected cases have passing evidence** across these runs (190 executions including the repaired fixture). All 191 declared cases compile; the original two live paths retain their earlier chunk 12 evidence. |
| `python3 scripts/verify_private_demo.py` | Passed packaged brand/identity, permission whitelist, backup exclusions, cleartext restriction, QA-component exclusion, exact 60-card asset and credential scan |
| SDK `apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk` | Passed, one signer, APK Signature Scheme v2 |
| Native SQLite | Executed actual v2→v3 additive SQL on synthetic rows, preserving raw/archived/quantity/pending facts; matched exported v3 table/index/FK shapes and checked dependent deletion cascades |
| Diff/resource/schema inspection | `git diff --check` passed; supplied resources and schema files unchanged |
| Gemini calls | **Exactly two successful calls this device pass**, no retries, synthetic inputs only; four recorded verification calls total across chunks 12/13 |
| Owner data / retained app | Database, WAL and settings fingerprints all byte-identical before/after tests. APK retained; installed hash matches below; cold launch `Status: ok`, `LaunchState: COLD`, live process confirmed. |

The native audit's initial empty-index-array assumption was repaired before its successful run. Generated Android Room migration/open-path validation subsequently passed in the full Pixel suite. Release assembly warned that two native libraries could not have symbols stripped, packaged them as-is and completed successfully. Final `./gradlew :app:assembleDebug :app:lintDebug --max-workers=2` passed in 31s after the added test fixtures; lint remains zero errors/17 advisories.

Evidence under `app/build/reports/chunk13-verification/`: `non-device/` (JVM/lint), `offline-audit/` (APK/native SQL), `full-device/` and `full-device-html/`, `live-and-visual-initial/`, `visual-fixture-rerun/`, `dark-reference-device/`, `captures/`, `visual-review/` and `device-verification.json`. Synthetic live-response JSONs are retained for semantic review, without keys. Generated reports are ignored; preserve them locally with private demo material. Exact device commands are in `device-commands.txt`; commands below record the main regression/live selections.

## Device coverage matrix

All rows have source/contract review and current executed JVM/Pixel coverage. The full local Pixel suite includes the listed Android classes; focused additions cover the final layout/copy changes. A model fixture tests deterministic Coach behaviour; the two changed-path tests separately exercise real Gemini.

| Specification | Relevant checks |
|---|---|
| FLOW-01–02 / SCR-01–02: persisted onboarding, removed setup roots, Back | `SplashViewModelTest`, `OnboardingViewModelTest`; `NavigationUiTest`, `Chunk05VisualTest` |
| FLOW-03/05 / SCR-03–05: create/save/empty→today | `NewHabitViewModelTest`, `HabitFormDraftTest`, `HomeViewModelTest`; `HabitFormUiTest`, `FormPersistenceTest`, `HomeWorkflowUiTest`, `HomeStateUiTest` |
| FLOW-04 / SCR-06: same unsaved draft/planning/Back | `DraftCoachActionTest`; `HabitFormUiTest`, `CoachWorkflowUiTest` |
| FLOW-06/07 / SCR-07/08: healthy/risk detail, correction, actual caller | `HistoryCalculatorTest`, `DetailFactsTest`, `DetailViewModelTest`; `DetailWorkflowUiTest`, `DetailCorrectionPersistenceTest`, `InsightWorkflowUiTest` |
| FLOW-08/09 / SCR-09/10: one-habit context, cards, typed Apply/ten-second Undo | `CoachContractTest`, `CoachQuestionTest`; `CoachBoundaryRuntimeTest`, `CoachActionsPersistenceTest`, `CoachWorkflowUiTest` |
| FLOW-10 / SCR-11: failure/backoff/cache/disable/cancellation | `CoachPolicyTest`; `GeminiTransportTest`, `CoachWorkflowUiTest` |
| FLOW-11 / SCR-14: edit/reminder/archive/five-second Undo/delete | `DetailViewModelTest`; `StorageTest`, `FormPersistenceTest`, `DetailWorkflowUiTest`, `DataControlsUiTest` |
| FLOW-12/13 / SCR-12/13: retained tabs/range/scroll/month buckets/archives | `StatsAggregatorTest`, `InsightViewModelTest`, new archived-only JVM regression; `NavigationUiTest`, `InsightWorkflowUiTest`, new archived-only UI regression |
| FLOW-14 / SCR-15: profile/theme/week start/Coach gates/data controls | `InsightViewModelTest`, `ProfileActionsTest`, `DataGateTest`; `PreferencesPrivacyTest`, `InsightWorkflowUiTest`, `DataControlsUiTest` |
| Both modes/all schedules/historical units/partial/zero/long history/risk/date | `HabitHistoryTest`, `HistoryCalculatorTest`, `StatsAggregatorTest`, `DateMonitorTest`, `ReminderPlannerTest`; `DomainRuntimeTest`, `StorageTest`, `SnapshotRepositoryTest`, form/correction/Home/Coach suites |
| Schema upgrade/reopen | `StorageTest.v1MigrationPreservesOriginalDataAndHistoricalMeaning` opens v1→v3; `CoachActionsPersistenceTest.schemaV2MigrationPreservesEveryRawFactAndAddsEmptyCoachStores` opens v2→v3; both suites include actual close/reopen |
| Export/history clear/wipe/recovery/reminders | `DataGateTest`, `ProfileActionsTest`, `ReminderPlannerTest`; `DataControlsTest`, `DataControlsUiTest`, `CoachActionsPersistenceTest`, using isolated stores/QA identities |

Compared current reference light/dark 393×832dp and small dark 360×640dp/font-1.5 captures with the supplied screen/flow/mockup composition. Inspected Home/archived planning, New/Edit, onboarding, detail/calendar, Coach cards/applied/conversation/offline/planning, Progress, Profile and dialogs. The final Edit fixture also captures the native keyboard with Save visible; existing tests assert native input/Send/Create access, roles/labels/states and 48dp/non-overlap boundaries. There are 94 retained QA PNGs, including three original live screenshots from the earlier chunk 12; nine current review contact sheets are in `visual-review/`. Test-only dark reference wrappers reuse the earlier fixtures; no new production layout was introduced. System Poppler rendered source pages after the bundled runtime's known host-glibc incompatibility.

The real reading reply proposed putting the phone in another room before opening the book, adding friction/Do Not Disturb, and a conditional distraction reflection; it explicitly preserved the goal. Starting help returned an honest no-settled-history reading plus relevant entry-step/gateway options. Both responses passed strict strategy/fact/action validation and single-send checks, appeared directly in conversation, and applied nothing implicitly. Actual semantics were reviewed from the synthetic JSON and captures; future model quality is not guaranteed by a single smoke sample.

## Executed Pixel commands and bounded limits

The full local command passed in 6m 8s:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.example.habit.ui.screens.coach.GeminiLiveSmokeTest --max-workers=2
```

This ran isolated local runtime/migration/navigation/visual checks with live dispatch excluded. `android.injected.androidTest.leaveApksInstalledAfterRun=true` remains in place. No uninstall-incompatible option, owner-store clear, real-history transfer or Git mutation occurred.

The following pair was executed once each within the five-case selection alongside `Chunk13VisualTest`, with explicit `coachLive=true` (exact combined command in `device-commands.txt`):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.habit.ui.screens.coach.GeminiLiveSmokeTest#liveFreshKarateStarterHasHonestRestDayFactsAndNoImplicitApply,com.example.habit.ui.screens.coach.GeminiLiveSmokeTest#liveSpecificReadingQuestionShowsGeneratedAdviceInConversationAndSendsOnce -Pandroid.testInstrumentationRunnerArguments.coachLive=true --max-workers=2
```

Both actual provider/coordinator cases passed with synthetic inputs, no owner Karate/Read data and exactly one generation request each. The original two live paths were not repeated. Owner-confirmed 500 RPD/15 RPM was not independently retrieved from authenticated AI Studio; this pass's two calls equal 0.4% of that confirmed daily allowance. Four verification calls are recorded across chunks, separate from any owner/other-tool usage. Preserve the approved 40–45% ceiling.

Actual notification permission was **denied before and after**, with its existing USER_SET/sensitivity flags preserved. No accessibility service was enabled before/after. Consequently no granted-permission notification-display smoke or actual TalkBack speech is claimed. Accessibility semantics and notification availability/cancellation were tested; the owner settings were not changed to obtain a result.

Remaining bounded platform limits: no actual reboot/system-clock/time-zone change, long Doze/OEM delivery, arbitrary OS kill during a write, OEM backup/transfer, cloud-provider export or forensic erasure simulation. Controlled clocks, date/recovery/ledger tests, Bundle/Parcel/reopen and native alarm registration establish the specified local contracts. Real quota/safety/network outages were not forced; failures have deterministic coverage. These limits do not represent an omitted feature or unfinished provider checkpoint.

## Private artifact

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `43d2604f62205b990f2810e617785eefc08972237cc6ada47387b857fa39a33d`.

Catalog SHA-256: `060c4c76706da0c77c2bce45222b98730055c82107928c8bd7873237a53d13ff`.

The owner-approved private academic APK contains the extractable credential: keep it local/private, never commit/publish it. `.env` remains ignored/mode 600 and no configured key value appeared in tracked/nonignored files. Its installed hash matches the built artifact, retained cold launch passed and owner data remained byte-identical. Unsigned release output also contains the private credential.

Chunks 12 and 13 are complete. Chunk 13 is the final planned chunk; no further implementation pass or chunk 14 is required. The owner handles Git and private demonstration/review.

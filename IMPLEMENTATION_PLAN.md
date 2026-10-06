# Habit Companion implementation plan

Created 6 October 2026 from the owner's final instructions and the current project/resources. This plan covers the full approved application. Implementation began with chunk 01; consult the status table and handoff for executed work.

Read `AGENTS.md` first. It records authoritative product decisions and continuing engineering constraints. `HABIT_COMPANION_REQUIREMENTS_REVIEW.md` remains useful for source locations and original screen details, but conflicting earlier proposals are superseded.

## 1. Original planning baseline and assumptions

At planning time, the project had Compose foundations, Splash, three-pane onboarding, empty/populated Home, a name-only create stub, Room schema v1 for habits/completions, DataStore for onboarding/name/theme, pure streak functions and manual dependency wiring. Detail, management, Coach, Progress and Profile remain incomplete or placeholders. The known routing, midnight, error/empty, accessibility and 400-day-limit issues still apply.

`resources/coach_cards.json` is present and was parsed during planning: **60 cards, 60 unique IDs**, each with `id`, `title`, `principle`, `action`, `use_when`, `tags`, `source`. The source is supplied content, not an executable action schema. Keep the entire library and attribution. No additional library is required.

At planning time, the manifest enabled backup with template XML rules; privacy configuration must be corrected during implementation. No AI endpoint/provider/authentication configuration was found. Existing example tests do not verify habit behaviour. No compilation or device test was performed during this planning request.

### Approved decisions that supersede the old review

| Topic | Locked requirement |
|---|---|
| Weekly | A user-selected completion quota within a week, distinct from Custom weekdays |
| Tracking | Binary default plus quantity tracking, both supported throughout the app |
| History | Effective settings/history; future changes must preserve past meaning |
| Risk | Two misses in the last seven scheduled occurrences; recovery after two consecutive completed occurrences |
| Apply | Typed and validated, immediate local application, action-specific confirmation |
| Undo | Ten-second inverse operation; protect later edits and completions |
| Strategies | Actual supplied 60-card resource, local retrieval and ID validation |
| Privacy | Local persistence; minimal mode-specific external Coach requests; deliberate backup configuration |
| Design | Existing screens, composition, visual system, four tabs and documented flows |

## 2. Chunk sizing and execution rules

Use **13 medium-sized chunks**, each with a bounded outcome and explicit completion gate. This separates the two largest risks—historical data semantics and Coach integration—into testable stages, while grouping related screen work so shared foundations are not repeatedly rebuilt.

Each chunk should compile and leave its own scope reviewable. Intermediate placeholders may remain only for later chunks and must stay visibly recorded as unfinished. Test doubles are allowed in tests/previews; a simulated Coach cannot satisfy production completion. The owner now requests permission between chunks: complete the requested chunk, provide its one-line commit message and briefly propose the next chunk for approval. The owner performs Git operations.

Start each chunk by reading its dependencies and current handoff, inspect only the relevant current files/resources, implement the bounded outcome, run the specified checks, and update the status. If context compacts, resume the active chunk. Split a chunk only when a real complexity or environment constraint requires it; document why without dropping its remaining scope. Do not re-plan the entire application every turn.

## 3. Consequential checkpoints

The approved decisions need no further approval. Two areas still lack sufficient detail for a final implementation contract. They do not prevent creating these documents or planning independent work.

### Weekly quota semantics — approved during chunk 1

The owner approved the concrete proposal during implementation. `DOMAIN_BEHAVIOR.md` records the final policy, examples, effective dates and calculation units. The following paragraphs retain the original checkpoint context; they are no longer pending decisions.

Weekly frequency is settled, but a quota has no fixed missed weekday. Agree a precise occurrence timeline before storing historical outcomes. Recommended proposal for review: one achieved occurrence per distinct eligible date when a binary log is done or a quantity target is met; at most the requested weekly quota contributes to quota consistency; unfilled required occurrences become misses only when the period closes. Extra achievements remain in history without inflating quota consistency. Daily/Custom retain their scheduled-date semantics.

This proposal still needs a precise streak display/unit and ordering of week-end shortfalls for the seven-occurrence risk/recovery rule. Also resolve first/archive partial weeks, midweek quota changes and week-start changes. Do not encode an arbitrary answer into a migration or pretend Weekly is Custom. During chunk 1, present a small worked example and settle only what cannot be resolved logically under the owner's rules. UI/spacing choices do not need questions.

Legacy v1 rows marked Weekly also need inspection before migration because the old schema used a weekday mask. Preserve a record of that old meaning; do not guess a quota and silently rewrite historical eligibility.

### External AI configuration — required by chunk 12

Provider, endpoint/model, credential provisioning and provider-specific retention/limits are not supplied. Build and verify the local service boundary, screens and action system before this point. Request the missing service details with a concrete minimal-payload proposal at integration time. Do not silently choose a provider or introduce accounts/cloud storage. Keep an unconfigured-service state honest; live Coach completion depends on actual integration and verification.

## 4. Chunk overview and dependencies

| Chunk | Deliverable | Depends on | Status |
|---|---|---|---|
| 01 | Historical domain contracts and pure behaviour | Weekly checkpoint approved | Complete |
| 02 | Versioned persistence and safe migration | 01 | Complete |
| 03 | Shared calculations, date refresh and observable state | 01–02 | Complete |
| 04 | Complete New/Edit Habit and retained drafts | 01–03 | Complete |
| 05 | Faithful Home, launch and main navigation | 03–04 | Complete |
| 06 | Detail, corrections and management sheet | 03–05 | Complete |
| 07 | Progress and Profile preferences | 03, 05–06 | Complete |
| 08 | Reminders, export and destructive data controls | 02, 06–07 | Complete |
| 09 | Supplied strategy retrieval and Coach contracts | 01–04, supplied resource | Complete |
| 10 | Typed action execution, local history and Undo | 02, 04, 08–09 | Complete |
| 11 | Coach screens, draft detours and root selection | 05–07, 09–10 | Complete |
| 12 | Real external AI service integration | 09–11, AI configuration | Not started |
| 13 | End-to-end verification and design completion | All preceding chunks | Not started |

Execute in the listed order. Dependencies describe correctness requirements, not an instruction to delegate or create additional chats. The early contract work is part of implementation, not another broad requirements-review project.

## 5. Detailed chunks

### Chunk 01 — Historical domain contracts and pure behaviour

**Outcome:** one coherent interpretation of Daily/Weekly/Custom and binary/quantity tracking, shared by all later screens and storage.

Extend domain models for schedule variants, weekly quotas, tracking mode, decimal-safe amount/target/unit, effective settings, eligible outcomes and typed setting changes. Establish deterministic transitions for risk/recovery and all-time current/best streaks, including open periods and changing frequency. Finish the weekly checkpoint with examples before finalising its data contract. Treat time/date as injected input rather than ambient calls throughout calculations.

Define field validation, historical mode/unit changes and correction semantics; define which settings are effective immediately versus at a future occurrence/period boundary. Use a small dated history contract so schedule/target changes preserve completed and missed outcomes. Do not build UI or make a schema migration yet.

**Verify:** focused pure tests for binary/quantity threshold and partial amounts; all three frequencies; pending today/current week; rest dates; creation/archive boundaries; long histories beyond 400 days; schedule/target changes; risk entry, two-success recovery and later re-entry; history correction replay. Include worked weekly scenarios such as 3-of-week achieved on nonconsecutive dates, shortfall at week end, partial weeks and extra logs.

**Completion gate:** domain types/calculations compile, policy examples are recorded and tests establish the contract needed for migration. No unresolved consequential policy is hidden in code.

### Chunk 02 — Versioned persistence and safe migration

**Outcome:** Room/DataStore can faithfully store and retrieve the approved model without losing v1 data.

Extend the existing entities/DAOs/repository rather than replacing the architecture. Add dated schedule/target/mode history, quantity records, cue/plan settings and reminder fields using the chunk 1 contract. Keep the original database identity/file. Introduce a tested v1 migration and exported schema; preserve names, IDs, icon/colour keys, timestamps, archive state and existing completion facts. Existing production writes are binary, so do not retroactively interpret them as measured quantities just because v1 contains `goal`/`count` columns.

Add transactional create/update/correction/archive/delete operations and observations for active and historical data. Extend preferences for week start, Coach enablement and reminders with compatible defaults. Configure automatic backup/extraction rules for local-only v1, including all new stores. Update public branding/copy to Habit Companion without unnecessary internal renames.

**Verify:** migration using an actual v1-schema test database and representative data, cascade deletion, archived retention, one record per eligible day, historical effective values, transaction failure/rollback and DataStore persistence/defaults. Inspect packaged manifest/backup configuration; device validation is recorded separately if unavailable.

**Completion gate:** no destructive migration fallback; v1 and new records round-trip correctly; affected tests and compilation pass. Runtime migration evidence or an explicit instrumentation limitation is recorded.

### Chunk 03 — Shared calculations, date refresh and observable state

**Outcome:** Home/detail/Progress/Profile can consume the same correct, reactive facts.

Integrate the domain contract with complete historical repository queries. Remove artificial streak limits. Implement shared aggregation for eligible completion totals/consistency, selected-month detail rate, current/all-time best and active top streaks. Keep archived historical contributions while excluding archived habits from active views. Include zero/partial/no-due cases and actual 4–6-week calendar buckets.

Introduce lifecycle-aware date/time refresh at midnight, resume and relevant time changes. Model loading, data, storage failure and retry separately; preserve prior successful data when appropriate. Ensure correction/settings writes invalidate every affected statistic coherently. Do not calculate unrelated copies of the same rule in each screen ViewModel.

**Verify:** aggregate counts versus ratios, quantity thresholds with old/new targets, quota weighting, archived/deleted contribution, corrected histories, long streaks, current partial period, week-start boundaries, injected midnight/resume/time-zone changes and repository error/retry flows.

**Completion gate:** screen-state inputs are deterministic and observable; empty data cannot mask storage failure; affected tests and compilation pass.

### Chunk 04 — Complete New/Edit Habit and retained drafts

**Outcome:** users can create/edit all supported habit types through the specified form.

Finish SCR-05 with name, supplied icon/colour choices, Daily/Weekly quota/Custom days, binary/quantity selection and conditional target/unit fields. Fit the approved additional tracking controls into the existing form hierarchy; retain the Coach card above the docked Create/Save action. Use the original typography, spacing and card structure with necessary scrolling/insets.

Use a retained/saved draft shared with the planning detour, support creation from empty-Home planning and ordinary form entry, and load existing habits for edit. Add field validation, duplicate-name handling consistent with the owner hierarchy, dirty Back confirmation, saving/error states and duplicate-submit guards. Apply historical changes through repository operations. Keep Coach wiring as a defined draft/result contract pending chunk 11; do not insert a fake habit to obtain an ID.

**Verify:** create/edit in both modes and all frequencies; invalid quota/amount/unit/day selection; whitespace/duplicate names; write failure retains input; repeated taps save once; recreation restores draft; edit updates rather than inserts; keyboard and large text keep the action accessible. Verify the draft/result contract with a test action even before the Coach UI exists.

**Completion gate:** real local create/edit succeeds, preserves historical interpretation and matches the supplied form composition plus approved mode/quota controls.

### Chunk 05 — Faithful Home, launch and main navigation

**Outcome:** the daily hub and four-root navigation behave as specified.

Finish SCR-01/02/03/04 and FLOW-01–05/12/14 foundations. Fix main root anchoring, Splash removal, onboarding persistence, tab save/restore and Back-to-Home. Replace incorrect habit-to-Coach-placeholder routing with the real detail contract completed in chunk 6; keep any temporary destination plainly tracked until then. Use the appropriate entry/pop/tab transitions rather than one global fade.

Restore mockup Home composition: greeting/date, summary text/ring, correct all-time Best where labelled, coloured icon/name/helper/streak/right-side completion and attention grouping. Binary controls toggle; quantity controls open a compact amount sheet/dialog with partial ring state and current target/unit. Non-due Custom habits have honest unavailable completion actions; Weekly quota affordances follow chunk 1. Add loading skeleton, retry, empty/no-due/all-done distinctions and spoken state/action labels.

**Verify:** first/subsequent launch, Skip/Continue/Back, rapid tab switching without duplicates, root state preservation, Home totals, binary and quantity updates/clear, repeated taps, quantity partial state, no-due days, storage failure, date refresh and 48dp controls. Compare light/dark Home and onboarding at the reference viewport and one smaller/large-text configuration.

**Completion gate:** daily logging/navigation is coherent; launch cannot be resurrected through Back; Home is visually faithful and reports correct totals/state. Detail remains tracked until chunk 6 if not yet rendered.

### Chunk 06 — Detail, corrections and management sheet

**Outcome:** SCR-07/08/14 and their operations work from Home and later Progress.

Build shared healthy/at-risk detail: collapsing header, current/best/rate, true local pattern callout, positive callout condition, month ratio, seven-column calendar, legend and outlined/filled Coach action. Retain real streak counts in at-risk state. Month swipe and eligible past-date correction support binary toggle and quantity input with that date's target/unit.

Implement the management sheet from overflow/long press: edit, reminder interaction entry, archive/five-second Undo, delete confirmation/cascade and Cancel. Consume modal/dialog Back correctly; return to actual caller after edit/dismiss; remove deleted destinations gracefully. Coach and reminder entry contracts remain connected to their later chunks without claiming they are implemented.

**Verify:** healthy/at-risk/new-history states, positive callout threshold, month changes, future/pre-creation/rest-day restrictions, corrections and dependent stats, quantity old-target correction, archive/Undo and delete, repeated operations, caller-aware Back and nested confirmation Back. Check calendar cells do not overlap and the docked action remains accessible.

**Completion gate:** detail and habit management operate against real history; deletion/archiving does not corrupt navigation or erase retained archived history.

### Chunk 07 — Progress and Profile preferences

**Outcome:** complete insight and local preference roots replace their placeholders.

Build shared SCR-12/13: Week/Month toggle, consistency/done/all-time best, seven daily bars, actual monthly week buckets, top-streak rails and detail entry. Use specified bar motions/units, zero/partial data and remembered tab/range/scroll state. Opened details return to Progress.

Build SCR-15 identity/initials, inline local name, lifetime stats, Preferences/Coach/Data groups. Wire Light/Dark/Follow system to actual app theme observation, week-start behaviour and Coach enablement. Keep four tabs; gate unavailable Coach with a useful explanation. Provide reminder, clear-history/export/wipe entries for chunk 8/10. Restore remains visibly disabled as specified.

**Verify:** displayed values against chunk 3 fixtures, 4/5/6-week months, no data, deleted/archived contribution, theme changes and persistence without restart, editable name/initials, week preference, tab state and Progress→detail→Back. Ensure Profile content below the mockup crop is scrollable.

**Completion gate:** Progress and Profile use real shared data; preferences persist and affect presentation; no incorrect duplicate calculations or placeholder screens remain in these roots.

### Chunk 08 — Reminders, export and destructive data controls

**Outcome:** specified system interactions and data controls perform their actual operations.

Implement global reminder toggle/time and per-habit inherited/override controls through compact dialogs/sheets in the existing surfaces. Schedule local notifications with the narrowest appropriate Android mechanism; handle permission denial, reboot/time changes, quota/weekday eligibility, edits and archive/delete. Do not promise exact timing that the chosen mechanism cannot deliver.

Implement explicit versioned export using the system file picker; include defined local history/settings/Coach data as appropriate and make export disclosure accurate. Implement type-to-confirm full wipe, coordinated Room/DataStore reset semantics and return to a consistent root. Keep clear-Coach-history scope separate; chunk 10 will attach its new stores. Prevent stale scheduled notifications, cached data or pending changes from reappearing after wipe. Record the chosen wipe scope in user-facing copy.

**Verify:** inherited/override times, disabled permission, cancellation/rescheduling, pending weekly/daily/custom reminders, destination cancellation/export content, failed I/O, wipe confirmation/cancel, repeated taps and post-wipe state. Validate device-specific scheduling when an emulator/device is available.

**Completion gate:** settings do real work; user export is deliberate; clearing/reminders remain consistent with local-only v1 and history retention.

### Chunk 09 — Supplied strategy retrieval and Coach contracts

**Outcome:** local retrieval and a strict minimal request/response boundary are ready independently of provider choice.

Package the supplied card collection as an app-readable asset without maintaining a divergent hand-edited copy. Validate required fields/unique IDs and preserve source attribution. Implement deterministic local ranking based on measured pattern, planning context, tags and `use_when`; free-text applicability must not become unsupported asserted knowledge about the user. Admit relevant strategies and produce a defined insufficient-context/error result if needed.

Define separate planning/existing-habit payload builders, selected strategy context, bounded question handling, structured reading/three-suggestion response and a closed typed-action schema. Account explicitly for tracking mode, quota and target where needed without expanding personal-data scope. Define a provider-neutral CoachService and explicit unavailable configuration/failure types; keep all fake responses in tests/previews.

**Verify:** all 60 actual cards load unchanged; stable ranking; missing/corrupt assets; unknown/unrequested strategy IDs; non-three/malformed suggestions; incompatible action/mode, invalid quota/unit/amount; no habit collection/name/ID/full-history leakage in existing-habit payloads; minimum planning payload; conditional interpretations.

**Completion gate:** actual resource-backed retrieval and payload/action validation pass contract tests; no provider or production fake data is introduced.

### Chunk 10 — Typed action execution, local history and Undo

**Outcome:** every admitted Apply action has a deterministic safe local effect and precise inverse operation.

Implement handlers for the supported action set: target, cue/anchor/plan, schedule/quota and reminder settings, plus draft-only planning recommendations where appropriate. Save only supported settings; strategy prose about external tasks remains advice rather than invented integration. Ensure action-specific confirmation text.

Add local per-habit Coach thread/cache and action metadata with a versioned migration. Cap threads at 50 messages, attach Clear coach history/export/delete/wipe and retain action identity. Apply atomically and immediately, preserve changed-field prior values, expose ten-second Undo and perform conflict-aware reversal. Handle settings history revisions, same-field subsequent edit, navigation/recreation/deadline and repeated Apply/Undo. Preserve completions and unrelated changes.

**Verify:** each action applies only validated fields; schedules/targets keep historical truth; prior schema migrates; unknown actions are rejected; duplicate Apply is guarded; Undo before/after deadline, recreated screen, changed same/other field, deleted habit, clear/wipe and pending amount log. Use controlled time and transactional failure tests.

**Completion gate:** real persisted actions and draft actions behave correctly; Undo cannot restore an entire stale habit over later edits; no informational card falsely reports a goal mutation.

### Chunk 11 — Coach screens, draft detours and root selection

**Outcome:** SCR-06/09/10/11 are fully wired to the local contracts and original UI.

Build shared Coach layout/variants: context chip, section/reading, three strategy cards, Apply, accurate applied confirmation/Undo, docked input and persisted conversation. Preserve loading shimmer, insufficient history, cached note, offline/timeout/rate-limit/server layouts, retry cooldown/backoff and message/input/keyboard behaviours.

Wire empty Home→planning→Apply→form without inserting a habit; form→planning→same draft; existing detail→Coach→detail; error/applied variants in place; Coach tab's local habit selection through a small sheet when needed. Gating handles disabled Coach at entry and during a pending request. A successful test service may drive instrumentation/preview states; production remains honestly unavailable until chunk 12 configures the actual service.

**Verify:** full caller/draft Back routes, cancel creates no habit, recreation, multi-habit selection sends only selected summary, three-card rendering, Apply/Undo state, follow-up loading/send guards, cached failure display, disabled Coach, mid-request disable, all modal/input insets and screen comparisons in both themes.

**Completion gate:** the UI and local operations are complete and integrated. Record explicitly that live external service is pending; test-driven screen success is not live Coach verification.

### Chunk 12 — Real external AI service integration

**Outcome:** the specified external Coach works with the agreed provider and restricted payload.

Resolve the external configuration checkpoint using the already built request/response contract. Integrate provider-specific transport/authentication with minimum necessary dependencies and network permissions. Keep credentials safe and configuration explicit; a secure narrow request relay, if actually required and authorised, must not evolve into a user habit database or account architecture.

Handle actual connectivity, timeout, cancellation, provider error/rate-limit response, retry policy and structured-response validation. No automatic broadening of payload or fallback to a different provider. Ensure clearing/disabling and screen recreation do not dispatch duplicate calls. Preserve all local functionality when service is unavailable.

**Verify:** deterministic transport tests for success/errors/malformed data; captured sanitised request shape; no secrets/personal payload in logs; exactly one dispatch per intended interaction; selected strategies/typed response enforcement. Run a live smoke test with an explicitly suitable test draft and minimal data when configured and authorised, without using an owner's real history implicitly.

**Completion gate:** provider wiring is configured and validation passes. Claim live verification only when performed; if credentials/network prevent it, record the precise remaining dependency and leave live verification incomplete.

### Chunk 13 — End-to-end verification and design completion

**Outcome:** evidence-backed completion of all five features, shared screen states and flows.

Run the full project build, relevant unit/instrumentation suites and lint. Exercise FLOW-01–14 plus edit/save, quantity input, calendar corrections, Coach root selection, reminders, export, history clear and full wipe. Verify both tracking modes/all schedules through real persistence and recreated navigation. Run migration from v1 and any later schemas through the final schema.

Compare actual rendered screens with the supplied mockups at 393 × 832dp in light/dark, then smaller-screen, large-text and keyboard/system-inset configurations. Correct only fidelity/behaviour defects and necessary adaptations. Check spoken labels/states and non-overlapping 48dp targets. Review final packaged permissions/backup/network configuration and removal of user-facing stub notes/fake responses.

**Verify:** empty/error/partial data; long streaks; month/week boundaries and midnight; historical schedules/targets; risk/recovery; archived/deleted contributions; repeated taps; quantity units; disabled/offline Coach; Apply/Undo conflicts; caller-aware Back and tab restoration. Record actual device/emulator evidence and environment limitations.

**Completion gate:** all required implementation is complete; remaining unverified platform/live-service checks are explicitly listed. No missing feature is hidden behind “build passes.” Deliver a concise completion/verification report and build artifact when available, preserving the academic design.

## 6. Traceability

| Requirement | Main implementation chunks |
|---|---|
| SCR-01 Splash / SCR-02 Onboarding | 02, 05, 13 |
| SCR-03/04 Home empty/today | 03–05, 13 |
| SCR-05 New/Edit Habit | 01–04, 06, 11, 13 |
| SCR-06 Coach planning | 04, 09–12, 13 |
| SCR-07/08 healthy/at-risk detail | 01–03, 06, 13 |
| SCR-09/10/11 Coach suggestions/applied/error | 09–13 |
| SCR-12/13 Progress Week/Month | 01–03, 07, 13 |
| SCR-14 management sheet | 06, 08, 13 |
| SCR-15 Profile/Settings | 02–03, 07–08, 10, 13 |
| FLOW-01–05 launch/create/planning | 04–05, 11, 13 |
| FLOW-06/07 detail entry | 05–07, 13 |
| FLOW-08–10 Coach entry/apply/failure | 09–13 |
| FLOW-11 management | 06, 08, 13 |
| FLOW-12–14 tabs/ranges/profile | 05, 07, 13 |
| History, quotas, binary/quantity, migrations | 01–04, 06, 10, 13 |
| Privacy, actual strategies, payload boundary | 02, 08–09, 12–13 |
| Reminders, export, destructive clearing | 08, 10, 13 |

## 7. Verification commands and evidence

During implementation use the Gradle wrapper and existing configured dependencies. The usual checks are `./gradlew :app:assembleDebug :app:testDebugUnitTest` for compilation/unit behaviour, `./gradlew :app:lintDebug` for relevant static checks, and `./gradlew :app:connectedDebugAndroidTest` when a suitable device/emulator exists. Run affected tests per chunk; do not repeat every device scenario after a harmless documentation edit. Add Room migration testing support only when needed for meaningful migration verification.

Commands actually executed are recorded per chunk below. Environment/toolchain/network problems must be reported accurately and resolved under applicable tool permissions. Do not install or upgrade unrelated tooling merely to follow the plan.

For each check record whether it was executed, passed/failed, and its practical limit. Preserve test fixtures only in tests; screenshots/previews alone do not prove persistence, migrations, transport or notifications.

## 8. Progress and handoff record

Current status: **chunks 01–11 complete; chunk 11 explicitly authorised and completed on 7 October 2026**. Chunks 12–13 remain not started. Chunk 10 was explicitly authorised and completed on 7 October 2026. Chunk 02 was explicitly authorised by the owner, including fixing the app's visible name; chunk 09 was explicitly authorised on 6 October and completed after resuming on 7 October 2026.

Append one concise entry per completed or paused implementation chunk using:

```text
Chunk ID and status:
Implemented outcome and affected areas:
Necessary interpretation/deviation and reason:
Checks actually executed and results:
Known limitations or remaining consequential decision:
Next chunk/dependency:
```

Update the overview's status at the same time. Do not mark a chunk complete while its required implementation remains missing. Distinguish completed implementation from environment-limited verification; do not erase pending evidence from the final record.

### Chunk 01 — Complete, 6 October 2026

- **Implemented:** independent pure `HabitHistory`, schedule/tracking/log/effective-setting types, decimal quantity and eligibility validation, historical correction, typed schedule/tracking changes with safe effective dates, injected clock/date source, ordered occurrence replay, all-time streaks, risk/recovery and consistency counts. Added focused JVM tests and one Android date/decimal/runtime test. Recorded the approved contract in `DOMAIN_BEHAVIOR.md`; updated standing instructions for owner-run Git and approval between chunks.
- **Policy:** owner explicitly approved Weekly quotas 1–7, Monday–Sunday periods independent of display preference, capped distinct-date successes, shortfalls ordered after successes at closure, prorated creation weeks, archive cancelling pending slots, occurrence streak units and effective edit dates. Pending expectations remain in consistency/progress denominators under the original review while being excluded from misses/risk. No unresolved chunk 01 product checkpoint remains.
- **Checks actually run:** baseline `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed; history-only tests plus assembly passed; all domain tests plus assembly passed; final `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed after final changes. Final reports show **34 JVM tests (33 new domain tests + 1 existing example), 2 Pixel 7 device tests (1 new runtime test + 1 existing context test), zero failures/errors/skips**. Domain cases include 1,000-completion streaks, history corrections, target/unit changes, every frequency, Weekly extra-log reallocation, partial/archive weeks, risk recovery/re-entry and Dhaka midnight. `git diff --check` passed. Git operations were limited to read-only status/diff inspection.
- **Verification limits:** the new domain is intentionally not wired to v1 persistence/screens until chunks 02–03; the existing repository/legacy calculator still have their documented limitations. Device tests validate Android runtime integration, not screen fidelity or every navigation flow. Room/DataStore migrations and UI changes are outside this chunk; no claim of runtime verification for them. No dependency or supplied resource changes.
- **Next:** chunk 02, versioned persistence/data-preserving migration, effective settings/quantity storage, transactional operations, compatible preferences, backup/privacy rules and public branding. Obtain the owner's chunk approval first; preserve v1 Weekly weekday-mask history rather than reinterpret it as a quota.
- **Suggested one-line commit:** `feat: add historical habit domain rules and verified weekly quota calculations`

### Chunk 02 — Complete, 6 October 2026

- **Implemented:** Room v2 with separate schedule/tracking effective histories, frozen local creation/archive dates, exact decimal quantity columns, cue/anchor/plan and inheritable per-habit reminder fields. Added registered v1→v2 migration and exported `2.json`; original `1.json`, database filename and legacy metadata remain unchanged. Added transactional create, settings/metadata changes, date correction, archive/five-second scoped Undo and cascading delete, plus transactional observations including archived records. Existing form/toggle writes now use the shared repository/container so new records always have history. Legacy rest-day controls cannot write misleading completions. Added compatible DataStore week-start, Coach enablement and reminder configuration with safe defaults and preserved keys.
- **Brand/privacy:** launcher label, Splash semantics and onboarding now use Habit Companion; Coach copy no longer promises that nothing leaves the device. Manifest disables backup; legacy backup and modern cloud/device-transfer rules exclude all nine private storage domains, including future stores. No cloud database, PostgreSQL installation, accounts or new production dependency. Backup rules were reviewed against [Android's official backup documentation](https://developer.android.com/identity/data/autobackup); actual cloud-restore/device-transfer/OEM transport operations were not run. Cross-platform matching is not configured: there is no iOS app identifier/team or custom backup agent. Do not claim all platform transports have been runtime-tested.
- **Necessary clarification:** archive-day logs already recorded before archiving remain historical facts, while unfinished archive-day expectations and interrupted Weekly quota slots are cancelled. Updated the domain contract/calculator and regression tests; new archive-day logging stays prohibited. Creation uses one captured instant for timestamp/date across midnight. Pending schedule and target edits are stored independently so neither overwrites the other.
- **Checks actually run:** initial assembly, unit tests and test-APK compilation passed. Room's added migration-test helper failed before the migration due to a serialization runtime compatibility error; removed that test-only dependency. The replacement test creates a database directly from the unchanged exported v1 schema and opens it through the production Room builder, exercising migration and Room's generated v2 schema validation. It checks IDs, names, icons/colours, timestamps, archive state, goals/counts, binary meaning and historical Weekly masks; even an invalid legacy mask remains in storage and raises an explicit domain validation error.
- **Final verification:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` passed after all changes. **35 JVM tests and 15 Pixel 7 device tests passed, zero failures/errors/skips.** Device tests cover migration, reopen/decimal round-trip, pending independent edits, transaction rollback, date/unit guards, repeated corrections, atomic reactive observations, archived retention, scoped/expired Undo, cascade deletion, midnight creation, old/new DataStore persistence, packaged brand/backup rules and prior runtime/context checks. Lint passed with **zero errors, 23 advisory results** for existing dependency/API age, redundant label, plural candidates and unused resources; no gratuitous toolchain upgrade. `git diff --check` passed. Final cold startup on Pixel returned `Status: ok`.
- **Device-test cleanup correction:** AGP initially uninstalled the debug app after connected tests; startup then reported the activity absent. This can clear pre-existing debug-app local data, so do not claim real phone data survived those test runs. Added `android.injected.androidTest.leaveApksInstalledAfterRun=true`, reran all device checks successfully, and verified the app remains installed and launches afterward. Future physical-device tests must retain APKs and use isolated test databases/preferences. No Git mutation was performed.
- **Remaining scope/limits:** migration and persistence are verified using isolated databases; full UI/Coach/notification flows are not complete. Current screen statistics still use the legacy calculator until chunk 03; theme/Coach/reminder settings are persisted but their remaining screen/service wiring belongs to later chunks. v1 had no original creation-zone history, so migration freezes its local interpretation in the device's migration-time zone rather than inventing past travel context. Invalid legacy records are retained and surfaced for error handling, not silently erased or guessed.
- **Next:** chunk 03, shared historical calculations/aggregation, lifecycle-aware date refresh and separate loading/data/error/retry observable states. Replace the 400-day query/calculation path and make archive/delete/correction changes update every statistic consistently. Obtain owner approval before starting.
- **Suggested one-line commit:** `feat: migrate local habit storage to v2 and rename app to Habit Companion`


### Chunk 03 — Complete, 6 October 2026

- **Implemented:** `StatsAggregator` and atomic `HabitSnapshot` over complete historical Room records; removed the 400-day window and legacy `StreakCalculator`. Shared facts include eligible weighted totals/consistency, selected-range/month rate, quantity partial/achieved status under dated targets, current/all-time best streaks, neutral/attention state, active top streaks, seven daily week buckets and actual 4–6 clipped month-week buckets. Archive keeps historical contribution and leaves active/top rows; deletion and correction update the same snapshot without separate screen rules. Weekly quota weighting and period boundaries retain the approved contract.
- **Date/state integration:** dynamic-zone `DeviceClock` plus shared `DateMonitor`, local-midnight timer, Activity resume and date/time/time-zone broadcasts. Today's writes recheck the captured date inside the Room transaction, distinct from intentional past corrections. Home now separates loading, genuine empty, no-due, all-done, read error/retry and write failure; retains successful data after failure and disables stale/in-flight writes. Retry stays on Home instead of navigating back to removed Splash. Best streak displays the all-time definition, including archived records. Rest dates cannot trigger completions; binary controls announce checked state and the correct mark/unmark action, and new habits remain neutral. Frequency text distinguishes Weekly quota from Custom weekdays; quantity partial progress comes from the historical target.
- **Necessary accommodations:** a clock/time-zone rollback evaluates stored facts as of the displayed date while preserving later records; future writes remain prohibited. Updated `DOMAIN_BEHAVIOR.md` to document date/range aggregation and retired integration limits. Four Compose tests initially failed before assertions because Espresso 3.5.1 reflects an InputManager method absent on Android 17. Updated only Android test dependencies to Espresso 3.7.0 and JUnit extension 1.3.0; [official AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/test) identify the corresponding InputManager fix. No production dependency, database schema, provided resource or design replacement.
- **Checks actually run:** compilation and the existing 35 unit tests passed at the first integration step; calculation/date tests passed; Home state tests brought the suite to 58 passing tests. Fixed one test assertion API mismatch during test-APK compilation, then all 20 non-UI device tests passed while the old test framework blocked four UI checks. After the justified test-library update, all 24 device tests passed. Following clock-rollback regression coverage, final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest` passed with **61 JVM tests and 24 Pixel 7 (Android 17) device tests, zero failures/errors/skips**. A fresh `./gradlew :app:lintDebug --rerun-tasks` passed with **zero errors and 24 advisories** (dependency/API age, redundant label, plural candidates and unused resources). This also cleared obsolete version quick-fix locations from the earlier cached lint report. `git diff --check` passed. Pixel cold startup returned `Status: ok`. A later process check found the task closed; Android exit-info reported `USER REQUESTED / REMOVE TASK`, and the crash buffer contained no Habit Companion exception. Do not describe that closed process as still running. Tests use isolated databases/preferences and retain installed APKs; no uninstall/data-clear or Git mutation was performed.
- **Coverage:** weighted counts rather than average percentages; zero/future denominators; dated quantity thresholds/corrections; 1,000-day repository streak and 700-day historical best; archived/deleted contribution; coherent reactive risk/recovery/current/best corrections; Weekly deadlines/extras/pending quotas; all month/week-start combinations for 2020–2030; midnight, resume/time jumps, time zones, 23/25-hour DST days and backward date refresh; read/preferences failure and retry; cached-data retention; repeated taps; failed/stale writes; disabled rest-day controls and completion accessibility semantics; v1 migration/DataStore/privacy regressions from chunk 02.
- **Remaining scope/limits:** shared inputs are ready for detail/Progress/Profile; those screens, saved theme UI, full navigation and complete Home composition belong to later chunks. Quantity amount input on Home belongs to chunk 05 and detail/corrections to chunk 06; this chunk supplies correct quantity facts and prevents binary writes to quantity habits. Midnight/resume/time-zone behavior was tested with controlled clocks rather than changing the owner's phone settings. UI tests cover the affected state/semantics, not complete mockup fidelity, large text, keyboard/insets or every Back flow. Gemini Flash free API tier preference is recorded in `AGENTS.md`; no credentials, external Coach request or live model test was added. Exact integration and applicable free-tier terms remain chunk 12 work.
- **Next:** chunk 04, faithful shared New/Edit form with Daily/Weekly quota/Custom controls, binary/quantity target/unit fields, retained draft and planning-detour contract, validation, loading/error and duplicate-submit guards, dirty Back confirmation, recreation and historical edits. Obtain owner approval first.
- **Suggested one-line commit:** `feat: unify historical habit statistics and refresh Home state safely`


### Chunk 04 — Complete, 6 October 2026

- **Implemented:** shared New/Edit form matching SCR-05's header/name/appearance/frequency/Coach/docked-action hierarchy, five supplied icon/colour choices and retained legacy appearance. Daily, Weekly quota and Custom are distinct; binary is recommended/default, with conditional quantity target/unit fields. Validation, dirty Back confirmation, active duplicate-name warning/confirmation, saving/error/retry and repeated-submit protection are wired to real local persistence. Edit retains the ID and previous completions, loads independently pending schedule/tracking settings and uses the approved historical effective dates. Atomic saves merge unrelated later changes and reject conflicting same-field changes without partial writes. No dependency, schema or original resource change.
- **Draft/navigation contract:** primitive SavedStateHandle fields retain the draft and successful save ID; a stable draft token scopes typed planning results. Shared route helpers carry optional habitId/planning flags. Empty-Home planning enters the unsaved creation flow; a validated primitive result returns to the same form through its back-stack SavedStateHandle. The actual Coach destination remains chunk 11; today's card reports that it is not connected and keeps the draft. The local context is not an external payload. Edit's management entry remains chunk 06.
- **Minimum adaptations:** approved tracking/quota controls require scrolling within the original form hierarchy; the Coach card stays before the docked Create/Save button. Visual weekday circles remain 40dp inside separate 48dp checkbox targets; small widths permit horizontal scrolling. Heading/button heights grow only when text needs more room, and IME/navigation insets keep focused input and Save accessible. Minor duplicate-name ambiguity is resolved as warning plus explicit confirmation, with an atomic final check; names are not unique database identities. Documented in `DOMAIN_BEHAVIOR.md`.
- **Checks actually run:** compilation and successive unit/device suites passed at logical integration steps. The first screenshot-only failure was an unavailable test-APK cache directory, fixed by using a dedicated target-app QA cache directory; no database/preferences were cleared. Reference capture was explicitly switched to light theme and keyboard visibility asserted rather than inferred. Final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` passed with **81 JVM tests and 44 Pixel 7 (Android 17) device tests, zero failures/errors/skips**. Lint has **zero errors and 24 existing advisories**. Final cold launch returned `Status: ok`. Tests retain installed APKs and use isolated databases/preferences. No Git mutation was performed; current staging remains the owner's responsibility.
- **New coverage:** both modes/all schedules; invalid quota/decimal/unit/custom selection; trimmed/case-insensitive duplicates and insertion races; repeated/failed saves; dirty Back; wrong-draft/disabled-Coach/invalid typed results; successful navigation detour without insertion; primitive Bundle/Parcel restoration through a real Room-backed ViewModel; edit ID/history/effective settings; independent pending edits; numeric target equality; SQL-trigger rollback; stale same-field conflicts; unrelated reminder/cue/completion preservation; deleted/archived stale forms; duplicate confirmation and Edit Save UI. Existing migration, DataStore, date, statistics and Home regression tests also passed.
- **Visual evidence/limits:** inspected rendered light 393 × 832dp and dark 360 × 640dp/font scale 1.5 form fixtures with the keyboard visible, plus scrolled Coach card. Root captures exclude the native keyboard, while the test asserts its inset/visibility. These checks are not a claim that all app screens are complete or every OS process-death point is tested. Actual OS kill during save was not simulated. Production Home quantity input, full navigation, detail/management, theme preference wiring and live Coach remain their planned later chunks. `HANDOFF.md` records the concise current state and next-chunk startup instructions.
- **Next:** chunk 05, faithful empty/populated Home and launch/main navigation, compact quantity input, correct detail route contract, tab state preservation, removed launch destinations and specified transitions. Verify first/subsequent launch, Back, binary/quantity logging, state/error paths and Home/onboarding light/dark responsiveness. Obtain owner approval before starting.
- **Suggested one-line commit:** `feat: complete habit forms with retained drafts and historical edits`


### Chunk 05 — Complete, 6 October 2026

- **Implemented:** faithful Home header/date, outlined summary with done/total ring and honest remaining/no-due/all-done copy, all-time Best, coloured icon → name/helper → real streak → separate 48dp completion controls, attention grouping and loading skeleton. Daily/Custom binary logging and compact quantity entry/clear use today's historical target/unit and the existing transactional date/eligibility checks. Partial amounts remain distinct from achieved targets; Weekly quota weighting is unchanged. The amount draft uses primitive SavedStateHandle fields; failed writes retain input, read failure offers Retry inside the dialog, repeated submissions are guarded and date/deletion/archive/expectation changes block stale submission. Disabled Coach hides the empty shortcut and has an explanatory root state; no network request was added.
- **Launch/navigation:** retained `main` graph anchored to Home, removed Splash/onboarding after routing, four-tab save/restore/single-top and Back-to-Home. Onboarding uses Continue on all three panes, persists before exit, guards repeated finish and handles save failure locally. Controlled Splash timing/read/timeout tests preserve cancellation. Habit rows now pass a local Long ID to `habit/{habitId}` and return through their caller's stack. Detail is explicitly unfinished until chunk 06; Progress/Profile/Coach content remains assigned to later chunks. New/Edit routes, planning flag/token and result envelope remain unchanged.
- **Minimum adaptations:** rows, date chip and Coach shortcut grow for larger text; onboarding body scrolls instead of hiding privacy copy. The summary ring grows only when font/count size requires it. The Add button retains its bottom-right position above the tabs in a reserved strip, so scrolling rows cannot be covered by its touch target. Launch fades use 200ms, sibling tabs 150ms, form entry/pop 250ms. The temporary detail port uses a 300ms fade/scale; its actual container/shared-element transition belongs to chunk 06 when detail content exists, not a claimed platform limitation. No schema, dependency, preference key, database identity or supplied resource changes.
- **Checks actually run:** baseline assembly/unit tests passed, followed by quantity/launch unit checks and test-APK compilation. First full device run passed 56/57 checks; the sole failure was a visual test locating an uncomposed lazy-list row. Replaced direct node scrolling with list scrolling; all six visual checks passed. Visual inspection then caught large-text summary crowding and FAB/control overlap, both corrected and explicitly checked. The first amount screenshot selected the underlying Home window; corrected it to the dialog and added a native IME visibility assertion. Final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` passed: **92 JVM tests and 59 Pixel 7 Android 17 device tests; zero failures/errors/skips. Lint zero errors, 23 existing advisories.** `git diff --check` passed. Verified the debug app remains installed and launches. Tests retain APKs and use isolated Room/DataStore fixtures; no real data clear, uninstall, Git mutation or external Coach traffic.
- **Coverage/evidence:** binary/quantity partial/achieved/clear, Weekly quota counts, Custom rest-day guards, repeat/failure/retry, saved amount state and stale date/deletion/read checks, Coach setting changes, first/subsequent graph launch, Skip/Continue/pane Back, removed launch entries, repeated tabs, retained/restored root state and detail return to Home/Progress. Existing historical/migration/form/privacy/date regressions passed. Compared supplied SCR-01–04, FLOW-01–05/12/14 and Home/onboarding mockups with rendered light/dark 393 × 832dp and dark 360 × 640dp/font 1.5 fixtures, including scrolled content and separate completion targets. Quantity dialog was checked with large text and native keyboard visible on Pixel; root/dialog PNGs omit the native keyboard itself. Generated captures live only in `cache/chunk05-qa` (local inspection copies in `/tmp/habit-chunk05-qa`).
- **Limits:** forced Compose viewport/font checks do not change the owner's device settings or prove every OEM/OS layout. Launch branch tests use isolated inputs through the production graph/screens; no uninstall or real preference reset was used to simulate a fresh install. Saved-state tests do not prove every arbitrary OS kill during a write. Detail/management, actual Progress/Profile, saved theme wiring, reminders/export and live Coach remain later chunks. No consequential chunk 05 product decision remains.
- **Next:** chunk 06, shared healthy/at-risk detail, month/calendar and eligible binary/quantity past corrections, overflow/long-press management, edit/reminder entry, archive with five-second Undo, delete confirmation and caller-aware Back. Obtain owner approval first.
- **Suggested one-line commit:** `feat: complete Home logging and retain launch and tab navigation state`


### Chunk 06 — Complete, 6 October 2026

- **Implemented:** replaced `PendingHabitDetail` with shared SCR-07/08 detail, scrolling title/collapsed app-bar title, current/all-time best/selected-month rate, truthful occurrence-based attention/recovery sentence, the exact positive-callout threshold, dated month ratio, seven-column 4–6-row calendar, legend and docked outlined/filled Coach action. Attention preserves positive streaks; new history stays neutral. Calendar separates achieved/partial/pending/flexible/rest/unavailable states; Weekly shortfalls remain quota outcomes rather than invented missed weekdays. Month arrows and reference-width grid swipes change the same saved month. Disabled Coach has an explanatory entry state; enabled Coach honestly reports the pending integration.
- **Corrections:** primitive SavedStateHandle drafts retain date, opening date, amount, historical schedule/tracking/target/unit and original log. Binary mark/unmark and decimal quantity save/clear use `HabitOperations.correctChecked`, comparing the displayed original facts inside the Room transaction. Eligibility, stale mode/unit/target/log, archive/delete, current-day midnight races, invalid input, repeated taps and failures are guarded. Only the selected date changes; shared Room observations recompute Home/detail/all dependent metrics. No schema/database/preference identity, production dependency, source resource or New/Edit planning-contract change.
- **Management/navigation:** shared sheet above destinations, entered through Home long press or detail overflow; supplied header, Edit, Change reminder entry, Archive, separated Delete and Cancel. Edit uses the existing saved form and returns to the actual caller. Archive removes active Home/detail immediately, retains history and exposes timestamp-scoped five-second Undo across the return to Home/Progress; expiry and restoration use primitive state. Delete names the habit and all-time streak, requires confirmation and cascades. Nested dialog Back leaves the sheet, then sheet Back dismisses. Snapshot/removal navigation checks the exact current entry to prevent double pops. Home/detail name bounds now share a real 300ms transition with the detail content; launch/tab/form contracts are retained.
- **Minimum adaptations/limit:** calendar dots remain 28dp inside distinct >=48dp cells. Widths below 336dp of content scroll the grid horizontally; month header swipes/arrows remain available there. Larger text grows/scrolls, stat labels align, and the docked Coach action stays accessible. Shared C-05 dots and filled attention action follow the explicit tables despite the square/outlined alternative picture. The installed Material3 1.4.0 exposes sheet motion through internal `MotionScheme`/motion-spec members, so the native sheet/predictive-Back motion is retained instead of claiming an exact 250ms duration. No stack upgrade or custom replacement sheet was added.
- **Checks actually run:** baseline assembly/unit tests passed; stepwise integration and expanded unit/test-APK compilation passed. First device checks caught zero-width weighted grid cells inside an unbounded scroll container; explicit bounded grid measurement fixed them. Stronger visibility checks required scrolling the outer lazy list to its calendar item; removing an unnecessary reference-width horizontal scroller resolved a real month-swipe interception. Two earlier checks lost foreground Compose hierarchies during physical navigation; device logs showed navigation gestures and no Habit Companion crash. The owner then authorised an uninterrupted rerun. Final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` passed with **104 JVM tests and 73 Pixel 7 Android 17 device tests; zero failures/errors/skips. Lint zero errors, 26 advisories** (existing dependency/API version checks, redundant label, plural candidates and unused resources; none located in the new detail/management files). `git diff --check` passed. Verified the debug APK remains installed; final cold launch returned `Status: ok`, `LaunchState: COLD`. No data clear, uninstall, external Coach traffic or Git mutation.
- **Coverage/evidence:** healthy/attention/neutral states and positive threshold; preserved attention streak/recovery; all month dates/leap day, Weekly flexible marks and weighted shortfalls, dated quantity targets/partial/achieved/clear; eligibility/future/pre-creation/rest, correction races, transaction rollback, missing/archived records, read/write failure/retry and midnight stale guards; saved primitive and Bundle/Parcel restoration through Room-backed detail; repeated management, archive retention/scoped Undo/expiry, named delete/cascade, long-press Edit, modal/dialog Back, Home/Progress callers and safe removal. Inspected supplied SCR-07/08/14, FLOW-06/07/11 and system-design p.3 against rendered light healthy/dark attention 393 × 832dp and dark 360 × 640dp/font 1.5, scrolled calendar/legend, management sheet and quantity dialog with native IME visibility asserted. Captures remain only in target `cache/chunk06-qa` and inspection copies `/tmp/habit-chunk06-qa`.
- **Limits:** forced viewport/font and saved-state reconstruction do not prove every OEM setting or arbitrary OS kill during a write. Native keyboard itself is outside the dialog PNG. Real Progress content/Profile/theme are chunk 07; notifications/export/wipe remain chunk 08 and Coach integration remains chunks 09–12. The Progress caller test uses the production graph with an isolated root port, not a claim that Progress content exists. No consequential chunk 06 decision remains.
- **Next:** chunk 07, actual Week/Month Progress and Profile identity/lifetime statistics/preferences; reactive theme, week-start and Coach settings, retained root/range state and Progress→detail→Back. Obtain owner approval before starting.
- **Suggested one-line commit:** `feat: add historical habit detail corrections and safe management actions`


### Chunk 07 — Complete, 6 October 2026

- **Implemented:** actual shared SCR-12/13 Progress root with saved Week/Month range, retained list state, weighted consistency/done and all-time best. Seven daily required-completion bars and actual 4–6 monthly weighted consistency buckets reuse the complete snapshot. Bars animate 400ms with 40ms stagger; monthly values >=10% sit above the filled bars, values below 75% use lighter primary. Top positive active streaks have proportional rails, 48dp minimum rows/8dp gaps, truthful occurrence units and detail entry/actual-caller Back. Progress names reuse the existing 300ms Home/detail shared transition. No new statistics engine or fixed five-week assumption.
- **Profile/preferences:** local 100dp initials avatar, inline retained/validated name, active count and honest earliest-history “tracking since” summary, lifetime shared best/completed/consistency. Preferences/Coach/Data groups include actual Light/Dark/Follow system, Monday/Sunday display start and Coach toggle; available settings persist independently of statistics failure. Immediate confirmed writes, repeated-tap guards and retained failed input; Cancel/Back does not apply the name draft. Activity observes theme and system-bar appearance without restart. Reminder/export/wipe/history entries accurately report later integrations; Restore is disabled. No schema/database filename/preference key, dependency, source resource, New/Edit draft/result contract or external Coach change.
- **Minimum adaptations:** separate >=48dp range targets, growing/scrolling text and inline keyboard accommodation, multiline chart units when needed, measured stat figures with equal-height cards, reduced large-text label tracking to retain words, explicit 100dp avatar per the table. Occurrence/required-completion copy supersedes misleading days/habit-day language for Weekly quotas. Earliest retained habit month is not an invented account join date. Native sheet fallback from chunk 06 is retained.
- **Checks actually run:** baseline/stepwise assembly, unit and test-APK checks passed; 10 new JVM tests bring the suite to **114** and 12 new Pixel workflows bring the device suite to **85**. Visual QA fixed crowded month units and large-text figures/card alignment. Final fit assertions check actual one-line percentage glyphs/bounds and equal card heights, rather than paragraph-container overflow. One intermediate native Back test lost focus. A full run passed, then a rerun after shared-name/value-label changes disconnected after 40 checks; the owner reconnected the Pixel. The resumed full `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` passed **114 JVM and 85 Pixel 7 Android 17 tests, zero failures/errors/skips**. After changing the chart offset to its lambda overload to remove a new lint advisory, the same command with `-Pandroid.testInstrumentationRunnerArguments.class=com.example.habit.ui.screens.insights.InsightWorkflowUiTest` passed all **114 JVM and 12 affected device tests** again. Final lint: **zero errors, 26 existing advisories**. Full device reports are preserved under `app/build/reports/chunk07-verification/full-device/`; standard connected reports now contain the final 12-test rerun. Final captures were transferred and inspected; retained package and cold launch (`Status: ok`) verified. `git diff --check` passed.
- **Coverage/evidence:** shared weighted fixtures, binary/quantity dated targets/partial results, flexible quotas/pending slots, 4/5/6 month buckets, week-start without quota repartition, zero data and archived/deleted totals, long all-time best, date/correction refresh, cached-read error/retry, settings independence, trim/initials/validation/cancel, repeat/failure/retry and retained name/range primitives; real isolated DataStore writes/reopen, immediate token/system-bar theme and Follow system, native keyboard visibility/inline Done, saved Bundle/Parcel name, substantial root scroll/range retention and actual Progress/detail/Back through the production graph, disabled Restore and honest deferred operations. Previous migration/domain/forms/Home/detail/privacy/navigation tests passed in the successful 85-test run. Inspected SCR-12/13/15, FLOW-12–14 and system-design p.4 against rendered light reference 393 × 832dp, dark 360 × 640dp/font 1.5, scrolled rails/Data, theme dialog and keyboard-visible inline name. Final captures remain in target `cache/chunk07-qa` and inspection copies `/tmp/habit-chunk07-qa`.
- **Limits:** tests use isolated Room/preferences and retain APKs; no real-data reset, uninstall or Git mutation. Forced configurations/saved-state reconstruction do not prove every OEM layout or arbitrary process kill, and captures omit the native keyboard itself (visibility is asserted at runtime). There is no remaining chunk 07 implementation check or consequential product decision.
- **Next:** chunk 08, real inherited/override reminders and permission/eligibility/rescheduling, explicit versioned file-picker export and type-to-confirm coordinated local clearing, with Restore disabled and Coach-history attachment remaining chunk 10. Obtain owner approval before implementation.
- **Suggested one-line commit:** `feat: complete Progress and Profile with reactive local preferences`


### Chunk 08 — Complete, 6 October 2026

- **Implemented:** faithful inline global reminder toggle/time and per-habit inherited/off/custom dialogs. Field-only conflict-checked writes, busy/retry/saved time state, explicit notification permission/settings access and honest inexact timing. One local AlarmManager alarm rechecks permission/channel, current dated binary/quantity expectations, Daily/Custom/Weekly pending eligibility, generation/date/time/zone and a persisted same-date attempt ledger. Observations/resume/boot/time/date/zone/package events rearm; completion, quota achievement, archive/delete and wipe cancel applicable alarms/notifications. No exact-alarm permission, dependency or external service.
- **Export/clear:** deliberate system-picker/granted-URI format-v1 JSON includes schema-v2 raw/legacy/archived records, exact quantities, pending histories, cues/plans/settings. Excludes operational state/drafts; Coach storage marked unimplemented until chunk 10; Restore disabled. IO runs off the UI thread, snapshots/writes are coordinated, cancel writes nothing and failures never claim success. Exact CLEAR resets Room/preferences/drafts/navigation/reminders to onboarding while retaining exported files/Android notification settings. Shared gate plus durable reset intent/generation supports failure/recovery, rejects old queued writes and retains non-reused IDs. No source resource/schema/database identity/old preference key/normal draft-contract or Git change.
- **Interpretation:** global enablement is the master; custom times respect it. Undelivered eligible reminders can catch up within the same day; no yesterday spillover or repeated same-date attempt. Ledger-before-post can lose a notification on abrupt process death; timing/force-stop limits are disclosed. Wipe is logical clearing; marker-write failure does not erase data, and later failures require safe recovery. Documented in DOMAIN_BEHAVIOR.md.
- **Checks actually run:** compilation, 12 focused JVM tests and 33 Pixel 7 Android 17 checks passed, zero failures/errors/skips; lint zero errors/26 existing advisories. The 33 include 18 new store/UI cases and 15 directly affected form/preferences/Profile/management regressions. Separate successful narrow reruns verified native keyboard/insets with accessible time actions and full native picker IO/JSON reopening through private fixtures. Earlier harness failures (selectors, Back, test main thread, retained alarm diagnostic history, native window/case handling and Kotlin-in-standalone-test-provider) were fixed. Android 17 rejected shell QA-component toggles; a default-hidden signature-protected test-root control now acknowledges cleanup and was retested. No owner data or permission reset, uninstall command or Git mutation. Retained debug installation/cold launch/process life verified; git diff --check passed.
- **Evidence/limits:** supplied SCR-14/15/FLOW-11/14 composition compared with light reference and small/dark/font-1.5 Data/dialog captures. Disabled destructive colour and disclosure length corrected. Native keyboard visibility asserted; PNGs omit the keyboard itself. Preserved 33-case reports at app/build/reports/chunk08-verification/focused-device; final connected reports contain the last native-export check. Unchanged-schema migrations/unrelated full suites intentionally deferred under owner direction. Actual reboot/Doze/OEM timing, granted-permission notification display, cloud providers and arbitrary process kill remain documented platform checks for chunk 13. Coach data attachment remains chunk 10.
- **Next:** chunk 09, unchanged supplied 60-card catalog, deterministic relevant retrieval and strict minimal Coach payload/response/action contracts. Obtain owner approval first.
- **Suggested one-line commit:** `feat: add local reminders, versioned export and coordinated data clearing`

### Chunk 09 — Complete, 7 October 2026

- **Implemented:** generated asset packaging from the unchanged supplied 60-card resource; strict ID/field/attribution loading with local provenance and honest missing/corrupt failures. Deterministic relevance scoring uses planning/measured tags, question tags and `use_when`, with stable ID ties and conditional applicability. Fewer than three relevant matches returns insufficient context. No padded/truncated/fabricated library or new dependency.
- **Contracts:** explicit planning/existing builders and JSON whitelists, bounded questions, historical 30-day required-occurrence/partial-quantity summaries, validated short fact-code/conditional readings and exactly three distinct admitted strategies. Closed advice/target/schedule/cue/plan/reminder/draft-count actions check modes, amounts/units, quotas/weekdays, reminder minutes, strategy capabilities and catalog provenance. Android strict parsing rejects coercion, unknown/duplicate/missing fields, unsupported types, trailing input and oversized/malformed data. Provider-neutral service and lazy manual-container wiring return only honest unconfigured failure in production. Exact fields/limits are documented in DOMAIN_BEHAVIOR.md.
- **Privacy/scope:** planning adds only schedule quota/weekdays and mode/target/unit required for compatible approved actions to name/aggregate count. Existing requests omit names/IDs/collection/dates/raw logs/all-time stats/cues/full conversation; user-entered question text remains an explicit possible transfer. No service request, credential, fake success, persistence/migration/database/preference identity, source resource, form/result envelope or screen change. Handlers/history/Undo remain chunk 10; UI chunk 11; Gemini setup/terms chunk 12.
- **Checks actually run:** final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.habit.CoachBoundaryRuntimeTest :app:lintDebug` passed **144 JVM and 8 Pixel 7 Android 17 Coach tests, zero failures/errors/skips; lint zero errors/26 existing advisories**. Earlier 12 selected draft/Room restoration/privacy/form regressions also passed. The initial 20-case run had one incorrect expected-rank assertion; correcting the fixture to include supplied card_39 left production ranking unchanged, and all eight affected cases passed twice (including the final reading cap/copy change). Initial AGP provider-source wiring was replaced with the supported generated-assets variant API. Final debug APK/source catalog bytes match exactly; all 60 original card fields checked on-device. Retained package and cold launch (`Status: ok`, `LaunchState: COLD`) verified; git diff --check passed. No owner-data clear/uninstall/Git mutation.
- **Visual/evidence:** inspected source SCR-06/09/FLOW-08 contract text and rendered planning/privacy tables; existing light 393 × 832dp and dark 360 × 640dp/font 1.5 form captures preserve Coach-card/Create composition, keyboard-visible Create access and actual native IME assertions. No new Coach-screen runtime result is claimed. System Poppler handled rendering after bundled Poppler's host-glibc incompatibility. Final standard reports contain successful 144/8 checks; the earlier 20-case report including 12 passing regressions is preserved at app/build/reports/chunk09-verification/first-device. Inspection captures are /tmp/habit-chunk09-qa; device captures retain existing cache/chunk04-qa names.
- **Limits:** lexical applicability is conditional, not semantic certainty or a diagnosis. Forced viewport/font/saved-state checks do not establish all OEM/process-death behavior. Schema/migration was unchanged, so unrelated full device suites were not repeated. There is no unresolved consequential decision or incomplete chunk 09 requirement.
- **Next:** chunk 10, validated persisted/draft handlers, immediate Apply/action-specific feedback, ten-second field-scoped conflict-aware Undo, data-preserving thread/cache/action migration, cap-50 local history and clear/export/delete/wipe attachment. Obtain owner approval first.
- **Suggested one-line commit:** `feat: add supplied Coach strategy retrieval and strict private contracts`


### Chunk 10 — Complete, 7 October 2026

- **Implemented:** additive Room v3/cache/message/action/field-revision stores, registered/exported v2→v3 migration; deterministic persisted target/schedule/quota/cue/anchor/plan/reminder handlers and form-owned unsaved planning handlers, with actual action-specific feedback. Original habit/history tables, database/preference identities, source resources and planning envelope/routes remain. No dependency/provider/network/production fixture.
- **Correctness:** reserve original request/catalog/local field identities before an interaction; strictly validate result/cache/selected-habit binding and each action on Apply. One immediate transaction writes mutation, exact prior changed fields/history row, receipt and message. Pending expectations retain approved effective dates; Undo restores only its own revision/value/absence. Ten-second original deadline, rollback/expiry/midnight guards, repeat/concurrent identity, same-field change-back conflict, unrelated edit/log preservation and failure-safe retry. Draft receipts remain primitive/token-bound through Parcel; count changes only the draft plan; no implicit insert.
- **Data/UI:** per-habit latest-50 messages, original cache context/provenance/time and durable action identity; confirmed Profile history clear with busy/cancel/error/retry, retaining applied settings/history; invalidated Undo/late results on clear and cascades on delete/wipe. Export stays format-v1 with schema-v3/storage-v1 Coach questions/responses/context/receipts and accurate disclosure; Restore remains disabled. Real Coach presentation/detours/dispatch remain later chunks.
- **Checks actually run:** baseline, stepwise assembly/JVM/test-APK checks passed after an initial wrong container enum-name reference was fixed. Final `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.habit.CoachActionsPersistenceTest,com.example.habit.StorageTest,com.example.habit.DataControlsTest,com.example.habit.FormPersistenceTest,com.example.habit.ui.screens.insights.DataControlsUiTest :app:lintDebug` passed **154 JVM and 60 focused Pixel 7 Android 17 tests, zero failures/errors/skips; lint zero errors/26 existing advisories**. Twenty new device cases and ten new JVM cases plus affected storage/form/reminder/data-control regressions cover supported actions/all schedules, pending/historical quantity, exact inverses, same/other-field and change-back conflicts, concurrent taps, restored original deadlines, exact expiry/rollback/midnight, transaction failures/retry, disable/archive/delete/clear/late result, cap/provenance, actual reopen, both migration paths, export/wipe and real Profile controls. The first v2 fixture omitted its existing index; the repair required optional empty-index handling. A grown success-label check required scrolling to the label. Harness defects were corrected and final success supersedes intermediate failures. An earlier expanded 58-check run also passed.
- **Evidence/limits:** inspected supplied SCR-10/15/FLOW-09 and review hierarchy; compared new clear/error dialogs at light reference 393 × 832dp and dark 360 × 640dp/font 1.5. Captures retained in target `cache/chunk10-qa` and `/tmp/habit-chunk10-qa`. System Poppler handled the bundled host-glibc incompatibility. Original catalog/generated bytes and digest match; retained debug installation and cold launch (`Status: ok`, `LaunchState: COLD`) verified; `git diff --check` passed. No owner-data clear/uninstall/Git mutation. Controlled clocks, Parcel and reopen do not prove every OEM or arbitrary process kill during a write. Coach-screen/caller/keyboard/deadline-display integration is chunk 11, live service/privacy configuration chunk 12; no required chunk 10 work is incomplete.
- **Next:** chunk 11, faithful shared Coach screens/states, preserved draft/detail callers and root selected-habit sheet, cached/error/retry/gating/input and Apply/Undo presentation. Production service remains unconfigured; obtain owner approval before starting.
- **Suggested one-line commit:** `feat: add validated Coach actions, scoped Undo and local history`


### Chunk 11 — Complete, 7 October 2026

- **Implemented:** shared planning/suggestions/applied/error Coach presentation and coordinator, original context/reading/three cards/tags, docked input, privacy/attribution dialog, saved conversation/cache and accurate typed Apply/Undo. Empty-Home and form planning return to the same preserved draft with the remaining original Undo deadline, without insertion. Existing Edit/detail routes return to their actual caller, including Progress. Root selection/sheet/tab state is retained; removal requires deliberate new selection.
- **Correctness:** strict original cached context/digest/strategy reconstruction; reserved single interactions, saved interrupted-call/failure/question/cooldown identities, cancellation on disable/navigation/removal, 30-second timeout and bounded exponential Retry-After-aware retry. Local read retry sends nothing; cap-50 thread waits for catalog; clear/removal rejects late saves. Real transaction failures/repeated taps preserve retryable state. No schema/database/preference identity, planning token/result envelope, source resource, dependency, provider/credential/INTERNET permission or live Coach traffic change. ACCESS_NETWORK_STATE performs only local inspection.
- **Verification:** final assembly and all **156 JVM tests** passed; lint **zero errors/26 existing advisories**. The expanded **107 Pixel 7 Android 17** checks passed 106, including all **29 new Coach checks**. One early native Back helper closed the test activity before modal focus. After waiting for focused windows, the final **12/12 device checks** passed (all ten detail regressions plus final Undo chip/form tests), providing passing evidence for every selected case across these runs. Do not label the expanded run itself zero-failure. Earlier IME/Done/publication harness issues and nullable receipt-flow restoration default were fixed and retested. Exact commands/results/report paths are in `HANDOFF.md`.
- **Visual/data evidence:** inspected supplied SCR-06/09/10/11/FLOW-08/09 and reference mockups against light 393 × 832dp and dark 360 × 640dp/font 1.5 planning/cards/applied/conversation/offline/source and returned-form Undo captures. Native IME visibility and input/Send/Create access are asserted, with keyboard pixels outside Compose PNGs. Captures: target `cache/chunk11-qa`, `/tmp/habit-chunk11-qa/chunk11-qa`. Catalog source/final APK bytes and SHA-256 remain identical; APK has no INTERNET permission. Retained package and cold launch/process verified; `git diff --check` passed. No real-data clear/uninstall/Git mutation.
- **Limits/next:** controlled clocks/Parcel/forced viewport do not establish every OEM or arbitrary process kill during a write. No required chunk 11 work remains incomplete. Production service remains honestly unconfigured. Chunk 12 requires owner authorisation, exact Gemini Flash configuration/safe credentials/applicable free-tier privacy acceptance and transport/live verification; chunk 13 remains final integration/design verification.

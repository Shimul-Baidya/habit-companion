# Habit Companion project instructions

These instructions apply throughout this project. Read this file before working and follow any later explicit owner instruction. The project is an academic Android Basics with Compose application for CSE-410. The final public name is **Habit Companion**; HabitFlow and Habit Flow are former names.

## Current phase and continuity

The current request authorises project instructions and implementation planning only. Do not implement app changes until the owner requests implementation. A later implementation request supersedes this phase restriction; do not request the same authorisation again.

Read `IMPLEMENTATION_PLAN.md` for chunk order, dependencies, acceptance criteria and progress. Read `HABIT_COMPANION_REQUIREMENTS_REVIEW.md` for source analysis and screen details, recognising that its earlier proposals are subordinate to the final decisions here. Keep this file for standing rules and the plan for execution status; do not duplicate the whole review in every handoff.

## Requirements hierarchy

1. Explicit owner decisions, including the approved decisions recorded here.
2. Original project purpose and five core features.
3. Detailed screen specification for behaviour and visual details.
4. UI-flow specification for navigation, transitions and Back behaviour.
5. Supplied mockup composition wherever it does not contradict an explicit requirement.
6. Existing code as evidence of progress, never as permission to change the plan.

Authoritative resources:

- `resources/latest_updated_abstract_theme_habitflow.docx`: purpose and academic scope.
- `resources/HabitFlow_Design (2).pdf`: system design and visual composition.
- `resources/HabitFlow_Screen_Details.pdf`: SCR-01–SCR-15, foundations and components.
- `resources/HabitFlow_UI_Flow.pdf`: FLOW-01–FLOW-14.
- `HabitFlow_Screen_Details.pptx`: editable companion to the screen specification.
- `resources/coach_cards.json`: supplied Coach strategy library. At planning time it contains 60 cards, not 50. Use the supplied collection rather than an assumed count.

Preserve original resource files and names for traceability. Do not edit the supplied strategy library to fit an implementation assumption. Inspect updated resources before the work that depends on them.

## Scope and autonomy

Implement the existing plan faithfully. Preserve the screen composition, navigation structure, visual language and all five core features: habit management, daily tracking, streaks, Progress dashboard and AI Coach. Do not redesign, remove features, substitute a generic interface or introduce unrelated functionality.

Resolve minor inconsistencies autonomously using the hierarchy, internal consistency and minimum deviation. This includes shades, spacing, icons, typography adjustments, wording, accessibility implementation, loading/error presentation, small animation differences and responsive accommodations. Do not ask the owner repeatedly about cosmetic details or already approved decisions.

Ask only when an unresolved decision materially affects product behaviour, stored data/schema, privacy or outbound Coach data, major scope, irreversible user data, or a genuinely ambiguous policy that cannot be resolved logically. Present a concrete proposal with its impact before asking. A routine migration needed to implement an approved feature does not itself require another product approval; preserve data and validate it.

Do not infer approval of old review proposals when they conflict with this file. In particular, Weekly is a quota, quantity tracking is in scope, immediate Apply with ten-second Undo is approved, and the strategy resource is already supplied. Do not reopen those decisions.

## Approved habit model and behaviour

### Frequency and history

- **Daily:** scheduled every day.
- **Weekly:** user chooses a number of completions within each week. It is not a single chosen weekday or a Custom weekday mask. Represent the weekly quota clearly in both UI and data.
- **Custom:** user selects specific weekdays.
- Use effective-date/history information for schedule, target and other changes that affect interpretation of past records. Editing today's settings must not reinterpret previous dates.
- Resolve weekly occurrence allocation, deadlines, partial weeks and metric units coherently before freezing the history model. Do not invent missed weekdays for a flexible weekly quota or silently count all unchosen days as misses. The plan records this consequential policy checkpoint.
- Changing the displayed week-start preference must not silently repartition already closed historical quota periods.

### Tracking and goals

- Support **binary** and **quantity-based** tracking in New/Edit Habit. Binary is the recommended/default v1 choice, not the only supported mode.
- Binary completion is one done/not-done result for an eligible scheduled day.
- Quantity completion records an amount against a typed target and unit appropriate to the habit. Use a compact amount input consistent with the design, not an unnecessarily complicated multi-tap counter.
- Keep amount, partial progress and achieved completion distinct. Do not count any positive quantity automatically as target completion.
- Target changes affect future expectations without deleting or invalidating earlier completions. Use historical targets when evaluating past quantity records.
- Both tracking modes must work across history, streaks, risk, statistics, editing, corrections and Coach actions. Preserve valid records when changing modes/units; do not guess unit conversions or reinterpret old records.

### Eligibility, streaks, risk and statistics

- No future completion or completion before creation. Non-scheduled dates are not misses and must not expose misleading completion actions.
- Today's unfinished scheduled habit remains pending while the day is open. Flexible weekly quotas remain pending until their applicable deadline; do not turn flexibility into daily failure.
- New habits with no history are neutral. Do not fabricate a broken state or zero streak because a habit is at risk.
- Risk starts at **at least two misses among the last seven scheduled occurrences**. It clears after **two consecutive completed scheduled occurrences**. Non-scheduled days count toward neither misses nor recovery. Replay the occurrence history deterministically after corrections; stale earlier misses must not immediately cancel a valid recovery without a new triggering outcome.
- Risk and current streak are separate. A positive current streak may coexist with an attention state.
- Best streak is all-time. Remove artificial 30/400-day calculation limits where they cause incorrect results. A separate 30-day Coach summary is allowed and does not limit local statistics.
- Consistency uses eligible scheduled habit-days/required occurrences, not an average of per-habit percentages. Define weekly quota weighting explicitly so flexible days are not all treated as required days. Handle zero denominators and partial periods honestly.
- Recompute dependent values together after history corrections. Preserve historical schedule/target interpretation and use a controllable clock/date source for calculations and tests.
- Refresh date-dependent state at midnight, on resume, and after applicable time/date/time-zone changes. Do not rely solely on a composable's initial effect.

### Management and reminders

- Archive removes habits from active Home/top-streak views and preserves their historical contribution where required. Provide the specified five-second archive Undo.
- Delete requires the specified confirmation and removes dependent records. Historical totals may change because data was actually deleted. A deleted habit must not leave a broken detail/Coach destination.
- Past-date correction must respect eligibility, historical mode/target and both tracking modes.
- Preserve global daily reminder/time and per-habit Change reminder interactions. Cancel/reschedule correctly after changes, completion where appropriate, archive and delete. Handle notification availability honestly.
- Export and destructive data clearing remain in scope. Restore/import stays disabled in v1 as specified; do not remove export because restore is deferred.

## Coach strategy, actions and privacy

### Supplied strategies

Use the actual supplied `resources/coach_cards.json`. Each card currently has `id`, `title`, `principle`, `action`, `use_when`, `tags` and `source`. Preserve IDs, conditions and attribution. `action` is explanatory prose, not an executable command or a prevalidated mutation.

Parse/validate the resource, retrieve/rank locally, and use matched cards as context for the Coach. Validate returned strategy IDs against the supplied catalog and the strategies admitted to the request. Do not fabricate a replacement library, truncate to 50 arbitrarily, or select irrelevant cards solely to fill three slots. Supplied book references are attribution, not independently verified scientific claims.

### Typed Apply and Undo

- Define an explicit closed set of supported, validated actions with deterministic local handlers. Examples include target changes, cue/anchor changes, schedule/frequency changes, reminder configuration and a planning recommendation about how many new habits to start.
- Never parse arbitrary model prose directly into database writes. Reject unsupported action types, invalid parameters, unknown strategy IDs and actions incompatible with the mode or target habit.
- Informational advice may become a defined local plan/cue setting if appropriate. Do not invent integrations or unrelated features so every strategy can perform an external action.
- A recommendation about new-habit count must not silently create/delete/archive other habits, or transmit the collection. Apply stays within the permitted draft/setting scope.
- Show confirmation describing the actual action; do not say “Goal changed” for a reminder, cue or schedule change.
- Apply immediately and retain the specific prior state required to undo. Expose **Undo for ten seconds**, then expire it. There is no deferred commit model.
- Undo reverses only its own change and must not overwrite unrelated later edits or completions. Use field/action-level change identity and conflict checks, with explicit handling of later edits to the same field.
- Preserve correctness across repeated taps, recreation, navigation and expiry. Existing-habit actions persist locally; planning actions update the unsaved draft and never insert a habit implicitly.

### Modes and outbound payload

- **Planning:** operate on an unsaved draft. The original planning contract permits draft name, frequency and aggregate current habit count, plus the question and retrieved strategy context needed for the interaction. Include additional mode/target information only when necessary for the approved behaviour and explicitly accounted for in the minimal payload contract.
- Empty Home → planning Coach → Apply → existing New/Edit Habit form with updated draft. Manage the draft within the creation flow; do not add a separate permanent planning screen or create a habit on Back/Cancel.
- Planning opened from a form returns to that same preserved form. Ordinary Back applies nothing.
- **Existing habit:** only the permitted one-habit measured summary, current question and relevant strategy context. Respect the detailed specification's exclusion of habit name and identifiers from this mode's outbound payload.
- Do not transmit the entire habit collection, unnecessary database IDs, unrelated personal data or the full local conversation by default. Local route IDs are not network fields.
- Interpret measured patterns conditionally. The model cannot know life events, motives, emotions or context that was never supplied.
- User-entered follow-up questions may be transmitted; explain this accurately. Do not claim absolutely no information ever leaves the device.
- Coach enablement gates every entry, including cards/buttons and the Coach tab, and request dispatch. Preserve the four-tab structure; unavailable Coach must explain how to enable it. Handle setting changes during an in-flight request.
- Local retrieval precedes the model call. Use one request per intended interaction; no background Coach traffic or network calls merely to render local screens.
- Provider, endpoint/model, authentication and service privacy details are not yet supplied. Keep the service boundary explicit; never invent credentials, silently pick a provider or put a shared secret into the app. Test responses belong in tests/previews, not a production pretend-Coach.

### Coach states and persistence

Preserve planning, suggestions, applied/conversation and unavailable/error states, initial three-suggestion presentation, strategy tags, docked input and short pattern reading. Handle insufficient history, loading, offline, timeout, rate limit, malformed response and server error. Retry stays in place with the documented cooldown/backoff. Failure copy explains that local habits/history remain available.

Persist local Coach threads per habit, capped at 50 messages as specified, with local suggestion caching and Clear coach history. Cache must retain provenance/context and must not masquerade as a fresh response. Applied/error variants do not add navigation entries. Exact local data storage details may extend Room with tested migrations.

## Local-only v1

Habits, completions, settings, drafts where retained, and local Coach history stay on the device. No accounts/sign-in, cloud synchronisation or online database for user habit data. Later deployment does not authorise adding cloud architecture.

The external AI service is the specified exception and receives only the defined payload. Explicit user export is another deliberate data transfer. Inspect and configure manifest and Android backup/extraction rules so automatic backup does not silently contradict this model. Do not leave template backup rules and claim local-only privacy has been verified. Verify platform-specific limits and describe any unavoidable limitations accurately.

## UI fidelity and navigation

- Preserve Habit Companion branding, muted green/teal palette, supplied light/dark tokens, typography hierarchy, gutters, spacing/radii, component foundations and screen-specific exceptions.
- Preserve coloured habit icon → name/helper → streak → separate completion control in mockup rows, plus documented attention grouping and summary hierarchy.
- Reference viewport is 393 × 832dp, not a fixed canvas. Adapt only as needed for smaller screens, larger text, keyboard and system insets. Maintain 48dp non-overlapping interaction areas around smaller visuals. Allow content to grow/scroll rather than clip essential text.
- Do not draw phone frames, notches, annotation numbers or sample system-bar content.
- Preserve the four tabs in order: Home, Progress, Coach, Profile. Keep local empty/loading/error/no-due-today/all-done states distinct.
- SCR-03/04 share Home; SCR-07/08 share detail; SCR-09/10/11 are Coach states; SCR-12/13 share Progress; SCR-14 is a modal. New/Edit shares the form. Do not make a separate permanent screen for each state.
- Splash is removed after routing. Persist onboarding completion before leaving; completed onboarding never returns through Back.
- Detail returns to its actual caller, including Progress. Coach returns to its actual caller/draft context. Applied/error states do not stack destinations.
- Sheets/dialogs consume Back before underlying navigation. Preserve dirty-form discard confirmation and draft state across recreation and Coach detours.
- Tab switching retains state without duplicate root entries. Use Home/the main graph as the retained anchor, not removed Splash.
- Use the documented transitions where practical: sibling tabs fade 150ms; Splash fade 200ms; creation/form steps slide 250ms; detail shared-element/container transition 300ms; Coach entry slides upward; error crossfade 200ms; ring 300ms; charts 400ms with 40ms stagger; sheet 250ms. Document a narrow fallback if a platform/API limitation prevents the intended transition.
- Month charts cover the actual 4–6 calendar-week buckets without losing dates, following the week-start display setting. Do not force every calendar month into five inaccurate bars.

## Architecture and engineering

Extend the existing Kotlin/Compose single-module architecture and manual `AppContainer` unless a concrete requirement necessitates change. UI presents state; ViewModels coordinate screen state; repositories/data sources persist; pure domain logic calculates eligibility/statistics; explicit Coach models define payload/actions.

Before relevant edits, inspect entities/DAOs/repositories, DataStore, navigation, reusable components and supplied resources. Use Room for relational habit/history/Coach data and DataStore for preferences. Retain the database identity/file and preference keys where changing them would lose existing data merely for branding.

Use data-preserving versioned Room migrations and exported schemas. Never use destructive migration fallback to hide missing migrations. Handle legacy records explicitly and do not guess consequential legacy meanings if real data is ambiguous.

Use save loading/error state and repeated-submit guards. Use saved draft/state appropriate to recreation; do not rely on transient `remember` alone. Distinguish database failure from an empty result, retain input on failure, and do not claim successful writes prematurely.

No hardcoded sample habits or production fake data. Add dependencies only when actually needed; explain the reason. Do not gratuitously rewrite working code, upgrade the stack or add a DI framework. Keep external credentials and sensitive user payloads out of committed files and logs.

## Verification and chunk handoff

Use the per-chunk checks in `IMPLEMENTATION_PLAN.md`. Compilation alone is insufficient. Add meaningful domain, repository/migration, navigation/state and Coach contract tests alongside the affected implementation. Verify visuals with the supplied sources rather than relying solely on code review or screenshots from previews.

Cover binary/quantity behaviour; Daily/Weekly/Custom schedules; historical edits; current/all-time best streaks; risk and two-occurrence recovery; partial/zero data; midnight/date changes; archived/deleted records; quantity targets/units; repeated taps; recreation; tab/caller Back; settings/theme; reminders; export/clear; strategy retrieval; payload validation; Apply/Undo; Coach disabled and network failures; spoken labels, keyboard/insets and touch targets.

Run compilation and affected tests for each implementation chunk. Run relevant instrumentation/device checks when the environment supports them; if it does not, record exactly what remains unverified. Final integration also includes lint and a full flow/visual check. Never describe static inspection, a mock response or an unexecuted test as a verified runtime result.

At each chunk boundary, update the plan's status and concise handoff: changes, checks actually run/results, unresolved issues or limitations, and the next chunk. Keep incomplete work marked incomplete. Later chats should resume from this record, not rediscover the project or repeat completed checks without a reason.

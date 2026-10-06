# Habit Companion requirements and design review

Reviewed on 6 October 2026 for the Android Basics with Compose academic project, CSE-410.

**Later owner decisions supersede this initial review's proposals.** Read `AGENTS.md` for the locked requirements and `IMPLEMENTATION_PLAN.md` for execution chunks. Weekly now means a completion quota within each week; binary and quantity tracking are both approved; risk/recovery and immediate Apply with ten-second Undo are approved. The supplied strategy library is now present at `resources/coach_cards.json` with 60 cards. References below to a missing library or unresolved approved choices describe the earlier review only.

## 1. Project direction and standing instruction

**The final application name is Habit Companion.** HabitFlow and Habit Flow are earlier names. The project directory may remain `Habit`; the directory and package identifiers do not need to match the public name.

The intended app is a personal habit tracker with local storage, daily check-offs, streaks, progress statistics, and an AI Coach that suggests concrete adjustments grounded in a curated habit-strategy library. Preserve the initial plan, especially its screen composition and navigation. Change something only to resolve an actual contradiction, prevent incorrect behaviour, or make a specified feature work. Do not use this review as permission to redesign the app, remove the Coach, add unrelated features, or replace the visual style.

This file records the requirements and future working constraints so they remain available in the project. It is a review, not an implementation change. **Recommendations and unresolved choices below are not decisions already approved by the owner.** The original resources and application source have not been changed by this review.

## 2. Sources and review coverage

The directory is actually named lowercase `resources`.

| Reference | File | What it establishes |
|---|---|---|
| A | [Project abstract](resources/latest_updated_abstract_theme_habitflow.docx) | Academic purpose, Lifestyle theme, five core features, behavioural-strategy grounding |
| D | [System and visual design](resources/HabitFlow_Design%20%282%29.pdf), 4 pages | Architecture, main mockups, product principles and broad flow |
| S | [Screen details](resources/HabitFlow_Screen_Details.pdf), 23 pages | SCR-01 through SCR-15, design tokens, reusable components, measurements, states and behaviour |
| F | [UI flow](resources/HabitFlow_UI_Flow.pdf), 17 pages | FLOW-01 through FLOW-14, routes, arguments, transitions and Back behaviour |
| P | [Screen-details presentation](HabitFlow_Screen_Details.pptx), 23 slides | Editable companion containing the same screen IDs, foundations and specifications |

References such as `S p.15` and `F FLOW-07` below identify exact locations. The PDFs were read as text and visually inspected, including their phone mockups; the DOCX text and PPTX slide text were inspected. The PowerPoint was not independently rendered in PowerPoint. No separate course rubric, backend specification, strategy-card content, or AGENTS.md was found in the inspected project tree.

The directory review covered app source, reusable components, themes, resources, navigation, ViewModels, data/domain code, Room schema, manifest, backup configuration, build configuration and existing tests. Generated build files, caches, wrappers and IDE files were inventoried as supporting files, not treated as design requirements. No build or device test was run for this documentation-only task. Existing build outputs do not prove the current app is complete. This directory is not currently recognised as a Git repository.

### How to resolve source disagreements

1. The owner's explicit instructions, including the final name and minimal-deviation rule, take priority.
2. Preserve the five core features from A and the product purpose from D.
3. Use S for detailed screen behaviour and F for navigation. A specific per-screen rule normally refines a general foundation rule.
4. Preserve the mockups' composition wherever they do not contradict an explicit specification. Record visual conflicts rather than quietly substituting a generic Compose layout.
5. Existing code shows implementation progress; it does not supersede the plan.

The resources have no reliable revision hierarchy proving that every table overrides every picture. The proposed conflict resolutions in section 7 are therefore explicit and reviewable.

## 3. Scope to preserve

### Core features

- **Habit management:** create and edit name, icon/colour and frequency; archive while retaining history; delete with confirmation.
- **Daily tracking:** a separate completion control on each Home row; one tap records a scheduled day's completion and a second tap can undo it. Past-date corrections belong in the detail calendar.
- **Streaks:** current and best streaks, a recent-miss pattern, a clear needs-attention state and a recovery path. Statistics and pattern detection are deterministic local calculations.
- **Progress:** Week and Month views, consistency, completion totals, all-time best streak and tappable top streaks.
- **AI Coach:** planning before creation and assistance for an existing habit, exactly three initial suggestions, visible strategy tags, application of concrete changes, Undo, follow-up conversation and designed error states.

### Supporting features already in the plan

Three-pane onboarding; light, dark and system theme; local editable profile name; reminders; configurable week start; Coach enable/disable and history clearing; data export and destructive data clearing. Archive and delete are distinct operations. Restore/import is explicitly phase 2 and disabled in v1 on onboarding (S p.8); export is mentioned separately in S p.23 and is not explicitly deferred.

There is no account or sign-in requirement. No cloud habit synchronisation, social feed, leaderboard, subscriptions, widgets or additional dashboard is specified. Do not introduce them as part of completing the original scope. Sample names, dates and numerical values in the mockups are examples, not data to seed into a user's app.

### Product principles

Keep habits visible on Home, make logging low friction, show consistency through streaks and a calendar, and help users recover when a pattern slips. The app should describe what happened without inventing motives or treating a missed habit as a personal failure. The academic abstract cites inspiration such as Atomic Habits and Feel Good Productivity; it does not supply the actual strategy library or supporting references.

## 4. Visual foundations

### Colour tokens from S p.3

| Token | Light | Dark |
|---|---|---|
| surface | #F3F7F5 | #0F1A1C |
| surface.card | #FFFFFF | #162427 |
| surface.sunken | #E7EEEB | #0A1416 |
| on.surface | #0D1B1E | #ECF3F1 |
| on.surface.muted | #5C7075 | #9DB0B3 |
| on.surface.faint | #8A9B9E | #6E8285 |
| primary | #0F766E | #2DD4BF |
| on.primary | #FFFFFF | #04201E |
| primary.container | #D7EBE5 | #123A38 |
| on.primary.container | #0B5A54 | #A7E8DF |
| danger | #C2410C | #F87171 |
| danger.container | #FBEBE8 | #33201E |
| outline | #E5ECE9 | #24363A |
| scrim | #0D1B1E | #000000 |

Keep the muted green backgrounds, teal primary colour, calm cards and restrained danger treatment. The supplied dark palette is separate, not a simple inversion. Habit icon swatches and some orange accents in the pictures are not fully covered by this token table; see V-01 below.

### Type and layout from S pp.4–5

Roboto/default system typeface, no bundled font. Roles are display 32/38 Bold, headline 26/32 Bold, title.lg 22/28 Bold, title 20/26 Bold, stat 32/36 Bold, body 16/22 Regular, caption 14/19 Regular, label 12/16 Bold and micro 11/14 Bold. Values are size/line height in sp. Section labels use uppercase and 1.5sp tracking. Screen-specific overrides include 24sp body line height and 24sp Profile statistics.

Reference viewport: 393 × 832dp. Horizontal gutters: 20dp. Spacing scale: 4, 8, 12, 16, 20, 24, 32dp. Standard radii: 8, 12, 16dp; sheets 24dp at the top; pills 50%. Flat cards have 1dp outlines; sheet elevation 8dp, dialog 12dp. Minimum interaction target: 48 × 48dp. Inline icons 20dp, navigation/app-bar icons 24dp, empty-state icons 44dp unless overridden.

The viewport is a fidelity reference, not a fixed-size canvas. Insets, the keyboard, smaller displays and larger text must not hide controls or content. Do not stretch every measurement proportionally to the device dimensions.

| ID | Shared component | Required baseline |
|---|---|---|
| C-01 | Habit row | 72dp height, 16dp radius/padding, card background, 1dp outline |
| C-02 | Completion ring | 40dp circle, 4dp stroke, teal on sunken track, 300ms fill; 48dp touch area |
| C-03 | Streak chip | 16dp flame, 20sp count; broken treatment for an actual broken streak |
| C-04 | Stat trio | Equal thirds, 32sp values, 12sp labels; 24dp surrounding vertical spacing |
| C-05 | Month grid | Seven columns, 28dp dots, 8dp gap, done fill, sunken misses, 2dp outline for today |
| C-06 | Suggestion card | 16dp radius, 20sp title, supporting caption, Apply at right, strategy tag beneath |
| C-07 | Apply pill | 78 × 32dp visible pill, primary.container, 11sp label |
| C-08 | Primary button | 56dp height, full content width, pill shape, primary fill, 20sp label |
| C-09 | Bottom navigation | 72dp, order Home / Progress / Coach / Profile, selected primary |
| C-10 | Section label | 12sp uppercase, 1.5sp tracking, muted, 12dp after preceding block |
| C-11 | Coach input | 44dp field and send control, docked, keyboard-aware |
| C-12 | Bottom sheet | 24dp top corners, 8dp elevation, 32dp handle, 55% scrim |

Preserve the visible small controls while accommodating the minimum touch area; calendar/day-picker geometry needs the specific correction in section 7. Do not draw the mockups' phone frames, notches, numbered annotation badges or sample status-bar time as application content.

## 5. Screen-by-screen requirements

Fifteen IDs describe screens **and states**, not fifteen independent navigation destinations.

### SCR-01 — Splash (S p.7)

Always-dark branded entry with light system icons, centred mark and wordmark **Habit Companion**, tagline and delayed loading bar. Mark: 192dp circle, 5dp primary ring, 24dp check, scale 0.92 to 1 over 220ms. Wordmark 32sp Bold; tagline 14sp at 70% alpha. Loading indicator 80 × 4dp, hidden when data is ready before 400ms. Minimum display 600ms and maximum wait 3000ms.

Read onboarding status and open/read local storage. First run goes to onboarding; otherwise Home renders from real data. Remove Splash from the back stack. Loading, migration and database-failure handling are required. On failure, leave Splash and present Home with a clear retry/error state; never discard existing records to manufacture an empty Home.

### SCR-02 — Onboarding (S p.8)

Exactly three swipeable panes. Preserve Skip at the top, stacked-card illustration, headline, short supporting text, pager dots, Continue and disabled Restore. Illustration cards are offset 60dp with alpha 0.35/0.6/1; top card animates for 400ms. Dots are 8dp with a 28 × 8dp active pill and 10dp gaps.

Continue advances; final Continue and Skip persist completion before opening Home. Back on panes 2/3 goes to the previous pane; pane 1 exits. Do not re-open Splash. Restore is unavailable in v1. The resources show the first pane most clearly; complete copy for all three panes is not specified there. Current code supplies candidate copy, which still needs branding and privacy corrections.

### SCR-03 — Home empty (S p.9)

Shared Home destination. Time-based greeting and local name at left, 72 × 64dp date chip at right. Empty card with 20dp corners and 32dp padding; 88dp decorative icon circle, 36dp plus; “Add your first habit” primary action. Below it, preserve the Coach shortcut card, 88dp high, 16dp corners and 44dp leading circle.

Home is selected. Progress and Coach tabs remain visible but unavailable, with an explanation; Profile remains usable. The separate planning shortcut is deliberately available while the contextual Coach tab is unavailable. Use a scrolling Column where necessary. Loading must not flash a genuine no-habits message. The first insert crossfades to populated Home in 200ms. Zero active habits after archive/delete also requires a coherent empty state.

### SCR-04 — Home today (S pp.10–11; D p.2)

Preserve greeting/date, daily summary card with ring and remaining/done text, current-streak summary, “Today's habits” heading and done/total counter, habit list, Add FAB and bottom navigation. Mockup rows contain a coloured habit icon, name, goal/helper line, streak and a separate completion circle. Keep that visual hierarchy; the existing left-ring implementation is not evidence that the plan changed.

Summary ring: 64dp, 6dp stroke. Rows: C-01 with 8dp between them. Row tap opens detail; completion tap toggles locally without navigating, animates 300ms and gives completion haptics. Long press opens SCR-14. Group at-risk habits beneath “Needs attention” as explicitly specified, even though some pictures show a per-row subtitle instead. FAB: 56dp, primary, 2dp elevation, 16dp clearance from edges.

All active habits remain discoverable on Home. Daily totals must be based on those scheduled today. Preserve loading, empty, populated and all-done states. The precise all-done celebration is not specified; do not invent an elaborate animation. A day with no scheduled habits is not the same as having no habits.

### SCR-05 — New habit and edit mode (S p.12; SCR-14 exit)

56dp app bar with Back and 26sp title. Form order: NAME label; 60dp name field with 12dp corners and 2dp focused border; ICON & COLOUR; 56dp swatches with 8dp gaps and selection ring; HOW OFTEN; 52dp Daily / Weekly / Custom segmented control; weekday controls; Coach card; docked Create.

Coach card stays directly above Create: 116dp high, 16dp corners, primary.container and 48dp leading circle. Create is 56dp with 20dp gutters and 24dp clearance above the inset. The form scrolls and respects the keyboard while the primary action stays reachable. New forms use the first swatch by default. Preserve unsaved form state during a Coach detour. Dirty Back shows discard confirmation.

Name is required. Handle empty, duplicate-name and invalid custom-day states. Save one real habit, then return to Home through a pop. Editing reuses this form, loads the selected habit and updates it rather than inserting another row; exact edit route/title/action copy are gaps to fill narrowly. Schedule meanings and duplicate-name policy are not fully specified; see section 7.

### SCR-06 — Coach planning (S p.13)

56dp Coach app bar; read-only habit/draft chip with 4dp teal leading bar; “BEFORE YOU COMMIT” context; short model reading; “Three ways to start”; exactly three suggestion cards, Apply pills, strategy tags and supporting lines; docked input/send control.

Reading uses 16sp text, 24sp line height, maximum four lines at the reference size, with loading shimmer. Cards have 16dp corners, 1dp outline and 12dp separation. Apply modifies the unsaved draft and returns to the existing creation form. Ordinary Back applies nothing. No habit row exists yet. Initial request scope is draft name, frequency and current habit count, plus retrieved strategy material needed by the Coach. Follow-up questions require an explicit payload rule too.

Required states: loading, loaded, application, offline and rate limited. The empty-Home shortcut lacks both a populated draft and a form to return to; preserve the shortcut but resolve that missing contract before implementation.

### SCR-07 — Healthy habit detail (S p.14)

Collapsing app bar with Back and management action; title/frequency block; current, best and monthly rate; positive callout; month header and completed/scheduled ratio; seven-column calendar; legend; docked outlined “Ask the Coach”. Current streak uses primary; best/rate use normal text.

Show the 76dp positive callout only when current equals best and best is at least seven. Calendar supports 4–6 rows and month swiping. Past days can be corrected; future days are disabled. Completion history and stats update together. The new-habit/no-history state must not imply a broken streak. Entry may be Home or Progress, so Back returns to the actual caller.

### SCR-08 — At-risk habit detail (S p.15)

Same route and structure as SCR-07. Preserve best and rate, neutral current-streak text, danger-container pattern callout, muted missed days and a filled primary “Get a plan” action. The callout comes from local calculations, not the model. It must describe actual history.

The stated trigger is at least two misses in the last seven scheduled days; the footer also says recovered after two clean days. Those rules need reconciliation. A risk flag does not necessarily mean the current streak is zero. Preserve the intended emphasis without displaying a fabricated `0d`.

### SCR-09 — Coach suggestions (S pp.16–17)

Coach app bar, selected-habit chip, streak/rate status, “WHAT I'M SEEING”, a short reading, “Three things to try”, exactly three cards and docked follow-up input. Each card contains an actionable title, Apply, a one-sentence reason and the retrieved strategy tag. Whole-card tap and Apply must trigger the same action once.

Match the one habit's recent 30-day pattern against approximately 50 local strategy cards, retrieve the top three, and use them in one model request per interaction. Open with an interpretation of the pattern rather than a blank chat. Disable blank sends and sending during a request. Loading uses a three-line shimmer; handle insufficient history without inventing a pattern.

For an existing habit, send only the permitted summary/question and strategy context, not the entire habit collection or database identifiers. Apply changes local habit settings and enters SCR-10 in place. Back returns to the caller, which observes updated data.

### SCR-10 — Coach applied (S p.18)

Same Coach destination. Habit sub-line reflects the actual change. Show a 104dp confirmation card with 16dp corners, primary.container, 36dp check and a ten-second Undo action. The applied card animates over 250ms and the thread scrolls to it.

Follow-up conversation remains open. User bubbles are right-aligned, primary, up to 76% width; Coach replies left-aligned, card background and 1dp outline. Both use 16dp corners with 12dp spacing. Persist up to 50 messages per habit and allow clearing from Profile. Preserve completions when applying a smaller goal. See C-06 for the contradictory save/Undo wording and C-07 for actions that are not numerical goal changes.

### SCR-11 — Coach unavailable (S p.19)

Error state of the existing Coach, not a new route. Keep context chip; display 148dp decorative circle and 56dp icon, centred headline, up to three lines of explanation, 52dp outlined Retry and optional 72dp cached-suggestion note. Distinguish no connection, timeout, rate limit and server failure through copy.

Always explain that local habits and streaks are unaffected by the Coach failure. Do not show a spinner before a request when already known offline. Retry stays in place, is disabled for at least three seconds after a tap and follows backoff. A connectivity precheck cannot replace handling failures during the call. Back returns to the originating draft/detail/tab context. Cached content must correspond to that habit and must not be presented as a new online response.

### SCR-12 — Progress week (S p.20)

Navigation root with no app-bar Back. Title and Week/Month segmented control (116 × 36dp visible control); equal-width consistency/done/best statistics; daily activity chart; Top streaks with proportional rails; bottom navigation selected on Progress.

Seven daily bars, 24dp wide, 8dp corners, 400ms rise with 40ms stagger. Day labels are 14sp; today is bold. Week start follows settings. Best is all-time, not limited to the displayed week. Top-streak rows are 48dp, 12dp corners, 1dp outline and 8dp separation; selecting one opens its detail. Hide that list if no habit has a streak. Support partial history and no-data states.

### SCR-13 — Progress month (S p.21)

The same composable with Month selected; no new back-stack entry. Monthly consistency and completion total, unchanged all-time best meaning, “Weekly consistency” chart, top streaks and bottom navigation. Source specifies five 34dp bars with 8dp corners, lighter primary below 75%, and value labels omitted below 10%. Support partial and empty months. The five-bar assumption needs correction for calendar months spanning four or six week rows.

### SCR-14 — Edit and delete sheet (S p.22)

Modal sheet over Home or detail, not a new full-screen destination. Preserve habit header and summary, Edit habit, **Change reminder** (visible in the mockup), Archive habit, Delete habit and Cancel. Rows are 72dp with 44dp leading circles. Delete has danger treatment and stays visually separated.

Sheet: 24dp top corners, 8dp elevation, 32 × 4dp handle, 12dp top spacing and 55% scrim. Back, outside tap, drag down and Cancel dismiss. If a confirmation dialog is open, Back first dismisses that dialog. Archive sets `archivedAt`, retains history and offers five-second Undo. Delete names the habit/streak being lost, clearly states irreversibility and removes dependent completions. Editing opens SCR-05 in edit mode. A deleted detail must not remain as a broken destination.

### SCR-15 — Profile and settings (S p.23)

Fourth navigation root. Local initials avatar, editable name, habit-count/joined summary and lifetime best/completed/consistency values. Avatar is specified as 100dp despite appearing smaller in the mockup. Lifetime values use 24sp. Preserve Preferences, Coach and Data groups and 56dp settings rows.

Preferences include daily reminder/time, theme and week-start choice. Theme opens Light / Dark / Follow system options and applies immediately. Coach controls include suggestions enablement and Clear coach history. Data includes export via the system file picker and destructive clearing with type-to-confirm. The bottom of the Data group is cropped in the supplied mockup; do not infer unsupported controls from the crop.

Preferences save on change with no Save button. Counts come from Room. Disabling Coach must remove or disable every route into it, including the bottom tab, and prevent further requests. This is a local profile, not an online account. Error and pending-write states must avoid claiming a setting was saved if the write failed.

## 6. Navigation contract

| Flow | Trigger and destination | Back/state contract | Motion |
|---|---|---|---|
| FLOW-01 | Launch → `onboarding` or `home` after settings/storage readiness | Remove Splash; Back cannot return to it | Fade 200ms |
| FLOW-02 | Skip or final Continue → `home` | Persist onboarding; remove onboarding; Home exits on Back | Right-in 250ms |
| FLOW-03 | Empty primary action or Home FAB → `habit/new` | Blank draft; dirty Back confirmation, then return to Home | Right-in 250ms |
| FLOW-04 | New-habit Coach card → planning Coach | Draft context; Apply returns a result into the same form; ordinary Back leaves draft unchanged | Up 250ms |
| FLOW-05 | Create → existing Home | Insert once, then pop; no refresh route needed | Right-out 250ms; first insert crossfade 200ms |
| FLOW-06 | Healthy habit row → `habit/{habitId}` | Pass Long ID; query local data; Back returns to caller | Name shared element/container transform 300ms |
| FLOW-07 | At-risk row → same detail route | Same destination; data selects SCR-08 | Same as FLOW-06 |
| FLOW-08 | Detail Coach action → `coach/{habitId}` | ID is a local navigation argument; rederive one-habit summary; Back returns to detail | Up 300ms |
| FLOW-09 | Apply → SCR-10 | Local change and Undo; no navigation | Card transition 250ms |
| FLOW-10 | Request error → SCR-11 | No navigation; Retry in place; keep caller/context | Crossfade 200ms |
| FLOW-11 | Overflow or row long press → SCR-14 | Modal state; dismiss consumes Back | Sheet/scrim 250ms |
| FLOW-12 | Bottom tab → `progress` | Preserve each tab's state, avoid duplicate roots; Back → Home | Fade 150ms |
| FLOW-13 | Week/Month toggle | Change query/range in place; Back still leaves Progress | Bars 400ms, stagger 40ms |
| FLOW-14 | Bottom tab → `profile` | Same root-switch contract; Back → Home | Fade 150ms |

Shared UI states: SCR-03/04 are Home; SCR-07/08 are detail; SCR-09/10/11 are Coach states; SCR-12/13 are Progress; SCR-14 is a modal. Planning Coach also reuses the error layout. Maintain these relationships.

The 14 flows are not a complete list of every interaction: the Coach root, empty-home planning, edit/save, reminder dialog, archive/delete outcomes, past-date corrections and export/wipe still need the small contracts described below. No additional permanent screen is justified merely to fill those gaps.

## 7. Necessary corrections and unresolved decisions

### Confirmed contradictions requiring a narrow resolution

| ID | Evidence and problem | Smallest recommended resolution |
|---|---|---|
| C-01 Naming | All original resources and current app strings use HabitFlow; owner requires Habit Companion | Use Habit Companion throughout user-facing copy and future deliverables. Retain original source filenames for traceability. Do not rename a database/package in a way that loses existing data. |
| C-02 Settings storage | D p.1 and S pp.7–8 refer to Room Settings/SettingsDao; F FLOW-02/14 and S p.23 say DataStore | Use DataStore for preferences/onboarding and Room for habits/history. Correct the documentary entity labels; current SettingsRepository already follows this separation. |
| C-03 Coach context and privacy | F guide says Coach receives only a habit ID, but planning takes a draft; S p.17 forbids sending a name while p.13 explicitly sends a draft name | Separate local navigation arguments from network payloads and distinguish planning from existing-habit mode. Disclose the planning exception. Include the retrieved strategy text and follow-up question in the actual payload contract. “No habit information ever leaves the phone” is not accurate for this design. |
| C-04 Empty-home planning | S p.9 goes directly to SCR-06 without a habit, while p.13 always returns to an existing SCR-05 form | Keep the direct shortcut. Give it an unsaved draft owned by the creation flow; use the existing input to obtain a habit idea before generating advice. Apply opens SCR-05 with that draft; cancel returns to empty Home. Planning entered from SCR-05 instead pops back to that existing form. This adds state handling, not a new designed screen. |
| C-05 Coach tab | Four tabs are required, but contextual Coach needs one habit and no selection flow is defined | Keep the tab. Recommended: if only one active habit exists, use it; if several exist, use a compact local selection sheet before rendering the existing Coach layout. Do not send the habit list to the model or silently select an arbitrary habit. No-habit state follows the disabled-tab rule. This selection interaction remains a proposal. |
| C-06 Apply and Undo | F FLOW-09 says update Room immediately and also says commit after ten seconds; S p.18 reflects the change immediately | Prefer an immediate atomic write with a retained previous value and ten-second inverse operation. On Undo, revert only that applied change, not later edits or completions. Define persistence/re-entry behaviour for the deadline. Update “committed after ten seconds” wording to “Undo expires after ten seconds.” |
| C-07 What Apply changes | Mockups include goal reduction, habit stacking and “protect the second day”; the stated write path only updates an integer goal | Define typed, validated actions: goal description/target, cue/anchor, schedule or reminder as appropriate. An informational principle cannot honestly show “Goal changed.” Store the actual adopted plan and use accurate confirmation copy. Do not parse arbitrary model prose into database writes. |
| C-08 Risk versus zero streak | S pp.11/15 force zero for at-risk rows, but two historical misses can coexist with a new positive run | Display the real current count. New habits with zero completions are neutral, not automatically broken. Keep the risk callout and needs-attention grouping separate from streak arithmetic. |
| C-09 Recovery rule | S p.15 says risk means two misses in the last seven scheduled days and also says recovery after two clean days | Proposed reconciliation: trigger on the stated miss pattern, clear attention after two consecutive completed scheduled occurrences, and re-evaluate when a later miss happens. Non-scheduled days do not count as recovery days. This is a policy choice requiring agreement; blindly using both rules produces contradictory status. |
| C-10 Monthly bars | S p.21/F FLOW-13 insist on five weekly bars | A calendar month can span 4–6 weeks. Preserve weekly aggregation and the chart design, but render the actual number of calendar-week buckets, clipping each bucket to the selected month and respecting week-start preference. Adjust width/gap only as needed. |
| C-11 Navigation root | F FLOW-12 uses `popUpTo(startDestination)` although the launch start is Splash and it has been removed | Use Home/the main graph as the root anchor after launch. Preserve the specified Back-to-Home and tab-state behaviour rather than literally targeting a removed Splash entry. |
| C-12 Back destinations | S p.19 always says Back → detail, even when failure came from planning; detail footers say Home despite entry from Progress | Return to the actual caller. Applied/error states do not create new entries. Remove S p.14's stray planning-Coach exit for an already-saved habit. |
| C-13 Coach off | F FLOW-14 lists hidden cards/buttons but omits the Coach tab | Gate every Coach entry and any pending request. Recommended: retain the four-tab layout with Coach unavailable and an explanation pointing to Profile, instead of reshuffling navigation. |
| C-14 Always-dark Splash and scrim | S p.7 says theme-resolved `on.surface` is always dark, but its dark-theme value is light; S p.22 uses `on.surface` for scrim despite a scrim token | Use a fixed splash palette matching the original dark appearance and use the explicit scrim token at 55%. Existing Splash already uses the light palette's dark background. |
| C-15 Small targets and fixed sizes | S requires 48dp targets but specifies 32dp Apply, 40dp weekdays, 28dp calendar dots and a 36dp segment | Keep smaller visuals inside non-overlapping touch areas. Seven calendar targets need at least 336dp before extra gaps, versus 353dp content width at the reference viewport: the 8dp visual gap cannot also be added between seven 48dp targets. Use distributed calendar cells with centred dots; allow a narrowly adjusted grid on smaller widths. |
| C-16 Data failure versus empty | S p.7 routes storage failure to “empty Home” | Reuse the Home surface, but distinguish failed load from zero records; show Retry, preserve data and prevent misleading write/success actions while storage is unavailable. Never reset the database as an automatic fallback. |

### Visual conflicts to record, not silently redesign

| ID | Conflict | Recommended interpretation |
|---|---|---|
| V-01 Missing accent tokens | Mockups use blue/purple/green/orange habit swatches, orange streaks/chart accents and yellow strategy tags; F-01 supplies mostly teal/danger tokens | Preserve the shown habit choices. Define their named light/dark swatch tokens from the references when implementing. For general state colours, provisionally follow explicit primary/danger specifications; flag any visible difference in the fidelity review. Do not reduce icon/colour selection to a single teal choice. |
| V-02 Detail variants differ visually | Healthy mockup has circular calendar days; broken mockup has square days and an outlined “Get a plan”; the table requires shared dots and a filled at-risk action | Prefer the explicit shared C-05 dots and filled at-risk button. Keep layout and informational hierarchy. Record these two visible deviations from those particular pictures. |
| V-03 Foundation rules overstate uniformity | “No values outside” conflicts with 20dp empty-card radius, 10dp pager gaps, 24sp Profile values and other explicit exceptions | Preserve documented screen-specific exceptions; do not flatten them into the generic scale. |
| V-04 Home pictures versus code/table | Pictures show coloured icon left and completion at right; current code puts completion left and omits the coloured icon. Some mockups show an attention subtitle rather than the specified divider | Preserve icon/name/helper/streak/completion order from the mockup and the explicit grouping behaviour from S p.11. The component table does not demand a left-side completion control. |
| V-05 Additional picture/table differences | Create is rectangular in some pictures but C-08 is pill-shaped; Profile avatar looks smaller than its 100dp spec; Coach reading is a dark card in some pictures but table describes plain text | Provisionally use explicit shape/size measurements and preserve surrounding composition. The Coach reading container is a visual choice still to resolve against the preferred reference, not a logical reason to redesign the Coach. |
| V-06 Weekly chart | Mockup highlights a bar orange; S p.20 specifies primary bars and bold “today” axis text | Follow the explicit chart rule provisionally. Preserve seven bars, labels, units and layout. |

### Missing definitions needed to make the specified app coherent

These are gaps rather than proven mistakes. The recommendations deliberately avoid additional screens.

1. **Daily / Weekly / Custom:** recommended meanings are all seven weekdays / one selected weekday / a nonempty set of selected weekdays. The sources do not state whether Weekly means once on a chosen day or any day in a week. Keep all three choices; settle the meaning before implementing streaks. Show weekdays consistently: Daily can show all selected but disabled; Weekly single-select; Custom multi-select. This resolves p.12's “Custom reveals” versus “Weekly and Custom enabled” wording without removing the control.
2. **Day and schedule history:** record one binary completion per habit/date. Today is pending until the local day ends; dates before creation, after archive and non-scheduled dates are not misses. Recompute on date/resume/time-zone changes. Retain effective dates for schedule edits so new schedules do not rewrite historical denominators or streaks. Goal reductions must not erase prior check-offs.
3. **Goals versus counts:** the UI promises one tap, while the current schema describes `goal` as times per day and `Completion.count` as a quantity. The examples mean pages, distance, glasses or duration. Recommended v1: binary day completion plus a goal description/optional structured quantity and unit. A smaller goal changes future expectations, not the validity of earlier completed days. Do not add an unrequested multi-tap counter UI.
4. **Statistics:** use completed eligible habit-days divided by scheduled eligible habit-days, rather than averaging habit percentages. Exclude future dates and dates outside each habit's active schedule. For the current period, recommended denominator includes scheduled today, so consistency can rise during the day, while risk still excludes unfinished today. Show a neutral no-data value when denominator is zero. Weekly chart is daily completion count as labelled; monthly chart is weekly percentage. Define Profile lifetime consistency by the same eligibility rules.
5. **All-time values and archives:** best streak means all available history; do not cap it at an arbitrary lookback. Recommended: archived histories remain in historical/lifetime totals up to archive, while archived habits leave Home and active top-streak lists. Delete removes their retained records and therefore affects totals. Permanent archived-habit browsing/unarchive is not specified; record that limitation rather than adding a new screen automatically.
6. **Unscheduled habits on Home:** preserve the “every habit” principle. Show active habits, but exclude non-due habits from today's total and prevent an unscheduled check-off from inflating completion statistics. Give a short not-due indication within the existing row. No-due-today requires neutral copy, not “No habits yet” or false all-done success.
7. **Past corrections:** permit eligible past days only, including no future/pre-creation completion. Current-day logging remains on Home. Recompute current/best/rate/risk together after a correction; a factual correction can change best even though an ordinary streak break must not reset it.
8. **Duplicate names and validation:** preserve a duplicate-name state, but do not assume names are database identifiers. Recommended: trimmed, case-insensitive duplicate warning among active habits; exact blocking/allowing policy remains unresolved. At least one valid scheduled weekday is required; prevent double-save and keep input on write failure. Disabled Create handles blank input; field validation can still handle attempted keyboard submission without enabling an invalid button.
9. **Reminders:** global daily toggle/time and the per-habit Change reminder row are both present. Define global enablement plus per-habit override/inherited time, local scheduled days, cancellation after archive/delete and behaviour when notifications are unavailable. Exact delivery timing and implementation are not specified. Do not silently drop either existing control.
10. **Export, clear and restore:** define an export format and included data before enabling export. Explicitly distinguish clearing Coach conversations/cache from wiping all habit data. Define whether a full wipe resets preferences/onboarding. Keep restore disabled in v1 as written; do not infer that export must also be removed. Ensure controls below the cropped Data section can scroll into view.
11. **Coach knowledge and service:** create the approximately 50 curated strategy cards with identifiers, principles, conditions and source attribution; no such asset is supplied. Choose service/provider, authentication, request/response format and limits during implementation. An API credential should not be treated as a user-facing feature. Keep retrieval local and validate returned strategy IDs/actions; handle missing, malformed or fewer-than-three suggestions as a response failure, not a partially valid application of changes.
12. **Claims and follow-up data:** the model may interpret measured behaviour but cannot know that “your week changed” or that evenings are busy unless told. Keep supportive language conditional. Follow-ups may contain personal text; disclose that submitted questions are transmitted. The retained 50-message local history does not automatically authorise sending the entire conversation on every request.
13. **Privacy and device backup:** current manifest enables backup and the backup XML files contain template rules. This needs a deliberate configuration review against the “stored on this phone” promise. Either enforce the intended local-only boundary or accurately describe supported backup/export exceptions. Do not claim the existing configuration has been privacy-verified.
14. **Accessible fixed layouts:** allow text/card height to grow when needed, keep docked input/buttons above keyboard and system bars, provide spoken labels/state for custom controls, and preserve non-overlapping tap targets. These are narrow usability accommodations, not a change in screen composition.

## 8. Existing implementation compared with the plan

This is a partially implemented project, not just an empty Android Studio template.

| Area | Observed state | Remaining work or deviation |
|---|---|---|
| Project/build | Single app module, Compose/Material 3, navigation, Room, DataStore and ViewModels; minSdk 24, target 36, compile 36.1 in files | No dependency upgrades are justified by this review alone; build compatibility was not tested |
| Branding | Launcher label, splash and onboarding still say HabitFlow; root project is `Habit`; database `habitflow.db` | Update public name. Retain storage continuity; an internal filename is not a branding defect |
| Foundations | Dedicated Color, Type, Dimens and Theme files closely mirror F-01/F-02; dynamic colour intentionally absent | Preserve these foundations; add only missing documented tokens and exceptions |
| Splash | Timed readiness, delayed loading, DataStore onboarding decision, Room count and Home error route exist | Review failure/cancellation and retry behaviour with real storage faults |
| Onboarding | Three panes, Skip, completion persistence, pager Back and disabled Restore exist | Candidate copy contains old branding and overbroad privacy wording; last button currently says Get started rather than the specified Continue |
| Home | Empty/populated layouts, greeting/date, tabs, Room observation, toggle and streak grouping exist | Missing illustrated row icons/goal subtitles and summary copy; loading skeleton and all-done presentation incomplete; row long press absent |
| Navigation | Splash, onboarding, Home, new-habit stub and three tab placeholders exist | Habit row currently opens the Coach placeholder instead of detail; root stack uses removed launch start; all transitions are currently 150ms fades despite F's explicit slide/shared-element transitions |
| New habit | Name-only form inserts a daily habit with defaults | Icon/colour, frequency/day picker, Coach card, edit mode, validation, dirty Back and saved draft are unfinished |
| Detail and management | No detail screen or action-sheet implementation | SCR-07/08/14 and their operations remain to build |
| Coach | Placeholder only; no service, strategy asset, conversation/suggestion tables or request code found | SCR-06/09/10/11, payload rules, application actions, Undo and error handling remain to build; manifest currently lacks Coach networking permissions |
| Progress | Placeholder | StatsAggregator, week/month charts and detail entry remain to build |
| Profile | Placeholder; settings repository only stores onboarding, name and theme | MainActivity follows system theme without observing saved theme choice; remaining settings, reminders, export, clearing and counts remain to build |
| Data | Habit and Completion tables, DAO queries, archived timestamp and completion cascade exist; schema v1 exported | No Coach history/cache, reminder/cue or effective schedule history model; changes need data-preserving schema migrations |
| Domain | Current/best/risk calculations exist | Current history fetch and streak walk cap at 400 calendar days; risk lacks two-clean-day recovery; schedule semantics and eligible dates need agreement |
| Verification | Default arithmetic and package-name example tests only | No meaningful habit-domain/navigation/Coach behaviour tests yet |

Additional concrete concerns visible in source:

- `HomeScreen` refreshes the date only when its `LaunchedEffect(Unit)` starts. That does not refresh a screen continuously left open across midnight.
- `HomeViewModel` catches an observation error by emitting an ordinary empty state. The snackbar relies on the launch route's `dbError` argument, so a later error can be indistinguishable from zero habits.
- Completion controls remain actionable for non-scheduled rows. The total filters scheduled habits, so visible actions and totals can disagree.
- `CompletionRing` accepts a content description but does not attach it to its interactive control; spoken labels/state need verification and completion.
- The Home summary computes the longest active streak but labels it “Best streak”; that confuses it with the all-time best used elsewhere.
- The form uses transient `remember` for its name and has no saving/error state. Draft retention and repeated submissions need handling when the full form is built.

These are source-review observations, not claims that the app was executed or every runtime defect has been found. Most missing areas are ordinary unfinished implementation, not reasons to alter the requirements.

## 9. Acceptance checks for later implementation

- Final visible branding is Habit Companion, while existing local records survive the rename.
- First launch follows SCR-01 → SCR-02 → empty Home; subsequent launch opens actual Home. Back never resurrects Splash/onboarding.
- All 15 documented states can be demonstrated, including loading, no data, errors, applied/Undo and management confirmations.
- Home matches the original hierarchy and row composition; completion and row navigation remain separate actions.
- Create/edit preserves drafts through Coach and recreation, validates schedules, writes once and returns to the correct caller.
- No premature misses today; no phantom misses before creation; rest days do not break streaks; current and best remain distinct; zero-history habits are not branded failures.
- Corrections, schedule changes, archive and delete update history/statistics consistently. Long streaks beyond 400 days remain correct.
- Weekly and monthly figures use the same eligibility rules, handle zero denominators and partial periods, and cover every date in 4–6-week months.
- Coach receives only the agreed mode-specific payload; retrieves local strategies; renders three validated suggestions; applies the correct action; Undo preserves unrelated changes.
- Offline/timeout/rate-limit/server states retain context and local functionality; Retry and Back follow the existing flow.
- Tabs preserve state without stacking; details opened from Progress return to Progress; modal Back dismisses the modal first.
- Theme, week start, reminders and Coach controls actually affect behaviour; export and clearing match their labels and confirmations.
- Compare light/dark screens at 393 × 832dp with the supplied references, then check smaller screens, large text, keyboard and system insets. Only documented necessary adaptations should differ.

## 10. Recommended next implementation order

First settle the small policy gaps that affect stored data: schedule meanings/history, binary completion versus goal quantity, risk recovery, typed Coach actions and Undo. Then finish the existing daily workflow and detail/management screens, followed by Progress/Profile/reminders and the Coach integration. Keep screen composition tied to S and D and transitions tied to F throughout. This sequence does not change the final scope or defer the AI Coach out of the project.

The initial plan is usable. Its required changes are mainly precise behaviour contracts and a few internal contradictions; they do not justify a new design or a different application.

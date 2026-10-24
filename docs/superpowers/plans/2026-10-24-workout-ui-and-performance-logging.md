# Relven Workout UI and Performance Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make workout details fully readable above Relven’s measured floating navigation and let users record genuine offline workout performance with recovery, summaries, and export.

**Architecture:** Keep the current Compose home shell and extend its viewport from the measured navigation obstruction rather than guessed spacers. Extend the existing `StrengthSession` state machine and its private `AtomicFile` JSON store (there is no Room-backed strength-session store) for confirmed set results; retain the prescribed workout JSON as immutable input. Add logging/timer behavior, persisted result metadata, summary/history, and versioned import/export in separate, sequential pull requests.

**Tech Stack:** Kotlin, Jetpack Compose, Android `AtomicFile`, existing Relven workout v3 parser and Gradle unit tests.

**Spec:** User request dated 2026-10-24, “Relven 2.0: Fix Workout UI and Implement Fast Set/Repetition Logging”.

## Global Constraints

- Do not use the owner’s physical Android device through ADB.
- Preserve the prescribed workout and existing workout v3 / plan schemas; actual results are separate from prescriptions.
- Persist each explicitly confirmed result locally and idempotently; never infer completed reps from a prescription.
- Preserve offline operation and existing workout execution/recovery behavior.
- Work on one Relven PR at a time, merge it before opening the next, and build phone and Wear debug APKs after each merge.
- Do not create multiple worktrees or merge unrelated upstream PRs as part of this feature.

## Review Focus

- The bottom-navigation obstruction changes with system navigation mode, font scale, and available viewport; test that the content inset uses measured geometry and clamps safely.
- A timed test can finish while a rest countdown is underway; keep the original set identity and let both states progress without confirming a result implicitly.
- Process death can occur during a set, pending entry, rest, or after confirmation; retain only confirmed values and recover phase/timer identity.
- Unilateral values and unknown optional measurements must not be collapsed into bilateral totals or zeroes.
- Legacy strength session files and plan snapshots must remain readable after storage/export changes.

---

## Sequential PR roadmap

Each PR is built, tested, reviewed, merged, and followed by fresh phone and Wear debug APK builds before the next PR is opened.

### PR 1: Measured safe viewport for the shared floating navigation

**Files:** `RelvenHomeActivity.kt`, Compose destination screens currently reserving fixed bottom spacers, a small pure layout helper, and its unit test.

- Measure the navigation’s actual top edge and the root content bounds.
- Constrain the content viewport to the area above that edge across all home destinations.
- Remove duplicated hard-coded bottom spacers from shared-shell screens.
- Verify viewport changes when root height/navigation geometry changes and that no navigation produces zero obstruction.

**Tests:** `HomeNavigationViewportTest` for measured, clamped obstruction calculations; app unit tests, lint, debug build, and `git diff --check`.

### PR 2: Durable performance record model and backward-compatible store codec

**Files:** `StrengthSession.kt`, `StrengthSessionStore.kt`, `StrengthSessionCodecTest.kt`, new set-result schema/export model tests, and schema documentation.

- Model actual repetitions/duration, side, completion/skip, timestamps, and optional ratings/notes without changing the prescribed workout snapshot.
- Support explicit zero reps and plausible high counts with a safe storage bound.
- Version the active/completed session JSON codec and continue decoding v1 files.
- Make confirmed result writes atomic, corrections/deletions explicit, and retries idempotent by execution/exercise/set identity.

### PR 3: Set state machine and monotonic timer lifecycle

**Files:** `StrengthSession.kt`, runtime/timer coordinator used by `RelvenHomeActivity`, recovery codec, and focused state/recovery tests.

- Add ready, active, awaiting-result, resting, paused, completed, and skipped semantics as needed by existing strength prescriptions.
- Anchor elapsed countdowns to monotonic time, persist sufficient anchors for recovery, and keep rest advancing while a result is pending.
- Preserve association with the set that produced the timed test even after rest expires or the next set becomes ready.
- Cover zero-rest, late logging, pause/resume, skip, and process recovery.

### PR 4: Fast Swedish logging UI

**Files:** `StrengthSessionScreen.kt`, Compose state/presentation helpers, string resources, and UI/presentation tests.

- After timed AMRAP, present a keypad-free result panel with large decrement/increment controls, hold acceleration, quick values, manual entry on request, skip, confirmation, and feedback.
- Add a concise left/right segmented control for unilateral results and an expandable area for optional ratings/notes.
- Add a clear manual “Set klart” path for untimed strength and an edit path for saved sets.
- Keep focus/keyboard closed by default, provide accessible touch targets, Swedish copy, and restrained haptics.

### PR 5: Real-performance summaries and history

**Files:** strength summary/history screens and state, relevant plan detail actions, `StrengthProgression.kt`, and tests.

- Show only stored actual values, including partial/skipped sets and completed/prescribed counts.
- Derive totals, best/average sets, work/rest duration, and left/right differences only when comparable data exists.
- Keep completed historical workouts distinct from unfinished resumable sessions.

### PR 6: Versioned performance JSON export/import

**Files:** existing Relven export/import path, performance schema, round-trip tests, migration tests, and AI kit capability docs.

- Export prescription and execution/results as distinct sections with stable identities, units, timestamps, optional fields, and partial status.
- Validate imported data before mutation and preserve older plan/workout formats unchanged.
- Verify round-trip fidelity, unknown optional values, duplicate retries, and legacy records.

## Execution gates

For each PR, write a failing focused test before implementation, observe the expected failure, make the smallest implementation that passes, run the complete relevant Gradle test target, lint and build, inspect the diff, request review, merge only after required CI is green, then build both debug APKs from updated `master`. Continue to the next PR only after the previous one is merged and those APK builds finish. Never use physical-device ADB.

## Known architecture findings

- Plan detail is `PlanCalendarScreen.kt` → `PlanWorkoutDetailsScreen.kt` inside `RelvenHomeActivity`’s floating-navigation `Box`.
- The detail uses a `116.dp` bottom spacer; Today, Exercise, Plan library/creator, and Progress use similar fixed spacers, while Plan library also applies nested bottom padding.
- Strength execution is implemented by `StrengthSession.kt` and `StrengthSessionScreen.kt`; `StrengthSessionStore.kt` uses private, atomic JSON files, not Room. The session codec is version 1 and currently rejects zero repetitions, caps a result at 500, and stores a countdown integer rather than a monotonic timer anchor.
- The current session store already persists in-progress state and completion history, and `StrengthProgression.kt` derives history from completed session results.

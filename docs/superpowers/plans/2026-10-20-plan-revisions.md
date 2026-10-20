# Plan Revision and Rescheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow a user to reschedule one item in the active plan by creating and activating an immutable full plan revision.

**Architecture:** Extend the strict parser to accept schema v1 imports and schema v2 revision snapshots. A tested transformation changes one entry date and adds revision metadata. The atomic plan store writes a new unique package before switching the active reference. Compose adds an explicit target-date selection flow with cancel, saving, and error states.

**Spec:** `docs/superpowers/specs/2026-10-20-plan-revisions-design.md`

## Global constraints

- Keep `se.hampusandersson.relven` and RunnerUp's existing workout/session engine.
- Keep original imports and past revisions unchanged.
- Do not change workout definitions, activity history, or infer completion.
- Do not use the physical Android device before all phases are complete and ready for manual testing.
- Only one PR may be open; merge before starting another.

## Review focus

- Schema v1 imports continue to parse identically; v2 revision metadata is strictly validated.
- A move preserves all package fields and unrelated entries; illegal rest/workout conflicts do not write.
- A revision is a new full snapshot with monotonically increasing lineage metadata.
- Atomic write or active-reference failure never changes the prior active plan.
- Calendar selection, cancel, saving and error recovery are localized and accessible.

### Task 1: Strict v2 revision schema and date transformation

- [x] Add failing parser and transformation tests for v1 compatibility, valid/invalid lineage, exact date move, field preservation, and collision rules.
- [x] Implement v2 metadata parsing and the revision transformation.
- [x] Run focused importer tests and verify the new tests pass.

### Task 2: Atomic revision store and active-plan switch

- [x] Add failing store tests for new revision ID, sequence/parent linkage, immutable old package, active switching, duplicate/recovery behavior.
- [x] Implement atomic save plus active-reference update, validating the previous saved package.
- [x] Run focused store tests and inspect failure recovery. Invalid moves and bad lineage leave the active package unchanged; revisions list in stable sequence order.

### Task 3: Calendar rescheduling flow

- [x] Add localized strings and per-entry reschedule controls with accessible labels.
- [x] Add target-date selection mode, cancel, explicit save, saving state, conflict feedback and recovery.
- [x] Integrate store calls off the UI thread and refresh active plan only on success.
- [x] Run relevant unit tests, formatter, debug/release builds and lint; audit responsive and accessibility behavior. The 7-column grid maintains at least 44dp date-cell width on a 320dp viewport.

### Task 4: Review and integrate

- [x] Run full local verification and `git diff --check`. Final gate passed after the same-date guard and stable revision ordering were added.
- [ ] Obtain independent branch review and fix actionable findings test-first.
- [ ] Commit, open one PR, attach it to the task, wait for hosted Android CI, merge, and verify no open Relven PR remains.
- [ ] Do not use physical ADB.

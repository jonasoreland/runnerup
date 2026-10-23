# Exercises layout and simple goals implementation plan

**Goal:** Fix the Exercises header overflow/spacing and let a simple run finish on a chosen time or distance while preserving current pace and heart-rate behavior.

**Architecture:** Keep the existing Compose screens and RunnerUp workout engine. Constrain the catalog header to screen width, place collection-transfer buttons after search, and use a dedicated persisted goal mode whose time/distance values become the active step's RunnerUp duration.

**Spec:** [Exercises layout and simple run goals](../specs/2026-10-23-exercise-layout-and-simple-goals-design.md). Existing palette and spacing remain authoritative (`RelvenColors.Background`, `Surface`, `Orange`).

**Constraints:** Keep `se.hampusandersson.relven`; use one checkout and one PR at a time; no physical ADB device; use `emulator-5586` through ADB port 5039.

## Tasks

- [x] Reproduce the layout on isolated emulator `emulator-5586`; confirm the header is clipped and search is far below it.
- [x] Add failing tests for goal-mode migration/mapping and RunnerUp step duration values.
- [x] Implement none/pace/time/distance/optional-heart-rate selection, persist the selected goal, and map time/distance to active-step duration.
- [x] Remove horizontal scrolling from the catalog header and move collection transfer actions below search.
- [x] Build/install on the isolated emulator and inspect Swedish layout and time/distance controls.
- [x] Run focused and full test/lint/build verification; inspect the final rendering and current diff.
- [ ] Update implementation status, commit, open the single PR, wait for CI, and merge before any next PR.

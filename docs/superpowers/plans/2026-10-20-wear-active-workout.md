# Wear Active Workout Glance Implementation Plan

> **For agentic workers:** Use the Superpowers TDD workflow task by task. Keep one open PR at a time and merge before starting another increment.

**Goal:** Replace RunnerUp's active Wear metric cards with a compact native Compose glance while retaining RunnerUp as the workout/session owner.

**Design:** `docs/superpowers/specs/2026-10-20-wear-active-workout-design.md`

## Tasks

1. Add a pure, tested mapping from RunnerUp's existing header/data keys into ordered Wear metric display state. Confirm the new test fails before implementing the mapping.
2. Add the current step intensity label to the existing TrackerWear header payload; keep the value localized by the phone and omit it when unavailable.
3. Enable Compose in the Wear module using the repository's existing Compose BOM and material dependencies. Replace only `RunInfoFragment`'s metric card rendering with the Compose glance. Preserve the fragment polling lifecycle and all existing pager/control/HR pages.
4. Run Wear tests, latest debug/release builds, lint, Spotless and diff checks. Inspect relevant layouts and compare source proportions against the downloaded Stitch Wear image. Do not use ADB.
5. Update implementation status, roadmap and feature parity with actual local/hosted results; request independent review; open one PR, wait for CI and merge it before beginning another milestone.

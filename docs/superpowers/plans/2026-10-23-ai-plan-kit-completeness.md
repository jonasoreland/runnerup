# AI Plan Kit Completeness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make clipboard and ZIP exports fully describe Relven's plan capabilities and include the complete bundled and device-local exercise catalog.

**Architecture:** Build a reproducible v2 base ZIP from canonical source files and the full bundled library. Add the current custom exercise collection at the user's explicit export action, and compose clipboard text from the same packaged source documents plus live local data.

**Tech Stack:** Python 3 standard library; Android Java/Kotlin; JSON Schema; JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-23-ai-plan-kit-completeness-design.md`

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- New AI output uses plan schema v1; schema v2 is created only by Relven from a stored predecessor.
- Do not modify plan, workout, or exercise schema contracts.
- Include local custom exercises only in user-initiated copy/share/save exports; no history, media, or network calls.
- Archived custom exercises may be described but must never be proposed as selectable plan exercises.
- Keep the owner’s physical Android device disconnected until the app is ready for manual testing.
- Keep at most one open PR and use the existing worktree.

## Review Focus

- Capability guide must distinguish schema-valid from importer-supported features.
- Custom IDs/revisions/archive state and bilingual text must survive export exactly.
- Empty custom catalog and malformed/oversized local data must have explicit behavior.
- Clipboard copy and ZIP save/share must use the same data snapshot and include all required files.
- ZIP v2 naming, asset names, UI labels, tests, and docs must agree.

## Tasks

### Task 1: Specify complete kit contents and capabilities

**Files:** `docs/ai-plan-kit/README.md`, `docs/ai-plan-kit/INSTRUCTIONS.md`, `docs/ai-plan-kit/capabilities.json`, new `docs/ai-plan-kit/JSON-FORMAT.md`.

- [ ] Document plan fields, dates/timezone, IDs, revisions, validation/resource limits, supported running ends/targets/nodes, supported strength exercise prescriptions, precise unsupported features, and examples.
- [ ] Describe bundled and custom exercise identities, revisions, equipment, bilingual data, active/archive status, and exact snapshot rules.
- [ ] Expand machine-readable capabilities to match the validators and app authoring UX.
- [ ] Review every claim against `RelvenPlanImporter`, `RelvenWorkoutImporter`, `RelvenStrengthWorkoutParser`, schemas, and current Plan creator.

### Task 2: Build deterministic v2 base kit

**Files:** `tools/build_ai_plan_kit.py`, `tools/test_build_ai_plan_kit.py`, `docs/ai-plan-kit/*`, `app/assets/relven/relven-ai-plan-kit-v2.zip`.

- [ ] Add a regression test that requires the bundled exercise library and custom collection schema in the ZIP, plus examples and all canonical schemas.
- [ ] Add source parity tests and verify v2 versioning plus exercise-count metadata.
- [ ] Generate the base ZIP with stable entries from the existing asset and schemas.
- [ ] Validate all bundled JSON files and relative schema references.

### Task 3: Include the user's custom collection at export time

**Files:** `app/src/main/org/runnerup/workout/RelvenPlanKit.java`, a narrow Java-callable exercise export facade in `app/src/main/kotlin/org/runnerup/relven/exercise/`, corresponding Robolectric tests, `ManageWorkoutsActivity.java`, `RelvenHomeActivity.kt`, localized strings.

- [ ] Add tests first for custom active and archived exercise export, no-custom export, and complete clipboard content.
- [ ] Add a Java-callable method that snapshots `CustomExerciseStore.exportCollection()` without exposing its private path to UI code.
- [ ] Implement runtime ZIP generation that adds the bounded custom collection to the static v2 package without closing caller-owned output streams.
- [ ] Compose clipboard content from the human prompt, JSON format guide, schemas, complete bundled catalog, examples, and the exact current custom collection.
- [ ] Keep all share/save/copy generation local, bounded, and failure-reporting; update labels to say the complete kit is copied/exported.
- [ ] Update tests for v2 asset path, generated ZIP contents, static/dynamic boundaries, and share-cache exact bytes.

### Task 4: Document and verify

**Files:** `docs/plan-format.md`, `docs/implementation-status.md`, existing AI Plan Kit docs/tests.

- [ ] Update app-facing docs with the full contents and copy/share/save workflow.
- [ ] Run Python kit tests, schema and ZIP validation, focused app tests, Latest debug build, lint, formatting, and `git diff --check`.
- [ ] Inspect staged/modified files carefully; leave unrelated recovery, insets, and attachment changes untouched.
- [ ] Request independent review, address findings, then open one PR only when the current PR queue is clear.

# AI Plan Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let phone users copy the AI plan-generation instructions and share or save a versioned ZIP built from Relven's current canonical plan/workout schemas.

**Architecture:** A deterministic Python standard-library generator packages documentation, capability metadata, the implemented JSON schemas, and the running example into a checked-in Android asset. A small Android helper exposes the instructions and archive bytes to the existing Workout Manager, which provides clipboard, chooser, and document-picker actions using localized UI and a cache-scoped FileProvider.

**Tech Stack:** Python 3 standard library, Android Java, AndroidX Activity Result APIs and FileProvider, Robolectric/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-ai-plan-kit-design.md`

## Global Constraints

- Application ID remains `se.hampusandersson.relven`.
- RunnerUp remains responsible for workout execution and activity history.
- New AI-generated plans use schema v1; schema v2 is only for app-managed revisions with a saved predecessor.
- The feature works offline and calls no AI service.
- Do not use the physical Android device until all phases are complete and manual testing is ready.
- Maintain one open PR at a time; merge it before starting the next increment.

## Review Focus

- Canonical schema updates must make the generated ZIP check fail until the asset is rebuilt.
- Unsupported workout/plan features must not be described as supported in the prompt or capability metadata.
- Canceling Android's save picker must not show a false success state.
- Share URIs must grant read-only access to the cache ZIP without exposing broader app files.
- Clipboard copy must extract the exact packaged instructions and give localized success/failure feedback.

---

### Task 1: Author and package the AI Plan Kit

**Files:**
- Create: `docs/ai-plan-kit/README.md`
- Create: `docs/ai-plan-kit/INSTRUCTIONS.md`
- Create: `docs/ai-plan-kit/capabilities.json`
- Create: `tools/build_ai_plan_kit.py`
- Create: `tools/test_build_ai_plan_kit.py`
- Create: `app/assets/relven/relven-ai-plan-kit-v1.zip`
- Read: `schemas/plan-v1.schema.json`, `schemas/plan-v2.schema.json`, `schemas/workout-v3.schema.json`, `schemas/examples/plan-running.json`

**Interfaces:**
- `build_archive() -> bytes` creates deterministic ZIP bytes from canonical source files.
- `main(--check)` returns failure when the checked-in asset differs from `build_archive()`.

- [ ] **Step 1: Write failing Python tests** for deterministic bytes, exact archive entry names, byte-for-byte parity with all source inputs, v1 generation/v2 app-managed capability flags, and stale-asset detection.
- [ ] **Step 2: Run `python tools/test_build_ai_plan_kit.py`** and verify it fails because the generator/source files do not yet exist.
- [ ] **Step 3: Add README, instructions, capabilities metadata, and generator**. Package both plan schemas, workout v3 schema, and the supported sample. Normalize text to LF and fix entry timestamps/permissions/order.
- [ ] **Step 4: Generate the asset and run `python tools/test_build_ai_plan_kit.py` plus `python tools/build_ai_plan_kit.py --check`**; all tests must pass and the output ZIP must open successfully.

### Task 2: Add native copy, share, and save actions

**Files:**
- Create: `app/src/main/org/runnerup/workout/RelvenPlanKit.java`
- Create: `app/test/java/org/runnerup/workout/RelvenPlanKitTest.java`
- Create: `app/res/xml/relven_plan_kit_file_paths.xml`
- Modify: `app/AndroidManifest.xml`
- Modify: `app/res/layout/manage_workouts.xml`
- Modify: `app/src/main/org/runnerup/view/ManageWorkoutsActivity.java`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Use: `app/assets/relven/relven-ai-plan-kit-v1.zip`

**Interfaces:**
- `RelvenPlanKit.readInstructions(Context) -> String` extracts the exact packaged `INSTRUCTIONS.md`.
- `RelvenPlanKit.copyArchive(Context, OutputStream) -> void` copies the complete packaged ZIP without closing the caller's stream.
- `ManageWorkoutsActivity` owns the native action dialog, clipboard, chooser, and create-document result handling.

- [ ] **Step 1: Write failing Robolectric tests** for prompt extraction, required capability/schema entries, and copied archive byte equality.
- [ ] **Step 2: Run `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanKitTest`** and verify the tests fail because the helper/asset access is missing.
- [ ] **Step 3: Implement the helper and native actions** with background ZIP copying, clipboard copy, Android share chooser, and `CreateDocument("application/zip")` save.
- [ ] **Step 4: Configure a cache-only FileProvider path with grant-read permission**; leave WorkoutFileProvider behavior unchanged.
- [ ] **Step 5: Add localized English/Swedish action labels and success/failure feedback.** Canceling the document picker performs no write and shows no success message.
- [ ] **Step 6: Run `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanKitTest :app:assembleLatestDebug`** and verify all pass.

### Task 3: Document and verify the increment

**Files:**
- Modify: `README.md`
- Modify: `docs/feature-parity.md`
- Modify: `docs/implementation-status.md`
- Verify: all files changed by Tasks 1 and 2

- [ ] **Step 1: Update documentation** with kit content, supported generation scope, copy/share/save access, and exact rebuild/check commands.
- [ ] **Step 2: Run full local gate**: `./gradlew :app:assembleLatestDebug :app:assembleLatestRelease :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck`, Python tests, generator `--check`, ZIP/schema validation, and `git diff --check`.
- [ ] **Step 3: Review the changed source, test failure/pass evidence, and untouched application ID.** Do not run ADB or install an APK.
- [ ] **Step 4: Commit, request one independent review, address findings test-first, then create one PR and merge only after hosted CI passes.**

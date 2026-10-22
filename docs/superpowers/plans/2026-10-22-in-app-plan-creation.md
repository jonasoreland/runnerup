# In-app Plan Builder Implementation Plan

**Goal:** Build and activate a running plan from Relven's Plan tab without requiring an external AI tool or JSON editor.

**Architecture:** Add a pure plan-draft builder that emits canonical schema-v1 JSON. A localized Compose form collects the inputs; `RelvenHomeActivity` validates and persists the package with the existing plan store, then opens the existing calendar. The Plan library keeps import and AI Plan Kit as alternatives.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Java `RelvenPlanStore` / `RelvenPlanImporter`.

**Spec:** `docs/superpowers/specs/2026-10-22-in-app-plan-creation.md`

## Global Constraints

- Preserve `se.hampusandersson.relven` and RunnerUp session authority.
- Create schema-v1 JSON and persist through `RelvenPlanStore` only.
- English and Swedish UI; no physical ADB or emulator.
- Use the existing worktree and one PR at a time.

## Review Focus

- Start dates before/after selected weekdays and month/year boundaries produce correct local dates.
- No selected training day, blank/overlong names, and invalid durations are rejected before saving.
- Duplicate IDs are impossible, including when a user creates multiple plans with the same name.
- A storage/activation failure leaves the editor recoverable and does not navigate to a phantom plan.
- Existing imported plans and revisions retain their exact source JSON.

### Task 1: Pure draft builder

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/ui/PlanDraftBuilder.kt`; create `app/test/java/org/runnerup/relven/ui/PlanDraftBuilderTest.kt`.

- [x] Write failing tests for valid easy, interval and long-run JSON; repeat-week schedule dates; leap/month/year transitions; and invalid inputs.
- [x] Verify RED, then implement schema-v1 JSON generation with UUIDs and locale-independent ISO dates.
- [x] Verify focused tests pass and parsed output matches the canonical importer model.

### Task 2: Compose plan builder and navigation

**Files:** Create `PlanCreationScreen.kt`; modify `PlanLibraryScreen.kt`, `RelvenHomeActivity.kt`, and English/Swedish app string resources; add focused Compose/state tests.

- [x] Add the “Create plan” entry point and localized form for name, start date, 1–16 weeks, weekday selection and per-day workout type.
- [x] Use an accessible Android date picker; provide field-level validation and a concise schedule preview before save.
- [x] Save only after validation, activate the successfully saved plan, refresh state, then open the existing Plan calendar.
- [x] Keep draft values on persistence errors, show saving feedback, and require confirmation before discarding edits.
- [x] Persist the exact pending package before saving, make retries idempotent, and expose retry in the Plan library after interruption or activation failure.
- [x] Keep submitted form values locked while pending; preserve discard confirmation if generation or durable pending-state persistence fails.

### Task 3: Verification and integration

- [x] Run focused tests, full app unit tests, `:app:assembleLatestDebug`, `:app:lintLatestRelease`, `:spotlessCheck`, and `git diff --check`.
- [x] Review the final diff for accidental edits to user data, package identity, schema semantics and localization.
- [ ] Create exactly one PR from the current branch; wait for independent review and hosted Android/F-Droid CI; merge before starting another PR.

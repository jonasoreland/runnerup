# Google Play Internal Publishing Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a guarded manual Play Internal publisher for Relven 2.0 phone and Wear bundles.

**Architecture:** The workflow restores the existing upload key and Google Play service-account JSON from repository secrets on a temporary GitHub hosted runner. A Gradle property supplies a validated phone Play version code; Wear receives the next code. The workflow verifies package, version, form factor, signatures, and matching certificates before upload.

**Tech Stack:** GitHub Actions, Gradle/Groovy, Android App Bundles, `r0adkll/upload-google-play@v1.1.5`, PowerShell documentation for secret setup.

**Spec:** [2026-10-21-play-publish-workflow-design.md](../specs/2026-10-21-play-publish-workflow-design.md)

## Global Constraints

- Preserve `se.hampusandersson.relven`.
- Use the existing upload key; do not create or substitute a signing key.
- Manual dispatch from `master` must require confirmation text `PUBLISH_INTERNAL`.
- Phone version code is even and greater than `24000374`; Wear version code is phone + 1.
- Do not reveal or commit secret values; remove temporary credentials after every job outcome.
- Do not use ADB or run the Play upload during implementation.

## Review Focus

- Untrusted workflow-dispatch values: pass via environment and validate as decimal range before Gradle/shell arithmetic.
- Java properties special characters in passwords/aliases: escape values before writing `release.properties`.
- Missing/malformed base64 or service-account JSON: fail early with a useful message and no credential contents.
- Bundle package/version/form-factor metadata mismatch: stop before upload.
- Partial failure after temporary file creation: unconditional cleanup.

---

### Task 1: Add validated Play version-code override

**Files:**
- Modify `build.gradle`

**Interface:** Gradle property `-PrelvenPlayVersionCode=<phone code>` is optional. Without it, keep the existing base. When supplied, require an even integer greater than `24000374`, subtract the configured `latestBaseVersionCode` for the root's relative `versionCode`, and leave Wear's existing `+1` rule intact.

- [x] Add the optional override and reject invalid/odd/baseline-or-lower/out-of-range values with a clear Gradle error.
- [x] Build phone and Wear release bundles with `-PrelvenPlayVersionCode=24000376` and verify app `24000376`, Wear `24000377`, and the original package ID.
- [x] Verify a code equal to the baseline, an odd code, and a non-decimal code fail configuration.

### Task 2: Add manual internal publisher and operator guide

**Files:**
- Create `.github/workflows/play-internal.yml`
- Create `docs/play-internal-publishing.md`
- Update `docs/implementation-status.md`
- Update `docs/feature-parity.md`

**Interfaces:** Workflow dispatch takes `confirm` and `phone_version_code` inputs. It reads five repository Actions secrets and uploads one Wear AAB to `wear:internal` and one phone AAB to `internal` only after verification succeeds.

- [x] Add the master-only confirmation gate, least-privilege token permissions, concurrency lock, hosted runner, exact current Gradle build/lint/test task paths, and five expected secret names.
- [x] Restore credentials with restrictive file permissions and create ignored `release.properties` with Java-properties escaped values.
- [x] Verify signed phone/Wear bundle package IDs, version codes, watch metadata, and identical upload certificates before upload.
- [x] Upload to the same internal tracks as the old workflow and remove all temporary key/JSON/properties files with an always-run cleanup step.
- [x] Document secret setup and service-account creation/permissions with official links; state that old repository secret values cannot be recovered through GitHub.
- [x] Validate workflow syntax, run the full local app/Wear build/test/lint/style matrix, inspect the diff. Independent review follows after commit.

### Task 3: Verify and merge serially

- [ ] Commit and push as one PR after confirming the open PR queue is empty.
- [ ] Wait for hosted Android/F-Droid CI and review approval; fix findings before merge.
- [ ] Merge the sole PR, confirm the queue returns to zero, and update Atomic project context.

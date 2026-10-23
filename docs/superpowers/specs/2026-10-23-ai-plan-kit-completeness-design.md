# AI Plan Kit completeness design

## Goal

Make the Android AI Plan Kit self-contained enough for an external AI to understand Relven's actual plan/workout contract and choose only exercises the user currently has available. Both clipboard copy and the ZIP export must include the canonical schemas, full bundled exercise library, supported examples, capability limits, and a snapshot of the device's custom exercise collection.

## Current gap

The checked-in ZIP has the exercise-library schema but not the 116 bundled exercise records or the user's custom records. Clipboard copy extracts only a short static instruction file. The app stores custom exercises in `files/relven/custom-exercises.json`; the bundled catalog is an Android asset. Custom archive state is not part of the canonical bundled library schema.

## Approach

Keep a deterministic, versioned base kit built from checked-in schemas, examples, and the bundled catalog asset. At export time, add the current user's custom exercise collection as a separate schema-validated document. For clipboard copy, produce a plain-text self-contained pack containing the prompt, human-readable format/capability guide, raw canonical schemas, bundled catalog JSON, examples, and current custom collection JSON.

Use the existing plan/workout formats. AI-generated packages target plan schema v1. Explain v2 only as Relven-managed immutable revision metadata. Document the exact importer-supported running subset and the separate strength/prehab subset, including real limits and unsupported schema constructs. For exercise prescribing, require an exact active catalog ID and snapshot; archived custom exercises are context only and cannot be selected. Keep local exercise data on-device and include no history, media, or network data.

Version this expanded package as AI Plan Kit v2 so existing v1 exports remain identifiable. No plan, exercise, or workout schema changes are required.

## Acceptance

- Clipboard content alone includes the full output contract, every schema, every bundled exercise record, and all device custom records with archive state.
- The downloadable v2 ZIP includes the same reference files plus the current custom-exercise collection snapshot.
- AIs are instructed to use only installed, non-archived, equipment-free catalog IDs and exact English snapshots for supported strength/prehab plans.
- Capability claims match the actual importer and plan creator, not every structure permitted by a broad JSON schema.
- Exports remain local/offline; custom data is exported only after the user chooses copy/share/save.
- Static archive generation remains deterministic; runtime data is validated and size-bounded.
- Tests cover ZIP contents, custom exercise inclusion/archive state, full prompt contents, and schema/reference compatibility.

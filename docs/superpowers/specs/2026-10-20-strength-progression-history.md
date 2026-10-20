# Strength progression history

## Goal

Show users their recorded per-set results for each catalog exercise so they can review real strength progression offline.

## Constraints

- Use completed guided strength/prehab sessions already stored in the app's private offline store.
- Match a result to an exercise by stable `exerciseId` from its persisted workout snapshot, never by translated/display name or step ID.
- Show completion date and the actual reps or timed duration for every set. Keep each session's set values together and order sessions newest first.
- Keep exercise detail read-only. Do not generate suggested loads, progression targets, training-load scores, or claims unsupported by captured data.
- Show at most the 10 most recent matching sessions per exercise. Scan at most the 500 most recently modified completion files and read at most 32 MiB per refresh. Report invalid or omitted records; do not silently treat damaged or unscanned records as valid history.
- Keep history available offline and do not change the RunnerUp running engine or the completion record schema.
- Preserve the existing Relven Compose colors, typography, spacing, and navigation patterns.

## Design

Add a “Your history” section to the existing exercise catalog detail screen. Each row represents one completed workout containing that exercise and displays its localized completion date plus the exact set results, using repetitions or seconds as recorded. An exercise with no matching records gets a clear empty state. Loading and storage failure states remain distinct; corrupt individual records are omitted and surfaced with a localized partial-history notice.

The private strength store enumerates a bounded set of recent completion files and validates each with the existing strict codec. It stops after 500 candidate files or 32 MiB read, whichever comes first, and reports invalid and omitted record counts. A pure projection filters each session's results using the exercise ID stored in its workout snapshot. UI state is loaded on the IO dispatcher and passed into the catalog detail renderer. The view remains read-only and independent of the currently active plan.

## Acceptance criteria

1. Completed sets for a selected catalog exercise appear in newest-first session groups with exact repetitions or durations.
2. Two exercises with the same localized name but different IDs never share history; a stable ID continues matching when its localized display name changes.
3. Multiple sets in one session retain their original set order and values.
4. Timed and repetition sets are formatted according to their actual recorded field.
5. No matching data produces a localized empty state; a storage read failure produces a retryable unavailable state.
6. Invalid historical files are omitted and disclosed while valid sessions remain visible.
7. The history list is bounded to the newest 10 sessions for one exercise; each refresh scans at most 500 files and reads at most 32 MiB.
8. The feature works entirely offline and adds no dependency or writes to workout records.
9. Phone debug/release and free/F-Droid builds, app tests, Wear/HR tests, lint, Spotless, and hosted CI pass. No ADB device is used before all phases are complete.

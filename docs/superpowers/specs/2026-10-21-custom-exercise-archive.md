# Custom Exercise Archive

## Goal

Let users remove personal exercises from normal catalog browsing and restore them later, while keeping saved plan and workout history references valid.

## Constraints

- Keep archive state private and offline with the custom exercise record.
- Preserve stable UUIDs, exercise content, and content revision when archiving or restoring.
- Existing records without archive metadata remain active when read.
- Keep archived records available to plan/reference resolution and historical detail lookup.
- Bundled exercises cannot be archived.
- Provide localized archive browsing, restore, and storage failure feedback.
- No physical device or ADB is used.

## Design

Add an optional archive flag to the current custom record format and default it to active when absent. The catalog UI filters archived entries out by default and has a separate archived view; archived custom details offer restore, while active custom details offer archive. The repository continues returning all entries for stable-ID resolution.

## Acceptance Criteria

1. Archiving hides a custom exercise from normal browsing without deleting it or changing its UUID or revision.
2. The archived view can find it and restore it to normal browsing.
3. Older custom records without archive metadata still load as active.
4. Bundled entries have no archive action; archived records remain included in ID-based plan and history resolution.
5. English and Swedish labels and recoverable storage errors are provided.
6. Focused persistence/state tests and the app/Wear build/test/lint/style gates pass.

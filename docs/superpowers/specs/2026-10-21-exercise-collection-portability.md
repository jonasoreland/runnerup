# Custom exercise collection portability

Relven can already create, edit, archive, and restore user-authored exercises offline. Add a versioned JSON file format so a user can export and later import those custom records on another installation.

The collection contains only custom exercises, including archived entries. Preserve each stable UUID, revision, bilingual name and instructions, category, movement pattern, muscle groups, equipment, and archive state. Bundled catalog exercises remain read-only and are never exported. No media or remote references exist in the current custom exercise model; the collection must not introduce them.

Import merges records into the current custom catalog. New IDs are added. Exact record matches are ignored, making repeated imports safe. An ID already in the bundled catalog, or an existing custom ID with any differing field/revision/archive state, rejects the entire import. Imported text must be canonical and have no surrounding whitespace, so accepted values are preserved exactly rather than silently normalized. Parse, validate, conflict-check, and enforce limits before one atomic store replacement. No partial writes or silent substitutions.

Use `format = "relven.exercise-collection"`, `schemaVersion = 1`, and an `exercises` array using the current custom record fields. Keep the payload at or below 1 MiB and the merged custom catalog at or below 200 entries. Reject duplicate IDs within the input and unknown/missing fields. Expose import and export in the existing Exercise Catalog via Android document pickers with localized success/error feedback. All work stays local and offline.

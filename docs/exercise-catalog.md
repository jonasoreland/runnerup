# Offline exercise catalog

The phone app bundles its initial exercise reference data in `app/src/main/assets/relven/exercise-library-v1.json`. The catalog is available offline and contains original, bilingual English/Swedish instructions for no-equipment bodyweight exercises.

`schemas/exercise-library-v1.schema.json` defines the versioned format. Each exercise has a stable UUID, an exercise revision, a category, movement-pattern key, muscle-group keys, equipment list, localized name, and localized instruction steps. `libraryRevision` versions the bundled catalog as a whole; changing the meaning or instructions for one exercise requires incrementing that exercise's `revision` while keeping its `exerciseId` stable. A new catalog format requires a new `schemaVersion` and parser support.

The Android parser validates the whole library before exposing any entries. The phone app exposes local search, category filters, and a detail view. The initial catalog is immutable. It contains no images, animation, custom exercises, workout prescriptions, logging, or strength-plan execution; those remain separate product work. No third-party exercise dataset or external media is used.

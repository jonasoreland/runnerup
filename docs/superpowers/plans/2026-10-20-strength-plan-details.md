# Plan: offline strength plan details

1. [x] Map Workout v3 strength schema and plan importer/store boundaries; add failing strict-parser and plan acceptance tests.
2. [x] Implement immutable strength/prehab detail model and strict offline parser, preserving source snapshots and isolating them from RunnerUp execution.
3. [x] Add plan-calendar detail state for ordered exercises, pinned snapshots, reps/sets/rest, with English/Swedish labels and empty/error behavior.
4. [x] Add plan revision/store regression coverage, update AI Plan Kit and feature parity guidance, and run full local debug/release/test/lint/Spotless gates without ADB.
5. [ ] Diff/app ID/queue check; commit, open one PR, wait hosted CI, merge, verify queue, and record the milestone.

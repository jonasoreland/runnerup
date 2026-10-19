# Migration milestone

1. Fetched the existing GitHub fork `HampusAndersson01/Relven-2.0` into the empty local checkout and added RunnerUp's `upstream` remote. Both pointed to `ac4f8baa` initially.
2. Built untouched phone/watch debug APKs and selected tests before changing application code.
3. Reused the original Relven phone/watch Play ID `se.hampusandersson.relven` per the owner's instruction, retained internal namespaces and wearable protocol paths, changed launcher/permission labels, and applied the uploaded icon from verified current Stitch project `15389433782836702529`.
4. Replaced README/store copy and removed stale upstream store screenshots/listings. License, credits, copyright headers and Git history remain.

The square icon reference came from the verified current Stitch project. The original vector supplied by the owner was copied read-only from the original Relven worktree, and a transparent cutout of its paths was rendered for phone and watch launchers. It has not been checked on a real device. Android's default launcher splash uses the app icon; a dedicated splash design remains pending.

An upgrade with the same ID may preserve the old Relven app's private data. The old phone stores workouts in `relven-workouts.db`, whereas this RunnerUp-derived app writes to `runnerup.db`; the separate names avoid a direct SQLite schema collision but do not make the old data visible. Existing workouts and history will not appear until an explicit migration is implemented and tested. A Play upload also requires the existing signing identity and a version code higher than every published phone/watch artifact. Do not uninstall the old app or ship this build as an update before a backup and migration pathway are verified. The inherited database export/import path also needs a compatibility test. The old `/runnerup.db.export` deep link remains until importer behavior is audited.

Next: map the old Room schema and supported workout/activity records into an explicit, repeatable migration with a backup/export path. Verify an actual upgrade using the existing signing key and a copy of production-era data before Play upload.
